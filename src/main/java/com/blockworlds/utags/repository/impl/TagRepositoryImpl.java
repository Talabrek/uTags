package com.blockworlds.utags.repository.impl;

import com.blockworlds.utags.model.Result;
import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.repository.TagRepository;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

/**
 * Implementation of TagRepository using SQL database.
 */
public class TagRepositoryImpl implements TagRepository {
    // SQL query constants
    private static final String SELECT_TAGS_ALL = "SELECT * FROM tags ORDER BY weight DESC";
    private static final String SELECT_TAGS_PREFIX = "SELECT * FROM tags WHERE type = 'PREFIX' OR type = 'BOTH' ORDER BY weight DESC";
    private static final String SELECT_TAGS_SUFFIX = "SELECT * FROM tags WHERE type = 'SUFFIX' OR type = 'BOTH' ORDER BY weight DESC";
    private static final String INSERT_TAG = "REPLACE INTO tags (name, display, type, public, color, material, weight) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_TAG = "DELETE FROM tags WHERE name = ?";
    private static final String SELECT_TAG_NAME_BY_DISPLAY = "SELECT name FROM tags WHERE display = ?";
    private static final String SELECT_TAG_DISPLAY_BY_NAME = "SELECT display FROM tags WHERE name = ?";
    private static final String COUNT_CUSTOM_TAGS = "SELECT COUNT(*) FROM tags WHERE name LIKE ?";
    private static final String PURGE_TAGS = "TRUNCATE TABLE tags";
    
    private final DatabaseConnector connector;
    private final Logger logger;
    
    /**
     * Creates a new TagRepositoryImpl.
     */
    public TagRepositoryImpl(DatabaseConnector connector, Logger logger) {
        this.connector = connector;
        this.logger = logger;
    }
    
    @Override
    public Result<List<Tag>> getAvailableTags(TagType tagType) {
        List<Tag> tags = new ArrayList<>();
        String query = tagType == TagType.PREFIX ? SELECT_TAGS_PREFIX : 
                      tagType == TagType.SUFFIX ? SELECT_TAGS_SUFFIX : SELECT_TAGS_ALL;
        
        try (Connection conn = connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                tags.add(createTagFromResultSet(rs));
            }
            return Result.success(tags);
        } catch (SQLException e) {
            logger.warning("Error retrieving tags: " + e.getMessage());
            return Result.error("Failed to retrieve tags", e);
        }
    }
    
    @Override
    public Result<Boolean> addTag(Tag tag) {
        try (Connection conn = connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_TAG)) {
            
            stmt.setString(1, tag.getName());
            stmt.setString(2, tag.getDisplay());
            stmt.setString(3, tag.getType().toString());
            stmt.setBoolean(4, tag.isPublic());
            stmt.setBoolean(5, tag.isColor());
            stmt.setString(6, serializeMaterial(tag.getMaterial()));
            stmt.setInt(7, tag.getWeight());
            
            int affected = stmt.executeUpdate();
            return Result.success(affected > 0);
        } catch (SQLException e) {
            logger.warning("Error adding tag: " + e.getMessage());
            return Result.error("Failed to add tag", e);
        }
    }
    
    @Override
    public Result<Boolean> deleteTag(String tagName) {
        try (Connection conn = connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_TAG)) {
            
            stmt.setString(1, tagName);
            
            int affected = stmt.executeUpdate();
            return Result.success(affected > 0);
        } catch (SQLException e) {
            logger.warning("Error deleting tag: " + e.getMessage());
            return Result.error("Failed to delete tag", e);
        }
    }
    
    @Override
    public Result<Boolean> editTagAttribute(String tagName, String attribute, String newValue) {
        // Validate attribute to prevent SQL injection
        if (!isValidAttribute(attribute)) {
            return Result.failure("Invalid attribute name: " + attribute);
        }
        
        String query = "UPDATE tags SET " + attribute + " = ? WHERE name = ?";
        
        try (Connection conn = connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, newValue);
            stmt.setString(2, tagName);
            
            int affected = stmt.executeUpdate();
            return Result.success(affected > 0);
        } catch (SQLException e) {
            logger.warning("Error updating tag attribute: " + e.getMessage());
            return Result.error("Failed to update tag attribute", e);
        }
    }
    
    @Override
    public Result<String> getTagNameByDisplay(String display) {
        try (Connection conn = connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_TAG_NAME_BY_DISPLAY)) {
            
            stmt.setString(1, display);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Result.success(rs.getString("name"));
                } else {
                    return Result.failure("No tag found with display: " + display);
                }
            }
        } catch (SQLException e) {
            logger.warning("Error getting tag name by display: " + e.getMessage());
            return Result.error("Failed to get tag name", e);
        }
    }
    
    @Override
    public Result<String> getTagDisplayByName(String name) {
        try (Connection conn = connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_TAG_DISPLAY_BY_NAME)) {
            
            stmt.setString(1, name);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Result.success(rs.getString("display"));
                } else {
                    return Result.failure("No tag found with name: " + name);
                }
            }
        } catch (SQLException e) {
            logger.warning("Error getting tag display by name: " + e.getMessage());
            return Result.error("Failed to get tag display", e);
        }
    }
    
    @Override
    public Result<Integer> countCustomTags(String playerName) {
        try (Connection conn = connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(COUNT_CUSTOM_TAGS)) {
            
            stmt.setString(1, playerName + "%");
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Result.success(rs.getInt(1));
                } else {
                    return Result.success(0);
                }
            }
        } catch (SQLException e) {
            logger.warning("Error counting custom tags: " + e.getMessage());
            return Result.error("Failed to count custom tags", e);
        }
    }
    
    @Override
    public Result<Boolean> purgeTagsTable() {
        try (Connection conn = connector.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.executeUpdate(PURGE_TAGS);
            return Result.success(true);
        } catch (SQLException e) {
            logger.warning("Error purging tags table: " + e.getMessage());
            return Result.error("Failed to purge tags table", e);
        }
    }
    
    /**
     * Creates a Tag object from a ResultSet row.
     */
    private Tag createTagFromResultSet(ResultSet rs) throws SQLException {
        return new Tag(
            rs.getString("name"),
            rs.getString("display"),
            TagType.valueOf(rs.getString("type")),
            rs.getBoolean("public"),
            rs.getBoolean("color"),
            deserializeMaterial(rs.getString("material")),
            rs.getInt("weight")
        );
    }
    
    /**
     * Serializes an ItemStack to a Base64 string.
     */
    private String serializeMaterial(ItemStack material) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeObject(material);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            logger.warning("Error serializing material: " + e.getMessage());
            throw new RuntimeException("Failed to serialize material", e);
        }
    }
    
    /**
     * Deserializes a Base64 string to an ItemStack.
     */
    private ItemStack deserializeMaterial(String base64Material) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64Material));
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                return (ItemStack) dataInput.readObject();
            }
        } catch (Exception e) {
            logger.warning("Failed to deserialize material: " + e.getMessage());
            return new ItemStack(Material.NAME_TAG);
        }
    }
    
    /**
     * Validates an attribute name to prevent SQL injection.
     */
    private boolean isValidAttribute(String attribute) {
        return attribute != null && (
            attribute.equals("name") ||
            attribute.equals("display") ||
            attribute.equals("type") ||
            attribute.equals("public") ||
            attribute.equals("color") ||
            attribute.equals("material") ||
            attribute.equals("weight")
        );
    }
}
