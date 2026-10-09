package com.blockworlds.utags.ledger;

import com.blockworlds.utags.SqlDialect;

import java.sql.Connection;
import java.sql.DriverManager;

class SqliteLedgerStoreTest extends LedgerStoreContract {
    private Connection db;

    @Override
    void reset() throws Exception {
        db = DriverManager.getConnection("jdbc:sqlite::memory:");
        migrate(db, SqlDialect.SQLITE);
    }

    @Override
    Connection open() {
        return db;
    }

    @Override
    SqlDialect dialect() {
        return SqlDialect.SQLITE;
    }
}
