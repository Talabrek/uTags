package com.blockworlds.utags.api;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A player's selected tags changed (by the player, an admin, or a revoke).
 * Fired off the main thread, after the change is committed; listeners must not block for long.
 */
public final class UTagActiveChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final ActiveTags before;
    private final ActiveTags after;

    public UTagActiveChangedEvent(UUID player, ActiveTags before, ActiveTags after) {
        super(true);
        this.player = player;
        this.before = before;
        this.after = after;
    }

    public UUID player() {
        return player;
    }

    public ActiveTags before() {
        return before;
    }

    public ActiveTags after() {
        return after;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
