package com.blockworlds.utags.service;

import com.blockworlds.utags.model.Result;
import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Service interface for tag-related business operations.
 */
public interface TagService {
    /**
     * Gets all available tags of the specified type.
     */
    Result<List<Tag>> getAvailableTags(TagType tagType);
    
    /**
     * Gets a tag by its name.
     */
    Result<Tag> getTagByName(String name);
    
    /**
     * Gets a tag by its display text.
     */
    Result<Tag> getTagByDisplay(String display);
    
    /**
     * Adds a new tag.
     */
    Result<Boolean> addTag(Tag tag);
    
    /**
     * Deletes a tag.
     */
    Result<Boolean> deleteTag(String tagName);
    
    /**
     * Edits a tag attribute.
     */
    Result<Boolean> editTagAttribute(String tagName, String attribute, String newValue);
    
    /**
     * Sets a player's tag.
     */
    Result<Boolean> setPlayerTag(Player player, String tagDisplay, TagType tagType);
    
    /**
     * Sets a player's tag by name.
     */
    Result<Boolean> setPlayerTagByName(Player player, String tagName, TagType tagType);
    
    /**
     * Gets a tag's display text by its name.
     */
    Result<String> getTagDisplayByName(String name);
    
    /**
     * Gets a tag's name by its display text.
     */
    Result<String> getTagNameByDisplay(String display);
    
    /**
     * Counts the number of custom tags for a player.
     */
    Result<Integer> countCustomTags(String playerName);
    
    /**
     * Purges all tags.
     */
    Result<Boolean> purgeTagsTable();
}
