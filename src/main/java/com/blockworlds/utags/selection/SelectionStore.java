package com.blockworlds.utags.selection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** The {@code utags_selection} table: one row per player with the tag worn as prefix and as suffix. */
public final class SelectionStore {

    private SelectionStore() {
    }

    /** The player's row, or null when they have none (a 1.3.0 player who has not joined since the upgrade). */
    public static Selection find(Connection c, UUID player) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT prefix_tag, suffix_tag FROM utags_selection WHERE player_uuid = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Selection(rs.getString(1), rs.getString(2)) : null;
            }
        }
    }

    /** Inserts the row; false when one already exists (another server upgraded the player first). */
    public static boolean insertIfAbsent(Connection c, UUID player, Selection selection) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO utags_selection (player_uuid, prefix_tag, suffix_tag, updated_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, selection.prefixTag());
            ps.setString(3, selection.suffixTag());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            // Only a duplicate key means "already there". Anything else (a deadlock, a lost connection) may have rolled
            // back the caller's whole transaction, so it must reach the caller. No re-read here: inside a MySQL
            // transaction the snapshot would not show the other server's row anyway; set() updates it instead.
            if (isDuplicateKey(e)) return false;
            throw e;
        }
    }

    static boolean isDuplicateKey(SQLException e) {
        if (e instanceof java.sql.SQLIntegrityConstraintViolationException) return true;
        if (e.getErrorCode() == 1062) return true; // MySQL ER_DUP_ENTRY
        String state = e.getSQLState();
        if (state != null && state.startsWith("23")) return true;
        String message = e.getMessage();
        return message != null && (message.contains("UNIQUE constraint failed") || message.contains("PRIMARY KEY constraint failed"));
    }

    /** Sets one of the two columns, creating the row if needed. {@code prefix} picks the column; tag may be null. */
    public static void set(Connection c, UUID player, boolean prefix, String tag) throws SQLException {
        String column = prefix ? "prefix_tag" : "suffix_tag";
        if (update(c, player, column, tag) > 0) return;
        Selection fresh = prefix ? new Selection(tag, null) : new Selection(null, tag);
        if (!insertIfAbsent(c, player, fresh)) update(c, player, column, tag);
    }

    private static int update(Connection c, UUID player, String column, String tag) throws SQLException {
        // column is one of two fixed names, never user text
        try (PreparedStatement ps = c.prepareStatement("UPDATE utags_selection SET " + column + " = ?, updated_at = ? WHERE player_uuid = ?")) {
            ps.setString(1, tag);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, player.toString());
            return ps.executeUpdate();
        }
    }
}
