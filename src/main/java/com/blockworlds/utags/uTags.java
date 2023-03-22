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

public class uTags extends JavaPlugin {

    private Connection connection;
    private String host, database, username, password;
    private int port;
    private String defaultTag;
    private LuckPerms luckPerms;
    private TagMenuManager tagMenuManager;

    @Override
    public void onEnable() {
        setupLuckPerms();
        registerCommandsAndEvents();
        setupTagMenuManager();
        loadConfig();
        setupDatabase();
        updateDatabaseSchema();
    }

    @Override
    public void onDisable() {
        closeDatabaseConnection();
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    private void setupLuckPerms() {
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            luckPerms = LuckPermsProvider.get();
        } else {
            getLogger().warning("LuckPerms not found! Disabling uTags...");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommandsAndEvents() {
        TagCommand tagCommand = new TagCommand(this);
        getCommand("tag").setExecutor(tagCommand);
        getCommand("tag").setTabCompleter(tagCommand);
        getServer().getPluginManager().registerEvents(new TagMenuListener(this), this);
    }

    private void setupTagMenuManager() {
        this.tagMenuManager = new TagMenuManager(this);
    }

    private void loadConfig() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        defaultTag = config.getString("default-tag");
    }

    private void setupDatabase() {
        host = getConfig().getString("database.host");
        port = getConfig().getInt("database.port");
        database = getConfig().getString("database.database");
        username = getConfig().getString("database.username");
        password = getConfig().getString("database.password");

        try {
            Class.forName("com.mysql.jdbc.Driver");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true";
            getLogger().info("Attempting to connect to " + url);
            connection = DriverManager.getConnection(url, username, password);
            createTagsTableIfNotExists();
        } catch (ClassNotFoundException | SQLException e) {
            getLogger().severe("Error setting up the MySQL database: " + e.getMessage());
        }
    }
    private void createTagsTableIfNotExists() throws SQLException {
        try (Statement statement = connection.createStatement()) {
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
        }
    }

    private void closeDatabaseConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (!connection.isClosed()) {
                return connection;
            } else {
                reconnectToDatabase();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return connection;
    }

    private void reconnectToDatabase() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true";
            connection = DriverManager.getConnection(url, username, password);
        } catch (ClassNotFoundException | SQLException e) {
            getLogger().severe("Error setting up the MySQL connection: " + e.getMessage());
        }
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

    public List<Tag> getAvailableTags(TagType tagType) {
        List<Tag> availableTags = new ArrayList<>();

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            ResultSet resultSet = getTagsResultSet(tagType, statement);

            while (resultSet.next()) {
                availableTags.add(createTagFromResultSet(resultSet));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return availableTags;
    }

    private ResultSet getTagsResultSet(TagType tagType, Statement statement) throws SQLException {
        if (tagType == TagType.PREFIX) {
            return statement.executeQuery("SELECT * FROM tags WHERE type = 'prefix' OR type = 'both';");
        } else if (tagType == TagType.SUFFIX) {
            return statement.executeQuery("SELECT * FROM tags WHERE type = 'suffix' OR type = 'both';");
        } else {
            return statement.executeQuery("SELECT * FROM tags;");
        }
    }

    private Tag createTagFromResultSet(ResultSet resultSet) throws SQLException {
        String name = resultSet.getString("name");
        String display = resultSet.getString("display");
        TagType type = TagType.valueOf(resultSet.getString("type"));
        boolean isPublic = resultSet.getBoolean("public");
        boolean color = resultSet.getBoolean("color");
        ItemStack material = deserializeMaterial(resultSet.getString("material"));

        return new Tag(name, display, type, isPublic, color, material);
    }

    private ItemStack deserializeMaterial(String base64Material) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64Material));
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                return (ItemStack) dataInput.readObject();
            }
        } catch (IllegalArgumentException | IOException | ClassNotFoundException e) {
            e.printStackTrace();
            // Set a default material if deserialization fails
            return new ItemStack(Material.NAME_TAG);
        }
    }

    public void addTagToDatabase(Tag tag) {
        try (Connection connection = getConnection();
             PreparedStatement statement = prepareInsertTagStatement(connection, tag)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private PreparedStatement prepareInsertTagStatement(Connection connection, Tag tag) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("REPLACE INTO tags (name, display, type, public, color, material) VALUES (?, ?, ?, ?, ?, ?)");

        statement.setString(1, tag.getName());
        statement.setString(2, tag.getDisplay());
        statement.setString(3, tag.getType().toString());
        statement.setBoolean(4, tag.isPublic());
        statement.setBoolean(5, tag.isColor());
        statement.setString(6, serializeMaterial(tag.getMaterial()));

        return statement;
    }

    private String serializeMaterial(ItemStack material) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeObject(material);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteTagFromDatabase(String tagName) {
        try (Connection connection = getConnection();
             PreparedStatement statement = prepareDeleteTagStatement(connection, tagName)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private PreparedStatement prepareDeleteTagStatement(Connection connection, String tagName) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("DELETE FROM tags WHERE name = ?");
        statement.setString(1, tagName);
        return statement;
    }

    public void purgeTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            dropTagsTable(statement);
            recreateTagsTable(statement);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void dropTagsTable(Statement statement) throws SQLException {
        String dropTableQuery = "DROP TABLE IF EXISTS tags";
        statement.executeUpdate(dropTableQuery);
    }

    private void recreateTagsTable(Statement statement) throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS tags (" +
                "`name` VARCHAR(255) PRIMARY KEY," +
                "`display` VARCHAR(255) NOT NULL," +
                "`type` ENUM('PREFIX', 'SUFFIX', 'BOTH') NOT NULL," +
                "`public` BOOLEAN NOT NULL," +
                "`color` BOOLEAN NOT NULL," +
                "`material` MEDIUMTEXT NOT NULL" +
                ");";
        statement.executeUpdate(createTable);
    }

    public void updateDatabaseSchema() {
        int currentSchemaVersion = getConfig().getInt("database.schema");
        int latestSchemaVersion = 3; // Update this value when the schema changes
        getLogger().info("Checking if database schema needs an update...");

        if (currentSchemaVersion < latestSchemaVersion) {
            getLogger().info("Schema version " + currentSchemaVersion + " needs to be updated to version " + latestSchemaVersion);

            try (Connection connection = getConnection()) {
                updateSchemaVersions(connection, currentSchemaVersion, latestSchemaVersion);
                updateConfigSchemaVersion(latestSchemaVersion);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateSchemaVersions(Connection connection, int currentSchemaVersion, int latestSchemaVersion) throws SQLException {
        for (int i = currentSchemaVersion + 1; i <= latestSchemaVersion; i++) {
            switch (i) {
                case 3:
                    addMaterialColumnIfNotExists(connection);
                    break;
                // Add more cases for future schema updates
            }
        }
    }

    private void addMaterialColumnIfNotExists(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet resultSet = metaData.getColumns(null, null, "tags", "material");
        if (!resultSet.next()) {
            try (Statement statement = connection.createStatement()) {
                String alterTable = "ALTER TABLE tags MODIFY COLUMN `material` MEDIUMTEXT NOT NULL;";
                statement.executeUpdate(alterTable);
            }
        }
    }

    private void updateConfigSchemaVersion(int latestSchemaVersion) {
        getConfig().set("database.schema", latestSchemaVersion);
        saveConfig();
    }
}