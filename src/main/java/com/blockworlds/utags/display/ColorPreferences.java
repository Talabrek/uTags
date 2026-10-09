package com.blockworlds.utags.display;

import com.blockworlds.utags.PlayerTagColorPreference;
import com.blockworlds.utags.module.ChangeFeed;
import com.blockworlds.utags.storage.Database;
import org.bukkit.ChatColor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The players' colour choices: the colours of a worn tag's brackets and text ({@code player_tag_color_preferences})
 * and the name colour ({@code player_preferences}). Reads come from memory; writes go to the database in the
 * background. Tag colours of a player are loaded on join, name colours of everyone on start.
 */
public final class ColorPreferences {

    private final Database db;
    private final Logger log;
    private final ChangeFeed changes;
    private final Map<UUID, Map<String, PlayerTagColorPreference>> tagColors = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameColors = new ConcurrentHashMap<>();

    public ColorPreferences(Database db, ChangeFeed changes) {
        this.db = db;
        this.log = db.logger();
        this.changes = changes;
    }

    // --- tag colours ---

    /** The player's colours for the tag; a default preference (null colours) when none are set. Never null. */
    public PlayerTagColorPreference tagPreference(UUID player, String tagName) {
        return tagColors.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(tagName, k -> new PlayerTagColorPreference(player, tagName));
    }

    /** Sets the colours in memory now and saves them in the background. Both null resets the tag to its own colours. */
    public CompletableFuture<Void> setTagColors(UUID player, String tagName, ChatColor bracket, ChatColor content) {
        Map<String, PlayerTagColorPreference> prefs = tagColors.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        boolean reset = bracket == null && content == null;
        if (reset) {
            prefs.remove(tagName);
            if (prefs.isEmpty()) tagColors.remove(player);
        } else {
            PlayerTagColorPreference pref = prefs.computeIfAbsent(tagName, k -> new PlayerTagColorPreference(player, tagName));
            pref.setBracketColor(bracket);
            pref.setContentColor(content);
        }
        String bracketCode = bracket != null ? "&" + bracket.getChar() : null;
        String contentCode = content != null ? "&" + content.getChar() : null;
        return db.run(c -> {
            if (reset) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM player_tag_color_preferences WHERE player_uuid = ? AND tag_name = ?")) {
                    ps.setString(1, player.toString());
                    ps.setString(2, tagName);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(db.dialect().upsertTagColorPreference())) {
                    ps.setString(1, player.toString());
                    ps.setString(2, tagName);
                    if (bracketCode != null) ps.setString(3, bracketCode); else ps.setNull(3, Types.VARCHAR);
                    if (contentCode != null) ps.setString(4, contentCode); else ps.setNull(4, Types.VARCHAR);
                    ps.executeUpdate();
                }
            }
            changes.changed(ChangeFeed.Kind.COLORS, player);
        }).exceptionally(ex -> {
            log.severe("Failed to save the tag colours of " + player + " for " + tagName + ": " + Database.reason(ex));
            return null;
        });
    }

    /** Loads the player's saved tag colours into memory (no write-back). */
    public CompletableFuture<Void> loadPlayer(UUID player) {
        return db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT tag_name, bracket_color_code, content_color_code FROM player_tag_color_preferences WHERE player_uuid = ?")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        String tagName = rs.getString("tag_name");
                        PlayerTagColorPreference pref = tagPreference(player, tagName);
                        pref.setBracketColor(colorOf(rs.getString("bracket_color_code")));
                        pref.setContentColor(colorOf(rs.getString("content_color_code")));
                        count++;
                    }
                    if (count > 0) log.fine("Loaded " + count + " tag color preferences for " + player);
                }
            }
        }).exceptionally(ex -> {
            log.severe("Failed to load tag color preferences for " + player + ": " + Database.reason(ex));
            return null;
        });
    }

    /**
     * Re-reads the player's colours after another server changed them: the name colour always (it is cached for
     * everyone), the tag colours only when {@code includeTagColors} (the player is online here). Replaces what is in
     * memory, so a colour reset elsewhere is dropped here too.
     */
    public CompletableFuture<Void> reloadPlayer(UUID player, boolean includeTagColors) {
        return db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT name_color_code FROM player_preferences WHERE player_uuid = ?")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    String code = rs.next() ? rs.getString(1) : null;
                    if (code == null) nameColors.remove(player); else nameColors.put(player, code);
                }
            }
            if (!includeTagColors) return;
            Map<String, PlayerTagColorPreference> fresh = new ConcurrentHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT tag_name, bracket_color_code, content_color_code FROM player_tag_color_preferences WHERE player_uuid = ?")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        PlayerTagColorPreference pref = new PlayerTagColorPreference(player, rs.getString("tag_name"));
                        pref.setBracketColor(colorOf(rs.getString("bracket_color_code")));
                        pref.setContentColor(colorOf(rs.getString("content_color_code")));
                        fresh.put(pref.getTagName(), pref);
                    }
                }
            }
            if (fresh.isEmpty()) tagColors.remove(player); else tagColors.put(player, fresh);
        });
    }

    private static ChatColor colorOf(String code) {
        return code != null && code.length() == 2 ? ChatColor.getByChar(code.charAt(1)) : null;
    }

    /** Drops a player's colours from memory (on quit); they are loaded again on the next join. */
    public void unload(UUID player) {
        tagColors.remove(player);
    }

    // --- name colour ---

    public CompletableFuture<Void> loadAllNameColors() {
        return db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT player_uuid, name_color_code FROM player_preferences");
                 ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    try {
                        String code = rs.getString("name_color_code");
                        if (code != null) nameColors.put(UUID.fromString(rs.getString("player_uuid")), code);
                        count++;
                    } catch (IllegalArgumentException e) {
                        log.warning("Invalid UUID found in player_preferences table: " + rs.getString("player_uuid"));
                    }
                }
                log.info("Loaded " + count + " player name color preferences.");
            }
        }).exceptionally(ex -> {
            log.severe("Failed to load player name color preferences: " + Database.reason(ex));
            return null;
        });
    }

    /** Saves the name colour code (for example "&a"; null or "reset" clears it). The cache changes immediately. */
    public CompletableFuture<Boolean> setNameColorCode(UUID player, String colorCode) {
        String code = colorCode == null || colorCode.equalsIgnoreCase("reset") ? null : colorCode;
        if (code == null) nameColors.remove(player); else nameColors.put(player, code);
        return db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("REPLACE INTO player_preferences (player_uuid, name_color_code) VALUES (?, ?)")) {
                ps.setString(1, player.toString());
                ps.setString(2, code);
                ps.executeUpdate();
                changes.changed(ChangeFeed.Kind.COLORS, player);
            }
        }).handle((v, ex) -> {
            if (ex != null) log.severe("Failed to save name color preference for " + player + ": " + Database.reason(ex));
            return ex == null;
        });
    }

    /** The player's name colour code such as "&a", or null for the default. */
    public String nameColorCode(UUID player) {
        return nameColors.get(player);
    }

    /** The player's name colour; white when not set or invalid. */
    public ChatColor nameColor(UUID player) {
        String code = nameColors.get(player);
        if (code == null || code.length() != 2 || code.charAt(0) != '&') return ChatColor.WHITE;
        ChatColor color = ChatColor.getByChar(code.charAt(1));
        return color != null && color.isColor() ? color : ChatColor.WHITE;
    }
}
