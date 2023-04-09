package com.blockworlds.utags;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class uTags extends JavaPlugin {

    private Connection connection;
    private String host, database, username, password;
    private int port;
    private String defaultTag;
    private LuckPerms luckPerms;
    private TagMenuManager tagMenuManager;

    private Map<UUID, String> previewTags;

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
        previewTags = new HashMap<>();
        TagCommand tagCommand = new TagCommand(this);
        getCommand("tag").setExecutor(tagCommand);
        getCommand("tag").setTabCompleter(tagCommand);
        getServer().getPluginManager().registerEvents(new TagMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new RequestMenuClickListener(this), this);
        getServer().getPluginManager().registerEvents(new TagCommandPreviewListener(this), this);
        getServer().getPluginManager().registerEvents(new LoginListener(this), this);
        long delay = 5 * 60 * 20; // 5 minutes in ticks (20 ticks per second)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!getCustomTagRequests().isEmpty()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("utags.staff")) {
                        player.sendMessage(ChatColor.RED + "There are pending tag requests. Use " + ChatColor.YELLOW + "/tag admin requests" + ChatColor.RED + " to check them.");
                    }
                }
            }
        }, delay, delay);
    }

    public boolean hasPendingTagRequests() {
        return getCustomTagRequests() != null && !getCustomTagRequests().isEmpty();
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
                            "`material` MEDIUMTEXT NOT NULL," +
                            "`weight` INT NOT NULL" +
                            ");"
            );
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `tag_requests` ("
                    + "`id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "`player_uuid` VARCHAR(36) NOT NULL,"
                    + "`player_name` VARCHAR(255) NOT NULL,"
                    + "`tag_display` VARCHAR(255) NOT NULL);");
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
            return statement.executeQuery("SELECT * FROM tags WHERE type = 'prefix' OR type = 'both' ORDER BY weight DESC;");
        } else if (tagType == TagType.SUFFIX) {
            return statement.executeQuery("SELECT * FROM tags WHERE type = 'suffix' OR type = 'both' ORDER BY weight DESC;");
        } else {
            return statement.executeQuery("SELECT * FROM tags ORDER BY weight DESC;");
        }
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
        PreparedStatement statement = connection.prepareStatement("REPLACE INTO tags (name, display, type, public, color, material, weight) VALUES (?, ?, ?, ?, ?, ?, ?)");

        statement.setString(1, tag.getName());
        statement.setString(2, tag.getDisplay());
        statement.setString(3, tag.getType().toString());
        statement.setBoolean(4, tag.isPublic());
        statement.setBoolean(5, tag.isColor());
        statement.setString(6, serializeMaterial(tag.getMaterial()));
        statement.setInt(7, tag.getWeight());
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

    public void purgeTagsTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            dropTagsTable(statement);
            recreateTagsTable(statement);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void purgeRequestsTable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            dropRequestsTable(statement);
            recreateRequestsTable(statement);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void dropTagsTable(Statement statement) throws SQLException {
        String dropTableQuery = "DROP TABLE IF EXISTS tags";
        statement.executeUpdate(dropTableQuery);
    }

    private void dropRequestsTable(Statement statement) throws SQLException {
        String dropTableQuery = "DROP TABLE IF EXISTS tag_requests";
        statement.executeUpdate(dropTableQuery);
    }

    private void recreateTagsTable(Statement statement) throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS tags (" +
                "`name` VARCHAR(255) PRIMARY KEY," +
                "`display` VARCHAR(255) NOT NULL," +
                "`type` ENUM('PREFIX', 'SUFFIX', 'BOTH') NOT NULL," +
                "`public` BOOLEAN NOT NULL," +
                "`color` BOOLEAN NOT NULL," +
                "`material` MEDIUMTEXT NOT NULL," +
                "`weight` INT NOT NULL" +
                ");";
        statement.executeUpdate(createTable);
    }

    private void recreateRequestsTable(Statement statement) throws SQLException {
        String createTable = ("CREATE TABLE IF NOT EXISTS `tag_requests` ("
                + "`id` INT AUTO_INCREMENT PRIMARY KEY,"
                + "`player_uuid` VARCHAR(36) NOT NULL,"
                + "`player_name` VARCHAR(255) NOT NULL,"
                + "`tag_display` VARCHAR(255) NOT NULL);");
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

    public void createCustomTagRequest(Player player, String tagDisplay) {

        int endIndex = tagDisplay.indexOf(']') + 1;
        if (endIndex < tagDisplay.length()) {
            tagDisplay = tagDisplay.substring(0, endIndex);
        }

        try (Connection connection = getConnection();
             PreparedStatement checkExistingRequest = connection.prepareStatement(
                     "SELECT * FROM tag_requests WHERE player_uuid = ?")) {

            checkExistingRequest.setString(1, player.getUniqueId().toString());
            ResultSet resultSet = checkExistingRequest.executeQuery();

            if (resultSet.next()) {
                try (PreparedStatement updateRequest = connection.prepareStatement(
                        "UPDATE tag_requests SET player_name = ?, tag_display = ? WHERE player_uuid = ?")) {

                    updateRequest.setString(1, player.getName());
                    updateRequest.setString(2, tagDisplay);
                    updateRequest.setString(3, player.getUniqueId().toString());
                    updateRequest.executeUpdate();

                    player.sendMessage(ChatColor.GREEN + "Your existing tag request has been updated with the new one!");
                } catch (SQLException e) {
                    e.printStackTrace();
                    player.sendMessage(ChatColor.RED + "An error occurred while updating your tag request.");
                    return;
                }
            } else {
                try (PreparedStatement insertRequest = connection.prepareStatement(
                        "INSERT INTO tag_requests (player_uuid, player_name, tag_display) VALUES (?, ?, ?)")) {

                    insertRequest.setString(1, player.getUniqueId().toString());
                    insertRequest.setString(2, player.getName());
                    insertRequest.setString(3, tagDisplay);
                    insertRequest.executeUpdate();

                    player.sendMessage(ChatColor.GREEN + "Your tag request has been submitted!");
                } catch (SQLException e) {
                    e.printStackTrace();
                    player.sendMessage(ChatColor.RED + "An error occurred while submitting your tag request.");
                    return;
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "An error occurred while checking for existing requests.");
            return;
        }
    }

    public int countCustomTags(String playerName) {
        // Count the number of custom tags for a player
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM tags WHERE name LIKE ?")) {

            statement.setString(1, playerName + "%");
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public List<CustomTagRequest> getCustomTagRequests() {
        List<CustomTagRequest> requests = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM tag_requests");
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                String playerName = resultSet.getString("player_name");
                String tagDisplay = resultSet.getString("tag_display");
                requests.add(new CustomTagRequest(id, playerUuid, playerName, tagDisplay));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return requests;
    }

    public CustomTagRequest getCustomTagRequestByPlayerName(String playerName) {
        CustomTagRequest request = null;
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM tag_requests WHERE player_name = ?;");
            statement.setString(1, playerName);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                int id = resultSet.getInt("id");
                UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                String display = resultSet.getString("tag_display");
                request = new CustomTagRequest(id, playerUuid, playerName, display);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return request;
    }

    public void acceptCustomTagRequest(CustomTagRequest request) {
        try (Connection connection = getConnection()) {
            String permission = "utags.tag." + request.getPlayerName() + (countCustomTags(request.getPlayerName()) + 1);
            // Add the new tag to the tags table
            addTagToDatabase(new Tag(request.getPlayerName() + (countCustomTags(request.getPlayerName()) + 1), request.getTagDisplay(), TagType.PREFIX, false, false, new ItemStack(Material.PLAYER_HEAD),1));

            // Remove the request from the tag_requests table
            removeCustomRequestFromDatabase(request);
            getLuckPerms().getUserManager().loadUser(request.getPlayerUuid()).thenAcceptAsync(user -> {
                user.data().add(Node.builder(permission).build());
                getLuckPerms().getUserManager().saveUser(user);

                // Execute the configured command to notify the player
                String command = getConfig().getString("accept-command", "mail send %player% Your custom tag request has been accepted!");
                command = command.replace("%player%", request.getPlayerName());
                String finalCommand = command;
                Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeCustomRequestFromDatabase(CustomTagRequest request) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM tag_requests WHERE id = ?;");
            statement.setInt(1, request.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void denyCustomTagRequest(CustomTagRequest request) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM tag_requests WHERE id = ?;");
            statement.setInt(1, request.getId());
            statement.executeUpdate();
            // Execute the configured command to notify the player
            String command = getConfig().getString("deny-command", "mail send %player% Your custom tag request has been denied.");
            command = command.replace("%player%", request.getPlayerName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void openRequestsMenu(Player player) {
        List<CustomTagRequest> requests = new ArrayList<>();
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM tag_requests;");
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                String playerName = resultSet.getString("player_name");
                String display = resultSet.getString("tag_display");
                requests.add(new CustomTagRequest(id, playerUuid, playerName, display));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Now create the inventory GUI and open it for the player
        openRequestsMenu(player, requests);
    }

    public void openRequestsMenu(Player player, List<CustomTagRequest> requests) {
        int size = 9 * (int) Math.ceil(requests.size() / 9.0);
        if (size < 9)
            size = 9;
        Inventory inventory = Bukkit.createInventory(null, size, ChatColor.BLUE + "Custom Tag Requests");

        for (CustomTagRequest request : requests) {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(request.getPlayerUuid()));
            skullMeta.setDisplayName(ChatColor.GREEN + request.getPlayerName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Requested Tag: " + ChatColor.translateAlternateColorCodes('&', request.getTagDisplay()));
            lore.add("");
            lore.add(ChatColor.YELLOW + "Left-click to accept");
            lore.add(ChatColor.RED + "Right-click to deny");
            skullMeta.setLore(lore);
            item.setItemMeta(skullMeta);
            inventory.addItem(item);
        }

        player.openInventory(inventory);
    }

    public boolean editTagAttribute(String tagName, String attribute, String newValue) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM tags WHERE name = ?")) {
            statement.setString(1, tagName);
            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()) {
                return false;
            }

            if (!attribute.equalsIgnoreCase("name") && !attribute.equalsIgnoreCase("display") && !attribute.equalsIgnoreCase("type") && !attribute.equalsIgnoreCase("public") && !attribute.equalsIgnoreCase("color") && !attribute.equalsIgnoreCase("material")) {
                return false;
            }

            PreparedStatement updateStatement = connection.prepareStatement("UPDATE tags SET " + attribute + " = ? WHERE name = ?");
            updateStatement.setString(1, newValue);
            updateStatement.setString(2, tagName);
            updateStatement.executeUpdate();
            updateStatement.close();

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void setPlayerTag(Player player, String tagName, TagType tagType) {
        User user = getLuckPerms().getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            if (tagType == TagType.PREFIX) {
                user.data().clear(NodeType.PREFIX.predicate());
                user.data().add(PrefixNode.builder(tagName, 10000).build());
            } else {
                user.data().clear(NodeType.SUFFIX.predicate());
                user.data().add(SuffixNode.builder(tagName, 10000).build());
            }
            getLuckPerms().getUserManager().saveUser(user);
        }
    }

    public String getTagNameByDisplay(String display) {
        String tagName = null;
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM tags WHERE display = ?;");
            statement.setString(1, display);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                tagName = resultSet.getString("name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tagName;
    }

    public String getTagDisplayByName(String name) {
        String tagDisplay = null;
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM tags WHERE name = ?;");
            statement.setString(1, name);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                tagDisplay = resultSet.getString("display");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tagDisplay;
    }

    public void addPreviewTag(Player player, String tag) {
        previewTags.put(player.getUniqueId(), tag);
    }

    public Map<UUID, String> getPreviewTags()
    {
        return previewTags;
    }
}