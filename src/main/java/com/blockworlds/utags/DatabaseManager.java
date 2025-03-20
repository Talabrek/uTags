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
        
        // Load database credentials from config
        this.host = plugin.getConfig().getString("database.host");
        this.port = plugin.getConfig().getInt("database.port");
        this.database = plugin.getConfig().getString("database.database");
        this.username = plugin.getConfig().getString("database.username");
        this.password = plugin.getConfig().getString("database.password");
        
        // Initialize connection pool
        this.dataSource = createDataSource();
        
        // Create tables
        createTablesIfNotExist();
        
        logger.info("DatabaseManager initialized successfully");
    }
    
    /**
     * Creates and configures the HikariCP data source
     * 
     * @return The configured data source
     */
    private HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        
        // Basic configuration
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=utf8");
        config.setUsername(username);
        config.setPassword(password);
        
        // Pool configuration
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(3);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(5000);
        config.setMaxLifetime(1800000);
        
        // Connection testing
        config.setConnectionTestQuery("SELECT 1");
        
        // Enable metrics gathering
        config.setRegisterMbeans(true);
        
        // Set pool name for easier debugging
        config.setPoolName("uTags-HikariPool");
        
        try {
            return new HikariDataSource(config);
        } catch (Exception e) {
            logger.severe("Failed to create HikariCP data source: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize database connection pool", e);
        }
    }
    
    /**
     * Gets a connection from the connection pool
     * 
     * @return A database connection
     * @throws SQLException If a database access error occurs
     */
    public Connection getConnection() throws SQLException {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            logger.severe("Failed to get connection from pool: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Creates necessary database tables if they don't exist
     */
    private void createTablesIfNotExist() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            
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
                "`weight` INT NOT NULL" +
                ");"
            );
            
            // Create tag requests table
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `tag_requests` (" +
                "`id` INT AUTO_INCREMENT PRIMARY KEY," +
                "`player_uuid` VARCHAR(36) NOT NULL," +
                "`player_name` VARCHAR(255) NOT NULL," +
                "`tag_display` VARCHAR(255) NOT NULL);"
            );
            
            logger.info("Database tables checked and created if needed");
        } catch (SQLException e) {
            logger.severe("Error creating database tables: " + e.getMessage());
            e.printStackTrace();
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
     * Updates a tag attribute in the database
     * 
     * @param tagName The name of the tag to update
     * @param attribute The attribute to update
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
        
        String query = "UPDATE tags SET " + attribute + " = ? WHERE name = ?";
        
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
     * Shuts down the connection pool
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool has been closed");
        }
    }
}
