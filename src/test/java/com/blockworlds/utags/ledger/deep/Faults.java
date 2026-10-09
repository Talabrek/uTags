package com.blockworlds.utags.ledger.deep;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A JDBC fault injector for the deep ledger tests: wraps a real connection and fails chosen calls. Every fault is
 * applied AFTER or INSTEAD OF the real call exactly as documented on its field, so the test knows what the server saw.
 */
final class Faults {

    /** Commit really happens, then the "reply is lost": the caller sees 08S01. Fires once. */
    volatile boolean commitThrowsAfter;
    /** The next N inserts into utags_grants throw 40001 instead of running (and the real transaction is rolled back, as InnoDB does for a deadlock victim). */
    final AtomicInteger failGrantInserts = new AtomicInteger();
    /** Only the grant insert with this 1-based number (counted over the whole wrapper life) fails with 40001; 0 = off. */
    volatile int failGrantInsertNumber;
    final AtomicInteger grantInserts = new AtomicInteger();
    /** Runs instead of the next INSERT INTO utags_selection, then that insert throws 40001. Fires once. */
    volatile SqlAction beforeSelectionInsertFails;
    /** Transactions started (setAutoCommit(false) on MySQL, BEGIN IMMEDIATE on SQLite). */
    final AtomicInteger begins = new AtomicInteger();

    @FunctionalInterface
    interface SqlAction {
        void run() throws Exception;
    }

    static SQLException deadlock() {
        return new SQLException("Deadlock found when trying to get lock (injected)", "40001", 1213);
    }

    Connection wrap(Connection real) {
        return (Connection) Proxy.newProxyInstance(Faults.class.getClassLoader(), new Class<?>[]{Connection.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "commit" -> {
                    call(real, m, a);
                    if (commitThrowsAfter) {
                        commitThrowsAfter = false;
                        throw new SQLException("Communications link failure (injected after a real commit)", "08S01");
                    }
                    return null;
                }
                case "setAutoCommit" -> {
                    if (Boolean.FALSE.equals(a[0])) begins.incrementAndGet();
                    return call(real, m, a);
                }
                case "prepareStatement" -> {
                    PreparedStatement ps = (PreparedStatement) call(real, m, a);
                    return statement(real, ps, (String) a[0]);
                }
                case "createStatement" -> {
                    Statement st = (Statement) call(real, m, a);
                    return sqliteControl(st);
                }
                default -> {
                    return call(real, m, a);
                }
            }
        });
    }

    private PreparedStatement statement(Connection real, PreparedStatement ps, String sql) {
        return (PreparedStatement) Proxy.newProxyInstance(Faults.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
            if (m.getName().equals("executeUpdate") && (a == null || a.length == 0)) {
                if (sql.startsWith("INSERT INTO utags_grants")) {
                    int n = grantInserts.incrementAndGet();
                    if (failGrantInserts.getAndUpdate(v -> v > 0 ? v - 1 : 0) > 0 || n == failGrantInsertNumber) {
                        victim(real);
                        throw deadlock();
                    }
                }
                if (sql.startsWith("INSERT INTO utags_selection") && beforeSelectionInsertFails != null) {
                    SqlAction action = beforeSelectionInsertFails;
                    beforeSelectionInsertFails = null;
                    victim(real);
                    action.run();
                    throw deadlock();
                }
            }
            return call(ps, m, a);
        });
    }

    /** What InnoDB does to a deadlock victim: the whole transaction is rolled back. */
    private static void victim(Connection real) throws SQLException {
        if (!real.getAutoCommit()) {
            real.rollback();
        } else {
            try (Statement st = real.createStatement()) {
                st.execute("ROLLBACK");
            } catch (SQLException ignored) {
                // no SQLite transaction open
            }
        }
    }

    private Statement sqliteControl(Statement st) {
        return (Statement) Proxy.newProxyInstance(Faults.class.getClassLoader(), new Class<?>[]{Statement.class}, (p, m, a) -> {
            if (m.getName().equals("execute") && a != null && a.length == 1 && a[0] instanceof String sql) {
                if (sql.equals("BEGIN IMMEDIATE")) begins.incrementAndGet();
                if (sql.equals("COMMIT")) {
                    Object r = call(st, m, a);
                    if (commitThrowsAfter) {
                        commitThrowsAfter = false;
                        throw new SQLException("disk I/O error (injected after a real commit)", "08S01");
                    }
                    return r;
                }
            }
            return call(st, m, a);
        });
    }

    private static Object call(Object target, Method m, Object[] a) throws Throwable {
        try {
            return m.invoke(target, a);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
