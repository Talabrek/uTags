package com.blockworlds.utags.services;

import com.blockworlds.utags.CustomTagRequest;
import com.blockworlds.utags.DatabaseManager;
import com.blockworlds.utags.Tag;
import com.blockworlds.utags.uTags;
import com.blockworlds.utags.utils.ErrorHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Service class for handling batch database operations in the uTags plugin.
 * Provides methods for batching similar database operations for better performance.
 */
public class BatchProcessingService {

    private final uTags plugin;
    private final DatabaseManager databaseManager;
    private final ErrorHandler errorHandler;
    private final CacheService cacheService;
    
    // Batch operation queues
    private final Queue<Tag> tagAddQueue;
    private final Queue<String> tagDeleteQueue;
    private final Queue<TagAttributeUpdate> tagUpdateQueue;
    private final Queue<CustomTagRequest> requestAddQueue;
    private final Queue<Integer> requestDeleteQueue;
    
    // Configuration
    private final int batchSize;
    private final long processIntervalMs;
    private final boolean autoFlush;
    
    // Statistics
    private final AtomicLong tagOperationsProcessed;
    private final AtomicLong requestOperationsProcessed;
    private final AtomicLong batchesProcessed;
    
    // Executor for scheduled processing
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a new BatchProcessingService.
     *
     * @param plugin The uTags plugin instance
     * @param errorHandler The error handler to use
     * @param cacheService The cache service to use
     * @param batchSize The maximum batch size
     * @param processIntervalMs The processing interval in milliseconds
     * @param autoFlush Whether to automatically flush batches
     */
    public BatchProcessingService(uTags plugin, ErrorHandler errorHandler, CacheService cacheService,
                                int batchSize, long processIntervalMs, boolean autoFlush) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.errorHandler = errorHandler;
        this.cacheService = cacheService;
        this.batchSize = batchSize;
        this.processIntervalMs = processIntervalMs;
        this.autoFlush = autoFlush;
        
        // Initialize queues
        this.tagAddQueue = new ConcurrentLinkedQueue<>();
        this.tagDeleteQueue = new ConcurrentLinkedQueue<>();
        this.tagUpdateQueue = new ConcurrentLinkedQueue<>();
        this.requestAddQueue = new ConcurrentLinkedQueue<>();
        this.requestDeleteQueue = new ConcurrentLinkedQueue<>();
        
        // Initialize statistics
        this.tagOperationsProcessed = new AtomicLong(0);
        this.requestOperationsProcessed = new AtomicLong(0);
        this.batchesProcessed = new AtomicLong(0);
        
        // Initialize scheduler
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "uTags-Batch-Processor");
            thread.setDaemon(true);
            return thread;
        });
        
        // Start scheduled processing if auto-flush is enabled
        if (autoFlush) {
            startScheduledProcessing();
        }
        
        plugin.getLogger().info("BatchProcessingService initialized with batch size " + batchSize + 
                               " and " + (autoFlush ? "auto-flush enabled" : "auto-flush disabled"));
    }
    
    /**
     * Starts scheduled batch processing.
     */
    private void startScheduledProcessing() {
        scheduler.scheduleAtFixedRate(
            this::processBatches, 
            processIntervalMs, 
            processIntervalMs, 
            TimeUnit.MILLISECONDS
        );
        
        plugin.getLogger().info("Scheduled batch processing started with interval " + 
                              (processIntervalMs / 1000) + "s");
    }
    
    /**
     * Adds a tag to the batch queue for creation or update.
     *
     * @param tag The tag to add or update
     */
    public void addTag(Tag tag) {
        if (tag == null) {
            return;
        }
        
        tagAddQueue.add(tag);
        
        // Process immediately if queue reaches batch size and auto-flush is enabled
        if (autoFlush && tagAddQueue.size() >= batchSize) {
            processTagAddBatch();
        }
    }
    
    /**
     * Adds a tag to the batch queue for deletion.
     *
     * @param tagName The name of the tag to delete
     */
    public void deleteTag(String tagName) {
        if (tagName == null || tagName.isEmpty()) {
            return;
        }
        
        tagDeleteQueue.add(tagName);
        
        // Process immediately if queue reaches batch size and auto-flush is enabled
        if (autoFlush && tagDeleteQueue.size() >= batchSize) {
            processTagDeleteBatch();
        }
    }
    
    /**
     * Adds a tag attribute update to the batch queue.
     *
     * @param tagName The name of the tag to update
     * @param attribute The attribute to update
     * @param newValue The new value for the attribute
     */
    public void updateTagAttribute(String tagName, String attribute, String newValue) {
        if (tagName == null || tagName.isEmpty() || attribute == null || attribute.isEmpty()) {
            return;
        }
        
        tagUpdateQueue.add(new TagAttributeUpdate(tagName, attribute, newValue));
        
        // Process immediately if queue reaches batch size and auto-flush is enabled
        if (autoFlush && tagUpdateQueue.size() >= batchSize) {
            processTagUpdateBatch();
        }
    }
    
    /**
     * Adds a custom tag request to the batch queue.
     *
     * @param request The custom tag request to add
     */
    public void addCustomTagRequest(CustomTagRequest request) {
        if (request == null) {
            return;
        }
        
        requestAddQueue.add(request);
        
        // Process immediately if queue reaches batch size and auto-flush is enabled
        if (autoFlush && requestAddQueue.size() >= batchSize) {
            processRequestAddBatch();
        }
    }
    
    /**
     * Adds a custom tag request deletion to the batch queue.
     *
     * @param requestId The ID of the request to delete
     */
    public void deleteCustomTagRequest(int requestId) {
        if (requestId <= 0) {
            return;
        }
        
        requestDeleteQueue.add(requestId);
        
        // Process immediately if queue reaches batch size and auto-flush is enabled
        if (autoFlush && requestDeleteQueue.size() >= batchSize) {
            processRequestDeleteBatch();
        }
    }
    
    /**
     * Processes all pending batch operations.
     */
    public void processBatches() {
        try {
            // Process tag operations
            processTagAddBatch();
            processTagDeleteBatch();
            processTagUpdateBatch();
            
            // Process request operations
            processRequestAddBatch();
            processRequestDeleteBatch();
        } catch (Exception e) {
            errorHandler.logError("Error processing batches", e);
        }
    }
    
    /**
     * Processes the tag add/update batch queue.
     */
    private void processTagAddBatch() {
        int queueSize = tagAddQueue.size();
        if (queueSize == 0) {
            return;
        }
        
        int batchCount = Math.min(queueSize, batchSize);
        List<Tag> batch = new ArrayList<>(batchCount);
        
        // Collect tags for batch processing
        for (int i = 0; i < batchCount; i++) {
            Tag tag = tagAddQueue.poll();
            if (tag != null) {
                batch.add(tag);
            }
        }
        
        if (batch.isEmpty()) {
            return;
        }
        
        // Process batch
        try (Connection connection = databaseManager.getConnection()) {
            // Start a transaction for the batch
            connection.setAutoCommit(false);
            
            try {
                // Prepare batch statement
                String sql = "REPLACE INTO tags (name, display, type, public, color, material, weight) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    
                    for (Tag tag : batch) {
                        stmt.setString(1, tag.getName());
                        stmt.setString(2, tag.getDisplay());
                        stmt.setString(3, tag.getType().toString());
                        stmt.setBoolean(4, tag.isPublic());
                        stmt.setBoolean(5, tag.isColor());
                        stmt.setString(6, DatabaseManager.serializeMaterial(tag.getMaterial()));
                        stmt.setInt(7, tag.getWeight());
                        
                        stmt.addBatch();
                        
                        // Invalidate cache for this tag
                        cacheService.invalidateTag(tag.getName());
                    }
                    
                    // Execute batch
                    int[] results = stmt.executeBatch();
                    
                    // Commit transaction
                    connection.commit();
                    
                    // Update statistics
                    tagOperationsProcessed.addAndGet(results.length);
                    batchesProcessed.incrementAndGet();
                    
                    plugin.getLogger().fine("Processed tag add/update batch of size " + results.length);
                }
            } catch (SQLException e) {
                // Rollback on error
                connection.rollback();
                throw e;
            } finally {
                // Restore auto-commit
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            errorHandler.logError("Error processing tag add/update batch", e);
            
            // Requeue failed operations if possible
            for (Tag tag : batch) {
                try {
                    // Attempt individual processing
                    boolean success = databaseManager.addTagToDatabase(tag);
                    if (success) {
                        cacheService.invalidateTag(tag.getName());
                        tagOperationsProcessed.incrementAndGet();
                    }
                } catch (Exception ex) {
                    errorHandler.logError("Error reprocessing tag " + tag.getName(), ex);
                }
            }
        }
    }
    
    /**
     * Processes the tag delete batch queue.
     */
    private void processTagDeleteBatch() {
        int queueSize = tagDeleteQueue.size();
        if (queueSize == 0) {
            return;
        }
        
        int batchCount = Math.min(queueSize, batchSize);
        List<String> batch = new ArrayList<>(batchCount);
        
        // Collect tag names for batch processing
        for (int i = 0; i < batchCount; i++) {
            String tagName = tagDeleteQueue.poll();
            if (tagName != null && !tagName.isEmpty()) {
                batch.add(tagName);
            }
        }
        
        if (batch.isEmpty()) {
            return;
        }
        
        // Process batch
        try (Connection connection = databaseManager.getConnection()) {
            // Start a transaction for the batch
            connection.setAutoCommit(false);
            
            try {
                // Prepare batch statement
                String sql = "DELETE FROM tags WHERE name = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    
                    for (String tagName : batch) {
                        stmt.setString(1, tagName);
                        stmt.addBatch();
                        
                        // Invalidate cache for this tag
                        cacheService.invalidateTag(tagName);
                    }
                    
                    // Execute batch
                    int[] results = stmt.executeBatch();
                    
                    // Commit transaction
                    connection.commit();
                    
                    // Update statistics
                    tagOperationsProcessed.addAndGet(results.length);
                    batchesProcessed.incrementAndGet();
                    
                    plugin.getLogger().fine("Processed tag delete batch of size " + results.length);
                }
            } catch (SQLException e) {
                // Rollback on error
                connection.rollback();
                throw e;
            } finally {
                // Restore auto-commit
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            errorHandler.logError("Error processing tag delete batch", e);
            
            // Requeue failed operations if possible
            for (String tagName : batch) {
                try {
                    // Attempt individual processing
                    boolean success = databaseManager.deleteTagFromDatabase(tagName);
                    if (success) {
                        cacheService.invalidateTag(tagName);
                        tagOperationsProcessed.incrementAndGet();
                    }
                } catch (Exception ex) {
                    errorHandler.logError("Error reprocessing tag deletion " + tagName, ex);
                }
            }
        }
    }
    
    /**
     * Processes the tag attribute update batch queue.
     */
    private void processTagUpdateBatch() {
        int queueSize = tagUpdateQueue.size();
        if (queueSize == 0) {
            return;
        }
        
        // Group updates by attribute for more efficient batching
        List<TagAttributeUpdate> updates = new ArrayList<>(queueSize);
        for (int i = 0; i < queueSize; i++) {
            TagAttributeUpdate update = tagUpdateQueue.poll();
            if (update != null) {
                updates.add(update);
            }
        }
        
        if (updates.isEmpty()) {
            return;
        }
        
        // Group updates by attribute
        var updatesByAttribute = updates.stream()
            .filter(update -> databaseManager.isValidAttribute(update.getAttribute()))
            .collect(java.util.stream.Collectors.groupingBy(TagAttributeUpdate::getAttribute));
        
        // Process each attribute group
        for (var entry : updatesByAttribute.entrySet()) {
            String attribute = entry.getKey();
            List<TagAttributeUpdate> attributeUpdates = entry.getValue();
            
            try (Connection connection = databaseManager.getConnection()) {
                // Start a transaction for the batch
                connection.setAutoCommit(false);
                
                try {
                    // Prepare batch statement
                    String sql = "UPDATE tags SET " + attribute + " = ? WHERE name = ?";
                    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                        
                        for (TagAttributeUpdate update : attributeUpdates) {
                            stmt.setString(1, update.getNewValue());
                            stmt.setString(2, update.getTagName());
                            stmt.addBatch();
                            
                            // Invalidate cache for this tag
                            cacheService.invalidateTag(update.getTagName());
                        }
                        
                        // Execute batch
                        int[] results = stmt.executeBatch();
                        
                        // Commit transaction
                        connection.commit();
                        
                        // Update statistics
                        tagOperationsProcessed.addAndGet(results.length);
                        
                        plugin.getLogger().fine("Processed tag attribute update batch of size " + 
                                             results.length + " for attribute " + attribute);
                    }
                } catch (SQLException e) {
                    // Rollback on error
                    connection.rollback();
                    throw e;
                } finally {
                    // Restore auto-commit
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                errorHandler.logError("Error processing tag attribute update batch for " + attribute, e);
                
                // Requeue failed operations if possible
                for (TagAttributeUpdate update : attributeUpdates) {
                    try {
                        // Attempt individual processing
                        boolean success = databaseManager.editTagAttribute(
                            update.getTagName(), update.getAttribute(), update.getNewValue());
                        if (success) {
                            cacheService.invalidateTag(update.getTagName());
                            tagOperationsProcessed.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        errorHandler.logError("Error reprocessing tag update " + update.getTagName(), ex);
                    }
                }
            }
        }
        
        // Update batch counter
        batchesProcessed.incrementAndGet();
    }
    
    /**
     * Processes the request add batch queue.
     */
    private void processRequestAddBatch() {
        int queueSize = requestAddQueue.size();
        if (queueSize == 0) {
            return;
        }
        
        int batchCount = Math.min(queueSize, batchSize);
        List<CustomTagRequest> batch = new ArrayList<>(batchCount);
        
        // Collect requests for batch processing
        for (int i = 0; i < batchCount; i++) {
            CustomTagRequest request = requestAddQueue.poll();
            if (request != null) {
                batch.add(request);
            }
        }
        
        if (batch.isEmpty()) {
            return;
        }
        
        // Process batch
        try (Connection connection = databaseManager.getConnection()) {
            // Start a transaction for the batch
            connection.setAutoCommit(false);
            
            try {
                // Prepare batch statement
                String sql = "INSERT INTO tag_requests (player_uuid, player_name, tag_display) VALUES (?, ?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    
                    for (CustomTagRequest request : batch) {
                        stmt.setString(1, request.getPlayerUuid().toString());
                        stmt.setString(2, request.getPlayerName());
                        stmt.setString(3, request.getTagDisplay());
                        stmt.addBatch();
                    }
                    
                    // Execute batch
                    int[] results = stmt.executeBatch();
                    
                    // Commit transaction
                    connection.commit();
                    
                    // Update statistics
                    requestOperationsProcessed.addAndGet(results.length);
                    batchesProcessed.incrementAndGet();
                    
                    plugin.getLogger().fine("Processed request add batch of size " + results.length);
                }
            } catch (SQLException e) {
                // Rollback on error
                connection.rollback();
                throw e;
            } finally {
                // Restore auto-commit
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            errorHandler.logError("Error processing request add batch", e);
            
            // Requeue failed operations if possible
            for (CustomTagRequest request : batch) {
                try {
                    // Attempt individual processing
                    boolean success = databaseManager.createCustomTagRequest(
                        request.getPlayerUuid(), request.getPlayerName(), request.getTagDisplay());
                    if (success) {
                        requestOperationsProcessed.incrementAndGet();
                    }
                } catch (Exception ex) {
                    errorHandler.logError("Error reprocessing request " + 
                                       request.getPlayerName() + " - " + request.getTagDisplay(), ex);
                }
            }
        }
    }
    
    /**
     * Processes the request delete batch queue.
     */
    private void processRequestDeleteBatch() {
        int queueSize = requestDeleteQueue.size();
        if (queueSize == 0) {
            return;
        }
        
        int batchCount = Math.min(queueSize, batchSize);
        List<Integer> batch = new ArrayList<>(batchCount);
        
        // Collect request IDs for batch processing
        for (int i = 0; i < batchCount; i++) {
            Integer requestId = requestDeleteQueue.poll();
            if (requestId != null && requestId > 0) {
                batch.add(requestId);
            }
        }
        
        if (batch.isEmpty()) {
            return;
        }
        
        // Process batch
        try (Connection connection = databaseManager.getConnection()) {
            // Start a transaction for the batch
            connection.setAutoCommit(false);
            
            try {
                // Prepare batch statement
                String sql = "DELETE FROM tag_requests WHERE id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    
                    for (Integer requestId : batch) {
                        stmt.setInt(1, requestId);
                        stmt.addBatch();
                    }
                    
                    // Execute batch
                    int[] results = stmt.executeBatch();
                    
                    // Commit transaction
                    connection.commit();
                    
                    // Update statistics
                    requestOperationsProcessed.addAndGet(results.length);
                    batchesProcessed.incrementAndGet();
                    
                    plugin.getLogger().fine("Processed request delete batch of size " + results.length);
                }
            } catch (SQLException e) {
                // Rollback on error
                connection.rollback();
                throw e;
            } finally {
                // Restore auto-commit
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            errorHandler.logError("Error processing request delete batch", e);
            
            // Requeue failed operations if possible
            for (Integer requestId : batch) {
                try {
                    // Attempt individual processing
                    boolean success = databaseManager.removeCustomTagRequest(requestId);
                    if (success) {
                        requestOperationsProcessed.incrementAndGet();
                    }
                } catch (Exception ex) {
                    errorHandler.logError("Error reprocessing request deletion " + requestId, ex);
                }
            }
        }
    }
    
    /**
     * Gets the current size of the tag add queue.
     *
     * @return The size of the tag add queue
     */
    public int getTagAddQueueSize() {
        return tagAddQueue.size();
    }
    
    /**
     * Gets the current size of the tag delete queue.
     *
     * @return The size of the tag delete queue
     */
    public int getTagDeleteQueueSize() {
        return tagDeleteQueue.size();
    }
    
    /**
     * Gets the current size of the tag update queue.
     *
     * @return The size of the tag update queue
     */
    public int getTagUpdateQueueSize() {
        return tagUpdateQueue.size();
    }
    
    /**
     * Gets the current size of the request add queue.
     *
     * @return The size of the request add queue
     */
    public int getRequestAddQueueSize() {
        return requestAddQueue.size();
    }
    
    /**
     * Gets the current size of the request delete queue.
     *
     * @return The size of the request delete queue
     */
    public int getRequestDeleteQueueSize() {
        return requestDeleteQueue.size();
    }
    
    /**
     * Gets statistics about the batch processing.
     *
     * @return A string with batch processing statistics
     */
    public String getStatistics() {
        return String.format(
            "Batch Stats: Batches=%d, Tag Ops=%d, Request Ops=%d, Queued: Tags=%d, Requests=%d",
            batchesProcessed.get(), tagOperationsProcessed.get(), requestOperationsProcessed.get(),
            getTagAddQueueSize() + getTagDeleteQueueSize() + getTagUpdateQueueSize(),
            getRequestAddQueueSize() + getRequestDeleteQueueSize()
        );
    }
    
    /**
     * Logs current batch processing statistics.
     */
    public void logStatistics() {
        plugin.getLogger().info(getStatistics());
    }
    
    /**
     * Shuts down the batch processing service, flushing any pending operations.
     */
    public void shutdown() {
        try {
            // Stop scheduled processing
            scheduler.shutdown();
            
            // Process any remaining batched operations
            plugin.getLogger().info("Processing remaining batched operations before shutdown...");
            processBatches();
            
            // Log final statistics
            logStatistics();
            
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorHandler.logWarning("Batch processing service shutdown interrupted");
        } catch (Exception e) {
            errorHandler.logError("Error shutting down batch processing service", e);
        }
    }
    
    /**
     * Class representing a tag attribute update operation.
     */
    private static class TagAttributeUpdate {
        private final String tagName;
        private final String attribute;
        private final String newValue;
        
        /**
         * Creates a new TagAttributeUpdate.
         *
         * @param tagName The name of the tag to update
         * @param attribute The attribute to update
         * @param newValue The new value for the attribute
         */
        public TagAttributeUpdate(String tagName, String attribute, String newValue) {
            this.tagName = tagName;
            this.attribute = attribute;
            this.newValue = newValue;
        }
        
        /**
         * Gets the name of the tag to update.
         *
         * @return The tag name
         */
        public String getTagName() {
            return tagName;
        }
        
        /**
         * Gets the attribute to update.
         *
         * @return The attribute
         */
        public String getAttribute() {
            return attribute;
        }
        
        /**
         * Gets the new value for the attribute.
         *
         * @return The new value
         */
        public String getNewValue() {
            return newValue;
        }
    }
}
