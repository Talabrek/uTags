package com.blockworlds.utags.services;

import com.blockworlds.utags.CustomTagRequest;
import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.ErrorHandler;
import com.blockworlds.utags.utils.MessageUtils;
import com.blockworlds.utags.utils.ValidationUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Service class for tag-related operations in the uTags plugin.
 * Centralizes business logic for working with tags and LuckPerms.
 */
public class TagService {

    private final uTags plugin;
    private final LuckPerms luckPerms;
    private final ErrorHandler errorHandler;

    /**
     * Creates a new TagService.
     *
     * @param plugin The uTags plugin instance
     * @param errorHandler The error handler to use
     */
    public TagService(uTags plugin, ErrorHandler errorHandler) {
        this.plugin = plugin;
        this.luckPerms = plugin.getLuckPerms();
        this.errorHandler = errorHandler;
    }

    /**
     * Gets all available tags of the specified type.
     *
     * @param tagType The type of tags to retrieve, or null for all tags
     * @return A list of available tags
     */
    public List<Tag> getAvailableTags(TagType tagType) {
        return plugin.getAvailableTags(tagType);
    }

    /**
     * Adds a tag to the database.
     *
     * @param tag The tag to add
     * @return True if successful, false otherwise
     */
    public boolean addTagToDatabase(Tag tag) {
        try {
            boolean result = plugin.addTagToDatabase(tag);
            if (result) {
                errorHandler.logInfo("Tag '" + tag.getName() + "' added to database");
            } else {
                errorHandler.logWarning("Failed to add tag '" + tag.getName() + "' to database");
            }
            return result;
        } catch (Exception e) {
            errorHandler.logError("Error adding tag to database", e);
            return false;
        }
    }

    /**
     * Deletes a tag from the database.
     *
     * @param tagName The name of the tag to delete
     * @return True if successful, false otherwise
     */
    public boolean deleteTagFromDatabase(String tagName) {
        try {
            boolean result = plugin.deleteTagFromDatabase(tagName);
            if (result) {
                errorHandler.logInfo("Tag '" + tagName + "' deleted from database");
            } else {
                errorHandler.logWarning("Failed to delete tag '" + tagName + "' from database (may not exist)");
            }
            return result;
        } catch (Exception e) {
            errorHandler.logError("Error deleting tag from database", e);
            return false;
        }
    }

    /**
     * Edits a tag attribute in the database.
     *
     * @param tagName The name of the tag to edit
     * @param attribute The attribute to edit
     * @param newValue The new value for the attribute
     * @return True if successful, false otherwise
     */
    public boolean editTagAttribute(String tagName, String attribute, String newValue) {
        if (!ValidationUtils.isValidDatabaseAttribute(attribute)) {
            errorHandler.logWarning("Invalid attribute name attempted: " + attribute);
            return false;
        }

        try {
            boolean result = plugin.editTagAttribute(tagName, attribute, newValue);
            if (result) {
                errorHandler.logInfo("Tag '" + tagName + "' attribute '" + attribute + "' updated to '" + newValue + "'");
            } else {
                errorHandler.logWarning("Failed to update tag '" + tagName + "' attribute '" + attribute + "'");
            }
            return result;
        } catch (Exception e) {
            errorHandler.logError("Error editing tag attribute", e);
            return false;
        }
    }

    /**
     * Sets a player's tag in LuckPerms.
     *
     * @param player The player to set the tag for
     * @param tagDisplay The display text for the tag
     * @param tagType The type of tag (PREFIX/SUFFIX)
     * @return True if successful, false otherwise
     */
    public boolean setPlayerTag(Player player, String tagDisplay, TagType tagType) {
        if (player == null || tagDisplay == null) {
            return false;
        }

        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                errorHandler.logWarning("Failed to get LuckPerms user for " + player.getName());
                return false;
            }

            if (tagType == TagType.PREFIX) {
                user.data().clear(NodeType.PREFIX.predicate());
                user.data().add(PrefixNode.builder(tagDisplay, 10000).build());
            } else {
                user.data().clear(NodeType.SUFFIX.predicate());
                user.data().add(SuffixNode.builder(tagDisplay, 10000).build());
            }

            luckPerms.getUserManager().saveUser(user);
            return true;
        } catch (Exception e) {
            errorHandler.logError("Error setting player tag", e);
            return false;
        }
    }

    /**
     * Gets a tag's name by its display text.
     *
     * @param display The display text
     * @return The tag name, or null if not found
     */
    public String getTagNameByDisplay(String display) {
        return plugin.getTagNameByDisplay(display);
    }

    /**
     * Gets a tag's display text by its name.
     *
     * @param name The tag name
     * @return The tag display text, or null if not found
     */
    public String getTagDisplayByName(String name) {
        return plugin.getTagDisplayByName(name);
    }

    /**
     * Creates a custom tag request.
     *
     * @param player The player making the request
     * @param tagDisplay The requested tag display
     * @return True if successful, false otherwise
     */
    public boolean createCustomTagRequest(Player player, String tagDisplay) {
        if (player == null || tagDisplay == null) {
            return false;
        }

        // Normalize the tag display
        tagDisplay = ValidationUtils.normalizeTagString(tagDisplay);

        try {
            boolean success = plugin.createCustomTagRequest(player, tagDisplay);
            if (success) {
                MessageUtils.sendSuccess(player, "Your tag request has been submitted!");
                
                // Notify staff about the new request
                notifyStaffAboutRequest(player);
            } else {
                MessageUtils.sendError(player, "An error occurred while submitting your tag request.");
            }
            return success;
        } catch (Exception e) {
            errorHandler.logError("Error creating custom tag request", e);
            MessageUtils.sendError(player, "An error occurred while submitting your tag request.");
            return false;
        }
    }

    /**
     * Notifies online staff members about a new tag request.
     *
     * @param requester The player who made the request
     */
    private void notifyStaffAboutRequest(Player requester) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("utags.staff")) {
                    MessageUtils.sendInfo(player, "New tag request from " + requester.getName() + "! Use /tag admin requests to review.");
                }
            }
        });
    }

    /**
     * Counts the number of custom tags a player has.
     *
     * @param playerName The name of the player
     * @return The number of custom tags
     */
    public int countCustomTags(String playerName) {
        return plugin.countCustomTags(playerName);
    }

    /**
     * Gets all custom tag requests.
     *
     * @return A list of custom tag requests
     */
    public List<CustomTagRequest> getCustomTagRequests() {
        return plugin.getCustomTagRequests();
    }

    /**
     * Gets a custom tag request by player name.
     *
     * @param playerName The name of the player
     * @return The custom tag request, or null if not found
     */
    public CustomTagRequest getCustomTagRequestByPlayerName(String playerName) {
        return plugin.getCustomTagRequestByPlayerName(playerName);
    }

    /**
     * Accepts a custom tag request.
     *
     * @param request The request to accept
     * @return True if successful, false otherwise
     */
    public boolean acceptCustomTagRequest(CustomTagRequest request) {
        try {
            String permission = "utags.tag." + request.getPlayerName() + (countCustomTags(request.getPlayerName()) + 1);
            
            // Add the new tag to the tags table
            Tag newTag = new Tag(
                request.getPlayerName() + (countCustomTags(request.getPlayerName()) + 1),
                request.getTagDisplay(),
                TagType.PREFIX,
                false,
                false,
                new ItemStack(Material.PLAYER_HEAD),
                1
            );
            
            plugin.addTagToDatabase(newTag);
            
            // Remove the request
            boolean removed = plugin.getDatabaseManager().removeCustomTagRequest(request.getId());
            if (!removed) {
                errorHandler.logWarning("Failed to remove custom tag request with ID: " + request.getId());
                return false;
            }
            
            // Add the permission to the player
            luckPerms.getUserManager().loadUser(request.getPlayerUuid()).thenAcceptAsync(user -> {
                user.data().add(Node.builder(permission).build());
                luckPerms.getUserManager().saveUser(user);
                
                // Execute the configured command to notify the player
                String command = plugin.getConfig().getString("accept-command", "mail send %player% Your custom tag request has been accepted!");
                command = command.replace("%player%", request.getPlayerName());
                String finalCommand = command;
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
            });
            
            return true;
        } catch (Exception e) {
            errorHandler.logError("Error accepting custom tag request", e);
            return false;
        }
    }

    /**
     * Denies a custom tag request.
     *
     * @param request The request to deny
     * @return True if successful, false otherwise
     */
    public boolean denyCustomTagRequest(CustomTagRequest request) {
        try {
            if (plugin.getDatabaseManager().removeCustomTagRequest(request.getId())) {
                // Execute the configured command to notify the player
                String command = plugin.getConfig().getString("deny-command", "mail send %player% Your custom tag request has been denied.");
                command = command.replace("%player%", request.getPlayerName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                return true;
            } else {
                errorHandler.logWarning("Failed to remove custom tag request with ID: " + request.getId());
                return false;
            }
        } catch (Exception e) {
            errorHandler.logError("Error denying custom tag request", e);
            return false;
        }
    }

    /**
     * Purges all tags from the database.
     *
     * @return True if successful, false otherwise
     */
    public boolean purgeTagsTable() {
        try {
            boolean result = plugin.purgeTagsTable();
            if (result) {
                errorHandler.logWarning("Tags table purged");
            } else {
                errorHandler.logError("Failed to purge tags table");
            }
            return result;
        } catch (Exception e) {
            errorHandler.logError("Error purging tags table", e);
            return false;
        }
    }

    /**
     * Purges all tag requests from the database.
     *
     * @return True if successful, false otherwise
     */
    public boolean purgeRequestsTable() {
        try {
            boolean result = plugin.purgeRequestsTable();
            if (result) {
                errorHandler.logWarning("Tag requests table purged");
            } else {
                errorHandler.logError("Failed to purge tag requests table");
            }
            return result;
        } catch (Exception e) {
            errorHandler.logError("Error purging tag requests table", e);
            return false;
        }
    }
}
