package com.blockworlds.utags.repository;

import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;

import java.util.List;

/**
 * Repository interface for Tag data access operations.
 * Follows the Repository pattern to abstract the data access layer.
 */
public interface TagRepository {
    /**
     * Gets all available tags of the specified type.
     * 
     * @param tagType The type of tags to retrieve (can be null to get all types)
     * @return A list of available tags
     */
    List<Tag> getAvailableTags(TagType tagType);

    /**
     * Adds a tag to the database.
     * 
     * @param tag The tag to add
     * @return True if successful, false otherwise
     */
    boolean addTag(Tag tag);

    /**
     * Deletes a tag from the database.
     * 
     * @param tagName The name of the tag to delete
     * @return True if successful, false otherwise
     */
    boolean deleteTag(String tagName);

    /**
     * Edits a tag attribute.
     * 
     * @param tagName The name of the tag to edit
     * @param attribute The attribute to edit
     * @param newValue The new value for the attribute
     * @return True if successful, false otherwise
     */
    boolean editTagAttribute(String tagName, String attribute, String newValue);

    /**
     * Gets a tag's name by its display text.
     * 
     * @param display The display text
     * @return The tag name, or null if not found
     */
    String getTagNameByDisplay(String display);

    /**
     * Gets a tag's display text by its name.
     * 
     * @param name The tag name
     * @return The tag display text, or null if not found
     */
    String getTagDisplayByName(String name);

    /**
     * Counts the number of custom tags for a player.
     * 
     * @param playerName The name of the player
     * @return The number of custom tags
     */
    int countCustomTags(String playerName);

    /**
     * Purges all tags from the database.
     * 
     * @return True if successful, false otherwise
     */
    boolean purgeTagsTable();
}
