package xyz.wtje.mongoconfigs.core.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;


public class AsyncUtils {
    private static final Logger LOGGER = Logger.getLogger(AsyncUtils.class.getName());
    
    
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
    
    
    public static void runInBackground(Runnable task, Executor executor) {
        CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Background task failed", e);
            }
        }, executor);
    }
    
    
    public static <T> CompletableFuture<T> completedFuture(T value) {
        return CompletableFuture.completedFuture(value);
    }
    
    
    public static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }
}