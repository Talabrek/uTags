package com.blockworlds.utags.ledger;

import com.blockworlds.utags.SqlDialect;
import com.blockworlds.utags.api.GrantOutcome;
import com.blockworlds.utags.api.GrantResult;
import com.blockworlds.utags.api.RevokeOutcome;
import com.blockworlds.utags.selection.Selection;
import com.blockworlds.utags.selection.SelectionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger rules on MySQL plus the required concurrency tests (two connections, 50 iterations each). Skipped
 * unless BW_MYSQL_DB is set; it DROPS the uTags tables of that database, so point it only at a database that is yours.
 */
@EnabledIfEnvironmentVariable(named = "BW_MYSQL_DB", matches = ".+")
class MysqlLedgerStoreTest extends LedgerStoreContract {

    private static final int ITERATIONS = 50;

    @Override
    Connection open() throws Exception {
        String url = "jdbc:mysql://" + env("BW_MYSQL_HOST", "127.0.0.1") + ":" + env("BW_MYSQL_PORT", "3306") + "/"
                + System.getenv("BW_MYSQL_DB") + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=60000";
        return DriverManager.getConnection(url, System.getenv("BW_MYSQL_USER"), env("BW_MYSQL_PASSWORD", env("BW_MYSQL_PASS", "")));
    }

    @Override
    void reset() throws Exception {
        try (Connection x = open(); Statement st = x.createStatement()) {
            for (String table : new String[]{"utags_grants", "utags_ops", "utags_players", "utags_selection", "utags_schema", "tag_requests", "player_tag_color_preferences", "player_preferences", "tags"}) {
                st.executeUpdate("DROP TABLE IF EXISTS " + table);
            }
            migrate(x, SqlDialect.MYSQL);
        }
    }

    @Override
    SqlDialect dialect() {
        return SqlDialect.MYSQL;
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isEmpty() ? fallback : v;
    }

    /** Runs both tasks at the same moment. */
    private List<Object> race(Callable<Object> a, Callable<Object> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier gate = new CyclicBarrier(2);
            Future<Object> fa = pool.submit(() -> { gate.await(); return a.call(); });
            Future<Object> fb = pool.submit(() -> { gate.await(); return b.call(); });
            return List.of(fa.get(), fb.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private Object revoke(UUID p, String tag, String source, String op) throws Exception {
        try (Connection x = open()) {
            return store.revoke(x, p, tag, source, op, false);
        }
    }

    private Object grant(UUID p, String tag, String source, String op) throws Exception {
        try (Connection x = open()) {
            return store.grant(x, p, tag, source, op, true, false);
        }
    }

    private Object transfer(UUID from, UUID into, String op) throws Exception {
        try (Connection x = open()) {
            return store.transferAll(x, from, into, op);
        }
    }

    private static long revokes(List<Object> out, RevokeOutcome outcome) {
        return out.stream().filter(o -> ((LedgerStore.RevokeOutcomeDetail) o).outcome() == outcome).count();
    }

    @Test
    void parallelRevokesFromDifferentSourcesGiveExactlyOneGone() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            UUID p = UUID.randomUUID();
            store.grant(c, p, "vip", "shop", "g1-" + i, true, false);
            store.grant(c, p, "vip", "event", "g2-" + i, true, false);
            SelectionStore.set(c, p, true, "vip");
            int n = i;
            List<Object> out = race(() -> revoke(p, "vip", "shop", "ra-" + n), () -> revoke(p, "vip", "event", "rb-" + n));
            assertEquals(1, revokes(out, RevokeOutcome.REVOKED_GONE), "iteration " + i);
            assertEquals(1, revokes(out, RevokeOutcome.REVOKED_STILL_OWNED), "iteration " + i);
            assertEquals(new Selection(null, null), SelectionStore.find(c, p), "iteration " + i);
            assertEquals(Set.of(), store.owned(c, p));
        }
    }

    @Test
    void parallelGrantsOfANewTagGiveExactlyOneGranted() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            UUID p = UUID.randomUUID();
            int n = i;
            List<Object> out = race(() -> grant(p, "vip", "shop", "ga-" + n), () -> grant(p, "vip", "event", "gb-" + n));
            long granted = out.stream().filter(o -> ((GrantResult) o).outcome() == GrantOutcome.GRANTED).count();
            assertEquals(1, granted, "iteration " + i);
            assertEquals(2, store.rows(c, p).size(), "iteration " + i);
        }
    }

    @Test
    void transferRacingAGrantLosesNothing() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            UUID from = UUID.randomUUID();
            UUID into = UUID.randomUUID();
            store.grant(c, from, "vip", "shop", "base-" + i, true, false);
            int n = i;
            race(() -> transfer(from, into, "t-" + n), () -> grant(from, "gold", "shop", "late-" + n));
            assertEquals(Set.of("vip", "gold"), store.owned(c, from), "iteration " + i);
            assertTrue(store.owned(c, into).contains("vip"), "iteration " + i);
            assertEquals(1, store.rows(c, from).stream().filter(r -> r.tagId().equals("gold")).count(), "iteration " + i);
            // a second transfer picks up whatever the first one raced past: nothing is lost either way
            transfer(from, into, "t2-" + n);
            assertEquals(Set.of("vip", "gold"), store.owned(c, into), "iteration " + i);
        }
    }

    @Test
    void transfersInOppositeDirectionsDoNotDeadlockOrLoseRows() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            store.grant(c, a, "vip", "shop", "ga-" + i, true, false);
            store.grant(c, b, "gold", "shop", "gb-" + i, true, false);
            int n = i;
            race(() -> transfer(a, b, "tab-" + n), () -> transfer(b, a, "tba-" + n));
            assertTrue(store.owned(c, a).contains("vip"), "iteration " + i);
            assertTrue(store.owned(c, b).contains("gold"), "iteration " + i);
            assertTrue(store.owned(c, a).size() >= 1 && store.owned(c, b).size() >= 1, "iteration " + i);
        }
    }

    @Test
    void replaysUnderConcurrencyReturnTheOriginalOutcome() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            UUID p = UUID.randomUUID();
            int n = i;
            List<Object> out = race(() -> grant(p, "vip", "shop", "same-" + n), () -> grant(p, "vip", "shop", "same-" + n));
            GrantResult r0 = (GrantResult) out.get(0);
            GrantResult r1 = (GrantResult) out.get(1);
            assertEquals(GrantOutcome.GRANTED, r0.outcome(), "iteration " + i);
            assertEquals(GrantOutcome.GRANTED, r1.outcome(), "iteration " + i);
            assertEquals(1, out.stream().filter(o -> ((GrantResult) o).replayed()).count(), "iteration " + i);
            assertEquals(1, store.rows(c, p).size(), "iteration " + i);
        }
    }
}
