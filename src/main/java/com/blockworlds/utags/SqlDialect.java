package com.blockworlds.utags;

import java.util.List;

/**
 * The SQL that differs between the two supported databases. Everything else in uTags is plain SQL that both run
 * unchanged (SELECT/UPDATE/DELETE, {@code REPLACE INTO}, {@code LIKE ... ESCAPE}, {@code LIMIT}).
 * <p>
 * The MySQL strings are the statements uTags has always used, so existing installs are unaffected. SQLite
 * differences: {@code INTEGER PRIMARY KEY AUTOINCREMENT}, a CHECK instead of ENUM, {@code ADD COLUMN} has no
 * {@code AFTER}, no {@code MODIFY COLUMN}, no {@code ON DUPLICATE KEY} (it is {@code ON CONFLICT ... DO UPDATE}),
 * and text compares case-sensitively, so name/display columns are {@code COLLATE NOCASE} to match MySQL's
 * default collation.
 */
public enum SqlDialect {
    MYSQL, SQLITE;

    private static final String TAG_REQUESTS_MYSQL = "CREATE TABLE IF NOT EXISTS `tag_requests` ("
            + "`id` INT AUTO_INCREMENT PRIMARY KEY,"
            + "`player_uuid` VARCHAR(36) NOT NULL,"
            + "`player_name` VARCHAR(255) NOT NULL,"
            + "`tag_display` VARCHAR(255) NOT NULL);";

    private static final String TAG_REQUESTS_SQLITE = "CREATE TABLE IF NOT EXISTS `tag_requests` ("
            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "`player_uuid` TEXT NOT NULL,"
            + "`player_name` TEXT NOT NULL,"
            + "`tag_display` TEXT NOT NULL);";

    /** Statements that create the {@code tags} table (and its owner index) when it is missing. */
    public List<String> createTags() {
        if (this == SQLITE) {
            return List.of(
                    "CREATE TABLE IF NOT EXISTS `tags` ("
                            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "`name` TEXT NOT NULL UNIQUE COLLATE NOCASE,"
                            + "`display` TEXT NOT NULL COLLATE NOCASE,"
                            + "`type` TEXT NOT NULL CHECK (`type` IN ('prefix', 'suffix', 'both')),"
                            + "`public` BOOLEAN NOT NULL,"
                            + "`color` BOOLEAN NOT NULL,"
                            + "`colorable` BOOLEAN NOT NULL DEFAULT 1,"
                            + "`material` TEXT NOT NULL,"
                            + "`weight` INTEGER NOT NULL,"
                            + "`owner_uuid` TEXT NULL,"
                            + "`slot` INTEGER NULL);",
                    "CREATE INDEX IF NOT EXISTS `idx_tags_owner` ON `tags` (`owner_uuid`);");
        }
        return List.of("CREATE TABLE IF NOT EXISTS `tags` ("
                + "`id` INT AUTO_INCREMENT PRIMARY KEY,"
                + "`name` VARCHAR(255) NOT NULL,"
                + "`display` VARCHAR(255) NOT NULL,"
                + "`type` ENUM('prefix', 'suffix', 'both') NOT NULL,"
                + "`public` BOOLEAN NOT NULL,"
                + "`color` BOOLEAN NOT NULL,"
                + "`colorable` BOOLEAN NOT NULL DEFAULT TRUE,"
                + "`material` MEDIUMTEXT NOT NULL,"
                + "`weight` INT NOT NULL,"
                + "`owner_uuid` VARCHAR(36) NULL,"
                + "`slot` INT NULL,"
                + "KEY `idx_tags_owner` (`owner_uuid`)"
                + ");");
    }

    /** The other tables, created when missing. */
    public List<String> createOtherTables() {
        if (this == SQLITE) {
            return List.of(
                    TAG_REQUESTS_SQLITE,
                    "CREATE TABLE IF NOT EXISTS `player_preferences` ("
                            + "`player_uuid` TEXT PRIMARY KEY NOT NULL,"
                            + "`name_color_code` TEXT NULL);",
                    "CREATE TABLE IF NOT EXISTS `player_tag_color_preferences` ("
                            + "`player_uuid` TEXT NOT NULL,"
                            + "`tag_name` TEXT NOT NULL COLLATE NOCASE,"
                            + "`bracket_color_code` TEXT NULL,"
                            + "`content_color_code` TEXT NULL,"
                            + "PRIMARY KEY (`player_uuid`, `tag_name`));");
        }
        return List.of(
                TAG_REQUESTS_MYSQL,
                "CREATE TABLE IF NOT EXISTS `player_preferences` ("
                        + "`player_uuid` VARCHAR(36) PRIMARY KEY NOT NULL,"
                        + "`name_color_code` VARCHAR(2) NULL"
                        + ");",
                "CREATE TABLE IF NOT EXISTS `player_tag_color_preferences` ("
                        + "`player_uuid` VARCHAR(36) NOT NULL,"
                        + "`tag_name` VARCHAR(255) NOT NULL,"
                        + "`bracket_color_code` VARCHAR(2) NULL,"
                        + "`content_color_code` VARCHAR(2) NULL,"
                        + "PRIMARY KEY (`player_uuid`, `tag_name`)"
                        + ");");
    }

    /** What {@code /tag admin purge tags} recreates after dropping the table. */
    public List<String> recreateTags() {
        if (this == SQLITE) return createTags();
        return List.of("CREATE TABLE IF NOT EXISTS tags ("
                + "`name` VARCHAR(255) PRIMARY KEY,"
                + "`display` VARCHAR(255) NOT NULL,"
                + "`type` ENUM('prefix', 'suffix', 'both') NOT NULL,"
                + "`public` BOOLEAN NOT NULL,"
                + "`color` BOOLEAN NOT NULL,"
                + "`colorable` BOOLEAN NOT NULL DEFAULT TRUE,"
                + "`material` MEDIUMTEXT NOT NULL,"
                + "`weight` INT NOT NULL,"
                + "`owner_uuid` VARCHAR(36) NULL,"
                + "`slot` INT NULL,"
                + "KEY `idx_tags_owner` (`owner_uuid`)"
                + ");");
    }

    /** What {@code /tag admin purge requests} recreates after dropping the table. */
    public String recreateRequests() {
        return this == SQLITE ? TAG_REQUESTS_SQLITE : TAG_REQUESTS_MYSQL;
    }

    /** Schema 3: widen {@code material}. SQLite creates the column as TEXT already, so there is nothing to do. */
    public String widenMaterial() {
        return this == SQLITE ? null : "ALTER TABLE tags MODIFY COLUMN `material` MEDIUMTEXT NOT NULL;";
    }

    /** Schema 4: add {@code colorable}. */
    public String addColorable() {
        return this == SQLITE
                ? "ALTER TABLE tags ADD COLUMN `colorable` BOOLEAN NOT NULL DEFAULT 1;"
                : "ALTER TABLE tags ADD COLUMN `colorable` BOOLEAN NOT NULL DEFAULT TRUE AFTER `color`;";
    }

    /** Schema 5: add {@code owner_uuid}. */
    public String addOwnerUuid() {
        return this == SQLITE
                ? "ALTER TABLE tags ADD COLUMN `owner_uuid` TEXT NULL;"
                : "ALTER TABLE tags ADD COLUMN `owner_uuid` VARCHAR(36) NULL;";
    }

    /** Schema 5: add {@code slot}. Same on both. */
    public String addSlot() {
        return "ALTER TABLE tags ADD COLUMN `slot` INT NULL;";
    }

    /** Schema 5: the owner index (SQLite can say IF NOT EXISTS, MySQL cannot). */
    public String createOwnerIndex() {
        return this == SQLITE
                ? "CREATE INDEX IF NOT EXISTS `idx_tags_owner` ON tags (`owner_uuid`);"
                : "CREATE INDEX `idx_tags_owner` ON tags (`owner_uuid`);";
    }

    /** Insert-or-update of a player's colors for one tag; parameters: uuid, tag name, bracket code, content code. */
    public String upsertTagColorPreference() {
        String insert = "INSERT INTO player_tag_color_preferences "
                + "(player_uuid, tag_name, bracket_color_code, content_color_code) VALUES (?, ?, ?, ?) ";
        return this == SQLITE
                ? insert + "ON CONFLICT(player_uuid, tag_name) DO UPDATE SET "
                        + "bracket_color_code = excluded.bracket_color_code, "
                        + "content_color_code = excluded.content_color_code"
                : insert + "ON DUPLICATE KEY UPDATE bracket_color_code = VALUES(bracket_color_code), "
                        + "content_color_code = VALUES(content_color_code)";
    }
}
