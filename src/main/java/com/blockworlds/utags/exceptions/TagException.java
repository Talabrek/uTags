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
class TagNotFoundException extends TagException {
    
    /**
     * Creates a new TagNotFoundException.
     *
     * @param tagName The name of the tag that was not found
     */
    public TagNotFoundException(String tagName) {
        super("Tag not found: " + tagName);
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return "The specified tag does not exist.";
    }
}

/**
 * Exception thrown when a player does not have permission to use a tag.
 */
class PermissionDeniedException extends TagException {
    
    /**
     * Creates a new PermissionDeniedException.
     *
     * @param permission The permission that was denied
     */
    public PermissionDeniedException(String permission) {
        super("Permission denied: " + permission);
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return "You don't have permission to use this feature.";
    }
}

/**
 * Exception thrown when tag validation fails.
 */
class ValidationException extends TagException {
    
    /**
     * Creates a new ValidationException.
     *
     * @param validationMessage The validation error message
     */
    public ValidationException(String validationMessage) {
        super("Validation failed: " + validationMessage);
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return getMessage().substring("Validation failed: ".length());
    }
}

/**
 * Exception thrown when a database operation fails.
 */
class DatabaseException extends TagException {
    
    /**
     * Creates a new DatabaseException.
     *
     * @param message The error message
     * @param cause The cause of the exception
     */
    public DatabaseException(String message, Throwable cause) {
        super("Database error: " + message, cause);
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return "A database error occurred. Please contact an administrator.";
    }
}

/**
 * Exception thrown when a player has reached their maximum number of custom tags.
 */
class MaxCustomTagsException extends TagException {
    
    /**
     * Creates a new MaxCustomTagsException.
     *
     * @param playerName The name of the player
     */
    public MaxCustomTagsException(String playerName) {
        super("Player " + playerName + " has reached their maximum number of custom tags.");
    }
    
    @Override
    public String getUserFriendlyMessage() {
        return "You have reached the maximum number of custom tags. Unlock more slots by becoming a premium member.";
    }
}
