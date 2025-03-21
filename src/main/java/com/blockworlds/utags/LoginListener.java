package com.blockworlds.utags;

import com.blockworlds.utags.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class LoginListener implements Listener {
    private uTags plugin;

    public LoginListener(uTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission(Utils.PERM_STAFF)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.hasPendingTagRequests()) {
                    player.sendMessage(ChatColor.RED + "There are pending tag requests. Use " + 
                        ChatColor.YELLOW + "/tag admin requests" + ChatColor.RED + " to check them.");
                }
            });
        }

        // Check custom tag permissions
        for (int i = 1; i <= 4; i++) {
            final int slotNum = i;
            if (player.hasPermission("utags.custom" + i) && !player.hasPermission("utags.tag." + player.getName() + i)) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    if (plugin.hasPendingTagRequests()) {
                        player.sendMessage(ChatColor.GREEN + "You are able to request a custom tag! Use " + 
                            ChatColor.YELLOW + "/tag request" + ChatColor.GREEN + " to request your tag.");
                    }
                });
            }
        }
    }
}
