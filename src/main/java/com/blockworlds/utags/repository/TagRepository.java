package com.blockworlds.utags.repository;

import com.blockworlds.utags.model.Result;
import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;

import java.util.List;

/**
 * Repository interface for Tag data access operations.
 */
public interface TagRepository {
    /** Gets all available tags of the specified type */
    Result<List<Tag>> getAvailableTags(TagType tagType);

    /** Adds a tag to the database */
    Result<Boolean> addTag(Tag tag);

    /** Deletes a tag from the database */
    Result<Boolean> deleteTag(String tagName);

    /** Edits a tag attribute */
    Result<Boolean> editTagAttribute(String tagName, String attribute, String newValue);

    /** Gets a tag's name by its display text */
    Result<String> getTagNameByDisplay(String display);

    /** Gets a tag's display text by its name */
    Result<String> getTagDisplayByName(String name);

    /** Counts the number of custom tags for a player */
    Result<Integer> countCustomTags(String playerName);

    /** Purges all tags from the database */
    Result<Boolean> purgeTagsTable();
}
