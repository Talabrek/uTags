package com.blockworlds.utags.menu;

import com.blockworlds.utags.*;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PreviewChatListener implements Listener {
    private final uTags plugin;

    public PreviewChatListener(uTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (plugin.menuState().previewTags().containsKey(playerId)) {
            event.setCancelled(true);
            String tag = plugin.menuState().previewTags().get(playerId);

            if (event.getMessage().equalsIgnoreCase("accept")) {
                // Handle the tag request asynchronously
                plugin.requests().submitAsync(player, tag);
                // Feedback is handled within the async method
            } else if (event.getMessage().equalsIgnoreCase("decline")) {
                player.sendMessage(ChatColor.RED + "You have declined to request this tag. Please try again.");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid Response. Please make a new tag request.");
            }
            plugin.menuState().previewTags().remove(playerId);
        }
    }
}