package com.blockworlds.utags.services;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.ErrorHandler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Service class for caching frequently accessed data in the uTags plugin.
 * Provides methods for caching and retrieving tag data, with automatic expiration
 * and statistics tracking.
 */
public class CacheService {

    // Cache storage structures
    private final Map<CacheKey, CacheEntry<?>> cache;
    
    // Statistics counters
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong totalLoadTime = new AtomicLong(0);
    
    // Configuration
    private final long defaultExpirationMs;
    private final boolean statsEnabled;
    
    // Plugin reference and utilities
    private final uTags plugin;
    private final ErrorHandler errorHandler;
    
    // Executor for cleanup tasks
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * Creates a new CacheService with the specified configuration.
     *
     * @param plugin The uTags plugin instance
     * @param errorHandler The error handler to use
     * @param defaultExpirationMs Default cache entry expiration time in milliseconds
     * @param statsEnabled Whether to track and report cache statistics
     */
    public CacheService(uTags plugin, ErrorHandler errorHandler, long defaultExpirationMs, boolean statsEnabled) {
        this.plugin = plugin;
        this.errorHandler = errorHandler;
        this.defaultExpirationMs = defaultExpirationMs;
        this.statsEnabled = statsEnabled;
        
        // Initialize thread-safe cache
        this.cache = new ConcurrentHashMap<>();
        
        // Initialize cleanup executor
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "uTags-Cache-Cleanup");
            thread.setDaemon(true);
            return thread;
        });
        
        // Schedule periodic cleanup
        this.cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredEntries, 
            defaultExpirationMs / 2, 
            defaultExpirationMs / 2, 
            TimeUnit.MILLISECONDS
        );
        
        // Log cache initialization
        plugin.getLogger().info("CacheService initialized with " + 
                               (defaultExpirationMs / 1000) + "s expiration time. " +
                               "Statistics tracking: " + (statsEnabled ? "enabled" : "disabled"));
    }
    
    /**
     * Gets a cached value for the specified key, or loads and caches it if not present.
     *
     * @param <T> The type of the cached value
     * @param key The cache key
     * @param loader The function to load the value if not in cache
     * @return The cached or loaded value
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(CacheKey key, Supplier<T> loader) {
        return getOrLoad(key, loader, defaultExpirationMs);
    }
    
    /**
     * Gets a cached value with a custom expiration time.
     *
     * @param <T> The type of the cached value
     * @param key The cache key
     * @param loader The function to load the value if not in cache
     * @param expirationMs Custom expiration time in milliseconds
     * @return The cached or loaded value
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(CacheKey key, Supplier<T> loader, long expirationMs) {
        // Check if the value is in cache and not expired
        CacheEntry<?> entry = cache.get(key);
        
        if (entry != null && !entry.isExpired()) {
            // Cache hit
            if (statsEnabled) {
                hits.incrementAndGet();
            }
            return (T) entry.getValue();
        }
        
        // Cache miss - load the value
        if (statsEnabled) {
            misses.incrementAndGet();
        }
        
        // Measuring load time for statistics
        long startTime = System.currentTimeMillis();
        T value = loader.get();
        long loadTime = System.currentTimeMillis() - startTime;
        
        if (statsEnabled) {
            totalLoadTime.addAndGet(loadTime);
        }
        
        // Store in cache if non-null
        if (value != null) {
            cache.put(key, new CacheEntry<>(value, expirationMs));
        }
        
        return value;
    }
    
    /**
     * Gets all available tags of a specified type from cache or loads them.
     *
     * @param tagType The type of tags to retrieve
     * @return A list of available tags
     */
    public List<Tag> getAvailableTags(TagType tagType) {
        CacheKey key = CacheKey.forAvailableTags(tagType);
        return getOrLoad(key, () -> plugin.getDatabaseManager().getAvailableTags(tagType));
    }
    
    /**
     * Gets a tag's name by its display text from cache or loads it.
     *
     * @param display The display text of the tag
     * @return The tag name, or null if not found
     */
    public String getTagNameByDisplay(String display) {
        if (display == null) return null;
        CacheKey key = CacheKey.forTagNameByDisplay(display);
        return getOrLoad(key, () -> plugin.getDatabaseManager().getTagNameByDisplay(display));
    }
    
    /**
     * Gets a tag's display text by its name from cache or loads it.
     *
     * @param name The name of the tag
     * @return The tag display text, or null if not found
     */
    public String getTagDisplayByName(String name) {
        if (name == null) return null;
        CacheKey key = CacheKey.forTagDisplayByName(name);
        return getOrLoad(key, () -> plugin.getDatabaseManager().getTagDisplayByName(name));
    }
    
    /**
     * Gets the count of custom tags for a player from cache or loads it.
     *
     * @param playerName The name of the player
     * @return The number of custom tags
     */
    public int countCustomTags(String playerName) {
        if (playerName == null) return 0;
        CacheKey key = CacheKey.forCustomTagCount(playerName);
        return getOrLoad(key, () -> plugin.getDatabaseManager().countCustomTags(playerName));
    }
    
    /**
     * Invalidates a specific cache entry.
     *
     * @param key The key to invalidate
     */
    public void invalidate(CacheKey key) {
        cache.remove(key);
    }
    
    /**
     * Invalidates all cache entries related to a specific tag.
     *
     * @param tagName The name of the tag
     */
    public void invalidateTag(String tagName) {
        if (tagName == null) return;
        
        // Invalidate tag display
        invalidate(CacheKey.forTagDisplayByName(tagName));
        
        // Invalidate tag display-to-name mapping
        String tagDisplay = plugin.getDatabaseManager().getTagDisplayByName(tagName);
        if (tagDisplay != null) {
            invalidate(CacheKey.forTagNameByDisplay(tagDisplay));
        }
        
        // Invalidate available tags lists
        invalidate(CacheKey.forAvailableTags(TagType.PREFIX));
        invalidate(CacheKey.forAvailableTags(TagType.SUFFIX));
        invalidate(CacheKey.forAvailableTags(TagType.BOTH));
        invalidate(CacheKey.forAvailableTags(null));
        
        // If it's a player tag, invalidate player tag count
        if (tagName.matches("^[a-zA-Z0-9_]+\\d$")) {
            String playerName = tagName.replaceAll("\\d$", "");
            invalidate(CacheKey.forCustomTagCount(playerName));
        }
    }
    
    /**
     * Invalidates all tag-related cache entries (complete cache flush).
     */
    public void invalidateAllTags() {
        cache.keySet().removeIf(key -> 
            key.getType() == CacheKeyType.AVAILABLE_TAGS ||
            key.getType() == CacheKeyType.TAG_DISPLAY_BY_NAME ||
            key.getType() == CacheKeyType.TAG_NAME_BY_DISPLAY ||
            key.getType() == CacheKeyType.CUSTOM_TAG_COUNT
        );
    }
    
    /**
     * Invalidates cache entries for a specific player.
     *
     * @param playerUuid The UUID of the player
     * @param playerName The name of the player
     */
    public void invalidatePlayerData(UUID playerUuid, String playerName) {
        if (playerName != null) {
            invalidate(CacheKey.forCustomTagCount(playerName));
        }
        
        // Invalidate other player-specific data as needed
    }
    
    /**
     * Invalidates all cache entries (complete cache clear).
     */
    public void invalidateAll() {
        cache.clear();
    }
    
    /**
     * Removes expired entries from the cache.
     */
    private void cleanupExpiredEntries() {
        try {
            int beforeSize = cache.size();
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            int removedCount = beforeSize - cache.size();
            
            if (removedCount > 0 && statsEnabled) {
                plugin.getLogger().fine("Cleaned up " + removedCount + " expired cache entries. Current cache size: " + cache.size());
            }
        } catch (Exception e) {
            errorHandler.logError("Error during cache cleanup", e);
        }
    }
    
    /**
     * Gets statistics about the cache usage.
     *
     * @return A string with cache statistics
     */
    public String getStatistics() {
        if (!statsEnabled) {
            return "Cache statistics are disabled";
        }
        
        long totalRequests = hits.get() + misses.get();
        double hitRate = totalRequests > 0 ? (double) hits.get() / totalRequests * 100 : 0;
        double avgLoadTimeMs = misses.get() > 0 ? (double) totalLoadTime.get() / misses.get() : 0;
        
        return String.format(
            "Cache Stats: Size=%d, Hits=%d, Misses=%d, Hit Rate=%.2f%%, Avg Load Time=%.2f ms",
            cache.size(), hits.get(), misses.get(), hitRate, avgLoadTimeMs
        );
    }
    
    /**
     * Logs current cache statistics if stats tracking is enabled.
     */
    public void logStatistics() {
        if (statsEnabled) {
            plugin.getLogger().info(getStatistics());
        }
    }
    
    /**
     * Shuts down the cache service, cleaning up resources.
     */
    public void shutdown() {
        try {
            cleanupExecutor.shutdown();
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
            if (statsEnabled) {
                logStatistics();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorHandler.logWarning("Cache service shutdown interrupted");
        } catch (Exception e) {
            errorHandler.logError("Error shutting down cache service", e);
        }
    }
    
    /**
     * Enumeration of cache key types.
     */
    public enum CacheKeyType {
        AVAILABLE_TAGS,
        TAG_DISPLAY_BY_NAME,
        TAG_NAME_BY_DISPLAY,
        CUSTOM_TAG_COUNT,
        CUSTOM
    }
    
    /**
     * Class representing a cache key with a type and identifier.
     */
    public static class CacheKey {
        private final CacheKeyType type;
        private final String identifier;
        
        private CacheKey(CacheKeyType type, String identifier) {
            this.type = type;
            this.identifier = identifier;
        }
        
        /**
         * Creates a key for available tags by type.
         *
         * @param tagType The tag type
         * @return The cache key
         */
        public static CacheKey forAvailableTags(TagType tagType) {
            return new CacheKey(CacheKeyType.AVAILABLE_TAGS, String.valueOf(tagType));
        }
        
        /**
         * Creates a key for tag display by name.
         *
         * @param name The tag name
         * @return The cache key
         */
        public static CacheKey forTagDisplayByName(String name) {
            return new CacheKey(CacheKeyType.TAG_DISPLAY_BY_NAME, name);
        }
        
        /**
         * Creates a key for tag name by display.
         *
         * @param display The tag display
         * @return The cache key
         */
        public static CacheKey forTagNameByDisplay(String display) {
            return new CacheKey(CacheKeyType.TAG_NAME_BY_DISPLAY, display);
        }
        
        /**
         * Creates a key for custom tag count by player.
         *
         * @param playerName The player name
         * @return The cache key
         */
        public static CacheKey forCustomTagCount(String playerName) {
            return new CacheKey(CacheKeyType.CUSTOM_TAG_COUNT, playerName);
        }
        
        /**
         * Creates a custom cache key.
         *
         * @param identifier The identifier
         * @return The cache key
         */
        public static CacheKey forCustom(String identifier) {
            return new CacheKey(CacheKeyType.CUSTOM, identifier);
        }
        
        /**
         * Gets the type of this cache key.
         *
         * @return The key type
         */
        public CacheKeyType getType() {
            return type;
        }
        
        /**
         * Gets the identifier of this cache key.
         *
         * @return The identifier
         */
        public String getIdentifier() {
            return identifier;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            
            CacheKey cacheKey = (CacheKey) o;
            return type == cacheKey.type && 
                  (identifier == null ? cacheKey.identifier == null : identifier.equals(cacheKey.identifier));
        }
        
        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + (identifier != null ? identifier.hashCode() : 0);
            return result;
        }
        
        @Override
        public String toString() {
            return type + ":" + identifier;
        }
    }
    
    /**
     * Class representing a cache entry with value and expiration time.
     *
     * @param <T> The type of the cached value
     */
    private static class CacheEntry<T> {
        private final T value;
        private final long expirationTime;
        
        /**
         * Creates a new cache entry.
         *
         * @param value The value to cache
         * @param ttlMs Time-to-live in milliseconds
         */
        public CacheEntry(T value, long ttlMs) {
            this.value = value;
            this.expirationTime = System.currentTimeMillis() + ttlMs;
        }
        
        /**
         * Gets the cached value.
         *
         * @return The value
         */
        public T getValue() {
            return value;
        }
        
        /**
         * Checks if this entry has expired.
         *
         * @return True if expired, false otherwise
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}
