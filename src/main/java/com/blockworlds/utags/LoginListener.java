package com.blockworlds.utags;

import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

        // Load player's custom tag color preferences
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID playerUuid = player.getUniqueId();
            plugin.getLogger().fine("Loading tag color preferences for " + player.getName() + " (" + playerUuid + ")");
            String sql = "SELECT tag_name, bracket_color_code, content_color_code FROM player_tag_color_preferences WHERE player_uuid = ?";
            try (Connection conn = plugin.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, playerUuid.toString());
                ResultSet rs = ps.executeQuery();

                int count = 0;
                while (rs.next()) {
                    String tagName = rs.getString("tag_name");
                    String bracketCode = rs.getString("bracket_color_code");
                    String contentCode = rs.getString("content_color_code");

                    ChatColor bracketColor = (bracketCode != null && bracketCode.length() == 2) ? ChatColor.getByChar(bracketCode.charAt(1)) : null;
                    ChatColor contentColor = (contentCode != null && contentCode.length() == 2) ? ChatColor.getByChar(contentCode.charAt(1)) : null;

                    // Use the existing method to populate the in-memory map
                    // Note: This will trigger another async save, which is redundant but harmless here.
                    // A better approach might be a dedicated loading method in uTags.
                    plugin.setPlayerTagColor(playerUuid, tagName, bracketColor, contentColor);
                    count++;
                }
                if (count > 0) {
                     plugin.getLogger().info("Loaded " + count + " tag color preferences for " + player.getName());
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load tag color preferences for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });


        // Determine and store the applied prefix tag name on join
        UUID playerUuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            User user = plugin.getLuckPerms().getUserManager().getUser(playerUuid);
            if (user != null) {
                String currentPrefix = user.getCachedData().getMetaData().getPrefix();
                if (currentPrefix != null && currentPrefix.length() > 2) {
                    // Check if the last 2 characters form a valid color code (§X or &X where X is 0-9a-fA-F)
                    String potentialDisplay = null;
                    int prefixLen = currentPrefix.length();
                    char potentialColorChar = currentPrefix.charAt(prefixLen - 2);
                    char potentialCodeChar = currentPrefix.charAt(prefixLen - 1);

                    // Validate the trailing color code pattern
                    if ((potentialColorChar == ChatColor.COLOR_CHAR || potentialColorChar == '&')
                            && "0123456789abcdefABCDEFkKlLmMnNoOrR".indexOf(potentialCodeChar) != -1) {
                        // Valid color code found at the end, extract display without it
                        potentialDisplay = currentPrefix.substring(0, prefixLen - 2);
                    } else {
                        // No valid trailing color code, use the full prefix as potential display
                        potentialDisplay = currentPrefix;
                    }

                    // Check if this display string corresponds to a known tag
                    final String finalPotentialDisplay = potentialDisplay;
                    plugin.getTagNameByDisplayAsync(finalPotentialDisplay).thenAcceptAsync(tagName -> {
                        if (tagName != null) {
                            // Store the mapping
                            plugin.playerAppliedPrefixTagName.put(playerUuid, tagName);
                            plugin.getLogger().info("Identified applied tag '" + tagName + "' for player " + player.getName() + " on join.");
                        } else {
                            // Prefix exists but doesn't match a known tag display (or tag was deleted)
                            // Remove any potentially stale mapping
                            plugin.playerAppliedPrefixTagName.remove(playerUuid);
                        }
                        // Update display name *after* potentially identifying the tag
                        Bukkit.getScheduler().runTask(plugin, () -> plugin.updatePlayerDisplayName(player));
                    }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
                } else {
                    // No prefix or prefix too short, remove any stale mapping and update display name
                    plugin.playerAppliedPrefixTagName.remove(playerUuid);
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.updatePlayerDisplayName(player));
                }
            } else {
                 // Could not find LP user, just update display name
                 Bukkit.getScheduler().runTask(plugin, () -> plugin.updatePlayerDisplayName(player));
            }
        });
        

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