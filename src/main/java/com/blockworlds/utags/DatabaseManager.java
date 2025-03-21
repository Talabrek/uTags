package com.blockworlds.utags;

import com.blockworlds.utags.model.CustomTagRequest;
import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.repository.RequestRepository;
import com.blockworlds.utags.repository.TagRepository;
import com.blockworlds.utags.utils.Utils;
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
import java.util.logging.Logger;

public class DatabaseManager implements TagRepository, RequestRepository {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final HikariDataSource dataSource;
    
    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        
        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String database = plugin.getConfig().getString("database.database", "utags");
        String username = plugin.getConfig().getString("database.username", "root");
        String password = plugin.getConfig().getString("database.password", "");
        
        this.dataSource = createDataSource(host, port, database, username, password);
        createTablesIfNotExist();
        logger.info("DatabaseManager initialized");
    }
    
    private HikariDataSource createDataSource(String host, int port, String database, String username, String password) {
        HikariConfig config = new HikariConfig();
        
        StringBuilder jdbcUrl = new StringBuilder("jdbc:mysql://")
            .append(host).append(":").append(port).append("/").append(database)
            .append("?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=utf8");
        
        config.setJdbcUrl(jdbcUrl.toString());
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(3);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(5000);
        config.setMaxLifetime(1800000);
        config.setPoolName("uTags-HikariPool");
        
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        return new HikariDataSource(config);
    }
    
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    private void createTablesIfNotExist() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            
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
            
            logger.info("Database tables checked");
        } catch (SQLException e) {
            logger.severe("Error creating database tables: " + e.getMessage());
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
    
    // TagRepository implementation
    @Override
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
            Utils.logError("Error retrieving available tags", e);
        }
        
        return availableTags;
    }
    
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
    
    private ItemStack deserializeMaterial(String base64Material) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64Material));
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                return (ItemStack) dataInput.readObject();
            }
        } catch (Exception e) {
            Utils.logWarning("Failed to deserialize material: " + e.getMessage());
            return new ItemStack(Material.NAME_TAG);
        }
    }
    
    private String serializeMaterial(ItemStack material) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeObject(material);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            Utils.logError("Error serializing material", e);
            throw new RuntimeException("Failed to serialize material", e);
        }
    }
    
    @Override
    public boolean addTag(Tag tag) {
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
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            Utils.logError("Error adding tag to database", e);
            return false;
        }
    }
    
    @Override
    public boolean deleteTag(String tagName) {
        String query = "DELETE FROM tags WHERE name = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, tagName);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            Utils.logError("Error deleting tag from database", e);
            return false;
        }
    }
    
    @Override
    public boolean editTagAttribute(String tagName, String attribute, String newValue) {
        if (!Utils.isValidAttribute(attribute)) {
            Utils.logWarning("Invalid attribute name attempted: " + attribute);
            return false;
        }
        
        String query;
        switch(attribute.toLowerCase()) {
            case "name": query = "UPDATE tags SET name = ? WHERE name = ?"; break;
            case "display": query = "UPDATE tags SET display = ? WHERE name = ?"; break;
            case "type": query = "UPDATE tags SET type = ? WHERE name = ?"; break;
            case "public": query = "UPDATE tags SET public = ? WHERE name = ?"; break;
            case "color": query = "UPDATE tags SET color = ? WHERE name = ?"; break;
            case "material": query = "UPDATE tags SET material = ? WHERE name = ?"; break;
            case "weight": query = "UPDATE tags SET weight = ? WHERE name = ?"; break;
            default: return false;
        }
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, newValue);
            statement.setString(2, tagName);
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            Utils.logError("Error updating tag attribute", e);
            return false;
        }
    }
    
    @Override
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
            Utils.logError("Error getting tag name by display", e);
        }
        
        return null;
    }
    
    @Override
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
            Utils.logError("Error getting tag display by name", e);
        }
        
        return null;
    }
    
    @Override
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
            Utils.logError("Error counting custom tags", e);
        }
        
        return 0;
    }
    
    @Override
    public boolean purgeTagsTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            
            statement.executeUpdate("DROP TABLE IF EXISTS tags");
            statement.executeUpdate(
                "CREATE TABLE `tags` (" +
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
            Utils.logError("Error purging tags table", e);
            return false;
        }
    }
    
    // RequestRepository implementation
    @Override
    public List<CustomTagRequest> getAllRequests() {
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
            Utils.logError("Error retrieving custom tag requests", e);
        }
        
        return requests;
    }
    
    @Override
    public CustomTagRequest getRequestByPlayerName(String playerName) {
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
            Utils.logError("Error getting custom tag request by player name", e);
        }
        
        return null;
    }
    
    @Override
    public boolean createRequest(UUID playerUuid, String playerName, String tagDisplay) {
        boolean exists = requestExistsForPlayer(playerUuid);
        
        if (exists) {
            return updateCustomTagRequest(playerUuid, playerName, tagDisplay);
        } else {
            String query = "INSERT INTO tag_requests (player_uuid, player_name, tag_display) VALUES (?, ?, ?)";
            
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {
                
                statement.setString(1, playerUuid.toString());
                statement.setString(2, playerName);
                statement.setString(3, tagDisplay);
                
                return statement.executeUpdate() > 0;
            } catch (SQLException e) {
                Utils.logError("Error creating custom tag request", e);
                return false;
            }
        }
    }
    
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
            Utils.logError("Error checking if request exists for player", e);
        }
        
        return false;
    }
    
    private boolean updateCustomTagRequest(UUID playerUuid, String playerName, String tagDisplay) {
        String query = "UPDATE tag_requests SET player_name = ?, tag_display = ? WHERE player_uuid = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, playerName);
            statement.setString(2, tagDisplay);
            statement.setString(3, playerUuid.toString());
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            Utils.logError("Error updating custom tag request", e);
            return false;
        }
    }
    
    @Override
    public boolean removeRequest(int requestId) {
        String query = "DELETE FROM tag_requests WHERE id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setInt(1, requestId);
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            Utils.logError("Error removing custom tag request", e);
            return false;
        }
    }
    
    @Override
    public boolean hasPendingRequests() {
        String query = "SELECT COUNT(*) FROM tag_requests";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            
            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }
        } catch (SQLException e) {
            Utils.logError("Error checking for pending requests", e);
        }
        
        return false;
    }
    
    @Override
    public boolean purgeRequestsTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            
            statement.executeUpdate("DROP TABLE IF EXISTS tag_requests");
            statement.executeUpdate(
                "CREATE TABLE `tag_requests` (" +
                "`id` INT AUTO_INCREMENT PRIMARY KEY," +
                "`player_uuid` VARCHAR(36) NOT NULL," +
                "`player_name` VARCHAR(255) NOT NULL," +
                "`tag_display` VARCHAR(255) NOT NULL);"
            );
            
            return true;
        } catch (SQLException e) {
            Utils.logError("Error purging requests table", e);
            return false;
        }
    }
    
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection closed");
        }
    }
    
    public void logPoolStatus() {
        logger.info("Connection Pool - Active: " + dataSource.getHikariPoolMXBean().getActiveConnections() +
                   ", Idle: " + dataSource.getHikariPoolMXBean().getIdleConnections() +
                   ", Total: " + dataSource.getHikariPoolMXBean().getTotalConnections());
    }
}
