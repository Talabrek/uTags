package com.blockworlds.utags;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TagCommand implements CommandExecutor, TabCompleter {
    private static final int LINES_PER_PAGE = 50;
    private final uTags plugin;
    // Pattern to validate Minecraft color codes (&0-9, &a-f, case-insensitive)
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("^&[0-9a-fA-F]$");

    public TagCommand(uTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Directly open the prefix selection menu (page 0)
            plugin.getTagMenuManager().openTagSelection(player, 0, TagType.PREFIX);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                if (args.length == 1) {
                    displayHelp(player, 1);
                } else {
                    displayHelp(player, Integer.parseInt(args[1]));
                }
                break;

            case "set":
                if (args.length == 2) {
                    boolean hasPermission = player.hasPermission("utags.tag." + args[1]);
                    boolean isAvailableTag = plugin.getAvailableTags(TagType.PREFIX).stream().anyMatch(availableTag -> availableTag.getName().equals(args[1]));

                    if (!hasPermission || !isAvailableTag) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to use this tag.");
                        return true;
                    }
                    plugin.setPlayerTag(player, args[1], TagType.PREFIX);
                    String tagDisplay = plugin.getTagDisplayByName(args[1]);
                    player.sendMessage(ChatColor.GREEN + "Your " + TagType.PREFIX + " has been updated to: " + ChatColor.translateAlternateColorCodes('&', tagDisplay != null ? tagDisplay : args[1]));
                } else {
                    player.sendMessage(ChatColor.RED + "Invalid usage. Use /tag help for a list of available commands.");
                }
                break;

            case "admin":
                handleAdminCommands(player, args);
                break;

            case "namecolor":
                handleNameColorCommand(player, args);
                break;

            case "request":
                if (args.length == 2) {
                    int customTagCount = plugin.countCustomTags(player.getName());
                    String requiredPermission = "utags.custom" + (customTagCount + 1);
                    plugin.getLogger().info("Checking required permission for request: " + requiredPermission);
                    if (player.hasPermission(requiredPermission)) {
                        String requestedTag = args[1];
                        String validationResult = isValidTag(requestedTag);
                        if (validationResult != null) {
                            player.sendMessage(ChatColor.RED + validationResult);
                            if (validationResult.contains("color code")) {
                                ChatColor[] colors = {ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA, ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY, ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA, ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE};
                                String[] colorCodes = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};
                                StringBuilder colorCodeList = new StringBuilder(ChatColor.AQUA + "List of available color codes: ");
                                for (int i = 0; i < colors.length; i++) {
                                    colorCodeList.append(colors[i]).append("&").append(colorCodes[i]).append(" ");
                                }
                                player.sendMessage(colorCodeList.toString().trim());
                            }
                            return true;
                        }
                        // Trim tag display after validation
                        int endIndex = requestedTag.indexOf(']') + 1;
                        if (endIndex > 0 && endIndex < requestedTag.length()) { // Ensure ']' exists and there's something after it
                            requestedTag = requestedTag.substring(0, endIndex);
                        }

                        // Open the GUI confirmation menu instead of chat prompt
                        plugin.getTagMenuManager().openRequestConfirmation(player, requestedTag);

                        // Remove old chat confirmation and preview listener logic
                        // player.sendMessage(ChatColor.GREEN + "Tag request preview: " + ChatColor.translateAlternateColorCodes('&', requestedTag));
                        // player.sendMessage(ChatColor.YELLOW + "Type 'accept' to accept the tag or 'decline' to try again.");
                        // plugin.addPreviewTag(player, requestedTag);

                        return true;
                    } else {
                        // TODO: Make permission denial message configurable
                        if (!requiredPermission.equalsIgnoreCase("utags.custom5")) // Example limit check
                            player.sendMessage(ChatColor.RED + "You can't request any more custom tags, unlock more custom tag slots every month as a premium subscriber.");
                        else
                            player.sendMessage(ChatColor.RED + "You have reached the maximum number of custom tags.");
                        return true;
                    }
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /tag request [YourNewTag]");
                    player.sendMessage(ChatColor.YELLOW + "You must follow these rules when requesting a custom tag:");
                    player.sendMessage(ChatColor.YELLOW + "1. The tag must start with a color code.");
                    player.sendMessage(ChatColor.YELLOW + "2. The tag must be surrounded by square brackets ([ and ]).");
                    player.sendMessage(ChatColor.YELLOW + "3. The tag must be a maximum of 15 characters long.");
                    player.sendMessage(ChatColor.YELLOW + "4. The tag must not contain any spaces.");
                    player.sendMessage(ChatColor.YELLOW + "5. The tag must not contain any formatting codes (&k, &l, etc).");
                    player.sendMessage(ChatColor.YELLOW + "6. The tag must not contain any invalid characters.");
                    player.sendMessage(ChatColor.YELLOW + "7. Everything after the final square bracket will be ignored.");
                    player.sendMessage(ChatColor.YELLOW + "A staff member will review your request and approve it if it meets the requirements.");
                    ChatColor[] colors = {ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA, ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY, ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA, ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE};
                    String[] colorCodes = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};
                    StringBuilder colorCodeList = new StringBuilder(ChatColor.AQUA + "List of available color codes: ");
                    for (int i = 0; i < colors.length; i++) {
                        colorCodeList.append(colors[i]).append("&").append(colorCodes[i]).append(" ");
                    }
                    player.sendMessage(colorCodeList.toString().trim());
                }
                break;

            default:
                player.sendMessage(ChatColor.RED + "Invalid usage. Use /tag help for a list of available commands.");
                break;
        }
        return true;
    }

    private void displayHelp(Player player, int page) {
        List<String> helpLines = new ArrayList<>();

        helpLines.add(ChatColor.YELLOW + "Available commands:");

        // General help commands
        helpLines.add(ChatColor.GREEN + "/tag - Open the tag GUI menu.");
        helpLines.add(ChatColor.GREEN + "/tag set [tag] - Quick set a prefix tag.");
        helpLines.add(ChatColor.GREEN + "/tag namecolor <&code|reset> - Set your name color.");
        helpLines.add(ChatColor.GREEN + "/tag request [tag] - Request a custom tag. (Requires Veteran or Premium Membership)");

        // Add admin commands only if the player has the appropriate permission
        if (player.hasPermission("utags.admin")) {
            helpLines.add(ChatColor.YELLOW + "Admin commands:");
            helpLines.add(ChatColor.YELLOW + "/tag admin create [name] [display] [type] [weight] - Create a new tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin delete [name] - Delete an existing tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin edit [tagname] [attribute] [newvalue] - Edit an existing tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin requests - View pending custom tag requests.");
            helpLines.add(ChatColor.RED + "/tag admin purge tags - Purge all tags from the database.");
            helpLines.add(ChatColor.RED + "/tag admin purge requests - Purge all custom tag requests from the database.");
        }

        int linesPerPage = 50;
        int totalPages = (int) Math.ceil((double) helpLines.size() / linesPerPage);
        int startIndex = (page - 1) * linesPerPage;
        int endIndex = Math.min(helpLines.size(), startIndex + linesPerPage);

        if (page < 1 || page > totalPages) {
            player.sendMessage(ChatColor.RED + "Invalid page number.");
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

    private void handleAdminCommands(Player player, String[] args) {
        if (!player.hasPermission("utags.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }

        if (args.length >= 2) {
            switch (args[1].toLowerCase()) {
                case "gui": // New case for the admin GUI
                    plugin.getAdminMenuManager().openAdminMainMenu(player);
                    break;
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
                    if (args.length == 3) {
                        // Send confirmation message
                        String purgeType = args[2].toLowerCase();
                        if (purgeType.equals("tags") || purgeType.equals("requests")) {
                            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "WARNING: " + ChatColor.YELLOW + "This action is irreversible!");
                            player.sendMessage(ChatColor.YELLOW + "To confirm purging all " + purgeType + ", type: " +
                                               ChatColor.WHITE + "/tag admin purge " + purgeType + " confirm");
                        } else {
                            player.sendMessage(ChatColor.RED + "Invalid purge type. Use 'tags' or 'requests'.");
                        }
                    } else if (args.length == 4 && args[3].equalsIgnoreCase("confirm")) {
                        // Execute purge on confirmation
                        String purgeType = args[2].toLowerCase();
                        switch (purgeType) {
                            case "tags":
                                plugin.purgeTagsTable();
                                player.sendMessage(ChatColor.RED + "All data has been purged from the tags table.");
                                break;
                            case "requests":
                                plugin.purgeRequestsTable();
                                player.sendMessage(ChatColor.RED + "All data has been purged from the requests table.");
                                break;
                            default:
                                player.sendMessage(ChatColor.RED + "Invalid purge type specified for confirmation.");
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "Usage: /tag admin purge <tags|requests> [confirm]");
                    }
                    break; // Added missing break statement
                case "requests":
                    plugin.openRequestsMenu(player);
                    break;
                default:
                    displayAdminUsage(player);
            }
        } else {
            displayAdminUsage(player);
        }
    }

    private void displayAdminUsage(Player player) {
        player.sendMessage(ChatColor.RED + "Usage: /tag admin create [name] [display] [type] [weight] [public(true/false)]");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin delete [name]");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin purge");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin requests");
    }

    private void createTag(Player player, String[] args) {
        if (args.length != 5) { // Changed from 4 to 5
            player.sendMessage(ChatColor.RED + "Usage: /tag admin create [name] [display] [type] [weight] [public(true/false)]");
            return;
        }

        String name = args[0];
        String display = ChatColor.translateAlternateColorCodes('&', args[1]);
        String typeString = args[2].toUpperCase();
        int weight = Integer.parseInt(args[3]);
        boolean isPublic = Boolean.parseBoolean(args[4]); // New argument for public status
        ItemStack material = player.getInventory().getItemInMainHand();

        if (!name.matches("^[a-zA-Z0-9_-]+$")) {
            player.sendMessage(ChatColor.RED + "Invalid tag [name]. It should contain only letters, numbers, underscores, and hyphens.");
            return;
        }
        TagType type;
        try {
            type = TagType.valueOf(typeString);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid tag [type]. It should be one of 'PREFIX', 'SUFFIX', or 'BOTH'.");
            return;
        }


        if (weight < 0) {
            player.sendMessage(ChatColor.RED + "Invalid [weight] value. It should be a number greater than or equal to 0.");
            return;
        }

        // If the player is holding a player head, get the player head with custom texture
        if (material.getType() == Material.PLAYER_HEAD && material.getItemMeta() instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta) material.getItemMeta();
            if (skullMeta.hasOwner()) {
                material.setItemMeta(skullMeta);
            }
        }

        if (material.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Invalid item for tag display. Hold an item in your hand.");
            return;
        }

        // Add the new tag to the database
        plugin.addTagToDatabase(new Tag(name, display, type, isPublic, Boolean.TRUE, material, weight)); // Use the isPublic variable

        player.sendMessage(ChatColor.GREEN + "Tag '" + name + "' - " + display + ChatColor.GREEN + " has been created.");
    }

    private void deleteTag(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /tag admin delete [name]");
            return;
        }

        String name = args[0];

        // Delete the tag from the database
        plugin.deleteTagFromDatabase(name);

        player.sendMessage(ChatColor.RED + "Tag '" + name + "' " + ChatColor.RED + "has been deleted.");
    }

    private void editTag(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /tag admin edit [tagname] [attribute] [newvalue]");
            return;
        }

        String tagName = args[1];
        String attribute = args[2];
        String newValue = args[3];

        if (!plugin.editTagAttribute(tagName, attribute, newValue)) {
            sender.sendMessage(ChatColor.RED + "Failed to edit the tag. Please check the tag name, attribute, and new value.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Successfully edited the tag.");
    }

    private boolean isValidBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    private String isValidTag(String tag) {
        String colorCodePattern = "(&[0-9a-fA-F])";
        String invalidCodePattern = "(&[rRkKlLmMnNoO])";
        String tagPattern = "^" + colorCodePattern + "\\[" + "(?:(?:" + colorCodePattern + "|.)*){0,15}" + "\\]" + ".*" + "$";
        Pattern pattern = Pattern.compile(tagPattern);
        Matcher matcher = pattern.matcher(tag);

        if (!matcher.matches()) {
            return "A valid tag must start with a color code (e.g., &d, &6) followed by '[' and end with ']'.";
        }

        if (tag.matches(".*" + invalidCodePattern + ".*")) {
            return "A valid tag must not contain formatting codes such as &n or &k.";
        }

        String content = tag.substring(tag.indexOf('[') + 1, tag.length() - 1);
        String contentWithoutColorCodes = content.replaceAll(colorCodePattern, "");

        if (contentWithoutColorCodes.length() > 15) {
            return "A valid tag must be between 1 and 15 characters long, excluding color codes.";
        }

        return null; // No validation error
    }

    private void handleNameColorCommand(Player player, String[] args) {
        if (!player.hasPermission("utags.command.namecolor")) {
            player.sendMessage(plugin.getMessage("no_permission")); // Use configurable message
            return;
        }

        if (args.length != 2) {
            player.sendMessage(plugin.getMessage("namecolor_usage")); // Use configurable message
            return;
        }

        String colorInput = args[1];
        String finalColorCode = null; // Store the validated code or null for reset

        if ("reset".equalsIgnoreCase(colorInput)) {
            finalColorCode = null; // Resetting means setting to null
        } else {
            Matcher matcher = COLOR_CODE_PATTERN.matcher(colorInput);
            if (!matcher.matches()) {
                player.sendMessage(plugin.getMessage("namecolor_invalid_color")); // Use configurable message
                // Optionally list valid codes here if needed, similar to /tag request
                return;
            }
            finalColorCode = colorInput; // It's a valid color code
        }

        // Save the name color preference using the correct method in uTags.java
        plugin.savePlayerNameColorCode(player.getUniqueId(), finalColorCode);

        // Trigger display name update (assuming method exists in uTags)
        plugin.updatePlayerDisplayName(player); // Important: This needs implementation in uTags.java

        // Send confirmation message
        if (finalColorCode == null) {
            player.sendMessage(plugin.getMessage("namecolor_reset_success"));
        } else {
            // Use replace to show the color in the message
            player.sendMessage(plugin.getMessage("namecolor_success").replace("{color}", ChatColor.translateAlternateColorCodes('&', finalColorCode) + finalColorCode + ChatColor.GREEN));
        }
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
            if (player.hasPermission("utags.admin")) {
                suggestions.add("admin");
            }
            suggestions.add("namecolor"); // Add namecolor suggestion
        } else if (args.length == 2) {
            if ("set".equalsIgnoreCase(args[0])) {
                for (Tag tag : plugin.getAvailableTags(TagType.PREFIX)) {
                    if (player.hasPermission("utags.tag." + tag.getName())) {
                        suggestions.add(tag.getName());
                    }
                }
            } else if ("admin".equalsIgnoreCase(args[0])) {
                if (player.hasPermission("utags.admin")) {
                    suggestions.add("gui"); // Add gui suggestion
                    suggestions.add("create");
                    suggestions.add("delete");
                    suggestions.add("edit"); // Add missing edit suggestion
                    suggestions.add("requests"); // Add missing requests suggestion
                    suggestions.add("purge");
                }
            } else if ("namecolor".equalsIgnoreCase(args[0])) {
                if (player.hasPermission("utags.command.namecolor")) {
                    // Suggest color codes and reset
                    suggestions.addAll(Stream.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f")
                                            .map(code -> "&" + code)
                                            .collect(Collectors.toList()));
                    suggestions.add("reset");
                }
            }
        } else if (args.length >= 3 && "admin".equalsIgnoreCase(args[0])) {
            if (player.hasPermission("utags.admin")) {
                if ("create".equalsIgnoreCase(args[1])) {
                    if (args.length == 3) {
                        suggestions.add("TagName");
                    } else if (args.length == 4) {
                        suggestions.add("[TagDisplay]");
                    } else if (args.length == 5) {
                        suggestions.add("PREFIX");
                        suggestions.add("SUFFIX");
                        suggestions.add("BOTH");
                    } else if (args.length == 6) {
                        suggestions.add("[weight]");
                    } else if (args.length == 7) {
                        suggestions.add("true");
                        suggestions.add("false");
                    }
                } else if ("delete".equalsIgnoreCase(args[1])) {
                    // Suggest all tag names for deletion
                    plugin.getAvailableTags(null).stream() // Get all tags (prefix, suffix, both)
                          .map(Tag::getName)
                          .distinct() // Avoid duplicates if tag is 'both'
                          .forEach(suggestions::add);
                } else if ("purge".equalsIgnoreCase(args[1])) {
                    if (args.length == 3) {
                        suggestions.add("tags");
                        suggestions.add("requests");
                    } else if (args.length == 4 && (args[2].equalsIgnoreCase("tags") || args[2].equalsIgnoreCase("requests"))) {
                        suggestions.add("confirm");
                    }
                }
                // TODO: Add suggestions for 'edit' command
            }
        }

        // Filter suggestions based on current input
        String currentArg = args[args.length - 1].toLowerCase();
        return suggestions.stream()
                          .filter(s -> s.toLowerCase().startsWith(currentArg))
                          .collect(Collectors.toList());
    }
}