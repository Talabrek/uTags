package com.blockworlds.utags.controller.impl;

import com.blockworlds.utags.controller.CommandController;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for handling the help command.
 */
public class HelpCommandController implements CommandController {

    private static final int LINES_PER_PAGE = 8;

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        int page = 1;

        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid page number. Please use a number.");
                return true;
            }
        }

        displayHelp(player, page);
        return true;
    }

    private void displayHelp(Player player, int page) {
        List<String> helpLines = new ArrayList<>();

        // Basic commands
        helpLines.add(ChatColor.YELLOW + "Available commands:");
        helpLines.add(ChatColor.GREEN + "/tag - Open the tag GUI menu.");
        helpLines.add(ChatColor.GREEN + "/tag set [tag] - Quick set a prefix tag.");
        helpLines.add(ChatColor.GREEN + "/tag request [tag] - Request a custom tag. (Requires permissions)");
        helpLines.add(ChatColor.GREEN + "/tag help [page] - Shows this help menu.");

        // Admin commands if player has permission
        if (player.hasPermission("utags.admin")) {
            helpLines.add(ChatColor.YELLOW + "Admin commands:");
            helpLines.add(ChatColor.YELLOW + "/tag admin create [name] [display] [type] [weight] - Create a new tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin delete [name] - Delete an existing tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin edit [tagname] [attribute] [newvalue] - Edit a tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin requests - View pending tag requests.");
            helpLines.add(ChatColor.RED + "/tag admin purge [tags|requests] - Purge all tags or requests.");
        }

        int totalPages = (int) Math.ceil((double) helpLines.size() / LINES_PER_PAGE);
        
        if (page < 1 || page > totalPages) {
            player.sendMessage(ChatColor.RED + "Invalid page number. Valid pages: 1-" + totalPages);
            return;
        }

        int startIndex = (page - 1) * LINES_PER_PAGE;
        int endIndex = Math.min(helpLines.size(), startIndex + LINES_PER_PAGE);

        player.sendMessage(ChatColor.GOLD + "=== Help (Page " + page + " of " + totalPages + ") ===");
        
        for (int i = startIndex; i < endIndex; i++) {
            player.sendMessage(helpLines.get(i));
        }

        if (page < totalPages) {
            player.sendMessage(ChatColor.GOLD + "Type /tag help " + (page + 1) + " for the next page.");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return suggestions;
        }

        if (args.length == 1) {
            suggestions.add("1");
            suggestions.add("2");
            
            // Add page 3 if player has admin permissions (more help content)
            if (sender.hasPermission("utags.admin")) {
                suggestions.add("3");
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
