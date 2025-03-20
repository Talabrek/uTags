package com.blockworlds.utags.utils;

import com.blockworlds.utags.uTags;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for standardized logging in the uTags plugin.
 * Provides methods for logging various events and actions with consistent formatting.
 */
public class LogUtils {
    
    private final uTags plugin;
    private final Logger logger;

    /**
     * Creates a new LogUtils instance.
     *
     * @param plugin The uTags plugin instance
     */
    public LogUtils(uTags plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Logs a player action.
     *
     * @param player The player performing the action
     * @param action The action being performed
     * @param result The result of the action
     */
    public void logPlayerAction(Player player, String action, String result) {
        logger.info(formatPlayerActionMessage(player, action, result));
    }

    /**
     * Logs a player action at the specified log level.
     *
     * @param level The log level
     * @param player The player performing the action
     * @param action The action being performed
     * @param result The result of the action
     */
    public void logPlayerAction(Level level, Player player, String action, String result) {
        logger.log(level, formatPlayerActionMessage(player, action, result));
    }

    /**
     * Formats a player action message.
     *
     * @param player The player performing the action
     * @param action The action being performed
     * @param result The result of the action
     * @return The formatted message
     */
    private String formatPlayerActionMessage(Player player, String action, String result) {
        return String.format("Player %s (%s) %s: %s", 
            player.getName(), player.getUniqueId(), action, result);
    }

    /**
     * Logs an admin action.
     *
     * @param player The admin performing the action
     * @param action The action being performed
     * @param target The target of the action
     * @param result The result of the action
     */
    public void logAdminAction(Player player, String action, String target, String result) {
        logger.info(formatAdminActionMessage(player, action, target, result));
    }

    /**
     * Logs an admin action at the specified log level.
     *
     * @param level The log level
     * @param player The admin performing the action
     * @param action The action being performed
     * @param target The target of the action
     * @param result The result of the action
     */
    public void logAdminAction(Level level, Player player, String action, String target, String result) {
        logger.log(level, formatAdminActionMessage(player, action, target, result));
    }

    /**
     * Formats an admin action message.
     *
     * @param player The admin performing the action
     * @param action The action being performed
     * @param target The target of the action
     * @param result The result of the action
     * @return The formatted message
     */
    private String formatAdminActionMessage(Player player, String action, String target, String result) {
        return String.format("Admin %s (%s) %s on %s: %s", 
            player.getName(), player.getUniqueId(), action, target, result);
    }

    /**
     * Logs a tag operation.
     *
     * @param player The player involved in the operation
     * @param operation The tag operation
     * @param tagName The name of the tag
     * @param result The result of the operation
     */
    public void logTagOperation(Player player, String operation, String tagName, String result) {
        logger.info(formatTagOperationMessage(player, operation, tagName, result));
    }

    /**
     * Logs a tag operation at the specified log level.
     *
     * @param level The log level
     * @param player The player involved in the operation
     * @param operation The tag operation
     * @param tagName The name of the tag
     * @param result The result of the operation
     */
    public void logTagOperation(Level level, Player player, String operation, String tagName, String result) {
        logger.log(level, formatTagOperationMessage(player, operation, tagName, result));
    }

    /**
     * Formats a tag operation message.
     *
     * @param player The player involved in the operation
     * @param operation The tag operation
     * @param tagName The name of the tag
     * @param result The result of the operation
     * @return The formatted message
     */
    private String formatTagOperationMessage(Player player, String operation, String tagName, String result) {
        return String.format("Tag operation: %s by %s on tag '%s'. Result: %s", 
            operation, player.getName(), tagName, result);
    }

    /**
     * Logs a database operation.
     *
     * @param operation The database operation
     * @param details The operation details
     * @param result The result of the operation
     */
    public void logDatabaseOperation(String operation, String details, String result) {
        logger.info(formatDatabaseOperationMessage(operation, details, result));
    }

    /**
     * Logs a database operation at the specified log level.
     *
     * @param level The log level
     * @param operation The database operation
     * @param details The operation details
     * @param result The result of the operation
     */
    public void logDatabaseOperation(Level level, String operation, String details, String result) {
        logger.log(level, formatDatabaseOperationMessage(operation, details, result));
    }

    /**
     * Formats a database operation message.
     *
     * @param operation The database operation
     * @param details The operation details
     * @param result The result of the operation
     * @return The formatted message
     */
    private String formatDatabaseOperationMessage(String operation, String details, String result) {
        return String.format("Database %s (%s): %s", operation, details, result);
    }

    /**
     * Logs a request operation.
     *
     * @param player The player making the request
     * @param requestType The type of request
     * @param details The request details
     * @param result The result of the request
     */
    public void logRequestOperation(Player player, String requestType, String details, String result) {
        logger.info(formatRequestOperationMessage(player, requestType, details, result));
    }

    /**
     * Logs a request operation at the specified log level.
     *
     * @param level The log level
     * @param player The player making the request
     * @param requestType The type of request
     * @param details The request details
     * @param result The result of the request
     */
    public void logRequestOperation(Level level, Player player, String requestType, String details, String result) {
        logger.log(level, formatRequestOperationMessage(player, requestType, details, result));
    }

    /**
     * Formats a request operation message.
     *
     * @param player The player making the request
     * @param requestType The type of request
     * @param details The request details
     * @param result The result of the request
     * @return The formatted message
     */
    private String formatRequestOperationMessage(Player player, String requestType, String details, String result) {
        return String.format("%s request by %s: %s. Result: %s", 
            requestType, player.getName(), details, result);
    }

    /**
     * Logs a plugin lifecycle event.
     *
     * @param event The lifecycle event
     * @param details The event details
     */
    public void logLifecycleEvent(String event, String details) {
        logger.info(formatLifecycleEventMessage(event, details));
    }

    /**
     * Logs a plugin lifecycle event at the specified log level.
     *
     * @param level The log level
     * @param event The lifecycle event
     * @param details The event details
     */
    public void logLifecycleEvent(Level level, String event, String details) {
        logger.log(level, formatLifecycleEventMessage(event, details));
    }

    /**
     * Formats a plugin lifecycle event message.
     *
     * @param event The lifecycle event
     * @param details The event details
     * @return The formatted message
     */
    private String formatLifecycleEventMessage(String event, String details) {
        return String.format("Plugin %s: %s", event, details);
    }

    /**
     * Logs plugin performance data.
     *
     * @param operation The operation being measured
     * @param executionTimeMs The execution time in milliseconds
     * @param additionalInfo Additional information about the operation
     */
    public void logPerformance(String operation, long executionTimeMs, String additionalInfo) {
        if (executionTimeMs > 500) {
            // Use warning level for slow operations
            logger.warning(formatPerformanceMessage(operation, executionTimeMs, additionalInfo));
        } else {
            // Use fine level for normal operations
            logger.fine(formatPerformanceMessage(operation, executionTimeMs, additionalInfo));
        }
    }

    /**
     * Formats a performance message.
     *
     * @param operation The operation being measured
     * @param executionTimeMs The execution time in milliseconds
     * @param additionalInfo Additional information about the operation
     * @return The formatted message
     */
    private String formatPerformanceMessage(String operation, long executionTimeMs, String additionalInfo) {
        return String.format("Performance [%s]: %d ms. %s", 
            operation, executionTimeMs, additionalInfo != null ? additionalInfo : "");
    }

    /**
     * Logs a security-related event.
     *
     * @param level The log level
     * @param player The player involved
     * @param action The security action
     * @param details The event details
     */
    public void logSecurityEvent(Level level, Player player, String action, String details) {
        logger.log(level, formatSecurityEventMessage(player, action, details));
    }

    /**
     * Formats a security event message.
     *
     * @param player The player involved
     * @param action The security action
     * @param details The event details
     * @return The formatted message
     */
    private String formatSecurityEventMessage(Player player, String action, String details) {
        return String.format("SECURITY [%s]: Player %s (%s). %s", 
            action, player.getName(), player.getUniqueId(), details);
    }
}
