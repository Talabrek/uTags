package com.blockworlds.utags.commands;

import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.MessageUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for command handlers in the uTags plugin.
 * Provides common functionality for all command handlers.
 */
public abstract class AbstractCommandHandler implements CommandHandler {
    
    protected final uTags plugin;
    
    /**
     * Creates a new AbstractCommandHandler.
     *
     * @param plugin The uTags plugin instance
     */
    public AbstractCommandHandler(uTags plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Checks if the command sender is a player and handles the error message if not.
     *
     * @param sender The command sender to check
     * @return True if the sender is a player, false otherwise
     */
    protected boolean isPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            MessageUtils.sendError(sender, "This command can only be used by players.");
            return false;
        }
        return true;
    }
    
    /**
     * Checks if a player has a required permission and handles the error message if not.
     *
     * @param player The player to check
     * @param permission The permission to check for
     * @return True if the player has the permission, false otherwise
     */
    protected boolean hasPermission(Player player, String permission) {
        if (!player.hasPermission(permission)) {
            MessageUtils.sendError(player, "You don't have permission to use this command.");
            return false;
        }
        return true;
    }
    
    /**
     * Gets a common empty list for tab completion.
     *
     * @return An empty list of strings
     */
    protected List<String> getEmptyTabCompletions() {
        return new ArrayList<>();
    }
    
    /**
     * Checks if the command has the expected number of arguments.
     *
     * @param args The command arguments
     * @param expected The expected number of arguments
     * @return True if the command has the expected number of arguments, false otherwise
     */
    protected boolean hasExpectedArgs(String[] args, int expected) {
        return args != null && args.length >= expected;
    }
    
    /**
     * Checks if a string equals any of the provided options (case insensitive).
     *
     * @param input The input string to check
     * @param options The options to check against
     * @return True if the input matches any option, false otherwise
     */
    protected boolean equalsAny(String input, String... options) {
        if (input == null) {
            return false;
        }
        
        for (String option : options) {
            if (input.equalsIgnoreCase(option)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Filters a list of suggestions based on a partial string.
     *
     * @param suggestions The list of suggestions to filter
     * @param partial The partial string to filter by
     * @return A filtered list of suggestions
     */
    protected List<String> filterSuggestions(List<String> suggestions, String partial) {
        if (partial == null || partial.isEmpty()) {
            return suggestions;
        }
        
        List<String> filtered = new ArrayList<>();
        String lowercasePartial = partial.toLowerCase();
        
        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase().startsWith(lowercasePartial)) {
                filtered.add(suggestion);
            }
        }
        
        return filtered;
    }
}
