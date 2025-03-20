package com.blockworlds.utags;

import com.blockworlds.utags.services.AsyncDatabaseService;
import com.blockworlds.utags.services.BatchProcessingService;
import com.blockworlds.utags.services.CacheService;
import com.blockworlds.utags.utils.ErrorHandler;
import com.blockworlds.utags.utils.InventoryOptimizer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Module class for performance optimizations in the uTags plugin.
 * Manages all performance-related services and configurations.
 */
public class PerformanceModule {

    private final uTags plugin;
    private final ErrorHandler errorHandler;
    
    // Performance optimization services
    private CacheService cacheService;
    private AsyncDatabaseService asyncDatabaseService;
    private BatchProcessingService batchProcessingService;
    private InventoryOptimizer inventoryOptimizer;
    
    // Configuration
    private boolean cachingEnabled;
    private boolean asyncEnabled;
    private boolean batchingEnabled;
    private boolean inventoryOptimizationEnabled;
    
    /**
     * Creates a new PerformanceModule.
     *
     * @param plugin The uTags plugin instance
     * @param errorHandler The error handler to use
     */
    public PerformanceModule(uTags plugin, ErrorHandler errorHandler) {
        this.plugin = plugin;
        this.errorHandler = errorHandler;
        
        // Initialize with default configurations
        loadConfiguration();
        
        // Initialize services
        initializeServices();
        
        plugin.getLogger().info("Performance module initialized with: " +
                               (cachingEnabled ? "caching, " : "") +
                               (asyncEnabled ? "async operations, " : "") +
                               (batchingEnabled ? "batching, " : "") +
                               (inventoryOptimizationEnabled ? "inventory optimization" : ""));
    }
    
    /**
     * Loads performance-related configuration from config.yml.
     */
    private void loadConfiguration() {
        // Ensure defaults are set
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        
        // Create performance section if it doesn't exist
        if (!config.contains("performance")) {
            config.createSection("performance");
            
            // Set default values
            ConfigurationSection perfSection = config.getConfigurationSection("performance");
            perfSection.set("caching.enabled", true);
            perfSection.set("caching.expiration-seconds", 300);
            perfSection.set("caching.statistics", true);
            
            perfSection.set("async.enabled", true);
            
            perfSection.set("batching.enabled", true);
            perfSection.set("batching.size", 20);
            perfSection.set("batching.interval-seconds", 5);
            perfSection.set("batching.auto-flush", true);
            
            perfSection.set("inventory.optimization", true);
            perfSection.set("inventory.templates", true);
            perfSection.set("inventory.item-pooling", true);
            perfSection.set("inventory.page-cache-size", 10);
            
            plugin.saveConfig();
        }
        
        // Load configuration values
        ConfigurationSection perfSection = config.getConfigurationSection("performance");
        
        // Caching config
        cachingEnabled = perfSection.getBoolean("caching.enabled", true);
        long cacheExpirationSeconds = perfSection.getLong("caching.expiration-seconds", 300);
        boolean cacheStatsEnabled = perfSection.getBoolean("caching.statistics", true);
        
        // Async config
        asyncEnabled = perfSection.getBoolean("async.enabled", true);
        
        // Batching config
        batchingEnabled = perfSection.getBoolean("batching.enabled", true);
        int batchSize = perfSection.getInt("batching.size", 20);
        long batchIntervalSeconds = perfSection.getLong("batching.interval-seconds", 5);
        boolean autoFlush = perfSection.getBoolean("batching.auto-flush", true);
        
        // Inventory optimization config
        inventoryOptimizationEnabled = perfSection.getBoolean("inventory.optimization", true);
        boolean useTemplates = perfSection.getBoolean("inventory.templates", true);
        boolean useItemPooling = perfSection.getBoolean("inventory.item-pooling", true);
        int pageCacheSize = perfSection.getInt("inventory.page-cache-size", 10);
        
        // Convert time units
        long cacheExpirationMs = cacheExpirationSeconds * 1000;
        long batchIntervalMs = batchIntervalSeconds * 1000;
        
        // Store configuration for service initialization
        this.cachingEnabled = cachingEnabled;
        this.asyncEnabled = asyncEnabled;
        this.batchingEnabled = batchingEnabled;
        this.inventoryOptimizationEnabled = inventoryOptimizationEnabled;
        
        // Initialize services based on configuration
        if (cachingEnabled) {
            cacheService = new CacheService(plugin, errorHandler, cacheExpirationMs, cacheStatsEnabled);
        }
        
        if (asyncEnabled) {
            asyncDatabaseService = new AsyncDatabaseService(
                plugin, errorHandler, cacheService, batchIntervalMs, batchSize, batchingEnabled
            );
        }
        
        if (batchingEnabled) {
            batchProcessingService = new BatchProcessingService(
                plugin, errorHandler, cacheService, batchSize, batchIntervalMs, autoFlush
            );
        }
        
        if (inventoryOptimizationEnabled) {
            inventoryOptimizer = new InventoryOptimizer(
                plugin, errorHandler, useTemplates, useItemPooling, pageCacheSize
            );
        }
    }
    
    /**
     * Initializes all performance-related services.
     */
    private void initializeServices() {
        // Initialize services in the correct order with proper dependencies
        if (cacheService == null && cachingEnabled) {
            FileConfiguration config = plugin.getConfig();
            ConfigurationSection perfSection = config.getConfigurationSection("performance");
            long cacheExpirationSeconds = perfSection.getLong("caching.expiration-seconds", 300);
            boolean cacheStatsEnabled = perfSection.getBoolean("caching.statistics", true);
            long cacheExpirationMs = cacheExpirationSeconds * 1000;
            
            cacheService = new CacheService(plugin, errorHandler, cacheExpirationMs, cacheStatsEnabled);
        }
        
        if (asyncDatabaseService == null && asyncEnabled) {
            FileConfiguration config = plugin.getConfig();
            ConfigurationSection perfSection = config.getConfigurationSection("performance");
            int batchSize = perfSection.getInt("batching.size", 20);
            long batchIntervalSeconds = perfSection.getLong("batching.interval-seconds", 5);
            long batchIntervalMs = batchIntervalSeconds * 1000;
            
            asyncDatabaseService = new AsyncDatabaseService(
                plugin, errorHandler, cacheService, batchIntervalMs, batchSize, batchingEnabled
            );
        }
        
        if (batchProcessingService == null && batchingEnabled) {
            FileConfiguration config = plugin.getConfig();
            ConfigurationSection perfSection = config.getConfigurationSection("performance");
            int batchSize = perfSection.getInt("batching.size", 20);
            long batchIntervalSeconds = perfSection.getLong("batching.interval-seconds", 5);
            boolean autoFlush = perfSection.getBoolean("batching.auto-flush", true);
            long batchIntervalMs = batchIntervalSeconds * 1000;
            
            batchProcessingService = new BatchProcessingService(
                plugin, errorHandler, cacheService, batchSize, batchIntervalMs, autoFlush
            );
        }
        
        if (inventoryOptimizer == null && inventoryOptimizationEnabled) {
            FileConfiguration config = plugin.getConfig();
            ConfigurationSection perfSection = config.getConfigurationSection("performance");
            boolean useTemplates = perfSection.getBoolean("inventory.templates", true);
            boolean useItemPooling = perfSection.getBoolean("inventory.item-pooling", true);
            int pageCacheSize = perfSection.getInt("inventory.page-cache-size", 10);
            
            inventoryOptimizer = new InventoryOptimizer(
                plugin, errorHandler, useTemplates, useItemPooling, pageCacheSize
            );
        }
    }
    
    /**
     * Gets the cache service.
     *
     * @return The cache service, or null if caching is disabled
     */
    public CacheService getCacheService() {
        return cacheService;
    }
    
    /**
     * Gets the async database service.
     *
     * @return The async database service, or null if async operations are disabled
     */
    public AsyncDatabaseService getAsyncDatabaseService() {
        return asyncDatabaseService;
    }
    
    /**
     * Gets the batch processing service.
     *
     * @return The batch processing service, or null if batching is disabled
     */
    public BatchProcessingService getBatchProcessingService() {
        return batchProcessingService;
    }
    
    /**
     * Gets the inventory optimizer.
     *
     * @return The inventory optimizer, or null if inventory optimization is disabled
     */
    public InventoryOptimizer getInventoryOptimizer() {
        return inventoryOptimizer;
    }
    
    /**
     * Checks if caching is enabled.
     *
     * @return True if caching is enabled, false otherwise
     */
    public boolean isCachingEnabled() {
        return cachingEnabled;
    }
    
    /**
     * Checks if async operations are enabled.
     *
     * @return True if async operations are enabled, false otherwise
     */
    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }
    
    /**
     * Checks if batching is enabled.
     *
     * @return True if batching is enabled, false otherwise
     */
    public boolean isBatchingEnabled() {
        return batchingEnabled;
    }
    
    /**
     * Checks if inventory optimization is enabled.
     *
     * @return True if inventory optimization is enabled, false otherwise
     */
    public boolean isInventoryOptimizationEnabled() {
        return inventoryOptimizationEnabled;
    }
    
    /**
     * Logs performance statistics from all enabled services.
     */
    public void logStatistics() {
        if (cachingEnabled && cacheService != null) {
            cacheService.logStatistics();
        }
        
        if (batchingEnabled && batchProcessingService != null) {
            batchProcessingService.logStatistics();
        }
        
        if (inventoryOptimizationEnabled && inventoryOptimizer != null) {
            plugin.getLogger().info(inventoryOptimizer.getStatistics());
        }
    }
    
    /**
     * Shuts down all services, cleaning up resources.
     */
    public void shutdown() {
        try {
            plugin.getLogger().info("Shutting down performance services...");
            
            // Shut down services in reverse order of dependencies
            if (inventoryOptimizationEnabled && inventoryOptimizer != null) {
                inventoryOptimizer.shutdown();
            }
            
            if (batchingEnabled && batchProcessingService != null) {
                batchProcessingService.shutdown();
            }
            
            if (asyncEnabled && asyncDatabaseService != null) {
                asyncDatabaseService.shutdown();
            }
            
            if (cachingEnabled && cacheService != null) {
                cacheService.shutdown();
            }
            
            plugin.getLogger().info("Performance services shutdown complete");
        } catch (Exception e) {
            errorHandler.logError("Error shutting down performance module", e);
        }
    }
}
