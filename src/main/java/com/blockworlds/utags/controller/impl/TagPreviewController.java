package com.blockworlds.utags.controller.impl;

import com.blockworlds.utags.service.RequestService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for handling tag preview confirmations in chat.
 */
public class TagPreviewController implements Listener {
    private final RequestService requestService;
    private final JavaPlugin plugin;
    
    // Store active tag previews by player UUID
    private final Map<UUID, String> activePreviews = new ConcurrentHashMap<>();
    
    // Preview timeout in seconds
    private static final long PREVIEW_TIMEOUT_SECONDS = 60;

    /**
     * Creates a new TagPreviewController with required dependencies.
     *
     * @param requestService The request service for creating tag requests
     * @param plugin The plugin instance for scheduling tasks
     */
    public TagPreviewController(RequestService requestService, JavaPlugin plugin) {
        this.requestService = requestService;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Only handle messages for players with active previews
        if (!activePreviews.containsKey(playerId)) {
            return;
        }
        
        // Cancel the chat event to prevent the message from appearing in chat
        event.setCancelled(true);
        
        String message = event.getMessage();
        String tagPreview = activePreviews.get(playerId);
        
        // Remove the preview regardless of the response
        activePreviews.remove(playerId);
        
        // Process the response
        if (message.equalsIgnoreCase("accept")) {
            boolean success = requestService.createCustomTagRequest(player, tagPreview);
            if (success) {
                player.sendMessage(ChatColor.GREEN + "Your tag request has been submitted!");
            } else {
                player.sendMessage(ChatColor.RED + "Failed to submit your tag request. Please try again.");
            }
        } else if (message.equalsIgnoreCase("decline")) {
            player.sendMessage(ChatColor.YELLOW + "Tag request cancelled. You can make a new request with /tag request.");
        } else {
            player.sendMessage(ChatColor.RED + "Invalid response. Please make a new tag request with /tag request.");
        }
    }
    
    /**
     * Creates a preview for a player and registers it for response handling.
     *
     * @param player The player to create the preview for
     * @param tagDisplay The tag display to preview
     */
    public void createPreview(Player player, String tagDisplay) {
        if (player == null || tagDisplay == null) return;
        
        UUID playerId = player.getUniqueId();
        activePreviews.put(playerId, tagDisplay);
        
        // Send preview message
        player.sendMessage(ChatColor.GREEN + "Tag request preview: " + 
            ChatColor.translateAlternateColorCodes('&', tagDisplay));
        player.sendMessage(ChatColor.YELLOW + "Type 'accept' to confirm or 'decline' to cancel.");
        
        // Set timeout to automatically remove the preview
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, 
            () -> {
                if (activePreviews.containsKey(playerId) && 
                    activePreviews.get(playerId).equals(tagDisplay)) {
                    activePreviews.remove(playerId);
                    player.sendMessage(ChatColor.RED + "Tag request timed out. Please try again.");
                }
            }, PREVIEW_TIMEOUT_SECONDS * 20);
    }
    
    /**
     * Checks if a player has an active preview.
     *
     * @param playerId The UUID of the player
     * @return True if the player has an active preview
     */
    public boolean hasActivePreview(UUID playerId) {
        return activePreviews.containsKey(playerId);
    }
    
    /**
     * Cancels an active preview for a player.
     *
     * @param playerId The UUID of the player
     */
    public void cancelPreview(UUID playerId) {
        activePreviews.remove(playerId);
    }
}
