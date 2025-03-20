package com.blockworlds.utags.commands;

import com.blockworlds.utags.uTags;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main command handler for the uTags plugin.
 * Delegates commands to specialized handlers based on the first argument.
 */
public class MainCommandHandler implements CommandExecutor, TabCompleter {

    private final uTags plugin;
    private final Map<String, CommandHandler> commandHandlers;
    private final HelpCommandHandler helpHandler;

    /**
     * Creates a new MainCommandHandler.
     *
     * @param plugin The uTags plugin instance
     */
    public MainCommandHandler(uTags plugin) {
        this.plugin = plugin;
        this.commandHandlers = new HashMap<>();
        
        // Initialize command handlers
        helpHandler = new HelpCommandHandler(plugin);
        
        // Register command handlers
        registerCommandHandler("admin", new AdminCommandHandler(plugin));
        registerCommandHandler("help", helpHandler);
        registerCommandHandler("request", new RequestCommandHandler(plugin));
        registerCommandHandler("set", new SetTagCommandHandler(plugin));
    }

    /**
     * Registers a command handler for a specific command.
     *
     * @param command The command to register the handler for
     * @param handler The handler to register
     */
    private void registerCommandHandler(String command, CommandHandler handler) {
        commandHandlers.put(command.toLowerCase(), handler);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // If no arguments, open the tag menu (if sender is a player)
        if (args.length == 0) {
            if (sender instanceof Player) {
                plugin.getTagMenuManager().openTagMenu((Player) sender);
                return true;
            } else {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }
        }

        // Get the first argument (sub-command)
        String subCommand = args[0].toLowerCase();
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        // Get the handler for this sub-command
        CommandHandler handler = commandHandlers.get(subCommand);

        // If no handler found, show help
        if (handler == null) {
            return helpHandler.handleCommand(sender, new String[0]);
        }

        // Handle the command with the appropriate handler
        return handler.handleCommand(sender, subArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        // First level commands
        if (args.length == 1) {
            suggestions.addAll(commandHandlers.keySet());
            return filterSuggestions(suggestions, args[0]);
        }

        // Delegate to the appropriate handler for deeper tab completion
        String subCommand = args[0].toLowerCase();
        CommandHandler handler = commandHandlers.get(subCommand);
        
        if (handler != null) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return handler.onTabComplete(sender, subArgs);
        }

        return suggestions;
    }
    
    /**
     * Filters a list of suggestions based on a partial string.
     *
     * @param suggestions The list of suggestions to filter
     * @param partial The partial string to filter by
     * @return A filtered list of suggestions
     */
    private List<String> filterSuggestions(List<String> suggestions, String partial) {
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
