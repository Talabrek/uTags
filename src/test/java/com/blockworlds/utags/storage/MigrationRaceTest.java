package com.blockworlds.utags.storage;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two servers starting together can both run an ALTER TABLE: the loser carries on instead of failing. */
class MigrationRaceTest {

    private static boolean hasColumn(Connection c, String column) throws SQLException {
        try (var rs = c.getMetaData().getColumns(null, null, "tags", column)) {
            return rs.next();
        }
    }

    @Test
    void aSecondAlterThatFindsTheColumnThereIsTolerated() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:"); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE tags (name TEXT)");
            String alter = "ALTER TABLE tags ADD COLUMN colorable INTEGER DEFAULT 0";
            Migrations.execTolerating(c, alter, () -> hasColumn(c, "colorable")); // the first server
            AtomicInteger checks = new AtomicInteger();
            // the second server saw no column when it looked, then ran the ALTER after the first one finished
            Migrations.execTolerating(c, alter, () -> {
                checks.incrementAndGet();
                return hasColumn(c, "colorable");
            });
            assertEquals(1, checks.get(), "the failure was re-checked once");
            assertTrue(hasColumn(c, "colorable"));
        }
    }

    @Test
    void aFailureThatLeavesTheColumnMissingIsStillAnError() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:"); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE tags (name TEXT)");
            assertThrows(SQLException.class, () -> Migrations.execTolerating(c,
                    "ALTER TABLE no_such_table ADD COLUMN slot INTEGER", () -> hasColumn(c, "slot")));
        }
    }
}
