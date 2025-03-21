package com.blockworlds.utags.service.impl;

import com.blockworlds.utags.model.Result;
import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.repository.TagRepository;
import com.blockworlds.utags.service.TagService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of TagService interface.
 */
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final LuckPerms luckPerms;
    private final Logger logger;

    /**
     * Creates a new TagServiceImpl.
     */
    public TagServiceImpl(TagRepository tagRepository, LuckPerms luckPerms, Logger logger) {
        this.tagRepository = tagRepository;
        this.luckPerms = luckPerms;
        this.logger = logger;
    }

    @Override
    public Result<List<Tag>> getAvailableTags(TagType tagType) {
        try {
            return tagRepository.getAvailableTags(tagType);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error retrieving available tags", e);
            return Result.error("Failed to retrieve tags: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Tag> getTagByName(String name) {
        if (name == null || name.isEmpty()) {
            return Result.failure("Tag name cannot be empty");
        }
        
        try {
            Result<List<Tag>> tagsResult = tagRepository.getAvailableTags(null);
            if (!tagsResult.isSuccess()) {
                return Result.failure(tagsResult.getMessage());
            }
            
            for (Tag tag : tagsResult.getValue()) {
                if (name.equals(tag.getName())) {
                    return Result.success(tag);
                }
            }
            
            return Result.failure("Tag not found: " + name);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error retrieving tag by name", e);
            return Result.error("Failed to retrieve tag: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Tag> getTagByDisplay(String display) {
        if (display == null || display.isEmpty()) {
            return Result.failure("Tag display cannot be empty");
        }
        
        try {
            Result<String> nameResult = tagRepository.getTagNameByDisplay(display);
            if (!nameResult.isSuccess()) {
                return Result.failure(nameResult.getMessage());
            }
            
            return getTagByName(nameResult.getValue());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error retrieving tag by display", e);
            return Result.error("Failed to retrieve tag: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Boolean> addTag(Tag tag) {
        if (tag == null) {
            return Result.failure("Tag cannot be null");
        }
        
        try {
            return tagRepository.addTag(tag);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error adding tag", e);
            return Result.error("Failed to add tag: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Boolean> deleteTag(String tagName) {
        if (tagName == null || tagName.isEmpty()) {
            return Result.failure("Tag name cannot be empty");
        }
        
        try {
            return tagRepository.deleteTag(tagName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error deleting tag", e);
            return Result.error("Failed to delete tag: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Boolean> editTagAttribute(String tagName, String attribute, String newValue) {
        if (tagName == null || tagName.isEmpty()) {
            return Result.failure("Tag name cannot be empty");
        }
        
        if (attribute == null || attribute.isEmpty()) {
            return Result.failure("Attribute name cannot be empty");
        }
        
        try {
            return tagRepository.editTagAttribute(tagName, attribute, newValue);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error editing tag attribute", e);
            return Result.error("Failed to edit tag: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Boolean> setPlayerTag(Player player, String tagDisplay, TagType tagType) {
        if (player == null) {
            return Result.failure("Player cannot be null");
        }
        
        if (tagDisplay == null || tagDisplay.isEmpty()) {
            return Result.failure("Tag display cannot be empty");
        }
        
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return Result.failure("Failed to get LuckPerms user");
            }
            
            if (tagType == TagType.PREFIX) {
                user.data().clear(NodeType.PREFIX.predicate());
                user.data().add(PrefixNode.builder(tagDisplay, 100).build());
            } else if (tagType == TagType.SUFFIX) {
                user.data().clear(NodeType.SUFFIX.predicate());
                user.data().add(SuffixNode.builder(tagDisplay, 100).build());
            } else {
                return Result.failure("Invalid tag type for player tag");
            }
            
            luckPerms.getUserManager().saveUser(user);
            return Result.success(true);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error setting player tag", e);
            return Result.error("Failed to set player tag: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Boolean> setPlayerTagByName(Player player, String tagName, TagType tagType) {
        if (player == null) {
            return Result.failure("Player cannot be null");
        }
        
        if (tagName == null || tagName.isEmpty()) {
            return Result.failure("Tag name cannot be empty");
        }
        
        try {
            Result<String> displayResult = getTagDisplayByName(tagName);
            if (!displayResult.isSuccess()) {
                return Result.failure(displayResult.getMessage());
            }
            
            return setPlayerTag(player, displayResult.getValue(), tagType);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error setting player tag by name", e);
            return Result.error("Failed to set player tag: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<String> getTagDisplayByName(String name) {
        if (name == null || name.isEmpty()) {
            return Result.failure("Tag name cannot be empty");
        }
        
        try {
            return tagRepository.getTagDisplayByName(name);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting tag display by name", e);
            return Result.error("Failed to get tag display: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<String> getTagNameByDisplay(String display) {
        if (display == null || display.isEmpty()) {
            return Result.failure("Tag display cannot be empty");
        }
        
        try {
            return tagRepository.getTagNameByDisplay(display);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting tag name by display", e);
            return Result.error("Failed to get tag name: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Integer> countCustomTags(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return Result.failure("Player name cannot be empty");
        }
        
        try {
            return tagRepository.countCustomTags(playerName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error counting custom tags", e);
            return Result.error("Failed to count custom tags: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Boolean> purgeTagsTable() {
        try {
            logger.warning("Purging all tags from database");
            return tagRepository.purgeTagsTable();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error purging tags table", e);
            return Result.error("Failed to purge tags: " + e.getMessage(), e);
        }
    }
}
