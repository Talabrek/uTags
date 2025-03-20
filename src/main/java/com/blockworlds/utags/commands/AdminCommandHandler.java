package com.blockworlds.utags.commands;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.MessageUtils;
import com.blockworlds.utags.utils.PermissionUtils;
import com.blockworlds.utags.utils.ValidationUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command handler for admin commands in the uTags plugin.
 * Handles commands like creating, deleting, and editing tags, as well as managing tag requests.
 */
public class AdminCommandHandler extends AbstractCommandHandler {
    
    private static final String PERM_ADMIN = "utags.admin";
    
    /**
     * Creates a new AdminCommandHandler.
     *
     * @param plugin The uTags plugin instance
     */
    public AdminCommandHandler(uTags plugin) {
        super(plugin);
    }
    
    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        
        if (player == null) {
            MessageUtils.sendError(sender, "This command can only be used by players.");
            return false;
        }
        
        if (!PermissionUtils.hasAdminPermission(player)) {
            MessageUtils.sendError(player, "You do not have permission to use admin commands.");
            return false;
        }
        
        if (args.length < 1) {
            displayAdminUsage(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        
        switch (subCommand) {
            case "create":
                return handleCreateCommand(player, subArgs);
            case "delete":
                return handleDeleteCommand(player, subArgs);
            case "edit":
                return handleEditCommand(player, subArgs);
            case "purge":
                return handlePurgeCommand(player, subArgs);
            case "requests":
                return handleRequestsCommand(player);
            default:
                displayAdminUsage(player);
                return true;
        }
    }
    
    /**
     * Handles the create tag command.
     *
     * @param player The player executing the command
     * @param args The command arguments
     * @return True if the command was handled successfully, false otherwise
     */
    private boolean handleCreateCommand(Player player, String[] args) {
        if (args.length != 4) {
            MessageUtils.sendError(player, "Usage: /tag admin create [name] [display] [type] [weight]");
            return false;
        }
        
        String name = args[0];
        String display = MessageUtils.colorize(args[1]);
        String typeString = args[2].toUpperCase();
        int weight;
        
        // Validate input
        if (!ValidationUtils.isValidTagName(name)) {
            MessageUtils.sendError(player, "Invalid tag [name]. It should contain only letters, numbers, underscores, and hyphens.");
            return false;
        }
        
        try {
            weight = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            MessageUtils.sendError(player, "Invalid weight value. Please use a number.");
            return false;
        }
        
        TagType type;
        try {
            type = TagType.valueOf(typeString);
        } catch (IllegalArgumentException e) {
            MessageUtils.sendError(player, "Invalid tag [type]. It should be one of 'PREFIX', 'SUFFIX', or 'BOTH'.");
            return false;
        }
        
        if (weight < 0) {
            MessageUtils.sendError(player, "Invalid [weight] value. It should be a number greater than or equal to 0.");
            return false;
        }
        
        // Get the material from player's hand
        ItemStack material = player.getInventory().getItemInMainHand();
        
        // Handle player head with custom texture
        if (material.getType() == Material.PLAYER_HEAD && material.getItemMeta() instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta) material.getItemMeta();
            if (skullMeta.hasOwner()) {
                material.setItemMeta(skullMeta);
            }
        }
        
        if (material.getType() == Material.AIR) {
            MessageUtils.sendError(player, "Invalid item for tag display. Hold an item in your hand.");
            return false;
        }
        
        // Create the tag
        Tag newTag = new Tag(name, display, type, Boolean.TRUE, Boolean.TRUE, material, weight);
        if (plugin.addTagToDatabase(newTag)) {
            MessageUtils.sendSuccess(player, "Tag '" + name + "' - " + display + " has been created.");
            return true;
        } else {
            MessageUtils.sendError(player, "An error occurred while creating the tag. Please check the console for details.");
            return false;
        }
    }
    
    /**
     * Handles the delete tag command.
     *
     * @param player The player executing the command
     * @param args The command arguments
     * @return True if the command was handled successfully, false otherwise
     */
    private boolean handleDeleteCommand(Player player, String[] args) {
        if (args.length != 1) {
            MessageUtils.sendError(player, "Usage: /tag admin delete [name]");
            return false;
        }
        
        String name = args[0];
        
        if (plugin.deleteTagFromDatabase(name)) {
            MessageUtils.sendSuccess(player, "Tag '" + name + "' has been deleted.");
            return true;
        } else {
            MessageUtils.sendError(player, "An error occurred while deleting the tag. It may not exist.");
            return false;
        }
    }
    
    /**
     * Handles the edit tag command.
     *
     * @param player The player executing the command
     * @param args The command arguments
     * @return True if the command was handled successfully, false otherwise
     */
    private boolean handleEditCommand(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtils.sendError(player, "Usage: /tag admin edit [tagname] [attribute] [newvalue]");
            return false;
        }
        
        String tagName = args[0];
        String attribute = args[1];
        String newValue = args[2];
        
        // Validate attribute name
        if (!ValidationUtils.isValidDatabaseAttribute(attribute)) {
            MessageUtils.sendError(player, "Invalid attribute. Valid attributes are: name, display, type, public, color, weight");
            return false;
        }
        
        // Validate specific attribute values
        if (attribute.equals("type")) {
            try {
                TagType.valueOf(newValue.toUpperCase());
            } catch (IllegalArgumentException e) {
                MessageUtils.sendError(player, "Invalid tag type. It should be one of 'PREFIX', 'SUFFIX', or 'BOTH'.");
                return false;
            }
        } else if (attribute.equals("public") || attribute.equals("color")) {
            if (!ValidationUtils.isValidBoolean(newValue)) {
                MessageUtils.sendError(player, "Invalid value for " + attribute + ". It should be 'true' or 'false'.");
                return false;
            }
        } else if (attribute.equals("weight")) {
            if (!ValidationUtils.isValidNumber(newValue)) {
                MessageUtils.sendError(player, "Invalid weight value. It should be a number.");
                return false;
            }
        }
        
        if (plugin.editTagAttribute(tagName, attribute, newValue)) {
            MessageUtils.sendSuccess(player, "Successfully edited the tag attribute.");
            return true;
        } else {
            MessageUtils.sendError(player, "Failed to edit the tag. Please check the tag name, attribute, and new value.");
            return false;
        }
    }
    
    /**
     * Handles the purge command.
     *
     * @param player The player executing the command
     * @param args The command arguments
     * @return True if the command was handled successfully, false otherwise
     */
    private boolean handlePurgeCommand(Player player, String[] args) {
        if (args.length != 1) {
            MessageUtils.sendError(player, "Usage: /tag admin purge [tags|requests]");
            return false;
        }
        
        String purgeType = args[0].toLowerCase();
        
        switch (purgeType) {
            case "tags":
                plugin.purgeTagsTable();
                MessageUtils.sendWarning(player, "All data has been purged from the tags table.");
                return true;
            case "requests":
                plugin.purgeRequestsTable();
                MessageUtils.sendWarning(player, "All data has been purged from the requests table.");
                return true;
            default:
                MessageUtils.sendError(player, "Invalid purge option. Use 'tags' or 'requests'.");
                return false;
        }
    }
    
    /**
     * Handles the tag requests command.
     *
     * @param player The player executing the command
     * @return True if the command was handled successfully, false otherwise
     */
    private boolean handleRequestsCommand(Player player) {
        plugin.openRequestsMenu(player);
        return true;
    }
    
    /**
     * Displays the admin command usage to a player.
     *
     * @param player The player to display usage to
     */
    private void displayAdminUsage(Player player) {
        MessageUtils.sendInfo(player, "Admin Commands:");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin create [name] [display] [type] [weight]");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin delete [name]");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin edit [tagname] [attribute] [newvalue]");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin purge [tags|requests]");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin requests");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> suggestions = new ArrayList<>();
        
        Player player = asPlayer(sender);
        if (player == null || !PermissionUtils.hasAdminPermission(player)) {
            return suggestions;
        }
        
        if (args.length == 1) {
            suggestions.add("create");
            suggestions.add("delete");
            suggestions.add("edit");
            suggestions.add("purge");
            suggestions.add("requests");
            return filterSuggestions(suggestions, args[0]);
        }
        
        String subCommand = args[0].toLowerCase();
        
        if (args.length >= 2) {
            switch (subCommand) {
                case "create":
                    return getCreateTabCompletions(player, args);
                case "delete":
                    if (args.length == 2) {
                        for (Tag tag : plugin.getAvailableTags(null)) {
                            suggestions.add(tag.getName());
                        }
                    }
                    break;
                case "edit":
                    return getEditTabCompletions(player, args);
                case "purge":
                    if (args.length == 2) {
                        suggestions.add("tags");
                        suggestions.add("requests");
                    }
                    break;
            }
        }
        
        return filterSuggestions(suggestions, args[args.length - 1]);
    }
    
    /**
     * Gets tab completion suggestions for the create command.
     *
     * @param player The player using tab completion
     * @param args The command arguments
     * @return A list of tab completion suggestions
     */
    private List<String> getCreateTabCompletions(Player player, String[] args) {
        List<String> suggestions = new ArrayList<>();
        
        if (args.length == 2) {
            suggestions.add("TagName");
        } else if (args.length == 3) {
            suggestions.add("&c[DisplayTag]");
        } else if (args.length == 4) {
            suggestions.add("PREFIX");
            suggestions.add("SUFFIX");
            suggestions.add("BOTH");
        } else if (args.length == 5) {
            suggestions.add("100");
        }
        
        return filterSuggestions(suggestions, args[args.length - 1]);
    }
    
    /**
     * Gets tab completion suggestions for the edit command.
     *
     * @param player The player using tab completion
     * @param args The command arguments
     * @return A list of tab completion suggestions
     */
    private List<String> getEditTabCompletions(Player player, String[] args) {
        List<String> suggestions = new ArrayList<>();
        
        if (args.length == 2) {
            for (Tag tag : plugin.getAvailableTags(null)) {
                suggestions.add(tag.getName());
            }
        } else if (args.length == 3) {
            suggestions.add("display");
            suggestions.add("type");
            suggestions.add("public");
            suggestions.add("color");
            suggestions.add("weight");
        } else if (args.length == 4) {
            String attribute = args[2].toLowerCase();
            if ("type".equals(attribute)) {
                suggestions.add("PREFIX");
                suggestions.add("SUFFIX");
                suggestions.add("BOTH");
            } else if ("public".equals(attribute) || "color".equals(attribute)) {
                suggestions.add("true");
                suggestions.add("false");
            } else if ("weight".equals(attribute)) {
                suggestions.add("100");
                suggestions.add("200");
                suggestions.add("300");
            }
        }
        
        return filterSuggestions(suggestions, args[args.length - 1]);
    }
}
