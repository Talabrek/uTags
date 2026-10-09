package com.blockworlds.utags.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * The Pro pack's files changed (a badge was added, edited or removed); rebuild a server pack that includes them.
 * Fired off the main thread, after the change is committed; listeners must not block for long.
 */
public final class UTagsPackChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String contentHash;

    public UTagsPackChangedEvent(String contentHash) {
        super(true);
        this.contentHash = contentHash;
    }

    public String contentHash() {
        return contentHash;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
