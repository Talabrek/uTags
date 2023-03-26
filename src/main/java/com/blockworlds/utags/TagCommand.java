package com.blockworlds.utags;

import org.bukkit.ChatColor;
import org.bukkit.Material;
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
                    int customTagCount = plugin.countCustomTags(player.getUniqueId());
                    String requiredPermission = "utags.custom" + (customTagCount + 1);

                    if (player.hasPermission(requiredPermission)) {
                        String tagDisplay = args[1];
                        plugin.createCustomTagRequest(player, tagDisplay);
                        return true;
                    } else {
                        if (!requiredPermission.equalsIgnoreCase("utags.custom5"))
                            player.sendMessage(ChatColor.RED + "You can't request any more custom tags, unlock more custom tag slots every month as a premium subscriber.");
                        else
                            player.sendMessage(ChatColor.RED + "You have reached the maximum number of custom tags.");
                        return true;
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /tag request [tagDisplay]");
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
            helpLines.add(ChatColor.YELLOW + "/tag admin create [name] [display] [type] [public] [color] - Create a new tag.");
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
        player.sendMessage(ChatColor.RED + "Usage: /tag admin create [name] [display] [type] [public] [color]");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin delete [name]");
        player.sendMessage(ChatColor.RED + "Usage: /tag admin purge");

    }

    private void createTag(Player player, String[] args) {
        if (args.length != 5) {
            player.sendMessage(ChatColor.RED + "Usage: /tag admin create [name] [display] [type] [public] [color]");
            return;
        }

        String name = args[0];
        String display = ChatColor.translateAlternateColorCodes('&', args[1]);
        String typeString = args[2].toUpperCase();
        String isPublicString = args[3];
        String isColorString = args[4];
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

        // Check if the boolean values are valid
        if (!isValidBoolean(isPublicString)) {
            player.sendMessage(ChatColor.RED + "Invalid [public] value. It should be either 'true' or 'false'.");
            return;
        }

        if (!isValidBoolean(isColorString)) {
            player.sendMessage(ChatColor.RED + "Invalid [color] value. It should be either 'true' or 'false'.");
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
        plugin.addTagToDatabase(new Tag(name, display, type, Boolean.parseBoolean(isPublicString), Boolean.parseBoolean(isColorString), material));

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
                    } else if (args.length == 6 || args.length == 7) {
                        suggestions.add("true");
                        suggestions.add("false");
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