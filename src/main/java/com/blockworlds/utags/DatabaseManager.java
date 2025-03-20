package com.blockworlds.utags;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database connections and operations for the uTags plugin.
 * Centralizes all database access to improve connection management, security, and maintainability.
 */
public class DatabaseManager {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final HikariDataSource dataSource;
    
    // Database credentials from config
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    
    /**
     * Creates a new DatabaseManager with the specified plugin
     * 
     * @param plugin The plugin instance
     */
    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        
        // Load and validate database credentials from config
        validateDatabaseConfig(plugin.getConfig());
        
        this.host = plugin.getConfig().getString("database.host");
        this.port = plugin.getConfig().getInt("database.port");
        this.database = plugin.getConfig().getString("database.database");
        this.username = plugin.getConfig().getString("database.username");
        this.password = plugin.getConfig().getString("database.password");
        
        // Initialize connection pool
        this.dataSource = createDataSource();
        
        // Create tables
        createTablesIfNotExist();
        
        // Schedule periodic health checks
        scheduleHealthChecks();
        
        logger.info("DatabaseManager initialized successfully");
    }
    
    /**
     * Validates that all required database configuration parameters are present
     * 
     * @param config The plugin configuration
     * @throws IllegalArgumentException If configuration is invalid
     */
    private void validateDatabaseConfig(org.bukkit.configuration.file.FileConfiguration config) {
        // List of required database configuration parameters
        String[] requiredParams = {
            "database.host", "database.port", "database.database", 
            "database.username", "database.password"
        };
        
        List<String> missingParams = new ArrayList<>();
        
        // Check for missing or empty parameters
        for (String param : requiredParams) {
            if (!config.contains(param) || config.getString(param, "").isEmpty()) {
                missingParams.add(param);
            }
        }
        
        // If any parameters are missing, throw an exception
        if (!missingParams.isEmpty()) {
            throw new IllegalArgumentException("Missing required database configuration parameters: " + 
                                               String.join(", ", missingParams));
        }
        
        // Validate port number
        int port = config.getInt("database.port");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid database port number. Must be between 1 and 65535.");
        }
        
        logger.info("Database configuration validation successful");
    }
    
    /**
     * Schedules periodic health checks for the database connection pool
     */
    private void scheduleHealthChecks() {
        // Run health check every 10 minutes (12000 ticks)
        int healthCheckInterval = plugin.getConfig().getInt("database.health-check-interval", 12000);
        
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::performHealthCheck, 
                                                                   healthCheckInterval, healthCheckInterval);
        
        logger.info("Database health checks scheduled every " + (healthCheckInterval/20) + " seconds");
    }
    
    /**
     * Performs a health check on the database connection pool
     */
    private void performHealthCheck() {
        try {
            // Log current pool status
            logPoolStatus();
            
            // Test a connection to verify database connectivity
            try (Connection connection = getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement("SELECT 1");
                     ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        logger.info("Database health check successful");
                    } else {
                        logger.warning("Database health check received unexpected result");
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Database health check failed: " + e.getMessage());
            
            // Attempt to refresh the connection pool if possible
            attemptPoolRefresh();
        }
    }
    
    /**
     * Attempts to refresh the connection pool in case of connectivity issues
     */
    private void attemptPoolRefresh() {
        logger.warning("Attempting to refresh database connection pool...");
        
        try {
            // Soft restart of the pool - HikariCP usually handles this automatically,
            // but we can evict idle connections to force reconnection
            if (dataSource.getHikariPoolMXBean() != null) {
                dataSource.getHikariPoolMXBean().softEvictConnections();
                logger.info("Connection pool refresh completed");
            }
        } catch (Exception e) {
            logger.severe("Failed to refresh connection pool: " + e.getMessage());
        }
    }
    
    /**
     * Creates and configures the HikariCP data source
     * 
     * @return The configured data source
     */
    private HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        
        // Get configuration values from config.yml with defaults if not present
        int maxPoolSize = plugin.getConfig().getInt("database.pool.max-size", 10);
        int minIdle = plugin.getConfig().getInt("database.pool.min-idle", 3);
        int idleTimeout = plugin.getConfig().getInt("database.pool.idle-timeout", 30000);
        int connectionTimeout = plugin.getConfig().getInt("database.pool.connection-timeout", 10000);
        int validationTimeout = plugin.getConfig().getInt("database.pool.validation-timeout", 5000);
        int maxLifetime = plugin.getConfig().getInt("database.pool.max-lifetime", 1800000);
        int leakDetectionThreshold = plugin.getConfig().getInt("database.pool.leak-detection-threshold", 30000);
        int initializationFailTimeout = plugin.getConfig().getInt("database.pool.initialization-fail-timeout", 10000);
        
        // JDBC URL with connection parameters
        StringBuilder jdbcUrl = new StringBuilder("jdbc:mysql://")
            .append(host).append(":").append(port).append("/").append(database)
            .append("?useSSL=false")
            .append("&autoReconnect=true")
            .append("&useUnicode=true")
            .append("&characterEncoding=utf8")
            .append("&connectTimeout=").append(connectionTimeout)
            .append("&socketTimeout=").append(connectionTimeout * 2)
            .append("&maxReconnects=10")
            .append("&initialTimeout=2")
            .append("&tcpKeepAlive=true");
        
        // Basic configuration
        config.setJdbcUrl(jdbcUrl.toString());
        config.setUsername(username);
        config.setPassword(password);
        
        // Pool configuration
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setIdleTimeout(idleTimeout);
        config.setConnectionTimeout(connectionTimeout);
        config.setValidationTimeout(validationTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setLeakDetectionThreshold(leakDetectionThreshold);
        config.setInitializationFailTimeout(initializationFailTimeout);
        
        // Auto-commit behavior
        config.setAutoCommit(true);
        
        // Connection testing and initialization
        config.setConnectionTestQuery("SELECT 1");
        config.setConnectionInitSql("SET NAMES utf8mb4");
        
        // Enable metrics gathering
        config.setRegisterMbeans(true);
        
        // Set pool name for easier debugging
        config.setPoolName("uTags-HikariPool");
        
        // Set custom properties
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        // Retry logic for initial connection
        int maxRetries = 5;
        int retryDelay = 2000; // milliseconds
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HikariDataSource dataSource = new HikariDataSource(config);
                
                // Test the connection to ensure it's working
                try (Connection connection = dataSource.getConnection()) {
                    try (Statement statement = connection.createStatement()) {
                        try (ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                            if (resultSet.next()) {
                                logger.info("Database connection established successfully after " + attempt + " attempt(s)");
                                return dataSource;
                            }
                        }
                    }
                }
                
                // If we get here, connection worked but query failed, still return the datasource
                logger.warning("Connection test query returned unexpected result, but connection was established");
                return dataSource;
                
            } catch (Exception e) {
                lastException = e;
                logger.warning("Database connection attempt " + attempt + " of " + maxRetries + " failed: " + e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelay * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        // If we get here, all connection attempts failed
        logger.severe("Failed to create database connection after " + maxRetries + " attempts");
        if (lastException != null) {
            lastException.printStackTrace();
        }
        throw new RuntimeException("Failed to initialize database connection pool", lastException);
    }
    
    /**
     * Gets a connection from the connection pool with retry logic for transient failures
     * 
     * @return A database connection
     * @throws SQLException If a database access error occurs
     */
    public Connection getConnection() throws SQLException {
        // Number of retries for transient failures
        int maxRetries = plugin.getConfig().getInt("database.connection-retries", 3);
        int initialRetryDelayMs = plugin.getConfig().getInt("database.retry-delay-ms", 200);
        
        SQLException lastException = null;
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                Connection connection = dataSource.getConnection();
                
                // Test if the connection is actually valid
                if (connection.isValid(2)) { // 2 second timeout
                    return connection;
                } else {
                    // Connection isn't valid, close it and try again
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        // Ignore exceptions when closing an invalid connection
                    }
                    throw new SQLException("Retrieved connection is invalid");
                }
            } catch (SQLException e) {
                lastException = e;
                
                // Only log the first failure as warning, the rest as fine to avoid log spam
                if (attempt == 0) {
                    logger.warning("Database connection attempt " + (attempt + 1) + " failed: " + e.getMessage());
                } else {
                    logger.fine("Database connection attempt " + (attempt + 1) + " failed: " + e.getMessage());
                }
                
                // Check if this is a recoverable error
                if (isRecoverableError(e)) {
                    // Wait before retrying with exponential backoff
                    try {
                        int delayMs = initialRetryDelayMs * (1 << attempt); // Exponential backoff
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Connection retry interrupted", ie);
                    }
                } else {
                    // Non-recoverable error, don't retry
                    logger.severe("Non-recoverable database error: " + e.getMessage());
                    throw e;
                }
            }
        }
        
        // All retries failed
        logger.severe("Failed to get database connection after " + maxRetries + " attempts: " + 
                     (lastException != null ? lastException.getMessage() : "Unknown error"));
        
        throw new SQLException("Failed to get database connection after " + maxRetries + " attempts", lastException);
    }
    
    /**
     * Determines if an SQL exception is potentially recoverable through retries
     * 
     * @param ex The SQLException to check
     * @return True if the error might be resolved by retrying
     */
    private boolean isRecoverableError(SQLException ex) {
        // MySQL error codes that indicate transient failures
        // 1040: Too many connections
        // 1042: Can't get hostname for connection
        // 1043: Bad handshake
        // 1047: Unknown protocol
        // 1081: Can't connect (permission error)
        // 1152: Aborted connection
        // 1159: Network error
        // 1160: Got timeout reading communication packets
        // 1161: Got timeout writing communication packets
        int[] recoverableCodes = {1040, 1042, 1043, 1047, 1081, 1152, 1159, 1160, 1161};
        
        // Check MySQL error code
        int errorCode = ex.getErrorCode();
        for (int code : recoverableCodes) {
            if (errorCode == code) {
                return true;
            }
        }
        
        // Check SQLState for connection issues (starts with '08')
        String sqlState = ex.getSQLState();
        if (sqlState != null && sqlState.startsWith("08")) {
            return true;
        }
        
        // Check for specific exception types or messages that suggest a retry might succeed
        String message = ex.getMessage().toLowerCase();
        return message.contains("timeout") 
            || message.contains("too many connections")
            || message.contains("connection reset")
            || message.contains("connection refused") 
            || message.contains("connection closed")
            || message.contains("broken pipe")
            || message.contains("network error");
    }
    
    /**
     * Creates necessary database tables if they don't exist
     * 
     * @throws DatabaseInitializationException If tables cannot be created
     */
    private void createTablesIfNotExist() {
        try (Connection connection = getConnection()) {
            createTablesWithRetry(connection, 3);
            logger.info("Database tables checked and created if needed");
        } catch (SQLException e) {
            String errorMsg = "Critical error creating database tables: " + e.getMessage();
            logger.severe(errorMsg);
            e.printStackTrace();
            throw new DatabaseInitializationException(errorMsg, e);
        }
    }
    
    /**
     * Creates tables with retry logic
     * 
     * @param connection The database connection
     * @param maxRetries The maximum number of retries
     * @throws SQLException If tables cannot be created after all retries
     */
    private void createTablesWithRetry(Connection connection, int maxRetries) throws SQLException {
        SQLException lastException = null;
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try (Statement statement = connection.createStatement()) {
                // Create tags table
                statement.executeUpdate(
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
                    ");"
                );
                
                // Create tag requests table
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `tag_requests` (" +
                    "`id` INT AUTO_INCREMENT PRIMARY KEY," +
                    "`player_uuid` VARCHAR(36) NOT NULL," +
                    "`player_name` VARCHAR(255) NOT NULL," +
                    "`tag_display` VARCHAR(255) NOT NULL," +
                    "INDEX `idx_player_uuid` (`player_uuid`)," +
                    "INDEX `idx_player_name` (`player_name`)" +
                    ");"
                );
                
                // Successfully created tables, return
                return;
            } catch (SQLException e) {
                lastException = e;
                logger.warning("Failed to create tables on attempt " + (attempt + 1) + ": " + e.getMessage());
                
                if (attempt < maxRetries - 1) {
                    try {
                        // Wait before retrying
                        Thread.sleep(1000 * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Table creation interrupted", ie);
                    }
                }
            }
        }
        
        // If we got here, all attempts failed
        if (lastException != null) {
            throw lastException;
        } else {
            throw new SQLException("Failed to create database tables for unknown reason");
        }
    }
    
    /**
     * Custom exception for database initialization failures
     */
    public static class DatabaseInitializationException extends RuntimeException {
        public DatabaseInitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Gets a list of available tags of the specified type
     * 
     * @param tagType The type of tags to retrieve
     * @return A list of available tags
     */
    public List<Tag> getAvailableTags(TagType tagType) {
        List<Tag> availableTags = new ArrayList<>();
        String query;
        
        if (tagType == TagType.PREFIX) {
            query = "SELECT * FROM tags WHERE type = 'PREFIX' OR type = 'BOTH' ORDER BY weight DESC;";
        } else if (tagType == TagType.SUFFIX) {
            query = "SELECT * FROM tags WHERE type = 'SUFFIX' OR type = 'BOTH' ORDER BY weight DESC;";
        } else {
            query = "SELECT * FROM tags ORDER BY weight DESC;";
        }
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            
            while (resultSet.next()) {
                availableTags.add(createTagFromResultSet(resultSet));
            }
        } catch (SQLException e) {
            logger.severe("Error retrieving available tags: " + e.getMessage());
            e.printStackTrace();
        }
        
        return availableTags;
    }
    
    /**
     * Creates a Tag object from a ResultSet row
     * 
     * @param resultSet The ResultSet containing tag data
     * @return A Tag object
     * @throws SQLException If a database access error occurs
     */
    private Tag createTagFromResultSet(ResultSet resultSet) throws SQLException {
        String name = resultSet.getString("name");
        String display = resultSet.getString("display");
        TagType type = TagType.valueOf(resultSet.getString("type"));
        boolean isPublic = resultSet.getBoolean("public");
        boolean color = resultSet.getBoolean("color");
        ItemStack material = deserializeMaterial(resultSet.getString("material"));
        int weight = resultSet.getInt("weight");
        
        return new Tag(name, display, type, isPublic, color, material, weight);
    }
    
    /**
     * Deserializes a base64 string into an ItemStack
     * 
     * @param base64Material The base64 encoded material
     * @return The deserialized ItemStack
     */
    private ItemStack deserializeMaterial(String base64Material) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64Material));
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                return (ItemStack) dataInput.readObject();
            }
        } catch (IllegalArgumentException | IOException | ClassNotFoundException e) {
            logger.warning("Failed to deserialize material, using default: " + e.getMessage());
            // Set a default material if deserialization fails
            return new ItemStack(Material.NAME_TAG);
        }
    }
    
    /**
     * Serializes an ItemStack to a base64 string
     * 
     * @param material The ItemStack to serialize
     * @return A base64 encoded string
     */
    private String serializeMaterial(ItemStack material) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeObject(material);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            logger.severe("Error serializing material: " + e.getMessage());
            throw new RuntimeException("Failed to serialize material", e);
        }
    }
    
    /**
     * Adds a tag to the database
     * 
     * @param tag The tag to add
     * @return True if successful, false otherwise
     */
    public boolean addTagToDatabase(Tag tag) {
        String query = "REPLACE INTO tags (name, display, type, public, color, material, weight) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, tag.getName());
            statement.setString(2, tag.getDisplay());
            statement.setString(3, tag.getType().toString());
            statement.setBoolean(4, tag.isPublic());
            statement.setBoolean(5, tag.isColor());
            statement.setString(6, serializeMaterial(tag.getMaterial()));
            statement.setInt(7, tag.getWeight());
            
            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.severe("Error adding tag to database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Deletes a tag from the database
     * 
     * @param tagName The name of the tag to delete
     * @return True if successful, false otherwise
     */
    public boolean deleteTagFromDatabase(String tagName) {
        String query = "DELETE FROM tags WHERE name = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, tagName);
            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.severe("Error deleting tag from database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
 * Updates a tag attribute in the database.
 * Previous implementation had SQL injection vulnerability by directly concatenating
 * the attribute name into the query string.
 *
 * @param tagName The name of the tag to edit
 * @param attribute The attribute to edit
 * @param newValue The new value for the attribute
 * @return True if successful, false otherwise
 */
public boolean editTagAttribute(String tagName, String attribute, String newValue) {
    // Validate attribute to prevent SQL injection
    if (!isValidAttribute(attribute)) {
        logger.warning("Invalid attribute name attempted: " + attribute);
        return false;
    }
    
    // First check if the tag exists
    if (!tagExists(tagName)) {
        return false;
    }
    
    // Use attribute-specific prepared statements instead of string concatenation
    String query;
    switch(attribute.toLowerCase()) {
        case "name":
            query = "UPDATE tags SET name = ? WHERE name = ?";
            break;
        case "display":
            query = "UPDATE tags SET display = ? WHERE name = ?";
            break;
        case "type":
            query = "UPDATE tags SET type = ? WHERE name = ?";
            break;
        case "public":
            query = "UPDATE tags SET public = ? WHERE name = ?";
            break;
        case "color":
            query = "UPDATE tags SET color = ? WHERE name = ?";
            break;
        case "material":
            query = "UPDATE tags SET material = ? WHERE name = ?";
            break;
        case "weight":
            query = "UPDATE tags SET weight = ? WHERE name = ?";
            break;
        default:
            // This shouldn't happen due to isValidAttribute check, but just in case
            logger.severe("Unexpected attribute passed validation: " + attribute);
            return false;
    }
    
    try (Connection connection = getConnection();
         PreparedStatement statement = connection.prepareStatement(query)) {
        
        statement.setString(1, newValue);
        statement.setString(2, tagName);
        
        int rowsAffected = statement.executeUpdate();
        return rowsAffected > 0;
    } catch (SQLException e) {
        logger.severe("Error updating tag attribute: " + e.getMessage());
        e.printStackTrace();
        return false;
    }
}
    
    /**
     * Checks if an attribute name is valid to prevent SQL injection
     * 
     * @param attribute The attribute name to check
     * @return True if valid, false otherwise
     */
    private boolean isValidAttribute(String attribute) {
        // Whitelist of valid column names
        return attribute.equals("name") || 
               attribute.equals("display") || 
               attribute.equals("type") || 
               attribute.equals("public") || 
               attribute.equals("color") || 
               attribute.equals("material") ||
               attribute.equals("weight");
    }
    
    /**
     * Checks if a tag exists in the database
     * 
     * @param tagName The name of the tag to check
     * @return True if the tag exists, false otherwise
     */
    private boolean tagExists(String tagName) {
        String query = "SELECT COUNT(*) FROM tags WHERE name = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, tagName);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.severe("Error checking if tag exists: " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    /**
     * Gets the tag name by its display text
     * 
     * @param display The display text of the tag
     * @return The tag name, or null if not found
     */
    public String getTagNameByDisplay(String display) {
        String query = "SELECT name FROM tags WHERE display = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, display);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("name");
                }
            }
        } catch (SQLException e) {
            logger.severe("Error getting tag name by display: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Gets the tag display text by its name
     * 
     * @param name The name of the tag
     * @return The tag display text, or null if not found
     */
    public String getTagDisplayByName(String name) {
        String query = "SELECT display FROM tags WHERE name = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, name);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("display");
                }
            }
        } catch (SQLException e) {
            logger.severe("Error getting tag display by name: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Gets a list of all custom tag requests
     * 
     * @return A list of CustomTagRequest objects
     */
    public List<CustomTagRequest> getCustomTagRequests() {
        List<CustomTagRequest> requests = new ArrayList<>();
        String query = "SELECT * FROM tag_requests";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                String playerName = resultSet.getString("player_name");
                String tagDisplay = resultSet.getString("tag_display");
                
                requests.add(new CustomTagRequest(id, playerUuid, playerName, tagDisplay));
            }
        } catch (SQLException e) {
            logger.severe("Error retrieving custom tag requests: " + e.getMessage());
            e.printStackTrace();
        }
        
        return requests;
    }
    
    /**
     * Gets a custom tag request by player name
     * 
     * @param playerName The name of the player
     * @return The CustomTagRequest, or null if not found
     */
    public CustomTagRequest getCustomTagRequestByPlayerName(String playerName) {
        String query = "SELECT * FROM tag_requests WHERE player_name = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, playerName);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                    String display = resultSet.getString("tag_display");
                    
                    return new CustomTagRequest(id, playerUuid, playerName, display);
                }
            }
        } catch (SQLException e) {
            logger.severe("Error getting custom tag request by player name: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Creates a custom tag request
     * 
     * @param playerUuid The UUID of the player
     * @param playerName The name of the player
     * @param tagDisplay The display text for the tag
     * @return True if successful, false otherwise
     */
    public boolean createCustomTagRequest(UUID playerUuid, String playerName, String tagDisplay) {
        // First check if a request already exists for this player
        boolean exists = requestExistsForPlayer(playerUuid);
        
        if (exists) {
            // Update existing request
            return updateCustomTagRequest(playerUuid, playerName, tagDisplay);
        } else {
            // Create new request
            String query = "INSERT INTO tag_requests (player_uuid, player_name, tag_display) VALUES (?, ?, ?)";
            
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {
                
                statement.setString(1, playerUuid.toString());
                statement.setString(2, playerName);
                statement.setString(3, tagDisplay);
                
                int rowsAffected = statement.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                logger.severe("Error creating custom tag request: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
    }
    
    /**
     * Checks if a tag request exists for a player
     * 
     * @param playerUuid The UUID of the player
     * @return True if a request exists, false otherwise
     */
    private boolean requestExistsForPlayer(UUID playerUuid) {
        String query = "SELECT COUNT(*) FROM tag_requests WHERE player_uuid = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, playerUuid.toString());
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.severe("Error checking if request exists for player: " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    /**
     * Updates an existing custom tag request
     * 
     * @param playerUuid The UUID of the player
     * @param playerName The name of the player
     * @param tagDisplay The display text for the tag
     * @return True if successful, false otherwise
     */
    private boolean updateCustomTagRequest(UUID playerUuid, String playerName, String tagDisplay) {
        String query = "UPDATE tag_requests SET player_name = ?, tag_display = ? WHERE player_uuid = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, playerName);
            statement.setString(2, tagDisplay);
            statement.setString(3, playerUuid.toString());
            
            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.severe("Error updating custom tag request: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Removes a custom tag request from the database
     * 
     * @param requestId The ID of the request to remove
     * @return True if successful, false otherwise
     */
    public boolean removeCustomTagRequest(int requestId) {
        String query = "DELETE FROM tag_requests WHERE id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setInt(1, requestId);
            
            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.severe("Error removing custom tag request: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Counts the number of custom tags for a player
     * 
     * @param playerName The name of the player
     * @return The number of custom tags
     */
    public int countCustomTags(String playerName) {
        String query = "SELECT COUNT(*) FROM tags WHERE name LIKE ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, playerName + "%");
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.severe("Error counting custom tags: " + e.getMessage());
            e.printStackTrace();
        }
        
        return 0;
    }
    
    /**
     * Purges all tags from the database
     * 
     * @return True if successful, false otherwise
     */
    public boolean purgeTagsTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            
            // Drop the table
            statement.executeUpdate("DROP TABLE IF EXISTS tags");
            
            // Recreate the table
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `tags` (" +
                "`id` INT AUTO_INCREMENT PRIMARY KEY," +
                "`name` VARCHAR(255) NOT NULL," +
                "`display` VARCHAR(255) NOT NULL," +
                "`type` ENUM('PREFIX', 'SUFFIX', 'BOTH') NOT NULL," +
                "`public` BOOLEAN NOT NULL," +
                "`color` BOOLEAN NOT NULL," +
                "`material` MEDIUMTEXT NOT NULL," +
                "`weight` INT NOT NULL" +
                ");"
            );
            
            return true;
        } catch (SQLException e) {
            logger.severe("Error purging tags table: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Purges all tag requests from the database
     * 
     * @return True if successful, false otherwise
     */
    public boolean purgeRequestsTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            
            // Drop the table
            statement.executeUpdate("DROP TABLE IF EXISTS tag_requests");
            
            // Recreate the table
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `tag_requests` (" +
                "`id` INT AUTO_INCREMENT PRIMARY KEY," +
                "`player_uuid` VARCHAR(36) NOT NULL," +
                "`player_name` VARCHAR(255) NOT NULL," +
                "`tag_display` VARCHAR(255) NOT NULL);"
            );
            
            return true;
        } catch (SQLException e) {
            logger.severe("Error purging requests table: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Updates the database schema if needed
     * 
     * @param currentVersion The current schema version
     * @param latestVersion The latest schema version
     * @return True if successful, false otherwise
     */
    public boolean updateDatabaseSchema(int currentVersion, int latestVersion) {
        if (currentVersion >= latestVersion) {
            return true; // No update needed
        }
        
        logger.info("Updating database schema from version " + currentVersion + " to version " + latestVersion);
        
        try (Connection connection = getConnection()) {
            for (int i = currentVersion + 1; i <= latestVersion; i++) {
                switch (i) {
                    case 3:
                        addMaterialColumnIfNotExists(connection);
                        break;
                    // Add more cases for future schema updates
                }
            }
            
            return true;
        } catch (SQLException e) {
            logger.severe("Error updating database schema: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Adds a material column to the tags table if it doesn't exist
     * 
     * @param connection The database connection
     * @throws SQLException If a database access error occurs
     */
    private void addMaterialColumnIfNotExists(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet resultSet = metaData.getColumns(null, null, "tags", "material");
        
        if (!resultSet.next()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE tags MODIFY COLUMN `material` MEDIUMTEXT NOT NULL;");
            }
        }
        
        resultSet.close();
    }
    
    /**
     * Gets the status of the connection pool
     * 
     * @return A string with pool statistics
     */
    public String getPoolStatus() {
        StringBuilder status = new StringBuilder();
        
        status.append("Connection Pool Status:\n");
        status.append("- Total Connections: ").append(dataSource.getHikariPoolMXBean().getTotalConnections()).append("\n");
        status.append("- Active Connections: ").append(dataSource.getHikariPoolMXBean().getActiveConnections()).append("\n");
        status.append("- Idle Connections: ").append(dataSource.getHikariPoolMXBean().getIdleConnections()).append("\n");
        status.append("- Threads Awaiting Connection: ").append(dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()).append("\n");
        
        return status.toString();
    }
    
    /**
     * Logs the current pool status
     */
    public void logPoolStatus() {
        logger.info(getPoolStatus());
    }
    
    /**
     * Executes a batch operation with a provided PreparedStatementConsumer
     * This allows for efficient execution of multiple similar database operations
     *
     * @param sql The SQL statement to prepare
     * @param batchSize The maximum size of each batch
     * @param items The items to process
     * @param preparer A functional interface to set parameters for each item
     * @param <T> The type of items to process
     * @return An array with the number of affected rows for each batch
     * @throws SQLException If a database error occurs
     */
    public <T> int[] executeBatch(String sql, int batchSize, List<T> items, 
                                PreparedStatementConsumer<T> preparer) throws SQLException {
        if (items == null || items.isEmpty()) {
            return new int[0];
        }

        List<int[]> results = new ArrayList<>();
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            int count = 0;
            for (T item : items) {
                preparer.accept(statement, item);
                statement.addBatch();
                count++;
                
                // Execute batch if we've reached the batch size
                if (count % batchSize == 0) {
                    results.add(statement.executeBatch());
                    statement.clearBatch();
                }
            }
            
            // Execute any remaining items
            if (count % batchSize != 0) {
                results.add(statement.executeBatch());
            }
        }
        
        // Combine all batch results into a single array
        return combineBatchResults(results);
    }
    
    /**
     * Combines multiple batch result arrays into a single array
     *
     * @param results List of batch result arrays
     * @return A single array containing all results
     */
    private int[] combineBatchResults(List<int[]> results) {
        int totalLength = 0;
        for (int[] result : results) {
            totalLength += result.length;
        }
        
        int[] combined = new int[totalLength];
        int position = 0;
        
        for (int[] result : results) {
            System.arraycopy(result, 0, combined, position, result.length);
            position += result.length;
        }
        
        return combined;
    }
    
    /**
     * Functional interface for setting prepared statement parameters
     *
     * @param <T> The type of item to process
     */
    @FunctionalInterface
    public interface PreparedStatementConsumer<T> {
        /**
         * Sets parameters on a PreparedStatement for a given item
         *
         * @param ps The PreparedStatement
         * @param item The item to use for parameters
         * @throws SQLException If a database error occurs
         */
        void accept(PreparedStatement ps, T item) throws SQLException;
    }
    
    /**
     * Shuts down the connection pool
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool has been closed");
        }
    }
}
