package com.blockworlds.utags.storage;

import com.blockworlds.utags.SqlDialect;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The migration tests, shared by the SQLite run and the (gated) MySQL run. */
abstract class MigrationsContract {

    /** A clean database with none of the uTags tables. */
    abstract Connection open() throws Exception;

    abstract SqlDialect dialect();

    private final List<String> logged = new ArrayList<>();

    private Logger logger() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { logged.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        return log;
    }

    private long migrationsRun() {
        return logged.stream().filter(m -> m.startsWith("Database migration")).count();
    }

    private int run(Connection c, int legacy) throws Exception {
        logged.clear();
        return Migrations.run(c, dialect(), Migrations.core(), legacy, logger());
    }

    private static int count(Connection c, String table) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void exec(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    @Test
    void emptyDatabaseGetsEverythingAndTheVersionIsRecorded() throws Exception {
        try (Connection c = open()) {
            assertEquals(Migrations.LATEST_CORE, run(c, 0));
            assertEquals(Migrations.LATEST_CORE, Migrations.readVersion(c));
            assertEquals((long) Migrations.core().size(), migrationsRun());
            for (String table : List.of("tags", "tag_requests", "player_preferences", "player_tag_color_preferences", "utags_selection", "utags_grants", "utags_ops", "utags_players", "utags_changes", "utags_schema")) {
                int expected = table.equals("utags_schema") ? 1 : 0;
                assertEquals(expected, count(c, table), table);
            }
            exec(c, "INSERT INTO utags_selection (player_uuid, prefix_tag, suffix_tag, updated_at) VALUES ('u', 'vip', NULL, 1)");
        }
    }

    @Test
    void rerunningDoesNothing() throws Exception {
        try (Connection c = open()) {
            run(c, 0);
            assertEquals(Migrations.LATEST_CORE, run(c, 0));
            assertEquals(0, migrationsRun());
            // a lower legacy number never lowers or re-runs anything once the database has its own version
            assertEquals(Migrations.LATEST_CORE, run(c, 2));
            assertEquals(0, migrationsRun());
            assertEquals(Migrations.LATEST_CORE, Migrations.readVersion(c));
        }
    }

    /** What a 1.3.0 server left: the latest tables and some rows, and no utags_schema. */
    private void create13(Connection c) throws Exception {
        for (String sql : dialect().createTags()) exec(c, sql);
        for (String sql : dialect().createOtherTables()) exec(c, sql);
        exec(c, "INSERT INTO tags (name, display, type, public, color, colorable, material, weight, owner_uuid, slot) "
                + "VALUES ('vip', '&8[&6VIP&8]', 'prefix', 1, 1, 1, 'NAME_TAG', 5, NULL, NULL)");
        exec(c, "INSERT INTO tags (name, display, type, public, color, colorable, material, weight, owner_uuid, slot) "
                + "VALUES ('c11111111-2222-3333-4444-555555555555-1', '&a[Mine]', 'prefix', 0, 0, 0, 'PLAYER_HEAD', 1, "
                + "'11111111-2222-3333-4444-555555555555', 1)");
        exec(c, "INSERT INTO player_preferences (player_uuid, name_color_code) VALUES ('p1', '&a')");
        exec(c, "INSERT INTO player_tag_color_preferences (player_uuid, tag_name, bracket_color_code, content_color_code) VALUES ('p1', 'vip', '&c', NULL)");
        exec(c, "INSERT INTO tag_requests (player_uuid, player_name, tag_display) VALUES ('p2', 'Steve', '&b[Steve]')");
    }

    private void assert13RowsIntact(Connection c) throws Exception {
        assertEquals(2, count(c, "tags"));
        assertEquals(1, count(c, "player_preferences"));
        assertEquals(1, count(c, "player_tag_color_preferences"));
        assertEquals(1, count(c, "tag_requests"));
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT display, weight, owner_uuid, slot FROM tags WHERE name = 'c11111111-2222-3333-4444-555555555555-1'")) {
            assertTrue(rs.next());
            assertEquals("&a[Mine]", rs.getString(1));
            assertEquals(1, rs.getInt(2));
            assertEquals("11111111-2222-3333-4444-555555555555", rs.getString(3));
            assertEquals(1, rs.getInt(4));
        }
    }

    @Test
    void a13DatabaseWithoutConfigSchemaKeepsEverything() throws Exception {
        try (Connection c = open()) {
            create13(c);
            // 1.3.0 never wrote database.schema for SQLite: legacy 0
            assertEquals(Migrations.LATEST_CORE, run(c, 0));
            assert13RowsIntact(c);
            assertEquals(0, count(c, "utags_selection"));
            run(c, 0);
            assertEquals(0, migrationsRun());
            assert13RowsIntact(c);
        }
    }

    @Test
    void a13MysqlStyleDatabaseWithConfigSchema5OnlyRunsTheNewMigration() throws Exception {
        try (Connection c = open()) {
            create13(c);
            assertEquals(Migrations.LATEST_CORE, run(c, 5));
            assertEquals(Migrations.core().stream().filter(m -> m.version() > 5).count(), migrationsRun());
            assert13RowsIntact(c);
            run(c, 5);
            assertEquals(0, migrationsRun());
        }
    }

    /** A tags table from before colorable, owner_uuid and slot existed. */
    @Test
    void anOldShapedTagsTableGainsTheColumnsAndKeepsItsRows() throws Exception {
        try (Connection c = open()) {
            boolean sqlite = dialect() == SqlDialect.SQLITE;
            exec(c, sqlite
                    ? "CREATE TABLE tags (name TEXT PRIMARY KEY NOT NULL, display TEXT NOT NULL, type TEXT NOT NULL, "
                    + "public BOOLEAN NOT NULL, color BOOLEAN NOT NULL, material TEXT NOT NULL, weight INTEGER NOT NULL)"
                    : "CREATE TABLE tags (name VARCHAR(255) PRIMARY KEY NOT NULL, display VARCHAR(255) NOT NULL, "
                    + "type ENUM('prefix','suffix','both') NOT NULL, public BOOLEAN NOT NULL, color BOOLEAN NOT NULL, "
                    + "material TEXT NOT NULL, weight INT NOT NULL)");
            exec(c, "INSERT INTO tags (name, display, type, public, color, material, weight) VALUES ('old', '&7[Old]', 'both', 1, 0, 'NAME_TAG', 3)");
            assertEquals(Migrations.LATEST_CORE, run(c, 2));
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT display, colorable, owner_uuid, slot FROM tags WHERE name = 'old'")) {
                assertTrue(rs.next());
                assertEquals("&7[Old]", rs.getString(1));
                assertTrue(rs.getBoolean(2), "existing tags are colorable by default");
                assertNull(rs.getString(3));
                assertFalse(rs.next());
            }
        }
    }
}
