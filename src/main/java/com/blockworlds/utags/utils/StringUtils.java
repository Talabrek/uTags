package com.blockworlds.utags.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for string manipulation and formatting operations.
 * Provides common string operations to ensure consistent handling throughout the plugin.
 */
public class StringUtils {

    // Common pattern for alphanumeric strings with underscores and hyphens
    private static final Pattern ALPHANUMERIC_UNDERSCORE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    
    // Maximum length for tag display names (excluding color codes)
    private static final int MAX_TAG_DISPLAY_LENGTH = 15;
    
    /**
     * Checks if a string is null or empty.
     *
     * @param str The string to check
     * @return True if the string is null or empty, false otherwise
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * Returns a default value if the string is null or empty.
     *
     * @param str The string to check
     * @param defaultValue The default value to return if the string is null or empty
     * @return The original string if not null or empty, otherwise the default value
     */
    public static String defaultIfEmpty(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }
    
    /**
     * Truncates a string to the specified maximum length.
     *
     * @param str The string to truncate
     * @param maxLength The maximum length
     * @return The truncated string, or the original if already shorter than maxLength
     */
    public static String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        
        return str.length() <= maxLength ? str : str.substring(0, maxLength);
    }
    
    /**
     * Validates that a string matches the alphanumeric pattern (letters, numbers, underscores, hyphens).
     *
     * @param str The string to validate
     * @return True if the string matches the pattern, false otherwise
     */
    public static boolean isAlphanumericUnderscore(String str) {
        if (isEmpty(str)) {
            return false;
        }
        
        return ALPHANUMERIC_UNDERSCORE_PATTERN.matcher(str).matches();
    }
    
    /**
     * Splits a string into lines of a specific length, trying to break at word boundaries.
     *
     * @param text The text to split
     * @param lineLength The maximum length per line
     * @return A list of lines
     */
    public static List<String> wordWrap(String text, int lineLength) {
        if (isEmpty(text)) {
            return new ArrayList<>();
        }
        
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > lineLength) {
                // If adding this word would exceed the line length, start a new line
                if (!currentLine.toString().trim().isEmpty()) {
                    lines.add(currentLine.toString().trim());
                }
                currentLine = new StringBuilder(word);
            } else {
                // Add the word to the current line
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }
        
        // Add the last line if not empty
        if (!currentLine.toString().trim().isEmpty()) {
            lines.add(currentLine.toString().trim());
        }
        
        return lines;
    }
    
    /**
     * Capitalizes the first letter of each word in a string.
     *
     * @param str The string to capitalize
     * @return The capitalized string
     */
    public static String capitalizeWords(String str) {
        if (isEmpty(str)) {
            return str;
        }
        
        StringBuilder result = new StringBuilder();
        String[] words = str.split("\\s+");
        
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1).toLowerCase())
                      .append(" ");
            }
        }
        
        return result.toString().trim();
    }
    
    /**
     * Removes color codes from a string.
     *
     * @param str The string to process
     * @return The string without color codes
     */
    public static String stripColorCodes(String str) {
        if (isEmpty(str)) {
            return str;
        }
        
        return str.replaceAll("&[0-9a-fA-F]", "");
    }
    
    /**
     * Counts the effective length of a string, ignoring color codes.
     *
     * @param str The string to measure
     * @return The length without color codes
     */
    public static int effectiveLength(String str) {
        if (isEmpty(str)) {
            return 0;
        }
        
        return stripColorCodes(str).length();
    }
    
    /**
     * Checks if a string is too long for a tag display, accounting for color codes.
     *
     * @param str The string to check
     * @return True if the effective length exceeds the maximum tag display length
     */
    public static boolean isTagDisplayTooLong(String str) {
        return effectiveLength(str) > MAX_TAG_DISPLAY_LENGTH;
    }
    
    /**
     * Escapes special characters in a string for use in HTML or XML.
     *
     * @param str The string to escape
     * @return The escaped string
     */
    public static String escapeHtml(String str) {
        if (isEmpty(str)) {
            return str;
        }
        
        return str.replace("&", "&amp;")
                 .replace("<", "&lt;")
                 .replace(">", "&gt;")
                 .replace("\"", "&quot;")
                 .replace("'", "&#39;");
    }
    
    /**
     * Pads a string to a specific length with spaces.
     *
     * @param str The string to pad
     * @param length The desired length
     * @param padLeft Whether to pad on the left (true) or right (false)
     * @return The padded string
     */
    public static String pad(String str, int length, boolean padLeft) {
        if (str == null) {
            str = "";
        }
        
        if (str.length() >= length) {
            return str;
        }
        
        StringBuilder sb = new StringBuilder();
        int padAmount = length - str.length();
        
        if (padLeft) {
            for (int i = 0; i < padAmount; i++) {
                sb.append(" ");
            }
            sb.append(str);
        } else {
            sb.append(str);
            for (int i = 0; i < padAmount; i++) {
                sb.append(" ");
            }
        }
        
        return sb.toString();
    }
}
