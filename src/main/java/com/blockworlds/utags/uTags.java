package com.blockworlds.utags;

import com.blockworlds.utags.services.*;
import com.blockworlds.utags.utils.ErrorHandler;
import com.blockworlds.utags.utils.InventoryOptimizer;
import com.blockworlds.utags.utils.MessageUtils;
import com.blockworlds.utags.utils.PerformanceMonitor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Main plugin class for uTags, now with enhanced security features.
 */
public class uTags extends JavaPlugin {

    private String defaultTag;
    private LuckPerms luckPerms;
    private TagMenuManager tagMenuManager;
    private DatabaseManager databaseManager;
    private Map<UUID, String> previewTags;
    
    // Core Services
    private ErrorHandler errorHandler;
    private PerformanceModule performanceModule;
    private PerformanceMonitor performanceMonitor;
    
    // Security Services
    private SecurityService securityService;
    private ValidationService validationService;
    private ConfigurationManager configurationManager;
    
    // Event Listeners
    private TagMenuListener tagMenuListener;
    private RequestMenuClickListener requestMenuClickListener;
    private TagCommandPreviewListener tagCommandPreviewListener;
    private LoginListener loginListener;

    @Override
    public void onEnable() {
        try {
            // First check if LuckPerms is available (required dependency)
            if (!setupLuckPerms()) {
                getLogger().severe("LuckPerms not found! Disabling uTags...");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            // Initialize error handler for centralized error management
            errorHandler = new ErrorHandler(this);
            
            // Load and validate configurations
            if (!loadConfig()) {
                getLogger().severe("Failed to load configuration! Disabling uTags...");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            // Initialize performance monitor
            performanceMonitor = new PerformanceMonitor(this, errorHandler);
            performanceMonitor.startMonitoring();
            
            // Initialize security services first
            configurationManager = new ConfigurationManager(this, errorHandler);
            securityService = new SecurityService(this, errorHandler);
            
            // Initialize database with proper error handling
            try {
                databaseManager = new DatabaseManager(this);
                
                // Update database schema if needed
                int currentSchemaVersion = getConfig().getInt("database.schema", 0);
                int latestSchemaVersion = 3; // Update this value when the schema changes
                
                if (databaseManager.updateDatabaseSchema(currentSchemaVersion, latestSchemaVersion)) {
                    // Update config with new schema version
                    getConfig().set("database.schema", latestSchemaVersion);
                    saveConfig();
                }
                
                // Initialize validation service after database is ready
                validationService = new ValidationService(this, errorHandler, securityService);
                
                // Initialize performance module
                performanceModule = new PerformanceModule(this, errorHandler);
                
                // Register commands and events with security-enhanced versions
                registerCommandsAndEvents();
                setupTagMenuManager();
                
                // Create security documentation
                configurationManager.createSecureConfigDocumentation();
                
                // Schedule regular performance and security logging
                scheduleLogging();
                
                getLogger().info("uTags has been enabled successfully with security enhancements!");
            } catch (DatabaseManager.DatabaseInitializationException e) {
                getLogger().severe("Failed to initialize database: " + e.getMessage());
                getLogger().severe("Disabling uTags due to database initialization failure");
                getServer().getPluginManager().disablePlugin(this);
                return;
            } catch (Exception e) {
                getLogger().severe("Unexpected error during database initialization: " + e.getMessage());
                e.printStackTrace();
                getLogger().severe("Disabling uTags due to initialization failure");
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
        // Shut down services in the correct order
        
        // Close event listeners to prevent memory leaks
        if (tagMenuListener != null) {
            tagMenuListener.cleanup();
        }
        
        // Shut down performance services
        if (performanceModule != null) {
            performanceModule.shutdown();
        }
        
        // Stop performance monitoring
        if (performanceMonitor != null) {
            performanceMonitor.stopMonitoring();
        }
        
        // Close database connections last
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        getLogger().info("uTags has been disabled!");
    }

    /**
     * Sets up the LuckPerms API.
     * 
     * @return True if LuckPerms is available, false otherwise
     */
    private boolean setupLuckPerms() {
        try {
            if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
                luckPerms = LuckPermsProvider.get();
                return true;
            }
            return false;
        } catch (Exception e) {
            getLogger().severe("Error setting up LuckPerms: " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads and validates plugin configuration.
     * 
     * @return True if configuration is valid, false otherwise
     */
    private boolean loadConfig() {
        try {
            // Ensure config file exists
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                getLogger().info("Creating default configuration file...");
                saveDefaultConfig();
            }
            
            // Load config
            reloadConfig();
            
            // Load default tag
            defaultTag = getConfig().getString("default-tag", "member");
            
            // Add default performance and security settings if not present
            if (!getConfig().contains("performance")) {
                getConfig().createSection("performance");
                
                // Cache settings
                getConfig().set("performance.caching.enabled", true);
                getConfig().set("performance.caching.expiration-seconds", 300);
                getConfig().set("performance.caching.statistics", true);
                
                // Async settings
                getConfig().set("performance.async.enabled", true);
                
                // Batching settings
                getConfig().set("performance.batching.enabled", true);
                getConfig().set("performance.batching.size", 20);
                getConfig().set("performance.batching.interval-seconds", 5);
                getConfig().set("performance.batching.auto-flush", true);
                
                // Inventory optimization settings
                getConfig().set("performance.inventory.optimization", true);
                getConfig().set("performance.inventory.templates", true);
                getConfig().set("performance.inventory.item-pooling", true);
                getConfig().set("performance.inventory.page-cache-size", 10);
                
                // Performance logging
                getConfig().set("performance.logging.enabled", true);
                getConfig().set("performance.logging.interval-minutes", 30);
                getConfig().set("performance.logging.tps-threshold", 18.0);
                
                saveConfig();
            }
            
            // Add security settings if not present
            if (!getConfig().contains("security")) {
                getConfig().createSection("security");
                
                // Security settings
                getConfig().set("security.encryption-enabled", false);
                getConfig().set("security.log-unauthorized-attempts", true);
                getConfig().set("security.max-failed-attempts", 5);
                getConfig().set("security.attempt-expiration-seconds", 300);
                getConfig().set("security.strict-validation", true);
                getConfig().set("security.max-tag-name-length", 64);
                getConfig().set("security.max-tag-display-length", 128);
                getConfig().set("security.max-effective-display-length", 32);
                getConfig().set("security.verify-all-transactions", true);
                getConfig().set("security.menu-timeout-seconds", 300);
                getConfig().set("security.prevent-menu-stealing", true);
                getConfig().set("security.sanitize-inputs", true);
                
                saveConfig();
            }
            
            return true;
        } catch (Exception e) {
            getLogger().severe("Error loading configuration: " + e.getMessage());
            return false;
        }
    }

    /**
     * Registers commands and event listeners with security enhancements.
     */
    private void registerCommandsAndEvents() {
        try {
            previewTags = new HashMap<>();
            
            // Register command with security-enhanced version
            TagCommand tagCommand = new TagCommand(this, errorHandler, securityService, validationService);
            getCommand("tag").setExecutor(tagCommand);
            getCommand("tag").setTabCompleter(tagCommand);
            
            // Register event listeners with security enhancements
            tagMenuListener = new TagMenuListener(this, securityService, errorHandler);
            requestMenuClickListener = new RequestMenuClickListener(this, securityService, errorHandler);
            tagCommandPreviewListener = new TagCommandPreviewListener(this);
            loginListener = new LoginListener(this);
            
            getServer().getPluginManager().registerEvents(tagMenuListener, this);
            getServer().getPluginManager().registerEvents(requestMenuClickListener, this);
            getServer().getPluginManager().registerEvents(tagCommandPreviewListener, this);
            getServer().getPluginManager().registerEvents(loginListener, this);
            
            // Schedule tag request check with security logging
            long delay = 5 * 60 * 20; // 5 minutes in ticks (20 ticks per second)
            Bukkit.getScheduler().runTaskTimer(this, this::checkTagRequests, delay, delay);
        } catch (Exception e) {
            errorHandler.logError("Error registering commands and events", e);
        }
    }
    
    /**
     * Checks for pending tag requests and notifies staff.
     */
    private void checkTagRequests() {
        try {
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
                    List<CustomTagRequest> requests = databaseManager.getCustomTagRequests();
                    if (requests != null && !requests.isEmpty()) {
                        notifyStaffAboutPendingRequests(requests.size());
                    }
                }
                return null;
            });
        } catch (Exception e) {
            errorHandler.logError("Error checking tag requests", e);
        }
    }
    
    /**
     * Notifies staff members about pending tag requests.
     * 
     * @param count The number of pending requests
     */
    private void notifyStaffAboutPendingRequests(int count) {
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("utags.staff")) {
                    // Securely validate each player has the correct permission
                    if (securityService.checkPermission(player, "utags.staff", "view tag requests")) {
                        player.sendMessage("§c[uTags] §fThere are " + count + " pending tag requests. Use " + 
                                        "§e/tag admin requests§f to check them.");
                    }
                }
            }
        } catch (Exception e) {
            errorHandler.logError("Error notifying staff about pending requests", e);
        }
    }

    /**
     * Sets up the tag menu manager.
     */
    private void setupTagMenuManager() {
        this.tagMenuManager = new TagMenuManager(this);
    }
    
    /**
     * Schedules periodic logging of performance and security information.
     */
    private void scheduleLogging() {
        try {
            // Schedule performance logging
            if (getConfig().getBoolean("performance.logging.enabled", true)) {
                long performanceInterval = getConfig().getLong("performance.logging.interval-minutes", 30) * 60 * 20; // Convert to ticks
                Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::logPerformanceStats, performanceInterval, performanceInterval);
            }
            
            // Schedule security logging
            if (getConfig().getBoolean("security.logging.enabled", true)) {
                long securityInterval = getConfig().getLong("security.logging.interval-minutes", 60) * 60 * 20; // Convert to ticks
                Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::logSecurityStats, securityInterval, securityInterval);
            }
        } catch (Exception e) {
            errorHandler.logError("Error scheduling logging tasks", e);
        }
    }
    
    /**
     * Logs current performance statistics.
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
                
                databaseManager.logPoolStatus();
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
     * Logs security-related statistics.
     */
    private void logSecurityStats() {
        try {
            // Log number of players with open menus
            int openMenusCount = (tagMenuListener != null) ? tagMenuListener.getOpenMenuCount() : 0;
            getLogger().info("Security Status: " + openMenusCount + " players with open menus");
            
            // Log any additional security metrics here
        } catch (Exception e) {
            errorHandler.logError("Error logging security statistics", e);
        }
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
     * Gets the security service.
     *
     * @return The security service
     */
    public SecurityService getSecurityService() {
        return securityService;
    }
    
    /**
     * Gets the validation service.
     *
     * @return The validation service
     */
    public ValidationService getValidationService() {
        return validationService;
    }
    
    /**
     * Gets the configuration manager.
     *
     * @return The configuration manager
     */
    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
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
     * Gets the performance monitor.
     *
     * @return The performance monitor
     */
    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    /**
     * Gets the tag menu manager.
     *
     * @return The tag menu manager
     */
    public TagMenuManager getTagMenuManager() {
        return tagMenuManager;
    }
    
    /**
     * Gets the default tag.
     *
     * @return The default tag
     */
    public String getDefaultTag() {
        return defaultTag;
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
     * Checks if there are pending tag requests with secure handling.
     *
     * @return True if there are pending requests, false otherwise
     */
    public boolean hasPendingTagRequests() {
        try {
            // Use cache if available for better performance
            if (performanceModule != null && performanceModule.isCachingEnabled()) {
                CacheService cache = performanceModule.getCacheService();
                CacheService.CacheKey key = CacheService.CacheKey.forCustom("has_pending_requests");
                return cache.getOrLoad(key, () -> !databaseManager.getCustomTagRequests().isEmpty());
            }
            
            List<CustomTagRequest> requests = databaseManager.getCustomTagRequests();
            return requests != null && !requests.isEmpty();
        } catch (Exception e) {
            errorHandler.logError("Error checking for pending tag requests", e);
            return false;
        }
    }
    
    /**
     * Gets all available tags of the specified type with security checks.
     *
     * @param tagType The type of tags to retrieve
     * @return A list of available tags
     */
    public List<Tag> getAvailableTags(TagType tagType) {
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("get_available_tags", () -> {
            try {
                // Use cache if available
                if (performanceModule != null && performanceModule.isCachingEnabled()) {
                    return performanceModule.getCacheService().getAvailableTags(tagType);
                }
                
                // Fall back to direct database access
                return databaseManager.getAvailableTags(tagType);
            } catch (Exception e) {
                errorHandler.logError("Error getting available tags", e);
                return new ArrayList<>(); // Return empty list in case of error
            }
        });
    }
    
    /**
     * Adds a tag to the database with security validation.
     *
     * @param tag The tag to add
     * @return True if successful, false otherwise
     */
    public boolean addTagToDatabase(Tag tag) {
        if (tag == null) {
            return false;
        }
        
        // Validate tag for security
        ValidationService.ValidationResult nameResult = validationService.validateTagName(tag.getName(), null);
        ValidationService.ValidationResult displayResult = validationService.validateTagDisplay(tag.getDisplay(), null);
        
        if (!nameResult.isValid() || !displayResult.isValid()) {
            String error = !nameResult.isValid() ? nameResult.getErrorMessage() : displayResult.getErrorMessage();
            errorHandler.logError("Invalid tag data: " + error, null);
            return false;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("add_tag", () -> {
            try {
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
            } catch (Exception e) {
                errorHandler.logError("Error adding tag to database", e);
                return false;
            }
        });
    }

    /**
     * Deletes a tag from the database with security validation.
     * 
     * @param tagName The name of the tag to delete
     * @return True if successful, false otherwise
     */
    public boolean deleteTagFromDatabase(String tagName) {
        if (tagName == null || tagName.isEmpty()) {
            return false;
        }
        
        // Validate tag name for security
        ValidationService.ValidationResult result = validationService.validateTagName(tagName, null);
        if (!result.isValid()) {
            errorHandler.logError("Invalid tag name: " + result.getErrorMessage(), null);
            return false;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("delete_tag", () -> {
            try {
                // Use batch processing if available
                if (performanceModule != null && performanceModule.isBatchingEnabled()) {
                    performanceModule.getBatchProcessingService().deleteTag(tagName);
                    return true; // Batching doesn't provide immediate result
                }
                
                // Use async if available
                if (performanceModule != null && performanceModule.isAsyncEnabled()) {
                    final boolean[] result2 = new boolean[1];
                    performanceModule.getAsyncDatabaseService().deleteTagFromDatabaseAsync(tagName, success -> {
                        result2[0] = success;
                    });
                    return result2[0];
                }
                
                // Fall back to direct database access
                boolean success = databaseManager.deleteTagFromDatabase(tagName);
                
                // Invalidate cache if needed
                if (success && performanceModule != null && performanceModule.isCachingEnabled()) {
                    performanceModule.getCacheService().invalidateTag(tagName);
                }
                
                return success;
            } catch (Exception e) {
                errorHandler.logError("Error deleting tag from database", e);
                return false;
            }
        });
    }
    
    /**
     * Edits a tag attribute with security validation.
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
        
        // Validate tag name and attribute for security
        ValidationService.ValidationResult nameResult = validationService.validateTagName(tagName, null);
        ValidationService.ValidationResult attrResult = validationService.validateAttribute(attribute);
        
        if (!nameResult.isValid() || !attrResult.isValid()) {
            String error = !nameResult.isValid() ? nameResult.getErrorMessage() : attrResult.getErrorMessage();
            errorHandler.logError("Invalid tag attribute edit: " + error, null);
            return false;
        }
        
        // Validate the new value based on attribute type
        boolean isValidValue = true;
        String validationError = "";
        
        switch (attribute.toLowerCase()) {
            case "name":
                ValidationService.ValidationResult newNameResult = validationService.validateTagName(newValue, null);
                isValidValue = newNameResult.isValid();
                if (!isValidValue) validationError = newNameResult.getErrorMessage();
                break;
            case "display":
                ValidationService.ValidationResult displayResult = validationService.validateTagDisplay(newValue, null);
                isValidValue = displayResult.isValid();
                if (!isValidValue) validationError = displayResult.getErrorMessage();
                break;
            case "type":
                ValidationService.ValidationResult typeResult = validationService.validateTagType(newValue);
                isValidValue = typeResult.isValid();
                if (!isValidValue) validationError = typeResult.getErrorMessage();
                break;
            case "public":
            case "color":
                ValidationService.ValidationResult boolResult = validationService.validateBoolean(newValue);
                isValidValue = boolResult.isValid();
                if (!isValidValue) validationError = boolResult.getErrorMessage();
                break;
            case "weight":
                ValidationService.ValidationResult weightResult = validationService.validateWeight(newValue);
                isValidValue = weightResult.isValid();
                if (!isValidValue) validationError = weightResult.getErrorMessage();
                break;
        }
        
        if (!isValidValue) {
            errorHandler.logError("Invalid value for tag attribute: " + validationError, null);
            return false;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("edit_tag_attribute", () -> {
            try {
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
            } catch (Exception e) {
                errorHandler.logError("Error editing tag attribute", e);
                return false;
            }
        });
    }
    
    /**
     * Purges the tags table securely.
     *
     * @return True if successful, false otherwise
     */
    public boolean purgeTagsTable() {
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("purge_tags", () -> {
            try {
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
            } catch (Exception e) {
                errorHandler.logError("Error purging tags table", e);
                return false;
            }
        });
    }
    
    /**
     * Purges the requests table securely.
     *
     * @return True if successful, false otherwise
     */
    public boolean purgeRequestsTable() {
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("purge_requests", () -> {
            try {
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
            } catch (Exception e) {
                errorHandler.logError("Error purging requests table", e);
                return false;
            }
        });
    }
    
    /**
     * Sets a player's tag with security validation.
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
        
        // Validate the player and tag for security
        ValidationService.ValidationResult playerResult = validationService.validatePlayer(player);
        ValidationService.ValidationResult displayResult = validationService.validateTagDisplay(tagDisplay, player);
        
        if (!playerResult.isValid() || !displayResult.isValid()) {
            String error = !playerResult.isValid() ? playerResult.getErrorMessage() : displayResult.getErrorMessage();
            errorHandler.logError("Invalid player or tag display: " + error, null);
            return false;
        }
        
        // Log this operation
        securityService.logSecurityEvent(Level.INFO, player, "SET_TAG",
            "Setting " + tagType + " for player: " + tagDisplay);
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("set_player_tag", () -> {
            try {
                User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    if (tagType == TagType.PREFIX) {
                        user.data().clear(NodeType.PREFIX.predicate());
                        user.data().add(PrefixNode.builder(tagDisplay, 10000).build());
                    } else {
                        user.data().clear(NodeType.SUFFIX.predicate());
                        user.data().add(SuffixNode.builder(tagDisplay, 10000).build());
                    }
                    luckPerms.getUserManager().saveUser(user);
                    
                    // Invalidate player-related cache entries
                    if (performanceModule != null && performanceModule.isCachingEnabled()) {
                        performanceModule.getCacheService().invalidatePlayerData(player.getUniqueId(), player.getName());
                    }
                    
                    return true;
                }
                return false;
            } catch (Exception e) {
                errorHandler.logError("Error setting player tag", e);
                return false;
            }
        });
    }
    
    /**
     * Gets a tag's name by its display text securely.
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
            try {
                // Use cache if available
                if (performanceModule != null && performanceModule.isCachingEnabled()) {
                    return performanceModule.getCacheService().getTagNameByDisplay(display);
                }
                
                // Fall back to direct database access
                return databaseManager.getTagNameByDisplay(display);
            } catch (Exception e) {
                errorHandler.logError("Error getting tag name by display", e);
                return null;
            }
        });
    }
    
    /**
     * Gets a tag's display text by its name securely.
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
            try {
                // Use cache if available
                if (performanceModule != null && performanceModule.isCachingEnabled()) {
                    return performanceModule.getCacheService().getTagDisplayByName(name);
                }
                
                // Fall back to direct database access
                return databaseManager.getTagDisplayByName(name);
            } catch (Exception e) {
                errorHandler.logError("Error getting tag display by name", e);
                return null;
            }
        });
    }
    
    /**
     * Adds a tag preview for a player with security validation.
     *
     * @param player The player to add the preview for
     * @param tag The tag to preview
     */
    public void addPreviewTag(Player player, String tag) {
        if (player == null || tag == null) {
            return;
        }
        
        // Validate tag for security
        ValidationService.ValidationResult result = validationService.validateTagDisplay(tag, player);
        if (!result.isValid()) {
            if (player.isOnline()) {
                player.sendMessage(ChatColor.RED + result.getErrorMessage());
            }
            return;
        }
        
        previewTags.put(player.getUniqueId(), tag);
        
        // Log the preview for security audit
        securityService.logSecurityEvent(Level.FINE, player, "TAG_PREVIEW", 
            "Player previewing tag: " + tag);
        
        // Set a timeout to remove the preview if not used
        long timeoutTicks = getConfig().getLong("security.menu-timeout-seconds", 300) * 20;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (previewTags.containsKey(player.getUniqueId()) && 
                previewTags.get(player.getUniqueId()).equals(tag)) {
                previewTags.remove(player.getUniqueId());
            }
        }, timeoutTicks);
    }
    
    /**
     * Gets the preview tags map.
     *
     * @return The preview tags map
     */
    public Map<UUID, String> getPreviewTags() {
        return Collections.unmodifiableMap(previewTags);
    }
    
    /**
     * Creates a custom tag request securely.
     *
     * @param player The player making the request
     * @param tagDisplay The requested tag display
     * @return True if successful, false otherwise
     */
    public boolean createCustomTagRequest(Player player, String tagDisplay) {
        if (player == null || tagDisplay == null || tagDisplay.isEmpty()) {
            return false;
        }
        
        // Validate tag for security
        ValidationService.ValidationResult result = validationService.validateTagDisplay(tagDisplay, player);
        if (!result.isValid()) {
            if (player.isOnline()) {
                player.sendMessage(ChatColor.RED + result.getErrorMessage());
            }
            return false;
        }
        
        // Format tag display consistently
        int endIndex = tagDisplay.indexOf(']') + 1;
        if (endIndex < tagDisplay.length()) {
            tagDisplay = tagDisplay.substring(0, endIndex);
        }
        
        // Log this request for security audit
        securityService.logSecurityEvent(Level.INFO, player, "CREATE_TAG_REQUEST",
            "Player requested tag: " + tagDisplay);
            
        final String finalTagDisplay = tagDisplay;
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("create_request", () -> {
            try {
                // Use batch processing if available
                if (performanceModule != null && performanceModule.isBatchingEnabled()) {
                    CustomTagRequest request = new CustomTagRequest(
                        0, player.getUniqueId(), player.getName(), finalTagDisplay
                    );
                    performanceModule.getBatchProcessingService().addCustomTagRequest(request);
                    if (player.isOnline()) {
                        player.sendMessage(ChatColor.GREEN + "Your tag request has been submitted!");
                    }
                    return true; // Batching doesn't provide immediate result
                }
                
                // Use async if available
                if (performanceModule != null && performanceModule.isAsyncEnabled()) {
                    performanceModule.getAsyncDatabaseService().createCustomTagRequestAsync(
                        player.getUniqueId(), player.getName(), finalTagDisplay,
                        success -> {
                            if (player.isOnline()) {
                                if (success) {
                                    player.sendMessage(ChatColor.GREEN + "Your tag request has been submitted!");
                                } else {
                                    player.sendMessage(ChatColor.RED + "An error occurred while submitting your tag request.");
                                }
                            }
                        }
                    );
                    return true; // Async doesn't provide immediate result
                }
                
                // Fall back to direct database access
                boolean success = databaseManager.createCustomTagRequest(player.getUniqueId(), player.getName(), finalTagDisplay);
                
                if (player.isOnline()) {
                    if (success) {
                        player.sendMessage(ChatColor.GREEN + "Your tag request has been submitted!");
                    } else {
                        player.sendMessage(ChatColor.RED + "An error occurred while submitting your tag request.");
                    }
                }
                
                return success;
            } catch (Exception e) {
                errorHandler.logError("Error creating custom tag request", e);
                return false;
            }
        });
    }
    
    /**
     * Gets the number of custom tags a player has.
     *
     * @param playerName The name of the player
     * @return The number of custom tags
     */
    public int countCustomTags(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return 0;
        }
        
        // Validate player name for security
        ValidationService.ValidationResult result = validationService.validatePlayerName(playerName);
        if (!result.isValid()) {
            errorHandler.logError("Invalid player name: " + result.getErrorMessage(), null);
            return 0;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("count_custom_tags", () -> {
            try {
                // Use cache if available
                if (performanceModule != null && performanceModule.isCachingEnabled()) {
                    return performanceModule.getCacheService().countCustomTags(playerName);
                }
                
                // Fall back to direct database access
                return databaseManager.countCustomTags(playerName);
            } catch (Exception e) {
                errorHandler.logError("Error counting custom tags", e);
                return 0;
            }
        });
    }
    
    /**
     * Gets all custom tag requests securely.
     *
     * @return A list of custom tag requests
     */
    public List<CustomTagRequest> getCustomTagRequests() {
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("get_requests", () -> {
            try {
                // Use cache if available
                if (performanceModule != null && performanceModule.isCachingEnabled()) {
                    CacheService.CacheKey key = CacheService.CacheKey.forCustom("tag_requests");
                    return performanceModule.getCacheService().getOrLoad(key, () -> databaseManager.getCustomTagRequests());
                }
                
                // Fall back to direct database access
                return databaseManager.getCustomTagRequests();
            } catch (Exception e) {
                errorHandler.logError("Error getting custom tag requests", e);
                return new ArrayList<>(); // Return empty list in case of error
            }
        });
    }
    
    /**
     * Gets a custom tag request by player name securely.
     *
     * @param playerName The name of the player
     * @return The custom tag request, or null if not found
     */
    public CustomTagRequest getCustomTagRequestByPlayerName(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return null;
        }
        
        // Validate player name for security
        ValidationService.ValidationResult result = validationService.validatePlayerName(playerName);
        if (!result.isValid()) {
            errorHandler.logError("Invalid player name: " + result.getErrorMessage(), null);
            return null;
        }
        
        // Use performance monitoring to track execution time
        return performanceMonitor.trackOperation("get_request_by_name", () -> {
            try {
                // Use cache if available
                if (performanceModule != null && performanceModule.isCachingEnabled()) {
                    CacheService.CacheKey key = CacheService.CacheKey.forCustom("request_" + playerName);
                    return performanceModule.getCacheService().getOrLoad(key, () -> databaseManager.getCustomTagRequestByPlayerName(playerName));
                }
                
                // Fall back to direct database access
                return databaseManager.getCustomTagRequestByPlayerName(playerName);
            } catch (Exception e) {
                errorHandler.logError("Error getting custom tag request by player name", e);
                return null;
            }
        });
    }
    
    /**
     * Accepts a custom tag request securely.
     *
     * @param request The request to accept
     * @return True if successful, false otherwise
     */
    public boolean acceptCustomTagRequest(CustomTagRequest request) {
        if (request == null) {
            return false;
        }
        
        // Validate request for security
        ValidationService.ValidationResult uuidResult = validationService.validateUUID(request.getPlayerUuid());
        ValidationService.ValidationResult nameResult = validationService.validatePlayerName(request.getPlayerName());
        ValidationService.ValidationResult displayResult = validationService.validateTagDisplay(request.getTagDisplay(), null);
        
        if (!uuidResult.isValid() || !nameResult.isValid() || !displayResult.isValid()) {
            String error = !uuidResult.isValid() ? uuidResult.getErrorMessage() : 
                          !nameResult.isValid() ? nameResult.getErrorMessage() : 
                          displayResult.getErrorMessage();
            errorHandler.logError("Invalid tag request: " + error, null);
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
                luckPerms.getUserManager().loadUser(request.getPlayerUuid()).thenAcceptAsync(user -> {
                    user.data().add(net.luckperms.api.node.Node.builder(permission).build());
                    luckPerms.getUserManager().saveUser(user);
                    
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
                
                // Log the acceptance for security audit
                securityService.logSecurityEvent(Level.INFO, null, "TAG_REQUEST_ACCEPTED",
                    "Accepted tag request for " + request.getPlayerName() + ": " + request.getTagDisplay());
                
                return true;
            } catch (Exception e) {
                errorHandler.logError("Error accepting custom tag request", e);
                return false;
            }
        });
    }
    
    /**
     * Denies a custom tag request securely.
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
                    
                    // Log the denial for security audit
                    securityService.logSecurityEvent(Level.INFO, null, "TAG_REQUEST_DENIED",
                        "Denied tag request for " + request.getPlayerName() + ": " + request.getTagDisplay());
                    
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
     * Opens the requests menu for a player securely.
     *
     * @param player The player to open the menu for
     */
    public void openRequestsMenu(Player player) {
        try {
            // Validate player for security
            ValidationService.ValidationResult result = validationService.validatePlayer(player);
            if (!result.isValid()) {
                errorHandler.logError("Invalid player: " + result.getErrorMessage(), null);
                return;
            }
            
            // Check admin permission
            if (!securityService.checkAdmin(player, "view tag requests")) {
                return;
            }
            
            // Get requests with security check
            List<CustomTagRequest> requests = getCustomTagRequests();
            openRequestsMenu(player, requests);
        } catch (Exception e) {
            errorHandler.logError("Error opening requests menu", e);
        }
    }
    
    /**
     * Opens the requests menu for a player with specific requests.
     *
     * @param player The player to open the menu for
     * @param requests The requests to display
     */
    public void openRequestsMenu(Player player, List<CustomTagRequest> requests) {
        // Use performance monitoring to track execution time
        performanceMonitor.trackOperation("open_requests_menu", () -> {
            try {
                // Validate player for security
                ValidationService.ValidationResult result = validationService.validatePlayer(player);
                if (!result.isValid()) {
                    errorHandler.logError("Invalid player: " + result.getErrorMessage(), null);
                    return null;
                }
                
                // Check admin permission
                if (!securityService.checkAdmin(player, "view tag requests")) {
                    return null;
                }
                
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
            } catch (Exception e) {
                errorHandler.logError("Error opening requests menu", e);
                return null;
            }
        });
    }
    
    /**
     * Logs the current database connection pool status.
     */
    public void logDatabaseStatus() {
        try {
            databaseManager.logPoolStatus();
        } catch (Exception e) {
            errorHandler.logError("Error logging database status", e);
        }
    }
    
    /**
     * Flushes all batched operations immediately.
     */
    public void flushAllBatches() {
        try {
            if (performanceModule != null && performanceModule.isBatchingEnabled()) {
                performanceModule.getBatchProcessingService().processBatches();
            }
        } catch (Exception e) {
            errorHandler.logError("Error flushing batches", e);
        }
    }
}
