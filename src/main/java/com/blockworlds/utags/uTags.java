package com.blockworlds.utags;

import com.blockworlds.utags.services.AsyncDatabaseService;
import com.blockworlds.utags.services.BatchProcessingService;
import com.blockworlds.utags.services.CacheService;
import com.blockworlds.utags.utils.ErrorHandler;
import com.blockworlds.utags.utils.InventoryOptimizer;
import com.blockworlds.utags.utils.PerformanceMonitor;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Main plugin class for uTags, managing the plugin lifecycle and core functionality.
 */
public class uTags extends JavaPlugin {

    private String defaultTag;
    private LuckPerms luckPerms;
    private TagMenuManager tagMenuManager;
    private DatabaseManager databaseManager;
    private Map<UUID, String> previewTags;
    
    // New performance-related components
    private ErrorHandler errorHandler;
    private PerformanceModule performanceModule;
    private PerformanceMonitor performanceMonitor;

    @Override
    public void onEnable() {
        try {
            setupLuckPerms();
            loadConfig();
            
            // Initialize error handler first for centralized error handling
            errorHandler = new ErrorHandler(this);
            
            // Initialize performance monitor
            performanceMonitor = new PerformanceMonitor(this, errorHandler);
            performanceMonitor.startMonitoring();
            
            // Initialize database manager with proper error handling
            try {
                databaseManager = new DatabaseManager(this);
                
                // Update database schema if needed
                int currentSchemaVersion = getConfig().getInt("database.schema");
                int latestSchemaVersion = 3; // Update this value when the schema changes
                
                if (databaseManager.updateDatabaseSchema(currentSchemaVersion, latestSchemaVersion)) {
                    // Update config with new schema version
                    getConfig().set("database.schema", latestSchemaVersion);
                    saveConfig();
                }
                
                // Initialize performance module after database is ready
                performanceModule = new PerformanceModule(this, errorHandler);
                
                // Database initialization successful, continue plugin setup
                registerCommandsAndEvents();
                setupTagMenuManager();
                
                // Schedule regular performance logging if enabled
                if (getConfig().getBoolean("performance.logging.enabled", true)) {
                    long interval = getConfig().getLong("performance.logging.interval-minutes", 30) * 60 * 20; // Convert to ticks
                    Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::logPerformanceStats, interval, interval);
                }
                
                getLogger().info("uTags has been enabled successfully with performance optimizations!");
            } catch (DatabaseManager.DatabaseInitializationException e) {
                getLogger().severe("Failed to initialize database: " + e.getMessage());
                getLogger().severe("Disabling uTags due to database initialization failure");
                getServer().getPluginManager().disablePlugin(this);
                return;
            } catch (Exception e) {
                getLogger().severe("Unexpected error during database initialization: " + e.getMessage());
                e.printStackTrace();
                getLogger().severe("Disabling uTags due to database initialization failure");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } catch (Exception e) {
            getLogger().severe("Critical error during plugin initialization: " + e.getMessage());
            e.printStackTrace();
            getLogger().severe("Disabling uTags due to initialization failure");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Shut down performance services first to ensure proper data flushing
        if (performanceModule != null) {
            performanceModule.shutdown();
        }
        
        // Stop performance monitoring
        if (performanceMonitor != null) {
            performanceMonitor.stopMonitoring();
        }
        
        // Close database connections
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        getLogger().info("uTags has been disabled!");
    }

    /**
     * Gets the LuckPerms API instance.
     *
     * @return The LuckPerms API instance
     */
    public LuckPerms getLuckPerms() {
        return luckPerms;
    }
    
    /**
     * Gets the database manager.
     *
     * @return The database manager
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    /**
     * Gets the error handler.
     *
     * @return The error handler
     */
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }
    
    /**
     * Gets the performance module.
     *
     * @return The performance module
     */
    public PerformanceModule getPerformanceModule() {
        return performanceModule;
    }
    
    /**
     * Gets the cache service if enabled.
     *
     * @return The cache service, or null if caching is disabled
     */
    public CacheService getCacheService() {
        return performanceModule != null ? performanceModule.getCacheService() : null;
    }
    
    /**
     * Gets the async database service if enabled.
     *
     * @return The async database service, or null if async operations are disabled
     */
    public AsyncDatabaseService getAsyncDatabaseService() {
        return performanceModule != null ? performanceModule.getAsyncDatabaseService() : null;
    }
    
    /**
     * Gets the batch processing service if enabled.
     *
     * @return The batch processing service, or null if batching is disabled
     */
    public BatchProcessingService getBatchProcessingService() {
        return performanceModule != null ? performanceModule.getBatchProcessingService() : null;
    }
    
    /**
     * Gets the inventory optimizer if enabled.
     *
     * @return The inventory optimizer, or null if inventory optimization is disabled
     */
    public InventoryOptimizer getInventoryOptimizer() {
        return performanceModule != null ? performanceModule.getInventoryOptimizer() : null;
    }
    
    /**
     * Gets the performance monitor.
     *
     * @return The performance monitor
     */
    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
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
        
        // Schedule tag request check
        long delay = 5 * 60 * 20; // 5 minutes in ticks (20 ticks per second)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // Use performance monitoring to track execution time
            performanceMonitor.trackOperation("check_tag_requests", () -> {
                // Use cache or async to check tag requests
                if (performanceModule != null && performanceModule.isAsyncEnabled()) {
                    AsyncDatabaseService asyncDb = performanceModule.getAsyncDatabaseService();
                    asyncDb.getCustomTagRequestsAsync().thenAccept(requests -> {
                        if (requests != null && !requests.isEmpty()) {
                            notifyStaffAboutPendingRequests(requests.size());
                        }
                    });
                } else {
                    if (!databaseManager.getCustomTagRequests().isEmpty()) {
                        notifyStaffAboutPendingRequests(databaseManager.getCustomTagRequests().size());
                    }
                }
            });
        }, delay, delay);
    }
    
    /**
     * Notifies staff members about pending tag requests.
     *
     * @param count The number of pending requests
     */
    private void notifyStaffAboutPendingRequests(int count) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("utags.staff")) {
                player.sendMessage(ChatColor.RED + "There are " + count + " pending tag requests. Use " + 
                                  ChatColor.YELLOW + "/tag admin requests" + 
                                  ChatColor.RED + " to check them.");
            }
        }
    }

    /**
     * Checks if there are pending tag requests.
     *
     * @return True if there are pending requests, false otherwise
     */
    public boolean hasPendingTagRequests() {
        // Use cache if available for better performance
        if (performanceModule != null && performanceModule.isCachingEnabled()) {
            CacheService cache = performanceModule.getCacheService();
            CacheService.CacheKey key = CacheService.CacheKey.forCustom("has_pending_requests");
            return cache.getOrLoad(key, () -> !databaseManager.getCustomTagRequests().isEmpty());
        }
        
        List<CustomTagRequest> requests = databaseManager.getCustomTagRequests();
        return requests != null && !requests.isEmpty();
    }
    
    private void setupTagMenuManager() {
        this.tagMenuManager = new TagMenuManager(this);
    }

    private void loadConfig() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        defaultTag = config.getString("default-tag");
        
        // Add default performance settings if not present
        if (!config.contains("performance")) {
            config.createSection("performance");
            
            // Cache settings
            config.set("performance.caching.enabled", true);
            config.set("performance.caching.expiration-seconds", 300);
            config.set("performance.caching.statistics", true);
            
            // Async settings
            config.set("performance.async.enabled", true);
            
            // Batching settings
            config.set("performance.batching.enabled", true);
            config.set("performance.batching.size", 20);
            config.set("performance.batching.interval-seconds", 5);
            config.set("performance.batching.auto-flush", true);
            
            // Inventory optimization settings
            config.set("performance.inventory.optimization", true);
            config.set("performance.inventory.templates", true);
            config.set("performance.inventory.item-pooling", true);
            config.set("performance.inventory.page-cache-size", 10);
            
            // Performance logging
            config.set("performance.logging.enabled", true);
            config.set("performance.logging.interval-minutes", 30);
            config.set("performance.logging.tps-threshold", 18.0);
            
            saveConfig();
        }
    }

    public String getDefaultTag() {
        return defaultTag;
    }

    public TagMenuManager getTagMenuManager() {
        return tagMenuManager;
    }

    /**
     * Gets all available tags of the specified type.
     * Uses caching if enabled for better performance.
     *
     * @param tagType The type of tags to retrieve
     * @return A list of available tags
     */
    public List<Tag> getAvailableTags(TagType tagType) {
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("get_available_tags", () -> {
            // Use cache if available
            if (performanceModule != null && performanceModule.isCachingEnabled()) {
                return performanceModule.getCacheService().getAvailableTags(tagType);
            }
            
            // Fall back to direct database access
            return databaseManager.getAvailableTags(tagType);
        });
    }

    /**
     * Adds a tag to the database.
     * Uses batching if enabled for better performance.
     *
     * @param tag The tag to add
     * @return True if successful, false otherwise
     */
    public boolean addTagToDatabase(Tag tag) {
        if (tag == null) {
            return false;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("add_tag", () -> {
            // Use batch processing if available
            if (performanceModule != null && performanceModule.isBatchingEnabled()) {
                performanceModule.getBatchProcessingService().addTag(tag);
                return true; // Batching doesn't provide immediate result
            }
            
            // Use async if available
            if (performanceModule != null && performanceModule.isAsyncEnabled()) {
                final boolean[] result = new boolean[1];
                performanceModule.getAsyncDatabaseService().addTagToDatabaseAsync(tag, success -> {
                    result[0] = success;
                });
                return result[0];
            }
            
            // Fall back to direct database access
            boolean success = databaseManager.addTagToDatabase(tag);
            
            // Invalidate cache if needed
            if (success && performanceModule != null && performanceModule.isCachingEnabled()) {
                performanceModule.getCacheService().invalidateTag(tag.getName());
            }
            
            return success;
        });
    }

    /**
     * Deletes a tag from the database.
     * Uses batching if enabled for better performance.
     *
     * @param tagName The name of the tag to delete
     * @return True if successful, false otherwise
     */
    public boolean deleteTagFromDatabase(String tagName) {
        if (tagName == null || tagName.isEmpty()) {
            return false;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("delete_tag", () -> {
            // Use batch processing if available
            if (performanceModule != null && performanceModule.isBatchingEnabled()) {
                performanceModule.getBatchProcessingService().deleteTag(tagName);
                return true; // Batching doesn't provide immediate result
            }
            
            // Use async if available
            if (performanceModule != null && performanceModule.isAsyncEnabled()) {
                final boolean[] result = new boolean[1];
                performanceModule.getAsyncDatabaseService().deleteTagFromDatabaseAsync(tagName, success -> {
                    result[0] = success;
                });
                return result[0];
            }
            
            // Fall back to direct database access
            boolean success = databaseManager.deleteTagFromDatabase(tagName);
            
            // Invalidate cache if needed
            if (success && performanceModule != null && performanceModule.isCachingEnabled()) {
                performanceModule.getCacheService().invalidateTag(tagName);
            }
            
            return success;
        });
    }

    /**
     * Purges the tags table.
     * Cannot be batched due to its nature, but can be async.
     *
     * @return True if successful, false otherwise
     */
    public boolean purgeTagsTable() {
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("purge_tags", () -> {
            // Use async if available
            if (performanceModule != null && performanceModule.isAsyncEnabled()) {
                final boolean[] result = new boolean[1];
                performanceModule.getAsyncDatabaseService().purgeTagsTableAsync(success -> {
                    result[0] = success;
                });
                return result[0];
            }
            
            // Fall back to direct database access
            boolean success = databaseManager.purgeTagsTable();
            
            // Invalidate cache if needed
            if (success && performanceModule != null && performanceModule.isCachingEnabled()) {
                performanceModule.getCacheService().invalidateAllTags();
            }
            
            return success;
        });
    }

    /**
     * Purges the requests table.
     * Cannot be batched due to its nature, but can be async.
     *
     * @return True if successful, false otherwise
     */
    public boolean purgeRequestsTable() {
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("purge_requests", () -> {
            // Use async if available
            if (performanceModule != null && performanceModule.isAsyncEnabled()) {
                final boolean[] result = new boolean[1];
                performanceModule.getAsyncDatabaseService().purgeRequestsTableAsync(success -> {
                    result[0] = success;
                });
                return result[0];
            }
            
            // Fall back to direct database access
            return databaseManager.purgeRequestsTable();
        });
    }

    /**
     * Creates a custom tag request.
     * Uses batching if enabled for better performance.
     *
     * @param player The player making the request
     * @param tagDisplay The requested tag display
     * @return True if successful, false otherwise
     */
    public boolean createCustomTagRequest(Player player, String tagDisplay) {
        if (player == null || tagDisplay == null || tagDisplay.isEmpty()) {
            return false;
        }
        
        int endIndex = tagDisplay.indexOf(']') + 1;
        if (endIndex < tagDisplay.length()) {
            tagDisplay = tagDisplay.substring(0, endIndex);
        }
        
        // Use performance monitoring to track execution time
        final String finalTagDisplay = tagDisplay;
        return performanceMonitor.trackOperation("create_request", () -> {
            // Use batch processing if available
            if (performanceModule != null && performanceModule.isBatchingEnabled()) {
                CustomTagRequest request = new CustomTagRequest(
                    0, player.getUniqueId(), player.getName(), finalTagDisplay
                );
                performanceModule.getBatchProcessingService().addCustomTagRequest(request);
                player.sendMessage(ChatColor.GREEN + "Your tag request has been submitted!");
                return true; // Batching doesn't provide immediate result
            }
            
            // Use async if available
            if (performanceModule != null && performanceModule.isAsyncEnabled()) {
                performanceModule.getAsyncDatabaseService().createCustomTagRequestAsync(
                    player.getUniqueId(), player.getName(), finalTagDisplay,
                    success -> {
                        if (success) {
                            player.sendMessage(ChatColor.GREEN + "Your tag request has been submitted!");
                        } else {
                            player.sendMessage(ChatColor.RED + "An error occurred while submitting your tag request.");
                        }
                    }
                );
                return true; // Async doesn't provide immediate result
            }
            
            // Fall back to direct database access
            boolean success = databaseManager.createCustomTagRequest(player.getUniqueId(), player.getName(), finalTagDisplay);
            
            if (success) {
                player.sendMessage(ChatColor.GREEN + "Your tag request has been submitted!");
            } else {
                player.sendMessage(ChatColor.RED + "An error occurred while submitting your tag request.");
            }
            
            return success;
        });
    }

    /**
     * Gets the count of custom tags for a player.
     * Uses caching if enabled for better performance.
     *
     * @param playerName The name of the player
     * @return The number of custom tags
     */
    public int countCustomTags(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return 0;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("count_custom_tags", () -> {
            // Use cache if available
            if (performanceModule != null && performanceModule.isCachingEnabled()) {
                return performanceModule.getCacheService().countCustomTags(playerName);
            }
            
            // Fall back to direct database access
            return databaseManager.countCustomTags(playerName);
        });
    }

    /**
     * Gets all custom tag requests.
     * Uses caching if enabled for better performance.
     *
     * @return A list of custom tag requests
     */
    public List<CustomTagRequest> getCustomTagRequests() {
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("get_requests", () -> {
            // Use cache if available
            if (performanceModule != null && performanceModule.isCachingEnabled()) {
                CacheService.CacheKey key = CacheService.CacheKey.forCustom("tag_requests");
                return performanceModule.getCacheService().getOrLoad(key, () -> databaseManager.getCustomTagRequests());
            }
            
            // Fall back to direct database access
            return databaseManager.getCustomTagRequests();
        });
    }

    /**
     * Gets a custom tag request by player name.
     * Uses caching if enabled for better performance.
     *
     * @param playerName The name of the player
     * @return The custom tag request, or null if not found
     */
    public CustomTagRequest getCustomTagRequestByPlayerName(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return null;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("get_request_by_name", () -> {
            // Use cache if available
            if (performanceModule != null && performanceModule.isCachingEnabled()) {
                CacheService.CacheKey key = CacheService.CacheKey.forCustom("request_" + playerName);
                return performanceModule.getCacheService().getOrLoad(key, () -> databaseManager.getCustomTagRequestByPlayerName(playerName));
            }
            
            // Fall back to direct database access
            return databaseManager.getCustomTagRequestByPlayerName(playerName);
        });
    }

    /**
     * Accepts a custom tag request.
     * Uses async operations if enabled for better performance.
     *
     * @param request The request to accept
     * @return True if successful, false otherwise
     */
    public boolean acceptCustomTagRequest(CustomTagRequest request) {
        if (request == null) {
            return false;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("accept_request", () -> {
            try {
                String permission = "utags.tag." + request.getPlayerName() + (countCustomTags(request.getPlayerName()) + 1);
                
                // Add the new tag to the tags table
                Tag newTag = new Tag(
                    request.getPlayerName() + (countCustomTags(request.getPlayerName()) + 1),
                    request.getTagDisplay(),
                    TagType.PREFIX,
                    false,
                    false,
                    new ItemStack(Material.PLAYER_HEAD),
                    1
                );
                
                // Use batch processing for the tag if available
                if (performanceModule != null && performanceModule.isBatchingEnabled()) {
                    performanceModule.getBatchProcessingService().addTag(newTag);
                } else {
                    addTagToDatabase(newTag);
                }
                
                // Remove the request
                boolean removed;
                if (performanceModule != null && performanceModule.isBatchingEnabled()) {
                    performanceModule.getBatchProcessingService().deleteCustomTagRequest(request.getId());
                    removed = true; // Assume success for batched operations
                } else {
                    removed = databaseManager.removeCustomTagRequest(request.getId());
                }
                
                if (!removed) {
                    errorHandler.logWarning("Failed to remove custom tag request with ID: " + request.getId());
                    return false;
                }
                
                // Add the permission to the player
                getLuckPerms().getUserManager().loadUser(request.getPlayerUuid()).thenAcceptAsync(user -> {
                    user.data().add(Node.builder(permission).build());
                    getLuckPerms().getUserManager().saveUser(user);
                    
                    // Execute the configured command to notify the player
                    String command = getConfig().getString("accept-command", "mail send %player% Your custom tag request has been accepted!");
                    command = command.replace("%player%", request.getPlayerName());
                    String finalCommand = command;
                    Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
                });
                
                // Invalidate cache for the player's tag count
                if (performanceModule != null && performanceModule.isCachingEnabled()) {
                    performanceModule.getCacheService().invalidatePlayerData(request.getPlayerUuid(), request.getPlayerName());
                }
                
                return true;
            } catch (Exception e) {
                errorHandler.logError("Error accepting custom tag request", e);
                return false;
            }
        });
    }

    /**
     * Denies a custom tag request.
     * Uses batching if enabled for better performance.
     *
     * @param request The request to deny
     * @return True if successful, false otherwise
     */
    public boolean denyCustomTagRequest(CustomTagRequest request) {
        if (request == null) {
            return false;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("deny_request", () -> {
            try {
                boolean removed;
                
                // Use batch processing if available
                if (performanceModule != null && performanceModule.isBatchingEnabled()) {
                    performanceModule.getBatchProcessingService().deleteCustomTagRequest(request.getId());
                    removed = true; // Assume success for batched operations
                } else {
                    removed = databaseManager.removeCustomTagRequest(request.getId());
                }
                
                if (removed) {
                    // Execute the configured command to notify the player
                    String command = getConfig().getString("deny-command", "mail send %player% Your custom tag request has been denied.");
                    command = command.replace("%player%", request.getPlayerName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    return true;
                } else {
                    errorHandler.logWarning("Failed to remove custom tag request with ID: " + request.getId());
                    return false;
                }
            } catch (Exception e) {
                errorHandler.logError("Error denying custom tag request", e);
                return false;
            }
        });
    }

    /**
     * Opens the requests menu for a player.
     * Uses optimized inventory handling if enabled.
     *
     * @param player The player to open the menu for
     */
    public void openRequestsMenu(Player player) {
        openRequestsMenu(player, getCustomTagRequests());
    }

    /**
     * Opens the requests menu for a player with specific requests.
     * Uses optimized inventory handling if enabled.
     *
     * @param player The player to open the menu for
     * @param requests The requests to display
     */
    public void openRequestsMenu(Player player, List<CustomTagRequest> requests) {
        // Use performance monitoring to track execution time
        performanceMonitor.trackOperation("open_requests_menu", () -> {
            // Use inventory optimizer if available
            if (performanceModule != null && performanceModule.isInventoryOptimizationEnabled()) {
                InventoryOptimizer optimizer = performanceModule.getInventoryOptimizer();
                
                int size = 9 * (int) Math.ceil(requests.size() / 9.0);
                if (size < 9) size = 9;
                
                Inventory inventory = optimizer.createFramedInventory(
                    size, ChatColor.BLUE + "Custom Tag Requests", player
                );
                
                for (CustomTagRequest request : requests) {
                    ItemStack item = optimizer.createPlayerHead(
                        Bukkit.getPlayer(request.getPlayerUuid()),
                        ChatColor.GREEN + request.getPlayerName(),
                        Arrays.asList(
                            ChatColor.GRAY + "Requested Tag: " + ChatColor.translateAlternateColorCodes('&', request.getTagDisplay()),
                            "",
                            ChatColor.YELLOW + "Left-click to accept",
                            ChatColor.RED + "Right-click to deny"
                        )
                    );
                    inventory.addItem(item);
                }
                
                player.openInventory(inventory);
                return null;
            }
            
            // Fall back to original implementation
            int size = 9 * (int) Math.ceil(requests.size() / 9.0);
            if (size < 9) size = 9;
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
            return null;
        });
    }

    /**
     * Edits a tag attribute in the database.
     * Uses batching if enabled for better performance.
     *
     * @param tagName The name of the tag to edit
     * @param attribute The attribute to edit
     * @param newValue The new value for the attribute
     * @return True if successful, false otherwise
     */
    public boolean editTagAttribute(String tagName, String attribute, String newValue) {
        if (tagName == null || tagName.isEmpty() || attribute == null || attribute.isEmpty()) {
            return false;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("edit_tag_attribute", () -> {
            // Use batch processing if available
            if (performanceModule != null && performanceModule.isBatchingEnabled()) {
                performanceModule.getBatchProcessingService().updateTagAttribute(tagName, attribute, newValue);
                return true; // Batching doesn't provide immediate result
            }
            
            // Use async if available
            if (performanceModule != null && performanceModule.isAsyncEnabled()) {
                final boolean[] result = new boolean[1];
                performanceModule.getAsyncDatabaseService().editTagAttributeAsync(tagName, attribute, newValue, success -> {
                    result[0] = success;
                });
                return result[0];
            }
            
            // Fall back to direct database access
            boolean success = databaseManager.editTagAttribute(tagName, attribute, newValue);
            
            // Invalidate cache if needed
            if (success && performanceModule != null && performanceModule.isCachingEnabled()) {
                performanceModule.getCacheService().invalidateTag(tagName);
            }
            
            return success;
        });
    }

    /**
     * Sets a player's tag.
     * Uses cache invalidation for better consistency.
     *
     * @param player The player to set the tag for
     * @param tagDisplay The display text for the tag
     * @param tagType The type of tag (PREFIX/SUFFIX)
     * @return True if successful, false otherwise
     */
    public boolean setPlayerTag(Player player, String tagDisplay, TagType tagType) {
        if (player == null || tagDisplay == null || tagType == null) {
            return false;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("set_player_tag", () -> {
            User user = getLuckPerms().getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                if (tagType == TagType.PREFIX) {
                    user.data().clear(NodeType.PREFIX.predicate());
                    user.data().add(PrefixNode.builder(tagDisplay, 10000).build());
                } else {
                    user.data().clear(NodeType.SUFFIX.predicate());
                    user.data().add(SuffixNode.builder(tagDisplay, 10000).build());
                }
                getLuckPerms().getUserManager().saveUser(user);
                
                // Invalidate player-related cache entries
                if (performanceModule != null && performanceModule.isCachingEnabled()) {
                    performanceModule.getCacheService().invalidatePlayerData(player.getUniqueId(), player.getName());
                }
                
                return true;
            }
            return false;
        });
    }

    /**
     * Gets a tag's name by its display text.
     * Uses caching if enabled for better performance.
     *
     * @param display The display text
     * @return The tag name, or null if not found
     */
    public String getTagNameByDisplay(String display) {
        if (display == null || display.isEmpty()) {
            return null;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("get_tag_name_by_display", () -> {
            // Use cache if available
            if (performanceModule != null && performanceModule.isCachingEnabled()) {
                return performanceModule.getCacheService().getTagNameByDisplay(display);
            }
            
            // Fall back to direct database access
            return databaseManager.getTagNameByDisplay(display);
        });
    }

    /**
     * Gets a tag's display text by its name.
     * Uses caching if enabled for better performance.
     *
     * @param name The tag name
     * @return The tag display text, or null if not found
     */
    public String getTagDisplayByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("get_tag_display_by_name", () -> {
            // Use cache if available
            if (performanceModule != null && performanceModule.isCachingEnabled()) {
                return performanceModule.getCacheService().getTagDisplayByName(name);
            }
            
            // Fall back to direct database access
            return databaseManager.getTagDisplayByName(name);
        });
    }

    public void addPreviewTag(Player player, String tag) {
        previewTags.put(player.getUniqueId(), tag);
    }

    public Map<UUID, String> getPreviewTags() {
        return previewTags;
    }
    
    /**
     * Logs the current database connection pool status.
     */
    public void logDatabaseStatus() {
        databaseManager.logPoolStatus();
    }
    
    /**
     * Logs performance statistics from all services.
     */
    private void logPerformanceStats() {
        try {
            // Check if TPS is below threshold to avoid unnecessary logging when server is fine
            double currentTps = performanceMonitor.getCurrentTps();
            double tpsThreshold = getConfig().getDouble("performance.logging.tps-threshold", 18.0);
            
            if (currentTps < tpsThreshold) {
                getLogger().warning("Server TPS is " + String.format("%.2f", currentTps) + 
                                 " (below threshold of " + tpsThreshold + "). Performance details:");
                
                // Log detailed statistics when TPS is low
                if (performanceModule != null) {
                    performanceModule.logStatistics();
                }
                
                if (performanceMonitor != null) {
                    getLogger().info(performanceMonitor.getStatistics());
                }
                
                logDatabaseStatus();
            } else {
                // Just log basic statistics when TPS is fine
                getLogger().info("Server performance good. TPS: " + String.format("%.2f", currentTps));
                
                if (performanceMonitor != null) {
                    getLogger().info(performanceMonitor.getBasicStatistics());
                }
            }
        } catch (Exception e) {
            errorHandler.logError("Error logging performance statistics", e);
        }
    }
    
    /**
     * Flushes all batched operations immediately.
     */
    public void flushAllBatches() {
        if (performanceModule != null && performanceModule.isBatchingEnabled()) {
            performanceModule.getBatchProcessingService().processBatches();
        }
    }
}
