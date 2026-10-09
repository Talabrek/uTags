package com.blockworlds.utags.sync;

import com.blockworlds.utags.SqlDialect;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The two-server test on MySQL: two independent engines on one database. Skipped unless BW_MYSQL_DB is set (with
 * BW_MYSQL_HOST, BW_MYSQL_PORT, BW_MYSQL_USER, BW_MYSQL_PASSWORD). It DROPS and recreates the table utags_changes of
 * that database, so point it only at a database that is yours alone.
 */
@EnabledIfEnvironmentVariable(named = "BW_MYSQL_DB", matches = ".+")
class MysqlSyncEngineTest extends SyncEngineContract {

    @Override
    Connection open() throws SQLException {
        String url = "jdbc:mysql://" + env("BW_MYSQL_HOST", "127.0.0.1") + ":" + env("BW_MYSQL_PORT", "3306") + "/"
                + System.getenv("BW_MYSQL_DB") + "?useSSL=false&allowPublicKeyRetrieval=true";
        return DriverManager.getConnection(url, System.getenv("BW_MYSQL_USER"), env("BW_MYSQL_PASSWORD", env("BW_MYSQL_PASS", "")));
    }

    @Override
    SqlDialect dialect() {
        return SqlDialect.MYSQL;
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isEmpty() ? fallback : v;
    }
}
