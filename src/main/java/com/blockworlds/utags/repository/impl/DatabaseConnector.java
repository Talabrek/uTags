package com.blockworlds.utags.repository.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Manages database connections using HikariCP connection pooling.
 */
public class DatabaseConnector {
    private static final String CREATE_TAGS_TABLE = 
        "CREATE TABLE IF NOT EXISTS `tags` (" +
        "`id` INT AUTO_INCREMENT PRIMARY KEY," +
        "`name` VARCHAR(255) NOT NULL," +
        "`display` VARCHAR(255) NOT NULL," +
        "`type` ENUM('PREFIX', 'SUFFIX', 'BOTH') NOT NULL," +
        "`public` BOOLEAN NOT NULL," +
        "`color` BOOLEAN NOT NULL," +
        "`material` MEDIUMTEXT NOT NULL," +
        "`weight` INT NOT NULL," +
        "INDEX `idx_tag_name` (`name`)" +
        ");";
    
    private static final String CREATE_REQUESTS_TABLE = 
        "CREATE TABLE IF NOT EXISTS `tag_requests` (" +
        "`id` INT AUTO_INCREMENT PRIMARY KEY," +
        "`player_uuid` VARCHAR(36) NOT NULL," +
        "`player_name` VARCHAR(255) NOT NULL," +
        "`tag_display` VARCHAR(255) NOT NULL," +
        "INDEX `idx_player_uuid` (`player_uuid`)," +
        "INDEX `idx_player_name` (`player_name`)" +
        ");";
    
    private final HikariDataSource dataSource;
    private final Logger logger;
    private final JavaPlugin plugin;
    private boolean databaseInitialized = false;
    
    /**
     * Creates a new DatabaseConnector with settings from plugin configuration.
     */
    public DatabaseConnector(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        FileConfiguration config = plugin.getConfig();
        
        // Get database configuration
        String host = config.getString("database.host", "localhost");
        int port = config.getInt("database.port", 3306);
        String database = config.getString("database.database", "utags");
        String username = config.getString("database.username", "root");
        String password = config.getString("database.password", "");
        
        // Configure HikariCP
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true", host, port, database));
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        
        // Pool settings
        hikariConfig.setMaximumPoolSize(config.getInt("database.pool.max-size", 10));
        hikariConfig.setMinimumIdle(config.getInt("database.pool.min-idle", 3));
        hikariConfig.setIdleTimeout(config.getInt("database.pool.idle-timeout", 30000));
        hikariConfig.setConnectionTimeout(config.getInt("database.pool.connection-timeout", 10000));
        hikariConfig.setPoolName("uTags-HikariPool");
        
        // Performance optimizations
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        // Initialize pool
        this.dataSource = new HikariDataSource(hikariConfig);
        createTablesIfNeeded();
        
        if (databaseInitialized) {
            logger.info("Database connection pool initialized successfully");
        }
    }
    
    /**
     * Gets a connection from the pool.
     * 
     * @return A database connection
     * @throws SQLException If a connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        if (!databaseInitialized) {
            throw new SQLException("Database is not properly initialized");
        }
        return dataSource.getConnection();
    }
    
    /**
     * Checks if the database is properly initialized.
     * 
     * @return true if the database is initialized, false otherwise
     */
    public boolean isDatabaseInitialized() {
        return databaseInitialized;
    }
    
    /**
     * Creates required tables if they don't exist.
     */
    private void createTablesIfNeeded() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.executeUpdate(CREATE_TAGS_TABLE);
            stmt.executeUpdate(CREATE_REQUESTS_TABLE);
            logger.info("Database tables checked");
            databaseInitialized = true;
        } catch (SQLException e) {
            logger.severe("Error creating database tables: " + e.getMessage());
            // Instead of throwing RuntimeException, set a flag
            databaseInitialized = false;
            // Let the plugin gracefully handle this
            plugin.getServer().getScheduler().runTask(plugin, () -> 
                plugin.getLogger().severe("Database initialization failed. Plugin may not function correctly."));
        }
    }
    
    /**
     * Closes the connection pool.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }
    
    /**
     * Gets statistics about the connection pool.
     * 
     * @return A string containing pool statistics
     */
    public String getPoolStats() {
        if (dataSource == null || dataSource.isClosed()) {
            return "Pool is closed";
        }
        return String.format("Pool Stats - Active: %d, Idle: %d, Total: %d", 
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections());
    }
}
