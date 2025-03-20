package com.blockworlds.utags.utils;

import com.blockworlds.utags.exceptions.*;
import com.blockworlds.utags.uTags;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
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
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleException(Exception e, CommandSender sender, String operation) {
        if (e instanceof TagException) {
            return handleTagException((TagException) e, sender);
        } else {
            return handleGenericException(e, sender, operation);
        }
    }

    /**
     * Handles a TagException, providing specific feedback based on the exception type.
     *
     * @param e The TagException to handle
     * @param sender The command sender to provide feedback to
     * @return Always returns false for convenience in command handlers
     */
    private boolean handleTagException(TagException e, CommandSender sender) {
        // Log the exception with appropriate level based on exception type
        Level logLevel = determineLogLevel(e);
        logger.log(logLevel, e.getMessage(), e);
        
        // Provide user-friendly feedback
        MessageUtils.sendError(sender, e.getUserFriendlyMessage());
        
        return false;
    }

    /**
     * Determines the appropriate log level based on the exception type.
     *
     * @param e The exception to check
     * @return The appropriate log level
     */
    private Level determineLogLevel(TagException e) {
        if (e instanceof ValidationException || e instanceof PermissionDeniedException || e instanceof MaxCustomTagsException) {
            // User input errors or expected conditions - WARNING level
            return Level.WARNING;
        } else if (e instanceof DatabaseException || e instanceof ConfigurationException) {
            // System-level errors - SEVERE level
            return Level.SEVERE;
        } else {
            // Default to WARNING for other TagExceptions
            return Level.WARNING;
        }
    }

    /**
     * Handles a generic exception, providing general feedback.
     *
     * @param e The exception to handle
     * @param sender The command sender to provide feedback to
     * @param operation The operation being performed when the exception occurred
     * @return Always returns false for convenience in command handlers
     */
    private boolean handleGenericException(Exception e, CommandSender sender, String operation) {
        // Log the exception
        logger.log(Level.SEVERE, "Error during " + operation + ": " + e.getMessage(), e);
        
        // Provide general feedback
        MessageUtils.sendError(sender, "An error occurred while " + operation + ". Please check the server logs.");
        
        return false;
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
     * Logs a debug message to the console if debug logging is enabled.
     *
     * @param message The debug message to log
     */
    public void logDebug(String message) {
        logger.fine(message);
    }

    /**
     * Handles a tag not found error.
     *
     * @param tagName The name of the tag that was not found
     * @param sender The command sender to provide feedback to
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleTagNotFound(String tagName, CommandSender sender) {
        TagNotFoundException e = new TagNotFoundException(tagName);
        logger.fine(e.getMessage());
        MessageUtils.sendError(sender, e.getUserFriendlyMessage());
        return false;
    }

    /**
     * Handles a validation error.
     *
     * @param validationMessage The validation error message
     * @param sender The command sender to provide feedback to
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleValidationError(String validationMessage, CommandSender sender) {
        ValidationException e = new ValidationException(validationMessage);
        logger.fine(e.getMessage());
        MessageUtils.sendError(sender, e.getUserFriendlyMessage());
        return false;
    }

    /**
     * Handles a permission error.
     *
     * @param permission The permission that was required
     * @param sender The command sender to provide feedback to
     * @return Always returns false for convenience in command handlers
     */
    public boolean handlePermissionError(String permission, CommandSender sender) {
        PermissionDeniedException e = new PermissionDeniedException(permission);
        logger.fine(e.getMessage() + " for " + sender.getName());
        MessageUtils.sendError(sender, e.getUserFriendlyMessage());
        return false;
    }

    /**
     * Handles a not-a-player error.
     *
     * @param sender The command sender
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleNotPlayerError(CommandSender sender) {
        MessageUtils.sendError(sender, "This command can only be used by players.");
        return false;
    }

    /**
     * Handles a database error.
     *
     * @param operation The database operation that failed
     * @param message The error message
     * @param cause The cause of the exception
     * @param sender The command sender to provide feedback to
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleDatabaseError(String operation, String message, Throwable cause, CommandSender sender) {
        DatabaseException e = new DatabaseException(operation, message, cause);
        logger.log(Level.SEVERE, e.getMessage(), e);
        MessageUtils.sendError(sender, e.getUserFriendlyMessage());
        return false;
    }

    /**
     * Handles a max custom tags error.
     *
     * @param playerName The name of the player
     * @param maxTags The maximum number of tags allowed
     * @param sender The command sender to provide feedback to
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleMaxCustomTagsError(String playerName, int maxTags, CommandSender sender) {
        MaxCustomTagsException e = new MaxCustomTagsException(playerName, maxTags);
        logger.fine(e.getMessage());
        MessageUtils.sendError(sender, e.getUserFriendlyMessage());
        return false;
    }

    /**
     * Handles a tag request error.
     *
     * @param message The error message
     * @param sender The command sender to provide feedback to
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleTagRequestError(String message, CommandSender sender) {
        TagRequestException e = new TagRequestException(message);
        logger.warning(e.getMessage());
        MessageUtils.sendError(sender, e.getUserFriendlyMessage());
        return false;
    }

    /**
     * Handles a menu error.
     *
     * @param message The error message
     * @param cause The cause of the exception
     * @param sender The command sender to provide feedback to
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleMenuError(String message, Throwable cause, CommandSender sender) {
        MenuException e = new MenuException(message, cause);
        logger.warning(e.getMessage(), e);
        MessageUtils.sendError(sender, e.getUserFriendlyMessage());
        return false;
    }

    /**
     * Handles a command error.
     *
     * @param command The command that failed
     * @param message The error message
     * @param sender The command sender to provide feedback to
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleCommandError(String command, String message, CommandSender sender) {
        CommandException e = new CommandException(command, message);
        logger.warning(e.getMessage());
        MessageUtils.sendError(sender, e.getUserFriendlyMessage());
        return false;
    }

    /**
     * Handles a configuration error.
     *
     * @param configKey The configuration key that caused the error
     * @param message The error message
     * @param sender The command sender to provide feedback to
     * @return Always returns false for convenience in command handlers
     */
    public boolean handleConfigurationError(String configKey, String message, CommandSender sender) {
        ConfigurationException e = new ConfigurationException(configKey, message);
        logger.severe(e.getMessage());
        MessageUtils.sendError(sender, e.getUserFriendlyMessage());
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

    /**
     * Checks if an object is null and handles the error if it is.
     *
     * @param object The object to check
     * @param objectName The name of the object for the error message
     * @param sender The command sender to provide feedback to
     * @return True if the object is not null, false otherwise
     */
    public boolean checkNotNull(Object object, String objectName, CommandSender sender) {
        if (object == null) {
            MessageUtils.sendError(sender, objectName + " not found.");
            return false;
        }
        return true;
    }

    /**
     * Creates and returns a wrapped exception runner that handles exceptions.
     *
     * @param sender The command sender for error feedback
     * @param operation The operation being performed
     * @return A runnable wrapper that handles exceptions
     */
    public ExceptionRunner createExceptionRunner(CommandSender sender, String operation) {
        return new ExceptionRunner(sender, operation);
    }

    /**
     * A wrapper class that handles exceptions in a runnable block.
     */
    public class ExceptionRunner {
        private final CommandSender sender;
        private final String operation;

        /**
         * Creates a new ExceptionRunner.
         *
         * @param sender The command sender for error feedback
         * @param operation The operation being performed
         */
        public ExceptionRunner(CommandSender sender, String operation) {
            this.sender = sender;
            this.operation = operation;
        }

        /**
         * Runs the provided runnable and handles any exceptions.
         *
         * @param runnable The runnable to execute
         * @return True if the runnable completed without exceptions, false otherwise
         */
        public boolean run(ThrowingRunnable runnable) {
            try {
                runnable.run();
                return true;
            } catch (Exception e) {
                handleException(e, sender, operation);
                return false;
            }
        }

        /**
         * A functional interface for runnables that can throw exceptions.
         */
        @FunctionalInterface
        public interface ThrowingRunnable {
            void run() throws Exception;
        }
    }
}
