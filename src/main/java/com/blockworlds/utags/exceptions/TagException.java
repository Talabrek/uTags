package com.blockworlds.utags.exceptions;

/**
 * Base exception class for all uTags plugin exceptions.
 * Provides common functionality for all plugin exceptions.
 */
public class TagException extends Exception {
    
    /**
     * Creates a new TagException with the specified message.
     *
     * @param message The error message
     */
    public TagException(String message) {
        super(message);
    }
    
    /**
     * Creates a new TagException with the specified message and cause.
     *
     * @param message The error message
     * @param cause The cause of the exception
     */
    public TagException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Gets a user-friendly error message for this exception.
     *
     * @return A user-friendly error message
     */
    public String getUserFriendlyMessage() {
        return getMessage();
    }
}

/**
 * Exception thrown when a tag is not found.
 */
public class TagNotFoundException extends TagException {
    private final String tagName;
    
    /**
     * Creates a new TagNotFoundException.
     *
     * @param tagName The name of the tag that was not found
     */
    public TagNotFoundException(String tagName) {
        super("Tag not found: " + tagName);
        this.tagName = tagName;
    }
    
    /**
     * Gets the name of the tag that was not found.
     *
     * @return The tag name
     */
    public String getTagName() {
        return tagName;
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return "The tag '" + tagName + "' does not exist.";
    }
}

/**
 * Exception thrown when a player does not have permission to use a tag.
 */
public class PermissionDeniedException extends TagException {
    private final String permission;
    
    /**
     * Creates a new PermissionDeniedException.
     *
     * @param permission The permission that was denied
     */
    public PermissionDeniedException(String permission) {
        super("Permission denied: " + permission);
        this.permission = permission;
    }
    
    /**
     * Gets the permission that was denied.
     *
     * @return The permission
     */
    public String getPermission() {
        return permission;
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return "You don't have permission to use this feature.";
    }
}

/**
 * Exception thrown when tag validation fails.
 */
public class ValidationException extends TagException {
    private final String validationMessage;
    
    /**
     * Creates a new ValidationException.
     *
     * @param validationMessage The validation error message
     */
    public ValidationException(String validationMessage) {
        super("Validation failed: " + validationMessage);
        this.validationMessage = validationMessage;
    }
    
    /**
     * Gets the validation error message.
     *
     * @return The validation message
     */
    public String getValidationMessage() {
        return validationMessage;
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return validationMessage;
    }
}

/**
 * Exception thrown when a database operation fails.
 */
public class DatabaseException extends TagException {
    private final String operation;
    
    /**
     * Creates a new DatabaseException.
     *
     * @param operation The database operation that failed
     * @param message The error message
     * @param cause The cause of the exception
     */
    public DatabaseException(String operation, String message, Throwable cause) {
        super("Database error during " + operation + ": " + message, cause);
        this.operation = operation;
    }
    
    /**
     * Gets the database operation that failed.
     *
     * @return The operation
     */
    public String getOperation() {
        return operation;
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return "A database error occurred. Please contact an administrator.";
    }
}

/**
 * Exception thrown when a player has reached their maximum number of custom tags.
 */
public class MaxCustomTagsException extends TagException {
    private final String playerName;
    private final int maxTags;
    
    /**
     * Creates a new MaxCustomTagsException.
     *
     * @param playerName The name of the player
     * @param maxTags The maximum number of tags allowed
     */
    public MaxCustomTagsException(String playerName, int maxTags) {
        super("Player " + playerName + " has reached their maximum number of custom tags (" + maxTags + ").");
        this.playerName = playerName;
        this.maxTags = maxTags;
    }
    
    /**
     * Gets the name of the player.
     *
     * @return The player name
     */
    public String getPlayerName() {
        return playerName;
    }
    
    /**
     * Gets the maximum number of tags allowed.
     *
     * @return The maximum tags
     */
    public int getMaxTags() {
        return maxTags;
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return "You have reached the maximum number of custom tags (" + maxTags + "). Unlock more slots by becoming a premium member.";
    }
}

/**
 * Exception thrown when a tag request operation fails.
 */
public class TagRequestException extends TagException {
    
    /**
     * Creates a new TagRequestException.
     *
     * @param message The error message
     */
    public TagRequestException(String message) {
        super(message);
    }
    
    /**
     * Creates a new TagRequestException with a cause.
     *
     * @param message The error message
     * @param cause The cause of the exception
     */
    public TagRequestException(String message, Throwable cause) {
        super(message, cause);
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return "Your tag request could not be processed. " + getMessage();
    }
}

/**
 * Exception thrown when a menu operation fails.
 */
public class MenuException extends TagException {
    
    /**
     * Creates a new MenuException.
     *
     * @param message The error message
     */
    public MenuException(String message) {
        super(message);
    }
    
    /**
     * Creates a new MenuException with a cause.
     *
     * @param message The error message
     * @param cause The cause of the exception
     */
    public MenuException(String message, Throwable cause) {
        super(message, cause);
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return "An error occurred while using the tag menu. Please try again.";
    }
}

/**
 * Exception thrown when a command execution fails.
 */
public class CommandException extends TagException {
    private final String command;
    
    /**
     * Creates a new CommandException.
     *
     * @param command The command that failed
     * @param message The error message
     */
    public CommandException(String command, String message) {
        super("Error executing command '" + command + "': " + message);
        this.command = command;
    }
    
    /**
     * Gets the command that failed.
     *
     * @return The command
     */
    public String getCommand() {
        return command;
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return "An error occurred while executing your command. Please check the syntax and try again.";
    }
}

/**
 * Exception thrown when a configuration error occurs.
 */
public class ConfigurationException extends TagException {
    private final String configKey;
    
    /**
     * Creates a new ConfigurationException.
     *
     * @param configKey The configuration key that caused the error
     * @param message The error message
     */
    public ConfigurationException(String configKey, String message) {
        super("Configuration error for key '" + configKey + "': " + message);
        this.configKey = configKey;
    }
    
    /**
     * Gets the configuration key that caused the error.
     *
     * @return The configuration key
     */
    public String getConfigKey() {
        return configKey;
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return "A plugin configuration error occurred. Please contact an administrator.";
    }
}
