package com.blockworlds.utags.ledger.deep;

import com.blockworlds.utags.SqlDialect;
import com.blockworlds.utags.api.GrantOutcome;
import com.blockworlds.utags.api.GrantResult;
import com.blockworlds.utags.api.RevokeOutcome;
import com.blockworlds.utags.ledger.LedgerStore;
import com.blockworlds.utags.selection.Selection;
import com.blockworlds.utags.selection.SelectionStore;
import com.blockworlds.utags.storage.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adversarial ledger tests that run on both dialects (deep verification of uTags 1.4.0). Each test prints one
 * "EVIDENCE|" line with its counts, so the report can quote numbers from both sides.
 */
abstract class DeepLedgerBase {

    abstract Connection open() throws Exception;

    abstract SqlDialect dialect();

    abstract void reset() throws Exception;

    LedgerStore store;
    Connection c;
    final UUID alice = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    final UUID bob = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @BeforeEach
    void setUp() throws Exception {
        reset();
        store = new LedgerStore(dialect());
        c = open();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (c != null) c.close();
    }

    static void migrate(Connection c, SqlDialect dialect) throws Exception {
        Migrations.run(c, dialect, Migrations.core(), 0, Logger.getAnonymousLogger());
    }

    void evidence(String test, String text) {
        System.out.println("EVIDENCE|" + dialect() + "|" + test + "|" + text);
    }

    int count(String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    int opsBinary(String op) throws SQLException {
        String sql = dialect() == SqlDialect.MYSQL
                ? "SELECT COUNT(*) FROM utags_ops WHERE BINARY op_id = ?"
                : "SELECT COUNT(*) FROM utags_ops WHERE op_id = ?";
        return count(sql, op);
    }

    // --- attack 2b: op ids that differ only in letter case are different op ids (ApiValidation allows A-Z and a-z) ---

    @Test
    void caseDistinctGrantOpIdsAreDistinctOps() throws Exception {
        GrantResult a = store.grant(c, alice, "vip", "tebex:x", "Order-A1", true, false);
        GrantResult b = store.grant(c, bob, "gold", "tebex:x", "order-a1", true, false);
        int bobRows = store.rows(c, bob).size();
        int ops = count("SELECT COUNT(*) FROM utags_ops");
        evidence("caseDistinctGrantOpIds", "first=" + a + " second=" + b + " bobRows=" + bobRows + " opsRows=" + ops
                + " bobOwned=" + store.owned(c, bob));
        assertFalse(b.replayed(), "a different op id (case) was treated as a replay of another player's grant");
        assertEquals(1, bobRows, "bob's grant row");
        assertEquals(2, ops, "op rows");
    }

    @Test
    void caseDistinctRevokeOpIdIsNotAReplayOfAGrant() throws Exception {
        store.grant(c, alice, "vip", "shop", "Op-7", true, false);
        Object second;
        try {
            second = store.revoke(c, alice, "vip", "shop", "op-7", false);
        } catch (Exception e) {
            second = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        int unrevoked = count("SELECT COUNT(*) FROM utags_grants WHERE revoked_at IS NULL");
        evidence("caseDistinctRevokeOpId", "revoke(op-7) after grant(Op-7) -> " + second + " unrevokedRows=" + unrevoked);
        assertEquals(0, unrevoked, "revoke op-7 must run (it is a new op id), leaving no unrevoked row");
    }

    // --- attack 1: ambiguous commit (the commit happens, the reply is lost) ---

    @Test
    void ambiguousGrantCommitThenRetryReplaysOnce() throws Exception {
        Faults f = new Faults();
        f.commitThrowsAfter = true;
        Connection w = f.wrap(c);
        assertThrows(SQLException.class, () -> store.grant(w, alice, "vip", "shop", "g-amb", true, false));
        int rowsAfterFailure = store.rows(c, alice).size();
        GrantResult retry = store.grant(c, alice, "vip", "shop", "g-amb", true, false);
        int rows = store.rows(c, alice).size();
        evidence("ambiguousGrant", "rowsAfterFailure=" + rowsAfterFailure + " retry=" + retry + " rows=" + rows + " ops=" + opsBinary("g-amb"));
        assertEquals(new GrantResult(GrantOutcome.GRANTED, true), retry);
        assertEquals(1, rows);
        assertEquals(1, opsBinary("g-amb"));
    }

    @Test
    void ambiguousRevokeCommitThenRetryReplaysOnce() throws Exception {
        store.grant(c, alice, "vip", "shop", "g1", true, false);
        SelectionStore.set(c, alice, true, "vip");
        Faults f = new Faults();
        f.commitThrowsAfter = true;
        Connection w = f.wrap(c);
        assertThrows(SQLException.class, () -> store.revoke(w, alice, "vip", "shop", "r-amb", false));
        Selection afterFailure = SelectionStore.find(c, alice);
        LedgerStore.RevokeOutcomeDetail retry = store.revoke(c, alice, "vip", "shop", "r-amb", false);
        int revoked = count("SELECT COUNT(*) FROM utags_grants WHERE revoke_op_id = ?", "r-amb");
        evidence("ambiguousRevoke", "selectionAfterFailure=" + afterFailure + " retry=" + retry + " revokedRows=" + revoked);
        assertEquals(RevokeOutcome.REVOKED_GONE, retry.outcome());
        assertTrue(retry.replayed());
        assertEquals(1, revoked);
    }

    /**
     * The commit cleared the stored selection, the reply was lost, and the retry is a replay. LedgerService only clears
     * the LuckPerms output, reloads the ownership cache and publishes the change when the result is NOT a replay, so
     * after this sequence nothing ever applies the committed revoke outside the database. This asserts what the
     * caller needs to finish the job: the replay must still say the selection was cleared.
     */
    @Test
    void replayOfAnAmbiguousRevokeStillReportsTheClearedSelection() throws Exception {
        store.grant(c, alice, "vip", "shop", "g1", true, false);
        SelectionStore.set(c, alice, true, "vip");
        Faults f = new Faults();
        f.commitThrowsAfter = true;
        Connection w = f.wrap(c);
        assertThrows(SQLException.class, () -> store.revoke(w, alice, "vip", "shop", "r-amb2", false));
        LedgerStore.RevokeOutcomeDetail retry = store.revoke(c, alice, "vip", "shop", "r-amb2", false);
        evidence("ambiguousRevokeSideEffects", "stored selection=" + SelectionStore.find(c, alice) + " replay=" + retry);
        // Fixed in LedgerService (M1): a REVOKED_GONE replay re-applies the stored selection, which the first call cleared.
        assertTrue(retry.replayed());
        assertEquals(RevokeOutcome.REVOKED_GONE, retry.outcome());
        assertNull(SelectionStore.find(c, alice).prefixTag(), "the committed first call cleared the stored selection");
    }

    @Test
    void ambiguousTransferCommitThenRetryDoesNotCopyTwice() throws Exception {
        store.grant(c, alice, "vip", "shop", "g1", true, false);
        store.grant(c, alice, "gold", "shop", "g2", true, false);
        store.grant(c, alice, "mvp", "event", "g3", true, false);
        Faults f = new Faults();
        f.commitThrowsAfter = true;
        Connection w = f.wrap(c);
        assertThrows(SQLException.class, () -> store.transferAll(w, alice, bob, "t-amb"));
        LedgerStore.TransferDetail retry = store.transferAll(c, alice, bob, "t-amb");
        int bobRows = store.rows(c, bob).size();
        evidence("ambiguousTransfer", "retry=" + retry + " bobRows=" + bobRows + " aliceRows=" + store.rows(c, alice).size());
        assertEquals(3, retry.copied());
        assertTrue(retry.replayed());
        assertEquals(3, bobRows);
        assertEquals(3, store.rows(c, alice).size());
    }

    // --- attack 5: a deadlock victim is retried; the retry must not double-insert ---

    @Test
    void deadlockOnTheFirstTwoAttemptsGrantsOnce() throws Exception {
        Faults f = new Faults();
        f.failGrantInserts.set(2);
        GrantResult r = store.grant(f.wrap(c), alice, "vip", "shop", "g-dl", true, false);
        int rows = store.rows(c, alice).size();
        evidence("deadlockRetryGrant", "result=" + r + " attempts=" + f.begins.get() + " rows=" + rows + " ops=" + opsBinary("g-dl"));
        assertEquals(new GrantResult(GrantOutcome.GRANTED, false), r);
        assertEquals(3, f.begins.get());
        assertEquals(1, rows);
        assertEquals(1, opsBinary("g-dl"));
    }

    @Test
    void deadlockOnEveryAttemptLeavesNothingAndTheCallerRetrySucceedsOnce() throws Exception {
        Faults f = new Faults();
        f.failGrantInserts.set(10);
        SQLException e = assertThrows(SQLException.class, () -> store.grant(f.wrap(c), alice, "vip", "shop", "g-dl4", true, false));
        int rowsAfter = store.rows(c, alice).size();
        int opsAfter = opsBinary("g-dl4");
        GrantResult retry = store.grant(c, alice, "vip", "shop", "g-dl4", true, false);
        evidence("deadlockExhausted", "state=" + e.getSQLState() + " attempts=" + f.begins.get() + " rowsAfter=" + rowsAfter
                + " opsAfter=" + opsAfter + " retry=" + retry + " rows=" + store.rows(c, alice).size());
        assertEquals(0, rowsAfter);
        assertEquals(0, opsAfter);
        assertEquals(new GrantResult(GrantOutcome.GRANTED, false), retry);
        assertEquals(1, store.rows(c, alice).size());
    }

    @Test
    void deadlockOnTheSecondCopyRetriesTheWholeTransfer() throws Exception {
        store.grant(c, alice, "vip", "shop", "g1", true, false);
        store.grant(c, alice, "gold", "shop", "g2", true, false);
        store.grant(c, alice, "mvp", "event", "g3", true, false);
        Faults f = new Faults();
        f.failGrantInsertNumber = 2;
        LedgerStore.TransferDetail r = store.transferAll(f.wrap(c), alice, bob, "t-dl");
        int bobRows = store.rows(c, bob).size();
        int copyIds = count("SELECT COUNT(DISTINCT op_id) FROM utags_grants WHERE copied_from_op = ?", "t-dl");
        evidence("deadlockRetryTransfer", "result=" + r + " attempts=" + f.begins.get() + " bobRows=" + bobRows + " copyIds=" + copyIds);
        assertEquals(3, r.copied());
        assertEquals(3, bobRows);
        assertEquals(3, copyIds);
    }

    // --- attack 2: op id reuse across kinds ---

    @Test
    void opIdReusedForAnotherKindWritesNothing() throws Exception {
        store.grant(c, alice, "vip", "shop", "k1", true, false);
        Exception revokeErr = assertThrows(Exception.class, () -> store.revoke(c, alice, "vip", "shop", "k1", false));
        Exception transferErr = assertThrows(Exception.class, () -> store.transferAll(c, alice, bob, "k1"));
        int unrevoked = count("SELECT COUNT(*) FROM utags_grants WHERE revoked_at IS NULL");
        int bobRows = store.rows(c, bob).size();
        evidence("opReuseAcrossKinds", "revoke->" + revokeErr.getClass().getSimpleName() + " transfer->"
                + (transferErr == null ? "none" : transferErr.getClass().getSimpleName()) + " unrevoked=" + unrevoked + " bobRows=" + bobRows);
        assertEquals(1, unrevoked);
        assertEquals(0, bobRows);
    }

    // --- attack 7: the selection can be re-set to a tag the revoke just took away (no lock on the select path) ---

    @Test
    void aSelectWhoseOwnershipCheckRanBeforeARevokeIsNotRefused() throws Exception {
        store.grant(c, alice, "vip", "shop", "g1", true, false);
        boolean ownedWhenChecked = store.owned(c, alice).contains("vip"); // SelectionService.select's owns() check
        LedgerStore.RevokeOutcomeDetail r = store.revoke(c, alice, "vip", "shop", "r1", false);
        boolean written = store.select(c, alice, true, "vip", false); // SelectionService.select's write (L1 fix: locked)
        Selection now = SelectionStore.find(c, alice);
        assertFalse(written, "a select after the revoke must be refused");
        evidence("selectRevokeRace", "ownedWhenChecked=" + ownedWhenChecked + " revoke=" + r.outcome() + " ownedNow="
                + store.owned(c, alice) + " selection=" + now);
        assertNotEquals("vip", now == null ? null : now.prefixTag(), "selection points at a tag the player no longer owns");
    }
}
