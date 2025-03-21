package com.blockworlds.utags.model;

import java.util.UUID;

/**
 * Represents a request for a custom tag.
 */
public class CustomTagRequest {
    private final int id;
    private final UUID playerUuid;
    private final String playerName;
    private final String tagDisplay;

    /**
     * Creates a new custom tag request.
     */
    public CustomTagRequest(int id, UUID playerUuid, String playerName, String tagDisplay) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.tagDisplay = tagDisplay;
    }

    /** Returns the unique ID of this request */
    public int getId() { return id; }
    
    /** Returns the UUID of the player who made the request */
    public UUID getPlayerUuid() { return playerUuid; }
    
    /** Returns the name of the player who made the request */
    public String getPlayerName() { return playerName; }
    
    /** Returns the requested tag display text */
    public String getTagDisplay() { return tagDisplay; }
}
