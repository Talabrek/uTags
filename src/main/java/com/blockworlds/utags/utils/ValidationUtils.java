package com.blockworlds.utags.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for validation operations in the uTags plugin.
 * Provides methods for validating user inputs and configuration values.
 */
public class ValidationUtils {

    // Regular expressions for validation
    private static final String NAME_PATTERN = "^[a-zA-Z0-9_-]+$";
    private static final String COLOR_CODE_PATTERN = "(&[0-9a-fA-F])";
    private static final String FORMATTING_CODE_PATTERN = "(&[rRkKlLmMnNoO])";
    private static final String TAG_PATTERN = "^" + COLOR_CODE_PATTERN + "\\[" + "(?:(?:" + COLOR_CODE_PATTERN + "|.)*){0,15}" + "\\]" + ".*" + "$";

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
        return name.matches(NAME_PATTERN);
    }

    /**
     * Validates a tag display format according to the plugin's rules.
     * A valid tag must:
     * 1. Start with a color code
     * 2. Be surrounded by square brackets [ ]
     * 3. Be max 15 characters long (excluding color codes)
     * 4. Not contain formatting codes
     *
     * @param tag The tag to validate
     * @return Null if valid, error message if invalid
     */
    public static String validateTagFormat(String tag) {
        if (tag == null || tag.isEmpty()) {
            return "Tag cannot be empty.";
        }

        Pattern pattern = Pattern.compile(TAG_PATTERN);
        Matcher matcher = pattern.matcher(tag);

        if (!matcher.matches()) {
            return "A valid tag must start with a color code (e.g., &d, &6) followed by '[' and end with ']'.";
        }

        if (tag.matches(".*" + FORMATTING_CODE_PATTERN + ".*")) {
            return "A valid tag must not contain formatting codes such as &n or &k.";
        }

        String content = tag.substring(tag.indexOf('[') + 1, tag.indexOf(']'));
        String contentWithoutColorCodes = content.replaceAll(COLOR_CODE_PATTERN, "");

        if (contentWithoutColorCodes.length() > 15) {
            return "A valid tag must be between 1 and 15 characters long, excluding color codes.";
        }

        return null;
    }

    /**
     * Validates a database attribute name to prevent SQL injection.
     *
     * @param attribute The attribute name to validate
     * @return True if the attribute is valid, false otherwise
     */
    public static boolean isValidDatabaseAttribute(String attribute) {
        // Whitelist of valid column names
        return attribute != null && (
               attribute.equals("name") || 
               attribute.equals("display") || 
               attribute.equals("type") || 
               attribute.equals("public") || 
               attribute.equals("color") || 
               attribute.equals("material") ||
               attribute.equals("weight"));
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
