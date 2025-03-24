package com.blockworlds.utags.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.blockworlds.utags.config.ConfigurationManager;

/**
 * Utility class for message-related operations in the uTags plugin.
 * Provides methods for formatting and sending messages to players.
 */
public class MessageUtils {
    private static ConfigurationManager configManager;
    
    /**
     * Initialize with ConfigurationManager
     * @param configManager The configuration manager
     */
    public static void init(ConfigurationManager configManager) {
        MessageUtils.configManager = configManager;
    }
    
    /**
     * Sends an informational message to a player.
     *
     * @param sender The recipient of the message
     * @param message The message to send or message key
     * @param args Optional arguments for message formatting
     */
    public static void sendInfo(CommandSender sender, String message, Object... args) {
        sender.sendMessage(colorize(getPrefix("info") + formatMessage(message, args)));
    }
    
    /**
     * Sends a success message to a player.
     *
     * @param sender The recipient of the message
     * @param message The message to send or message key
     * @param args Optional arguments for message formatting
     */
    public static void sendSuccess(CommandSender sender, String message, Object... args) {
        sender.sendMessage(colorize(getPrefix("success") + formatMessage(message, args)));
    }
    
    /**
     * Sends an error message to a player.
     *
     * @param sender The recipient of the message
     * @param message The message to send or message key
     * @param args Optional arguments for message formatting
     */
    public static void sendError(CommandSender sender, String message, Object... args) {
        sender.sendMessage(colorize(getPrefix("error") + formatMessage(message, args)));
    }
    
    /**
     * Sends a warning message to a player.
     *
     * @param sender The recipient of the message
     * @param message The message to send or message key
     * @param args Optional arguments for message formatting
     */
    public static void sendWarning(CommandSender sender, String message, Object... args) {
        sender.sendMessage(colorize(getPrefix("warning") + formatMessage(message, args)));
    }
    
    /**
     * Gets a message prefix from configuration or uses default if configuration isn't initialized.
     *
     * @param type The prefix type (info, success, error, warning)
     * @return The prefix
     */
    private static String getPrefix(String type) {
        if (configManager == null) {
            // Default prefixes if config isn't initialized
            switch (type) {
                case "info": return ChatColor.AQUA + "[uTags] " + ChatColor.WHITE;
                case "success": return ChatColor.GREEN + "[uTags] " + ChatColor.GREEN;
                case "error": return ChatColor.RED + "[uTags] " + ChatColor.RED;
                case "warning": return ChatColor.GOLD + "[uTags] " + ChatColor.YELLOW;
                default: return "[uTags] ";
            }
        }
        return configManager.getMessage("prefix." + type);
    }
    
    /**
     * Formats a message with arguments.
     *
     * @param message The message to format
     * @param args Arguments for formatting
     * @return The formatted message
     */
    private static String formatMessage(String message, Object... args) {
        if (configManager != null && message.startsWith("msg.")) {
            message = configManager.getMessage(message.substring(4));
        }
        
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                message = message.replace("{" + i + "}", String.valueOf(args[i]));
            }
        }
        
        return message;
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
     * Shows color code help to a player.
     *
     * @param player The player to show the help to
     */
    public static void showColorCodes(Player player) {
        ChatColor[] colors = {ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, 
                            ChatColor.DARK_AQUA, ChatColor.DARK_RED, ChatColor.DARK_PURPLE, 
                            ChatColor.GOLD, ChatColor.GRAY, ChatColor.DARK_GRAY, ChatColor.BLUE, 
                            ChatColor.GREEN, ChatColor.AQUA, ChatColor.RED, ChatColor.LIGHT_PURPLE, 
                            ChatColor.YELLOW, ChatColor.WHITE};
        String[] colorCodes = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};
        StringBuilder colorCodeList = new StringBuilder(ChatColor.AQUA + "List of available color codes: ");
        
        for (int i = 0; i < colors.length; i++) {
            colorCodeList.append(colors[i]).append("&").append(colorCodes[i]).append(" ");
        }
        
        player.sendMessage(colorCodeList.toString().trim());
    }
}
