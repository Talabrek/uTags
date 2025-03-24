package com.blockworlds.utags.util;

import java.util.regex.Pattern;

/**
 * Utility class for validation operations in the uTags plugin.
 * Provides methods for validating user inputs and configuration values.
 */
public class ValidationUtils {
    // Regular expressions for validation
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(&[0-9a-fA-F])");
    private static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("(&[rRkKlLmMnNoO])");
    
    /**
     * Validates a tag name (used for internal tag identification).
     *
     * @param name The tag name to validate
     * @return True if the name is valid, false otherwise
     */
    public static boolean isValidTagName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return ALPHANUMERIC_PATTERN.matcher(name).matches();
    }
    
    /**
     * Validates a tag display format.
     *
     * @param tag The tag to validate
     * @return Null if valid, error message if invalid
     */
    public static String validateTagFormat(String tag) {
        if (tag == null || tag.isEmpty()) {
            return "Tag cannot be empty.";
        }
        
        // Check for color codes
        if (!COLOR_CODE_PATTERN.matcher(tag).find()) {
            return "A valid tag must contain at least one color code (e.g., &a, &b).";
        }
        
        // Check for formatting codes (not allowed)
        if (FORMATTING_CODE_PATTERN.matcher(tag).find()) {
            return "A valid tag must not contain formatting codes such as &n or &k.";
        }
        
        // Check for brackets
        if (!tag.contains("[") || !tag.contains("]")) {
            return "A valid tag must be surrounded by [ and ] brackets.";
        }
        
        // Extract content and check length
        try {
            String content = tag.substring(tag.indexOf('[') + 1, tag.indexOf(']'));
            String contentWithoutColorCodes = content.replaceAll(COLOR_CODE_PATTERN.pattern(), "");
            
            if (contentWithoutColorCodes.length() > 15) {
                return "A valid tag must be at most 15 characters long (excluding color codes).";
            }
        } catch (IndexOutOfBoundsException e) {
            return "Invalid tag format - brackets must be properly ordered.";
        }
        
        return null; // Valid tag
    }
    
    /**
     * Validates a database attribute name to prevent SQL injection.
     *
     * @param attribute The attribute name to validate
     * @return True if the attribute is valid, false otherwise
     */
    public static boolean isValidAttribute(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return false;
        }
        
        // Whitelist of valid column names
        return attribute.equals("name") || 
               attribute.equals("display") || 
               attribute.equals("type") || 
               attribute.equals("public") || 
               attribute.equals("color") || 
               attribute.equals("material") ||
               attribute.equals("weight");
    }
    
    /**
     * Validates a boolean string value.
     *
     * @param value The string to validate
     * @return True if the string is a valid boolean, false otherwise
     */
    public static boolean isValidBoolean(String value) {
        return value != null && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value));
    }
    
    /**
     * Validates a number string value.
     *
     * @param value The string to validate
     * @return True if the string is a valid number, false otherwise
     */
    public static boolean isValidNumber(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Extracts the tag content from a tag string.
     * 
     * @param tagString The full tag string
     * @return The tag content without brackets
     */
    public static String extractTagContent(String tagString) {
        if (tagString == null || tagString.isEmpty()) {
            return "";
        }
        
        int startIndex = tagString.indexOf('[');
        int endIndex = tagString.indexOf(']');
        
        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            return tagString;
        }
        
        return tagString.substring(startIndex + 1, endIndex);
    }
    
    /**
     * Normalizes a tag string by ensuring it ends at the closing bracket.
     * 
     * @param tagString The tag string to normalize
     * @return The normalized tag string
     */
    public static String normalizeTagString(String tagString) {
        if (tagString == null || tagString.isEmpty()) {
            return "";
        }
        
        int endIndex = tagString.indexOf(']') + 1;
        if (endIndex <= 0 || endIndex > tagString.length()) {
            return tagString;
        }
        
        return tagString.substring(0, endIndex);
    }
}
