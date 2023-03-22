package com.blockworlds.utags;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
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
/*
public class uTagsB extends JavaPlugin {

    private Connection connection;
    private String host, database, username, password;
    private int port;
    private String defaultTag;
    private LuckPerms luckPerms;
    private TagMenuManager tagMenuManager;
    @Override
    public void onEnable() {
        // Check if LuckPerms is installed and enabled
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            // Get the LuckPerms API instance
            luckPerms = LuckPermsProvider.get();
        } else {
            getLogger().warning("LuckPerms not found! Disabling uTags...");
            getServer().getPluginManager().disablePlugin(this);
        }
        // Register the tag command and listener
        getCommand("tag").setExecutor(new TagCommand(this));
        getServer().getPluginManager().registerEvents(new TagMenuListener(this), this);
        this.tagMenuManager = new TagMenuManager(this);
        // Load the config.yml file
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        defaultTag = config.getString("default-tag");

        // Set up the database
        setupDatabase();
        updateDatabaseSchema();
    }

    @Override
    public void onDisable() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    private void setupDatabase() {
        String host = getConfig().getString("database.host");
        int port = getConfig().getInt("database.port");
        String database = getConfig().getString("database.database");
        String username = getConfig().getString("database.username");
        String password = getConfig().getString("database.password");

        try {
            Class.forName("com.mysql.jdbc.Driver");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true";
            getLogger().info("Attemping to connect to " + url);
            Connection connection = DriverManager.getConnection(url, username, password);
            setConnection(connection);
            Statement statement = connection.createStatement();
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `tags` (" +
                            "`id` INT AUTO_INCREMENT PRIMARY KEY," +
                            "`name` VARCHAR(255) NOT NULL," +
                            "`display` VARCHAR(255) NOT NULL," +
                            "`type` ENUM('prefix', 'suffix', 'both') NOT NULL," +
                            "`public` BOOLEAN NOT NULL," +
                            "`color` BOOLEAN NOT NULL," +
                            "`material` MEDIUMTEXT NOT NULL" +
                            ");"
            );
            statement.close();
        } catch (ClassNotFoundException | SQLException e) {
            getLogger().severe("Error setting up the MySQL database: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        try {
            if (!connection.isClosed()) {
                return connection;
            }
            else{
                String host = getConfig().getString("database.host");
                int port = getConfig().getInt("database.port");
                String database = getConfig().getString("database.database");
                String username = getConfig().getString("database.username");
                String password = getConfig().getString("database.password");
                try {
                    Class.forName("com.mysql.jdbc.Driver");
                    String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true";
                    connection = DriverManager.getConnection(url, username, password);
                    return connection;
                } catch (ClassNotFoundException | SQLException e) {
                    getLogger().severe("Error setting up the MySQL connection: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public String getDefaultTag() {
        return defaultTag;
    }

    public TagMenuManager getTagMenuManager() {
        return tagMenuManager;
    }

    public List<Tag> getAvailableTags(String tagType) {
        List<Tag> availableTags = new ArrayList<>();
        Connection connection = null;
        Statement statement = null;
        try {
            connection = getConnection();
            statement = connection.createStatement();
            ResultSet resultSet;
            if (tagType.equalsIgnoreCase("PREFIX"))
                resultSet = statement.executeQuery("SELECT * FROM tags WHERE type = 'prefix' OR type = 'both';");
            else if (tagType.equalsIgnoreCase("SUFFIX"))
                resultSet = statement.executeQuery("SELECT * FROM tags WHERE type = 'suffix' OR type = 'both';");
            else
                resultSet = statement.executeQuery("SELECT * FROM tags;");
            while (resultSet.next()) {
                String name = resultSet.getString("name");
                String display = resultSet.getString("display");
                String type = resultSet.getString("type");
                boolean isPublic = resultSet.getBoolean("public");
                boolean color = resultSet.getBoolean("color");
                ItemStack material = null;
                try {
                    String base64Material = resultSet.getString("material");
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64Material));
                    BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
                    material = (ItemStack) dataInput.readObject();
                    dataInput.close();
                } catch (IllegalArgumentException | IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                    // Set a default material if deserialization fails
                    material = new ItemStack(Material.NAME_TAG);
                }
                availableTags.add(new Tag(name, display, type, isPublic, color, material));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return availableTags;
    }

    public void addTagToDatabase(Tag tag) {
        Connection connection = null;
        PreparedStatement statement = null;

        try {
            // Connect to the database and insert the new tag
            connection = getConnection();
            statement = connection.prepareStatement("REPLACE INTO tags (name, display, type, public, color, material) VALUES (?, ?, ?, ?, ?, ?)");

            statement.setString(1, tag.getName());
            statement.setString(2, tag.getDisplay());
            statement.setString(3, tag.getType().toString());
            statement.setBoolean(4, tag.isPublic());
            statement.setBoolean(5, tag.isColor());
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
                dataOutput.writeObject(tag.getMaterial());
                dataOutput.close();
                String base64Material = Base64.getEncoder().encodeToString(outputStream.toByteArray());
                statement.setString(6, base64Material);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            // Close resources manually
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void deleteTagFromDatabase(String tagName) {
        // Connect to the database and delete the tag
        Connection connection = null;
        PreparedStatement statement = null;
        try {
             connection = getConnection();
             statement = connection.prepareStatement("DELETE FROM tags WHERE name = ?");

            statement.setString(1, tagName);

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void purgeTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            // Drop the tags table
            String dropTableQuery = "DROP TABLE IF EXISTS tags";
            statement.executeUpdate(dropTableQuery);

            // Recreate the tags table
            String createTable = "CREATE TABLE IF NOT EXISTS tags (" +
                    "`name` VARCHAR(255) PRIMARY KEY," +
                    "`display` VARCHAR(255) NOT NULL," +
                    "`type` ENUM('PREFIX', 'SUFFIX', 'BOTH') NOT NULL," +
                    "`public` BOOLEAN NOT NULL," +
                    "`color` BOOLEAN NOT NULL," +
                    "`material` MEDIUMTEXT NOT NULL" +
                    ");";
            statement.executeUpdate(createTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void updateDatabaseSchema() {
        int currentSchemaVersion = getConfig().getInt("database.schema");
        int latestSchemaVersion = 3; // Update this value when the schema changes
        getLogger().info("Checking if database schema needs an update...");
        if (currentSchemaVersion < latestSchemaVersion) {
            getLogger().info("Schema version " + currentSchemaVersion + " needs to be updated to version " + latestSchemaVersion);
            try (Connection connection = getConnection()) {
                // Alter the table for each version increment
                for (int i = currentSchemaVersion + 1; i <= latestSchemaVersion; i++) {
                    switch (i) {
                        case 3:
                            // Check if the 'material' column exists
                            DatabaseMetaData metaData = connection.getMetaData();
                            ResultSet resultSet = metaData.getColumns(null, null, "tags", "material");
                            if (!resultSet.next()) {
                                // If the 'material' column doesn't exist, add it with the default value
                                try (Statement statement = connection.createStatement()) {
                                    String alterTable = "ALTER TABLE tags MODIFY COLUMN `material` MEDIUMTEXT NOT NULL;";
                                    statement.executeUpdate(alterTable);
                                }
                            }
                            break;
                        // Add more cases for future schema updates
                    }
                }

                // Update the schema version in the config file
                getConfig().set("database.schema", latestSchemaVersion);
                saveConfig();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}


    /*public List<Tag> getAvailableSuffixes() {
        List<Tag> availableSuffixes = new ArrayList<>();
        Connection connection = null;
        Statement statement = null;
        try {
            connection = getConnection();
            statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT * FROM tags WHERE type = 'suffix' OR type = 'both';");

            while (resultSet.next()) {
                String name = resultSet.getString("name");
                String display = resultSet.getString("display");
                String type = resultSet.getString("type");
                boolean isPublic = resultSet.getBoolean("public");
                boolean color = resultSet.getBoolean("color");
                try {
                    String base64Material = resultSet.getString("material");
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64Material));
                    BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
                    ItemStack material = (ItemStack) dataInput.readObject();
                    dataInput.close();
                    availableSuffixes.add(new Tag(name, display, type, isPublic, color, material));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return availableSuffixes;
    }*/