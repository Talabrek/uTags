package com.blockworlds.utags;

import com.blockworlds.utags.services.SecurityService;
import com.blockworlds.utags.services.ValidationService;
import com.blockworlds.utags.services.ValidationService.ValidationResult;
import com.blockworlds.utags.utils.ErrorHandler;
import com.blockworlds.utags.utils.MessageUtils;
import com.blockworlds.utags.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * Handles all tag-related commands for the uTags plugin with enhanced security
 */
public class TagCommand implements CommandExecutor, TabCompleter {
    private static final int LINES_PER_PAGE = 8;
    private final uTags plugin;
    private final ErrorHandler errorHandler;
    private final SecurityService securityService;
    private final ValidationService validationService;

    public TagCommand(uTags plugin, ErrorHandler errorHandler, SecurityService securityService, ValidationService validationService) {
        this.plugin = plugin;
        this.errorHandler = errorHandler;
        this.securityService = securityService;
        this.validationService = validationService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            // Validate sender is a player
            if (!(sender instanceof Player)) {
                MessageUtils.sendError(sender, "This command can only be used by players.");
                return true;
            }
            
            Player player = (Player) sender;

            // Basic command with no args - open tag menu
            if (args.length == 0) {
                plugin.getTagMenuManager().openTagMenu(player);
                return true;
            }

            // Handle different subcommands with proper validation and security checks
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
                    
                case "encrypt":
                    // Special command to encrypt database password (admin only)
                    if (securityService.checkAdmin(player, "encrypt database password")) {
                        boolean success = plugin.getConfigurationManager().encryptDatabasePassword(
                            plugin.getConfigurationManager().getDatabasePassword()
                        );
                        if (success) {
                            MessageUtils.sendSuccess(player, "Database password has been encrypted in the configuration.");
                        } else {
                            MessageUtils.sendError(player, "Failed to encrypt database password.");
                        }
                    }
                    break;
                
                default:
                    MessageUtils.sendError(player, "Invalid command. Use /tag help for a list of available commands.");
                    break;
            }
            
            return true;
        } catch (Exception e) {
            // Global exception handler for unexpected errors
            errorHandler.logError("Unexpected error in tag command", e);
            if (sender instanceof Player) {
                MessageUtils.sendError(sender, "An unexpected error occurred. Please contact an administrator.");
            }
            return true;
        }
    }

    /**
     * Handles the help command with improved pagination
     */
    private void handleHelpCommand(Player player, String[] args) {
        int page = 1;
        
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                MessageUtils.sendError(player, "Invalid page number. Please use a number.");
                return;
            }
        }
        
        displayHelp(player, page);
    }

    /**
     * Handles the set command with security and validation
     */
    private void handleSetCommand(Player player, String[] args) {
        // Validate args
        ValidationResult argsResult = validationService.validateCommandArgs(args, 2, 2);
        if (!argsResult.isValid()) {
            MessageUtils.sendError(player, argsResult.getErrorMessage());
            MessageUtils.sendError(player, "Usage: /tag set [tag]");
            return;
        }
        
        String tagName = args[1];
        
        // Validate tag name
        ValidationResult tagResult = validationService.validateTagName(tagName, player);
        if (!tagResult.isValid()) {
            MessageUtils.sendError(player, tagResult.getErrorMessage());
            return;
        }

        // Check permission for this tag
        if (!securityService.checkTagAccess(player, tagName)) {
            return; // Security service already logs this
        }
        
        // Get tag display from name
        String tagDisplay = plugin.getTagDisplayByName(tagName);
        if (tagDisplay == null) {
            MessageUtils.sendError(player, "Tag '" + tagName + "' does not exist.");
            return;
        }
        
        // Log the action
        securityService.logSecurityEvent(Level.INFO, player, "TAG_SET",
            "Player set their tag to " + tagName + " (" + tagDisplay + ")");
            
        // Set the tag
        boolean success = plugin.setPlayerTag(player, tagDisplay, TagType.PREFIX);
        
        if (success) {
            MessageUtils.sendSuccess(player, "Your " + TagType.PREFIX + " has been updated to: " + 
                                    ChatColor.translateAlternateColorCodes('&', tagDisplay));
        } else {
            MessageUtils.sendError(player, "Failed to update your " + TagType.PREFIX + ".");
        }
    }

    /**
     * Handles admin commands with enhanced security checks
     */
    private void handleAdminCommands(Player player, String[] args) {
        // Check admin permission
        if (!securityService.checkAdmin(player, "command")) {
            return; // Security service already logs this
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
                // Log this admin action
                securityService.logSecurityEvent(Level.INFO, player, "ADMIN_VIEW_REQUESTS", 
                    "Admin viewing tag requests");
                plugin.openRequestsMenu(player);
                break;
            default:
                displayAdminUsage(player);
        }
    }

    /**
     * Handles tag request command with improved validation
     */
    private void handleTagRequest(Player player, String[] args) {
        // Validate arguments
        ValidationResult argsResult = validationService.validateCommandArgs(args, 2, 2);
        if (!argsResult.isValid()) {
            showTagRequestHelp(player);
            return;
        }
        
        // Get the next available custom tag slot
        int customTagCount = plugin.countCustomTags(player.getName());
        String requiredPermission = "utags.custom" + (customTagCount + 1);
        
        // Check if player has permission for the next slot
        if (!securityService.checkPermission(player, requiredPermission, "request custom tag")) {
            if (customTagCount >= 5) {
                MessageUtils.sendError(player, "You have reached the maximum number of custom tags.");
            } else {
                MessageUtils.sendError(player, "You don't have permission to request any more custom tags. " +
                                     "Unlock more custom tag slots as a premium subscriber.");
            }
            return;
        }
        
        String requestedTag = args[1];
        
        // Validate tag format with detailed feedback
        ValidationResult tagResult = validationService.validateTagDisplay(requestedTag, player);
        if (!tagResult.isValid()) {
            MessageUtils.sendError(player, tagResult.getErrorMessage());
            
            // If the error is color code related, show color code help
            if (tagResult.getErrorMessage().contains("color code")) {
                validationService.showColorCodeHelp(player);
            }
            return;
        }
        
        // Normalize the tag display
        // Process the tag preview
        int endIndex = requestedTag.indexOf(']') + 1;
        if (endIndex < requestedTag.length()) {
            requestedTag = requestedTag.substring(0, endIndex);
        }
        
        // Log the request attempt
        securityService.logSecurityEvent(Level.INFO, player, "TAG_REQUEST",
            "Player requested custom tag: " + requestedTag);
        
        // Show preview and get confirmation
        MessageUtils.sendSuccess(player, "Tag request preview: " + ChatColor.translateAlternateColorCodes('&', requestedTag));
        MessageUtils.sendInfo(player, "Type 'accept' to confirm the tag or 'decline' to try again.");
        
        // Register the preview listener
        plugin.addPreviewTag(player, requestedTag);
    }

    /**
     * Handles purge commands with enhanced security
     */
    private void handlePurgeCommand(Player player, String[] args) {
        // Validate arguments
        ValidationResult argsResult = validationService.validateCommandArgs(args, 1, 1);
        if (!argsResult.isValid()) {
            MessageUtils.sendError(player, "Usage: /tag admin purge [tags|requests]");
            return;
        }
        
        // Double-check admin permission for dangerous operations
        if (!securityService.checkAdmin(player, "purge data")) {
            return; // Security service already logs this
        }
        
        // Require confirmation for dangerous operations
        switch (args[0].toLowerCase()) {
            case "tags":
                // Log this critical admin action
                securityService.logSecurityEvent(Level.WARNING, player, "ADMIN_PURGE_TAGS", 
                    "Admin purged all tags from the database");
                
                plugin.purgeTagsTable();
                MessageUtils.sendWarning(player, "All data has been purged from the tags table.");
                break;
            case "requests":
                // Log this admin action
                securityService.logSecurityEvent(Level.WARNING, player, "ADMIN_PURGE_REQUESTS", 
                    "Admin purged all tag requests from the database");
                
                plugin.purgeRequestsTable();
                MessageUtils.sendWarning(player, "All data has been purged from the requests table.");
                break;
            default:
                MessageUtils.sendError(player, "Invalid purge option. Use 'tags' or 'requests'.");
        }
    }

    /**
     * Displays the admin command usage
     */
    private void displayAdminUsage(Player player) {
        MessageUtils.sendInfo(player, "Admin Commands:");
        MessageUtils.sendInfo(player, "/tag admin create [name] [display] [type] [weight] - Create a new tag");
        MessageUtils.sendInfo(player, "/tag admin delete [name] - Delete an existing tag");
        MessageUtils.sendInfo(player, "/tag admin edit [tagname] [attribute] [newvalue] - Edit an existing tag");
        MessageUtils.sendInfo(player, "/tag admin requests - View pending custom tag requests");
        MessageUtils.sendWarning(player, "/tag admin purge tags - Purge all tags from the database");
        MessageUtils.sendWarning(player, "/tag admin purge requests - Purge all custom tag requests from the database");
    }

    /**
     * Creates a new tag with enhanced validation
     */
    private void createTag(Player player, String[] args) {
        // Validate command arguments
        ValidationResult argsResult = validationService.validateCommandArgs(args, 4, 4);
        if (!argsResult.isValid()) {
            MessageUtils.sendError(player, "Usage: /tag admin create [name] [display] [type] [weight]");
            return;
        }

        String name = args[0];
        String display = ChatColor.translateAlternateColorCodes('&', args[1]);
        String typeString = args[2].toUpperCase();
        
        // Validate tag name
        ValidationResult nameResult = validationService.validateTagName(name, player);
        if (!nameResult.isValid()) {
            MessageUtils.sendError(player, nameResult.getErrorMessage());
            return;
        }
        
        // Validate tag display
        ValidationResult displayResult = validationService.validateTagDisplay(args[1], player);
        if (!displayResult.isValid()) {
            MessageUtils.sendError(player, displayResult.getErrorMessage());
            return;
        }
        
        // Validate tag type
        ValidationResult typeResult = validationService.validateTagType(typeString);
        if (!typeResult.isValid()) {
            MessageUtils.sendError(player, typeResult.getErrorMessage());
            return;
        }
        TagType type = typeResult.getValue();
        
        // Validate weight
        ValidationResult weightResult = validationService.validateWeight(args[3]);
        if (!weightResult.isValid()) {
            MessageUtils.sendError(player, weightResult.getErrorMessage());
            return;
        }
        int weight = weightResult.getValue();

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
            return;
        }

        // Create the tag
        Tag newTag = new Tag(name, display, type, true, true, material, weight);
        
        // Log this admin action
        securityService.logSecurityEvent(Level.INFO, player, "ADMIN_CREATE_TAG", 
            "Admin created tag: " + name + " with display: " + display);
        
        // Add the tag to the database
        if (plugin.addTagToDatabase(newTag)) {
            MessageUtils.sendSuccess(player, "Tag '" + name + "' - " + display + ChatColor.GREEN + " has been created.");
        } else {
            MessageUtils.sendError(player, "An error occurred while creating the tag. Please check the server logs.");
        }
    }

    /**
     * Deletes a tag with enhanced security
     */
    private void deleteTag(Player player, String[] args) {
        // Validate command arguments
        ValidationResult argsResult = validationService.validateCommandArgs(args, 1, 1);
        if (!argsResult.isValid()) {
            MessageUtils.sendError(player, "Usage: /tag admin delete [name]");
            return;
        }

        String name = args[0];
        
        // Validate tag name
        ValidationResult nameResult = validationService.validateTagName(name, player);
        if (!nameResult.isValid()) {
            MessageUtils.sendError(player, nameResult.getErrorMessage());
            return;
        }
        
        // Log this admin action
        securityService.logSecurityEvent(Level.WARNING, player, "ADMIN_DELETE_TAG", 
            "Admin deleted tag: " + name);
        
        // Delete the tag
        if (plugin.deleteTagFromDatabase(name)) {
            MessageUtils.sendSuccess(player, "Tag '" + name + "' has been deleted.");
        } else {
            MessageUtils.sendError(player, "An error occurred while deleting the tag. It may not exist.");
        }
    }

    /**
     * Edits a tag with enhanced validation and security
     */
    private void editTag(Player player, String[] args) {
        // Validate command arguments
        ValidationResult argsResult = validationService.validateCommandArgs(args, 3, 3);
        if (!argsResult.isValid()) {
            MessageUtils.sendError(player, "Usage: /tag admin edit [tagname] [attribute] [newvalue]");
            return;
        }

        String tagName = args[0];
        String attribute = args[1];
        String newValue = args[2];

        // Validate tag name
        ValidationResult nameResult = validationService.validateTagName(tagName, player);
        if (!nameResult.isValid()) {
            MessageUtils.sendError(player, nameResult.getErrorMessage());
            return;
        }
        
        // Validate attribute name
        ValidationResult attrResult = validationService.validateAttribute(attribute);
        if (!attrResult.isValid()) {
            MessageUtils.sendError(player, attrResult.getErrorMessage());
            return;
        }

        // Validate specific attribute values
        switch (attribute.toLowerCase()) {
            case "name":
                ValidationResult newNameResult = validationService.validateTagName(newValue, player);
                if (!newNameResult.isValid()) {
                    MessageUtils.sendError(player, newNameResult.getErrorMessage());
                    return;
                }
                break;
                
            case "display":
                ValidationResult displayResult = validationService.validateTagDisplay(newValue, player);
                if (!displayResult.isValid()) {
                    MessageUtils.sendError(player, displayResult.getErrorMessage());
                    return;
                }
                break;
                
            case "type":
                ValidationResult typeResult = validationService.validateTagType(newValue);
                if (!typeResult.isValid()) {
                    MessageUtils.sendError(player, typeResult.getErrorMessage());
                    return;
                }
                break;
                
            case "public":
            case "color":
                ValidationResult boolResult = validationService.validateBoolean(newValue);
                if (!boolResult.isValid()) {
                    MessageUtils.sendError(player, boolResult.getErrorMessage());
                    return;
                }
                break;
                
            case "weight":
                ValidationResult weightResult = validationService.validateWeight(newValue);
                if (!weightResult.isValid()) {
                    MessageUtils.sendError(player, weightResult.getErrorMessage());
                    return;
                }
                break;
        }
        
        // Log this admin action
        securityService.logSecurityEvent(Level.INFO, player, "ADMIN_EDIT_TAG", 
            "Admin edited tag: " + tagName + ", attribute: " + attribute + ", new value: " + newValue);

        // Edit the tag
        if (plugin.editTagAttribute(tagName, attribute, newValue)) {
            MessageUtils.sendSuccess(player, "Successfully edited the tag attribute.");
        } else {
            MessageUtils.sendError(player, "Failed to edit the tag. Please check the tag name, attribute, and new value.");
        }
    }

    /**
     * Displays help information with pagination support
     */
    private void displayHelp(Player player, int page) {
        List<String> helpLines = new ArrayList<>();

        helpLines.add(ChatColor.YELLOW + "Available commands:");

        // General help commands
        helpLines.add(ChatColor.GREEN + "/tag - Open the tag GUI menu.");
        helpLines.add(ChatColor.GREEN + "/tag set [tag]- Quick set a prefix tag.");
        helpLines.add(ChatColor.GREEN + "/tag request [tag] - Request a custom tag. (Requires Veteran or Premium Membership)");
        helpLines.add(ChatColor.GREEN + "/tag help [page] - Shows this help menu.");

        // Add admin commands only if the player has the appropriate permission
        if (player.hasPermission("utags.admin")) {
            helpLines.add(ChatColor.YELLOW + "Admin commands:");
            helpLines.add(ChatColor.YELLOW + "/tag admin create [name] [display] [type] [weight] - Create a new tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin delete [name] - Delete an existing tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin edit [tagname] [attribute] [newvalue] - Edit an existing tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin requests - View pending custom tag requests.");
            helpLines.add(ChatColor.RED + "/tag admin purge tags - Purge all tags from the database.");
            helpLines.add(ChatColor.RED + "/tag admin purge requests - Purge all custom tag requests from the database.");
            helpLines.add(ChatColor.YELLOW + "/tag encrypt - Encrypt the database password in the config.");
        }

        int totalPages = (int) Math.ceil((double) helpLines.size() / LINES_PER_PAGE);
        int startIndex = (page - 1) * LINES_PER_PAGE;
        int endIndex = Math.min(helpLines.size(), startIndex + LINES_PER_PAGE);

        if (page < 1 || page > totalPages) {
            MessageUtils.sendError(player, "Invalid page number. Valid pages: 1-" + totalPages);
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

    /**
     * Shows tag request help with more detail
     */
    private void showTagRequestHelp(Player player) {
        MessageUtils.sendInfo(player, "Usage: /tag request [YourNewTag]");
        MessageUtils.sendInfo(player, "Tag Request Rules:");
        MessageUtils.sendInfo(player, "1. The tag must start with a color code.");
        MessageUtils.sendInfo(player, "2. The tag must be surrounded by square brackets ([ and ]).");
        MessageUtils.sendInfo(player, "3. The tag must be a maximum of 15 characters long.");
        MessageUtils.sendInfo(player, "4. The tag must not contain any formatting codes (&k, &l, etc).");
        MessageUtils.sendInfo(player, "5. The tag must not contain any invalid characters.");
        MessageUtils.sendInfo(player, "6. Everything after the final square bracket will be ignored.");
        
        // Show color codes for reference
        validationService.showColorCodeHelp(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return suggestions;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            // First level of the command
            suggestions.add("request");
            suggestions.add("set");
            suggestions.add("help");
            if (player.hasPermission("utags.admin")) {
                suggestions.add("admin");
                suggestions.add("encrypt");
            }
        } else if (args.length == 2) {
            // Second level of the command
            if ("set".equalsIgnoreCase(args[0])) {
                // Suggest available tags the player has permission to use
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
                // Suggest page numbers
                suggestions.add("1");
                suggestions.add("2");
            } else if ("request".equalsIgnoreCase(args[0])) {
                // Suggest tag formats
                suggestions.add("&c[YourTag]");
                suggestions.add("&e[CustomTag]");
                suggestions.add("&a[UniqueTag]");
            }
        } else if (args.length >= 3 && "admin".equalsIgnoreCase(args[0]) && player.hasPermission("utags.admin")) {
            // Admin sub-commands
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
                // Suggest existing tags to delete
                for (Tag tag : plugin.getAvailableTags(null)) {
                    suggestions.add(tag.getName());
                }
            } else if ("edit".equalsIgnoreCase(args[1])) {
                if (args.length == 3) {
                    // Suggest existing tags to edit
                    for (Tag tag : plugin.getAvailableTags(null)) {
                        suggestions.add(tag.getName());
                    }
                } else if (args.length == 4) {
                    // Suggest attributes that can be edited
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
                        suggestions.add("200");
                        suggestions.add("300");
                    }
                }
            } else if ("purge".equalsIgnoreCase(args[1])) {
                suggestions.add("tags");
                suggestions.add("requests");
            }
        }

        // Filter suggestions based on what the player has already typed
        if (args.length > 0) {
            String lastArg = args[args.length - 1].toLowerCase();
            suggestions.removeIf(suggestion -> !suggestion.toLowerCase().startsWith(lastArg));
        }

        return suggestions;
    }
}
