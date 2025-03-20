package com.blockworlds.utags.services;

import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.ErrorHandler;
import com.blockworlds.utags.utils.PermissionUtils;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Service class for security-related operations in the uTags plugin.
 * Centralizes permission checks, access control, and security logging.
 */
public class SecurityService {

    private final uTags plugin;
    private final ErrorHandler errorHandler;
    
    // Track failed access attempts to detect potential attacks
    private final Map<UUID, AccessAttemptTracker> accessAttempts = new HashMap<>();
    
    // Configuration
    private final int maxFailedAttempts;
    private final long attemptExpirationMs;
    
    /**
     * Creates a new SecurityService.
     *
     * @param plugin The uTags plugin instance
     * @param errorHandler The error handler to use
     */
    public SecurityService(uTags plugin, ErrorHandler errorHandler) {
        this.plugin = plugin;
        this.errorHandler = errorHandler;
        
        // Load configuration
        this.maxFailedAttempts = plugin.getConfig().getInt("security.max-failed-attempts", 5);
        this.attemptExpirationMs = plugin.getConfig().getLong("security.attempt-expiration-seconds", 300) * 1000;
    }
    
    /**
     * Checks if a player has a specific permission and logs unauthorized attempts.
     *
     * @param player The player to check
     * @param permission The permission to check for
     * @param operation The operation being attempted
     * @return True if the player has permission, false otherwise
     */
    public boolean checkPermission(Player player, String permission, String operation) {
        if (player == null) {
            return false;
        }
        
        boolean hasPermission = player.hasPermission(permission);
        
        if (!hasPermission) {
            // Log unauthorized access attempt
            logSecurityEvent(Level.WARNING, player, "PERMISSION_DENIED",
                "Player attempted unauthorized operation: " + operation +
                " (Required permission: " + permission + ")");
            
            // Track failed attempts
            recordFailedAttempt(player, operation);
        }
        
        return hasPermission;
    }
    
    /**
     * Checks if a player has admin privileges.
     *
     * @param player The player to check
     * @param operation The admin operation being attempted
     * @return True if the player has admin privileges, false otherwise
     */
    public boolean checkAdmin(Player player, String operation) {
        return checkPermission(player, PermissionUtils.PERM_ADMIN, "admin operation: " + operation);
    }
    
    /**
     * Checks if a player has access to a specific tag.
     *
     * @param player The player to check
     * @param tagName The name of the tag
     * @return True if the player has access to the tag, false otherwise
     */
    public boolean checkTagAccess(Player player, String tagName) {
        return checkPermission(player, PermissionUtils.PERM_TAG_BASE + "." + tagName, 
                              "access tag: " + tagName);
    }
    
    /**
     * Records a failed access attempt and checks for potential attacks.
     *
     * @param player The player who attempted access
     * @param operation The operation that was attempted
     */
    private void recordFailedAttempt(Player player, String operation) {
        UUID playerId = player.getUniqueId();
        
        // Get or create tracker for this player
        AccessAttemptTracker tracker = accessAttempts.computeIfAbsent(
            playerId, id -> new AccessAttemptTracker()
        );
        
        // Record attempt
        tracker.recordAttempt();
        
        // Check if the player has too many failed attempts
        if (tracker.getRecentAttemptCount() >= maxFailedAttempts) {
            handlePotentialAttack(player, operation, tracker.getRecentAttemptCount());
        }
    }
    
    /**
     * Handles a potential security attack by a player.
     *
     * @param player The player who may be attacking
     * @param operation The operation that triggered the detection
     * @param attemptCount The number of failed attempts
     */
    private void handlePotentialAttack(Player player, String operation, int attemptCount) {
        // Log the potential attack
        logSecurityEvent(Level.SEVERE, player, "POTENTIAL_ATTACK",
            "Player has made " + attemptCount + " failed access attempts. " +
            "Last operation: " + operation);
        
        // Notify server administrators who are online
        String message = "§c[uTags Security] §fPotential security attack detected from player " +
                         player.getName() + " (" + attemptCount + " failed attempts)";
                         
        plugin.getServer().getOnlinePlayers().stream()
            .filter(p -> p.hasPermission(PermissionUtils.PERM_ADMIN))
            .forEach(p -> p.sendMessage(message));
        
        // Additional actions could be taken here, such as:
        // - Temporarily ban the player
        // - Restrict their actions
        // - Send them a warning
    }
    
    /**
     * Validates and sanitizes an input string to prevent security issues.
     *
     * @param input The input string to validate
     * @param type The type of input (for logging purposes)
     * @param player The player who provided the input
     * @param maxLength The maximum allowed length
     * @return The sanitized input, or null if invalid
     */
    public String validateInput(String input, String type, Player player, int maxLength) {
        if (input == null || input.isEmpty()) {
            if (player != null) {
                logSecurityEvent(Level.INFO, player, "EMPTY_INPUT",
                    "Player provided empty " + type + " input");
            }
            return null;
        }
        
        // Basic sanitization - remove control characters and trim
        String sanitized = input.replaceAll("[\\p{Cntrl}]", "").trim();
        
        // Check length
        if (sanitized.length() > maxLength) {
            if (player != null) {
                logSecurityEvent(Level.WARNING, player, "INPUT_TOO_LONG",
                    "Player provided " + type + " input exceeding max length (" + 
                    sanitized.length() + " > " + maxLength + ")");
            }
            return null;
        }
        
        // If input was modified during sanitization, log it
        if (!sanitized.equals(input) && player != null) {
            logSecurityEvent(Level.INFO, player, "INPUT_SANITIZED",
                "Player input was sanitized: " + type);
        }
        
        return sanitized;
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
        if (player == null) {
            plugin.getLogger().log(level, "[SECURITY][" + action + "] " + details);
            return;
        }
        
        String playerInfo = player.getName() + " (" + player.getUniqueId() + ")";
        plugin.getLogger().log(level, "[SECURITY][" + action + "] Player " + playerInfo + ": " + details);
        
        // For serious security events, also write to a separate security log file
        if (level == Level.WARNING || level == Level.SEVERE) {
            errorHandler.logSecurityEvent(level, player, action, details);
        }
    }
    
    /**
     * Records an unauthorized access attempt.
     *
     * @param player The player who attempted access
     * @param operation The operation that was attempted
     * @param reason The reason access was denied
     */
    public void recordUnauthorizedAccess(Player player, String operation, String reason) {
        logSecurityEvent(Level.WARNING, player, "UNAUTHORIZED_ACCESS",
            "Operation: " + operation + ", Reason: " + reason);
        
        // Record failed attempt for rate limiting
        recordFailedAttempt(player, operation);
    }
    
    /**
     * Class to track access attempts and detect potential attacks.
     */
    private class AccessAttemptTracker {
        private long[] attemptTimestamps;
        private int currentIndex;
        
        /**
         * Creates a new AccessAttemptTracker.
         */
        public AccessAttemptTracker() {
            this.attemptTimestamps = new long[maxFailedAttempts];
            this.currentIndex = 0;
        }
        
        /**
         * Records a failed access attempt.
         */
        public void recordAttempt() {
            attemptTimestamps[currentIndex] = System.currentTimeMillis();
            currentIndex = (currentIndex + 1) % maxFailedAttempts;
        }
        
        /**
         * Gets the number of recent attempts within the expiration window.
         *
         * @return The number of recent attempts
         */
        public int getRecentAttemptCount() {
            long now = System.currentTimeMillis();
            int count = 0;
            
            for (long timestamp : attemptTimestamps) {
                if (timestamp > 0 && now - timestamp < attemptExpirationMs) {
                    count++;
                }
            }
            
            return count;
        }
    }
}
