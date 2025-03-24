package com.blockworlds.utags.util;

import com.blockworlds.utags.model.Result;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for error handling in the uTags plugin.
 * Provides methods for handling exceptions and providing feedback.
 */
public class ErrorHandler {
    private final JavaPlugin plugin;
    private final Logger logger;
    
    /**
     * Creates a new ErrorHandler.
     *
     * @param plugin The JavaPlugin instance
     */
    public ErrorHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    /**
     * Handles an exception, logs it, and provides feedback to the user.
     *
     * @param e The exception to handle
     * @param sender The command sender to provide feedback to
     * @param operation The operation being performed when the exception occurred
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleException(Exception e, CommandSender sender, String operation) {
        logger.log(Level.SEVERE, "Error during " + operation + ": " + e.getMessage(), e);
        MessageUtils.sendError(sender, "An unexpected error occurred while " + operation + ". Please contact an administrator.");
        return false;
    }
    
    /**
     * Handles a result object, providing feedback based on success/failure.
     *
     * @param result The result to handle
     * @param sender The command sender to provide feedback to
     * @param successMessage The message to send on success
     * @param failureMessage The message to send on failure
     * @param args Additional arguments for the message
     * @param <T> The type of the result value
     * @return The result value if successful, null otherwise
     */
    public <T> T handleResult(Result<T> result, CommandSender sender, 
                            String successMessage, String failureMessage, Object... args) {
        if (result.isSuccess()) {
            if (successMessage != null) {
                MessageUtils.sendSuccess(sender, successMessage, args);
            }
            return result.getValue();
        } else {
            if (result.getError() != null) {
                logger.log(Level.WARNING, result.getMessage(), result.getError());
            } else {
                logger.warning(result.getMessage());
            }
            
            MessageUtils.sendError(sender, failureMessage, args);
            return null;
        }
    }
    
    /**
     * Logs an error message to the console.
     *
     * @param message The error message to log
     */
    public void logError(String message) {
        logger.severe(message);
    }
    
    /**
     * Logs an error message with exception to the console.
     *
     * @param message The error message to log
     * @param e The exception associated with the error
     */
    public void logError(String message, Exception e) {
        logger.log(Level.SEVERE, message, e);
    }
    
    /**
     * Logs a warning message to the console.
     *
     * @param message The warning message to log
     */
    public void logWarning(String message) {
        logger.warning(message);
    }
    
    /**
     * Logs an informational message to the console.
     *
     * @param message The informational message to log
     */
    public void logInfo(String message) {
        logger.info(message);
    }
    
    /**
     * Logs a security event to the console.
     *
     * @param level The log level
     * @param player The player involved
     * @param action The security action
     * @param details The event details
     */
    public void logSecurityEvent(Level level, Player player, String action, String details) {
        if (player == null) {
            logger.log(level, "[SECURITY][" + action + "] " + details);
            return;
        }
        
        String playerInfo = player.getName() + " (" + player.getUniqueId() + ")";
        logger.log(level, "[SECURITY][" + action + "] Player " + playerInfo + ": " + details);
    }
    
    /**
     * Checks if the command sender is a player and handles the error if not.
     *
     * @param sender The command sender to check
     * @return The player if the sender is a player, null otherwise
     */
    public Player checkPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            MessageUtils.sendError(sender, "This command can only be used by players.");
            return null;
        }
        return (Player) sender;
    }
    
    /**
     * Checks if a player has the required permission and handles the error if not.
     *
     * @param player The player to check
     * @param permission The permission to check for
     * @return True if the player has the permission, false otherwise
     */
    public boolean checkPermission(Player player, String permission) {
        if (!player.hasPermission(permission)) {
            MessageUtils.sendError(player, "You don't have permission to use this command.");
            return false;
        }
        return true;
    }
    
    /**
     * Checks if a tag exists and handles the error if not.
     * 
     * @param tagName The tag name to check
     * @param tagDisplay The tag display value
     * @param sender The command sender
     * @return True if the tag exists, false otherwise
     */
    public boolean checkTagExists(String tagName, String tagDisplay, CommandSender sender) {
        if (tagDisplay == null) {
            MessageUtils.sendError(sender, "Tag '" + tagName + "' does not exist.");
            return false;
        }
        return true;
    }
}
