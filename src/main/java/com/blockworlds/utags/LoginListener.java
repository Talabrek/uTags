package com.blockworlds.utags;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LoginListener implements Listener {
    private final uTags plugin;

    public LoginListener(uTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.selection().unload(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        if (player.hasPermission("utags.staff")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.requests().hasAny()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.sendMessage(ChatColor.RED + "There are pending tag requests. Use " + ChatColor.YELLOW + "/tag admin requests" + ChatColor.RED + " to check them.");
                        }
                    });
                }
            });
        }

        // Colours and the stored selection load in the background; the LuckPerms output and display name follow.
        // A player with no stored selection (first join since the 1.4.0 upgrade) is upgraded from LuckPerms once.
        plugin.selection().onJoin(player);

        // Remind players with an unlocked, empty custom tag slot. Slots are checked by UUID (CustomTags), not by
        // utags.tag.<name><N>: Bedrock names start with "." and names change.
        List<Integer> unlockedSlots = new ArrayList<>();
        for (int slot = 1; slot <= 4; slot++) {
            if (player.hasPermission("utags.custom" + slot)) unlockedSlots.add(slot);
        }
        if (!unlockedSlots.isEmpty()) {
            String playerName = player.getName();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Map<Integer, Tag> owned;
                try {
                    owned = CustomTags.ownedByOrThrow(plugin.database(), playerUuid, playerName);
                } catch (java.sql.SQLException e) {
                    plugin.getLogger().warning("Custom tag reminder skipped for " + playerName + ": " + e.getMessage());
                    return;
                }
                // the slot /tag request would fill (the lowest empty one) must be unlocked
                boolean canRequest = unlockedSlots.contains(CustomTags.nextFreeSlot(owned));
                if (!canRequest || plugin.requests().hasPending(playerUuid)) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(ChatColor.GREEN + "You are able to request a custom tag! Use " + ChatColor.YELLOW + "/tag request" + ChatColor.GREEN + " to request your tag.");
                    }
                });
            });
        }
    }
}
