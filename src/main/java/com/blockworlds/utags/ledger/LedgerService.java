package com.blockworlds.utags.ledger;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.api.ActiveTags;
import com.blockworlds.utags.api.GrantResult;
import com.blockworlds.utags.api.RevokeResult;
import com.blockworlds.utags.api.TransferResult;
import com.blockworlds.utags.api.UTagActiveChangedEvent;
import com.blockworlds.utags.api.UTagGrantedEvent;
import com.blockworlds.utags.api.UTagRevokedEvent;
import com.blockworlds.utags.api.GrantOutcome;
import com.blockworlds.utags.api.RevokeOutcome;
import com.blockworlds.utags.module.ChangeFeed;
import com.blockworlds.utags.selection.Selection;
import com.blockworlds.utags.selection.SelectionService;
import com.blockworlds.utags.storage.Database;
import com.blockworlds.utags.tags.TagRegistry;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * The async side of the ledger: looks up what only the server knows (tag defined, permission held), runs the
 * {@link LedgerStore} transaction on the database executor, and after the commit refreshes the ownership cache,
 * publishes the change, applies a cleared selection to the output and fires the events. A replay fires no events,
 * but it repeats the idempotent steps (reload, publish, re-apply the stored selection): when the first call committed
 * and its reply was lost, the caller's retry is a replay, and those steps would otherwise never run.
 */
public final class LedgerService {

    private final Plugin plugin;
    private final Database db;
    private final LedgerStore store;
    private final TagRegistry tags;
    private final Ownership ownership;
    private final SelectionService selection;
    private final ChangeFeed changes;
    private final LuckPerms luckPerms;
    private final Logger log;

    public LedgerService(Plugin plugin, Database db, LedgerStore store, TagRegistry tags, Ownership ownership,
                         SelectionService selection, ChangeFeed changes, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.db = db;
        this.store = store;
        this.tags = tags;
        this.ownership = ownership;
        this.selection = selection;
        this.changes = changes;
        this.luckPerms = luckPerms;
        this.log = plugin.getLogger();
    }

    public CompletableFuture<GrantResult> grant(UUID player, String tagId, String source, String opId) {
        Tag tag = tags.byName(tagId);
        String canonical = tag == null ? tagId : tag.getName();
        return ownership.hasTagPermission(player, canonical)
                .thenCompose(permission -> db.supply(c -> store.grant(c, player, canonical, source, opId, tag != null, permission)))
                .thenApply(result -> {
                    if (result.outcome() != GrantOutcome.UNKNOWN_TAG) {
                        committed(player);
                        if (!result.replayed()) fire(new UTagGrantedEvent(player, canonical, source, opId, result.outcome()));
                    }
                    return result;
                });
    }

    public CompletableFuture<RevokeResult> revoke(UUID player, String tagId, String source, String opId) {
        Tag tag = tags.byName(tagId);
        String canonical = tag == null ? tagId : tag.getName();
        return ownership.hasTagPermission(player, canonical)
                .thenCompose(permission -> db.supply(c -> store.revoke(c, player, canonical, source, opId, permission)))
                .thenApply(detail -> {
                    if (detail.outcome() != RevokeOutcome.NOT_HELD) {
                        committed(player);
                        if (!detail.replayed()) {
                            if (detail.clearedSelection()) selectionCleared(player, canonical, detail);
                            fire(new UTagRevokedEvent(player, canonical, source, opId, detail.outcome()));
                        } else if (detail.outcome() == RevokeOutcome.REVOKED_GONE) {
                            // the original call may have cleared the stored selection: re-apply what is stored
                            reapplySelection(player);
                        }
                    }
                    return new RevokeResult(detail.outcome(), detail.replayed());
                });
    }

    public CompletableFuture<TransferResult> transferAll(UUID from, UUID into, String opId) {
        return db.supply(c -> store.transferAll(c, from, into, opId)).thenApply(detail -> {
            if (detail.replayed()) {
                // the original call may have copied rows and set the selection: reload both
                if (detail.copied() > 0) committed(into);
                reapplySelection(into);
            } else {
                if (detail.copied() > 0) committed(into);
                if (detail.adopted() != null) reapplySelection(into);
            }
            return detail.result();
        });
    }

    /** Every ledger row of the player, newest first. */
    public CompletableFuture<List<LedgerStore.Row>> rows(UUID player) {
        return db.supply(c -> store.rows(c, player));
    }

    /** The ledger and permission tags of the player (permission tags among the defined ones). */
    public CompletableFuture<Set<String>> owned(UUID player) {
        List<String> defined = tags.all().stream().map(Tag::getName).toList();
        CompletableFuture<Set<String>> ledger = db.supply(c -> store.owned(c, player));
        return ledger.thenCombine(ownership.permissionTags(player, defined), (rows, byPermission) -> {
            Set<String> all = new LinkedHashSet<>();
            for (String row : rows) {
                Tag tag = tags.byName(row);
                all.add(tag == null ? row : tag.getName());
            }
            all.addAll(byPermission);
            return all;
        });
    }

    // --- after commit ---

    private void committed(UUID player) {
        ownership.reload(player);
        changes.changed(ChangeFeed.Kind.GRANTS, player);
    }

    /** Publishes a selection change and, when the player is online, reloads it and rewrites the LuckPerms output. */
    private void reapplySelection(UUID player) {
        changes.changed(ChangeFeed.Kind.SELECTION, player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(player);
            if (online != null) selection.onJoin(online); // reloads the selection and rewrites the output
        });
    }

    /**
     * A revoke removed the last claim on the worn tag: the stored selection is already cleared. Online players get the
     * cache and the LuckPerms output fixed through the selection service; offline ones only the LuckPerms nodes.
     */
    private void selectionCleared(UUID player, String tagName, LedgerStore.RevokeOutcomeDetail detail) {
        changes.changed(ChangeFeed.Kind.SELECTION, player);
        Selection now = selection.cached(player); // NONE for an offline player
        ActiveTags before = new ActiveTags(detail.clearedPrefix() ? tagName : now.prefixTag(),
                detail.clearedSuffix() ? tagName : now.suffixTag());
        ActiveTags after = new ActiveTags(detail.clearedPrefix() ? null : now.prefixTag(),
                detail.clearedSuffix() ? null : now.suffixTag());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(player);
            if (online != null) {
                if (detail.clearedPrefix()) selection.clear(online, TagType.PREFIX).exceptionally(ex -> warn(player, ex));
                if (detail.clearedSuffix()) selection.clear(online, TagType.SUFFIX).exceptionally(ex -> warn(player, ex));
            } else {
                luckPerms.getUserManager().loadUser(player).thenCompose(user -> {
                    if (detail.clearedPrefix()) user.data().clear(NodeType.PREFIX.predicate(n -> n.getPriority() == SelectionService.PRIORITY));
                    if (detail.clearedSuffix()) user.data().clear(NodeType.SUFFIX.predicate(n -> n.getPriority() == SelectionService.PRIORITY));
                    return luckPerms.getUserManager().saveUser(user);
                }).exceptionally(ex -> warn(player, ex));
            }
        });
        fire(new UTagActiveChangedEvent(player, before, after));
    }
    private Void warn(UUID player, Throwable ex) {
        log.warning("Could not clear the LuckPerms output of " + player + ": " + Database.reason(ex));
        return null;
    }

    private void fire(org.bukkit.event.Event event) {
        try {
            Bukkit.getPluginManager().callEvent(event);
        } catch (RuntimeException e) {
            log.warning("A listener failed on " + event.getEventName() + ": " + e);
        }
    }
}
