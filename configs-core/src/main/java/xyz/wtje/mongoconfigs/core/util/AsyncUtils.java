package xyz.wtje.mongoconfigs.core.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilities for better async performance and error handling
 */
public class AsyncUtils {
    private static final Logger LOGGER = Logger.getLogger(AsyncUtils.class.getName());
    
    /**
     * Executes supplier async with proper error handling
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Async operation failed", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }
    
    /**
     * Chains async operations with fallback
     */
    public static <T, U> CompletableFuture<U> thenComposeAsyncWithFallback(
            CompletableFuture<T> future,
            Function<T, CompletableFuture<U>> mapper,
            Supplier<U> fallback,
            Executor executor) {
        
        return future
            .thenComposeAsync(mapper, executor)
            .exceptionally(throwable -> {
                LOGGER.log(Level.WARNING, "Async chain failed, using fallback", throwable);
                return fallback.get();
            });
    }
    
    /**
     * Runs operation in background without blocking
     */
    public static void runInBackground(Runnable task, Executor executor) {
        CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Background task failed", e);
            }
        }, executor);
    }
    
    /**
     * Creates a completed future with null-safe value
     */
    public static <T> CompletableFuture<T> completedFuture(T value) {
        return CompletableFuture.completedFuture(value);
    }
    
    /**
     * Creates a failed future with exception
     */
    public static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }
}