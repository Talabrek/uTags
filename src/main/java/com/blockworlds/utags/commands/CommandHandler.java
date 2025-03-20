package com.blockworlds.utags.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Interface for command handlers in the uTags plugin.
 * Each command handler is responsible for handling a specific subset of commands.
 */
public interface CommandHandler {
    
    /**
     * Executes a command.
     *
     * @param sender The sender of the command
     * @param args The command arguments
     * @return True if the command was handled successfully, false otherwise
     */
    boolean handleCommand(CommandSender sender, String[] args);
    
    /**
     * Provides tab completion suggestions for a command.
     *
     * @param sender The sender of the command
     * @param args The command arguments
     * @return A list of tab completion suggestions
     */
    List<String> onTabComplete(CommandSender sender, String[] args);
    
    /**
     * Casts a CommandSender to Player if possible.
     *
     * @param sender The CommandSender to cast
     * @return The Player if casting is successful, null otherwise
     */
    default Player asPlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        return null;
    }
}
