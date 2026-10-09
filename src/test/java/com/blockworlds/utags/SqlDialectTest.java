package com.blockworlds.utags;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlDialectTest {

    // --- the SQL text of each dialect ---

    @Test
    void mysqlKeepsItsOriginalStatements() {
        SqlDialect d = SqlDialect.MYSQL;
        assertTrue(d.createTags().get(0).contains("`id` INT AUTO_INCREMENT PRIMARY KEY"));
        assertTrue(d.createTags().get(0).contains("ENUM('prefix', 'suffix', 'both')"));
        assertTrue(d.createTags().get(0).contains("KEY `idx_tags_owner` (`owner_uuid`)"));
        assertEquals("ALTER TABLE tags MODIFY COLUMN `material` MEDIUMTEXT NOT NULL;", d.widenMaterial());
        assertEquals("ALTER TABLE tags ADD COLUMN `colorable` BOOLEAN NOT NULL DEFAULT TRUE AFTER `color`;", d.addColorable());
        assertEquals("CREATE INDEX `idx_tags_owner` ON tags (`owner_uuid`);", d.createOwnerIndex());
        assertEquals("INSERT INTO player_tag_color_preferences "
                + "(player_uuid, tag_name, bracket_color_code, content_color_code) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE bracket_color_code = VALUES(bracket_color_code), "
                + "content_color_code = VALUES(content_color_code)", d.upsertTagColorPreference());
    }

    @Test
    void sqliteUsesNoMysqlOnlySyntax() {
        SqlDialect d = SqlDialect.SQLITE;
        List<String> all = new ArrayList<>(d.createTags());
        all.addAll(d.createOtherTables());
        all.addAll(d.recreateTags());
        all.add(d.recreateRequests());
        all.add(d.addColorable());
        all.add(d.addOwnerUuid());
        all.add(d.addSlot());
        all.add(d.createOwnerIndex());
        all.add(d.upsertTagColorPreference());
        for (String sql : all) {
            assertFalse(sql.contains("AUTO_INCREMENT"), sql);
            assertFalse(sql.contains("ENUM"), sql);
            assertFalse(sql.contains("MEDIUMTEXT"), sql);
            assertFalse(sql.contains("ON DUPLICATE KEY"), sql);
            assertFalse(sql.contains(" AFTER "), sql);
            assertFalse(sql.contains("MODIFY COLUMN"), sql);
            assertFalse(sql.contains("VARCHAR"), sql);
        }
        assertNull(d.widenMaterial());
        assertTrue(d.upsertTagColorPreference().contains("ON CONFLICT(player_uuid, tag_name) DO UPDATE"));
    }

    // --- the SQLite statements against a real in-memory SQLite ---

    private static Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    private static void createAll(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String sql : SqlDialect.SQLITE.createTags()) st.executeUpdate(sql);
            for (String sql : SqlDialect.SQLITE.createOtherTables()) st.executeUpdate(sql);
        }
    }

    private static void replaceTag(Connection c, String name, String display, String type) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("REPLACE INTO tags (name, display, type, public, color, colorable, "
                + "material, weight, owner_uuid, slot) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, name);
            ps.setString(2, display);
            ps.setString(3, type);
            ps.setBoolean(4, true);
            ps.setBoolean(5, false);
            ps.setBoolean(6, true);
            ps.setString(7, "material");
            ps.setInt(8, 5);
            ps.setNull(9, java.sql.Types.VARCHAR);
            ps.setNull(10, java.sql.Types.INTEGER);
            ps.executeUpdate();
        }
    }

    private static int count(Connection c, String sql, String... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setString(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                int n = 0;
                while (rs.next()) n++;
                return n;
            }
        }
    }

    @Test
    void sqliteSchemaCreatesAndIsIdempotent() throws SQLException {
        try (Connection c = open()) {
            createAll(c);
            createAll(c);
            assertEquals(0, count(c, "SELECT * FROM tags"));
            assertEquals(0, count(c, "SELECT * FROM tag_requests"));
            assertEquals(0, count(c, "SELECT * FROM player_preferences"));
            assertEquals(0, count(c, "SELECT * FROM player_tag_color_preferences"));
        }
    }

    @Test
    void replaceIntoReplacesByNameAndNamesMatchIgnoringCase() throws SQLException {
        try (Connection c = open()) {
            createAll(c);
            replaceTag(c, "Admin", "[Admin]", "prefix");
            replaceTag(c, "admin", "[Boss]", "prefix");
            assertEquals(1, count(c, "SELECT * FROM tags"));
            assertEquals(1, count(c, "SELECT display FROM tags WHERE name = ?", "ADMIN"));
            assertEquals(1, count(c, "SELECT name FROM tags WHERE display = ? LIMIT 1;", "[boss]"));
        }
    }

    @Test
    void typeIsConstrainedLikeTheMysqlEnum() throws SQLException {
        try (Connection c = open()) {
            createAll(c);
            assertThrows(SQLException.class, () -> replaceTag(c, "x", "x", "middle"));
            replaceTag(c, "both", "b", "both");
            assertEquals(1, count(c, "SELECT * FROM tags WHERE type = 'prefix' OR type = 'both' ORDER BY weight DESC;"));
        }
    }

    @Test
    void likeWithEscapeWorksAsOnMysql() throws SQLException {
        try (Connection c = open()) {
            createAll(c);
            replaceTag(c, "Steve1", "a", "prefix");
            replaceTag(c, "Steve_x", "b", "prefix");
            assertEquals(1, count(c, "SELECT * FROM tags WHERE owner_uuid IS NULL AND name LIKE ? ESCAPE '!'", "Steve!_%"));
        }
    }

    @Test
    void upsertInsertsThenUpdates() throws SQLException {
        try (Connection c = open()) {
            createAll(c);
            for (String bracket : new String[]{"&a", "&b"}) {
                try (PreparedStatement ps = c.prepareStatement(SqlDialect.SQLITE.upsertTagColorPreference())) {
                    ps.setString(1, "uuid");
                    ps.setString(2, "vip");
                    ps.setString(3, bracket);
                    ps.setString(4, "&c");
                    ps.executeUpdate();
                }
            }
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT bracket_color_code, content_color_code FROM player_tag_color_preferences")) {
                assertTrue(rs.next());
                assertEquals("&b", rs.getString(1));
                assertEquals("&c", rs.getString(2));
                assertFalse(rs.next());
            }
        }
    }

    @Test
    void requestsGetAutoIncrementIds() throws SQLException {
        try (Connection c = open()) {
            createAll(c);
            for (int i = 0; i < 2; i++) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO tag_requests (player_uuid, player_name, tag_display) VALUES (?, ?, ?)")) {
                    ps.setString(1, "u" + i);
                    ps.setString(2, "n");
                    ps.setString(3, "t");
                    ps.executeUpdate();
                }
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id FROM tag_requests ORDER BY id")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
        }
    }

    @Test
    void migrationStatementsAddMissingColumns() throws SQLException {
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE tags (name TEXT PRIMARY KEY, display TEXT NOT NULL, type TEXT NOT NULL, "
                    + "public BOOLEAN NOT NULL, color BOOLEAN NOT NULL, material TEXT NOT NULL, weight INT NOT NULL)");
            st.executeUpdate(SqlDialect.SQLITE.addColorable());
            st.executeUpdate(SqlDialect.SQLITE.addOwnerUuid());
            st.executeUpdate(SqlDialect.SQLITE.createOwnerIndex());
            st.executeUpdate(SqlDialect.SQLITE.createOwnerIndex());
            st.executeUpdate(SqlDialect.SQLITE.addSlot());
            st.executeUpdate("INSERT INTO tags (name, display, type, public, color, material, weight) "
                    + "VALUES ('a', 'a', 'prefix', 1, 0, 'm', 1)");
            try (ResultSet rs = st.executeQuery("SELECT colorable, owner_uuid, slot FROM tags")) {
                assertTrue(rs.next());
                assertTrue(rs.getBoolean(1));
                assertNull(rs.getString(2));
            }
        }
    }

    @Test
    void purgeDropAndRecreateWorks() throws SQLException {
        try (Connection c = open(); Statement st = c.createStatement()) {
            createAll(c);
            replaceTag(c, "a", "a", "prefix");
            st.executeUpdate("DROP TABLE IF EXISTS tags");
            st.executeUpdate("DROP TABLE IF EXISTS tag_requests");
            for (String sql : SqlDialect.SQLITE.recreateTags()) st.executeUpdate(sql);
            st.executeUpdate(SqlDialect.SQLITE.recreateRequests());
            assertEquals(0, count(c, "SELECT * FROM tags"));
        }
    }
}
