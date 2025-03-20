package com.blockworlds.utags.utils;

import com.blockworlds.utags.uTags;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Utility class for monitoring and tracking performance metrics in the uTags plugin.
 * Provides methods for tracking operation execution times and server performance.
 */
public class PerformanceMonitor {

    private final uTags plugin;
    private final ErrorHandler errorHandler;
    
    // TPS monitoring
    private final AtomicLong lastPoll = new AtomicLong(System.currentTimeMillis());
    private final double[] tpsHistory = new double[20]; // Stores last 20 TPS readings
    private int tpsIndex = 0;
    private double currentTps = 20.0;
    private BukkitTask tpsTask;
    
    // Operation timing data
    private final Map<String, OperationStats> operationStats = new ConcurrentHashMap<>();
    
    // Memory monitoring
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    // Formatter for numbers
    private final DecimalFormat df = new DecimalFormat("#0.00");

    /**
     * Creates a new PerformanceMonitor.
     *
     * @param plugin The uTags plugin instance
     * @param errorHandler The error handler to use
     */
    public PerformanceMonitor(uTags plugin, ErrorHandler errorHandler) {
        this.plugin = plugin;
        this.errorHandler = errorHandler;
    }
    
    /**
     * Starts performance monitoring.
     */
    public void startMonitoring() {
        // Start TPS monitoring task
        tpsTask = Bukkit.getScheduler().runTaskTimer(plugin, this::measureTps, 40, 40); // Every 2 seconds
        
        plugin.getLogger().info("Performance monitoring started");
    }
    
    /**
     * Stops performance monitoring.
     */
    public void stopMonitoring() {
        if (tpsTask != null) {
            tpsTask.cancel();
            tpsTask = null;
        }
        
        plugin.getLogger().info("Performance monitoring stopped");
    }
    
    /**
     * Measures the current server TPS (Ticks Per Second).
     */
    private void measureTps() {
        try {
            long now = System.currentTimeMillis();
            long elapsed = now - lastPoll.getAndSet(now);
            
            // Calculate TPS: 20 ticks should take 1000ms, so 1000/elapsed * 20 = current TPS
            double tps = 20.0 * 1000.0 / elapsed;
            
            // Cap at 20 TPS and ensure it's not negative
            tps = Math.min(20.0, Math.max(0.0, tps));
            
            // Update TPS history
            tpsHistory[tpsIndex] = tps;
            tpsIndex = (tpsIndex + 1) % tpsHistory.length;
            
            // Calculate average TPS
            currentTps = calculateAverageTps();
            
            // Log warning if TPS is very low
            if (currentTps < 10.0) {
                plugin.getLogger().warning("Server TPS is critically low: " + df.format(currentTps));
                // Log detailed performance stats
                plugin.getLogger().warning(getStatistics());
            }
        } catch (Exception e) {
            errorHandler.logError("Error measuring TPS", e);
        }
    }
    
    /**
     * Calculates the average TPS from the history.
     *
     * @return The average TPS
     */
    private double calculateAverageTps() {
        double sum = 0.0;
        int count = 0;
        
        for (double tps : tpsHistory) {
            if (tps > 0) {
                sum += tps;
                count++;
            }
        }
        
        return count > 0 ? sum / count : 20.0;
    }
    
    /**
     * Gets the current server TPS.
     *
     * @return The current TPS
     */
    public double getCurrentTps() {
        return currentTps;
    }
    
    /**
     * Tracks the execution time of an operation.
     *
     * @param <T> The return type of the operation
     * @param operationName The name of the operation
     * @param operation The operation to track
     * @return The result of the operation
     */
    public <T> T trackOperation(String operationName, Supplier<T> operation) {
        long startTime = System.nanoTime();
        T result = null;
        boolean success = false;
        
        try {
            result = operation.get();
            success = true;
            return result;
        } finally {
            long endTime = System.nanoTime();
            long durationNanos = endTime - startTime;
            
            recordOperationStats(operationName, durationNanos, success);
        }
    }
    
    /**
     * Records statistics for an operation.
     *
     * @param operationName The name of the operation
     * @param durationNanos The duration of the operation in nanoseconds
     * @param success Whether the operation was successful
     */
    private void recordOperationStats(String operationName, long durationNanos, boolean success) {
        try {
            operationStats.computeIfAbsent(operationName, name -> new OperationStats())
                .recordExecution(durationNanos, success);
        } catch (Exception e) {
            // Don't let stats recording crash anything
            errorHandler.logError("Error recording operation stats for " + operationName, e);
        }
    }
    
    /**
     * Resets all operation statistics.
     */
    public void resetStats() {
        operationStats.clear();
    }
    
    /**
     * Gets basic performance statistics.
     *
     * @return A string with basic performance data
     */
    public String getBasicStatistics() {
        StringBuilder stats = new StringBuilder("Performance Summary: ");
        stats.append("TPS=").append(df.format(currentTps));
        stats.append(", Memory=").append(getMemoryUsage());
        stats.append(", Operations=").append(operationStats.size());
        
        // Add slowest operation
        String slowestOp = getSlowestOperation();
        if (slowestOp != null) {
            stats.append(", Slowest=").append(slowestOp);
        }
        
        return stats.toString();
    }
    
    /**
     * Gets a string with detailed performance statistics.
     *
     * @return A string with detailed performance data
     */
    public String getStatistics() {
        StringBuilder stats = new StringBuilder("Performance Statistics:\n");
        
        // Server metrics
        stats.append("- TPS: ").append(df.format(currentTps)).append("\n");
        stats.append("- Memory: ").append(getMemoryUsage()).append("\n");
        
        // Operation stats
        stats.append("- Operations:\n");
        
        // Sort operations by average time (descending)
        operationStats.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue().getAverageTimeMs(), e1.getValue().getAverageTimeMs()))
            .forEach(entry -> {
                String name = entry.getKey();
                OperationStats opStats = entry.getValue();
                
                stats.append("  * ").append(name).append(": ");
                stats.append("Count=").append(opStats.getCount()).append(", ");
                stats.append("Avg=").append(df.format(opStats.getAverageTimeMs())).append("ms, ");
                stats.append("Max=").append(df.format(opStats.getMaxTimeMs())).append("ms, ");
                stats.append("Success=").append(opStats.getSuccessRate()).append("%\n");
            });
        
        return stats.toString();
    }
    
    /**
     * Gets the name of the slowest operation and its average time.
     *
     * @return A string with the slowest operation info, or null if no operations tracked
     */
    private String getSlowestOperation() {
        if (operationStats.isEmpty()) {
            return null;
        }
        
        Map.Entry<String, OperationStats> slowest = null;
        
        for (Map.Entry<String, OperationStats> entry : operationStats.entrySet()) {
            if (slowest == null || entry.getValue().getAverageTimeMs() > slowest.getValue().getAverageTimeMs()) {
                slowest = entry;
            }
        }
        
        if (slowest != null) {
            return slowest.getKey() + "(" + df.format(slowest.getValue().getAverageTimeMs()) + "ms)";
        }
        
        return null;
    }
    
    /**
     * Gets current memory usage information.
     *
     * @return A string with memory usage info
     */
    private String getMemoryUsage() {
        long used = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long max = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        int percentage = (int) ((double) used / max * 100);
        
        return used + "MB/" + max + "MB (" + percentage + "%)";
    }
    
    /**
     * Class for tracking statistics about operations.
     */
    private static class OperationStats {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalTimeNanos = new AtomicLong(0);
        private final AtomicLong maxTimeNanos = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        
        /**
         * Records an execution of the operation.
         *
         * @param durationNanos The duration in nanoseconds
         * @param success Whether the operation was successful
         */
        public void recordExecution(long durationNanos, boolean success) {
            count.incrementAndGet();
            totalTimeNanos.addAndGet(durationNanos);
            
            // Update max time if this execution was longer
            long currentMax;
            do {
                currentMax = maxTimeNanos.get();
                if (durationNanos <= currentMax) {
                    break;
                }
            } while (!maxTimeNanos.compareAndSet(currentMax, durationNanos));
            
            if (success) {
                successCount.incrementAndGet();
            }
        }
        
        /**
         * Gets the total number of executions.
         *
         * @return The total count
         */
        public long getCount() {
            return count.get();
        }
        
        /**
         * Gets the average execution time in milliseconds.
         *
         * @return The average time in milliseconds
         */
        public double getAverageTimeMs() {
            long executions = count.get();
            if (executions == 0) {
                return 0.0;
            }
            
            return (double) totalTimeNanos.get() / executions / 1_000_000.0;
        }
        
        /**
         * Gets the maximum execution time in milliseconds.
         *
         * @return The maximum time in milliseconds
         */
        public double getMaxTimeMs() {
            return (double) maxTimeNanos.get() / 1_000_000.0;
        }
        
        /**
         * Gets the success rate as a percentage.
         *
         * @return The success rate percentage
         */
        public int getSuccessRate() {
            long executions = count.get();
            if (executions == 0) {
                return 100;
            }
            
            return (int) ((double) successCount.get() / executions * 100);
        }
    }
}
