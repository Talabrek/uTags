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

public class TagCommand implements CommandExecutor, TabCompleter {
    private static final int LINES_PER_PAGE = 50;
    private final uTags plugin;

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
            plugin.getTagMenuManager().openTagMenu(player);
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
                    plugin.setPlayerTag(player, plugin.getTagDisplayByName(args[1]), TagType.PREFIX);
                    player.sendMessage(ChatColor.GREEN + "Your " + TagType.PREFIX + " has been updated to: " + ChatColor.translateAlternateColorCodes('&', plugin.getTagDisplayByName(args[1])));
                } else {
                    player.sendMessage(ChatColor.RED + "Invalid usage. Use /tag help for a list of available commands.");
                }
                break;

            case "admin":
                handleAdminCommands(player, args);
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
                        // Show the tag preview and prompt the player to accept or decline
                        int endIndex = requestedTag.indexOf(']') + 1;
                        if (endIndex < requestedTag.length()) {
                            requestedTag = requestedTag.substring(0, endIndex);
                        }
                        player.sendMessage(ChatColor.GREEN + "Tag request preview: " + ChatColor.translateAlternateColorCodes('&', requestedTag));
                        player.sendMessage(ChatColor.YELLOW + "Type 'accept' to accept the tag or 'decline' to try again.");

                        // Register the preview listener
                        plugin.addPreviewTag(player, requestedTag);
                        //plugin.createCustomTagRequest(player, requestedTag);
                        return true;
                    } else {
                        if (!requiredPermission.equalsIgnoreCase("utags.custom5"))
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
        helpLines.add(ChatColor.GREEN + "/tag set [tag]- Quick set a prefix tag.");
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
                    if (args.length >= 3) {
                        switch (args[2].toLowerCase()) {
                            case "tags":
                                plugin.purgeTagsTable();
                                player.sendMessage(ChatColor.RED + "All data has been purged from the tags table.");
                                break;
                            case "requests":
                                plugin.purgeRequestsTable();
                                player.sendMessage(ChatColor.RED + "All data has been purged from the requests table.");
                                break;
                            default:
                                player.sendMessage(ChatColor.RED + "Invalid purge option. Use /tag admin help for a list of available commands.");
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "Invalid purge option. Use /tag admin help for a list of available commands.");
                    }
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
        player.sendMessage(ChatColor.RED + "Usage: /tag admin create [name] [display] [type] [weight]");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin delete [name]");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin purge");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin requests");
    }

    private void createTag(Player player, String[] args) {
        if (args.length != 4) {
            player.sendMessage(ChatColor.RED + "Usage: /tag admin create [name] [display] [type] [weight]");
            return;
        }

        String name = args[0];
        String display = ChatColor.translateAlternateColorCodes('&', args[1]);
        String typeString = args[2].toUpperCase();
        int weight = Integer.parseInt(args[3]);
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
        plugin.addTagToDatabase(new Tag(name, display, type, Boolean.TRUE, Boolean.TRUE, material, weight));

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

        return null;
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
        } else if (args.length == 2) {
            if ("set".equalsIgnoreCase(args[0])) {
                for (Tag tag : plugin.getAvailableTags(TagType.PREFIX)) {
                    if (player.hasPermission("utags.tag." + tag.getName())) {
                        suggestions.add(tag.getName());
                    }
                }
            } else if ("admin".equalsIgnoreCase(args[0])) {
                if (player.hasPermission("utags.admin")) {
                    suggestions.add("create");
                    suggestions.add("delete");
                    suggestions.add("purge");
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
                        suggestions.add("[number]");
                    }
                } else if ("delete".equalsIgnoreCase(args[1])) {
                    for (Tag tag : plugin.getAvailableTags(TagType.PREFIX)) {
                        suggestions.add(tag.getName());
                    }
                    for (Tag tag : plugin.getAvailableTags(TagType.SUFFIX)) {
                        suggestions.add(tag.getName());
                    }
                }
            }
        }

        return suggestions;
    }
}