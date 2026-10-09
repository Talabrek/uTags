package com.blockworlds.utags.api;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A grant was written (not fired on a replay or for UNKNOWN_TAG).
 * Fired off the main thread, after the change is committed; listeners must not block for long.
 */
public final class UTagGrantedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final String tagId;
    private final String source;
    private final String opId;
    private final GrantOutcome outcome;

    public UTagGrantedEvent(UUID player, String tagId, String source, String opId, GrantOutcome outcome) {
        super(true);
        this.player = player;
        this.tagId = tagId;
        this.source = source;
        this.opId = opId;
        this.outcome = outcome;
    }

    public UUID player() {
        return player;
    }

    public String tagId() {
        return tagId;
    }

    public String source() {
        return source;
    }

    public String opId() {
        return opId;
    }

    public GrantOutcome outcome() {
        return outcome;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
