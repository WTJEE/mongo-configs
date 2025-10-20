package xyz.wtje.mongoconfigs.core.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Logger;


public class AsyncCache<K, V> {
    private static final Logger LOGGER = Logger.getLogger(AsyncCache.class.getName());
    
    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, CompletableFuture<V>> loadingFutures = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Executor executor;
    private final long maxSize;
    
    public AsyncCache(Duration ttl, Executor executor, long maxSize) {
        this.ttl = ttl;
        this.executor = executor;
        this.maxSize = maxSize;
    }
    
    public CompletableFuture<V> getAsync(K key, Supplier<CompletableFuture<V>> loader) {
        
        CacheEntry<V> entry = cache.get(key);
        if (entry != null && !isExpired(entry)) {
            return CompletableFuture.completedFuture(entry.value);
        }
        
        
        CompletableFuture<V> loadingFuture = loadingFutures.get(key);
        if (loadingFuture != null) {
            return loadingFuture;
        }
        
        
        CompletableFuture<V> future = loader.get()
            .thenApplyAsync(value -> {
                
                cache.put(key, new CacheEntry<>(value, Instant.now()));
                loadingFutures.remove(key);
                
                
                if (cache.size() > maxSize) {
                    evictOldest();
                }
                
                return value;
            }, executor)
            .exceptionally(throwable -> {
                loadingFutures.remove(key);
                LOGGER.warning("Failed to load cache entry for key: " + key + " - " + throwable.getMessage());
                return null;
            });
        
        loadingFutures.put(key, future);
        return future;
    }
    
    public void put(K key, V value) {
        cache.put(key, new CacheEntry<>(value, Instant.now()));
        if (cache.size() > maxSize) {
            evictOldest();
        }
    }
    
    public void invalidate(K key) {
        cache.remove(key);
        loadingFutures.remove(key);
    }
    
    public void invalidateAll() {
        cache.clear();
        loadingFutures.clear();
    }
    
    private boolean isExpired(CacheEntry<V> entry) {
        if (ttl == null) return false;
        return entry.timestamp.plus(ttl).isBefore(Instant.now());
    }
    
    private void evictOldest() {
        if (cache.size() <= maxSize) return;
        
        
        cache.entrySet().stream()
            .sorted((e1, e2) -> e1.getValue().timestamp.compareTo(e2.getValue().timestamp))
            .limit(cache.size() - maxSize + 100) 
            .forEach(entry -> cache.remove(entry.getKey()));
    }
    
    public int size() {
        return cache.size();
    }
    
    public void cleanExpired() {
        if (ttl == null) return;
        
        Instant cutoff = Instant.now().minus(ttl);
        cache.entrySet().removeIf(entry -> entry.getValue().timestamp.isBefore(cutoff));
    }
    
    private static class CacheEntry<V> {
        final V value;
        final Instant timestamp;
        
        CacheEntry(V value, Instant timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }
}