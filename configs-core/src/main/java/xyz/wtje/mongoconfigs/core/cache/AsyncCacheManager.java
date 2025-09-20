package xyz.wtje.mongoconfigs.core.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance async cache manager using Caffeine
 * Optimized for MongoDB configs and messages with proper async handling
 */
public class AsyncCacheManager {
    private static final Logger LOGGER = Logger.getLogger(AsyncCacheManager.class.getName());
    
    // Separate caches for different data types
    private final AsyncCache<String, Object> messageCache;
    private final AsyncCache<String, Object> configCache;
    private final AsyncCache<String, Map<String, Object>> documentCache;
    
    // Metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheLoads = new AtomicLong(0);
    
    // Executor for async operations
    private final Executor executor;
    
    // Cache configuration
    private final long maxSize;
    private final Duration ttl;
    private final Duration refreshAfterWrite;
    private final boolean recordStats;
    
    public AsyncCacheManager(long maxSize, Duration ttl, Duration refreshAfterWrite, 
                            boolean recordStats, Executor executor) {
        this.maxSize = maxSize;
        this.ttl = ttl;
        this.refreshAfterWrite = refreshAfterWrite;
        this.recordStats = recordStats;
        this.executor = executor;
        
        // Build message cache with optimal settings
        var messageCacheBuilder = Caffeine.newBuilder()
            .maximumSize(maxSize / 2) // Half for messages
            .executor(executor)
            .removalListener((String key, Object value, RemovalCause cause) -> {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Message cache eviction: " + key + " (" + cause + ")");
                }
            });
            
        if (ttl != null) {
            messageCacheBuilder.expireAfterWrite(ttl);
        }
        if (refreshAfterWrite != null) {
            messageCacheBuilder.refreshAfterWrite(refreshAfterWrite);
        }
        if (recordStats) {
            messageCacheBuilder.recordStats();
        }
        
        this.messageCache = messageCacheBuilder.buildAsync();
        
        // Build config cache with different settings
        var configCacheBuilder = Caffeine.newBuilder()
            .maximumSize(maxSize / 4) // Quarter for configs
            .executor(executor)
            .removalListener((String key, Object value, RemovalCause cause) -> {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Config cache eviction: " + key + " (" + cause + ")");
                }
            });
            
        if (ttl != null) {
            configCacheBuilder.expireAfterWrite(ttl.multipliedBy(2)); // Configs live longer
        }
        if (recordStats) {
            configCacheBuilder.recordStats();
        }
        
        this.configCache = configCacheBuilder.buildAsync();
        
        // Build document cache for full documents
        var documentCacheBuilder = Caffeine.newBuilder()
            .maximumSize(maxSize / 4) // Quarter for documents
            .executor(executor)
            .weigher((String key, Map<String, Object> value) -> value.size()) // Weight by map size
            .removalListener((String key, Map<String, Object> value, RemovalCause cause) -> {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Document cache eviction: " + key + " (" + cause + ")");
                }
            });
            
        if (ttl != null) {
            documentCacheBuilder.expireAfterAccess(ttl); // LRU for documents
        }
        if (recordStats) {
            documentCacheBuilder.recordStats();
        }
        
        this.documentCache = documentCacheBuilder.buildAsync();
        
        LOGGER.info("AsyncCacheManager initialized with maxSize=" + maxSize + 
                   ", ttl=" + ttl + ", refreshAfterWrite=" + refreshAfterWrite);
    }
    
    /**
     * Get message async with loader function
     */
    public CompletableFuture<String> getMessageAsync(String collection, String language, 
                                                     String key, Function<String, String> loader) {
        String cacheKey = buildMessageKey(collection, language, key);
        
        return messageCache.get(cacheKey, k -> {
            cacheMisses.incrementAndGet();
            cacheLoads.incrementAndGet();
            String loaded = loader.apply(k);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Loading message: " + k + " -> " + loaded);
            }
            return loaded;
        }).thenApply(value -> {
            if (value != null) {
                cacheHits.incrementAndGet();
                return value.toString();
            }
            return null;
        });
    }
    
    /**
     * Put message async
     */
    public CompletableFuture<Void> putMessageAsync(String collection, String language, 
                                                   String key, Object value) {
        if (key == null || value == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String cacheKey = buildMessageKey(collection, language, key);
        messageCache.put(cacheKey, CompletableFuture.completedFuture(value));
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Batch put messages for a language
     */
    public CompletableFuture<Void> putMessageBatchAsync(String collection, String language, 
                                                        Map<String, Object> messages) {
        if (messages == null || messages.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : messages.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (key != null && value != null) {
                // Handle nested maps recursively
                if (value instanceof Map) {
                    futures.add(putNestedMessagesAsync(collection, language, key, (Map<?, ?>) value));
                } else {
                    futures.add(putMessageAsync(collection, language, key, value));
                }
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Put nested messages recursively
     */
    private CompletableFuture<Void> putNestedMessagesAsync(String collection, String language,
                                                           String prefix, Map<?, ?> nestedMap) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (Map.Entry<?, ?> entry : nestedMap.entrySet()) {
            if (entry.getKey() == null) continue;
            
            String fullKey = prefix.isEmpty() ? entry.getKey().toString() : 
                           prefix + "." + entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                futures.add(putNestedMessagesAsync(collection, language, fullKey, (Map<?, ?>) value));
            } else if (value != null) {
                futures.add(putMessageAsync(collection, language, fullKey, value));
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Get config value async
     */
    public <T> CompletableFuture<T> getConfigAsync(String collection, String key, 
                                                   T defaultValue, Function<String, T> loader) {
        String cacheKey = buildConfigKey(collection, key);
        
        return configCache.get(cacheKey, k -> {
            cacheMisses.incrementAndGet();
            cacheLoads.incrementAndGet();
            T loaded = loader.apply(k);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Loading config: " + k + " -> " + loaded);
            }
            return loaded;
        }).thenApply(value -> {
            if (value != null) {
                cacheHits.incrementAndGet();
                return (T) value;
            }
            return defaultValue;
        });
    }
    
    /**
     * Put config value async
     */
    public CompletableFuture<Void> putConfigAsync(String collection, String key, Object value) {
        if (key == null || value == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String cacheKey = buildConfigKey(collection, key);
        configCache.put(cacheKey, CompletableFuture.completedFuture(value));
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Batch put config data
     */
    public CompletableFuture<Void> putConfigBatchAsync(String collection, Map<String, Object> configs) {
        if (configs == null || configs.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : configs.entrySet()) {
            futures.add(putConfigAsync(collection, entry.getKey(), entry.getValue()));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Get full document async
     */
    public CompletableFuture<Map<String, Object>> getDocumentAsync(String collection, String docId,
                                                                   Function<String, Map<String, Object>> loader) {
        String cacheKey = buildDocumentKey(collection, docId);
        
        return documentCache.get(cacheKey, k -> {
            cacheMisses.incrementAndGet();
            cacheLoads.incrementAndGet();
            Map<String, Object> loaded = loader.apply(k);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Loading document: " + k);
            }
            return loaded;
        }).thenApply(value -> {
            if (value != null) {
                cacheHits.incrementAndGet();
            }
            return value;
        });
    }
    
    /**
     * Put document async
     */
    public CompletableFuture<Void> putDocumentAsync(String collection, String docId, 
                                                    Map<String, Object> document) {
        if (docId == null || document == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String cacheKey = buildDocumentKey(collection, docId);
        documentCache.put(cacheKey, CompletableFuture.completedFuture(document));
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invalidate collection async
     */
    public CompletableFuture<Void> invalidateCollectionAsync(String collection) {
        return CompletableFuture.runAsync(() -> {
            String prefix = collection + ":";
            
            // Invalidate all entries for this collection
            messageCache.synchronous().asMap().keySet()
                .removeIf(key -> key.startsWith(prefix));
            configCache.synchronous().asMap().keySet()
                .removeIf(key -> key.startsWith(prefix));
            documentCache.synchronous().asMap().keySet()
                .removeIf(key -> key.startsWith(prefix));
                
            LOGGER.info("🧹 Invalidated all cache entries for collection: " + collection);
        }, executor);
    }
    
    /**
     * Invalidate specific language in collection
     */
    public CompletableFuture<Void> invalidateLanguageAsync(String collection, String language) {
        return CompletableFuture.runAsync(() -> {
            String prefix = buildMessageKey(collection, language, "");
            
            messageCache.synchronous().asMap().keySet()
                .removeIf(key -> key.startsWith(prefix));
                
            LOGGER.info("🧹 Invalidated cache entries for " + collection + ":" + language);
        }, executor);
    }
    
    /**
     * Clear all caches
     */
    public CompletableFuture<Void> clearAllAsync() {
        return CompletableFuture.runAsync(() -> {
            messageCache.synchronous().invalidateAll();
            configCache.synchronous().invalidateAll();
            documentCache.synchronous().invalidateAll();
            
            LOGGER.info("🧹 Cleared all caches");
        }, executor);
    }
    
    /**
     * Refresh specific entry
     */
    public CompletableFuture<Void> refreshAsync(String cacheKey) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Try refreshing in all caches
        futures.add(messageCache.synchronous().refresh(cacheKey));
        futures.add(configCache.synchronous().refresh(cacheKey));
        futures.add(documentCache.synchronous().refresh(cacheKey));
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Get cache statistics
     */
    public CacheStatistics getStatistics() {
        CacheStatistics stats = new CacheStatistics();
        
        // Aggregate stats from all caches
        if (recordStats) {
            CacheStats messageStats = messageCache.synchronous().stats();
            CacheStats configStats = configCache.synchronous().stats();
            CacheStats docStats = documentCache.synchronous().stats();
            
            stats.totalHits = messageStats.hitCount() + configStats.hitCount() + docStats.hitCount();
            stats.totalMisses = messageStats.missCount() + configStats.missCount() + docStats.missCount();
            stats.totalLoads = messageStats.loadCount() + configStats.loadCount() + docStats.loadCount();
            stats.totalEvictions = messageStats.evictionCount() + configStats.evictionCount() + docStats.evictionCount();
            
            stats.messageSize = messageCache.synchronous().asMap().size();
            stats.configSize = configCache.synchronous().asMap().size();
            stats.documentSize = documentCache.synchronous().asMap().size();
            
            stats.hitRate = stats.totalHits / (double) (stats.totalHits + stats.totalMisses);
        } else {
            // Use our own counters
            stats.totalHits = cacheHits.get();
            stats.totalMisses = cacheMisses.get();
            stats.totalLoads = cacheLoads.get();
            
            stats.messageSize = messageCache.synchronous().asMap().size();
            stats.configSize = configCache.synchronous().asMap().size();
            stats.documentSize = documentCache.synchronous().asMap().size();
            
            long total = stats.totalHits + stats.totalMisses;
            stats.hitRate = total > 0 ? stats.totalHits / (double) total : 0.0;
        }
        
        return stats;
    }
    
    /**
     * Perform cleanup/maintenance
     */
    public CompletableFuture<Void> cleanupAsync() {
        return CompletableFuture.runAsync(() -> {
            messageCache.synchronous().cleanUp();
            configCache.synchronous().cleanUp();
            documentCache.synchronous().cleanUp();
            
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Cache cleanup completed");
            }
        }, executor);
    }
    
    // Helper methods
    private String buildMessageKey(String collection, String language, String key) {
        return collection + ":" + language + ":" + key;
    }
    
    private String buildConfigKey(String collection, String key) {
        return collection + ":" + key;
    }
    
    private String buildDocumentKey(String collection, String docId) {
        return collection + ":doc:" + docId;
    }
    
    /**
     * Cache statistics container
     */
    public static class CacheStatistics {
        public long totalHits;
        public long totalMisses;
        public long totalLoads;
        public long totalEvictions;
        public long messageSize;
        public long configSize;
        public long documentSize;
        public double hitRate;
        
        @Override
        public String toString() {
            return String.format("CacheStats[hits=%d, misses=%d, loads=%d, evictions=%d, " +
                               "msgSize=%d, cfgSize=%d, docSize=%d, hitRate=%.2f%%]",
                totalHits, totalMisses, totalLoads, totalEvictions,
                messageSize, configSize, documentSize, hitRate * 100);
        }
    }
}