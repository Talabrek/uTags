package com.blockworlds.utags.utils;

import com.blockworlds.utags.exceptions.TagException;
import com.blockworlds.utags.uTags;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for centralized error handling in the uTags plugin.
 * Provides methods for handling exceptions, logging errors, and providing user feedback.
 */
public class ErrorHandler {

    private final uTags plugin;
    private final Logger logger;

    /**
     * Creates a new ErrorHandler.
     *
     * @param plugin The uTags plugin instance
     */
    public ErrorHandler(uTags plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Handles an exception, logs it, and provides feedback to the user.
     *
     * @param e The exception to handle
     * @param sender The command sender to provide feedback to
     * @param operation The operation being performed when the exception occurred
     */
    public void handleException(Exception e, CommandSender sender, String operation) {
        if (e instanceof TagException) {
            handleTagException((TagException) e, sender);
        } else {
            handleGenericException(e, sender, operation);
        }
    }

    /**
     * Handles a TagException, providing specific feedback based on the exception type.
     *
     * @param e The TagException to handle
     * @param sender The command sender to provide feedback to
     */
    private void handleTagException(TagException e, CommandSender sender) {
        // Log the exception
        logger.log(Level.WARNING, e.getMessage(), e);
        
        // Provide user-friendly feedback
        MessageUtils.sendError(sender, e.getUserFriendlyMessage());
    }

    /**
     * Handles a generic exception, providing general feedback.
     *
     * @param e The exception to handle
     * @param sender The command sender to provide feedback to
     * @param operation The operation being performed when the exception occurred
     */
    private void handleGenericException(Exception e, CommandSender sender, String operation) {
        // Log the exception
        logger.log(Level.SEVERE, "Error during " + operation + ": " + e.getMessage(), e);
        
        // Provide general feedback
        MessageUtils.sendError(sender, "An error occurred while " + operation + ". Please check the server logs.");
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
     * Logs a warning message with exception to the console.
     *
     * @param message The warning message to log
     * @param e The exception associated with the warning
     */
    public void logWarning(String message, Exception e) {
        logger.log(Level.WARNING, message, e);
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
     * Handles a validation error, logging it and providing feedback to the user.
     *
     * @param validationMessage The validation error message
     * @param sender The command sender to provide feedback to
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleValidationError(String validationMessage, CommandSender sender) {
        logger.fine("Validation error: " + validationMessage);
        MessageUtils.sendError(sender, validationMessage);
        return false;
    }

    /**
     * Handles a permission error, logging it and providing feedback to the user.
     *
     * @param permission The permission that was required
     * @param sender The command sender to provide feedback to
     * @return Always returns false for convenience in command handlers
     */
    public boolean handlePermissionError(String permission, CommandSender sender) {
        logger.fine("Permission denied: " + permission + " for " + sender.getName());
        MessageUtils.sendError(sender, "You don't have permission to use this feature.");
        return false;
    }

    /**
     * Handles a not-a-player error, providing feedback to the sender.
     *
     * @param sender The command sender
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleNotPlayerError(CommandSender sender) {
        MessageUtils.sendError(sender, "This command can only be used by players.");
        return false;
    }

    /**
     * Checks if the command sender is a player and handles the error if not.
     *
     * @param sender The command sender to check
     * @return The player if the sender is a player, null otherwise
     */
    public Player checkPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            handleNotPlayerError(sender);
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
            handlePermissionError(permission, player);
            return false;
        }
        return true;
    }
}
