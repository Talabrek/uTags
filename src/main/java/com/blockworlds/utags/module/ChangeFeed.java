package com.blockworlds.utags.module;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * The seam between code that changes shared data and network sync (contracts.md, "Ownership").
 *
 * <ul>
 *   <li>Writers call {@link #changed} after their transaction commits (a tag definition: player null; a player's
 *       selection, grants or colours: that player).</li>
 *   <li>Caches call {@link #subscribe} to hear about changes made on OTHER servers, so they can reload.</li>
 *   <li>The sync module calls {@link #setPublisher} to send local changes out, and {@link #deliverRemote} for each
 *       change it reads from another server. Without a sync module (single server) both directions do nothing.</li>
 * </ul>
 * Listeners run on the thread that delivers (not the main thread) and must not block.
 */
public final class ChangeFeed {

    public enum Kind { TAGS, SELECTION, GRANTS, COLORS }

    private final Logger log;
    private final Map<Kind, List<Consumer<UUID>>> listeners = new ConcurrentHashMap<>();
    private volatile BiConsumer<Kind, UUID> publisher = (kind, player) -> { };

    public ChangeFeed(Logger log) {
        this.log = log;
    }

    /** Shared data changed here (call after commit). {@code player} is null for server-wide data such as tag definitions. */
    public void changed(Kind kind, UUID player) {
        try {
            publisher.accept(kind, player);
        } catch (RuntimeException e) {
            log.warning("Could not publish a " + kind + " change: " + e);
        }
    }

    /** Hears changes made on other servers; {@code onRemote} gets the player, or null for "reload everything". */
    public void subscribe(Kind kind, Consumer<UUID> onRemote) {
        listeners.computeIfAbsent(kind, k -> new CopyOnWriteArrayList<>()).add(onRemote);
    }

    /** For the sync module: where local changes go. */
    public void setPublisher(BiConsumer<Kind, UUID> publisher) {
        this.publisher = publisher;
    }

    /** For the sync module: a change another server made. */
    public void deliverRemote(Kind kind, UUID player) {
        for (Consumer<UUID> listener : listeners.getOrDefault(kind, List.of())) {
            try {
                listener.accept(player);
            } catch (RuntimeException e) {
                log.warning("A " + kind + " change listener failed: " + e);
            }
        }
    }
}
