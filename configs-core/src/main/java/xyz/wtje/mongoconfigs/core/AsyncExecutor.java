package xyz.wtje.mongoconfigs.core;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance async executor for MongoDB operations
 * Optimized for BSON/JSON conversions and cache operations
 */
public class AsyncExecutor {
    private static final Logger LOGGER = Logger.getLogger(AsyncExecutor.class.getName());
    
    // Separate thread pools for different operation types
    private final ExecutorService ioPool;          // For I/O operations (MongoDB)
    private final ExecutorService computePool;     // For CPU-intensive tasks (JSON parsing)
    private final ScheduledExecutorService scheduler; // For scheduled tasks
    private final ForkJoinPool fjPool;            // For parallel streaming operations
    
    // Metrics
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private final AtomicInteger completedTasks = new AtomicInteger(0);
    
    public AsyncExecutor(int ioThreads, int computeThreads) {
        // I/O pool - optimized for blocking I/O operations
        this.ioPool = new ThreadPoolExecutor(
            ioThreads / 2,  // core pool size
            ioThreads,      // max pool size
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "MongoConfigs-IO-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // Compute pool - optimized for CPU-intensive tasks
        this.computePool = new ThreadPoolExecutor(
            computeThreads,
            computeThreads * 2,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "MongoConfigs-Compute-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY + 1); // Slightly higher priority
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // Scheduler for periodic tasks
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "MongoConfigs-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // ForkJoin pool for parallel operations
        this.fjPool = new ForkJoinPool(
            computeThreads,
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            (t, e) -> LOGGER.log(Level.WARNING, "Uncaught exception in ForkJoin pool", e),
            true // async mode
        );
        
        // Start metrics reporting
        scheduler.scheduleAtFixedRate(this::reportMetrics, 1, 5, TimeUnit.MINUTES);
        
        LOGGER.info("AsyncExecutor initialized with " + ioThreads + " I/O threads and " + 
                   computeThreads + " compute threads");
    }
    
    /**
     * Execute I/O bound task (MongoDB operations)
     */
    public CompletableFuture<Void> executeIO(Runnable task) {
        activeTasks.incrementAndGet();
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } finally {
                activeTasks.decrementAndGet();
                completedTasks.incrementAndGet();
            }
        }, ioPool);
    }
    
    /**
     * Execute CPU-intensive task (JSON parsing, BSON conversion)
     */
    public CompletableFuture<Void> executeCompute(Runnable task) {
        activeTasks.incrementAndGet();
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } finally {
                activeTasks.decrementAndGet();
                completedTasks.incrementAndGet();
            }
        }, computePool);
    }
    
    /**
     * Supply value from I/O operation
     */
    public <T> CompletableFuture<T> supplyIO(Callable<T> supplier) {
        activeTasks.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                activeTasks.decrementAndGet();
                completedTasks.incrementAndGet();
            }
        }, ioPool);
    }
    
    /**
     * Supply value from compute operation
     */
    public <T> CompletableFuture<T> supplyCompute(Callable<T> supplier) {
        activeTasks.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                activeTasks.decrementAndGet();
                completedTasks.incrementAndGet();
            }
        }, computePool);
    }
    
    /**
     * Execute with timeout
     */
    public <T> CompletableFuture<T> executeWithTimeout(Callable<T> task, long timeout, TimeUnit unit) {
        CompletableFuture<T> future = supplyIO(task);
        
        // Schedule timeout
        ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("Operation timed out after " + timeout + " " + unit));
            }
        }, timeout, unit);
        
        // Cancel timeout if task completes
        future.whenComplete((result, error) -> timeoutFuture.cancel(false));
        
        return future;
    }
    
    /**
     * Schedule periodic task
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(() -> {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in scheduled task", e);
            }
        }, initialDelay, period, unit);
    }
    
    /**
     * Get executor for I/O operations
     */
    public Executor getIOExecutor() {
        return ioPool;
    }
    
    /**
     * Get executor for compute operations  
     */
    public Executor getComputeExecutor() {
        return computePool;
    }
    
    /**
     * Get ForkJoin pool for parallel operations
     */
    public ForkJoinPool getForkJoinPool() {
        return fjPool;
    }
    
    /**
     * Batch execute multiple tasks in parallel
     */
    public CompletableFuture<Void> executeBatch(Runnable... tasks) {
        CompletableFuture<?>[] futures = new CompletableFuture[tasks.length];
        for (int i = 0; i < tasks.length; i++) {
            futures[i] = executeCompute(tasks[i]);
        }
        return CompletableFuture.allOf(futures);
    }
    
    /**
     * Execute task with retry logic
     */
    public <T> CompletableFuture<T> executeWithRetry(Callable<T> task, int maxRetries, long delayMs) {
        return executeWithRetryInternal(task, maxRetries, delayMs, 0);
    }
    
    private <T> CompletableFuture<T> executeWithRetryInternal(Callable<T> task, int maxRetries, long delayMs, int attempt) {
        return supplyIO(task)
            .exceptionally(throwable -> {
                if (attempt < maxRetries) {
                    LOGGER.info("Retrying task, attempt " + (attempt + 1) + " of " + maxRetries);
                    try {
                        Thread.sleep(delayMs * (attempt + 1)); // Exponential backoff
                        return executeWithRetryInternal(task, maxRetries, delayMs, attempt + 1).join();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                } else {
                    throw new CompletionException("Task failed after " + maxRetries + " retries", throwable);
                }
            });
    }
    
    /**
     * Report metrics
     */
    private void reportMetrics() {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(String.format("AsyncExecutor metrics: active=%d, completed=%d, ioQueue=%d, computeQueue=%d",
                activeTasks.get(),
                completedTasks.get(),
                ((ThreadPoolExecutor) ioPool).getQueue().size(),
                ((ThreadPoolExecutor) computePool).getQueue().size()
            ));
        }
    }
    
    /**
     * Shutdown all executors
     */
    public void shutdown() {
        LOGGER.info("Shutting down AsyncExecutor...");
        
        scheduler.shutdown();
        ioPool.shutdown();
        computePool.shutdown();
        fjPool.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!ioPool.awaitTermination(5, TimeUnit.SECONDS)) {
                ioPool.shutdownNow();
            }
            if (!computePool.awaitTermination(5, TimeUnit.SECONDS)) {
                computePool.shutdownNow();
            }
            if (!fjPool.awaitTermination(5, TimeUnit.SECONDS)) {
                fjPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Interrupted during shutdown", e);
        }
        
        LOGGER.info("AsyncExecutor shutdown complete. Total tasks completed: " + completedTasks.get());
    }
    
    /**
     * Check if executor is healthy
     */
    public boolean isHealthy() {
        return !ioPool.isShutdown() && !computePool.isShutdown() && 
               !scheduler.isShutdown() && !fjPool.isShutdown();
    }
}