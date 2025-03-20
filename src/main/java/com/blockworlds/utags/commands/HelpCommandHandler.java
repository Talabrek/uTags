package com.blockworlds.utags.commands;

import com.blockworlds.utags.exceptions.ValidationException;
import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.ErrorHandler;
import com.blockworlds.utags.utils.MessageUtils;
import com.blockworlds.utags.utils.Preconditions;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Command handler for help commands in the uTags plugin.
 * Handles "/tag help [page]" commands to provide plugin usage information
 * with pagination support.
 */
public class HelpCommandHandler extends AbstractCommandHandler {

    // Constants for help display
    private static final int LINES_PER_PAGE = 8;
    private static final String HEADER_FORMAT = ChatColor.GOLD + "=== uTags Help (Page %d of %d) ===";
    private static final String NEXT_PAGE_FORMAT = ChatColor.GOLD + "Type /tag help %d for the next page.";
    
    private final ErrorHandler errorHandler;

    /**
     * Creates a new HelpCommandHandler.
     *
     * @param plugin The uTags plugin instance
     */
    public HelpCommandHandler(uTags plugin) {
        super(plugin);
        this.errorHandler = new ErrorHandler(plugin);
    }

    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        try {
            // Default to page 1 if no page specified
            int page = 1;
            
            if (args.length >= 1) {
                try {
                    page = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    throw new ValidationException("Invalid page number. Please use a number.");
                }
            }
            
            displayHelp(sender, page);
            return true;
        } catch (ValidationException e) {
            return errorHandler.handleException(e, sender, "displaying help");
        } catch (Exception e) {
            return errorHandler.handleException(e, sender, "processing help command");
        }
    }

    /**
     * Displays the help page to a user.
     * Shows general commands to all users and admin commands only to users with admin permissions.
     *
     * @param sender The command sender to display help to
     * @param page The page number to display
     * @throws ValidationException If the page number is invalid
     */
    private void displayHelp(CommandSender sender, int page) throws ValidationException {
        // Build the list of help messages
        List<String> helpLines = buildHelpLines(sender);
        
        // Calculate pagination info
        int totalPages = (int) Math.ceil((double) helpLines.size() / LINES_PER_PAGE);
        
        // Validate page number
        Preconditions.checkRange(page, 1, totalPages, 
            "Invalid page number. Valid pages: 1-" + totalPages);
        
        // Calculate start and end indices for the current page
        int startIndex = (page - 1) * LINES_PER_PAGE;
        int endIndex = Math.min(helpLines.size(), startIndex + LINES_PER_PAGE);

        // Display the header
        sender.sendMessage(String.format(HEADER_FORMAT, page, totalPages));
        
        // Display the help lines for this page
        for (int i = startIndex; i < endIndex; i++) {
            sender.sendMessage(helpLines.get(i));
        }

        // Display next page prompt if there are more pages
        if (page < totalPages) {
            sender.sendMessage(String.format(NEXT_PAGE_FORMAT, page + 1));
        }
    }
    
    /**
     * Builds the complete list of help lines based on sender permissions.
     *
     * @param sender The command sender
     * @return A list of formatted help lines
     */
    private List<String> buildHelpLines(CommandSender sender) {
        List<String> helpLines = new ArrayList<>();

        // Add general help commands for all users
        helpLines.add(ChatColor.YELLOW + "Available commands:");
        helpLines.add(ChatColor.GREEN + "/tag - " + ChatColor.WHITE + "Open the tag GUI menu.");
        helpLines.add(ChatColor.GREEN + "/tag set [tag]- " + ChatColor.WHITE + "Quick set a prefix tag.");
        helpLines.add(ChatColor.GREEN + "/tag request [tag] - " + ChatColor.WHITE + "Request a custom tag. (Requires Veteran or Premium Membership)");
        helpLines.add(ChatColor.GREEN + "/tag help [page] - " + ChatColor.WHITE + "Shows this help menu.");

        // Add admin commands only if the sender has the appropriate permission
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
        
        return helpLines;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> pageNumbers = new ArrayList<>();
            
            // Calculate how many pages to show based on permissions
            int totalPages = calculateTotalPagesForSender(sender);
            
            // Add page numbers as suggestions
            for (int i = 1; i <= totalPages; i++) {
                pageNumbers.add(String.valueOf(i));
            }
            
            return filterSuggestions(pageNumbers, args[0]);
        }
        
        return getEmptyTabCompletions();
    }
    
    /**
     * Calculates the total number of help pages available for the sender.
     *
     * @param sender The command sender
     * @return The total number of pages
     */
    private int calculateTotalPagesForSender(CommandSender sender) {
        // More lines are shown to admins
        int totalLines = sender.hasPermission("utags.admin") ? 12 : 5;
        return (int) Math.ceil((double) totalLines / LINES_PER_PAGE);
    }
}
