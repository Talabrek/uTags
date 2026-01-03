package com.blockworlds.utags;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
// Removed incorrect import: import net.luckperms.api.cacheddata.MetaData;
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

import com.blockworlds.utags.TagColorMenuManager;
import java.util.concurrent.ConcurrentHashMap;
import com.zaxxer.hikari.HikariDataSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import com.zaxxer.hikari.HikariConfig;
import java.util.concurrent.CompletableFuture;

public class uTags extends JavaPlugin {

    // Removed host, port, database, username, password - now read from env vars
    private String defaultTag;
    private LuckPerms luckPerms;
    private TagMenuManager tagMenuManager;
    private HikariDataSource dataSource;
    private AdminMenuManager adminMenuManager;
    private TagColorMenuManager tagColorMenuManager;
    private NameColorMenuManager nameColorMenuManager; // Added for Name Color GUI

    private final Map<UUID, String> previewTags = Collections.synchronizedMap(new HashMap<>());

    // Stores player-specific color preferences for tags
    // Key: Player UUID, Value: Map<TagName, PreferenceObject>
    private final Map<UUID, Map<String, PlayerTagColorPreference>> playerColorPreferences = new ConcurrentHashMap<>(); // For tag-specific colors
    // Stores player-specific name color preferences (&a, &c, etc., or null for default)
    private final Map<UUID, String> playerNameColorPreferences = new ConcurrentHashMap<>();
    // Stores the NAME of the prefix tag currently applied to the player
    public final Map<UUID, String> playerAppliedPrefixTagName = new ConcurrentHashMap<>(); // Made public for LoginListener access
    // Stores player preference for showing all public tags vs. only permitted ones
    private final Map<UUID, Boolean> showAllPublicTagsPreference = new ConcurrentHashMap<>(); // Default: false (show permitted)

    @Override
    public void onEnable() {
        setupTagColorMenuManager();
        setupNameColorMenuManager(); // Added for Name Color GUI
        setupLuckPerms();
        // LuckPerms listener registration is now handled within setupLuckPerms()
        // Initialize managers before registering listeners that depend on them
        setupTagMenuManager();
        setupAdminMenuManager();
        registerCommandsAndEvents(); // Now managers are not null

        loadConfig();
        setupDatabase();
        updateDatabaseSchema();
        cleanupInvalidMaterials();
        loadAllPlayerNameColorsAsync(); // Load name colors on startup
    }

    // --- Constants ---
    public static final int TAG_PREFIX_PRIORITY = 10000; // Priority for uTags prefixes
    public static final int NAME_COLOR_SUFFIX_PRIORITY = 100; // Priority for uTags name color suffixes

    @Override
    public void onDisable() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            getLogger().info("Database connection pool closed.");
        }
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    private void setupLuckPerms() {
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            luckPerms = LuckPermsProvider.get();
            // Register the LuckPerms listener *after* successfully getting the API instance
            // new LuckPermsListener(this, luckPerms); // Instantiate to register with LuckPerms event bus - Disabled to prevent feedback loop
        } else {
            getLogger().warning("LuckPerms not found! Cannot register listener. Disabling uTags...");
            getServer().getPluginManager().disablePlugin(this);
        }
    }


    // Listener registration removed

    public void registerCommandsAndEvents() { // Made public for testing
        TagCommand tagCommand = new TagCommand(this);
        // Existing Listeners
        getServer().getPluginManager().registerEvents(new TagColorMenuListener(this, this.tagColorMenuManager), this);
        getServer().getPluginManager().registerEvents(new TagMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new RequestMenuClickListener(this), this);
        getServer().getPluginManager().registerEvents(new TagCommandPreviewListener(this), this);
        getServer().getPluginManager().registerEvents(new LoginListener(this), this);
        getServer().getPluginManager().registerEvents(new AdminMenuListener(this, this.adminMenuManager), this);
        // Added for Name Color GUI
        getServer().getPluginManager().registerEvents(new NameColorMenuListener(this), this);

        // Existing Commands
        getCommand("tag").setExecutor(tagCommand);
        getCommand("tag").setTabCompleter(tagCommand);
        // Added for Name Color GUI
        getCommand("name").setExecutor(new NameCommand(this, this.nameColorMenuManager)); // Changed command name and class

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


    private void setupAdminMenuManager() {
        if (this.tagMenuManager == null) {
            // Ensure TagMenuManager is initialized first
            setupTagMenuManager();
        }
        this.adminMenuManager = new AdminMenuManager(this, this.tagMenuManager);
    }

    private void setupTagColorMenuManager() {
        this.tagColorMenuManager = new TagColorMenuManager(this);
    }

    // Added for Name Color GUI
    private void setupNameColorMenuManager() {
        this.nameColorMenuManager = new NameColorMenuManager(this);
    }


    private void loadConfig() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        defaultTag = config.getString("default-tag");
    }

    private void setupDatabase() {
        try {
            // Configure HikariCP
            HikariConfig config = new HikariConfig();
            // Check for explicit driver/url (for H2 testing or other DBs)
            String driverClassName = getConfig().getString("database.driverClassName");
            String jdbcUrl = getConfig().getString("database.jdbcUrl");
            // Read database credentials from config.yml
            String dbHost = getConfig().getString("database.host", "localhost");
            int dbPort = getConfig().getInt("database.port", 3306);
            String dbName = getConfig().getString("database.database", "utags");
            String dbUser = getConfig().getString("database.username", "user");
            String dbPass = getConfig().getString("database.password", "password");

            // Log the credentials being used (excluding password for security)
            getLogger().info("Connecting to database: " + dbHost + ":" + dbPort + "/" + dbName + " as user: " + dbUser);


            if (driverClassName != null && !driverClassName.isEmpty() && jdbcUrl != null && !jdbcUrl.isEmpty()) {
                // Use explicit driver and URL if provided
                config.setDriverClassName(driverClassName);
                config.setJdbcUrl(jdbcUrl);
                getLogger().info("Using custom JDBC driver and URL from config.");
            } else {
                // Construct MySQL URL using config values
                config.setJdbcUrl("jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName + "?autoReconnect=true&useSSL=false");
                getLogger().info("Using MySQL JDBC URL constructed from config.yml values.");
            }

            // Set common pool properties using config values
            config.setUsername(dbUser);
            config.setPassword(dbPass);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(5);
            config.setConnectionTimeout(30000); // 30 seconds
            config.setIdleTimeout(600000); // 10 minutes
            config.setMaxLifetime(1800000); // 30 minutes
            
            // Initialize the connection pool
            dataSource = new HikariDataSource(config);
            getLogger().info("Database connection pool initialized successfully");
            
            // Create tables if they don't exist
            try (Connection connection = dataSource.getConnection()) {
                createTagsTableIfNotExists(connection);
                getLogger().info("Database tables verified/created successfully");
            }
        } catch (SQLException e) {
            getLogger().severe("Error setting up the MySQL database: " + e.getMessage());
            getLogger().severe("Please check your database configuration in config.yml");
            // Log more details but don't crash the plugin
            e.printStackTrace();
            
            // Disable the plugin gracefully
            getLogger().severe("Disabling uTags plugin due to database connection failure");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void createTagsTableIfNotExists(Connection connection) throws SQLException {
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

           // Add player_preferences table for name colors
           statement.executeUpdate("CREATE TABLE IF NOT EXISTS `player_preferences` ("
                   + "`player_uuid` VARCHAR(36) PRIMARY KEY NOT NULL,"
                   + "`name_color_code` VARCHAR(2) NULL" // Allow NULL for reset/default
                   + ");");
           // Add player_tag_color_preferences table
           statement.executeUpdate("CREATE TABLE IF NOT EXISTS `player_tag_color_preferences` (" +
                   "`player_uuid` VARCHAR(36) NOT NULL," +
                   "`tag_name` VARCHAR(255) NOT NULL," +
                   "`bracket_color_code` VARCHAR(2) NULL," +
                   "`content_color_code` VARCHAR(2) NULL," +
                   "PRIMARY KEY (`player_uuid`, `tag_name`)" +
                   ");");
       }
    }

    /**
     * Gets a database connection from the connection pool.
     * The connection must be closed by the caller after use.
     *
     * @return A database connection from the pool
     * @throws SQLException if a database access error occurs
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            getLogger().severe("Database connection pool is closed or not initialized!");
            throw new SQLException("Database connection pool is not available");
        }
        
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            getLogger().severe("Failed to get database connection: " + e.getMessage());
            throw e;
        }
    }

    public String getDefaultTag() {
        return defaultTag;
    }

    public TagMenuManager getTagMenuManager() {
        return tagMenuManager;
    }



    public TagColorMenuManager getTagColorMenuManager() {
        return tagColorMenuManager;
    }

    public AdminMenuManager getAdminMenuManager() {
        return adminMenuManager;
    }

    // Added for Name Color GUI access
    public NameColorMenuManager getNameColorMenuManager() {
        return nameColorMenuManager;
    }


    // --- Tag Visibility Preference Methods ---

    /**
     * Gets the player's preference for showing all public tags.
     * Defaults to false (showing only permitted tags) if no preference is set.
     *
     * @param playerUuid The UUID of the player.
     * @return true if the player prefers to see all public tags, false otherwise.
     */
    public boolean getShowAllPublicTagsPreference(UUID playerUuid) {
        return showAllPublicTagsPreference.getOrDefault(playerUuid, false);
    }

    /**
     * Toggles the player's preference for showing all public tags.
     * If the preference was true, it becomes false, and vice versa.
     *
     * @param playerUuid The UUID of the player whose preference to toggle.
     */
    public void toggleShowAllPublicTagsPreference(UUID playerUuid) {
        showAllPublicTagsPreference.compute(playerUuid, (uuid, currentPref) -> currentPref == null ? true : !currentPref);
        // Optional: Log the change
        // getLogger().info("Toggled showAllPublicTagsPreference for " + playerUuid + " to: " + getShowAllPublicTagsPreference(playerUuid));
    }

    // Removed unused stubs getPlayerDataConfig and savePlayerDataConfig



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
        
        // Handle case-insensitive enum values
        String typeStr = resultSet.getString("type").toUpperCase();
        TagType type;
        try {
            type = TagType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid tag type found in database: " + typeStr + ". Defaulting to PREFIX.");
            type = TagType.PREFIX;
        }
        
        boolean isPublic = resultSet.getBoolean("public");
        boolean color = resultSet.getBoolean("color");
        ItemStack material = deserializeMaterial(resultSet.getString("material"));
        int weight = resultSet.getInt("weight");

        return new Tag(name, display, type, isPublic, color, material, weight);
    }

    // Removed commented-out code
    public void cleanupInvalidMaterials() {
        getLogger().info("Checking for invalid player head materials...");
        
        // First, verify the table structure to make sure we have the right columns
        List<String> columns = new ArrayList<>();
        try (Connection conn = getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, "tags", null)) {
            
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
            
            getLogger().info("Found columns: " + String.join(", ", columns));
        } catch (SQLException e) {
            getLogger().severe("Error checking table structure: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        
        // Determine which column to use for the WHERE clause based on what's available
        String idColumn = columns.contains("id") ? "id" : "name";
        getLogger().info("Using column '" + idColumn + "' as identifier for updates");
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + idColumn + ", name, material FROM tags")) {
            
            int cleanedCount = 0;
            
            while (rs.next()) {
                String identifier = rs.getString(idColumn);
                String tagName = rs.getString("name");
                String materialData = rs.getString("material");
                
                boolean needsRepair = false;
                
                try {
                    // Try to deserialize to test if it's valid
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(materialData));
                    try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                        dataInput.readObject();
                    }
                } catch (Exception e) {
                    // If any exception occurs during deserialization, mark for repair
                    getLogger().warning("Error deserializing material for tag '" + tagName + "': " + e.getMessage());
                    needsRepair = true;
                }
                
                if (needsRepair) {
                    getLogger().warning("Repairing invalid material for tag '" + tagName + "'");
                    
                    // Create a replacement item
                    ItemStack replacementItem = new ItemStack(Material.NAME_TAG);
                    String serialized;
                    
                    try {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                            dataOutput.writeObject(replacementItem);
                        }
                        serialized = Base64.getEncoder().encodeToString(outputStream.toByteArray());
                        
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE tags SET material = ? WHERE " + idColumn + " = ?")) {
                            ps.setString(1, serialized);
                            ps.setString(2, identifier);
                            int updated = ps.executeUpdate();
                            
                            if (updated > 0) {
                                cleanedCount++;
                                getLogger().info("Successfully repaired tag '" + tagName + "'");
                            } else {
                                getLogger().warning("Failed to repair tag '" + tagName + "', no rows updated");
                            }
                        }
                    } catch (Exception e) {
                        getLogger().severe("Error while trying to repair tag '" + tagName + "': " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            
            if (cleanedCount > 0) {
                getLogger().info("Repaired " + cleanedCount + " invalid material entries.");
            } else {
                getLogger().info("No invalid material entries found or repaired.");
            }
        } catch (SQLException e) {
            getLogger().severe("Error cleaning up invalid materials: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ItemStack deserializeMaterial(String base64Material) {
        if (base64Material == null || base64Material.isEmpty()) {
            getLogger().warning("Empty or null material data encountered, using default material.");
            return new ItemStack(Material.NAME_TAG);
        }
        
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64Material));
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                return (ItemStack) dataInput.readObject();
            }
        } catch (Exception e) {
            getLogger().warning("Error deserializing material: " + e.getMessage());
            
            // Debug information
            if (base64Material != null) {
                getLogger().info("Base64 length: " + base64Material.length());
                getLogger().info("Base64 prefix: " + 
                    (base64Material.length() > 20 ? base64Material.substring(0, 20) + "..." : base64Material));
            }
            
            // Return a default item based on the error
            if (e.toString().contains("invalid characters") || e.toString().contains("Head Database")) {
                // For player head errors
                return new ItemStack(Material.NAME_TAG);
            } else {
                // For other errors, use NAME_TAG as a fallback
                return new ItemStack(Material.NAME_TAG);
            }
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

    public String serializeMaterial(ItemStack material) { // Changed to public
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
                "`type` ENUM('prefix', 'suffix', 'both') NOT NULL," +
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

    /**
     * Asynchronously creates or updates a custom tag request in the database.
     *
     * @param player     The player making the request.
     * @param tagDisplay The requested tag display string.
     * @return A CompletableFuture that completes when the operation is finished.
     */
    public CompletableFuture<Void> createCustomTagRequestAsync(Player player, String tagDisplay) {
        // Basic synchronous validation
        if (tagDisplay == null || tagDisplay.trim().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Tag display cannot be empty.");
            return CompletableFuture.completedFuture(null); // Complete immediately
        }

        // Simple trim based on ']' - Keep original logic for now
        int endIndex = tagDisplay.indexOf(']') + 1;
        if (endIndex > 0 && endIndex < tagDisplay.length()) { // Ensure endIndex is valid and not the whole string
            tagDisplay = tagDisplay.substring(0, endIndex);
        }
        // Further validation could be added here if needed

        // Use final variables for lambda
        final String finalTagDisplay = tagDisplay.trim(); // Trim before using
        final String playerUUID = player.getUniqueId().toString();
        final String playerName = player.getName(); // Capture name now

        if (finalTagDisplay.isEmpty()) {
             player.sendMessage(ChatColor.RED + "Tag display became empty after trimming.");
             return CompletableFuture.completedFuture(null);
        }

        // Run database operations asynchronously
        return CompletableFuture.runAsync(() -> {
            boolean updateFailed = false;
            boolean insertFailed = false;
            boolean checkFailed = false;
            boolean updated = false;

            try (Connection connection = getConnection();
                 PreparedStatement checkExistingRequest = connection.prepareStatement(
                         "SELECT id FROM tag_requests WHERE player_uuid = ? LIMIT 1")) { // Optimized query

                checkExistingRequest.setString(1, playerUUID);
                ResultSet resultSet = checkExistingRequest.executeQuery();

                if (resultSet.next()) {
                    // Request exists, update it
                    try (PreparedStatement updateRequest = connection.prepareStatement(
                            "UPDATE tag_requests SET player_name = ?, tag_display = ? WHERE player_uuid = ?")) {
                        updateRequest.setString(1, playerName);
                        updateRequest.setString(2, finalTagDisplay);
                        updateRequest.setString(3, playerUUID);
                        updateRequest.executeUpdate();
                        updated = true; // Mark as updated
                    } catch (SQLException e) {
                        getLogger().severe("Error updating tag request for " + playerName + ": " + e.getMessage());
                        e.printStackTrace();
                        updateFailed = true;
                    }
                } else {
                    // Request doesn't exist, insert it
                    try (PreparedStatement insertRequest = connection.prepareStatement(
                            "INSERT INTO tag_requests (player_uuid, player_name, tag_display) VALUES (?, ?, ?)")) {
                        insertRequest.setString(1, playerUUID);
                        insertRequest.setString(2, playerName);
                        insertRequest.setString(3, finalTagDisplay);
                        insertRequest.executeUpdate();
                        // No need for 'updated' flag here, success is implied if no exception
                    } catch (SQLException e) {
                        getLogger().severe("Error inserting tag request for " + playerName + ": " + e.getMessage());
                        e.printStackTrace();
                        insertFailed = true;
                    }
                }
            } catch (SQLException e) {
                getLogger().severe("Error checking existing tag request for " + playerName + ": " + e.getMessage());
                e.printStackTrace();
                checkFailed = true;
            }

            // Send feedback to the player back on the main thread
            final boolean finalCheckFailed = checkFailed;
            final boolean finalUpdateFailed = updateFailed;
            final boolean finalInsertFailed = insertFailed;
            final boolean finalUpdated = updated;

            Bukkit.getScheduler().runTask(this, () -> {
                if (finalCheckFailed) {
                    player.sendMessage(ChatColor.RED + "An error occurred while checking for existing requests.");
                } else if (finalUpdateFailed) {
                    player.sendMessage(ChatColor.RED + "An error occurred while updating your tag request.");
                } else if (finalInsertFailed) {
                    player.sendMessage(ChatColor.RED + "An error occurred while submitting your tag request.");
                } else if (finalUpdated) {
                    player.sendMessage(ChatColor.GREEN + "Your existing tag request has been updated with the new one!");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Your tag request has been submitted!");
                }
            });
        });
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
            // Get count once to avoid race condition between permission and tag creation
            int customTagNumber = countCustomTags(request.getPlayerName()) + 1;
            String tagName = request.getPlayerName() + customTagNumber;
            String permission = "utags.tag." + tagName;
            // Add the new tag to the tags table
            addTagToDatabase(new Tag(tagName, request.getTagDisplay(), TagType.PREFIX, false, false, new ItemStack(Material.PLAYER_HEAD), 1));

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
        // Ensure minimum size of 9 (1 row) and maximum of 54 (6 rows)
        // Also ensure we have enough space for all requests
        int rows = Math.max(1, (int) Math.ceil(requests.size() / 9.0));
        rows = Math.min(rows, 6);  // Maximum 6 rows in a chest GUI
        int size = rows * 9;       // Size must be a multiple of 9
        
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
        // List of allowed attribute names to prevent SQL injection
        Set<String> allowedAttributes = new HashSet<>(Arrays.asList("name", "display", "type", "public", "color", "material", "weight"));

        // Verify the attribute is valid
        if (!allowedAttributes.contains(attribute.toLowerCase())) {
            return false;
        }

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM tags WHERE name = ?")) {
            statement.setString(1, tagName);
            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()) {
                return false;
            }

            // Use prepared statements with the validated attribute name
            String sql = "";
            switch(attribute.toLowerCase()) {
                case "name": sql = "UPDATE tags SET name = ? WHERE name = ?"; break;
                case "display": sql = "UPDATE tags SET display = ? WHERE name = ?"; break;
                case "type": sql = "UPDATE tags SET type = ? WHERE name = ?"; break;
                case "public": sql = "UPDATE tags SET public = ? WHERE name = ?"; break;
                case "color": sql = "UPDATE tags SET color = ? WHERE name = ?"; break;
                case "material": sql = "UPDATE tags SET material = ? WHERE name = ?"; break;
                case "weight": sql = "UPDATE tags SET weight = ? WHERE name = ?"; break;
                default: return false; // This shouldn't happen due to the validation above
            }

            try (PreparedStatement updateStatement = connection.prepareStatement(sql)) {
                // Set the appropriate type based on the attribute
                switch(attribute.toLowerCase()) {
                    case "weight":
                        try {
                            updateStatement.setInt(1, Integer.parseInt(newValue));
                        } catch (NumberFormatException e) {
                            getLogger().warning("Invalid integer value for weight: " + newValue);
                            return false;
                        }
                        break;
                    case "public":
                    case "color":
                        updateStatement.setBoolean(1, Boolean.parseBoolean(newValue));
                        break;
                    case "type":
                        // Validate enum value
                        String upperValue = newValue.toUpperCase();
                        if (!upperValue.equals("PREFIX") && !upperValue.equals("SUFFIX") && !upperValue.equals("BOTH")) {
                            getLogger().warning("Invalid type value: " + newValue + ". Must be PREFIX, SUFFIX, or BOTH.");
                            return false;
                        }
                        updateStatement.setString(1, upperValue.toLowerCase());
                        break;
                    default:
                        updateStatement.setString(1, newValue);
                        break;
                }
                updateStatement.setString(2, tagName);
                updateStatement.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            getLogger().severe("Error updating tag attribute: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Formats a tag display string based on player's color preferences.
     * Assumes display format like "[content]".
     *
     * @param originalDisplay The original tag display string (e.g., "&a[VIP]").
     * @param preference      The player's color preference object for this tag.
     * @return The formatted string with custom colors applied, or the original string if no preferences are set.
     */
    public String formatTagDisplayWithColor(String originalDisplay, PlayerTagColorPreference preference) {
        if (preference == null || preference.isDefault()) {
            return originalDisplay; // No custom colors set
        }

        ChatColor bracketColor = preference.getBracketColor();
        ChatColor contentColor = preference.getContentColor();

        // Find the positions of the brackets
        int openBracketIndex = originalDisplay.indexOf('[');
        int closeBracketIndex = originalDisplay.indexOf(']');

        // If brackets aren't found, return original (or handle error)
        if (openBracketIndex == -1 || closeBracketIndex == -1 || openBracketIndex >= closeBracketIndex) {
            getLogger().warning("Could not parse brackets in tag display: " + originalDisplay);
            return originalDisplay;
        }

        String prefix = originalDisplay.substring(0, openBracketIndex); // Colors before the opening bracket
        String content = originalDisplay.substring(openBracketIndex + 1, closeBracketIndex);
        String suffix = originalDisplay.substring(closeBracketIndex + 1); // Text/colors after the closing bracket

        StringBuilder formatted = new StringBuilder();

        // Apply prefix colors
        formatted.append(prefix);

        // Apply bracket color
        if (bracketColor != null) {
            formatted.append(bracketColor).append('[');
        } else {
            // Need to re-apply prefix colors if bracket color is default
            formatted.append(ChatColor.getLastColors(prefix)).append('[');
        }

        // Apply content color
        if (contentColor != null) {
            // Append content with its new color
            formatted.append(contentColor).append(content);
        } else {
            // If content color is default, try to determine the original content color
             String originalInside = originalDisplay.substring(openBracketIndex + 1, closeBracketIndex);
             String lastColorInside = ChatColor.getLastColors(originalInside);
             if (!lastColorInside.isEmpty()) {
                 // Apply original internal color if found
                 formatted.append(lastColorInside).append(content);
             } else {
                 // Fallback: Apply the color that was active just before the content (either prefix or bracket color)
                 String colorBeforeContentStr = ChatColor.getLastColors(prefix);
                 if (bracketColor != null) {
                    colorBeforeContentStr = bracketColor.toString();
                 }
                 formatted.append(colorBeforeContentStr).append(content);
             }
        }

        // Apply bracket color to closing bracket
        if (bracketColor != null) {
            formatted.append(bracketColor).append(']');
        } else {
            // Re-apply prefix colors if bracket color is default
             formatted.append(ChatColor.getLastColors(prefix)).append(']');
        }

        // Append the original suffix directly. Let Minecraft handle color context continuation.
        formatted.append(suffix);

        return formatted.toString();
    }


    public void setPlayerTag(Player player, String tagName, TagType tagType) {
        User user = getLuckPerms().getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            getLogger().warning("[uTags] Could not find LuckPerms user for " + player.getName());
            return;
        }

        // --- Step 1 & 2: Retrieve Current Prefix and Extract Trailing Color (Synchronously) ---
        String extractedNameColor = "§f"; // Default to white
        Node existingPrefixNodeToRemove = null; // Store the node to remove later

        if (tagType == TagType.PREFIX) { // Only need to do this for prefix changes
            getLogger().fine("[uTags] Searching for existing prefix node with priority " + TAG_PREFIX_PRIORITY + " for " + player.getName());
            for (Node node : user.data().toCollection()) {
                if (node instanceof PrefixNode && ((PrefixNode) node).getPriority() == TAG_PREFIX_PRIORITY) {
                    existingPrefixNodeToRemove = node; // Found the node to remove
                    PrefixNode prefixNode = (PrefixNode) node;
                    String currentPrefixValue = prefixNode.getMetaValue(); // Get the actual prefix string value
                    getLogger().info("[uTags Debug] Retrieved current prefix value: '" + (currentPrefixValue != null ? currentPrefixValue.replace(String.valueOf(ChatColor.COLOR_CHAR), "&") : "null") + "' for " + player.getName());

                    // --- CORRECTED LOGIC: Extract the *last two characters* as potential color code ---
                    extractedNameColor = "§f"; // Default to white

                    if (currentPrefixValue != null && currentPrefixValue.length() >= 2) {
                        String potentialColorCode = currentPrefixValue.substring(currentPrefixValue.length() - 2);
                        char sectionChar = potentialColorCode.charAt(0);
                        char codeChar = potentialColorCode.charAt(1);

                        // Check if it's a valid color/format code using ChatColor.getByChar
                        // Allow both '§' and '&' as the section character for flexibility, but store using '§'
                        if ((sectionChar == ChatColor.COLOR_CHAR || sectionChar == '&') && ChatColor.getByChar(codeChar) != null) {
                            // Construct the valid code using the standard COLOR_CHAR ('§')
                            extractedNameColor = ChatColor.COLOR_CHAR + "" + codeChar;
                            getLogger().info("[uTags Debug] Extracted valid trailing name color code: '" + extractedNameColor.replace(String.valueOf(ChatColor.COLOR_CHAR), "&") + "' for " + player.getName());
                        } else {
                            getLogger().info("[uTags Debug] Last two characters ('" + potentialColorCode.replace(String.valueOf(ChatColor.COLOR_CHAR), "&") + "') are not a valid color/format code for " + player.getName() + ". Using default ('§f').");
                            // extractedNameColor remains "§f" (default)
                        }
                    } else {
                        getLogger().info("[uTags Debug] Current prefix is null or too short (< 2 chars) for " + player.getName() + ". Using default color ('§f').");
                        // extractedNameColor remains "§f"
                    }
                    // --- END CORRECTED LOGIC ---
                    break; // Found the relevant node, exit loop
                }
            }
             if (existingPrefixNodeToRemove == null) {
                 getLogger().info("[uTags Debug] No existing uTags prefix node found for " + player.getName() + ". Using default color ('§f').");
                 // extractedNameColor remains "§f" (default)
             }
        }
            // Debug log moved inside the corrected logic block above.
        // Make the extracted color effectively final for use in lambda
        final String finalExtractedNameColor = extractedNameColor;
        final Node finalExistingPrefixNodeToRemove = existingPrefixNodeToRemove; // Also make the node effectively final

        // --- Step 3: Fetch the display string for the *new* tag asynchronously ---
        getTagDisplayByNameAsync(tagName).thenAcceptAsync(newTagBaseDisplay -> { // Renamed variable for clarity
            if (newTagBaseDisplay == null || newTagBaseDisplay.isEmpty()) {
                getLogger().warning("[uTags] Could not find display string for tag: " + tagName);
                Bukkit.getScheduler().runTask(this, () -> player.sendMessage(ChatColor.RED + "Error applying tag: Could not find tag display."));
                return;
            }
            getLogger().info("[uTags Debug] Retrieved new tag display string: '" + newTagBaseDisplay.replace(String.valueOf(ChatColor.COLOR_CHAR), "&") + "' for " + player.getName());


            if (tagType == TagType.PREFIX) {
                // --- Step 4 & 5: Combine new tag display with extracted color and Update LuckPerms ---

                // Apply Player-Specific Tag Colors to the new tag's base display
                PlayerTagColorPreference colorPref = getPlayerTagColorPreference(player.getUniqueId(), tagName);
                String colorFormattedNewTagDisplay = formatTagDisplayWithColor(newTagBaseDisplay, colorPref);
                getLogger().info("[uTags Debug] Color-formatted new tag string: '" + colorFormattedNewTagDisplay.replace(String.valueOf(ChatColor.COLOR_CHAR), "&") + "' for " + player.getName());

                // Combine the *color-formatted* new tag display with the *extracted* name color
                String finalPrefixValue = colorFormattedNewTagDisplay + finalExtractedNameColor;
                getLogger().info("[uTags Debug] Final combined prefix string: '" + finalPrefixValue.replace(String.valueOf(ChatColor.COLOR_CHAR), "&") + "' for " + player.getName());

                // Remove the existing uTags prefix node (if found) - Use the effectively final variable
                if (finalExistingPrefixNodeToRemove != null) {
                    user.data().remove(finalExistingPrefixNodeToRemove);
                    getLogger().fine("[uTags] Removed existing uTags prefix node for " + player.getName());
                } else {
                    getLogger().fine("[uTags] No existing uTags prefix node found to remove for " + player.getName());
                }

                // Add the new uTags prefix node
                user.data().add(PrefixNode.builder(finalPrefixValue, TAG_PREFIX_PRIORITY).build());
                getLogger().fine("[uTags] Added new uTags prefix node for " + player.getName());

                // Store the applied tag NAME for later color updates
                playerAppliedPrefixTagName.put(player.getUniqueId(), tagName);

                // --- Step 6: Trigger Refresh (Save user and update display name) ---
                // The save logic below handles the refresh

                // [This entire block was moved and refactored above, before the async call and inside the new logic]

                // [This logic was moved above]

                // Save user data and schedule refresh
                getLuckPerms().getUserManager().saveUser(user)
                    .exceptionally(ex -> {
                        getLogger().severe("Failed to save user " + player.getName() + " in setPlayerTag (prefix): " + ex.getMessage());
                        // ex.printStackTrace(); // Optionally uncomment for full stack trace
                        return null; // Required for exceptionally
                    })
                    .thenRunAsync(() -> {
                    // Existing callback logic...
                    getLogger().info("[uTags Debug] saveUser callback started for " + player.getName());
                    getLogger().fine("[uTags] setPlayerTag (prefix) save complete for " + player.getName() + ". Scheduling display name update.");

                    // Directly schedule the final display name update on the main thread
                    Bukkit.getScheduler().runTask(this, () -> {
                        try {
                            // Get the LuckPerms user again inside the task to ensure we have the latest reference
                            User currentUser = luckPerms.getUserManager().getUser(player.getUniqueId());
                            if (currentUser == null) {
                                getLogger().warning("[uTags] Could not find LuckPerms user for " + player.getName() + " during display name update task.");
                                return;
                            }

            // [Code removed from here and moved earlier]
                            // Get prefix and suffix from the user's cached data
                            String prefix = currentUser.getCachedData().getMetaData().getPrefix();
                            String suffix = currentUser.getCachedData().getMetaData().getSuffix();
                            String name = player.getName();
                            String reset = ChatColor.RESET.toString(); // Ensure reset code is included

                            // Construct the final display name
                            // Note: Name color is implicitly handled by LuckPerms suffix/prefix if configured correctly.
                            // We just assemble the parts provided by the cached metadata.
                            String finalDisplayName = (prefix != null ? ChatColor.translateAlternateColorCodes('&', prefix) : "") +
                                                      name + // Base name, color should come from prefix/suffix
                                                      (suffix != null ? ChatColor.translateAlternateColorCodes('&', suffix) : "") +
                                                      reset; // Add reset at the end

                            getLogger().fine("[uTags] Constructed display name in update task for " + name + ": " + finalDisplayName.replace(String.valueOf(ChatColor.COLOR_CHAR), "&"));

                            // Set the display name and list name
                            player.setDisplayName(finalDisplayName);
                            player.setPlayerListName(finalDisplayName); // Update list name too

                            getLogger().info("[uTags] Successfully updated display name for " + name + " via scheduled task after prefix set.");

                        } catch (Exception e) {
                            getLogger().severe("[uTags] Failed during scheduled display name update for " + player.getName() + " after prefix set: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });

                }, runnable -> Bukkit.getScheduler().runTask(this, runnable)); // Ensure Bukkit tasks run on main thread

            } else if (tagType == TagType.SUFFIX) {
                // Suffix logic: Use the newTagBaseDisplay directly
                user.data().clear(NodeType.SUFFIX.predicate()); // Clear existing suffixes first
                user.data().add(SuffixNode.builder(newTagBaseDisplay, 10000).build()); // Use the fetched display string

                // Save the user data asynchronously (Suffix doesn't need the special prefix handling)
                getLuckPerms().getUserManager().saveUser(user)
                    .exceptionally(ex -> {
                        getLogger().severe("Failed to save user " + player.getName() + " in setPlayerTag (suffix): " + ex.getMessage());
                        // ex.printStackTrace(); // Optionally uncomment for full stack trace
                        return null; // Required for exceptionally
                    })
                    .thenRunAsync(() -> {
                    // Existing callback logic...
                    getLogger().info("[uTags Debug] saveUser callback started for " + player.getName());
                    getLogger().fine("[uTags] setPlayerTag (suffix) save complete for " + player.getName() + ". Scheduling display name update.");

                    // Load the user again to get the most up-to-date data after save
                    // Directly schedule the final display name update on the main thread
                    // No need to reload/invalidate here, the previous saveUser should make the data available
                    // in the subsequent task if LuckPerms caching works as expected.
                    // If issues arise, uncomment the loadUser/invalidate logic.
                    // luckPerms.getUserManager().loadUser(player.getUniqueId()).thenAcceptAsync(userToRecalc -> { ... });
                    Bukkit.getScheduler().runTask(this, () -> {
                        try {
                             // Get the LuckPerms user again inside the task
                             User currentUser = luckPerms.getUserManager().getUser(player.getUniqueId());
                             if (currentUser == null) {
                                 getLogger().warning("[uTags] Could not find LuckPerms user for " + player.getName() + " during display name update task (suffix).");
                                 return;
                             }

                            // Get prefix and suffix from the user's cached data
                            String prefix = currentUser.getCachedData().getMetaData().getPrefix();
                            String suffix = currentUser.getCachedData().getMetaData().getSuffix(); // Should reflect the new suffix
                            String name = player.getName();
                            String reset = ChatColor.RESET.toString();

                            // Construct the final display name
                            String finalDisplayName = (prefix != null ? ChatColor.translateAlternateColorCodes('&', prefix) : "") +
                                                      name + // Base name
                                                      (suffix != null ? ChatColor.translateAlternateColorCodes('&', suffix) : "") +
                                                      reset; // Add reset at the end

                            getLogger().fine("[uTags] Constructed display name in update task for " + name + " (suffix): " + finalDisplayName.replace(String.valueOf(ChatColor.COLOR_CHAR), "&"));

                            // Set the display name and list name
                            player.setDisplayName(finalDisplayName);
                            player.setPlayerListName(finalDisplayName); // Update list name too

                            getLogger().info("[uTags] Successfully updated display name for " + name + " via scheduled task after suffix set.");

                        } catch (Exception e) {
                            getLogger().severe("[uTags] Failed during scheduled display name update for " + player.getName() + " after suffix set: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });

                // [This logic was replaced by the direct Bukkit task scheduling above]

                }, runnable -> Bukkit.getScheduler().runTask(this, runnable)); // Ensure Bukkit tasks run on main thread
            }

            // Remove this entire redundant save block

        }, runnable -> Bukkit.getScheduler().runTask(this, runnable)); // Ensure Bukkit tasks run on main thread
    }

    /**
     * Asynchronously retrieves the internal tag name based on its display value.
     *
     * @param display The display value of the tag.
     * @return A CompletableFuture containing the internal tag name, or null if not found or an error occurs.
     */
    public CompletableFuture<String> getTagNameByDisplayAsync(String display) {
        return CompletableFuture.supplyAsync(() -> {
            String tagName = null;
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT name FROM tags WHERE display = ? LIMIT 1;")) { // Optimized query
                statement.setString(1, display);
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    tagName = resultSet.getString("name");
                }
            } catch (SQLException e) {
                getLogger().severe("Error fetching tag name by display '" + display + "': " + e.getMessage());
                e.printStackTrace(); // Log the stack trace for debugging
                // Optionally, complete exceptionally: future.completeExceptionally(e);
                // For simplicity here, we return null on error.
            }
            return tagName;
        });
    }

    /**
     * Asynchronously retrieves the display value of a tag based on its internal name.
     *
     * @param name The internal name of the tag.
     * @return A CompletableFuture containing the display value, or null if not found or an error occurs.
     */
    public CompletableFuture<String> getTagDisplayByNameAsync(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String tagDisplay = null;
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT display FROM tags WHERE name = ? LIMIT 1;")) { // Optimized query
                statement.setString(1, name);
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    tagDisplay = resultSet.getString("display");
                }
            } catch (SQLException e) {
                getLogger().severe("Error fetching tag display by name '" + name + "': " + e.getMessage());
                e.printStackTrace(); // Log the stack trace for debugging
                // Optionally, complete exceptionally: future.completeExceptionally(e);
                // For simplicity here, we return null on error.
            }
            return tagDisplay;
        });
}

    /**
     * Synchronously retrieves the display value of a tag based on its internal name.
     * Use getTagDisplayByNameAsync for better performance when possible.
     *
     * @param name The internal name of the tag.
     * @return The display value, or null if not found or an error occurs.
     */
    public String getTagDisplayByName(String name) {
        String tagDisplay = null;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT display FROM tags WHERE name = ? LIMIT 1;")) {
            statement.setString(1, name);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                tagDisplay = resultSet.getString("display");
            }
        } catch (SQLException e) {
            getLogger().severe("Error fetching tag display synchronously by name '" + name + "': " + e.getMessage());
            e.printStackTrace();
        }
        return tagDisplay;
    }


    // Keep the synchronous version for now if needed elsewhere, or remove if fully replaced
    // public String getTagDisplayByName(String name) { ... }


    /**
     * Retrieves a Tag object from the database based on its internal name.
     * Returns null if the tag is not found or an error occurs.
     */
    public Tag getTagByName(String name) {
        Tag tag = null;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM tags WHERE name = ?")) {

            statement.setString(1, name);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                tag = createTagFromResultSet(resultSet); // Reuse existing creation logic
            }
        } catch (SQLException e) {
            getLogger().severe("Error retrieving tag by name '" + name + "': " + e.getMessage());
            e.printStackTrace();
        }
        return tag;
    }

    public void addPreviewTag(Player player, String tag) {
        previewTags.put(player.getUniqueId(), tag);
    }

    public Map<UUID, String> getPreviewTags()
    {
        return previewTags;
    }


    /**
     * Gets the specific color preference for a player and tag.
     * If no preference exists, it returns a default preference object (with null colors).
     *
     * @param playerUuid The UUID of the player.
     * @param tagName    The internal name of the tag.
     * @return The PlayerTagColorPreference object, never null.
     */
    public PlayerTagColorPreference getPlayerTagColorPreference(UUID playerUuid, String tagName) {
        return playerColorPreferences
                .computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(tagName, k -> new PlayerTagColorPreference(playerUuid, tagName));
    }

    /**
     * Sets the custom color preference for a player and a specific tag.
     * Using null for a color means reverting that part (bracket or content) to the tag's default color.
     * If both colors are null, the preference entry might be removed for cleanup (optional optimization).
     *
     * @param playerUuid   The UUID of the player.
     * @param tagName      The internal name of the tag.
     * @param bracketColor The desired ChatColor for the brackets, or null to use default.
     * @param contentColor The desired ChatColor for the content, or null to use default.
     */
    public void setPlayerTagColor(UUID playerUuid, String tagName, ChatColor bracketColor, ChatColor contentColor) {
        // 1. Update in-memory map (immediate feedback)
        Map<String, PlayerTagColorPreference> playerPrefs = playerColorPreferences
                .computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());

        boolean removedInMemory = false;
        if (bracketColor == null && contentColor == null) {
            // Remove from memory if both colors are null (reset)
            playerPrefs.remove(tagName);
            if (playerPrefs.isEmpty()) {
                playerColorPreferences.remove(playerUuid);
            }
            removedInMemory = true;
        } else {
            // Update or create preference in memory
            PlayerTagColorPreference pref = playerPrefs
                    .computeIfAbsent(tagName, k -> new PlayerTagColorPreference(playerUuid, tagName));
            pref.setBracketColor(bracketColor);
            pref.setContentColor(contentColor);
        }

        // --- Immediate LuckPerms Update if Wearing This Tag ---
        String appliedTagName = playerAppliedPrefixTagName.get(playerUuid);
        if (appliedTagName != null && appliedTagName.equals(tagName)) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                getLogger().info("[uTags] Player " + player.getName() + " is wearing the tag being color-modified (" + tagName + "). Updating LuckPerms prefix.");

                // Get the newly set preference (could be null if reset)
                PlayerTagColorPreference newColorPref = playerPrefs.get(tagName);

                // Perform the update asynchronously
                CompletableFuture.runAsync(() -> {
                    User user = luckPerms.getUserManager().getUser(playerUuid);
                    if (user == null) {
                        getLogger().warning("Could not load LuckPerms user for immediate tag color update: " + player.getName());
                        return;
                    }

                    // Fetch the base display string for the tag
                    String baseTagDisplay = getTagDisplayByName(tagName);
                    if (baseTagDisplay == null) {
                         getLogger().warning("Could not find base display for tag '" + tagName + "' during color update for " + player.getName());
                         return;
                    }

                    // --- Find existing uTags prefix node and extract trailing color ---
                    String preservedTrailingColorCode = "§f"; // Default to white (§f)
                    Node existingPrefixNodeToRemove = null;

                    for (Node node : user.data().toCollection()) {
                        if (node instanceof PrefixNode && ((PrefixNode) node).getPriority() == TAG_PREFIX_PRIORITY) {
                            existingPrefixNodeToRemove = node;
                            PrefixNode prefixNode = (PrefixNode) node;
                            String nodeKey = prefixNode.getKey();
                            String prefixKeyPrefix = "prefix." + TAG_PREFIX_PRIORITY + ".";
                            String existingValue = null;
                            if (nodeKey.startsWith(prefixKeyPrefix)) {
                                existingValue = nodeKey.substring(prefixKeyPrefix.length());
                            } else {
                                 getLogger().warning("[uTags] Found prefix node with correct priority, but key format unexpected: " + nodeKey);
                            }

                           if (existingValue != null && !existingValue.isEmpty()) {
                               int lastColorIndex = existingValue.lastIndexOf(ChatColor.COLOR_CHAR);
                               if (lastColorIndex != -1 && lastColorIndex < existingValue.length() - 1) {
                                   char codeChar = existingValue.charAt(lastColorIndex + 1);
                                   if ("0123456789abcdefklmnor".indexOf(Character.toLowerCase(codeChar)) != -1) {
                                       preservedTrailingColorCode = existingValue.substring(lastColorIndex, lastColorIndex + 2);
                                   }
                               }
                           }
                            getLogger().fine("[uTags Debug] Extracted trailing color code during color update: '" + preservedTrailingColorCode.replace(String.valueOf(ChatColor.COLOR_CHAR), "&") + "' for " + player.getName());
                            break;
                        }
                    }

                    // --- Remove the existing uTags prefix node (if found) ---
                    if (existingPrefixNodeToRemove != null) {
                        user.data().remove(existingPrefixNodeToRemove);
                        getLogger().fine("[uTags] Removed existing uTags prefix node for " + player.getName() + " during color update.");
                    }

                    // --- Format tag with NEW colors and combine with preserved name color ---
                    String colorFormattedTagDisplay = formatTagDisplayWithColor(baseTagDisplay, newColorPref); // Use the new preference
                    String finalPrefixValue = colorFormattedTagDisplay + preservedTrailingColorCode;
                    getLogger().info("[uTags Debug] Final combined prefix string during color update: '" + finalPrefixValue.replace(String.valueOf(ChatColor.COLOR_CHAR), "&") + "' for " + player.getName());

                    // --- Add the new uTags prefix node ---
                    user.data().add(PrefixNode.builder(finalPrefixValue, TAG_PREFIX_PRIORITY).build());

                    // --- Save user data and schedule refresh ---
                    luckPerms.getUserManager().saveUser(user)
                        .exceptionally(ex -> {
                            getLogger().severe("Failed to save user " + player.getName() + " after immediate tag color update: " + ex.getMessage());
                            return null;
                        })
                        .thenRunAsync(() -> {
                            getLogger().fine("LuckPerms save complete for " + player.getName() + " after tag color update. Triggering display name refresh.");
                            // Use the existing refresh mechanism
                            refreshBukkitDisplayName(player);
                        }, runnable -> Bukkit.getScheduler().runTask(this, runnable)); // Ensure Bukkit tasks run on main thread

                }); // End CompletableFuture.runAsync for LuckPerms update
            } else {
                 getLogger().warning("[uTags] Player " + playerUuid + " is wearing tag " + tagName + " but is offline. Skipping immediate LuckPerms update.");
            }
        } else {
             getLogger().fine("[uTags] Player " + playerUuid + " is not wearing the tag being color-modified (" + tagName + "). Skipping immediate LuckPerms update.");
        }

        // 2. Persist change to database asynchronously (This runs regardless of whether the tag was worn)
        final boolean finalRemovedInMemory = removedInMemory; // Need final variable for lambda
        CompletableFuture.runAsync(() -> {
            String bracketCode = (bracketColor != null) ? "&" + bracketColor.getChar() : null;
            String contentCode = (contentColor != null) ? "&" + contentColor.getChar() : null;

            try (Connection conn = getConnection()) {
                if (finalRemovedInMemory) {
                    // Delete the row if colors were reset in memory
                    String deleteSql = "DELETE FROM player_tag_color_preferences WHERE player_uuid = ? AND tag_name = ?";
                    try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, tagName);
                        int rowsAffected = ps.executeUpdate();
                        if (rowsAffected > 0) {
                            getLogger().fine("Removed tag color preference from DB for " + playerUuid + ", tag: " + tagName);
                        }
                    }
                } else {
                    // Insert or update the row
                    String upsertSql = "INSERT INTO player_tag_color_preferences (player_uuid, tag_name, bracket_color_code, content_color_code) " +
                                       "VALUES (?, ?, ?, ?) " +
                                       "ON DUPLICATE KEY UPDATE bracket_color_code = VALUES(bracket_color_code), content_color_code = VALUES(content_color_code)";
                    try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, tagName);
                        // Handle nulls for SQL
                        if (bracketCode != null) ps.setString(3, bracketCode); else ps.setNull(3, Types.VARCHAR);
                        if (contentCode != null) ps.setString(4, contentCode); else ps.setNull(4, Types.VARCHAR);

                        ps.executeUpdate();
                        getLogger().fine("Saved tag color preference to DB for " + playerUuid + ", tag: " + tagName);
                    }
                }
            } catch (SQLException e) {
                getLogger().severe("Failed to save player tag color preference to database for " + playerUuid + ", tag: " + tagName + ". Error: " + e.getMessage());
                e.printStackTrace(); // Log stack trace for debugging
            }
        });
    }

    /**
     * Resets the color preference for a specific player and tag back to the default.
     *
     * @param playerUuid The UUID of the player.
     * @param tagName    The internal name of the tag.
     */
    public void resetPlayerTagColor(UUID playerUuid, String tagName) {
        setPlayerTagColor(playerUuid, tagName, null, null); // Setting both to null effectively resets
    }

    // --- Name Color Preference Methods ---

    /**
     * Asynchronously loads all player name color preferences from the database into the cache.
     * Should be called on plugin enable.
     */
    private void loadAllPlayerNameColorsAsync() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT player_uuid, name_color_code FROM player_preferences");
                 ResultSet rs = ps.executeQuery()) {

                int count = 0;
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                        String colorCode = rs.getString("name_color_code"); // Can be null
                        playerNameColorPreferences.put(uuid, colorCode);
                        count++;
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Invalid UUID found in player_preferences table: " + rs.getString("player_uuid"));
                    }
                }
                getLogger().info("Loaded " + count + " player name color preferences.");

            } catch (SQLException e) {
                getLogger().severe("Failed to load player name color preferences: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }


    /**
     * Asynchronously saves a player's name color preference to the database and updates the cache.
     *
     * @param playerUuid The UUID of the player.
     * @param colorCode  The color code string (e.g., "&a") or null to reset.
     */
    public void savePlayerNameColorCode(UUID playerUuid, String colorCode) {
        // Update cache immediately
        String finalColorCode = (colorCode == null || colorCode.equalsIgnoreCase("reset")) ? null : colorCode; // Treat "reset" as null
        if (finalColorCode == null) {
            playerNameColorPreferences.remove(playerUuid);
        } else {
            playerNameColorPreferences.put(playerUuid, finalColorCode);
        }

        // Save to DB asynchronously and then update LuckPerms/display name
        CompletableFuture.runAsync(() -> {
            String sql = "REPLACE INTO player_preferences (player_uuid, name_color_code) VALUES (?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, playerUuid.toString());
                ps.setString(2, finalColorCode); // Use finalColorCode (handles null)
                ps.executeUpdate();

                // DB save successful. Now schedule the display name update on the main thread.
                // updatePlayerDisplayName will handle applying the name color via the suffix node.
                Bukkit.getScheduler().runTask(this, () -> {
                    Player onlinePlayer = Bukkit.getPlayer(playerUuid);
                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        getLogger().fine("Name color preference saved for " + onlinePlayer.getName() + ". Triggering display name update.");
                        updatePlayerDisplayName(onlinePlayer); // Call the method that handles suffix and display name refresh
                    } else {
                         getLogger().fine("Name color preference saved for offline UUID " + playerUuid + ". Display name update skipped.");
                    }
                });

            } catch (SQLException e) {
                getLogger().severe("Failed to save name color preference for " + playerUuid + ": " + e.getMessage());
                e.printStackTrace();
                // Consider reverting cache change or marking as dirty? For now, log the error.
            }
        });
    }

    /**
     * Gets the player's chosen name color from the cache.
     *
     * @param playerUuid The UUID of the player.
     * @return The ChatColor preference, or ChatColor.WHITE if not set or invalid.
     */
    public ChatColor getPlayerNameColor(UUID playerUuid) {
        String colorCode = playerNameColorPreferences.get(playerUuid);

        if (colorCode == null || colorCode.length() != 2 || colorCode.charAt(0) != '&') {
            return ChatColor.WHITE; // Default if not set or invalid format
        }

        ChatColor color = ChatColor.getByChar(colorCode.charAt(1));
        return (color != null && color.isColor()) ? color : ChatColor.WHITE; // Return valid color or default
    }

    /**
     * Gets the internal map storing player name color preferences.
     * Primarily intended for testing or specific internal uses.
     *
     * @return The map of player UUIDs to color codes.
     */
    public Map<UUID, String> getPlayerNameColorPreferencesMap() {
        return playerNameColorPreferences;
    }

    // Cleaned up stray brace and comment



    /**
     * Gets the player's preferred name color code from the cache.
     * Returns null if no preference is set (use default).
     *
     * @param playerUuid The UUID of the player.
     * @return The color code string (e.g., "&a") or null.
     */
    public String getPlayerNameColorCode(UUID playerUuid) {
        return playerNameColorPreferences.get(playerUuid); // Returns null if not found, which is desired
    }


    // --- Message Utility ---

    /**
     * Gets a message from the config.yml, translates color codes, and handles missing keys.
     *
     * @param key The configuration key for the message.
     * @return The formatted message string.
     */
    public String getMessage(String key) {
        String message = getConfig().getString("messages." + key);
        if (message == null) {
            getLogger().warning("Missing message key in config.yml: messages." + key);
            return ChatColor.RED + "Error: Missing message for " + key; // Fallback message
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }


    // --- Display Name Update ---

    /**
     * Updates the player's display name based on their stored name color preference.
     * This should be called on login and after changing the name color.
     * It respects LuckPerms prefixes/suffixes.
     *
     * @param player The player whose display name should be updated.
     */
    public void updatePlayerDisplayName(Player player) {
        // getLogger().info("[uTags Debug] updatePlayerDisplayName called for " + player.getName()); // Optional: Keep if needed
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerUuid = player.getUniqueId();
        String baseName = player.getName(); // Keep base name for potential future use

        // Get the intended name color code from cache FIRST
        String intendedNameColorCode = getPlayerNameColorCode(playerUuid); // e.g., "&a", or null for default
        getLogger().info("[uTags Debug] Intended name color code for " + player.getName() + ": '" + (intendedNameColorCode != null ? intendedNameColorCode : "none") + "'"); // Requirement 6.3

        // Load the LuckPerms user asynchronously for modification
        luckPerms.getUserManager().loadUser(playerUuid).thenAcceptAsync(user -> {
            if (user == null) {
                getLogger().warning("Could not load LuckPerms user for display name update: " + player.getName());
                return;
            }

            // --- Prefix Handling Logic for Name Color ---
            PrefixNode currentTagPrefixNode = null;
            String currentPrefixValue = ""; // Default to empty string if no prefix node found

            // Find the existing prefix node managed by uTags (using TAG_PREFIX_PRIORITY)
            for (Node node : user.getNodes(NodeType.PREFIX)) {
                if (node instanceof PrefixNode) {
                    PrefixNode prefixNode = (PrefixNode) node;
                    if (prefixNode.getPriority() == TAG_PREFIX_PRIORITY) {
                        currentTagPrefixNode = prefixNode;
                        currentPrefixValue = prefixNode.getMetaValue();
                        break; // Found the relevant prefix node
                    }
                }
            }
            // Log retrieved prefix *before* checking if it's empty (Requirement 1a / 2)
            getLogger().info("[uTags Debug] Retrieved existing uTags prefix node value for " + player.getName() + ": '" + currentPrefixValue + "'");

            // Requirement 1a: Check if the retrieved prefix is null or empty. Stop if it is.
            // Note: currentPrefixValue defaults to "" if no node is found, so we check isEmpty().
            // If a node *must* exist for the /name command to work, this logic might need adjustment
            // based on whether an empty prefix node is valid or implies an error state.
            // Assuming for now that an empty/non-existent prefix means we cannot proceed with name color update.
            if (currentPrefixValue.isEmpty()) {
                 getLogger().severe("[uTags Error] Cannot update name color for " + player.getName() + ": Existing uTags prefix node (priority " + TAG_PREFIX_PRIORITY + ") is missing or empty.");
                 // Optionally, attempt to reset the display name if this state is unexpected
                 // Bukkit.getScheduler().runTask(this, () -> refreshBukkitDisplayName(player));
                 return; // Stop processing for this user
            }


            // --- Extract Base Tag Part --- Requirement 1b / 2
            String baseTagPart = null;
            String appliedTagName = playerAppliedPrefixTagName.get(playerUuid); // Check if we know which tag is applied

            if (appliedTagName != null) {
                Tag appliedTag = getTagByName(appliedTagName); // Attempt to fetch the Tag object
                if (appliedTag != null) {
                    // Preferred method: Use the original display string of the applied tag
                    baseTagPart = appliedTag.getDisplay();
                    getLogger().info("[uTags Debug] Extracted base tag part for " + player.getName() + " from applied tag '" + appliedTagName + "': '" + baseTagPart + "'");
                } else {
                    getLogger().warning("[uTags Debug] Could not find tag definition for applied tag name '" + appliedTagName + "' for player " + player.getName() + ". Falling back to parsing current prefix.");
                }
            } else {
                getLogger().warning("[uTags Debug] No applied tag name found in map for player " + player.getName() + ". Falling back to parsing current prefix.");
            }

            // Fallback: If we couldn't get the base tag from the map/DB, parse the current prefix value
            if (baseTagPart == null) {
                 // Find the last color code (& followed by 0-9a-fA-Fk-oK-OrR)
                 int lastColorCodeIndex = -1;
                 for (int i = currentPrefixValue.length() - 2; i >= 0; i--) {
                     if (currentPrefixValue.charAt(i) == '&' && "0123456789abcdefABCDEFkKlLmMnNoOrR".indexOf(currentPrefixValue.charAt(i + 1)) != -1) {
                         lastColorCodeIndex = i;
                         break;
                     }
                 }

                 if (lastColorCodeIndex != -1) {
                     // Base part is everything before the last color code
                     baseTagPart = currentPrefixValue.substring(0, lastColorCodeIndex);
                     getLogger().info("[uTags Debug] Extracted base tag part for " + player.getName() + " using fallback (before last '&'): '" + baseTagPart + "'");
                 } else {
                     // If no color code found at the end, assume the whole prefix is the base tag part
                     // This might happen if the prefix doesn't end with a name color, which could be an issue itself.
                     baseTagPart = currentPrefixValue;
                     getLogger().warning("[uTags Debug] Extracted base tag part for " + player.getName() + " using fallback (no trailing color code found, using full prefix): '" + baseTagPart + "'");
                 }
            }

            // --- Construct New Prefix Value --- Requirement 3
            String newPrefixValue = baseTagPart + (intendedNameColorCode != null ? intendedNameColorCode : ""); // Append color code or nothing if null
            getLogger().info("[uTags Debug] Final combined string being set for " + player.getName() + ": '" + newPrefixValue + "'"); // Requirement 1c / 2

            // Determine if an update to the prefix node is needed
            boolean needsUpdate = false;
            if (currentTagPrefixNode == null && !newPrefixValue.isEmpty()) {
                 needsUpdate = true; // Adding a new prefix node
                 getLogger().fine("Needs update: Adding new prefix node for " + player.getName());
            } else if (currentTagPrefixNode != null && newPrefixValue.isEmpty()) {
                 needsUpdate = true; // Removing the existing prefix node (setting empty prefix)
                 getLogger().fine("Needs update: Removing prefix node for " + player.getName());
            } else if (currentTagPrefixNode != null && !currentPrefixValue.equals(newPrefixValue)) {
                 needsUpdate = true; // Changing the prefix value
                 getLogger().fine("Needs update: Changing prefix node for " + player.getName() + " from '" + currentPrefixValue + "' to '" + newPrefixValue + "'");
            } else {
                 getLogger().fine("No prefix update needed for " + player.getName() + ". Current: '" + currentPrefixValue + "', New: '" + newPrefixValue + "'");
            }


            // Perform prefix node modifications ONLY if needsUpdate is true
            if (needsUpdate) {
                // Remove the old uTags prefix node if it existed --- Requirement 4
                if (currentTagPrefixNode != null) {
                    user.data().remove(currentTagPrefixNode);
                    getLogger().info("[uTags Debug] Removed existing uTags prefix node for " + player.getName()); // Requirement 1d log refinement
                }
                // Add the new prefix node if the combined value is not empty --- Requirement 5
                if (!newPrefixValue.isEmpty()) {
                    PrefixNode newPrefixNode = PrefixNode.builder()
                            .prefix(newPrefixValue)
                            .priority(TAG_PREFIX_PRIORITY) // Use the same priority as tags
                            .build();
                    user.data().add(newPrefixNode);
                    getLogger().info("[uTags Debug] Added new uTags prefix node for " + player.getName() + " with value: '" + newPrefixValue + "'"); // Requirement 1e log refinement
                }

                // Save the user data ONLY if prefix changed
                luckPerms.getUserManager().saveUser(user).thenRunAsync(() -> {
                    getLogger().fine("LuckPerms saveUser complete for " + player.getName() + " after prefix update. Reloading user and updating display name directly.");

                    // Load the user again to get the most up-to-date data after save
                    luckPerms.getUserManager().loadUser(player.getUniqueId()).thenAcceptAsync(userToRecalc -> {
                        getLogger().fine("Invalidating cached data for " + player.getName() + " after prefix update.");
                        // It's crucial to invalidate the cache so the subsequent getMetaData() call reflects the changes
                        userToRecalc.getCachedData().invalidate();

                        // Directly update the display name on the main thread using the recalculated data
                        Bukkit.getScheduler().runTask(this, () -> {
                            try {
                                // Get the calculated prefix and suffix from the reloaded user's metadata
                                String prefix = userToRecalc.getCachedData().getMetaData().getPrefix(); // Get current prefix
                                String suffix = userToRecalc.getCachedData().getMetaData().getSuffix(); // Suffix remains unchanged by this logic

                                // Format the display name
                                String currentPlayerName = player.getName(); // Renamed variable

                                // The prefix now contains the tag AND the name color. The suffix is separate.
                                String formattedName = (prefix != null ? ChatColor.translateAlternateColorCodes('&', prefix) : "") +
                                                       currentPlayerName + // Player name itself doesn't need extra color here
                                                       (suffix != null ? ChatColor.translateAlternateColorCodes('&', suffix) : "");

                                getLogger().fine("Directly setting display name for " + player.getName() + " after prefix update to: " + formattedName);
                                player.setDisplayName(formattedName);
                                player.setPlayerListName(formattedName); // Update list name too
                                getLogger().info("[uTags Debug] Display name update triggered for " + player.getName() + " after saveUser and refresh."); // Requirement 1f / 2

                            } catch (Exception e) {
                                getLogger().severe("Error directly updating display name for " + player.getName() + " after prefix update: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });

                    }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(this, runnable)); // Run user loading/cache invalidation async

                }, runnable -> Bukkit.getScheduler().runTask(this, runnable)); // Ensure Bukkit tasks run on main thread

            } else {
                 // Prefix didn't change. Do nothing here.
                 getLogger().fine("No LuckPerms prefix update needed for " + player.getName() + ". No save/refresh scheduled from updatePlayerDisplayName.");
            }

        }).exceptionally(ex -> {
            getLogger().severe("Error during LuckPerms operations in updatePlayerDisplayName for " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            // Attempt to reset display name on error
            Bukkit.getScheduler().runTask(this, () -> player.setDisplayName(player.getName()));
            return null;
        });
    }

    /**
     * Refreshes the player's Bukkit display name based on current LuckPerms data.
     * Should be called on the main server thread.
     */
    public void refreshBukkitDisplayName(Player player) {
        // This method is now just a trigger, called via Bukkit Scheduler on the main thread.
        // The actual work, including fresh data loading, happens in processDisplayNameRefresh.
        if (player == null || !player.isOnline()) {
            return;
        }
        // Directly call the processing method. It will handle async loading internally.
        processDisplayNameRefresh(player);
    }

    // Helper method to process the refresh logic after user is loaded
    // Helper method to process the refresh logic, now loading data freshly
    private void processDisplayNameRefresh(Player player) {
        if (player == null || !player.isOnline()) {
            getLogger().warning("[uTags Debug] Player is null or offline before starting processDisplayNameRefresh.");
            return; // Player logged out before task could even start
        }

        // Force a fresh load of user data directly from LuckPerms storage
        luckPerms.getUserManager().loadUser(player.getUniqueId()).thenAcceptAsync(loadedUser -> {
            getLogger().info("[uTags Debug] Inside loadUser callback for " + player.getName());
            // --- Start of async block ---
            // Ensure player is still valid *after* the async load completes
            if (loadedUser == null || player == null || !player.isOnline()) {
                getLogger().warning("[uTags Debug] User data loaded, but player " + (player != null ? player.getName() : "UNKNOWN") + " is now null or offline.");
                return;
            }

            // Fetch the prefix AND suffix from the *freshly loaded* user data's metadata
            net.luckperms.api.cacheddata.CachedMetaData metaData = loadedUser.getCachedData().getMetaData();
            String rawPrefixMeta = metaData.getPrefix();
            String suffix = metaData.getSuffix();

            getLogger().info("[uTags Debug] Freshly loaded meta for " + player.getName() + ": Prefix='" + rawPrefixMeta + "', Suffix='" + suffix + "'");
            // Ensure nulls become empty strings
            if (rawPrefixMeta == null) rawPrefixMeta = "";
            if (suffix == null) suffix = "";

            // Apply formatting only if the prefix isn't empty
            // Directly use the raw prefix from the fresh metadata
            String prefix = rawPrefixMeta.isEmpty() ? "" : rawPrefixMeta + " "; // Add space only if prefix exists

            // Construct the final display name: prefix + name + suffix + reset
            String finalDisplayName = prefix + player.getName() + suffix + ChatColor.RESET; // Use ChatColor.RESET

            getLogger().info("[uTags Debug] Processing refresh for " + player.getName() +
                    ". Freshly Loaded Raw Prefix Meta: '" + rawPrefixMeta +
                    "', Prefix: '" + prefix + // Changed variable name
                    "', Freshly Loaded Suffix: '" + suffix +
                    "', Final Name: '" + finalDisplayName + "'");

            // Set the display name on the Bukkit player object - MUST run on main thread
            final String nameToSet = finalDisplayName; // Final variable for lambda
            getLogger().info("[uTags Debug] Scheduling final display name set for " + player.getName());
            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    // Double-check player validity right before setting
                    if (player != null && player.isOnline()) {
                        // --- Requested Debug Logging ---
                        // Log the exact values *before* applying them.
                        // 'nameToSet' contains the final calculated display name string.
                        // We can re-log the components used to build it from the outer scope if needed, but logging the final string is most direct.
                        getLogger().info(String.format("[uTags Debug] Applying Display Name (Main Thread): Player=%s, Final Calculated Name='%s'",
                                player.getName(), nameToSet));
                        // --- End Debug Logging ---

                        // Set the display name and player list name using Bukkit API
                        player.setDisplayName(nameToSet);
                        player.setPlayerListName(nameToSet); // Update tab list name as well

                        getLogger().info("[uTags Debug] Successfully set display name for " + player.getName() + " (main thread) to: " + nameToSet);
                    } else {
                         getLogger().warning("[uTags Debug] Player " + (player != null ? player.getName() : "UNKNOWN") + " became invalid just before setting display name on main thread.");
                    }
                } catch (Exception e) {
                    getLogger().severe("Error setting display name for " + player.getName() + " on main thread: " + e.getMessage());
                     getLogger().severe(String.format("Failed name: '%s'", nameToSet)); // Log the name that caused the error
                    e.printStackTrace();
                }
            });
            // --- End of async block ---

        }, runnable -> Bukkit.getScheduler().runTask(this, runnable)) // Ensure Bukkit tasks run on main thread for the async callback itself
        .exceptionally(ex -> {
             getLogger().severe("Error during async user load in processDisplayNameRefresh for " + player.getName() + ": " + ex.getMessage());
             ex.printStackTrace();
             // Attempt to reset display name on main thread in case of error during load
             Bukkit.getScheduler().runTask(this, () -> {
                 if (player != null && player.isOnline()) {
                    player.setDisplayName(player.getName());
                    player.setPlayerListName(player.getName());
                 }
             });
             return null;
         });
    }

}
