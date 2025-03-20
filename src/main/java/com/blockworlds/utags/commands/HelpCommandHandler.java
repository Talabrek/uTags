package com.blockworlds.utags.commands;

import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.MessageUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Command handler for help commands in the uTags plugin.
 * Handles "/tag help [page]" commands to provide plugin usage information.
 */
public class HelpCommandHandler extends AbstractCommandHandler {

    private static final int LINES_PER_PAGE = 8;

    /**
     * Creates a new HelpCommandHandler.
     *
     * @param plugin The uTags plugin instance
     */
    public HelpCommandHandler(uTags plugin) {
        super(plugin);
    }

    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        // Default to page 1 if no page specified
        int page = 1;
        
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                MessageUtils.sendError(sender, "Invalid page number. Please use a number.");
                return false;
            }
        }
        
        displayHelp(sender, page);
        return true;
    }

    /**
     * Displays the help page to a user.
     *
     * @param sender The command sender to display help to
     * @param page The page number to display
     */
    private void displayHelp(CommandSender sender, int page) {
        List<String> helpLines = new ArrayList<>();

        // General help commands
        helpLines.add(ChatColor.GREEN + "/tag - " + ChatColor.WHITE + "Open the tag GUI menu.");
        helpLines.add(ChatColor.GREEN + "/tag set [tag] - " + ChatColor.WHITE + "Quick set a prefix tag.");
        helpLines.add(ChatColor.GREEN + "/tag request [tag] - " + ChatColor.WHITE + "Request a custom tag. (Requires Veteran or Premium Membership)");
        helpLines.add(ChatColor.GREEN + "/tag help [page] - " + ChatColor.WHITE + "Shows this help menu.");

        // Add admin commands only if the player has the appropriate permission
        if (sender.hasPermission("utags.admin")) {
            helpLines.add(ChatColor.GOLD + " ");
            helpLines.add(ChatColor.YELLOW + "Admin commands:");
            helpLines.add(ChatColor.YELLOW + "/tag admin create [name] [display] [type] [weight] - " + ChatColor.WHITE + "Create a new tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin delete [name] - " + ChatColor.WHITE + "Delete an existing tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin edit [tagname] [attribute] [newvalue] - " + ChatColor.WHITE + "Edit an existing tag.");
            helpLines.add(ChatColor.YELLOW + "/tag admin requests - " + ChatColor.WHITE + "View pending custom tag requests.");
            helpLines.add(ChatColor.RED + "/tag admin purge tags - " + ChatColor.WHITE + "Purge all tags from the database.");
            helpLines.add(ChatColor.RED + "/tag admin purge requests - " + ChatColor.WHITE + "Purge all custom tag requests from the database.");
        }

        int totalPages = (int) Math.ceil((double) helpLines.size() / LINES_PER_PAGE);
        int startIndex = (page - 1) * LINES_PER_PAGE;
        int endIndex = Math.min(helpLines.size(), startIndex + LINES_PER_PAGE);

        if (page < 1 || page > totalPages) {
            MessageUtils.sendError(sender, "Invalid page number. Valid pages: 1-" + totalPages);
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== uTags Help (Page " + page + " of " + totalPages + ") ===");
        
        for (int i = startIndex; i < endIndex; i++) {
            sender.sendMessage(helpLines.get(i));
        }

        if (page < totalPages) {
            sender.sendMessage(ChatColor.GOLD + "Type /tag help " + (page + 1) + " for the next page.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> pageNumbers = new ArrayList<>();
            int totalPages = sender.hasPermission("utags.admin") ? 2 : 1;
            
            for (int i = 1; i <= totalPages; i++) {
                pageNumbers.add(String.valueOf(i));
            }
            
            return filterSuggestions(pageNumbers, args[0]);
        }
        
        return getEmptyTabCompletions();
    }
}
