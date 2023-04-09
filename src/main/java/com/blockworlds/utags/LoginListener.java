package com.blockworlds.utags;

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

        if (player.hasPermission("utags.staff")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.hasPendingTagRequests()) {
                    player.sendMessage(ChatColor.RED + "There are pending tag requests. Use " + ChatColor.YELLOW + "/tag admin requests" + ChatColor.RED + " to check them.");
                }
            });
        }

        

        if (player.hasPermission("utags.custom1") && !player.hasPermission("utags.tag." + player.getName() + "1")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.hasPendingTagRequests()) {
                    player.sendMessage(ChatColor.GREEN + "You are able to request a custom tag! Use " + ChatColor.YELLOW + "/tag request" + ChatColor.GREEN + " to request your tag.");
                }
            });
        }
        if (player.hasPermission("utags.custom2") && !player.hasPermission("utags.tag." + player.getName() + "2")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.hasPendingTagRequests()) {
                    player.sendMessage(ChatColor.GREEN + "You are able to request a custom tag! Use " + ChatColor.YELLOW + "/tag request" + ChatColor.GREEN + " to request your tag.");
                }
            });
        }
        if (player.hasPermission("utags.custom3") && !player.hasPermission("utags.tag." + player.getName() + "3")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.hasPendingTagRequests()) {
                    player.sendMessage(ChatColor.GREEN + "You are able to request a custom tag! Use " + ChatColor.YELLOW + "/tag request" + ChatColor.GREEN + " to request your tag.");
                }
            });
        }
        if (player.hasPermission("utags.custom4") && !player.hasPermission("utags.tag." + player.getName() + "4")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.hasPendingTagRequests()) {
                    player.sendMessage(ChatColor.GREEN + "You are able to request a custom tag! Use " + ChatColor.YELLOW + "/tag request" + ChatColor.GREEN + " to request your tag.");
                }
            });
        }
    }
}