package com.blockworlds.utags.controller.impl;

import com.blockworlds.utags.controller.CommandController;
import com.blockworlds.utags.model.Result;
import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.service.MenuService;
import com.blockworlds.utags.service.TagService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for handling basic tag commands.
 */
public class TagCommandController implements CommandController {

    private final TagService tagService;
    private final MenuService menuService;

    /**
     * Creates a new TagCommandController.
     */
    public TagCommandController(TagService tagService, MenuService menuService) {
        this.tagService = tagService;
        this.menuService = menuService;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // No arguments - open the tag menu
        if (args.length == 0) {
            menuService.openTagMenu(player);
            return true;
        }

        // Handle set command
        if ("set".equalsIgnoreCase(args[0])) {
            return handleSetCommand(player, args);
        }

        return false; // Not handled, pass to other controllers
    }

    private boolean handleSetCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /tag set [tag]");
            return true;
        }

        String tagName = args[1];
        Result<String> displayResult = tagService.getTagDisplayByName(tagName);
        
        if (!displayResult.isSuccess()) {
            player.sendMessage(ChatColor.RED + displayResult.getMessage());
            return true;
        }
        
        Result<Boolean> result = tagService.setPlayerTag(player, displayResult.getValue(), TagType.PREFIX);
        
        if (result.isSuccess() && result.getValue()) {
            player.sendMessage(ChatColor.GREEN + "Your tag has been updated to: " + 
                ChatColor.translateAlternateColorCodes('&', displayResult.getValue()));
        } else {
            player.sendMessage(ChatColor.RED + "Failed to update your tag: " + result.getMessage());
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return suggestions;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            suggestions.add("set");
        } else if (args.length == 2 && "set".equalsIgnoreCase(args[0])) {
            // Get all tags the player has permission to use
            Result<List<Tag>> tagsResult = tagService.getAvailableTags(TagType.PREFIX);
            if (tagsResult.isSuccess()) {
                for (Tag tag : tagsResult.getValue()) {
                    if (player.hasPermission("utags.tag." + tag.getName())) {
                        suggestions.add(tag.getName());
                    }
                }
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
