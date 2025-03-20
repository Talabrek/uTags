package com.blockworlds.utags.services;

import com.blockworlds.utags.TagType;
import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.ErrorHandler;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service class for input validation in the uTags plugin.
 * Centralizes validation logic for all user inputs.
 */
public class ValidationService {

    private final uTags plugin;
    private final ErrorHandler errorHandler;
    private final SecurityService securityService;
    
    // Regex patterns for validation
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(&[0-9a-fA-F])");
    private static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("(&[rRkKlLmMnNoO])");
    
    // Constants for validation
    private static final int MAX_TAG_NAME_LENGTH = 64;
    private static final int MAX_TAG_DISPLAY_LENGTH = 128;
    private static final int MAX_EFFECTIVE_DISPLAY_LENGTH = 32; // Length after color codes are removed
    
    /**
     * Creates a new ValidationService.
     *
     * @param plugin The uTags plugin instance
     * @param errorHandler The error handler to use
     * @param securityService The security service to use
     */
    public ValidationService(uTags plugin, ErrorHandler errorHandler, SecurityService securityService) {
        this.plugin = plugin;
        this.errorHandler = errorHandler;
        this.securityService = securityService;
    }
    
    /**
     * Validates a tag name.
     *
     * @param tagName The tag name to validate
     * @param player The player performing the action (for logging)
     * @return A validation result with error message if invalid
     */
    public ValidationResult validateTagName(String tagName, Player player) {
        if (tagName == null || tagName.isEmpty()) {
            if (player != null) {
                securityService.logSecurityEvent(java.util.logging.Level.INFO, player, "INVALID_INPUT",
                    "Empty tag name provided");
            }
            return ValidationResult.error("Tag name cannot be empty");
        }
        
        if (tagName.length() > MAX_TAG_NAME_LENGTH) {
            if (player != null) {
                securityService.logSecurityEvent(java.util.logging.Level.WARNING, player, "INVALID_INPUT",
                    "Tag name exceeds maximum length: " + tagName.length() + " > " + MAX_TAG_NAME_LENGTH);
            }
            return ValidationResult.error("Tag name is too long (max " + MAX_TAG_NAME_LENGTH + " characters)");
        }
        
        if (!ALPHANUMERIC_PATTERN.matcher(tagName).matches()) {
            if (player != null) {
                securityService.logSecurityEvent(java.util.logging.Level.WARNING, player, "INVALID_INPUT",
                    "Tag name contains invalid characters: " + tagName);
            }
            return ValidationResult.error("Tag name must contain only letters, numbers, underscores, and hyphens");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates a tag display text.
     *
     * @param display The display text to validate
     * @param player The player performing the action (for logging)
     * @return A validation result with error message if invalid
     */
    public ValidationResult validateTagDisplay(String display, Player player) {
        if (display == null || display.isEmpty()) {
            return ValidationResult.error("Tag display cannot be empty");
        }
        
        if (display.length() > MAX_TAG_DISPLAY_LENGTH) {
            if (player != null) {
                securityService.logSecurityEvent(java.util.logging.Level.WARNING, player, "INVALID_INPUT",
                    "Tag display exceeds maximum length: " + display.length() + " > " + MAX_TAG_DISPLAY_LENGTH);
            }
            return ValidationResult.error("Tag display is too long (max " + MAX_TAG_DISPLAY_LENGTH + " characters)");
        }
        
        // Check for color codes
        if (!COLOR_CODE_PATTERN.matcher(display).find()) {
            return ValidationResult.error("Tag display must contain at least one color code (e.g., &a, &b, &c)");
        }
        
        // Check for invalid formatting codes
        if (FORMATTING_CODE_PATTERN.matcher(display).find()) {
            return ValidationResult.error("Tag display cannot contain formatting codes (e.g., &k, &l, &n)");
        }
        
        // Check if the tag is properly wrapped with [] brackets
        if (!display.contains("[") || !display.contains("]")) {
            return ValidationResult.error("Tag display must be surrounded by [ and ] brackets");
        }
        
        // Extract content inside brackets and check its length
        String content;
        try {
            content = display.substring(display.indexOf('[') + 1, display.indexOf(']'));
        } catch (IndexOutOfBoundsException e) {
            return ValidationResult.error("Invalid tag format - brackets must be properly ordered");
        }
        
        String contentWithoutColorCodes = content.replaceAll(COLOR_CODE_PATTERN.pattern(), "");
        
        if (contentWithoutColorCodes.length() > MAX_EFFECTIVE_DISPLAY_LENGTH) {
            if (player != null) {
                securityService.logSecurityEvent(java.util.logging.Level.WARNING, player, "INVALID_INPUT",
                    "Tag content exceeds maximum effective length: " + 
                    contentWithoutColorCodes.length() + " > " + MAX_EFFECTIVE_DISPLAY_LENGTH);
            }
            return ValidationResult.error("Tag content is too long (max " + MAX_EFFECTIVE_DISPLAY_LENGTH + " characters excluding color codes)");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates a player name.
     *
     * @param playerName The player name to validate
     * @return A validation result with error message if invalid
     */
    public ValidationResult validatePlayerName(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return ValidationResult.error("Player name cannot be empty");
        }
        
        if (!USERNAME_PATTERN.matcher(playerName).matches()) {
            return ValidationResult.error("Invalid player name format (must be 3-16 characters, only letters, numbers, and underscores)");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates a UUID string.
     *
     * @param uuid The UUID string to validate
     * @return A validation result with error message if invalid
     */
    public ValidationResult validateUUID(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return ValidationResult.error("UUID cannot be empty");
        }
        
        if (!UUID_PATTERN.matcher(uuid).matches()) {
            return ValidationResult.error("Invalid UUID format");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates a UUID object.
     *
     * @param uuid The UUID to validate
     * @return A validation result with error message if invalid
     */
    public ValidationResult validateUUID(UUID uuid) {
        if (uuid == null) {
            return ValidationResult.error("UUID cannot be null");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates a player object.
     *
     * @param player The player to validate
     * @return A validation result with error message if invalid
     */
    public ValidationResult validatePlayer(Player player) {
        if (player == null) {
            return ValidationResult.error("Player cannot be null");
        }
        
        // Check if the player is still online
        if (!player.isOnline()) {
            return ValidationResult.error("Player is not online");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates a tag type.
     *
     * @param typeString The tag type string to validate
     * @return A validation result with the TagType if valid or error message if invalid
     */
    public ValidationResult validateTagType(String typeString) {
        if (typeString == null || typeString.isEmpty()) {
            return ValidationResult.error("Tag type cannot be empty");
        }
        
        try {
            TagType type = TagType.valueOf(typeString.toUpperCase());
            return ValidationResult.success(type);
        } catch (IllegalArgumentException e) {
            return ValidationResult.error("Invalid tag type. Valid types: PREFIX, SUFFIX, BOTH");
        }
    }
    
    /**
     * Validates a weight value.
     *
     * @param weightString The weight string to validate
     * @return A validation result with the weight if valid or error message if invalid
     */
    public ValidationResult validateWeight(String weightString) {
        if (weightString == null || weightString.isEmpty()) {
            return ValidationResult.error("Weight cannot be empty");
        }
        
        try {
            int weight = Integer.parseInt(weightString);
            if (weight < 0) {
                return ValidationResult.error("Weight must be a non-negative integer");
            }
            return ValidationResult.success(weight);
        } catch (NumberFormatException e) {
            return ValidationResult.error("Weight must be a valid integer");
        }
    }
    
    /**
     * Validates an attribute name for database operations.
     *
     * @param attribute The attribute name to validate
     * @return A validation result with error message if invalid
     */
    public ValidationResult validateAttribute(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return ValidationResult.error("Attribute name cannot be empty");
        }
        
        // Whitelist of valid column names
        boolean isValid = attribute.equals("name") || 
                         attribute.equals("display") || 
                         attribute.equals("type") || 
                         attribute.equals("public") || 
                         attribute.equals("color") || 
                         attribute.equals("material") ||
                         attribute.equals("weight");
                         
        if (!isValid) {
            return ValidationResult.error("Invalid attribute name. Valid attributes: name, display, type, public, color, material, weight");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates a boolean string value.
     *
     * @param value The string to validate
     * @return A validation result with the boolean if valid or error message if invalid
     */
    public ValidationResult validateBoolean(String value) {
        if (value == null || value.isEmpty()) {
            return ValidationResult.error("Boolean value cannot be empty");
        }
        
        if ("true".equalsIgnoreCase(value)) {
            return ValidationResult.success(true);
        } else if ("false".equalsIgnoreCase(value)) {
            return ValidationResult.success(false);
        } else {
            return ValidationResult.error("Invalid boolean value. Use 'true' or 'false'");
        }
    }
    
    /**
     * Validates command arguments.
     *
     * @param args The command arguments to validate
     * @param minArgs The minimum number of arguments required
     * @param maxArgs The maximum number of arguments allowed (-1 for unlimited)
     * @return A validation result with error message if invalid
     */
    public ValidationResult validateCommandArgs(String[] args, int minArgs, int maxArgs) {
        if (args == null) {
            if (minArgs == 0) {
                return ValidationResult.success();
            } else {
                return ValidationResult.error("Command requires at least " + minArgs + " arguments");
            }
        }
        
        if (args.length < minArgs) {
            return ValidationResult.error("Command requires at least " + minArgs + " arguments");
        }
        
        if (maxArgs >= 0 && args.length > maxArgs) {
            return ValidationResult.error("Command requires at most " + maxArgs + " arguments");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Displays color code help to a player.
     *
     * @param player The player to show help to
     */
    public void showColorCodeHelp(Player player) {
        ChatColor[] colors = {ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA, 
                            ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY, 
                            ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA, 
                            ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE};
        String[] colorCodes = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};
        StringBuilder colorCodeList = new StringBuilder(ChatColor.AQUA + "List of available color codes: ");
        
        for (int i = 0; i < colors.length; i++) {
            colorCodeList.append(colors[i]).append("&").append(colorCodes[i]).append(" ");
        }
        
        player.sendMessage(colorCodeList.toString().trim());
    }
    
    /**
     * Class representing a validation result with optional error message and value.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final Object value;
        
        private ValidationResult(boolean valid, String errorMessage, Object value) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.value = value;
        }
        
        /**
         * Creates a successful validation result.
         *
         * @return A successful validation result
         */
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }
        
        /**
         * Creates a successful validation result with a value.
         *
         * @param value The valid value
         * @return A successful validation result with value
         */
        public static ValidationResult success(Object value) {
            return new ValidationResult(true, null, value);
        }
        
        /**
         * Creates a failed validation result with an error message.
         *
         * @param errorMessage The error message
         * @return A failed validation result
         */
        public static ValidationResult error(String errorMessage) {
            return new ValidationResult(false, errorMessage, null);
        }
        
        /**
         * Checks if the validation was successful.
         *
         * @return True if valid, false otherwise
         */
        public boolean isValid() {
            return valid;
        }
        
        /**
         * Gets the error message for a failed validation.
         *
         * @return The error message, or null if validation was successful
         */
        public String getErrorMessage() {
            return errorMessage;
        }
        
        /**
         * Gets the validated value.
         *
         * @param <T> The type of the value
         * @return The validated value, or null if validation failed
         */
        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) value;
        }
    }
}
