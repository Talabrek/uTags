package com.blockworlds.utags.sync;

import com.blockworlds.utags.SqlDialect;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class SqliteSyncEngineTest extends SyncEngineContract {

    @TempDir
    Path dir;

    @Override
    Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + new File(dir.toFile(), "sync.db").getAbsolutePath());
    }

    @Override
    SqlDialect dialect() {
        return SqlDialect.SQLITE;
    }
}
