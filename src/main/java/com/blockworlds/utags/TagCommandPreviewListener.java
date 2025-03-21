package com.blockworlds.utags;

import com.blockworlds.utags.utils.Utils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

public class TagCommandPreviewListener implements Listener {
    private final uTags plugin;

    public TagCommandPreviewListener(uTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Check if the player has a preview tag
        if (plugin.getPreviewTags().containsKey(playerId)) {
            event.setCancelled(true);
            String tag = plugin.getPreviewTags().get(playerId);

            if (event.getMessage().equalsIgnoreCase("accept")) {
                plugin.createCustomTagRequest(player, tag);
            } else if (event.getMessage().equalsIgnoreCase("decline")) {
                Utils.sendError(player, "You have declined to request this tag. Please try again.");
            } else {
                Utils.sendError(player, "Invalid Response. Please make a new tag request.");
            }
            
            plugin.getPreviewTags().remove(playerId);
        }
    }
}
