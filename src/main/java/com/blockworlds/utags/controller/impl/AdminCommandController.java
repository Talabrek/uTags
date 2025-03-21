package com.blockworlds.utags.controller.impl;

import com.blockworlds.utags.controller.CommandController;
import com.blockworlds.utags.model.Result;
import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.service.MenuService;
import com.blockworlds.utags.service.RequestService;
import com.blockworlds.utags.service.TagService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Controller for handling admin commands.
 */
public class AdminCommandController implements CommandController {

    private final TagService tagService;
    private final RequestService requestService;
    private final MenuService menuService;

    /**
     * Creates a new AdminCommandController.
     */
    public AdminCommandController(TagService tagService, RequestService requestService, MenuService menuService) {
        this.tagService = tagService;
        this.requestService = requestService;
        this.menuService = menuService;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check admin permission
        if (!player.hasPermission("utags.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use admin commands.");
            return true;
        }

        if (args.length < 1) {
            sendAdminHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                return handleCreateCommand(player, Arrays.copyOfRange(args, 1, args.length));
            case "delete":
                return handleDeleteCommand(player, Arrays.copyOfRange(args, 1, args.length));
            case "edit":
                return handleEditCommand(player, Arrays.copyOfRange(args, 1, args.length));
            case "requests":
                return handleRequestsCommand(player);
            case "purge":
                return handlePurgeCommand(player, Arrays.copyOfRange(args, 1, args.length));
            default:
                sendAdminHelp(player);
                return true;
        }
    }

    private boolean handleCreateCommand(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /tag admin create [name] [display] [type] [weight]");
            return true;
        }

        String name = args[0];
        String display = ChatColor.translateAlternateColorCodes('&', args[1]);
        String typeStr = args[2].toUpperCase();
        String weightStr = args[3];

        TagType type;
        try {
            type = TagType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid tag type. Valid types: PREFIX, SUFFIX, BOTH");
            return true;
        }

        int weight;
        try {
            weight = Integer.parseInt(weightStr);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Weight must be a valid integer");
            return true;
        }

        ItemStack material = player.getInventory().getItemInMainHand();
        if (material.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Hold an item in your hand to use as the tag's icon");
            return true;
        }

        Tag tag = new Tag(name, display, type, true, true, material.clone(), weight);
        Result<Boolean> result = tagService.addTag(tag);

        if (result.isSuccess() && result.getValue()) {
            player.sendMessage(ChatColor.GREEN + "Tag created successfully!");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create tag: " + result.getMessage());
        }

        return true;
    }

    private boolean handleDeleteCommand(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /tag admin delete [name]");
            return true;
        }

        String name = args[0];
        Result<Boolean> result = tagService.deleteTag(name);

        if (result.isSuccess() && result.getValue()) {
            player.sendMessage(ChatColor.GREEN + "Tag deleted successfully!");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to delete tag: " + result.getMessage());
        }

        return true;
    }

    private boolean handleEditCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /tag admin edit [tagname] [attribute] [newvalue]");
            return true;
        }

        String tagName = args[0];
        String attribute = args[1];
        String newValue = args[2];

        Result<Boolean> result = tagService.editTagAttribute(tagName, attribute, newValue);

        if (result.isSuccess() && result.getValue()) {
            player.sendMessage(ChatColor.GREEN + "Tag updated successfully!");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to update tag: " + result.getMessage());
        }

        return true;
    }

    private boolean handleRequestsCommand(Player player) {
        menuService.openRequestsMenu(player);
        return true;
    }

    private boolean handlePurgeCommand(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /tag admin purge [tags|requests]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "tags":
                Result<Boolean> tagsResult = tagService.purgeTagsTable();
                if (tagsResult.isSuccess() && tagsResult.getValue()) {
                    player.sendMessage(ChatColor.YELLOW + "All tags have been purged!");
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to purge tags: " + tagsResult.getMessage());
                }
                break;
            case "requests":
                Result<Boolean> requestsResult = requestService.purgeRequestsTable();
                if (requestsResult.isSuccess() && requestsResult.getValue()) {
                    player.sendMessage(ChatColor.YELLOW + "All tag requests have been purged!");
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to purge requests: " + requestsResult.getMessage());
                }
                break;
            default:
                player.sendMessage(ChatColor.RED + "Invalid option. Use 'tags' or 'requests'");
                break;
        }

        return true;
    }

    private void sendAdminHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Admin Commands:");
        player.sendMessage(ChatColor.YELLOW + "/tag admin create [name] [display] [type] [weight] - Create a new tag");
        player.sendMessage(ChatColor.YELLOW + "/tag admin delete [name] - Delete an existing tag");
        player.sendMessage(ChatColor.YELLOW + "/tag admin edit [tagname] [attribute] [newvalue] - Edit a tag");
        player.sendMessage(ChatColor.YELLOW + "/tag admin requests - View pending tag requests");
        player.sendMessage(ChatColor.RED + "/tag admin purge [tags|requests] - Purge all tags or requests");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return suggestions;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("utags.admin")) {
            return suggestions;
        }

        if (args.length == 1) {
            suggestions.add("create");
            suggestions.add("delete");
            suggestions.add("edit");
            suggestions.add("requests");
            suggestions.add("purge");
        } else if (args.length >= 2) {
            switch (args[0].toLowerCase()) {
                case "create":
                    if (args.length == 4) {
                        suggestions.add("PREFIX");
                        suggestions.add("SUFFIX");
                        suggestions.add("BOTH");
                    } else if (args.length == 5) {
                        suggestions.add("100");
                    }
                    break;
                case "delete":
                    if (args.length == 2) {
                        Result<List<Tag>> result = tagService.getAvailableTags(null);
                        if (result.isSuccess()) {
                            for (Tag tag : result.getValue()) {
                                suggestions.add(tag.getName());
                            }
                        }
                    }
                    break;
                case "edit":
                    if (args.length == 2) {
                        Result<List<Tag>> result = tagService.getAvailableTags(null);
                        if (result.isSuccess()) {
                            for (Tag tag : result.getValue()) {
                                suggestions.add(tag.getName());
                            }
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
                        }
                    }
                    break;
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

    private List<String> filterSuggestions(List<String> suggestions, String prefix) {
        if (prefix.isEmpty()) {
            return suggestions;
        }

        List<String> filtered = new ArrayList<>();
        String lowerPrefix = prefix.toLowerCase();

        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase().startsWith(lowerPrefix)) {
                filtered.add(suggestion);
            }
        }

        return filtered;
    }
}
