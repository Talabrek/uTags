package com.blockworlds.utags.controller;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Interface for handling commands in the uTags plugin.
 */
public interface CommandController {
    /**
     * Executes a command.
     *
     * @param sender The sender of the command
     * @param args The command arguments
     * @return True if the command was handled, false otherwise
     */
    boolean execute(CommandSender sender, String[] args);
    
    /**
     * Provides tab completion suggestions for a command.
     *
     * @param sender The sender of the command
     * @param args The command arguments
     * @return A list of tab completion suggestions
     */
    List<String> tabComplete(CommandSender sender, String[] args);
}
