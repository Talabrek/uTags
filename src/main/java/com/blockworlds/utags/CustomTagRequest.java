package com.blockworlds.utags;

import java.util.UUID;

public class CustomTagRequest {
    private final int id;
    private final UUID playerUuid;
    private final String playerName;
    private final String tagDisplay;

    public CustomTagRequest(int id, UUID playerUuid, String playerName, String tagDisplay) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.tagDisplay = tagDisplay;
    }

    public int getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getTagDisplay() {
        return tagDisplay;
    }
}