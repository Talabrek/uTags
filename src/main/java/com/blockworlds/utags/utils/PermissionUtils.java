package com.blockworlds.utags.util;

import org.bukkit.entity.Player;

/**
 * Utility class for permission-related operations in the uTags plugin.
 * Provides methods for checking and working with player permissions.
 */
public class PermissionUtils {
    // Permission constants
    public static final String PERM_TAG_BASE = "utags.tag";
    public static final String PERM_ADMIN = "utags.admin";
    public static final String PERM_STAFF = "utags.staff";
    public static final String PERM_TAG_COLOR = "utags.tagcolor";
    public static final String PERM_CUSTOM_PREFIX = "utags.custom";
    
    /**
     * Checks if a player has permission to use a specific tag.
     *
     * @param player The player to check
     * @param tagName The name of the tag
     * @return True if the player has permission, false otherwise
     */
    public static boolean hasTagPermission(Player player, String tagName) {
        if (player == null || tagName == null || tagName.isEmpty()) {
            return false;
        }
        
        // Check for specific tag permission
        if (player.hasPermission(PERM_TAG_BASE + "." + tagName)) {
            return true;
        }
        
        // Check for wildcard permission
        if (player.hasPermission(PERM_TAG_BASE + ".*")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if a player has admin permissions.
     *
     * @param player The player to check
     * @return True if the player has admin permissions, false otherwise
     */
    public static boolean hasAdminPermission(Player player) {
        return player != null && player.hasPermission(PERM_ADMIN);
    }
    
    /**
     * Checks if a player has staff permissions.
     *
     * @param player The player to check
     * @return True if the player has staff permissions, false otherwise
     */
    public static boolean hasStaffPermission(Player player) {
        return player != null && player.hasPermission(PERM_STAFF);
    }
    
    /**
     * Checks if a player has color changing permissions.
     *
     * @param player The player to check
     * @return True if the player has color changing permissions, false otherwise
     */
    public static boolean hasColorPermission(Player player) {
        return player != null && player.hasPermission(PERM_TAG_COLOR);
    }
    
    /**
     * Checks if a player has permission for a custom tag slot.
     *
     * @param player The player to check
     * @param slotNumber The custom tag slot number (1-based)
     * @return True if the player has the permission, false otherwise
     */
    public static boolean hasCustomSlotPermission(Player player, int slotNumber) {
        if (player == null || slotNumber < 1 || slotNumber > 5) {
            return false;
        }
        
        return player.hasPermission(PERM_CUSTOM_PREFIX + slotNumber);
    }
    
    /**
     * Checks if a player has a specific custom tag.
     *
     * @param player The player to check
     * @param slotNumber The custom tag slot number (1-based)
     * @return True if the player has the custom tag, false otherwise
     */
    public static boolean hasCustomTag(Player player, int slotNumber) {
        if (player == null || slotNumber < 1 || slotNumber > 5) {
            return false;
        }
        
        return player.hasPermission(PERM_TAG_BASE + "." + player.getName() + slotNumber);
    }
    
    /**
     * Gets the next available custom tag slot for a player.
     *
     * @param player The player to check
     * @return The next available slot (1-5), or 0 if none available
     */
    public static int getNextAvailableCustomSlot(Player player) {
        if (player == null) {
            return 0;
        }
        
        for (int i = 1; i <= 5; i++) {
            if (hasCustomSlotPermission(player, i) && !hasCustomTag(player, i)) {
                return i;
            }
        }
        
        return 0;
    }
    
    /**
     * Counts how many custom tag slots a player has permission to use.
     *
     * @param player The player to check
     * @return The number of custom tag slots available to the player
     */
    public static int countCustomSlotPermissions(Player player) {
        if (player == null) {
            return 0;
        }
        
        int count = 0;
        for (int i = 1; i <= 5; i++) {
            if (hasCustomSlotPermission(player, i)) {
                count++;
            }
        }
        
        return count;
    }
}
