package com.blockworlds.utags.ledger.deep;

import com.blockworlds.utags.SqlDialect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

/** The deep tests on SQLite (a file database, so every open() is a real second connection). The control dialect. */
class SqliteDeepLedgerTest extends DeepLedgerBase {

    private Path file;

    @Override
    void reset() throws Exception {
        file = Files.createTempFile("utags-deep", ".db");
        try (Connection x = open()) {
            migrate(x, SqlDialect.SQLITE);
        }
    }

    @Override
    Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath() + "?busy_timeout=10000");
    }

    @Override
    SqlDialect dialect() {
        return SqlDialect.SQLITE;
    }

    @Override
    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        super.tearDown();
        Files.deleteIfExists(file);
        Files.deleteIfExists(Path.of(file + "-wal"));
        Files.deleteIfExists(Path.of(file + "-shm"));
    }
}
