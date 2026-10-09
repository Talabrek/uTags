package com.blockworlds.utags.storage;

import com.blockworlds.utags.SqlDialect;
import com.blockworlds.utags.StorageType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.util.DriverDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * The connection pool and the small executor every database call runs on. Nothing on the main thread may call
 * {@link #connection()}; use {@link #supply} / {@link #run} and hop back to the main thread for Bukkit calls.
 */
public final class Database implements AutoCloseable {

    @FunctionalInterface
    public interface SqlSupplier<T> {
        T get(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlRunnable {
        void run(Connection connection) throws SQLException;
    }

    private final HikariDataSource dataSource;
    private final StorageType type;
    private final Logger log;
    private final ExecutorService executor;

    private Database(HikariDataSource dataSource, StorageType type, Logger log) {
        this.dataSource = dataSource;
        this.type = type;
        this.log = log;
        AtomicInteger n = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(type == StorageType.SQLITE ? 2 : 3, r -> {
            Thread t = new Thread(r, "uTags-db-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Wraps a pool you built yourself (tests). */
    public static Database of(HikariDataSource dataSource, StorageType type, Logger log) {
        return new Database(dataSource, type, log);
    }

    /** Opens the pool from the plugin's config; throws when the database cannot be used. */
    public static Database open(JavaPlugin plugin) throws SQLException {
        FileConfiguration cfg = plugin.getConfig();
        Logger log = plugin.getLogger();
        // storage.type wins; without it, a config that already names a MySQL host keeps using MySQL
        // ignoreDefault: the jar's config.yml defines storage.type, which an upgraded install's file does not have
        String configured = cfg.contains("storage.type", true) ? cfg.getString("storage.type") : null;
        StorageType type = StorageType.resolve(configured, cfg.contains("database.host", true));

        HikariConfig config = new HikariConfig();
        if (type == StorageType.SQLITE) {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) log.warning("Could not create " + dataFolder);
            File file = new File(dataFolder, cfg.getString("storage.sqlite.file", "utags.db"));
            log.info("Using SQLite storage: " + file);
            applySqlitePool(config, file);
        } else {
            configureMysql(cfg, log, config);
            // Try one plain connection first: Hikari logs a full stack trace when it cannot connect,
            // so an unreachable server should fail here with only the one-line message
            try (Connection probe = new DriverDataSource(config.getJdbcUrl(), config.getDriverClassName(),
                    config.getDataSourceProperties(), config.getUsername(), config.getPassword()).getConnection()) {
                probe.isValid(5);
            }
        }
        HikariDataSource ds = new HikariDataSource(config);
        log.info("Database connection pool initialized successfully (" + type.name().toLowerCase(Locale.ROOT) + ")");
        return new Database(ds, type, log);
    }

    /** The Hikari settings for a SQLite file (public so the tests exercise the real pool setup). */
    public static void applySqlitePool(HikariConfig config, File file) {
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        // WAL lets the async tasks read while one writes; a busy timeout makes a second writer wait, not fail
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
    }

    private static void configureMysql(FileConfiguration cfg, Logger log, HikariConfig config) {
        // explicit driver/url (for H2 testing or other DBs)
        String driverClassName = cfg.getString("database.driverClassName");
        String jdbcUrl = cfg.getString("database.jdbcUrl");
        String dbHost = cfg.getString("database.host", "localhost");
        int dbPort = cfg.getInt("database.port", 3306);
        String dbName = cfg.getString("database.database", "utags");
        String dbUser = cfg.getString("database.username", "user");
        String dbPass = cfg.getString("database.password", "password");
        log.info("Connecting to database: " + dbHost + ":" + dbPort + "/" + dbName + " as user: " + dbUser);

        if (driverClassName != null && !driverClassName.isEmpty() && jdbcUrl != null && !jdbcUrl.isEmpty()) {
            config.setDriverClassName(driverClassName);
            config.setJdbcUrl(jdbcUrl);
            log.info("Using custom JDBC driver and URL from config.");
        } else {
            config.setJdbcUrl("jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName + "?autoReconnect=true&useSSL=false&connectTimeout=10000");
            log.info("Using MySQL JDBC URL constructed from config.yml values.");
        }
        config.setUsername(dbUser);
        config.setPassword(dbPass);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
    }


    /** The message of the real cause behind a CompletableFuture failure. */
    public static String reason(Throwable t) {
        Throwable cause = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
        return String.valueOf(cause.getMessage());
    }
    public StorageType type() {
        return type;
    }

    public SqlDialect dialect() {
        return type.dialect();
    }

    public Logger logger() {
        return log;
    }

    public ExecutorService executor() {
        return executor;
    }

    /** A pooled connection; the caller closes it. Never call this on the main thread. */
    public Connection connection() throws SQLException {
        if (dataSource.isClosed()) throw new SQLException("Database connection pool is not available");
        return dataSource.getConnection();
    }

    /** Runs the query on the database executor. A SQLException completes the future exceptionally. */
    public <T> CompletableFuture<T> supply(SqlSupplier<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = connection()) {
                return work.get(c);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> run(SqlRunnable work) {
        return supply(c -> {
            work.run(c);
            return null;
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        if (!dataSource.isClosed()) dataSource.close();
    }
}
