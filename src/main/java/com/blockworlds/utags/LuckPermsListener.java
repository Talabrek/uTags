package com.blockworlds.utags;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class LuckPermsListener {

    private final uTags plugin;

    public LuckPermsListener(uTags plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        // Register the listener with LuckPerms' event bus
        EventBus eventBus = luckPerms.getEventBus();
        eventBus.subscribe(this.plugin, UserDataRecalculateEvent.class, this::onUserDataRecalculate);
        plugin.getLogger().info("Registered LuckPerms UserDataRecalculateEvent listener.");
    }

    private void onUserDataRecalculate(UserDataRecalculateEvent event) {
        User user = event.getUser();
        // Try to get the Bukkit player associated with this user
        Player player = Bukkit.getPlayer(user.getUniqueId());

        // Check if the player is online on this server instance
        if (player != null && player.isOnline()) {
             // --- LISTENER DEBUG LOGGING ---
             // Get metadata DIRECTLY from the user object provided by the event
             String eventPrefix = user.getCachedData().getMetaData().getPrefix();
             String eventSuffix = user.getCachedData().getMetaData().getSuffix();
             plugin.getLogger().info("[LISTENER DEBUG] UserDataRecalculateEvent for " + player.getName() + ". Prefix: '" + eventPrefix + "', Suffix: '" + eventSuffix + "'. Scheduling refresh.");
             // --- END LISTENER DEBUG LOGGING ---

            // Schedule the display name refresh to run on the main server thread
            // No delay needed here, as the event signifies data is ready
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Pass null for the prefix; the refresh method will fetch the latest from cache
                plugin.refreshBukkitDisplayName(player);
            });
        } else {
             plugin.getLogger().finest("LuckPerms UserDataRecalculateEvent detected for offline/unknown user: " + user.getUniqueId());
        }
    }
}