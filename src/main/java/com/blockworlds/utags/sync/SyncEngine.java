package com.blockworlds.utags.sync;

import com.blockworlds.utags.module.ChangeFeed;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Network sync over the shared {@code utags_changes} table; plain JDBC, no Bukkit, so two engines can be tested
 * against one database. Local changes are queued by {@link #publish} (never blocks) and inserted in a batch on the
 * engine's own thread. A poller reads rows newer than the last seen id that another server wrote, coalesces them
 * (one delivery per kind and player) and hands them to {@link ChangeFeed#deliverRemote}.
 * <p>
 * Auto-increment ids can commit out of order (id 10 commits after id 11 was already read), so each poll re-reads a
 * window of the newest {@link #WINDOW} ids and skips the ones it has already seen.
 */
public final class SyncEngine implements AutoCloseable {

    @FunctionalInterface
    public interface Connections {
        Connection open() throws SQLException;
    }

    static final int WINDOW = 200;
    static final long KEEP_MILLIS = 24L * 60 * 60 * 1000;

    private record Pending(ChangeFeed.Kind kind, UUID player, long at) {
    }

    private record Key(ChangeFeed.Kind kind, UUID player) {
    }

    private final Connections connections;
    private final String serverId;
    private final ChangeFeed feed;
    private final Logger log;
    private final Queue<Pending> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushQueued = new AtomicBoolean();
    private final TreeSet<Long> seen = new TreeSet<>();
    private final ScheduledExecutorService thread = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "uTags-sync");
        t.setDaemon(true);
        return t;
    });
    private final java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
    private long lastSeen;
    private boolean pollFailing;

    public SyncEngine(Connections connections, String serverId, ChangeFeed feed, Logger log) {
        this.connections = connections;
        this.serverId = serverId;
        this.feed = feed;
        this.log = log;
    }

    /** Starts: reads the current max id (history is not replayed), then publishes and polls. */
    public void start(int pollSeconds) {
        thread.execute(() -> {
            try {
                init();
            } catch (SQLException e) {
                log.warning("Sync could not start (is the utags_changes table there?): " + e.getMessage());
                return;
            }
            feed.setPublisher(this::publish);
            started.countDown();
            thread.scheduleWithFixedDelay(this::safePoll, pollSeconds, pollSeconds, TimeUnit.SECONDS);
            thread.scheduleWithFixedDelay(this::safePrune, 0, 60, TimeUnit.MINUTES);
        });
    }

    /** Queues a local change for the database; returns at once. */
    public void publish(ChangeFeed.Kind kind, UUID player) {
        queue.add(new Pending(kind, player, System.currentTimeMillis()));
        if (flushQueued.compareAndSet(false, true)) {
            try {
                thread.execute(this::safeFlush);
            } catch (RuntimeException closed) {
                flushQueued.set(false);
            }
        }
    }

    /** Waits until start() has read the last seen id and set the publisher (for tests). */
    boolean awaitStarted(long millis) throws InterruptedException {
        return started.await(millis, TimeUnit.MILLISECONDS);
    }

    // --- the steps (package-visible so tests can drive them without timers) ---

    void init() throws SQLException {
        try (Connection c = connections.open();
             PreparedStatement max = c.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM utags_changes")) {
            try (ResultSet rs = max.executeQuery()) {
                rs.next();
                lastSeen = rs.getLong(1);
            }
            seen.clear();
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM utags_changes WHERE id > ?")) {
                ps.setLong(1, lastSeen - WINDOW);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) seen.add(rs.getLong(1));
                }
            }
        }
    }

    /**
     * Inserts everything queued, in one batch. Synchronized so a caller's flush waits for one already running on the
     * engine thread: once it returns, everything published before the call is in the table.
     */
    synchronized void flush() throws SQLException {
        List<Pending> batch = new ArrayList<>();
        for (Pending p; (p = queue.poll()) != null; ) batch.add(p);
        if (batch.isEmpty()) return;
        try (Connection c = connections.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO utags_changes (kind, player_uuid, server_id, created_at) VALUES (?, ?, ?, ?)")) {
            for (Pending p : batch) {
                ps.setString(1, p.kind().name());
                ps.setString(2, p.player() == null ? null : p.player().toString());
                ps.setString(3, serverId);
                ps.setLong(4, p.at());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Reads new rows from other servers and delivers them; returns how many deliveries were made. */
    int pollOnce() throws SQLException {
        Set<Key> deliveries = new LinkedHashSet<>();
        long newest = lastSeen;
        List<Long> fresh = new ArrayList<>();
        try (Connection c = connections.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, kind, player_uuid, server_id FROM utags_changes WHERE id > ? ORDER BY id")) {
            ps.setLong(1, lastSeen - WINDOW);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    if (seen.contains(id)) continue;
                    fresh.add(id);
                    newest = Math.max(newest, id);
                    if (serverId.equals(rs.getString(4))) continue;
                    ChangeFeed.Kind kind;
                    UUID player;
                    try {
                        kind = ChangeFeed.Kind.valueOf(rs.getString(2));
                        String raw = rs.getString(3);
                        player = raw == null ? null : UUID.fromString(raw);
                    } catch (IllegalArgumentException unknown) {
                        continue; // a newer version's kind, or a bad row
                    }
                    deliveries.add(new Key(kind, player));
                }
            }
        }
        seen.addAll(fresh);
        lastSeen = newest;
        seen.headSet(lastSeen - WINDOW, true).clear();
        for (Key key : deliveries) feed.deliverRemote(key.kind(), key.player());
        return deliveries.size();
    }

    /** Deletes rows older than 24 hours before {@code now}; returns how many. */
    int prune(long now) throws SQLException {
        try (Connection c = connections.open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM utags_changes WHERE created_at < ?")) {
            ps.setLong(1, now - KEEP_MILLIS);
            return ps.executeUpdate();
        }
    }

    private void safeFlush() {
        flushQueued.set(false);
        try {
            flush();
        } catch (SQLException | RuntimeException e) {
            log.warning("Sync could not publish a change (other servers pick it up on their next tag reload): " + e.getMessage());
        }
        if (!queue.isEmpty() && flushQueued.compareAndSet(false, true)) thread.execute(this::safeFlush);
    }

    private void safePoll() {
        try {
            pollOnce();
            if (pollFailing) log.info("Sync is reading changes again.");
            pollFailing = false;
        } catch (SQLException | RuntimeException e) {
            if (!pollFailing) log.warning("Sync could not read changes: " + e.getMessage());
            pollFailing = true;
        }
    }

    private void safePrune() {
        try {
            prune(System.currentTimeMillis());
        } catch (SQLException | RuntimeException e) {
            log.warning("Sync could not prune old changes: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        feed.setPublisher((k, p) -> { });
        thread.shutdown();
        try {
            thread.awaitTermination(2, TimeUnit.SECONDS);
            flush(); // last changes before shutdown
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (SQLException | RuntimeException e) {
            log.warning("Sync could not publish the last changes: " + e.getMessage());
        }
    }
}
