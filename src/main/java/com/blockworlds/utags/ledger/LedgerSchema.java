package com.blockworlds.utags.ledger;

import com.blockworlds.utags.SqlDialect;

import java.util.List;

/** The DDL of migration 7: {@code utags_grants}, {@code utags_ops}, {@code utags_players} (contracts.md, "Ledger semantics"). */
public final class LedgerSchema {

    /**
     * Op ids are case-sensitive ("Order-A1" and "order-a1" are two ops), as on SQLite. MySQL's default collation
     * ignores case and accents, so the op id columns use a binary one.
     */
    static final String OP_ID_COLLATION = "CHARACTER SET utf8mb4 COLLATE utf8mb4_bin";

    private LedgerSchema() {
    }

    /** Migration 9: the op id columns of a ledger created by an earlier 1.4.0 build. MySQL only; safe to run twice. */
    public static List<String> binaryOpIds(SqlDialect dialect) {
        if (dialect == SqlDialect.SQLITE) return List.of();
        return List.of(
                "ALTER TABLE utags_ops MODIFY op_id VARCHAR(128) " + OP_ID_COLLATION + " NOT NULL",
                "ALTER TABLE utags_grants MODIFY op_id VARCHAR(128) " + OP_ID_COLLATION + " NOT NULL, "
                        + "MODIFY revoke_op_id VARCHAR(128) " + OP_ID_COLLATION + " NULL, "
                        + "MODIFY copied_from_op VARCHAR(128) " + OP_ID_COLLATION + " NULL");
    }

    public static List<String> create(SqlDialect dialect) {
        if (dialect == SqlDialect.SQLITE) {
            return List.of(
                    "CREATE TABLE IF NOT EXISTS utags_grants ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "player_uuid TEXT NOT NULL, "
                            + "tag_id TEXT NOT NULL COLLATE NOCASE, "
                            + "source TEXT NOT NULL, "
                            + "op_id TEXT NOT NULL UNIQUE, "
                            + "granted_at BIGINT NOT NULL, "
                            + "revoked_at BIGINT NULL, "
                            + "revoke_op_id TEXT NULL, "
                            + "copied_from_op TEXT NULL)",
                    "CREATE INDEX IF NOT EXISTS idx_utags_grants_player ON utags_grants (player_uuid, tag_id)",
                    "CREATE TABLE IF NOT EXISTS utags_ops ("
                            + "op_id TEXT NOT NULL PRIMARY KEY, "
                            + "kind TEXT NOT NULL, "
                            + "outcome TEXT NOT NULL, "
                            + "copied INTEGER NOT NULL DEFAULT 0, "
                            + "at BIGINT NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS utags_players ("
                            + "player_uuid TEXT NOT NULL PRIMARY KEY, "
                            + "updated_at BIGINT NOT NULL)");
        }
        return List.of(
                "CREATE TABLE IF NOT EXISTS utags_grants ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "player_uuid VARCHAR(36) NOT NULL, "
                        + "tag_id VARCHAR(255) NOT NULL, "
                        + "source VARCHAR(64) NOT NULL, "
                        + "op_id VARCHAR(128) " + OP_ID_COLLATION + " NOT NULL, "
                        + "granted_at BIGINT NOT NULL, "
                        + "revoked_at BIGINT NULL, "
                        + "revoke_op_id VARCHAR(128) " + OP_ID_COLLATION + " NULL, "
                        + "copied_from_op VARCHAR(128) " + OP_ID_COLLATION + " NULL, "
                        + "UNIQUE KEY uq_utags_grants_op (op_id), "
                        + "KEY idx_utags_grants_player (player_uuid, tag_id))",
                "CREATE TABLE IF NOT EXISTS utags_ops ("
                        + "op_id VARCHAR(128) " + OP_ID_COLLATION + " NOT NULL PRIMARY KEY, "
                        + "kind VARCHAR(16) NOT NULL, "
                        + "outcome VARCHAR(32) NOT NULL, "
                        + "copied INT NOT NULL DEFAULT 0, "
                        + "at BIGINT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS utags_players ("
                        + "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                        + "updated_at BIGINT NOT NULL)");
    }
}
