package com.blockworlds.utags;

import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class TagCommand implements CommandExecutor, TabCompleter {
    private static final int LINES_PER_PAGE = 8;
    private final uTags plugin;

    public TagCommand(uTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (!(sender instanceof Player)) {
                Utils.sendError(sender, "This command can only be used by players.");
                return true;
            }
            
            Player player = (Player) sender;

            if (args.length == 0) {
                plugin.getTagMenuManager().openTagMenu(player);
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "help":
                    handleHelpCommand(player, args);
                    break;
                case "set":
                    handleSetCommand(player, args);
                    break;
                case "admin":
                    handleAdminCommands(player, args);
                    break;
                case "request":
                    handleTagRequest(player, args);
                    break;
                default:
                    Utils.sendError(player, "Invalid command. Use /tag help for a list of available commands.");
                    break;
            }
            
            return true;
        } catch (Exception e) {
            Utils.logError("Unexpected error in tag command", e);
            if (sender instanceof Player) {
                Utils.sendError(sender, "An unexpected error occurred. Please contact an administrator.");
            }
            return true;
        }
    }

    private void handleHelpCommand(Player player, String[] args) {
        int page = 1;
        
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Utils.sendError(player, "Invalid page number. Please use a number.");
                return;
            }
        }
        
        displayHelp(player, page);
    }

    private void handleSetCommand(Player player, String[] args) {
        if (args.length < 2) {
            Utils.sendError(player, "Usage: /tag set [tag]");
            return;
        }
        
        String tagName = args[1];
        
        if (!Utils.isValidTagName(tagName)) {
            Utils.sendError(player, "Invalid tag name. Tag names must contain only letters, numbers, underscores, and hyphens.");
            return;
        }

        if (!Utils.hasTagPermission(player, tagName)) {
            Utils.sendError(player, "You don't have permission to use this tag.");
            return;
        }
        
        String tagDisplay = plugin.getTagDisplayByName(tagName);
        if (tagDisplay == null) {
            Utils.sendError(player, "Tag '" + tagName + "' does not exist.");
            return;
        }
        
        Utils.logSecurityEvent(Level.INFO, player, "TAG_SET",
            "Player set their tag to " + tagName + " (" + tagDisplay + ")");
            
        boolean success = plugin.setPlayerTag(player, tagDisplay, TagType.PREFIX);
        
        if (success) {
            Utils.sendSuccess(player, "Your " + TagType.PREFIX + " has been updated to: " + 
                            ChatColor.translateAlternateColorCodes('&', tagDisplay));
        } else {
            Utils.sendError(player, "Failed to update your " + TagType.PREFIX + ".");
        }
    }

    private void handleAdminCommands(Player player, String[] args) {
        if (!Utils.hasAdminPermission(player)) {
            Utils.sendError(player, "You don't have permission to use admin commands.");
            return;
        }

        if (args.length < 2) {
            displayAdminUsage(player);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "create":
                createTag(player, Arrays.copyOfRange(args, 2, args.length));
                break;
            case "delete":
                deleteTag(player, Arrays.copyOfRange(args, 2, args.length));
                break;
            case "edit":
                editTag(player, Arrays.copyOfRange(args, 2, args.length));
                break;
            case "purge":
                handlePurgeCommand(player, Arrays.copyOfRange(args, 2, args.length));
                break;
            case "requests":
                Utils.logSecurityEvent(Level.INFO, player, "ADMIN_VIEW_REQUESTS", 
                    "Admin viewing tag requests");
                plugin.openRequestsMenu(player);
                break;
            default:
                displayAdminUsage(player);
        }
    }

    private void handleTagRequest(Player player, String[] args) {
        if (args.length < 2) {
            showTagRequestHelp(player);
            return;
        }
        
        int customTagCount = plugin.countCustomTags(player.getName());
        String requiredPermission = "utags.custom" + (customTagCount + 1);
        
        if (!player.hasPermission(requiredPermission)) {
            if (customTagCount >= 5) {
                Utils.sendError(player, "You have reached the maximum number of custom tags.");
            } else {
                Utils.sendError(player, "You don't have permission to request any more custom tags. " +
                             "Unlock more custom tag slots as a premium subscriber.");
            }
            return;
        }
        
        String requestedTag = args[1];
        String validationResult = Utils.validateTagFormat(requestedTag);
        
        if (validationResult != null) {
            Utils.sendError(player, validationResult);
            return;
        }
        
        int endIndex = requestedTag.indexOf(']') + 1;
        if (endIndex < requestedTag.length()) {
            requestedTag = requestedTag.substring(0, endIndex);
        }
        
        Utils.logSecurityEvent(Level.INFO, player, "TAG_REQUEST",
            "Player requested custom tag: " + requestedTag);
        
        Utils.sendSuccess(player, "Tag request preview: " + ChatColor.translateAlternateColorCodes('&', requestedTag));
        Utils.sendInfo(player, "Type 'accept' to confirm the tag or 'decline' to try again.");
        
        plugin.addPreviewTag(player, requestedTag);
    }

    private void handlePurgeCommand(Player player, String[] args) {
        if (args.length < 1) {
            Utils.sendError(player, "Usage: /tag admin purge [tags|requests]");
            return;
        }
        
        if (!Utils.hasAdminPermission(player)) {
            Utils.sendError(player, "You don't have permission to use this command.");
            return;
        }
        
        switch (args[0].toLowerCase()) {
            case "tags":
                Utils.logSecurityEvent(Level.WARNING, player, "ADMIN_PURGE_TAGS", 
                    "Admin purged all tags from the database");
                
                plugin.purgeTagsTable();
                Utils.sendWarning(player, "All data has been purged from the tags table.");
                break;
            case "requests":
                Utils.logSecurityEvent(Level.WARNING, player, "ADMIN_PURGE_REQUESTS", 
                    "Admin purged all tag requests from the database");
                
                plugin.purgeRequestsTable();
                Utils.sendWarning(player, "All data has been purged from the requests table.");
                break;
            default:
                Utils.sendError(player, "Invalid purge option. Use 'tags' or 'requests'.");
        }
    }

    private void displayAdminUsage(Player player) {
        Utils.sendInfo(player, "Admin Commands:");
        Utils.sendInfo(player, "/tag admin create [name] [display] [type] [weight] - Create a new tag");
        Utils.sendInfo(player, "/tag admin delete [name] - Delete an existing tag");
        Utils.sendInfo(player, "/tag admin edit [tagname] [attribute] [newvalue] - Edit an existing tag");
        Utils.sendInfo(player, "/tag admin requests - View pending custom tag requests");
        Utils.sendWarning(player, "/tag admin purge tags - Purge all tags from the database");
        Utils.sendWarning(player, "/tag admin purge requests - Purge all custom tag requests from the database");
    }

    private void createTag(Player player, String[] args) {
        if (args.length < 4) {
            Utils.sendError(player, "Usage: /tag admin create [name] [display] [type] [weight]");
            return;
        }

        String name = args[0];
        String display = ChatColor.translateAlternateColorCodes('&', args[1]);
        String typeString = args[2].toUpperCase();
        String weightStr = args[3];
        
        if (!Utils.isValidTagName(name)) {
            Utils.sendError(player, "Invalid tag name. Tag names must contain only letters, numbers, underscores, and hyphens.");
            return;
        }
        
        String displayValidation = Utils.validateTagFormat(args[1]);
        if (displayValidation != null) {
            Utils.sendError(player, displayValidation);
            return;
        }
        
        TagType type;
        try {
            type = TagType.valueOf(typeString);
        } catch (IllegalArgumentException e) {
            Utils.sendError(player, "Invalid tag type. Valid types: PREFIX, SUFFIX, BOTH");
            return;
        }
        
        int weight;
        try {
            weight = Integer.parseInt(weightStr);
            if (weight < 0) {
                Utils.sendError(player, "Weight must be a non-negative integer");
                return;
            }
        } catch (NumberFormatException e) {
            Utils.sendError(player, "Weight must be a valid integer");
            return;
        }

        ItemStack material = player.getInventory().getItemInMainHand();
        
        if (material.getType() == Material.AIR) {
            Utils.sendError(player, "Invalid item for tag display. Hold an item in your hand.");
            return;
        }

        Tag newTag = new Tag(name, display, type, true, true, material, weight);
        
        Utils.logSecurityEvent(Level.INFO, player, "ADMIN_CREATE_TAG", 
            "Admin created tag: " + name + " with display: " + display);
        
        if (plugin.addTagToDatabase(newTag)) {
            Utils.sendSuccess(player, "Tag '" + name + "' - " + display + ChatColor.GREEN + " has been created.");
        } else {
            Utils.sendError(player, "An error occurred while creating the tag. Please check the server logs.");
        }
    }

    private void deleteTag(Player player, String[] args) {
        if (args.length < 1) {
            Utils.sendError(player, "Usage: /tag admin delete [name]");
            return;
        }

        String name = args[0];
        
        if (!Utils.isValidTagName(name)) {
            Utils.sendError(player, "Invalid tag name. Tag names must contain only letters, numbers, underscores, and hyphens.");
            return;
        }
        
        Utils.logSecurityEvent(Level.WARNING, player, "ADMIN_DELETE_TAG", 
            "Admin deleted tag: " + name);
        
        if (plugin.deleteTagFromDatabase(name)) {
            Utils.sendSuccess(player, "Tag '" + name + "' has been deleted.");
        } else {
            Utils.sendError(player, "An error occurred while deleting the tag. It may not exist.");
        }
    }

    private void editTag(Player player, String[] args) {
        if (args.length < 3) {
            Utils.sendError(player, "Usage: /tag admin edit [tagname] [attribute] [newvalue]");
            return;
        }

        String tagName = args[0];
        String attribute = args[1];
        String newValue = args[2];

        if (!Utils.isValidTagName(tagName)) {
            Utils.sendError(player, "Invalid tag name. Tag names must contain only letters, numbers, underscores, and hyphens.");
            return;
        }
        
        if (!Utils.isValidAttribute(attribute)) {
            Utils.sendError(player, "Invalid attribute. Valid attributes: name, display, type, public, color, material, weight");
            return;
        }

        // Attribute-specific validation
        if (attribute.equals("display")) {
            String validation = Utils.validateTagFormat(newValue);
            if (validation != null) {
                Utils.sendError(player, validation);
                return;
            }
        } else if (attribute.equals("type")) {
            try {
                TagType.valueOf(newValue.toUpperCase());
            } catch (IllegalArgumentException e) {
                Utils.sendError(player, "Invalid tag type. Valid types: PREFIX, SUFFIX, BOTH");
                return;
            }
        } else if (attribute.equals("public") || attribute.equals("color")) {
            if (!Utils.isValidBoolean(newValue)) {
                Utils.sendError(player, "Invalid boolean value. Use 'true' or 'false'");
                return;
            }
        } else if (attribute.equals("weight")) {
            if (!Utils.isValidNumber(newValue)) {
                Utils.sendError(player, "Invalid weight. Weight must be a number");
                return;
            }
        }
        
        Utils.logSecurityEvent(Level.INFO, player, "ADMIN_EDIT_TAG", 
            "Admin edited tag: " + tagName + ", attribute: " + attribute + ", new value: " + newValue);

        if (plugin.editTagAttribute(tagName, attribute, newValue)) {
            Utils.sendSuccess(player, "Successfully edited the tag attribute.");
        } else {
            Utils.sendError(player, "Failed to edit the tag. Please check the tag name, attribute, and new value.");
        }
    }

    private void displayHelp(Player player, int page) {
        List<String> helpLines = new ArrayList<>();

        helpLines.add(ChatColor.YELLOW + "Available commands:");
        helpLines.add(ChatColor.GREEN + "/tag - Open the tag GUI menu.");
        helpLines.add(ChatColor.GREEN + "/tag set [tag]- Quick set a prefix tag.");
        helpLines.add(ChatColor.GREEN + "/tag request [tag] - Request a custom tag. (Requires Veteran or Premium Membership)");
        helpLines.add(ChatColor.GREEN + "/tag help [page] - Shows this help menu.");

        if (player.hasPermission("utags.admin")) {
            helpLines.add(ChatColor.YELLOW + "Admin commands:");
            helpLines.add(ChatColor.YELLOW + "/tag admin create [name] [display] [type] [weight] - Create a new tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin delete [name] - Delete an existing tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin edit [tagname] [attribute] [newvalue] - Edit an existing tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin requests - View pending custom tag requests.");
            helpLines.add(ChatColor.RED + "/tag admin purge tags - Purge all tags from the database.");
            helpLines.add(ChatColor.RED + "/tag admin purge requests - Purge all custom tag requests from the database.");
        }

        int totalPages = (int) Math.ceil((double) helpLines.size() / LINES_PER_PAGE);
        int startIndex = (page - 1) * LINES_PER_PAGE;
        int endIndex = Math.min(helpLines.size(), startIndex + LINES_PER_PAGE);

        if (page < 1 || page > totalPages) {
            Utils.sendError(player, "Invalid page number. Valid pages: 1-" + totalPages);
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== Help (Page " + page + " of " + totalPages + ") ===");
        for (int i = startIndex; i < endIndex; i++) {
            player.sendMessage(helpLines.get(i));
        }

        if (page < totalPages) {
            player.sendMessage(ChatColor.GOLD + "Type /tag help " + (page + 1) + " for the next page.");
        }
    }

    private void showTagRequestHelp(Player player) {
        Utils.sendInfo(player, "Usage: /tag request [YourNewTag]");
        Utils.sendInfo(player, "Tag Request Rules:");
        Utils.sendInfo(player, "1. The tag must start with a color code.");
        Utils.sendInfo(player, "2. The tag must be surrounded by square brackets ([ and ]).");
        Utils.sendInfo(player, "3. The tag must be a maximum of 15 characters long.");
        Utils.sendInfo(player, "4. The tag must not contain any formatting codes (&k, &l, etc).");
        
        showColorCodes(player);
    }
    
    private void showColorCodes(Player player) {
        ChatColor[] colors = {ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA, 
                            ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY, 
                            ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA, 
                            ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE};
        String[] colorCodes = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};
        StringBuilder colorCodeList = new StringBuilder(ChatColor.AQUA + "List of available color codes: ");
        
        for (int i = 0; i < colors.length; i++) {
            colorCodeList.append(colors[i]).append("&").append(colorCodes[i]).append(" ");
        }
        
        player.sendMessage(colorCodeList.toString().trim());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return suggestions;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            suggestions.add("request");
            suggestions.add("set");
            suggestions.add("help");
            if (player.hasPermission("utags.admin")) {
                suggestions.add("admin");
            }
        } else if (args.length == 2) {
            if ("set".equalsIgnoreCase(args[0])) {
                for (Tag tag : plugin.getAvailableTags(TagType.PREFIX)) {
                    if (player.hasPermission("utags.tag." + tag.getName())) {
                        suggestions.add(tag.getName());
                    }
                }
            } else if ("admin".equalsIgnoreCase(args[0]) && player.hasPermission("utags.admin")) {
                suggestions.add("create");
                suggestions.add("delete");
                suggestions.add("edit");
                suggestions.add("purge");
                suggestions.add("requests");
            } else if ("help".equalsIgnoreCase(args[0])) {
                suggestions.add("1");
                suggestions.add("2");
            } else if ("request".equalsIgnoreCase(args[0])) {
                suggestions.add("&c[YourTag]");
                suggestions.add("&e[CustomTag]");
                suggestions.add("&a[UniqueTag]");
            }
        } else if (args.length >= 3 && "admin".equalsIgnoreCase(args[0]) && player.hasPermission("utags.admin")) {
            if ("create".equalsIgnoreCase(args[1])) {
                if (args.length == 3) {
                    suggestions.add("TagName");
                } else if (args.length == 4) {
                    suggestions.add("&c[DisplayTag]");
                } else if (args.length == 5) {
                    suggestions.add("PREFIX");
                    suggestions.add("SUFFIX");
                    suggestions.add("BOTH");
                } else if (args.length == 6) {
                    suggestions.add("100");
                }
            } else if ("delete".equalsIgnoreCase(args[1])) {
                for (Tag tag : plugin.getAvailableTags(null)) {
                    suggestions.add(tag.getName());
                }
            } else if ("edit".equalsIgnoreCase(args[1])) {
                if (args.length == 3) {
                    for (Tag tag : plugin.getAvailableTags(null)) {
                        suggestions.add(tag.getName());
                    }
                } else if (args.length == 4) {
                    suggestions.add("display");
                    suggestions.add("type");
                    suggestions.add("public");
                    suggestions.add("color");
                    suggestions.add("weight");
                } else if (args.length == 5) {
                    String attribute = args[3].toLowerCase();
                    if ("type".equals(attribute)) {
                        suggestions.add("PREFIX");
                        suggestions.add("SUFFIX");
                        suggestions.add("BOTH");
                    } else if ("public".equals(attribute) || "color".equals(attribute)) {
                        suggestions.add("true");
                        suggestions.add("false");
                    } else if ("weight".equals(attribute)) {
                        suggestions.add("100");
                    }
                }
            } else if ("purge".equalsIgnoreCase(args[1])) {
                suggestions.add("tags");
                suggestions.add("requests");
            }
        }

        if (args.length > 0) {
            String lastArg = args[args.length - 1].toLowerCase();
            suggestions.removeIf(suggestion -> !suggestion.toLowerCase().startsWith(lastArg));
        }

        return suggestions;
    }
}
