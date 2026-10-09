package com.blockworlds.utags.storage;

import com.blockworlds.utags.SqlDialect;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * The same migration tests on MySQL. Skipped unless BW_MYSQL_DB is set (with BW_MYSQL_HOST, BW_MYSQL_PORT,
 * BW_MYSQL_USER, BW_MYSQL_PASSWORD): it DROPS the uTags tables of that database at the start of every test, so point
 * it only at a database that is yours alone (one DB per run).
 */
@EnabledIfEnvironmentVariable(named = "BW_MYSQL_DB", matches = ".+")
class MysqlMigrationsTest extends MigrationsContract {
    @Override
    Connection open() throws Exception {
        String url = "jdbc:mysql://" + env("BW_MYSQL_HOST", "127.0.0.1") + ":" + env("BW_MYSQL_PORT", "3306") + "/"
                + System.getenv("BW_MYSQL_DB") + "?useSSL=false&allowPublicKeyRetrieval=true";
        Connection c = DriverManager.getConnection(url, System.getenv("BW_MYSQL_USER"), env("BW_MYSQL_PASSWORD", env("BW_MYSQL_PASS", "")));
        try (Statement st = c.createStatement()) {
            for (String table : new String[]{"utags_changes", "utags_grants", "utags_ops", "utags_players", "utags_selection", "utags_schema", "tag_requests", "player_tag_color_preferences", "player_preferences", "tags"}) {
                st.executeUpdate("DROP TABLE IF EXISTS " + table);
            }
        }
        return c;
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
