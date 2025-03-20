package com.blockworlds.utags.utils;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for message-related operations in the uTags plugin.
 * Provides methods for formatting and sending messages to players.
 */
public class MessageUtils {

    // Common message prefixes
    public static final String PREFIX_INFO = ChatColor.AQUA + "[uTags] " + ChatColor.WHITE;
    public static final String PREFIX_SUCCESS = ChatColor.GREEN + "[uTags] " + ChatColor.GREEN;
    public static final String PREFIX_ERROR = ChatColor.RED + "[uTags] " + ChatColor.RED;
    public static final String PREFIX_WARNING = ChatColor.GOLD + "[uTags] " + ChatColor.YELLOW;

    /**
     * Sends an informational message to a player.
     *
     * @param sender The recipient of the message
     * @param message The message to send
     */
    public static void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(PREFIX_INFO + colorize(message));
    }

    /**
     * Sends a success message to a player.
     *
     * @param sender The recipient of the message
     * @param message The message to send
     */
    public static void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(PREFIX_SUCCESS + colorize(message));
    }

    /**
     * Sends an error message to a player.
     *
     * @param sender The recipient of the message
     * @param message The message to send
     */
    public static void sendError(CommandSender sender, String message) {
        sender.sendMessage(PREFIX_ERROR + colorize(message));
    }

    /**
     * Sends a warning message to a player.
     *
     * @param sender The recipient of the message
     * @param message The message to send
     */
    public static void sendWarning(CommandSender sender, String message) {
        sender.sendMessage(PREFIX_WARNING + colorize(message));
    }

    /**
     * Translates color codes in a string.
     *
     * @param text The text to colorize
     * @return The colorized string
     */
    public static String colorize(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Translates color codes in a list of strings.
     *
     * @param textList The list of texts to colorize
     * @return The list of colorized strings
     */
    public static List<String> colorizeStringList(List<String> textList) {
        List<String> colorizedList = new ArrayList<>();
        
        if (textList == null || textList.isEmpty()) {
            return colorizedList;
        }
        
        for (String text : textList) {
            colorizedList.add(colorize(text));
        }
        
        return colorizedList;
    }

    /**
     * Sends a message about tag operations.
     *
     * @param player The player to send the message to
     * @param tagType The type of tag (PREFIX/SUFFIX)
     * @param tagDisplay The tag display text
     * @param isSuccess Whether the operation was successful
     */
    public static void sendTagOperationMessage(Player player, String tagType, String tagDisplay, boolean isSuccess) {
        if (isSuccess) {
            player.sendMessage(ChatColor.GREEN + "Your " + tagType + " has been updated to: " + 
                               ChatColor.translateAlternateColorCodes('&', tagDisplay));
        } else {
            player.sendMessage(ChatColor.RED + "Failed to update your " + tagType + ".");
        }
    }

    /**
     * Shows color code help to a player.
     *
     * @param player The player to show the help to
     */
    public static void showColorCodes(Player player) {
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
}
