package com.blockworlds.utags;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The SQLite pool the plugin builds, opened for real on a file: schema, WAL, data surviving a reopen. */
class SqlitePoolTest {

    @TempDir
    Path dir;

    private HikariDataSource open(File file) {
        HikariConfig config = new HikariConfig();
        com.blockworlds.utags.storage.Database.applySqlitePool(config, file);
        return new HikariDataSource(config);
    }

    @Test
    void createsSchemaInWalModeAndKeepsDataAcrossReopen() throws Exception {
        File file = dir.resolve("utags.db").toFile();
        try (HikariDataSource ds = open(file); Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            for (String sql : SqlDialect.SQLITE.createTags()) st.executeUpdate(sql);
            for (String sql : SqlDialect.SQLITE.createOtherTables()) st.executeUpdate(sql);
            st.executeUpdate("INSERT INTO player_preferences (player_uuid, name_color_code) VALUES ('u', '&a')");
            try (ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
                assertTrue(rs.next());
                assertEquals("wal", rs.getString(1));
            }
        }
        assertTrue(file.exists());
        try (HikariDataSource ds = open(file); Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name_color_code FROM player_preferences WHERE player_uuid = 'u'")) {
            assertTrue(rs.next());
            assertEquals("&a", rs.getString(1));
        }
    }

    @Test
    void severalPooledConnectionsCanReadWhileOneWrites() throws Exception {
        try (HikariDataSource ds = open(dir.resolve("two.db").toFile());
             Connection writer = ds.getConnection(); Connection reader = ds.getConnection()) {
            try (Statement st = writer.createStatement()) {
                for (String sql : SqlDialect.SQLITE.createOtherTables()) st.executeUpdate(sql);
                writer.setAutoCommit(false);
                st.executeUpdate("INSERT INTO player_preferences (player_uuid, name_color_code) VALUES ('w', '&b')");
                try (Statement rs = reader.createStatement(); ResultSet r = rs.executeQuery("SELECT * FROM player_preferences")) {
                    assertTrue(!r.next(), "uncommitted row is invisible to the other connection");
                }
                writer.commit();
            }
        }
    }
}
