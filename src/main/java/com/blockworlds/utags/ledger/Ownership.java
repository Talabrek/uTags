package com.blockworlds.utags.ledger;

import com.blockworlds.utags.CustomTags;
import com.blockworlds.utags.module.ChangeFeed;
import com.blockworlds.utags.storage.Database;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Who owns which tag: an unrevoked ledger row OR the permission {@code utags.tag.<tag>}. Menus and commands ask
 * {@link #owns(Player, String)}, which answers from memory: the ledger tags of each online player are loaded on join,
 * reloaded after every local commit and when another server reports a GRANTS change. Before the ledger is attached
 * (or while a join load is still running) only the permission counts, which is exactly the 1.3.0 behaviour.
 */
public final class Ownership implements Listener {

    private final LuckPerms luckPerms;
    private final Logger log;
    private final Map<UUID, Set<String>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> applied = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private volatile Database db;
    private volatile LedgerStore store;

    public Ownership(LuckPerms luckPerms, Logger log) {
        this.luckPerms = luckPerms;
        this.log = log;
    }

    /** Called by the ledger module once the schema exists. */
    void attach(Database db, LedgerStore store) {
        this.db = db;
        this.store = store;
    }

    void detach() {
        this.store = null;
        cache.clear();
        applied.clear();
    }

    /** Wires the remote-change reload of the ledger part into the change feed. */
    void subscribe(ChangeFeed changes) {
        changes.subscribe(ChangeFeed.Kind.GRANTS, player -> {
            if (player == null) cache.keySet().forEach(this::reload);
            else if (cache.containsKey(player)) reload(player);
        });
    }

    // --- sync answers for menus and commands (main thread or any) ---

    /** True when the online player holds a ledger grant or the permission for the tag. */
    public boolean owns(Player player, String tagName) {
        return player.hasPermission(CustomTags.permission(tagName)) || ledgerOwns(player.getUniqueId(), tagName);
    }

    /** True when the player's cached ledger tags include the tag (case-insensitive, like the tag names). */
    public boolean ledgerOwns(UUID player, String tagName) {
        Set<String> tags = cache.get(player);
        return tags != null && tagName != null && tags.contains(key(tagName));
    }

    /** The cached ledger tags of an online player (lower-case ids); empty for others. */
    public Set<String> cachedLedgerTags(UUID player) {
        return cache.getOrDefault(player, Set.of());
    }

    // --- loading ---

    /** Reloads the ledger tags of a player from the database, off the main thread. Stale answers are dropped. */
    public CompletableFuture<Void> reload(UUID player) {
        Database database = db;
        LedgerStore ledger = store;
        if (database == null || ledger == null) return CompletableFuture.completedFuture(null);
        long ticket = sequence.incrementAndGet();
        return database.supply(c -> ledger.owned(c, player)).thenAccept(tags -> {
            Set<String> keys = new HashSet<>();
            for (String tag : tags) keys.add(key(tag));
            AtomicLong last = applied.computeIfAbsent(player, k -> new AtomicLong());
            synchronized (last) {
                if (ticket < last.get()) return; // a newer load already landed
                last.set(ticket);
                if (store != null) cache.put(player, Set.copyOf(keys));
            }
        }).exceptionally(ex -> {
            log.warning("Could not load the tag grants of " + player + ": " + Database.reason(ex));
            return null;
        });
    }

    public void forget(UUID player) {
        cache.remove(player);
        applied.remove(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reload(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    // --- async answers (any player, online or not) ---

    /** True when the permission {@code utags.tag.<tag>} is granted, read from LuckPerms (works for offline players). */
    public CompletableFuture<Boolean> hasTagPermission(UUID player, String tagName) {
        return permissionUser(player).thenApply(user -> permits(user, tagName));
    }

    /** Which of the tags the player holds by permission, read from LuckPerms. */
    public CompletableFuture<Set<String>> permissionTags(UUID player, Collection<String> tagNames) {
        return permissionUser(player).thenApply(user -> {
            Set<String> held = new HashSet<>();
            for (String tag : tagNames) if (permits(user, tag)) held.add(tag);
            return held;
        });
    }

    private CompletableFuture<User> permissionUser(UUID player) {
        return luckPerms.getUserManager().loadUser(player);
    }

    private static boolean permits(User user, String tagName) {
        return user.getCachedData().getPermissionData().checkPermission(CustomTags.permission(tagName)).asBoolean();
    }

    static String key(String tag) {
        return tag.toLowerCase(Locale.ROOT);
    }
}
