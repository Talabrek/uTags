package com.blockworlds.utags.selection;

import com.blockworlds.utags.PlayerTagColorPreference;
import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.display.ColorPreferences;
import com.blockworlds.utags.display.DisplayService;
import com.blockworlds.utags.module.ChangeFeed;
import com.blockworlds.utags.storage.Database;
import com.blockworlds.utags.tags.TagRegistry;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Which tag each player wears. The database ({@code utags_selection}) is the truth; the LuckPerms prefix and suffix
 * nodes at priority {@link #PRIORITY} are written as output for chat plugins and never read back, except once per
 * player to upgrade a 1.2/1.3 install (see {@link #loadOrUpgrade}).
 */
public final class SelectionService {

    /** Priority of the LuckPerms prefix/suffix nodes uTags writes. */
    public static final int PRIORITY = 10000;

    private final JavaPlugin plugin;
    private final Database db;
    private final TagRegistry tags;
    private final ColorPreferences colors;
    private final DisplayService display;
    private final LuckPerms luckPerms;
    private final Logger log;
    private final SelectionCache cache = new SelectionCache();
    private final ChangeFeed changes;
    private volatile SelectWriter writer = (c, player, prefix, tag, permissionOwned) -> {
        SelectionStore.set(c, player, prefix, tag);
        return true;
    };

    /** Writes a select to the database; false when the player no longer owns the tag (nothing written). */
    @FunctionalInterface
    public interface SelectWriter {
        boolean write(java.sql.Connection c, UUID player, boolean prefix, String tag, boolean permissionOwned)
                throws java.sql.SQLException;
    }

    public SelectionService(JavaPlugin plugin, Database db, TagRegistry tags, ColorPreferences colors,
                            DisplayService display, LuckPerms luckPerms, ChangeFeed changes) {
        this.plugin = plugin;
        this.db = db;
        this.tags = tags;
        this.colors = colors;
        this.display = display;
        this.luckPerms = luckPerms;
        this.log = plugin.getLogger();
        this.changes = changes;
        changes.subscribe(ChangeFeed.Kind.SELECTION, who -> onRemote(who, false));
        changes.subscribe(ChangeFeed.Kind.COLORS, who -> onRemote(who, true));
    }

    /** The ledger installs its locked write, so a select cannot cross a revoke of the same tag. */
    public void useWriter(SelectWriter writer) {
        this.writer = writer;
    }

    /** The selection loaded for an online player (NONE before the join load finished). */
    public Selection cached(UUID player) {
        return cache.get(player);
    }

    /** The name of the prefix tag the player wears, or null. */
    public String appliedPrefix(UUID player) {
        return cached(player).prefixTag();
    }

    public void unload(UUID player) {
        cache.remove(player);
        colors.unload(player);
    }

    // --- join ---

    /**
     * Loads the player's selection. A player with no row yet (a 1.2/1.3 install, first join since the upgrade) gets
     * one derived once from the LuckPerms nodes at priority 10000 and stored; after that the nodes are output only.
     */
    public CompletableFuture<Selection> loadOrUpgrade(UUID player) {
        long token = cache.beginLoad(player);
        return tags.ready().thenCompose(v -> db.supply(c -> SelectionStore.find(c, player))).thenCompose(found -> {
            if (found != null) return CompletableFuture.completedFuture(found);
            return readLuckPerms(player).thenCompose(lp -> {
                Selection derived = Selection.deriveFromLuckPerms(lp, this::tagNameByDisplay);
                return db.supply(c -> SelectionStore.insertIfAbsent(c, player, derived) ? derived : SelectionStore.find(c, player));
            });
        }).thenApply(selection -> cache.completeLoad(player, token, selection)); // keeps a pick made while loading
    }

    private String tagNameByDisplay(String text) {
        for (Tag t : tags.all()) {
            if (text.equals(t.getDisplay()) || text.equals(DisplayService.toLegacy(t.getDisplay()))) return t.getName();
        }
        return null;
    }

    private CompletableFuture<Selection.LuckPermsValues> readLuckPerms(UUID player) {
        return luckPerms.getUserManager().loadUser(player).thenApply(user -> {
            String prefix = null;
            String suffix = null;
            for (Node node : user.getNodes(NodeType.PREFIX)) {
                if (node instanceof PrefixNode p && p.getPriority() == PRIORITY) prefix = p.getMetaValue();
            }
            for (Node node : user.getNodes(NodeType.SUFFIX)) {
                if (node instanceof SuffixNode s && s.getPriority() == PRIORITY) suffix = s.getMetaValue();
            }
            return new Selection.LuckPermsValues(prefix, suffix);
        });
    }

    /** Join handler: loads colours and the selection off the main thread, then writes the output on the main thread's turn. */
    public void onJoin(Player player) {
        UUID uuid = player.getUniqueId();
        tags.ready().thenCompose(v -> colors.loadPlayer(uuid)).thenCompose(v -> loadOrUpgrade(uuid)).thenAcceptAsync(selection -> {
            if (!player.isOnline()) return;
            if (selection.prefixTag() != null) log.fine("Identified applied tag '" + selection.prefixTag() + "' for player " + player.getName() + " on join.");
            applyStored(player);
        }, r -> Bukkit.getScheduler().runTask(plugin, r)).exceptionally(ex -> {
            log.severe("Could not load the tag selection of " + player.getName() + ": " + Database.reason(ex));
            return null;
        });
    }

    // --- changes ---

    /** Makes the tag the player's prefix or suffix: saved in the database first, then written to LuckPerms. Main thread. */
    public void select(Player player, String tagName, TagType type) {
        Tag tag = tags.byName(tagName);
        if (tag == null || tag.getDisplay() == null || tag.getDisplay().isEmpty()) {
            log.warning("Could not find display string for tag: " + tagName);
            player.sendMessage(ChatColor.RED + "Error applying tag: Could not find tag display.");
            return;
        }
        boolean prefix = type == TagType.PREFIX;
        if (!prefix && type != TagType.SUFFIX) return;
        UUID uuid = player.getUniqueId();
        boolean permissionOwned = player.hasPermission(com.blockworlds.utags.CustomTags.permission(tag.getName()));
        SelectWriter write = writer;
        db.supply(c -> write.write(c, uuid, prefix, tag.getName(), permissionOwned)).thenCompose(written -> {
            if (!written) {
                // revoked between the menu's ownership check and this write
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(ChatColor.RED + "You no longer own this tag."));
                return CompletableFuture.<User>completedFuture(null);
            }
            changes.changed(ChangeFeed.Kind.SELECTION, uuid);
            cache.setLocal(uuid, prefix, tag.getName());
            return luckPerms.getUserManager().loadUser(uuid);
        }).thenCompose(user -> {
            if (user == null) return CompletableFuture.<Void>completedFuture(null); // the write was refused
            if (prefix) {
                replacePrefix(user, tag, "&" + colors.nameColor(uuid).getChar());
            } else {
                user.data().clear(NodeType.SUFFIX.predicate()); // a suffix pick replaces every suffix, as in 1.3.0
                user.data().add(SuffixNode.builder(DisplayService.toLegacy(tag.getDisplay()), PRIORITY).build());
            }
            return luckPerms.getUserManager().saveUser(user);
        }).whenComplete((v, ex) -> {
            if (ex != null) log.severe("Failed to set the " + type.name().toLowerCase() + " of " + player.getName() + ": " + Database.reason(ex));
            Bukkit.getScheduler().runTask(plugin, () -> display.refreshDisplayName(player));
        });
    }

    /** Colour of the worn tag changed: save it, and rewrite the prefix when the player wears that tag. */
    public void setTagColor(UUID player, String tagName, ChatColor bracket, ChatColor content) {
        colors.setTagColors(player, tagName, bracket, content);
        Player online = Bukkit.getPlayer(player);
        if (online != null && tagName.equals(appliedPrefix(player))) applyOutput(online, "&" + colors.nameColor(player).getChar());
    }

    /** Saves a name colour code ("&a", null or "reset" to clear) and applies it to the player if online. */
    public void setNameColor(UUID player, String colorCode) {
        colors.setNameColorCode(player, colorCode).thenAcceptAsync(saved -> {
            Player online = Bukkit.getPlayer(player);
            if (online != null && online.isOnline()) applyStored(online);
        }, r -> Bukkit.getScheduler().runTask(plugin, r));
    }

    /** Rewrites the LuckPerms output from the stored selection and the player's stored name colour. */
    public void applyStored(Player player) {
        if (player == null || !player.isOnline()) return;
        applyOutput(player, "&" + colors.nameColor(player.getUniqueId()).getChar());
    }

    /**
     * Writes the prefix (tag + name colour code) and suffix nodes when they differ from the stored selection, then
     * refreshes the display name. A player with no stored prefix tag keeps whatever node is there.
     */
    private void applyOutput(Player player, String nameColorCode) {
        UUID uuid = player.getUniqueId();
        Selection selection = cached(uuid);
        Tag prefixTag = tags.byName(selection.prefixTag());
        Tag suffixTag = tags.byName(selection.suffixTag());
        if (prefixTag == null && suffixTag == null) {
            display.refreshDisplayName(player);
            return;
        }
        luckPerms.getUserManager().loadUser(uuid).thenCompose(user -> {
            boolean changed = false;
            if (prefixTag != null) changed |= replacePrefix(user, prefixTag, nameColorCode);
            if (suffixTag != null) {
                String wanted = DisplayService.toLegacy(suffixTag.getDisplay());
                boolean present = user.getNodes(NodeType.SUFFIX).stream()
                        .anyMatch(n -> n instanceof SuffixNode s && s.getPriority() == PRIORITY && wanted.equals(s.getMetaValue()));
                if (!present) {
                    user.data().clear(NodeType.SUFFIX.predicate().and(n -> n instanceof SuffixNode s && s.getPriority() == PRIORITY));
                    user.data().add(SuffixNode.builder(wanted, PRIORITY).build());
                    changed = true;
                }
            }
            return changed ? luckPerms.getUserManager().saveUser(user) : CompletableFuture.<Void>completedFuture(null);
        }).whenComplete((v, ex) -> {
            if (ex != null) log.severe("Failed to write the tag of " + player.getName() + " to LuckPerms: " + Database.reason(ex));
            Bukkit.getScheduler().runTask(plugin, () -> display.refreshDisplayName(player));
        });
    }

    // --- changes made on other servers ---

    /**
     * Another server changed this player's selection or colours (called from the sync thread). Only an online
     * player matters here, except that a name colour is cached for everyone. The reload goes through
     * {@link SelectionCache}, so a pick made here while it runs is kept; the LuckPerms output is then rewritten on
     * the main thread.
     */
    private void onRemote(UUID uuid, boolean colourChange) {
        if (uuid == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            boolean online = player != null && player.isOnline();
            if (!online && !colourChange) return;
            CompletableFuture<Void> colourStep = colourChange ? colors.reloadPlayer(uuid, online) : CompletableFuture.completedFuture(null);
            if (!online) return;
            Selection before = cached(uuid);
            colourStep.thenCompose(v -> reloadSelection(uuid)).thenAcceptAsync(after -> {
                if (!player.isOnline()) return;
                clearRemovedOutput(player, before, after);
                applyStored(player);
            }, r -> Bukkit.getScheduler().runTask(plugin, r)).exceptionally(ex -> {
                log.warning("Could not apply a change from another server for " + player.getName() + ": " + Database.reason(ex));
                return null;
            });
        });
    }

    /** Re-reads the player's row into the cache (keeping fields they changed here meanwhile). */
    public CompletableFuture<Selection> reloadSelection(UUID uuid) {
        long token = cache.beginLoad(uuid);
        return db.supply(c -> SelectionStore.find(c, uuid))
                .thenApply(found -> cache.completeLoad(uuid, token, found == null ? cache.get(uuid) : found));
    }

    /** A tag cleared on another server leaves its LuckPerms node behind otherwise (applyOutput only writes). */
    private void clearRemovedOutput(Player player, Selection before, Selection after) {
        boolean prefix = before.prefixTag() != null && after.prefixTag() == null;
        boolean suffix = before.suffixTag() != null && after.suffixTag() == null;
        if (!prefix && !suffix) return;
        luckPerms.getUserManager().loadUser(player.getUniqueId()).thenCompose(user -> {
            if (prefix) user.data().clear(NodeType.PREFIX.predicate().and(n -> n instanceof PrefixNode p && p.getPriority() == PRIORITY));
            if (suffix) user.data().clear(NodeType.SUFFIX.predicate().and(n -> n instanceof SuffixNode s && s.getPriority() == PRIORITY));
            return luckPerms.getUserManager().saveUser(user);
        }).whenComplete((v, ex) -> {
            if (ex != null) log.severe("Failed to clear the tag of " + player.getName() + " in LuckPerms: " + Database.reason(ex));
            Bukkit.getScheduler().runTask(plugin, () -> display.refreshDisplayName(player));
        });
    }

    /** The two spellings of a colour code, {@code §x} and {@code &x}, mean the same: compared equal, no rewrite. */
    public static boolean sameValue(String a, String b) {
        if (a == null || b == null) return a == b;
        return a.replace('§', '&').equals(b.replace('§', '&'));
    }

    /**
     * Removes the worn prefix or suffix: the stored selection is cleared and the LuckPerms node with it. Completes
     * (off the main thread) once LuckPerms is saved; the future fails if the database or LuckPerms failed.
     */
    public CompletableFuture<Void> clear(Player player, TagType type) {
        boolean prefix = type == TagType.PREFIX;
        UUID uuid = player.getUniqueId();
        return db.run(c -> {
            SelectionStore.set(c, uuid, prefix, null);
            changes.changed(ChangeFeed.Kind.SELECTION, uuid);
        }).thenCompose(v -> {
            cache.setLocal(uuid, prefix, null);
            return luckPerms.getUserManager().loadUser(uuid);
        }).thenCompose(user -> {
            user.data().clear((prefix ? NodeType.PREFIX : NodeType.SUFFIX).predicate());
            return luckPerms.getUserManager().saveUser(user);
        }).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> display.refreshDisplayName(player)));
    }

    /** Replaces the priority-10000 prefix node with the tag's; false when it already holds that value. */
    private boolean replacePrefix(User user, Tag tag, String nameColorCode) {
        PlayerTagColorPreference pref = colors.tagPreference(user.getUniqueId(), tag.getName());
        String wanted = DisplayService.lpPrefix(tag.getDisplay(), pref, nameColorCode);
        PrefixNode existing = null;
        for (Node node : user.getNodes(NodeType.PREFIX)) {
            if (node instanceof PrefixNode p && p.getPriority() == PRIORITY) {
                existing = p;
                break;
            }
        }
        if (existing != null && sameValue(wanted, existing.getMetaValue())) return false;
        if (existing != null) user.data().remove(existing);
        user.data().add(PrefixNode.builder(wanted, PRIORITY).build());
        return true;
    }
}
