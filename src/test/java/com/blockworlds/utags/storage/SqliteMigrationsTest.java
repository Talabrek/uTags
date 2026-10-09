package com.blockworlds.utags.storage;

import com.blockworlds.utags.SqlDialect;

import java.sql.Connection;
import java.sql.DriverManager;

class SqliteMigrationsTest extends MigrationsContract {
    @Override
    Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @Override
    SqlDialect dialect() {
        return SqlDialect.SQLITE;
    }
}
