package xyz.wtje.mongoconfigs.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheManager {
    private static final Logger LOGGER = Logger.getLogger(CacheManager.class.getName());

    private final Cache<String, Object> messageCache;
    private final Cache<String, Object> configCache;
    private final AtomicLong configRequests;
    private final AtomicLong messageRequests;
    
    // Lock for atomic cache refresh operations - prevents reads during reload
    private final ReadWriteLock messageRefreshLock = new ReentrantReadWriteLock();
    private final ReadWriteLock configRefreshLock = new ReentrantReadWriteLock();
    
    // Virtual thread executor for non-blocking async operations (Java 21+)
    private final ExecutorService virtualExecutor;
    
    // Shutdown flag to prevent operations after close
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Listeners for invalidation events
    private final Set<Consumer<String>> invalidationListeners = new CopyOnWriteArraySet<>();
    
    // Listeners for refresh completion events
    private final Set<Consumer<String>> refreshCompleteListeners = new CopyOnWriteArraySet<>();

    public CacheManager() {
        this(0, null, true);
    }

    public CacheManager(long maxSize, Duration ttl) {
        this(maxSize, ttl, true);
    }

    public CacheManager(long maxSize, Duration ttl, boolean recordStats) {

        this.configRequests = new AtomicLong(0);
        this.messageRequests = new AtomicLong(0);
        
        // Create virtual thread executor for non-blocking operations (Java 21+)
        // Each task runs on its own lightweight virtual thread - zero main thread blocking!
        // Virtual threads are extremely lightweight (~1KB vs ~1MB for platform threads)
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

        Caffeine<Object, Object> msgBuilder = Caffeine.newBuilder();
        Caffeine<Object, Object> cfgBuilder = Caffeine.newBuilder();

        if (maxSize > 0) {
            msgBuilder.maximumSize(maxSize);
            cfgBuilder.maximumSize(maxSize);
        }

        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            msgBuilder.expireAfterWrite(ttl);
            cfgBuilder.expireAfterWrite(ttl);
        }

        if (recordStats) {
            msgBuilder.recordStats();
            cfgBuilder.recordStats();
        }

        this.messageCache = msgBuilder.build();
        this.configCache = cfgBuilder.build();

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(() -> "CacheManager initialised with Caffeine (maxSize=" + maxSize + ", ttl=" + ttl
                    + ", recordStats=" + recordStats + ")");
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getMessage(String collection, String language, String key, T defaultValue) {
        messageRequests.incrementAndGet();
        String cacheKey = collection + ":" + language + ":" + key;
        
        // Use read lock to ensure consistent reads during cache refresh
        messageRefreshLock.readLock().lock();
        try {
            Object cached = messageCache.getIfPresent(cacheKey);
            return cached != null ? (T) cached : defaultValue;
        } finally {
            messageRefreshLock.readLock().unlock();
        }
    }

    public String getMessage(String collection, String language, String key) {
        messageRequests.incrementAndGet();
        String cacheKey = collection + ":" + language + ":" + key;
        
        // Use read lock to ensure consistent reads during cache refresh
        messageRefreshLock.readLock().lock();
        try {
            Object cached = messageCache.getIfPresent(cacheKey);
            return cached != null ? cached.toString() : null;
        } finally {
            messageRefreshLock.readLock().unlock();
        }
    }

    /**
     * Gets a message asynchronously using virtual threads.
     * This method is completely non-blocking and safe for main thread usage.
     */
    public CompletableFuture<String> getMessageAsync(String collection, String language, String key) {
        return CompletableFuture.supplyAsync(() -> getMessage(collection, language, key), virtualExecutor);
    }

    /**
     * Gets a message asynchronously with a default value using virtual threads.
     * This method is completely non-blocking and safe for main thread usage.
     */
    public CompletableFuture<String> getMessageAsync(String collection, String language, String key,
            String defaultValue) {
        return CompletableFuture.supplyAsync(() -> getMessage(collection, language, key, defaultValue), virtualExecutor);
    }

    public void putMessage(String collection, String language, String key, Object value) {
        if (key == null || key.isEmpty() || value == null) {
            return;
        }
        String cacheKey = collection + ":" + language + ":" + key;
        messageCache.put(cacheKey, value);
    }

    public CompletableFuture<Void> putMessageAsync(String collection, String language, String key, Object value) {
        putMessage(collection, language, key, value);
        return CompletableFuture.completedFuture(null);
    }

    public void putMessageData(String collection, String language, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }
            flattenMessageValue(collection, language, key, entry.getValue());
        }
    }

    public void replaceLanguageData(String collection, String language, Map<String, Object> data) {
        if (collection == null || collection.isEmpty() || language == null || language.isEmpty()) {
            return;
        }
        String prefix = collection + ":" + language + ":";
        
        // Use write lock for atomic replacement - prevents stale reads during reload
        messageRefreshLock.writeLock().lock();
        try {
            // First add new data, THEN invalidate old entries not in new data
            // This ensures no window where data is missing
            if (data != null && !data.isEmpty()) {
                putMessageData(collection, language, data);
            }
            
            // Now clean up any old entries that are no longer in the new data
            Set<String> newKeys = data != null ? flattenKeys(data, prefix) : Set.of();
            for (String key : messageCache.asMap().keySet()) {
                if (key.startsWith(prefix) && !newKeys.contains(key)) {
                    messageCache.invalidate(key);
                }
            }
            
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Atomically replaced language data for " + collection + ":" + language + 
                           " (keys=" + (data != null ? data.size() : 0) + ")");
            }
        } finally {
            messageRefreshLock.writeLock().unlock();
        }
        
        // Notify listeners after lock is released
        notifyRefreshComplete(collection + ":" + language);
    }
    
    /**
     * Atomically replaces language data with new data using virtual threads.
     * This method is completely non-blocking and safe for main thread usage.
     */
    public CompletableFuture<Void> replaceLanguageDataAsync(String collection, String language, Map<String, Object> data) {
        return CompletableFuture.runAsync(() -> replaceLanguageData(collection, language, data), virtualExecutor);
    }
    
    private Set<String> flattenKeys(Map<String, Object> data, String prefix) {
        Set<String> keys = new java.util.HashSet<>();
        flattenKeysRecursive(data, prefix, keys);
        return keys;
    }
    
    private void flattenKeysRecursive(Map<String, Object> data, String prefix, Set<String> result) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) continue;
            
            String fullKey = prefix + key;
            
            if (entry.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                flattenKeysRecursive(nestedMap, fullKey + ".", result);
            } else {
                result.add(fullKey);
            }
        }
    }

    private void invalidateMessagesWithPrefix(String prefix) {
        // Expensive but necessary if Caffeine doesn't support prefix queries (it
        // doesn't)
        // Ideally we would track keys per collection/language but that adds complexity.
        // Given this is config reload, iteration is acceptable.
        for (String key : messageCache.asMap().keySet()) {
            if (key.startsWith(prefix)) {
                messageCache.invalidate(key);
            }
        }
    }

    public CompletableFuture<Void> putMessageDataAsync(String collection, String language, Map<String, Object> data) {
        putMessageData(collection, language, data);
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    private void flattenMessageValue(String collection, String language, String key, Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            mapValue.forEach((nestedKey, nestedValue) -> {
                if (nestedKey != null) {
                    String nestedPath = key == null || key.isEmpty()
                            ? nestedKey.toString()
                            : key + "." + nestedKey;
                    flattenMessageValue(collection, language, nestedPath, nestedValue);
                }
            });
            return;
        }

        if (value instanceof Iterable<?> iterable) {
            putMessage(collection, language, key, copyToStringList(iterable));
            return;
        }

        if (value != null && value.getClass().isArray()) {
            putMessage(collection, language, key, copyArrayToStringList(value));
            return;
        }

        if (value != null) {
            putMessage(collection, language, key, value);
        }
    }

    private List<String> copyToStringList(Iterable<?> iterable) {
        ArrayList<String> copy = new ArrayList<>();
        for (Object element : iterable) {
            copy.add(element == null ? "null" : element.toString());
        }
        return Collections.unmodifiableList(copy);
    }

    private List<String> copyArrayToStringList(Object array) {
        int length = java.lang.reflect.Array.getLength(array);
        ArrayList<String> copy = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            Object element = java.lang.reflect.Array.get(array, i);
            copy.add(element == null ? "null" : element.toString());
        }
        return Collections.unmodifiableList(copy);
    }

    public void putConfigData(String collection, Map<String, Object> data) {
        configRequests.incrementAndGet();
        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                configCache.put(collection + ":" + entry.getKey(), entry.getValue());
            }
        }
    }

    public void replaceConfigData(String collection, Map<String, Object> data) {
        if (collection == null || collection.isEmpty()) {
            return;
        }
        String prefix = collection + ":";
        
        // Use write lock for atomic replacement - prevents stale reads during reload
        configRefreshLock.writeLock().lock();
        try {
            // First add new data, THEN invalidate old entries not in new data
            if (data != null && !data.isEmpty()) {
                putConfigData(collection, data);
            }
            
            // Now clean up any old entries that are no longer in the new data
            Set<String> newKeys = data != null ? 
                data.keySet().stream().map(k -> prefix + k).collect(java.util.stream.Collectors.toSet()) : 
                Set.of();
            for (String key : configCache.asMap().keySet()) {
                if (key.startsWith(prefix) && !newKeys.contains(key)) {
                    configCache.invalidate(key);
                }
            }
            
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Atomically replaced config data for " + collection + 
                           " (keys=" + (data != null ? data.size() : 0) + ")");
            }
        } finally {
            configRefreshLock.writeLock().unlock();
        }
        
        // Notify listeners after lock is released
        notifyRefreshComplete(collection + ":config");
    }
    
    /**
     * Atomically replaces config data using virtual threads.
     * This method is completely non-blocking and safe for main thread usage.
     */
    public CompletableFuture<Void> replaceConfigDataAsync(String collection, Map<String, Object> data) {
        return CompletableFuture.runAsync(() -> replaceConfigData(collection, data), virtualExecutor);
    }

    private void invalidateConfigWithPrefix(String prefix) {
        for (String key : configCache.asMap().keySet()) {
            if (key.startsWith(prefix)) {
                configCache.invalidate(key);
            }
        }
    }

    public CompletableFuture<Void> putConfigDataAsync(String collection, Map<String, Object> data) {
        putConfigData(collection, data);
        return CompletableFuture.completedFuture(null);
    }

    public boolean hasCollection(String collection) {
        String prefix = collection + ":";
        // Check if any key starts with collection:
        // Caffeine doesn't support this efficiently.
        // Optimization: checking specific meta-key if we had one, but we don't.
        // Fallback: iterate (slow) or rely on MongoManager 'knownCollections' which is
        // typically checked before cache.
        // The original implementation iterated.
        return configCache.asMap().keySet().stream().anyMatch(key -> key.startsWith(prefix)) ||
                messageCache.asMap().keySet().stream().anyMatch(key -> key.startsWith(prefix));
    }

    public CompletableFuture<Boolean> hasCollectionAsync(String collection) {
        return CompletableFuture.completedFuture(hasCollection(collection));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        configRequests.incrementAndGet();
        
        // Use read lock to ensure consistent reads during cache refresh
        configRefreshLock.readLock().lock();
        try {
            Object cached = configCache.getIfPresent(key);
            return cached != null ? (T) cached : defaultValue;
        } finally {
            configRefreshLock.readLock().unlock();
        }
    }

    /**
     * Gets a config value asynchronously using virtual threads.
     * This method is completely non-blocking and safe for main thread usage.
     */
    public CompletableFuture<Object> getAsync(String key) {
        return CompletableFuture.supplyAsync(() -> get(key, null), virtualExecutor);
    }

    /**
     * Gets a config value asynchronously with a default value using virtual threads.
     * This method is completely non-blocking and safe for main thread usage.
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getAsync(String key, T defaultValue) {
        return CompletableFuture.supplyAsync(() -> get(key, defaultValue), virtualExecutor);
    }

    public void put(String key, Object value) {
        configCache.put(key, value);
    }

    public CompletableFuture<Void> putAsync(String key, Object value) {
        put(key, value);
        return CompletableFuture.completedFuture(null);
    }

    public void invalidate(String key) {
        configCache.invalidate(key);
        invalidateMessagesWithPrefix(key + ":");
    }

    public CompletableFuture<Void> invalidateAsync(String key) {
        invalidate(key);
        return CompletableFuture.completedFuture(null);
    }

    public void invalidateCollection(String collection) {
        invalidateConfigWithPrefix(collection + ":");
        invalidateMessagesWithPrefix(collection + ":");

        for (Consumer<String> listener : invalidationListeners) {
            try {
                listener.accept(collection);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in invalidation listener", e);
            }
        }
    }

    public CompletableFuture<Void> invalidateCollectionAsync(String collection) {
        return CompletableFuture.runAsync(() -> invalidateCollection(collection), virtualExecutor);
    }

    public void invalidateAll() {
        configCache.invalidateAll();
        messageCache.invalidateAll();

        for (Consumer<String> listener : invalidationListeners) {
            try {
                listener.accept("*");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in invalidation listener", e);
            }
        }
    }

    public CompletableFuture<Void> invalidateAllAsync() {
        return CompletableFuture.runAsync(this::invalidateAll, virtualExecutor);
    }

    public void invalidateMessages(String collection) {
        invalidateMessagesWithPrefix(collection + ":");

        for (Consumer<String> listener : invalidationListeners) {
            try {
                listener.accept(collection + ":messages");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in invalidation listener", e);
            }
        }
    }

    public CompletableFuture<Void> invalidateMessagesAsync(String collection) {
        invalidateMessages(collection);
        return CompletableFuture.completedFuture(null);
    }

    public void addInvalidationListener(Consumer<String> listener) {
        invalidationListeners.add(listener);
    }

    public void removeInvalidationListener(Consumer<String> listener) {
        invalidationListeners.remove(listener);
    }
    
    /**
     * Adds a listener that is notified when a cache refresh completes.
     * This is useful for triggering actions after new data is loaded.
     */
    public void addRefreshCompleteListener(Consumer<String> listener) {
        refreshCompleteListeners.add(listener);
    }
    
    /**
     * Removes a refresh complete listener.
     */
    public void removeRefreshCompleteListener(Consumer<String> listener) {
        refreshCompleteListeners.remove(listener);
    }
    
    private void notifyRefreshComplete(String key) {
        for (Consumer<String> listener : refreshCompleteListeners) {
            try {
                listener.accept(key);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in refresh complete listener", e);
            }
        }
    }

    public void refresh(String collection) {
        invalidateCollection(collection);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Cache refreshed for collection: " + collection);
        }
    }

    public CompletableFuture<Void> refreshAsync(String collection) {
        refresh(collection);
        return CompletableFuture.completedFuture(null);
    }

    public long getEstimatedSize() {
        return messageCache.estimatedSize() + configCache.estimatedSize();
    }

    public long getConfigRequests() {
        return configRequests.get();
    }

    public long getMessageRequests() {
        return messageRequests.get();
    }

    public long getMessageCacheSize() {
        return messageCache.estimatedSize();
    }

    public long getLocalCacheSize() {
        return configCache.estimatedSize();
    }

    public void cleanUp() {
        messageCache.cleanUp();
        configCache.cleanUp();
    }

    public CompletableFuture<Void> cleanUpAsync() {
        return CompletableFuture.runAsync(this::cleanUp, virtualExecutor);
    }
    
    /**
     * Returns the virtual thread executor for non-blocking async operations.
     * Uses Java 21+ virtual threads for maximum concurrency without thread overhead.
     */
    public Executor getVirtualExecutor() {
        return virtualExecutor;
    }
    
    /**
     * Returns whether this cache manager has been closed.
     */
    public boolean isClosed() {
        return closed.get();
    }
    
    /**
     * Closes the cache manager and releases all resources.
     * Shuts down the virtual thread executor and clears all caches.
     * 
     * @param timeoutMs Maximum time to wait for executor shutdown in milliseconds
     */
    public void close(long timeoutMs) {
        if (!closed.compareAndSet(false, true)) {
            return; // Already closed
        }
        
        // Shutdown virtual executor
        if (virtualExecutor != null && !virtualExecutor.isShutdown()) {
            virtualExecutor.shutdown();
            try {
                if (!virtualExecutor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                    LOGGER.fine("Virtual executor did not terminate in time, forcing shutdown");
                    virtualExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                virtualExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Clear caches
        cleanUp();
        messageCache.invalidateAll();
        configCache.invalidateAll();
        
        // Clear listeners
        invalidationListeners.clear();
        refreshCompleteListeners.clear();
        
        LOGGER.info("CacheManager closed");
    }
    
    /**
     * Closes the cache manager with a default timeout of 5 seconds.
     */
    public void close() {
        close(5000);
    }
}
