package com.blockworlds.utags.ledger;

import com.blockworlds.utags.SqlDialect;
import com.blockworlds.utags.api.GrantOutcome;
import com.blockworlds.utags.api.GrantResult;
import com.blockworlds.utags.api.RevokeOutcome;
import com.blockworlds.utags.selection.Selection;
import com.blockworlds.utags.selection.SelectionStore;
import com.blockworlds.utags.storage.Migrations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ledger rules of contracts.md, shared by the SQLite run and the (gated) MySQL run. */
abstract class LedgerStoreContract {

    /** A new connection to the migrated database. */
    abstract Connection open() throws Exception;

    abstract SqlDialect dialect();

    /** Empties the database and migrates it. */
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

    static void migrate(Connection c, SqlDialect dialect) throws Exception {
        Migrations.run(c, dialect, Migrations.core(), 0, Logger.getAnonymousLogger());
    }

    int count(String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void grantNewThenAlreadyOwnedThenUnknown() throws Exception {
        assertEquals(new GrantResult(GrantOutcome.GRANTED, false), store.grant(c, alice, "vip", "shop", "op1", true, false));
        assertEquals(new GrantResult(GrantOutcome.ALREADY_OWNED, false), store.grant(c, alice, "vip", "event", "op2", true, false));
        assertEquals(2, count("SELECT COUNT(*) FROM utags_grants WHERE revoked_at IS NULL"));
        assertEquals(new GrantResult(GrantOutcome.UNKNOWN_TAG, false), store.grant(c, alice, "nope", "shop", "op3", false, false));
        assertEquals(0, count("SELECT COUNT(*) FROM utags_grants WHERE tag_id = 'nope'"));
        assertEquals(0, count("SELECT COUNT(*) FROM utags_ops WHERE op_id = 'op3'"));
        assertEquals(Set.of("vip"), store.owned(c, alice));
    }

    @Test
    void permissionOwnedGrantIsAlreadyOwned() throws Exception {
        assertEquals(GrantOutcome.ALREADY_OWNED, store.grant(c, alice, "vip", "shop", "op1", true, true).outcome());
    }

    @Test
    void replayReturnsTheOriginalOutcomeAndWritesNothing() throws Exception {
        store.grant(c, alice, "vip", "shop", "op1", true, false);
        store.grant(c, alice, "vip", "shop", "op2", true, false); // ALREADY_OWNED
        assertEquals(new GrantResult(GrantOutcome.GRANTED, true), store.grant(c, bob, "other", "x", "op1", true, true));
        assertEquals(new GrantResult(GrantOutcome.ALREADY_OWNED, true), store.grant(c, alice, "vip", "shop", "op2", true, false));
        assertEquals(2, count("SELECT COUNT(*) FROM utags_grants"));
        assertEquals(0, count("SELECT COUNT(*) FROM utags_grants WHERE player_uuid = '" + bob + "'"));
        store.revoke(c, alice, "vip", "shop", "r1", false);
        var again = store.revoke(c, alice, "vip", "shop", "r1", false);
        assertTrue(again.replayed());
        assertEquals(RevokeOutcome.REVOKED_GONE, again.outcome()); // both rows had the same source
        assertThrows(IllegalArgumentException.class, () -> store.grant(c, alice, "vip", "shop", "r1", true, false));
    }

    @Test
    void revokeIsScopedToTheSource() throws Exception {
        store.grant(c, alice, "vip", "shop", "g1", true, false);
        store.grant(c, alice, "vip", "event", "g2", true, false);
        assertEquals(RevokeOutcome.NOT_HELD, store.revoke(c, alice, "vip", "other", "r0", false).outcome());
        assertEquals(RevokeOutcome.REVOKED_STILL_OWNED, store.revoke(c, alice, "vip", "shop", "r1", false).outcome());
        assertEquals(Set.of("vip"), store.owned(c, alice));
        assertEquals(RevokeOutcome.REVOKED_GONE, store.revoke(c, alice, "vip", "event", "r2", false).outcome());
        assertEquals(Set.of(), store.owned(c, alice));
        assertEquals(RevokeOutcome.NOT_HELD, store.revoke(c, alice, "vip", "event", "r3", false).outcome());
        assertEquals(2, count("SELECT COUNT(*) FROM utags_grants WHERE revoked_at IS NOT NULL"));
    }

    @Test
    void revokeWithPermissionLeavesItStillOwned() throws Exception {
        store.grant(c, alice, "vip", "shop", "g1", true, true);
        assertEquals(RevokeOutcome.REVOKED_STILL_OWNED, store.revoke(c, alice, "vip", "shop", "r1", true).outcome());
    }

    @Test
    void goneClearsTheSelectionButStillOwnedKeepsIt() throws Exception {
        store.grant(c, alice, "vip", "shop", "g1", true, false);
        store.grant(c, alice, "vip", "event", "g2", true, false);
        SelectionStore.set(c, alice, true, "vip");
        SelectionStore.set(c, alice, false, "other");
        var still = store.revoke(c, alice, "vip", "shop", "r1", false);
        assertFalse(still.clearedSelection());
        assertEquals(new Selection("vip", "other"), SelectionStore.find(c, alice));
        var gone = store.revoke(c, alice, "vip", "event", "r2", false);
        assertTrue(gone.clearedPrefix());
        assertFalse(gone.clearedSuffix());
        assertEquals(new Selection(null, "other"), SelectionStore.find(c, alice));
    }

    @Test
    void transferCopiesTheUnionAndSkipsSameTagAndSource() throws Exception {
        store.grant(c, alice, "vip", "shop", "a1", true, false);
        store.grant(c, alice, "vip", "event", "a2", true, false);
        store.grant(c, alice, "gold", "shop", "a3", true, false);
        store.grant(c, alice, "gone", "shop", "a4", true, false);
        store.revoke(c, alice, "gone", "shop", "ar", false);
        store.grant(c, bob, "vip", "shop", "b1", true, false);
        var moved = store.transferAll(c, alice, bob, "t1");
        assertEquals(2, moved.copied());
        assertFalse(moved.replayed());
        assertEquals(Set.of("vip", "gold"), store.owned(c, bob));
        assertEquals(Set.of("vip", "gold"), store.owned(c, alice));
        assertEquals(2, count("SELECT COUNT(*) FROM utags_grants WHERE copied_from_op = 't1' AND op_id LIKE 't1#%'"));
        assertEquals(2, count("SELECT COUNT(*) FROM utags_grants WHERE player_uuid = '" + bob + "' AND tag_id = 'vip' AND revoked_at IS NULL"));
        assertEquals(3, count("SELECT COUNT(*) FROM utags_grants WHERE player_uuid = '" + alice + "' AND revoked_at IS NULL"));
    }

    @Test
    void transferKeepsOrAdoptsTheSelection() throws Exception {
        store.grant(c, alice, "vip", "shop", "a1", true, false);
        SelectionStore.set(c, alice, true, "vip");
        var adopt = store.transferAll(c, alice, bob, "t1");
        assertEquals(new Selection("vip", null), adopt.adopted());
        assertEquals(new Selection("vip", null), SelectionStore.find(c, bob));
        SelectionStore.set(c, alice, false, "gold");
        var keep = store.transferAll(c, alice, bob, "t2");
        assertNull(keep.adopted());
        assertEquals(new Selection("vip", null), SelectionStore.find(c, bob));
    }

    @Test
    void transferReplayReturnsTheOriginalCount() throws Exception {
        store.grant(c, alice, "vip", "shop", "a1", true, false);
        store.grant(c, alice, "gold", "shop", "a2", true, false);
        assertEquals(2, store.transferAll(c, alice, bob, "t1").copied());
        store.grant(c, alice, "new", "shop", "a3", true, false);
        var replay = store.transferAll(c, alice, bob, "t1");
        assertTrue(replay.replayed());
        assertEquals(2, replay.copied());
        assertEquals(Set.of("vip", "gold"), store.owned(c, bob));
        assertNotNull(replay.result());
    }
}
