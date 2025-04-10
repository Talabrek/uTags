package com.blockworlds.utags;

import org.bukkit.ChatColor;
import java.util.UUID;

/**
 * Stores a player's custom color preferences for a specific tag.
 */
public class PlayerTagColorPreference {

    // Fields for tag-specific colors (original purpose)
    private final UUID playerUuid;
    private final String tagName;
    private ChatColor bracketColor;
    private ChatColor contentColor;

    // Removed plugin field - logic moved to uTags.java

    /**
     * Constructor for PlayerTagColorPreference.
     * Initializes with default colors (null, indicating no override).
     *
     * @param playerUuid The UUID of the player.
     * @param tagName    The name of the tag being customized.
     */
    public PlayerTagColorPreference(UUID playerUuid, String tagName) {
        this.playerUuid = playerUuid;
        this.tagName = tagName;
        this.bracketColor = null; // Default: Use tag's original color
        this.contentColor = null; // Default: Use tag's original color
        // Removed leftover plugin initialization
    }

    /**
     * Constructor allowing initial color setting.
     *
     * @param playerUuid    The UUID of the player.
     * @param tagName       The name of the tag being customized.
     * @param bracketColor  The initial bracket color (can be null).
     * @param contentColor  The initial content color (can be null).
     */
    public PlayerTagColorPreference(UUID playerUuid, String tagName, ChatColor bracketColor, ChatColor contentColor) {
        this.playerUuid = playerUuid;
        this.tagName = tagName;
        this.bracketColor = bracketColor;
        this.contentColor = contentColor;
        // Removed plugin initialization
    }

    // Removed constructor specific to plugin injection

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getTagName() {
        return tagName;
    }

    public ChatColor getBracketColor() {
        return bracketColor;
    }

    public void setBracketColor(ChatColor bracketColor) {
        this.bracketColor = bracketColor;
    }

    public ChatColor getContentColor() {
        return contentColor;
    }

    // Removed setPlayerNameColor method - logic moved to uTags.java

    // Removed getPlayerNameColor method - logic moved to uTags.java



    public void setContentColor(ChatColor contentColor) {
        this.contentColor = contentColor;
    }

    /**
     * Checks if this preference object represents the default state (no custom colors).
     * @return true if both bracket and content colors are null, false otherwise.
     */
    public boolean isDefault() {
        return bracketColor == null && contentColor == null;
    }
}