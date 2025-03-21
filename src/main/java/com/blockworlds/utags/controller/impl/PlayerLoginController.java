package com.blockworlds.utags.controller.impl;

import com.blockworlds.utags.service.RequestService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

/**
 * Controller for handling player login events related to uTags.
 */
public class PlayerLoginController implements Listener {
    private final RequestService requestService;
    private final JavaPlugin plugin;
    
    // Permission constants
    private static final String PERM_STAFF = "utags.staff";
    private static final String PERM_CUSTOM_PREFIX = "utags.custom";
    private static final String PERM_TAG_PREFIX = "utags.tag.";

    /**
     * Creates a new PlayerLoginController with required dependencies.
     *
     * @param requestService The request service for checking pending requests
     * @param plugin The plugin instance for scheduling tasks
     */
    public PlayerLoginController(RequestService requestService, JavaPlugin plugin) {
        this.requestService = requestService;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        
        // Run checks asynchronously to avoid login delay
        scheduler.runTaskAsynchronously(plugin, () -> {
            // Notify staff members about pending tag requests
            if (player.hasPermission(PERM_STAFF) && requestService.hasPendingRequests()) {
                notifyStaffMember(player);
            }
            
            // Check if player can request custom tags
            checkCustomTagPermissions(player);
        });
    }
    
    private void notifyStaffMember(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.sendMessage(ChatColor.RED + "There are pending tag requests. Use " + 
                ChatColor.YELLOW + "/tag admin requests" + ChatColor.RED + " to check them.");
        });
    }
    
    private void checkCustomTagPermissions(Player player) {
        // Check custom tag slots 1-4
        for (int i = 1; i <= 4; i++) {
            final int slotNum = i;
            // If player has permission for custom slot but doesn't have the tag yet
            if (player.hasPermission(PERM_CUSTOM_PREFIX + slotNum) && 
                !player.hasPermission(PERM_TAG_PREFIX + player.getName() + slotNum)) {
                
                // Run notification on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.GREEN + "You are able to request a custom tag! Use " + 
                        ChatColor.YELLOW + "/tag request" + ChatColor.GREEN + " to request your tag.");
                });
                
                // Only notify once
                break;
            }
        }
    }
}
