package xyz.wtje.mongoconfigs.core.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class CacheManager {
    private static final Logger LOGGER = Logger.getLogger(CacheManager.class.getName());

    private final ConcurrentMap<String, Object> messageCache;
    private final ConcurrentMap<String, Object> configCache;
    private final AtomicLong configRequests;
    private final AtomicLong messageRequests;
    private final long maxSize;
    private final Duration ttl;
    private final boolean recordStats;
    
    // Cache invalidation callbacks
    private final Set<Consumer<String>> invalidationListeners = new CopyOnWriteArraySet<>();

    public CacheManager() {
        this(0, null, true);
    }

    public CacheManager(long maxSize, Duration ttl) {
        this(maxSize, ttl, true);
    }

    public CacheManager(long maxSize, Duration ttl, boolean recordStats) {
        this.maxSize = maxSize;
        this.ttl = ttl;
        this.recordStats = recordStats;
        this.messageCache = new ConcurrentHashMap<>();
        this.configCache = new ConcurrentHashMap<>();
        this.configRequests = new AtomicLong(0);
        this.messageRequests = new AtomicLong(0);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(() -> "CacheManager initialised (maxSize=" + maxSize + ", ttl=" + ttl + ", recordStats=" + recordStats + ")");
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getMessage(String collection, String language, String key, T defaultValue) {
        messageRequests.incrementAndGet();
        String cacheKey = collection + ":" + language + ":" + key;
        Object cached = messageCache.get(cacheKey);
        return cached != null ? (T) cached : defaultValue;
    }

    public String getMessage(String collection, String language, String key) {
        messageRequests.incrementAndGet();
        String cacheKey = collection + ":" + language + ":" + key;
        Object cached = messageCache.get(cacheKey);
        return cached != null ? cached.toString() : null;
    }

    public CompletableFuture<String> getMessageAsync(String collection, String language, String key) {
        return CompletableFuture.completedFuture(getMessage(collection, language, key));
    }

    public CompletableFuture<String> getMessageAsync(String collection, String language, String key, String defaultValue) {
        return CompletableFuture.completedFuture(getMessage(collection, language, key, defaultValue));
    }

    public void putMessage(String collection, String language, String key, Object value) {
        if (key == null || key.isEmpty() || value == null) {
            return;
        }
        String cacheKey = collection + ":" + language + ":" + key;
        messageCache.put(cacheKey, value);
        enforceCapacity(messageCache, "messages");
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
            enforceCapacity(configCache, "configs");
        }
    }

    public CompletableFuture<Void> putConfigDataAsync(String collection, Map<String, Object> data) {
        putConfigData(collection, data);
        return CompletableFuture.completedFuture(null);
    }

    public boolean hasCollection(String collection) {
        return configCache.keySet().stream().anyMatch(key -> key.startsWith(collection + ":")) ||
               messageCache.keySet().stream().anyMatch(key -> key.startsWith(collection + ":"));
    }

    public CompletableFuture<Boolean> hasCollectionAsync(String collection) {
        return CompletableFuture.completedFuture(hasCollection(collection));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        configRequests.incrementAndGet();
        Object cached = configCache.get(key);
        return cached != null ? (T) cached : defaultValue;
    }

    public CompletableFuture<Object> getAsync(String key) {
        return CompletableFuture.completedFuture(get(key, null));
    }

    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getAsync(String key, T defaultValue) {
        return CompletableFuture.completedFuture(get(key, defaultValue));
    }

    public void put(String key, Object value) {
        configCache.put(key, value);
        enforceCapacity(configCache, "configs");
    }

    public CompletableFuture<Void> putAsync(String key, Object value) {
        put(key, value);
        return CompletableFuture.completedFuture(null);
    }

    public void invalidate(String key) {
        configCache.remove(key);
        messageCache.keySet().removeIf(cacheKey -> cacheKey.startsWith(key + ":"));
    }

    public CompletableFuture<Void> invalidateAsync(String key) {
        invalidate(key);
        return CompletableFuture.completedFuture(null);
    }

    public void invalidateCollection(String collection) {
        configCache.keySet().removeIf(key -> key.startsWith(collection + ":"));
        messageCache.keySet().removeIf(key -> key.startsWith(collection + ":"));
        
        // Notify listeners about invalidation
        for (Consumer<String> listener : invalidationListeners) {
            try {
                listener.accept(collection);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in invalidation listener", e);
            }
        }
    }

    public CompletableFuture<Void> invalidateCollectionAsync(String collection) {
        invalidateCollection(collection);
        return CompletableFuture.completedFuture(null);
    }

    public void invalidateAll() {
        configCache.clear();
        messageCache.clear();
        
        // Notify listeners about full invalidation
        for (Consumer<String> listener : invalidationListeners) {
            try {
                listener.accept("*"); // Special marker for full invalidation
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in invalidation listener", e);
            }
        }
    }

    public CompletableFuture<Void> invalidateAllAsync() {
        invalidateAll();
        return CompletableFuture.completedFuture(null);
    }

    public void invalidateMessages(String collection) {
        messageCache.keySet().removeIf(key -> key.startsWith(collection + ":"));
        
        // Notify listeners about message invalidation
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

    /**
     * Add a listener that gets notified when cache is invalidated
     * @param listener Consumer that receives collection name (or "*" for full invalidation)
     */
    public void addInvalidationListener(Consumer<String> listener) {
        invalidationListeners.add(listener);
    }

    /**
     * Remove an invalidation listener
     */
    public void removeInvalidationListener(Consumer<String> listener) {
        invalidationListeners.remove(listener);
    }

    /**
     * Refresh cache for a specific collection by reloading from source
     * This should be implemented by the calling code
     */
    public void refresh(String collection) {
        // Invalidate first
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
        return messageCache.size() + configCache.size();
    }

    public long getConfigRequests() {
        return configRequests.get();
    }

    public long getMessageRequests() {
        return messageRequests.get();
    }

    public long getMessageCacheSize() {
        return messageCache.size();
    }

    public long getLocalCacheSize() {
        return configCache.size();
    }

    public void cleanUp() {
        enforceCapacity(messageCache, "messages");
        enforceCapacity(configCache, "configs");
    }

    public CompletableFuture<Void> cleanUpAsync() {
        cleanUp();
        return CompletableFuture.completedFuture(null);
    }

    private void enforceCapacity(ConcurrentMap<String, Object> cache, String cacheName) {
        if (maxSize <= 0) {
            return;
        }

        while (cache.size() > maxSize) {
            String evictedKey = cache.keySet().stream().findFirst().orElse(null);
            if (evictedKey == null) {
                break;
            }
            cache.remove(evictedKey);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(() -> "Cache eviction for " + evictedKey + " due to size limit in " + cacheName);
            }
        }
    }
}
