package com.blockworlds.utags.tags;

import com.blockworlds.utags.CustomTagRequest;
import com.blockworlds.utags.CustomTags;
import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.storage.Database;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Custom tag requests (table {@code tag_requests}). The methods without "Async" in their name block on the
 * database: call them from an async task, never the main thread.
 */
public final class TagRequests {

    public enum AcceptResult { ACCEPTED, ALREADY_HANDLED, FAILED }

    private final JavaPlugin plugin;
    private final Database db;
    private final TagRegistry registry;
    private final LuckPerms luckPerms;
    private final Logger log;

    public TagRequests(JavaPlugin plugin, Database db, TagRegistry registry, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.db = db;
        this.registry = registry;
        this.luckPerms = luckPerms;
        this.log = plugin.getLogger();
    }

    /** Every five minutes, tells online staff that requests are waiting (the check runs off the main thread). */
    public void startReminder() {
        long delay = 5 * 60 * 20;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!hasAny()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("utags.staff")) {
                        player.sendMessage(ChatColor.RED + "There are pending tag requests. Use " + ChatColor.YELLOW + "/tag admin requests" + ChatColor.RED + " to check them.");
                    }
                }
            });
        }, delay, delay);
    }

    public boolean hasAny() {
        return !all().isEmpty();
    }

    /** True when the player has a request waiting for review. */
    public boolean hasPending(UUID player) {
        try (Connection connection = db.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM tag_requests WHERE player_uuid = ? LIMIT 1")) {
            statement.setString(1, player.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            log.severe("Could not check the tag requests of " + player + ": " + e.getMessage());
            return false;
        }
    }

    public CustomTagRequest byId(int id) {
        try (Connection connection = db.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM tag_requests WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new CustomTagRequest(id, UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("player_name"), resultSet.getString("tag_display"));
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            log.severe("Could not load tag request #" + id + ": " + e.getMessage());
        }
        return null;
    }

    public List<CustomTagRequest> all() {
        List<CustomTagRequest> requests = new ArrayList<>();
        try (Connection connection = db.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM tag_requests");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                requests.add(new CustomTagRequest(resultSet.getInt("id"), UUID.fromString(resultSet.getString("player_uuid")),
                        resultSet.getString("player_name"), resultSet.getString("tag_display")));
            }
        } catch (SQLException e) {
            log.severe("Could not load the tag requests: " + e.getMessage());
        }
        return requests;
    }

    /**
     * The slot the player's next custom tag goes into: the lowest empty one; -1 when the query failed (never
     * guess: an empty result would hand out slot 1 again).
     */
    public int nextCustomSlot(UUID owner, String currentName) {
        try {
            return CustomTags.nextFreeSlot(CustomTags.ownedByOrThrow(db, owner, currentName));
        } catch (SQLException e) {
            log.severe("Could not load custom tags of " + owner + ": " + e.getMessage());
            return -1;
        }
    }

    /** Creates or updates the player's request and tells them the result (on the main thread). */
    public CompletableFuture<Void> submitAsync(Player player, String tagDisplay) {
        if (tagDisplay == null || tagDisplay.trim().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Tag display cannot be empty.");
            return CompletableFuture.completedFuture(null);
        }
        // Simple trim based on ']'
        int endIndex = tagDisplay.indexOf(']') + 1;
        if (endIndex > 0 && endIndex < tagDisplay.length()) tagDisplay = tagDisplay.substring(0, endIndex);
        final String display = tagDisplay.trim();
        if (display.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Tag display became empty after trimming.");
            return CompletableFuture.completedFuture(null);
        }
        final String playerUuid = player.getUniqueId().toString();
        final String playerName = player.getName();

        return CompletableFuture.runAsync(() -> {
            String failure = null;
            boolean updated = false;
            try (Connection connection = db.connection();
                 PreparedStatement check = connection.prepareStatement("SELECT id FROM tag_requests WHERE player_uuid = ? LIMIT 1")) {
                check.setString(1, playerUuid);
                boolean exists;
                try (ResultSet rs = check.executeQuery()) {
                    exists = rs.next();
                }
                if (exists) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE tag_requests SET player_name = ?, tag_display = ? WHERE player_uuid = ?")) {
                        update.setString(1, playerName);
                        update.setString(2, display);
                        update.setString(3, playerUuid);
                        update.executeUpdate();
                        updated = true;
                    } catch (SQLException e) {
                        log.severe("Error updating tag request for " + playerName + ": " + e.getMessage());
                        failure = "An error occurred while updating your tag request.";
                    }
                } else {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO tag_requests (player_uuid, player_name, tag_display) VALUES (?, ?, ?)")) {
                        insert.setString(1, playerUuid);
                        insert.setString(2, playerName);
                        insert.setString(3, display);
                        insert.executeUpdate();
                    } catch (SQLException e) {
                        log.severe("Error inserting tag request for " + playerName + ": " + e.getMessage());
                        failure = "An error occurred while submitting your tag request.";
                    }
                }
            } catch (SQLException e) {
                log.severe("Error checking existing tag request for " + playerName + ": " + e.getMessage());
                failure = "An error occurred while checking for existing requests.";
            }
            final String message = failure != null ? ChatColor.RED + failure
                    : updated ? ChatColor.GREEN + "Your existing tag request has been updated with the new one!"
                    : ChatColor.GREEN + "Your tag request has been submitted!";
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(message));
        }, db.executor());
    }

    /**
     * Accepts a request: the new tag fills the owner's lowest empty slot and is named after their UUID, not their
     * name (Bedrock names start with "." and names change; see CustomTags). Returns ALREADY_HANDLED when the request
     * was already handled (by another staff member, say).
     */
    public AcceptResult accept(CustomTagRequest request) {
        // Claim the request first, so two staff accepting at once can't create two tags
        if (!deleteRow(request.getId())) return AcceptResult.ALREADY_HANDLED;
        UUID owner = request.getPlayerUuid();
        int slot = nextCustomSlot(owner, request.getPlayerName());
        String tagName = slot > 0 ? CustomTags.tagName(owner, slot) : null;
        if (tagName == null || !registry.insertBlocking(new Tag(tagName, request.getTagDisplay(), TagType.PREFIX, false, false, false,
                new ItemStack(Material.PLAYER_HEAD), 1, owner, slot))) {
            // Nothing was created: put the request back so it isn't lost, and grant nothing
            restoreRow(request);
            return AcceptResult.FAILED;
        }
        registry.reload();
        String permission = CustomTags.permission(tagName);
        log.info("Accepted custom tag request #" + request.getId() + " of " + request.getPlayerName()
                + " (" + owner + "): tag '" + tagName + "', slot " + slot);

        luckPerms.getUserManager().loadUser(owner).thenAcceptAsync(user -> {
            user.data().add(Node.builder(permission).build());
            luckPerms.getUserManager().saveUser(user);
            runConfigured("accept-command", request.getPlayerName());
        });
        return AcceptResult.ACCEPTED;
    }

    /** Denies a request; false when it was already handled. The notify command runs on the main thread. */
    public boolean deny(CustomTagRequest request) {
        if (!deleteRow(request.getId())) return false;
        runConfigured("deny-command", request.getPlayerName());
        return true;
    }

    private void runConfigured(String key, String playerName) {
        String command = plugin.getConfig().getString(key, "").replace("%player%", playerName);
        if (!command.isBlank()) Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    /** Re-creates a request this server claimed but couldn't turn into a tag. */
    private void restoreRow(CustomTagRequest request) {
        try (Connection connection = db.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO tag_requests (player_uuid, player_name, tag_display) VALUES (?, ?, ?)")) {
            statement.setString(1, request.getPlayerUuid().toString());
            statement.setString(2, request.getPlayerName());
            statement.setString(3, request.getTagDisplay());
            statement.executeUpdate();
            log.warning("Tag request of " + request.getPlayerName() + " could not be accepted; it is pending again.");
        } catch (SQLException e) {
            log.severe("Tag request of " + request.getPlayerName() + " (" + request.getPlayerUuid() + ", '"
                    + request.getTagDisplay() + "') could not be accepted or restored: " + e.getMessage());
        }
    }

    /** Deletes one request row; true when this call removed it. */
    private boolean deleteRow(int id) {
        try (Connection connection = db.connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM tag_requests WHERE id = ?")) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("Could not remove tag request #" + id + ": " + e.getMessage());
            return false;
        }
    }

    /** Drops and recreates the requests table (/tag admin purge requests); completes on the main thread. */
    public CompletableFuture<Void> purgeAsync() {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = db.connection(); Statement st = c.createStatement()) {
                st.executeUpdate("DROP TABLE IF EXISTS tag_requests");
                st.executeUpdate(db.dialect().recreateRequests());
            } catch (SQLException e) {
                log.severe("Could not purge the requests table: " + e.getMessage());
            }
        }, db.executor()).thenApplyAsync(v -> null, r -> Bukkit.getScheduler().runTask(plugin, r));
    }
}
