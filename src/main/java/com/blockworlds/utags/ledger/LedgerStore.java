package com.blockworlds.utags.ledger;

import com.blockworlds.utags.SqlDialect;
import com.blockworlds.utags.api.GrantOutcome;
import com.blockworlds.utags.api.GrantResult;
import com.blockworlds.utags.api.RevokeOutcome;
import com.blockworlds.utags.api.TransferResult;
import com.blockworlds.utags.selection.Selection;
import com.blockworlds.utags.selection.SelectionStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The grants ledger on plain JDBC (contracts.md, "Ledger semantics"). Every changing method is one transaction that
 * follows the locking order: op row first (a duplicate is a replay), then the lock row of each player involved, then
 * reads, decision, writes, commit. MySQL locks with {@code SELECT ... FOR UPDATE} on {@code utags_players}; SQLite
 * takes the write lock up front with {@code BEGIN IMMEDIATE}. A deadlock victim is retried from the start.
 * <p>
 * Whether the player owns the tag through a permission, and whether the tag is defined, are facts outside the
 * database: the caller looks them up first and passes them in.
 */
public final class LedgerStore {

    private static final int DEADLOCK_RETRIES = 4;
    /** MySQL's "lock wait timeout": also SQLState 40001, but waiting again would only hold a thread for minutes. */
    private static final int LOCK_WAIT_TIMEOUT = 1205;
    /** The width of the op id columns; a transfer adds "#n" to the caller's op id (at most 100 characters). */
    private static final int MAX_OP_ID = 128;

    /** One ledger row. */
    public record Row(long id, UUID player, String tagId, String source, String opId, long grantedAt,
                      Long revokedAt, String revokeOpId, String copiedFromOp) {
    }

    /** A revoke result plus what it did to the selection (the caller applies the LuckPerms output). */
    public record RevokeOutcomeDetail(RevokeOutcome outcome, boolean replayed, boolean clearedPrefix, boolean clearedSuffix) {
        public boolean clearedSelection() {
            return clearedPrefix || clearedSuffix;
        }
    }

    /** A transfer result plus the selection the target adopted (null when none). */
    public record TransferDetail(int copied, boolean replayed, Selection adopted) {
        public TransferResult result() {
            return new TransferResult(copied, replayed);
        }
    }

    @FunctionalInterface
    private interface Work<T> {
        T run() throws SQLException;
    }

    private final SqlDialect dialect;
    private final LongSupplier clock;

    public LedgerStore(SqlDialect dialect) {
        this(dialect, System::currentTimeMillis);
    }

    public LedgerStore(SqlDialect dialect, LongSupplier clock) {
        this.dialect = dialect;
        this.clock = clock;
    }

    // --- operations ---

    /** Grants; {@code tagId} must be the canonical tag name. UNKNOWN_TAG writes nothing, not even the op. */
    public GrantResult grant(Connection c, UUID player, String tagId, String source, String opId,
                             boolean tagExists, boolean permissionOwned) throws SQLException {
        return inTransaction(c, () -> {
            if (!insertOp(c, opId, "grant")) {
                return new GrantResult(GrantOutcome.valueOf(storedOutcome(c, opId, "grant")), true);
            }
            lockPlayer(c, player);
            if (!tagExists) return null; // roll back, op row included, so a retry after the tag exists can succeed
            boolean owned = permissionOwned || countUnrevoked(c, player, tagId, null) > 0;
            GrantOutcome outcome = owned ? GrantOutcome.ALREADY_OWNED : GrantOutcome.GRANTED;
            insertGrant(c, player, tagId, source, opId, null);
            finishOp(c, opId, outcome.name(), 0);
            return new GrantResult(outcome, false);
        }, new GrantResult(GrantOutcome.UNKNOWN_TAG, false));
    }

    public RevokeOutcomeDetail revoke(Connection c, UUID player, String tagId, String source, String opId,
                                      boolean permissionOwned) throws SQLException {
        return inTransaction(c, () -> {
            if (!insertOp(c, opId, "revoke")) {
                return new RevokeOutcomeDetail(RevokeOutcome.valueOf(storedOutcome(c, opId, "revoke")), true, false, false);
            }
            lockPlayer(c, player);
            if (countUnrevoked(c, player, tagId, source) == 0) {
                finishOp(c, opId, RevokeOutcome.NOT_HELD.name(), 0);
                return new RevokeOutcomeDetail(RevokeOutcome.NOT_HELD, false, false, false);
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE utags_grants SET revoked_at = ?, revoke_op_id = ? "
                    + "WHERE player_uuid = ? AND tag_id = ? AND source = ? AND revoked_at IS NULL")) {
                ps.setLong(1, clock.getAsLong());
                ps.setString(2, opId);
                ps.setString(3, player.toString());
                ps.setString(4, tagId);
                ps.setString(5, source);
                ps.executeUpdate();
            }
            boolean gone = !permissionOwned && countUnrevoked(c, player, tagId, null) == 0;
            boolean clearPrefix = false;
            boolean clearSuffix = false;
            if (gone) {
                Selection selection = SelectionStore.find(c, player);
                if (selection != null) {
                    clearPrefix = tagId.equalsIgnoreCase(selection.prefixTag());
                    clearSuffix = tagId.equalsIgnoreCase(selection.suffixTag());
                    if (clearPrefix) SelectionStore.set(c, player, true, null);
                    if (clearSuffix) SelectionStore.set(c, player, false, null);
                }
            }
            RevokeOutcome outcome = gone ? RevokeOutcome.REVOKED_GONE : RevokeOutcome.REVOKED_STILL_OWNED;
            finishOp(c, opId, outcome.name(), 0);
            return new RevokeOutcomeDetail(outcome, false, clearPrefix, clearSuffix);
        }, null);
    }

    public TransferDetail transferAll(Connection c, UUID from, UUID into, String opId) throws SQLException {
        if (from.equals(into)) throw new IllegalArgumentException("from and into are the same player");
        return inTransaction(c, () -> {
            if (!insertOp(c, opId, "transfer")) {
                storedOutcome(c, opId, "transfer");
                return new TransferDetail(storedCopied(c, opId), true, null);
            }
            UUID first = from.toString().compareTo(into.toString()) < 0 ? from : into;
            UUID second = first.equals(from) ? into : from;
            lockPlayer(c, first);
            lockPlayer(c, second);
            int copied = 0;
            for (Row row : query(c, "SELECT * FROM utags_grants WHERE player_uuid = ? AND revoked_at IS NULL ORDER BY id", from)) {
                if (countUnrevoked(c, into, row.tagId(), row.source()) > 0) continue;
                copied++;
                insertGrant(c, into, row.tagId(), row.source(), opId + "#" + copied, opId);
            }
            Selection adopted = null;
            Selection theirs = SelectionStore.find(c, into);
            if (theirs == null || (theirs.prefixTag() == null && theirs.suffixTag() == null)) {
                Selection mine = SelectionStore.find(c, from);
                if (mine != null && (mine.prefixTag() != null || mine.suffixTag() != null)) {
                    if (mine.prefixTag() != null) SelectionStore.set(c, into, true, mine.prefixTag());
                    if (mine.suffixTag() != null) SelectionStore.set(c, into, false, mine.suffixTag());
                    adopted = mine;
                }
            }
            finishOp(c, opId, "COPIED", copied);
            return new TransferDetail(copied, false, adopted);
        }, null);
    }

    /**
     * Sets the tag the player wears ({@code tagId} null clears it) under the player's lock, so a select cannot cross a
     * revoke: either the revoke sees the selection and clears it, or the select sees the revoke and writes nothing.
     *
     * @return false (nothing written) when the player owns the tag neither by permission nor by an unrevoked row
     */
    public boolean select(Connection c, UUID player, boolean prefix, String tagId, boolean permissionOwned) throws SQLException {
        return inTransaction(c, () -> {
            lockPlayer(c, player);
            if (tagId != null && !permissionOwned && countUnrevoked(c, player, tagId, null) == 0) return null;
            SelectionStore.set(c, player, prefix, tagId);
            return Boolean.TRUE;
        }, Boolean.FALSE);
    }

    // --- reads (no transaction) ---

    /** The tags with at least one unrevoked row, as stored. */
    public Set<String> owned(Connection c, UUID player) throws SQLException {
        Set<String> tags = new LinkedHashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT DISTINCT tag_id FROM utags_grants WHERE player_uuid = ? AND revoked_at IS NULL ORDER BY tag_id")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tags.add(rs.getString(1));
            }
        }
        return tags;
    }

    /** Every row of the player, newest first (for /tag admin grants). */
    public List<Row> rows(Connection c, UUID player) throws SQLException {
        return query(c, "SELECT * FROM utags_grants WHERE player_uuid = ? ORDER BY id DESC", player);
    }

    private List<Row> query(Connection c, String sql, UUID player) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long revoked = rs.getLong("revoked_at");
                    Long revokedAt = rs.wasNull() ? null : revoked;
                    rows.add(new Row(rs.getLong("id"), UUID.fromString(rs.getString("player_uuid")), rs.getString("tag_id"),
                            rs.getString("source"), rs.getString("op_id"), rs.getLong("granted_at"), revokedAt,
                            rs.getString("revoke_op_id"), rs.getString("copied_from_op")));
                }
            }
        }
        return rows;
    }

    // --- statements ---

    /** Step 1: false when the op id exists (a replay). */
    private boolean insertOp(Connection c, String opId, String kind) throws SQLException {
        // INSERT IGNORE would truncate a longer id and commit an op with no outcome
        if (opId.length() > MAX_OP_ID - 8) throw new IllegalArgumentException("op id longer than " + (MAX_OP_ID - 8) + " characters");
        String verb = dialect == SqlDialect.SQLITE ? "INSERT OR IGNORE" : "INSERT IGNORE";
        try (PreparedStatement ps = c.prepareStatement(verb + " INTO utags_ops (op_id, kind, outcome, copied, at) VALUES (?, ?, '', 0, ?)")) {
            ps.setString(1, opId);
            ps.setString(2, kind);
            ps.setLong(3, clock.getAsLong());
            return ps.executeUpdate() == 1;
        }
    }

    private void finishOp(Connection c, String opId, String outcome, int copied) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE utags_ops SET outcome = ?, copied = ? WHERE op_id = ?")) {
            ps.setString(1, outcome);
            ps.setInt(2, copied);
            ps.setString(3, opId);
            ps.executeUpdate();
        }
    }

    private String storedOutcome(Connection c, String opId, String kind) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT kind, outcome FROM utags_ops WHERE op_id = ?")) {
            ps.setString(1, opId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("op " + opId + " vanished");
                if (!kind.equals(rs.getString(1))) {
                    // a caller bug, not an unknown outcome: retrying with this op id can never succeed
                    throw new IllegalArgumentException("op id " + opId + " was used for a " + rs.getString(1) + ", not a " + kind);
                }
                return rs.getString(2);
            }
        }
    }

    private int storedCopied(Connection c, String opId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT copied FROM utags_ops WHERE op_id = ?")) {
            ps.setString(1, opId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Step 2: ensure the lock row of the player, then lock it (MySQL); SQLite already holds the write lock. */
    private void lockPlayer(Connection c, UUID player) throws SQLException {
        String sql = dialect == SqlDialect.SQLITE
                ? "INSERT INTO utags_players (player_uuid, updated_at) VALUES (?, ?) ON CONFLICT(player_uuid) DO UPDATE SET updated_at = excluded.updated_at"
                : "INSERT INTO utags_players (player_uuid, updated_at) VALUES (?, ?) ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setLong(2, clock.getAsLong());
            ps.executeUpdate();
        }
        if (dialect == SqlDialect.MYSQL) {
            try (PreparedStatement ps = c.prepareStatement("SELECT player_uuid FROM utags_players WHERE player_uuid = ? FOR UPDATE")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                }
            }
        }
    }

    private int countUnrevoked(Connection c, UUID player, String tagId, String source) throws SQLException {
        String sql = "SELECT COUNT(*) FROM utags_grants WHERE player_uuid = ? AND tag_id = ? AND revoked_at IS NULL"
                + (source == null ? "" : " AND source = ?");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, tagId);
            if (source != null) ps.setString(3, source);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void insertGrant(Connection c, UUID player, String tagId, String source, String opId, String copiedFrom) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO utags_grants "
                + "(player_uuid, tag_id, source, op_id, granted_at, copied_from_op) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, tagId);
            ps.setString(3, source);
            ps.setString(4, opId);
            ps.setLong(5, clock.getAsLong());
            ps.setString(6, copiedFrom);
            ps.executeUpdate();
        }
    }

    // --- transactions ---

    /**
     * Runs the work in one transaction. A null result means "roll back and answer {@code onNull}"; an exception rolls
     * back too. A deadlock victim restarts the whole transaction.
     */
    private <T> T inTransaction(Connection c, Work<T> work, T onNull) throws SQLException {
        for (int attempt = 1; ; attempt++) {
            begin(c);
            try {
                T result = work.run();
                end(c, result == null ? "ROLLBACK" : "COMMIT");
                return result == null ? onNull : result;
            } catch (SQLException e) {
                rollbackQuietly(c, e);
                if (attempt < DEADLOCK_RETRIES && "40001".equals(e.getSQLState()) && e.getErrorCode() != LOCK_WAIT_TIMEOUT) continue;
                throw e;
            } catch (RuntimeException e) {
                rollbackQuietly(c, e);
                throw e;
            } finally {
                if (dialect == SqlDialect.MYSQL) {
                    try {
                        c.setAutoCommit(true);
                    } catch (SQLException ignored) {
                        // a broken connection is discarded by the pool
                    }
                }
            }
        }
    }

    private void begin(Connection c) throws SQLException {
        if (dialect == SqlDialect.SQLITE) {
            try (Statement st = c.createStatement()) {
                st.execute("BEGIN IMMEDIATE");
            }
        } else {
            c.setAutoCommit(false);
        }
    }

    private void end(Connection c, String what) throws SQLException {
        if (dialect == SqlDialect.SQLITE) {
            try (Statement st = c.createStatement()) {
                st.execute(what);
            }
        } else if (what.equals("COMMIT")) {
            c.commit();
        } else {
            c.rollback();
        }
    }

    private void rollbackQuietly(Connection c, Exception cause) {
        try {
            end(c, "ROLLBACK");
        } catch (SQLException e) {
            cause.addSuppressed(e);
        }
    }
}
