package com.blockworlds.utags.sync;

import com.blockworlds.utags.SqlDialect;
import com.blockworlds.utags.module.ChangeFeed;
import com.blockworlds.utags.module.ChangeFeed.Kind;
import com.blockworlds.utags.storage.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sync engine tests, shared by the SQLite run and the (gated) MySQL run. Engines are driven step by step. */
abstract class SyncEngineContract {

    /** A new connection to the shared database. */
    abstract Connection open() throws SQLException;

    abstract SqlDialect dialect();

    record Delivery(Kind kind, UUID player) {
    }

    /** One server: its own feed, a recorder of what it was told, and an engine. */
    final class Server {
        final ChangeFeed feed = new ChangeFeed(Logger.getAnonymousLogger());
        final List<Delivery> heard = new CopyOnWriteArrayList<>();
        final SyncEngine engine;

        Server(String id) {
            for (Kind k : Kind.values()) feed.subscribe(k, who -> heard.add(new Delivery(k, who)));
            engine = new SyncEngine(SyncEngineContract.this::open, id, feed, Logger.getAnonymousLogger());
        }
    }

    private final List<Server> servers = new ArrayList<>();

    Server server(String id) {
        Server s = new Server(id);
        servers.add(s);
        return s;
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS utags_changes");
            Migrations.createChangesTable(c, dialect());
        }
    }

    @AfterEach
    void tearDown() {
        servers.forEach(s -> s.engine.close());
        servers.clear();
    }

    private long rows() throws Exception {
        try (Connection c = open(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM utags_changes")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void aChangePublishedByAIsDeliveredToBAndNeverBackToA() throws Exception {
        Server a = server("A");
        Server b = server("B");
        a.engine.init();
        b.engine.init();
        UUID p = UUID.randomUUID();
        a.engine.publish(Kind.SELECTION, p);
        a.engine.flush();
        assertEquals(1, b.engine.pollOnce());
        assertEquals(List.of(new Delivery(Kind.SELECTION, p)), b.heard);
        assertEquals(0, a.engine.pollOnce());
        assertTrue(a.heard.isEmpty());
        assertEquals(0, b.engine.pollOnce()); // delivered once only
        // and the other way round
        b.engine.publish(Kind.TAGS, null);
        b.engine.flush();
        assertEquals(1, a.engine.pollOnce());
        assertEquals(List.of(new Delivery(Kind.TAGS, null)), a.heard);
        assertEquals(0, b.engine.pollOnce());
        assertEquals(1, b.heard.size());
    }

    @Test
    void manyChangesForOnePlayerInOnePollDeliverOnce() throws Exception {
        Server a = server("A");
        Server b = server("B");
        a.engine.init();
        b.engine.init();
        UUID p = UUID.randomUUID();
        UUID q = UUID.randomUUID();
        for (int i = 0; i < 5; i++) a.engine.publish(Kind.COLORS, p);
        a.engine.publish(Kind.SELECTION, p);
        a.engine.publish(Kind.COLORS, q);
        for (int i = 0; i < 3; i++) a.engine.publish(Kind.TAGS, null);
        a.engine.flush();
        assertEquals(10, rows()); // every change is stored
        assertEquals(4, b.engine.pollOnce());
        assertEquals(List.of(new Delivery(Kind.COLORS, p), new Delivery(Kind.SELECTION, p),
                new Delivery(Kind.COLORS, q), new Delivery(Kind.TAGS, null)), b.heard);
    }

    @Test
    void historyBeforeStartIsNotReplayed() throws Exception {
        Server a = server("A");
        a.engine.init();
        a.engine.publish(Kind.TAGS, null);
        a.engine.publish(Kind.SELECTION, UUID.randomUUID());
        a.engine.flush();
        Server b = server("B");
        b.engine.init(); // starts after the two rows exist
        assertEquals(0, b.engine.pollOnce());
        assertTrue(b.heard.isEmpty());
        a.engine.publish(Kind.TAGS, null);
        a.engine.flush();
        assertEquals(1, b.engine.pollOnce());
    }

    @Test
    void aRowThatCommitsLateWithALowerIdIsStillSeen() throws Exception {
        Server b = server("B");
        b.engine.init();
        UUID late = UUID.randomUUID();
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO utags_changes (id, kind, player_uuid, server_id, created_at) VALUES (11, 'TAGS', NULL, 'A', 1)");
        }
        assertEquals(1, b.engine.pollOnce());
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO utags_changes (id, kind, player_uuid, server_id, created_at) VALUES (10, 'SELECTION', '" + late + "', 'A', 1)");
        }
        assertEquals(1, b.engine.pollOnce());
        assertEquals(new Delivery(Kind.SELECTION, late), b.heard.get(1));
        assertEquals(0, b.engine.pollOnce());
    }

    @Test
    void pruneDeletesRowsOlderThan24Hours() throws Exception {
        long now = 10L * 24 * 3600 * 1000;
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO utags_changes (kind, server_id, created_at) VALUES ('TAGS', 'A', " + (now - 25L * 3600 * 1000) + ")");
            st.executeUpdate("INSERT INTO utags_changes (kind, server_id, created_at) VALUES ('TAGS', 'A', " + (now - 23L * 3600 * 1000) + ")");
            st.executeUpdate("INSERT INTO utags_changes (kind, server_id, created_at) VALUES ('TAGS', 'A', " + now + ")");
        }
        assertEquals(1, server("A").engine.prune(now));
        assertEquals(2, rows());
    }

    @Test
    void unknownKindsAreSkipped() throws Exception {
        Server b = server("B");
        b.engine.init();
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO utags_changes (kind, server_id, created_at) VALUES ('FUTURE', 'A', 1)");
        }
        assertEquals(0, b.engine.pollOnce());
    }

    /** The real timers: A publishes, B hears it within about two polls (1 s each), A never does. */
    @Test
    void twoRunningEnginesSyncWithinTwoPolls() throws Exception {
        Server a = server("A");
        Server b = server("B");
        a.engine.start(1);
        b.engine.start(1);
        assertTrue(a.engine.awaitStarted(10_000) && b.engine.awaitStarted(10_000), "engines did not start");
        UUID p = UUID.randomUUID();
        long t0 = System.nanoTime();
        a.feed.changed(Kind.SELECTION, p);
        while (b.heard.isEmpty() && System.nanoTime() - t0 < 6_000_000_000L) Thread.sleep(50);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(List.of(new Delivery(Kind.SELECTION, p)), b.heard);
        assertTrue(ms < 2600, "took " + ms + " ms");
        Thread.sleep(2200);
        assertTrue(a.heard.isEmpty(), "A heard its own change");
        assertEquals(1, b.heard.size());
    }
}
