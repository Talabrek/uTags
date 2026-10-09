package com.blockworlds.utags.storage;

import com.blockworlds.utags.SqlDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Numbered, additive schema migrations with the version kept in the database ({@code utags_schema}) on SQLite and
 * MySQL alike. Core migrations are 1-99 and only add (no drops or renames), so a 1.3.0 server on a shared MySQL
 * database keeps working until it is upgraded; uTags Pro adds 100+ that touch only {@code utags_pro_*} tables.
 * Every step checks the live schema first, so running one twice (two servers starting together) is harmless.
 */
public final class Migrations {

    @FunctionalInterface
    public interface Step {
        void apply(Connection connection, SqlDialect dialect, Logger log) throws SQLException;
    }

    public record Migration(int version, String name, Step step) {
    }

    /** The newest core migration. */
    public static final int LATEST_CORE = 9; // 7 ledger, 8 network sync, 9 case-sensitive op ids

    private Migrations() {
    }

    /** The core migrations. 3, 4 and 5 are the schema steps 1.3.0 numbered in config.yml (database.schema). */
    public static List<Migration> core() {
        return List.of(
                new Migration(1, "base tables", (c, d, log) -> {
                    try (Statement st = c.createStatement()) {
                        for (String sql : d.createTags()) {
                            // an index on owner_uuid can only be built once migration 5 has added the column
                            try {
                                st.executeUpdate(sql);
                            } catch (SQLException e) {
                                if (!sql.startsWith("CREATE INDEX")) throw e;
                            }
                        }
                        for (String sql : d.createOtherTables()) st.executeUpdate(sql);
                    }
                }),
                new Migration(2, "reserved (schema 2 of 1.0)", (c, d, log) -> {
                }),
                new Migration(3, "widen tags.material", (c, d, log) -> {
                    String sql = d.widenMaterial();
                    if (sql != null && hasColumn(c, "tags", "material")) exec(c, sql);
                }),
                new Migration(4, "tags.colorable", (c, d, log) -> {
                    if (!hasColumn(c, "tags", "colorable")) {
                        log.info("Adding 'colorable' column to 'tags' table...");
                        execTolerating(c, d.addColorable(), () -> hasColumn(c, "tags", "colorable"));
                    }
                }),
                new Migration(5, "tags.owner_uuid and tags.slot", (c, d, log) -> {
                    if (!hasColumn(c, "tags", "owner_uuid")) {
                        log.info("Adding 'owner_uuid' column to 'tags' table...");
                        execTolerating(c, d.addOwnerUuid(), () -> hasColumn(c, "tags", "owner_uuid"));
                        // the index goes with the column: if another server won the race it built it as well
                        execTolerating(c, d.createOwnerIndex(), () -> hasColumn(c, "tags", "owner_uuid"));
                    }
                    if (!hasColumn(c, "tags", "slot")) {
                        log.info("Adding 'slot' column to 'tags' table...");
                        execTolerating(c, d.addSlot(), () -> hasColumn(c, "tags", "slot"));
                    }
                }),
                new Migration(6, "utags_selection", (c, d, log) -> exec(c,
                        "CREATE TABLE IF NOT EXISTS utags_selection ("
                                + "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                                + "prefix_tag VARCHAR(255) NULL, "
                                + "suffix_tag VARCHAR(255) NULL, "
                                + "updated_at BIGINT NOT NULL)")),
                new Migration(7, "ledger: utags_grants, utags_ops, utags_players", (c, d, log) -> {
                    for (String sql : com.blockworlds.utags.ledger.LedgerSchema.create(d)) exec(c, sql);
                }),
                new Migration(8, "utags_changes (network sync)", (c, d, log) -> createChangesTable(c, d)),
                new Migration(9, "ledger: case-sensitive op ids", (c, d, log) -> {
                    for (String sql : com.blockworlds.utags.ledger.LedgerSchema.binaryOpIds(d)) exec(c, sql);
                }));
    }

    /** The change feed table of network sync; created on SQLite too (harmless, the sync module only runs on MySQL). */
    public static void createChangesTable(Connection c, SqlDialect d) throws SQLException {
        if (d == SqlDialect.SQLITE) {
            exec(c, "CREATE TABLE IF NOT EXISTS utags_changes ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, player_uuid TEXT NULL, "
                    + "server_id TEXT NOT NULL, created_at BIGINT NOT NULL)");
            exec(c, "CREATE INDEX IF NOT EXISTS idx_utags_changes_created ON utags_changes (created_at)");
        } else {
            exec(c, "CREATE TABLE IF NOT EXISTS utags_changes ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, kind VARCHAR(16) NOT NULL, player_uuid VARCHAR(36) NULL, "
                    + "server_id VARCHAR(36) NOT NULL, created_at BIGINT NOT NULL, "
                    + "KEY idx_utags_changes_created (created_at))");
        }
    }

    /**
     * Runs every migration newer than the stored version, in order, recording the version after each one.
     *
     * @param legacyVersion the 1.3.0 {@code database.schema} value from config.yml; used only when the database has
     *                      no version yet (then every step is still idempotent; this only skips known-done steps)
     * @return the version after the run
     */
    public static int run(Connection c, SqlDialect dialect, List<Migration> migrations, int legacyVersion, Logger log)
            throws SQLException {
        exec(c, "CREATE TABLE IF NOT EXISTS utags_schema (id INTEGER NOT NULL PRIMARY KEY, version INTEGER NOT NULL)");
        Integer stored = readVersion(c);
        int current;
        if (stored == null) {
            current = Math.max(0, legacyVersion);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO utags_schema (id, version) VALUES (1, ?)")) {
                ps.setInt(1, current);
                ps.executeUpdate();
            } catch (SQLException duplicate) {
                // another server inserted the row first; carry on from what is stored
                Integer now = readVersion(c);
                if (now == null) throw duplicate;
                current = now;
            }
        } else {
            current = stored;
        }
        for (Migration m : migrations.stream().sorted(Comparator.comparingInt(Migration::version)).toList()) {
            if (m.version() <= current) continue;
            log.info("Database migration " + m.version() + ": " + m.name());
            m.step().apply(c, dialect, log);
            // never lowers the version: another server may already have migrated further
            try (PreparedStatement ps = c.prepareStatement("UPDATE utags_schema SET version = ? WHERE id = 1 AND version < ?")) {
                ps.setInt(1, m.version());
                ps.setInt(2, m.version());
                ps.executeUpdate();
            }
            current = m.version();
        }
        return current;
    }

    /** The recorded schema version, or null when none is recorded yet. */
    public static Integer readVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT version FROM utags_schema WHERE id = 1")) {
            return rs.next() ? rs.getInt(1) : null;
        }
    }

    static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        try (ResultSet rs = c.getMetaData().getColumns(c.getCatalog(), null, table, column)) {
            return rs.next();
        }
    }

    @FunctionalInterface
    public interface Check {
        boolean test() throws SQLException;
    }

    /**
     * Runs an ALTER or CREATE INDEX that another server may run at the same moment: the check after the check
     * ("is this column there now?") above is not atomic with the statement. When the statement fails and
     * {@code alreadyApplied} says the other server got there first, carry on instead of failing the migration.
     */
    public static void execTolerating(Connection c, String sql, Check alreadyApplied) throws SQLException {
        try {
            exec(c, sql);
        } catch (SQLException e) {
            boolean done;
            try {
                done = alreadyApplied.test();
            } catch (SQLException again) {
                e.addSuppressed(again);
                throw e;
            }
            if (!done) throw e;
        }
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
