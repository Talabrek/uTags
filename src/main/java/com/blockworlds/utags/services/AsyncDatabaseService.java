package com.blockworlds.utags.services;

import com.blockworlds.utags.CustomTagRequest;
import com.blockworlds.utags.DatabaseManager;
import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.ErrorHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Service class for handling database operations asynchronously.
 * Wraps the DatabaseManager to provide non-blocking database access.
 */
public class AsyncDatabaseService {

    private final uTags plugin;
    private final DatabaseManager databaseManager;
    private final ErrorHandler errorHandler;
    private final CacheService cacheService;
    
    // Batch operation queues
    private final Queue<Runnable> tagBatchQueue;
    private final Queue<Runnable> requestBatchQueue;
    
    // Executor for handling async operations
    private final ExecutorService asyncExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    
    // Batch operation configuration
    private final long batchProcessIntervalMs;
    private final int maxBatchSize;
    private final boolean batchingEnabled;
    
    // Scheduled tasks
    private BukkitTask tagBatchTask;
    private BukkitTask requestBatchTask;

    /**
     * Creates a new AsyncDatabaseService.
     *
     * @param plugin The uTags plugin instance
     * @param errorHandler The error handler to use
     * @param cacheService The cache service to use
     * @param batchProcessIntervalMs Interval for processing batched operations in milliseconds
     * @param maxBatchSize Maximum number of operations to batch together
     * @param batchingEnabled Whether to enable batching of operations
     */
    public AsyncDatabaseService(uTags plugin, ErrorHandler errorHandler, CacheService cacheService,
                               long batchProcessIntervalMs, int maxBatchSize, boolean batchingEnabled) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.errorHandler = errorHandler;
        this.cacheService = cacheService;
        this.batchProcessIntervalMs = batchProcessIntervalMs;
        this.maxBatchSize = maxBatchSize;
        this.batchingEnabled = batchingEnabled;
        
        // Initialize thread-safe queues
        this.tagBatchQueue = new ConcurrentLinkedQueue<>();
        this.requestBatchQueue = new ConcurrentLinkedQueue<>();
        
        // Initialize executors
        this.asyncExecutor = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r, "uTags-Async-Worker");
            thread.setDaemon(true);
            return thread;
        });
        
        this.scheduledExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r, "uTags-Scheduled-Worker");
            thread.setDaemon(true);
            return thread;
        });
        
        // Start batch processing tasks if batching is enabled
        if (batchingEnabled) {
            startBatchProcessing();
        }
        
        plugin.getLogger().info("AsyncDatabaseService initialized. Batching " + 
                              (batchingEnabled ? "enabled with " + maxBatchSize + " max batch size" : "disabled"));
    }
    
    /**
     * Starts background tasks for processing batched operations.
     */
    private void startBatchProcessing() {
        // Schedule tag batch processing
        long tickInterval = batchProcessIntervalMs / 50; // Convert ms to ticks (20 ticks = 1 second)
        tagBatchTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin, this::processTagBatchQueue, tickInterval, tickInterval
        );
        
        // Schedule request batch processing
        requestBatchTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin, this::processRequestBatchQueue, tickInterval, tickInterval
        );
    }
    
    /**
     * Executes an operation asynchronously.
     *
     * @param <T> The return type of the operation
     * @param operation The operation to execute
     * @return A CompletableFuture with the operation result
     */
    private <T> CompletableFuture<T> executeAsync(Supplier<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        
        asyncExecutor.submit(() -> {
            try {
                T result = operation.get();
                future.complete(result);
            } catch (Exception e) {
                errorHandler.logError("Error in async database operation", e);
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
    
    /**
     * Executes a void operation asynchronously.
     *
     * @param operation The operation to execute
     * @return A CompletableFuture that completes when the operation is done
     */
    private CompletableFuture<Void> executeAsyncVoid(Runnable operation) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        asyncExecutor.submit(() -> {
            try {
                operation.run();
                future.complete(null);
            } catch (Exception e) {
                errorHandler.logError("Error in async database operation", e);
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
    
    /**
     * Gets all available tags of a specified type asynchronously.
     *
     * @param tagType The type of tags to retrieve
     * @return A CompletableFuture with the list of available tags
     */
    public CompletableFuture<List<Tag>> getAvailableTagsAsync(TagType tagType) {
        // Try to get from cache first
        List<Tag> cachedTags = cacheService.getAvailableTags(tagType);
        if (cachedTags != null) {
            return CompletableFuture.completedFuture(cachedTags);
        }
        
        // Otherwise load asynchronously
        return executeAsync(() -> {
            List<Tag> tags = databaseManager.getAvailableTags(tagType);
            // Update cache with loaded data
            if (tags != null) {
                cacheService.getOrLoad(CacheService.CacheKey.forAvailableTags(tagType), () -> tags);
            }
            return tags;
        });
    }
    
    /**
     * Gets a tag's name by its display text asynchronously.
     *
     * @param display The display text of the tag
     * @return A CompletableFuture with the tag name, or null if not found
     */
    public CompletableFuture<String> getTagNameByDisplayAsync(String display) {
        if (display == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Try to get from cache first
        String cachedName = cacheService.getTagNameByDisplay(display);
        if (cachedName != null) {
            return CompletableFuture.completedFuture(cachedName);
        }
        
        // Otherwise load asynchronously
        return executeAsync(() -> {
            String name = databaseManager.getTagNameByDisplay(display);
            // Update cache with loaded data
            if (name != null) {
                cacheService.getOrLoad(CacheService.CacheKey.forTagNameByDisplay(display), () -> name);
            }
            return name;
        });
    }
    
    /**
     * Gets a tag's display text by its name asynchronously.
     *
     * @param name The name of the tag
     * @return A CompletableFuture with the tag display text, or null if not found
     */
    public CompletableFuture<String> getTagDisplayByNameAsync(String name) {
        if (name == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Try to get from cache first
        String cachedDisplay = cacheService.getTagDisplayByName(name);
        if (cachedDisplay != null) {
            return CompletableFuture.completedFuture(cachedDisplay);
        }
        
        // Otherwise load asynchronously
        return executeAsync(() -> {
            String display = databaseManager.getTagDisplayByName(name);
            // Update cache with loaded data
            if (display != null) {
                cacheService.getOrLoad(CacheService.CacheKey.forTagDisplayByName(name), () -> display);
            }
            return display;
        });
    }
    
    /**
     * Gets the count of custom tags for a player asynchronously.
     *
     * @param playerName The name of the player
     * @return A CompletableFuture with the number of custom tags
     */
    public CompletableFuture<Integer> countCustomTagsAsync(String playerName) {
        if (playerName == null) {
            return CompletableFuture.completedFuture(0);
        }
        
        // Try to get from cache first
        Integer cachedCount = cacheService.countCustomTags(playerName);
        if (cachedCount != null) {
            return CompletableFuture.completedFuture(cachedCount);
        }
        
        // Otherwise load asynchronously
        return executeAsync(() -> {
            int count = databaseManager.countCustomTags(playerName);
            // Update cache with loaded data
            cacheService.getOrLoad(CacheService.CacheKey.forCustomTagCount(playerName), () -> count);
            return count;
        });
    }
    
    /**
     * Gets all custom tag requests asynchronously.
     *
     * @return A CompletableFuture with the list of custom tag requests
     */
    public CompletableFuture<List<CustomTagRequest>> getCustomTagRequestsAsync() {
        return executeAsync(() -> databaseManager.getCustomTagRequests());
    }
    
    /**
     * Gets a custom tag request by player name asynchronously.
     *
     * @param playerName The name of the player
     * @return A CompletableFuture with the custom tag request, or null if not found
     */
    public CompletableFuture<CustomTagRequest> getCustomTagRequestByPlayerNameAsync(String playerName) {
        if (playerName == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        return executeAsync(() -> databaseManager.getCustomTagRequestByPlayerName(playerName));
    }
    
    /**
     * Adds a tag to the database asynchronously.
     * Can be batched if batching is enabled.
     *
     * @param tag The tag to add
     * @param callback Callback to execute when the operation completes
     */
    public void addTagToDatabaseAsync(Tag tag, Consumer<Boolean> callback) {
        if (tag == null) {
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        
        if (batchingEnabled) {
            // Add to batch queue
            tagBatchQueue.add(() -> {
                boolean result = databaseManager.addTagToDatabase(tag);
                if (result) {
                    // Invalidate relevant cache entries
                    cacheService.invalidateTag(tag.getName());
                }
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
                }
            });
        } else {
            // Execute immediately
            executeAsync(() -> {
                boolean result = databaseManager.addTagToDatabase(tag);
                if (result) {
                    // Invalidate relevant cache entries
                    cacheService.invalidateTag(tag.getName());
                }
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
                }
                return result;
            });
        }
    }
    
    /**
     * Deletes a tag from the database asynchronously.
     * Can be batched if batching is enabled.
     *
     * @param tagName The name of the tag to delete
     * @param callback Callback to execute when the operation completes
     */
    public void deleteTagFromDatabaseAsync(String tagName, Consumer<Boolean> callback) {
        if (tagName == null) {
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        
        if (batchingEnabled) {
            // Add to batch queue
            tagBatchQueue.add(() -> {
                boolean result = databaseManager.deleteTagFromDatabase(tagName);
                if (result) {
                    // Invalidate relevant cache entries
                    cacheService.invalidateTag(tagName);
                }
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
                }
            });
        } else {
            // Execute immediately
            executeAsync(() -> {
                boolean result = databaseManager.deleteTagFromDatabase(tagName);
                if (result) {
                    // Invalidate relevant cache entries
                    cacheService.invalidateTag(tagName);
                }
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
                }
                return result;
            });
        }
    }
    
    /**
     * Edits a tag attribute in the database asynchronously.
     * Can be batched if batching is enabled.
     *
     * @param tagName The name of the tag to edit
     * @param attribute The attribute to edit
     * @param newValue The new value for the attribute
     * @param callback Callback to execute when the operation completes
     */
    public void editTagAttributeAsync(String tagName, String attribute, String newValue, Consumer<Boolean> callback) {
        if (tagName == null || attribute == null) {
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        
        if (batchingEnabled) {
            // Add to batch queue
            tagBatchQueue.add(() -> {
                boolean result = databaseManager.editTagAttribute(tagName, attribute, newValue);
                if (result) {
                    // Invalidate relevant cache entries
                    cacheService.invalidateTag(tagName);
                }
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
                }
            });
        } else {
            // Execute immediately
            executeAsync(() -> {
                boolean result = databaseManager.editTagAttribute(tagName, attribute, newValue);
                if (result) {
                    // Invalidate relevant cache entries
                    cacheService.invalidateTag(tagName);
                }
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
                }
                return result;
            });
        }
    }
    
    /**
     * Creates a custom tag request asynchronously.
     * Can be batched if batching is enabled.
     *
     * @param playerUuid The UUID of the player
     * @param playerName The name of the player
     * @param tagDisplay The display text for the tag
     * @param callback Callback to execute when the operation completes
     */
    public void createCustomTagRequestAsync(UUID playerUuid, String playerName, String tagDisplay, Consumer<Boolean> callback) {
        if (playerUuid == null || playerName == null || tagDisplay == null) {
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        
        if (batchingEnabled) {
            // Add to batch queue
            requestBatchQueue.add(() -> {
                boolean result = databaseManager.createCustomTagRequest(playerUuid, playerName, tagDisplay);
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
                }
            });
        } else {
            // Execute immediately
            executeAsync(() -> {
                boolean result = databaseManager.createCustomTagRequest(playerUuid, playerName, tagDisplay);
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
                }
                return result;
            });
        }
    }
    
    /**
     * Removes a custom tag request asynchronously.
     * Can be batched if batching is enabled.
     *
     * @param requestId The ID of the request to remove
     * @param callback Callback to execute when the operation completes
     */
    public void removeCustomTagRequestAsync(int requestId, Consumer<Boolean> callback) {
        if (batchingEnabled) {
            // Add to batch queue
            requestBatchQueue.add(() -> {
                boolean result = databaseManager.removeCustomTagRequest(requestId);
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
                }
            });
        } else {
            // Execute immediately
            executeAsync(() -> {
                boolean result = databaseManager.removeCustomTagRequest(requestId);
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
                }
                return result;
            });
        }
    }
    
    /**
     * Purges the tags table asynchronously.
     *
     * @param callback Callback to execute when the operation completes
     */
    public void purgeTagsTableAsync(Consumer<Boolean> callback) {
        executeAsync(() -> {
            boolean result = databaseManager.purgeTagsTable();
            if (result) {
                // Invalidate all tag-related cache entries
                cacheService.invalidateAllTags();
            }
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
            }
            return result;
        });
    }
    
    /**
     * Purges the requests table asynchronously.
     *
     * @param callback Callback to execute when the operation completes
     */
    public void purgeRequestsTableAsync(Consumer<Boolean> callback) {
        executeAsync(() -> {
            boolean result = databaseManager.purgeRequestsTable();
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
            }
            return result;
        });
    }
    
    /**
     * Processes the tag batch queue.
     */
    private void processTagBatchQueue() {
        try {
            int batchSize = Math.min(tagBatchQueue.size(), maxBatchSize);
            if (batchSize == 0) {
                return;
            }
            
            List<Runnable> batch = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                Runnable op = tagBatchQueue.poll();
                if (op != null) {
                    batch.add(op);
                }
            }
            
            plugin.getLogger().fine("Processing tag batch of size " + batch.size());
            
            for (Runnable op : batch) {
                try {
                    op.run();
                } catch (Exception e) {
                    errorHandler.logError("Error processing batched tag operation", e);
                }
            }
        } catch (Exception e) {
            errorHandler.logError("Error processing tag batch queue", e);
        }
    }
    
    /**
     * Processes the request batch queue.
     */
    private void processRequestBatchQueue() {
        try {
            int batchSize = Math.min(requestBatchQueue.size(), maxBatchSize);
            if (batchSize == 0) {
                return;
            }
            
            List<Runnable> batch = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                Runnable op = requestBatchQueue.poll();
                if (op != null) {
                    batch.add(op);
                }
            }
            
            plugin.getLogger().fine("Processing request batch of size " + batch.size());
            
            for (Runnable op : batch) {
                try {
                    op.run();
                } catch (Exception e) {
                    errorHandler.logError("Error processing batched request operation", e);
                }
            }
        } catch (Exception e) {
            errorHandler.logError("Error processing request batch queue", e);
        }
    }
    
    /**
     * Forces processing of all batched operations.
     *
     * @return A CompletableFuture that completes when all operations are processed
     */
    public CompletableFuture<Void> flushAllBatches() {
        return executeAsyncVoid(() -> {
            processTagBatchQueue();
            processRequestBatchQueue();
        });
    }
    
    /**
     * Gets the current size of the tag batch queue.
     *
     * @return The size of the tag batch queue
     */
    public int getTagBatchQueueSize() {
        return tagBatchQueue.size();
    }
    
    /**
     * Gets the current size of the request batch queue.
     *
     * @return The size of the request batch queue
     */
    public int getRequestBatchQueueSize() {
        return requestBatchQueue.size();
    }
    
    /**
     * Shuts down the async database service, cleaning up resources.
     */
    public void shutdown() {
        try {
            // Cancel scheduled tasks
            if (tagBatchTask != null) {
                tagBatchTask.cancel();
            }
            if (requestBatchTask != null) {
                requestBatchTask.cancel();
            }
            
            // Process any remaining batched operations
            plugin.getLogger().info("Processing remaining batched operations before shutdown...");
            processTagBatchQueue();
            processRequestBatchQueue();
            
            // Shutdown executors
            asyncExecutor.shutdown();
            scheduledExecutor.shutdown();
            
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            plugin.getLogger().info("AsyncDatabaseService shutdown complete");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorHandler.logWarning("Async database service shutdown interrupted");
        } catch (Exception e) {
            errorHandler.logError("Error shutting down async database service", e);
        }
    }
}
