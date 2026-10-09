package com.blockworlds.utags;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageTypeTest {

    @Test
    void missingTypeWithoutHostIsSqlite() {
        assertEquals(StorageType.SQLITE, StorageType.resolve(null, false));
    }

    @Test
    void missingTypeWithHostIsMysqlSoOldInstallsKeepWorking() {
        assertEquals(StorageType.MYSQL, StorageType.resolve(null, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sqlite", "SQLite", " sqlite "})
    void sqliteIsRecognisedEvenWhenAHostIsSet(String value) {
        assertEquals(StorageType.SQLITE, StorageType.resolve(value, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "MySQL", "mariadb"})
    void mysqlIsRecognisedEvenWithoutAHost(String value) {
        assertEquals(StorageType.MYSQL, StorageType.resolve(value, false));
    }

    @Test
    void unknownTypeFallsBackLikeAMissingOne() {
        assertEquals(StorageType.SQLITE, StorageType.resolve("postgres", false));
        assertEquals(StorageType.MYSQL, StorageType.resolve("postgres", true));
    }

    @Test
    void typesUseTheirOwnDialect() {
        assertEquals(SqlDialect.SQLITE, StorageType.SQLITE.dialect());
        assertEquals(SqlDialect.MYSQL, StorageType.MYSQL.dialect());
    }
}
