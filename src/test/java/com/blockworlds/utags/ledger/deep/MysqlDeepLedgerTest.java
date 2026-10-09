package com.blockworlds.utags.ledger.deep;

import com.blockworlds.utags.SqlDialect;
import com.blockworlds.utags.api.GrantOutcome;
import com.blockworlds.utags.api.GrantResult;
import com.blockworlds.utags.api.RevokeOutcome;
import com.blockworlds.utags.ledger.LedgerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep ledger tests on MySQL plus the MySQL-only attacks (lock wait timeout, stress, transfer chains, the swallowed
 * deadlock in the selection insert). Skipped unless BW_MYSQL_DB is set; it DROPS the uTags tables of that database.
 */
@EnabledIfEnvironmentVariable(named = "BW_MYSQL_DB", matches = ".+")
class MysqlDeepLedgerTest extends DeepLedgerBase {

    @Override
    Connection open() throws Exception {
        String url = "jdbc:mysql://" + env("BW_MYSQL_HOST", "127.0.0.1") + ":" + env("BW_MYSQL_PORT", "33312") + "/"
                + System.getenv("BW_MYSQL_DB") + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=120000";
        return DriverManager.getConnection(url, System.getenv("BW_MYSQL_USER"), env("BW_MYSQL_PASSWORD", ""));
    }

    @Override
    void reset() throws Exception {
        try (Connection x = open(); Statement st = x.createStatement()) {
            for (String table : new String[]{"utags_grants", "utags_ops", "utags_players", "utags_selection", "utags_schema",
                    "tag_requests", "player_tag_color_preferences", "player_preferences", "tags"}) {
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

    private List<Object> race(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CyclicBarrier gate = new CyclicBarrier(tasks.size());
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> t : tasks) {
                futures.add(pool.submit(() -> {
                    gate.await();
                    try {
                        return t.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            List<Object> out = new ArrayList<>();
            for (Future<Object> f : futures) out.add(f.get(1200, TimeUnit.SECONDS));
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String describe(Exception e) {
        return e instanceof SQLException s ? "SQL[" + s.getSQLState() + "/" + s.getErrorCode() + "]" : e.getClass().getSimpleName();
    }

    @Test
    void collationOfTheLedgerColumns() throws Exception {
        Map<String, String> cols = new TreeMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT CONCAT(TABLE_NAME,'.',COLUMN_NAME), COLLATION_NAME FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('utags_grants','utags_ops') AND COLLATION_NAME IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) cols.put(rs.getString(1), rs.getString(2));
        }
        String version;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT VERSION(), @@sql_mode, @@innodb_lock_wait_timeout, @@innodb_rollback_on_timeout, @@transaction_isolation")) {
            rs.next();
            version = rs.getString(1) + " sql_mode=" + rs.getString(2) + " lockWait=" + rs.getString(3) + " rollbackOnTimeout=" + rs.getString(4) + " iso=" + rs.getString(5);
        }
        evidence("collation", version + " " + cols);
        for (String col : new String[]{"utags_ops.op_id", "utags_grants.op_id", "utags_grants.revoke_op_id", "utags_grants.copied_from_op"}) {
            assertEquals("utf8mb4_bin", cols.get(col), col);
        }
    }

    /** A ledger made by an earlier 1.4.0 build (op ids in the default, case-blind collation) is fixed by migration 9. */
    @Test
    void migrationNineMakesAnOldLedgerCaseSensitive() throws Exception {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE utags_ops MODIFY op_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL");
            st.executeUpdate("ALTER TABLE utags_grants MODIFY op_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL");
            st.executeUpdate("UPDATE utags_schema SET version = 8 WHERE id = 1");
        }
        store.grant(c, alice, "vip", "shop", "Order-A1", true, false);
        assertEquals(9, com.blockworlds.utags.storage.Migrations.run(c, SqlDialect.MYSQL,
                com.blockworlds.utags.storage.Migrations.core(), 0, java.util.logging.Logger.getAnonymousLogger()));
        GrantResult second = store.grant(c, bob, "vip", "shop", "order-a1", true, false);
        evidence("migration9", "second=" + second + " ops=" + count("SELECT COUNT(*) FROM utags_ops"));
        assertEquals(new GrantResult(GrantOutcome.GRANTED, false), second);
        assertEquals(2, count("SELECT COUNT(*) FROM utags_ops"));
    }

    // --- attack 5b: lock wait timeout (1205) ---

    @Test
    void lockWaitTimeoutIsNotAmbiguousAndTheRetryGrantsOnce() throws Exception {
        store.grant(c, alice, "gold", "shop", "seed", true, false);
        try (Connection holder = open(); Connection b = open()) {
            holder.setAutoCommit(false);
            try (Statement st = holder.createStatement()) {
                st.executeQuery("SELECT player_uuid FROM utags_players WHERE player_uuid = '" + alice + "' FOR UPDATE").close();
            }
            try (Statement st = b.createStatement()) {
                st.execute("SET SESSION innodb_lock_wait_timeout = 1");
            }
            Faults f = new Faults();
            long t0 = System.nanoTime();
            String err = "none";
            try {
                store.grant(f.wrap(b), alice, "vip", "shop", "g-lw", true, false);
            } catch (SQLException e) {
                err = describe(e);
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            int opsWhileHeld = opsBinary("g-lw");
            holder.rollback();
            GrantResult retry = store.grant(b, alice, "vip", "shop", "g-lw", true, false);
            int vipRows = count("SELECT COUNT(*) FROM utags_grants WHERE tag_id = 'vip'");
            evidence("lockWaitTimeout", "error=" + err + " attempts=" + f.begins.get() + " elapsedMs=" + ms + " opsBeforeRetry=" + opsWhileHeld
                    + " retry=" + retry + " vipRows=" + vipRows);
            assertEquals(0, opsWhileHeld);
            assertEquals(new GrantResult(GrantOutcome.GRANTED, false), retry);
            assertEquals(1, vipRows);
            assertEquals(1, f.begins.get(), "a lock wait timeout was retried " + f.begins.get() + " times (each waits the full innodb_lock_wait_timeout, 50 s by default)");
        }
    }

    // --- attack 6: transferAll chains ---

    @Test
    void threeWayTransferChainsNeitherDeadlockNorMiscount() throws Exception {
        int errors = 0;
        int mismatches = 0;
        Map<String, Integer> errorKinds = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID d = UUID.randomUUID();
            store.grant(c, a, "vip", "shop", "ca-" + i, true, false);
            store.grant(c, b, "gold", "shop", "cb-" + i, true, false);
            store.grant(c, d, "mvp", "event", "cd-" + i, true, false);
            int n = i;
            List<Object> out = race(List.of(
                    () -> transfer(a, b, "ab-" + n), () -> transfer(b, d, "bd-" + n), () -> transfer(d, a, "da-" + n),
                    () -> grantOn(a, "gold", "event", "late-" + n), () -> revokeOn(b, "gold", "shop", "rv-" + n)));
            for (Object o : out) {
                if (o instanceof Exception e) {
                    errors++;
                    errorKinds.merge(describe(e), 1, Integer::sum);
                }
            }
            for (String op : new String[]{"ab-" + n, "bd-" + n, "da-" + n}) {
                int stored = count("SELECT COALESCE(MAX(copied), -1) FROM utags_ops WHERE op_id = ?", op);
                int rows = count("SELECT COUNT(*) FROM utags_grants WHERE copied_from_op = ?", op);
                if (stored != rows) mismatches++;
            }
            if (!store.owned(c, a).contains("vip") || !store.owned(c, d).contains("mvp")) mismatches++;
        }
        evidence("transferChains", "iterations=30 escapedErrors=" + errors + " kinds=" + errorKinds + " copiedMismatches=" + mismatches);
        assertEquals(0, errors);
        assertEquals(0, mismatches);
    }

    private Object transfer(UUID from, UUID into, String op) throws Exception {
        try (Connection x = open()) {
            return store.transferAll(x, from, into, op);
        }
    }

    private Object grantOn(UUID p, String tag, String source, String op) throws Exception {
        try (Connection x = open()) {
            return store.grant(x, p, tag, source, op, true, false);
        }
    }

    private Object revokeOn(UUID p, String tag, String source, String op) throws Exception {
        try (Connection x = open()) {
            return store.revoke(x, p, tag, source, op, false);
        }
    }

    // --- attack 6 + 5: mixed stress on adjacent players, then count both sides ---

    private record Def(int kind, UUID p, UUID q, String tag, String source) {
    }

    private Object run(Def d, String op, Connection x) throws SQLException {
        return switch (d.kind) {
            case 0 -> store.grant(x, d.p, d.tag, d.source, op, true, false);
            case 1 -> store.revoke(x, d.p, d.tag, d.source, op, false);
            default -> store.transferAll(x, d.p, d.q, op);
        };
    }

    private static String outcomeOf(Object r) {
        if (r instanceof GrantResult g) return g.outcome().name();
        if (r instanceof LedgerStore.RevokeOutcomeDetail v) return v.outcome().name();
        return "COPIED";
    }

    private static boolean replayedOf(Object r) {
        if (r instanceof GrantResult g) return g.replayed();
        if (r instanceof LedgerStore.RevokeOutcomeDetail v) return v.replayed();
        return ((LedgerStore.TransferDetail) r).replayed();
    }

    @Test
    void mixedStressOnAdjacentPlayersMatchesTheStoredOutcomes() throws Exception {
        UUID[] players = new UUID[5];
        for (int i = 0; i < players.length; i++) players[i] = UUID.fromString("00000000-0000-0000-0000-00000000010" + i);
        String[] tags = {"vip", "gold", "mvp"};
        String[] sources = {"shop", "event"};
        int threads = 6;
        int perThread = 60;
        Map<String, Def> defs = new ConcurrentHashMap<>();
        Map<String, Object> observed = new ConcurrentHashMap<>();
        Map<String, String> firstErrors = new ConcurrentHashMap<>();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int tt = t;
            tasks.add(() -> {
                Random r = new Random(1000 + tt);
                try (Connection x = open()) {
                    for (int i = 0; i < perThread; i++) {
                        String op = "s" + tt + "-" + i;
                        int k = r.nextInt(10);
                        UUID p = players[r.nextInt(players.length)];
                        UUID q;
                        do {
                            q = players[r.nextInt(players.length)];
                        } while (q.equals(p));
                        Def d = new Def(k < 5 ? 0 : k < 8 ? 1 : 2, p, q, tags[r.nextInt(tags.length)], sources[r.nextInt(sources.length)]);
                        defs.put(op, d);
                        try {
                            observed.put(op, run(d, op, x));
                        } catch (Exception e) {
                            firstErrors.put(op, describe(e));
                        }
                    }
                }
                return null;
            });
        }
        long t0 = System.nanoTime();
        race(tasks);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        // the caller's duty after an error: retry with the same op id
        int retryErrors = 0;
        int retriedAsReplay = 0;
        for (String op : firstErrors.keySet()) {
            try {
                Object r = run(defs.get(op), op, c);
                observed.put(op, r);
                if (replayedOf(r)) retriedAsReplay++;
            } catch (Exception e) {
                retryErrors++;
            }
        }
        Map<String, String[]> stored = new HashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT op_id, kind, outcome, copied FROM utags_ops")) {
            while (rs.next()) stored.put(rs.getString(1), new String[]{rs.getString(2), rs.getString(3), rs.getString(4)});
        }
        Map<String, Integer> grantRows = groupCount("op_id");
        Map<String, Integer> revokeRows = groupCount("revoke_op_id");
        Map<String, Integer> copyRows = groupCount("copied_from_op");
        int outcomeMismatch = 0;
        int rowMismatch = 0;
        int firstCallReplayed = 0;
        Map<String, Integer> outcomes = new TreeMap<>();
        for (Map.Entry<String, Object> e : observed.entrySet()) {
            String op = e.getKey();
            Object r = e.getValue();
            String[] s = stored.get(op);
            outcomes.merge(outcomeOf(r), 1, Integer::sum);
            if (!firstErrors.containsKey(op) && replayedOf(r)) firstCallReplayed++;
            if (s == null || !s[1].equals(outcomeOf(r))) {
                outcomeMismatch++;
                continue;
            }
            switch (defs.get(op).kind) {
                case 0 -> { if (grantRows.getOrDefault(op, 0) != 1) rowMismatch++; }
                case 1 -> { if ((revokeRows.getOrDefault(op, 0) > 0) == s[1].equals(RevokeOutcome.NOT_HELD.name())) rowMismatch++; }
                default -> {
                    int copied = ((LedgerStore.TransferDetail) r).copied();
                    if (copied != Integer.parseInt(s[2]) || copied != copyRows.getOrDefault(op, 0)) rowMismatch++;
                }
            }
        }
        int totalRows = count("SELECT COUNT(*) FROM utags_grants");
        int grantOps = (int) defs.values().stream().filter(d -> d.kind == 0).count();
        int copiedSum = copyRows.values().stream().mapToInt(Integer::intValue).sum();
        // replay everything: same outcomes, nothing written
        int replayMismatch = 0;
        for (Map.Entry<String, Def> e : defs.entrySet()) {
            Object r = run(e.getValue(), e.getKey(), c);
            Object o = observed.get(e.getKey());
            if (!replayedOf(r) || o == null || !outcomeOf(r).equals(outcomeOf(o))) replayMismatch++;
        }
        int totalAfterReplay = count("SELECT COUNT(*) FROM utags_grants");
        evidence("stress", "ops=" + defs.size() + " ms=" + ms + " firstErrors=" + firstErrors.size() + " kinds=" + kinds(firstErrors)
                + " retryErrors=" + retryErrors + " retriedAsReplay=" + retriedAsReplay + " outcomes=" + outcomes
                + " outcomeMismatch=" + outcomeMismatch + " rowMismatch=" + rowMismatch + " firstCallReplayed=" + firstCallReplayed
                + " grantRows=" + totalRows + " expected=" + (grantOps + copiedSum) + " (grants " + grantOps + " + copies " + copiedSum + ")"
                + " replayMismatch=" + replayMismatch + " rowsAfterReplay=" + totalAfterReplay);
        assertEquals(0, retryErrors);
        assertEquals(0, outcomeMismatch);
        assertEquals(0, rowMismatch);
        assertEquals(0, firstCallReplayed);
        assertEquals(grantOps + copiedSum, totalRows);
        assertEquals(0, replayMismatch);
        assertEquals(totalRows, totalAfterReplay);
    }

    private static Map<String, Integer> kinds(Map<String, String> errors) {
        Map<String, Integer> k = new TreeMap<>();
        for (String v : errors.values()) k.merge(v, 1, Integer::sum);
        return k;
    }

    private Map<String, Integer> groupCount(String column) throws SQLException {
        Map<String, Integer> m = new HashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT " + column + ", COUNT(*) FROM utags_grants WHERE " + column + " IS NOT NULL GROUP BY " + column)) {
            while (rs.next()) m.put(rs.getString(1), rs.getInt(2));
        }
        return m;
    }

    // --- latent: SelectionStore.insertIfAbsent swallows a deadlock that rolled back the whole ledger transaction ---

    @Test
    void aDeadlockInTheAdoptedSelectionInsertIsNotReportedAsSuccess() throws Exception {
        store.grant(c, alice, "vip", "shop", "g1", true, false);
        store.grant(c, alice, "gold", "shop", "g2", true, false);
        com.blockworlds.utags.selection.SelectionStore.set(c, alice, true, "vip");
        Faults f = new Faults();
        // the transaction is the deadlock victim on its selection insert while the select path inserts bob's row
        f.beforeSelectionInsertFails = () -> {
            try (Connection other = open(); Statement st = other.createStatement()) {
                st.executeUpdate("INSERT INTO utags_selection (player_uuid, prefix_tag, suffix_tag, updated_at) VALUES ('" + bob + "', NULL, NULL, 0)");
            }
        };
        Object result;
        try {
            result = store.transferAll(f.wrap(c), alice, bob, "t-sel");
        } catch (Exception e) {
            result = describe(e);
        }
        int bobRows = store.rows(c, bob).size();
        int ops = opsBinary("t-sel");
        evidence("selectionInsertDeadlock", "result=" + result + " bobRowsCommitted=" + bobRows + " opRowCommitted=" + ops
                + " bobSelection=" + com.blockworlds.utags.selection.SelectionStore.find(c, bob));
        if (result instanceof LedgerStore.TransferDetail d && !d.replayed()) {
            assertEquals(d.copied(), bobRows, "transferAll reported " + d.copied() + " copies but committed " + bobRows);
            assertEquals(1, ops, "transferAll reported success but its op row was rolled back");
        }
    }

    // --- attack 4: INSERT IGNORE turns a too-long op id into a truncation warning (store level; ApiValidation caps 100) ---

    @Test
    void anOverlongOpIdIsRefusedNotTruncated() throws Exception {
        String base = "x".repeat(128);
        Object first;
        Object second;
        try {
            first = store.revoke(c, alice, "vip", "shop", base + "A", false);
        } catch (Exception e) {
            first = describe(e);
        }
        try {
            second = store.revoke(c, alice, "vip", "shop", base + "B", false);
        } catch (Exception e) {
            second = describe(e);
        }
        String storedOutcome = "none";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT outcome FROM utags_ops")) {
            if (rs.next()) storedOutcome = "'" + rs.getString(1) + "'";
        }
        evidence("overlongOpId", "first=" + first + " second=" + second + " opsRows=" + count("SELECT COUNT(*) FROM utags_ops")
                + " storedOutcome=" + storedOutcome);
        assertEquals(0, count("SELECT COUNT(*) FROM utags_ops"), "a truncated op row was committed");
    }
}
