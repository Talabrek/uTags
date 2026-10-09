package com.blockworlds.utags;

import java.util.Locale;

/** Which database uTags stores its data in ({@code storage.type} in config.yml). */
public enum StorageType {
    SQLITE(SqlDialect.SQLITE),
    MYSQL(SqlDialect.MYSQL);

    private final SqlDialect dialect;

    StorageType(SqlDialect dialect) {
        this.dialect = dialect;
    }

    public SqlDialect dialect() {
        return dialect;
    }

    /**
     * The storage type to use. A recognised {@code storage.type} wins; a missing or unknown one means MySQL when
     * the config already has a {@code database.host} (an install from before SQLite support, which must keep
     * working without a config change) and SQLite otherwise.
     */
    public static StorageType resolve(String configured, boolean databaseHostSet) {
        if (configured != null) {
            String value = configured.trim().toLowerCase(Locale.ROOT);
            if (value.equals("sqlite")) return SQLITE;
            if (value.equals("mysql") || value.equals("mariadb")) return MYSQL;
        }
        return databaseHostSet ? MYSQL : SQLITE;
    }
}
