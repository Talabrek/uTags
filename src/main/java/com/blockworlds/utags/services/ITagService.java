package com.blockworlds.utags.service;

import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Service interface for tag-related business operations.
 * Centralizes tag management logic and coordinates between repositories and controllers.
 */
public interface ITagService {
    /**
     * Gets all available tags of the specified type.
     * 
     * @param tagType The type of tags to retrieve
     * @return A list of available tags
     */
    List<Tag> getAvailableTags(TagType tagType);

    /**
     * Gets a tag by its name.
     * 
     * @param name The name of the tag
     * @return The tag, or null if not found
     */
    Tag getTagByName(String name);

    /**
     * Gets a tag by its display text.
     * 
     * @param display The display text
     * @return The tag, or null if not found
     */
    Tag getTagByDisplay(String display);

    /**
     * Creates a new tag.
     * 
     * @param tag The tag to create
     * @return True if successful, false otherwise
     */
    boolean createTag(Tag tag);

    /**
     * Deletes a tag.
     * 
     * @param tagName The name of the tag to delete
     * @return True if successful, false otherwise
     */
    boolean deleteTag(String tagName);

    /**
     * Updates a tag attribute.
     * 
     * @param tagName The name of the tag to update
     * @param attribute The attribute to update
     * @param newValue The new value for the attribute
     * @return True if successful, false otherwise
     */
    boolean updateTagAttribute(String tagName, String attribute, String newValue);

    /**
     * Sets a player's tag.
     * 
     * @param player The player to set the tag for
     * @param tagDisplay The display text for the tag
     * @param tagType The type of tag (PREFIX/SUFFIX)
     * @return True if successful, false otherwise
     */
    boolean setPlayerTag(Player player, String tagDisplay, TagType tagType);

    /**
     * Sets a player's tag.
     * 
     * @param player The player to set the tag for
     * @param tagName The name of the tag
     * @param tagType The type of tag (PREFIX/SUFFIX)
     * @return True if successful, false otherwise
     */
    boolean setPlayerTagByName(Player player, String tagName, TagType tagType);

    /**
     * Gets the display text for a tag.
     * 
     * @param tagName The name of the tag
     * @return The display text, or null if not found
     */
    String getTagDisplay(String tagName);

    /**
     * Gets the name of a tag from its display text.
     * 
     * @param display The display text
     * @return The tag name, or null if not found
     */
    String getTagName(String display);

    /**
     * Counts the number of custom tags for a player.
     * 
     * @param playerName The name of the player
     * @return The number of custom tags
     */
    int countCustomTags(String playerName);

    /**
     * Purges all tags.
     * 
     * @return True if successful, false otherwise
     */
    boolean purgeTags();
}
