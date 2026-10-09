package com.blockworlds.utags.command;

import com.blockworlds.utags.*;
import com.blockworlds.utags.Tag;
import com.blockworlds.utags.menu.*;

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

/**
 * What the /tag subcommands do. {@link UTagsCommands} builds the Brigadier tree and calls these methods; every
 * message is the one 1.3.0 sent. Runs on the main thread; the database work is in the services (async).
 */
public class TagCommand {
    private static final int LINES_PER_PAGE = 50;
    private final uTags plugin;
    // Pattern to validate Minecraft color codes (&0-9, &a-f, case-insensitive)
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("^&[0-9a-fA-F]$");

    public TagCommand(uTags plugin) {
        this.plugin = plugin;
    }

    static final String INVALID_USAGE = ChatColor.RED + "Invalid usage. Use /tag help for a list of available commands.";

    /** /tag: the prefix selection menu (page 0). */
    void open(Player player) {
        plugin.getTagMenuManager().openTagMenu(player);
    }

    void invalidUsage(Player player) {
        player.sendMessage(INVALID_USAGE);
    }

    void set(Player player, String tagName) {
        boolean hasPermission = plugin.ownership().owns(player, tagName);
        boolean isAvailableTag = plugin.tags().available(TagType.PREFIX).stream().anyMatch(availableTag -> availableTag.getName().equals(tagName));
        if (!hasPermission || !isAvailableTag) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this tag.");
            return;
        }
        // The selection service looks up the display and applies the name color
        plugin.selection().select(player, tagName, TagType.PREFIX);
    }

    /** /tag request <tag>: the next custom slot is counted off the main thread (by UUID, so Bedrock names work). */
    void request(Player player, String requestedTag) {
        java.util.UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int slot = plugin.requests().nextCustomSlot(uuid, playerName);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) handleRequest(player, requestedTag, slot);
            });
        });
    }

    /** /tag request without a tag: the rules. */
    void requestRules(Player player) {
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

    /** /tag request, once the slot the tag would fill is known (main thread). */
    private void handleRequest(Player player, String requestedTag, int slot) {
        if (slot < 1) {
            player.sendMessage(ChatColor.RED + "Could not check your custom tags right now. Please try again later.");
            return;
        }
        String requiredPermission = "utags.custom" + slot;
        plugin.logDebug("Checking required permission for request: " + requiredPermission);
        if (player.hasPermission(requiredPermission)) {
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
                return;
            }
            // Trim tag display after validation
            int endIndex = requestedTag.indexOf(']') + 1;
            if (endIndex > 0 && endIndex < requestedTag.length()) { // Ensure ']' exists and there's something after it
                requestedTag = requestedTag.substring(0, endIndex);
            }

            // Open the GUI confirmation menu instead of chat prompt
            plugin.getTagMenuManager().openRequestConfirmation(player, requestedTag);
        } else if (slot <= 4) {
            // TODO: Make permission denial message configurable
            player.sendMessage(ChatColor.RED + "You can't request any more custom tags, ask a server admin for more custom tag slots.");
        } else {
            // The menu has four custom tag slots
            player.sendMessage(ChatColor.RED + "You have reached the maximum number of custom tags.");
        }
    }

    void displayHelp(Player player, int page) {
        List<String> helpLines = new ArrayList<>();

        helpLines.add(ChatColor.YELLOW + "Available commands:");

        // General help commands
        helpLines.add(ChatColor.GREEN + "/tag - Open the tag GUI menu.");
        helpLines.add(ChatColor.GREEN + "/tag set [tag] - Quick set a prefix tag.");
        helpLines.add(ChatColor.GREEN + "/tag namecolor <&code|reset> - Set your name color.");
        helpLines.add(ChatColor.GREEN + "/tag request [tag] - Request a custom tag. (Needs a custom tag slot, permission utags.custom1, utags.custom2, ...)");

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

    /** Every /tag admin subcommand starts here: staff only. */
    boolean requireAdmin(Player player) {
        if (player.hasPermission("utags.admin")) return true;
        player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
        return false;
    }

    void adminGui(Player player) {
        plugin.getAdminMenuManager().openAdminMainMenu(player);
    }

    /** /tag admin purge <tags|requests> [confirm]; confirm is null for the warning. */
    void purge(Player player, String type, boolean confirm) {
        String purgeType = type.toLowerCase();
        if (!confirm) {
            if (purgeType.equals("tags") || purgeType.equals("requests")) {
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "WARNING: " + ChatColor.YELLOW + "This action is irreversible!");
                player.sendMessage(ChatColor.YELLOW + "To confirm purging all " + purgeType + ", type: " +
                                   ChatColor.WHITE + "/tag admin purge " + purgeType + " confirm");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid purge type. Use 'tags' or 'requests'.");
            }
            return;
        }
        switch (purgeType) {
            case "tags":
                plugin.tags().purge().thenRun(() -> player.sendMessage(ChatColor.RED + "All data has been purged from the tags table."));
                break;
            case "requests":
                plugin.requests().purgeAsync().thenRun(() -> player.sendMessage(ChatColor.RED + "All data has been purged from the requests table."));
                break;
            default:
                player.sendMessage(ChatColor.RED + "Invalid purge type specified for confirmation.");
        }
    }

    void purgeUsage(Player player) {
        player.sendMessage(ChatColor.RED + "Usage: /tag admin purge <tags|requests> [confirm]");
    }

    void adminRequests(Player player) {
        plugin.requestMenus().open(player);
    }

    void adminDebug(Player player) {
        plugin.setDebugModeEnabled(!plugin.isDebugModeEnabled()); // Toggle the mode
        player.sendMessage(ChatColor.YELLOW + "Debug mode is now " + (plugin.isDebugModeEnabled() ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED") + ChatColor.YELLOW + ".");
    }

    void displayAdminUsage(Player player) {
        player.sendMessage(ChatColor.RED + "Usage: /tag admin create [name] [display] [type] [weight] [public(true/false)]");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin delete [name]");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin purge");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin requests");
    }

    void createTag(Player player, String[] args) {
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

        // Add the new tag to the database (in the background; the answer comes back on the main thread)
        final String createdDisplay = display;
        plugin.tags().create(new Tag(name, display, type, isPublic, Boolean.TRUE, true, material, weight)).thenAccept(saved -> {
            if (!player.isOnline()) return;
            if (!saved) {
                player.sendMessage(ChatColor.RED + "Could not save tag '" + name + "' (database error, see the console).");
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Tag '" + name + "' - " + createdDisplay + ChatColor.GREEN + " has been created.");
        });
    }

    void deleteTag(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /tag admin delete [name]");
            return;
        }

        String name = args[0];

        // Delete the tag from the database
        plugin.tags().delete(name).thenRun(() -> {
            if (player.isOnline()) player.sendMessage(ChatColor.RED + "Tag '" + name + "' " + ChatColor.RED + "has been deleted.");
        });
    }

    /** /tag admin edit <tag> <attribute> <value> (1.3.0 read the arguments one place too far and always asked for a fourth). */
    void editTag(Player player, String tagName, String attribute, String newValue) {
        plugin.tags().edit(tagName, attribute, newValue).thenAccept(edited -> {
            if (!edited) {
                player.sendMessage(ChatColor.RED + "Failed to edit the tag. Please check the tag name, attribute, and new value.");
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Successfully edited the tag.");
        });
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

    /** /tag namecolor [code]: code is null when the player gave none. */
    void nameColor(Player player, String colorInput) {
        if (!player.hasPermission("utags.command.namecolor")) {
            player.sendMessage(plugin.display().message("no_permission")); // Use configurable message
            return;
        }

        if (colorInput == null) {
            player.sendMessage(plugin.display().message("namecolor_usage")); // Use configurable message
            return;
        }

        String finalColorCode = null; // Store the validated code or null for reset

        if ("reset".equalsIgnoreCase(colorInput)) {
            finalColorCode = null; // Resetting means setting to null
        } else {
            Matcher matcher = COLOR_CODE_PATTERN.matcher(colorInput);
            if (!matcher.matches()) {
                player.sendMessage(plugin.display().message("namecolor_invalid_color")); // Use configurable message
                // Optionally list valid codes here if needed, similar to /tag request
                return;
            }
            finalColorCode = colorInput; // It's a valid color code
        }

        // Save the name color preference using the correct method in uTags.java
        plugin.selection().setNameColor(player.getUniqueId(), finalColorCode);

        // Trigger display name update using the new method
        plugin.selection().applyStored(player); // Apply the stored color which triggers the update

        // Send confirmation message
        if (finalColorCode == null) {
            player.sendMessage(plugin.display().message("namecolor_reset_success"));
        } else {
            // Use replace to show the color in the message
            player.sendMessage(plugin.display().message("namecolor_success").replace("{color}", ChatColor.translateAlternateColorCodes('&', finalColorCode) + finalColorCode + ChatColor.GREEN));
        }
    }
}
