package xyz.wtje.mongoconfigs.core.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class CacheManager {

    private final Cache<String, Object> messageCache;
    private final AsyncCache<String, Object> asyncMessageCache;
    private final Cache<String, Object> configCache;
    private final AsyncCache<String, Object> asyncConfigCache;
    private final AtomicLong configRequests = new AtomicLong(0);
    private final AtomicLong messageRequests = new AtomicLong(0);

    public CacheManager() {
        this(10000, Duration.ofSeconds(Long.MAX_VALUE), true);
    }

    public CacheManager(long maxSize, Duration ttl) {
        this(maxSize, ttl, true);
    }

    public CacheManager(long maxSize, Duration ttl, boolean recordStats) {
        Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder()
                .maximumSize(maxSize);

        if (ttl != null) {
            caffeineBuilder.expireAfterWrite(ttl);
        }

        if (recordStats) {
            caffeineBuilder.recordStats();
        }

        this.messageCache = caffeineBuilder.build();
        this.asyncMessageCache = caffeineBuilder.buildAsync();
        this.configCache = caffeineBuilder.build();
        this.asyncConfigCache = caffeineBuilder.buildAsync();
    }

    @SuppressWarnings("unchecked")
    public <T> T getMessage(String collection, String language, String key, T defaultValue) {
        messageRequests.incrementAndGet();
        String cacheKey = collection + ":" + language + ":" + key;
        Object cached = messageCache.getIfPresent(cacheKey);
        return cached != null ? (T) cached : defaultValue;
    }

    public String getMessage(String collection, String language, String key) {
        messageRequests.incrementAndGet();
        String cacheKey = collection + ":" + language + ":" + key;
        Object cached = messageCache.getIfPresent(cacheKey);
        return cached != null ? cached.toString() : null;
    }

    public CompletableFuture<String> getMessageAsync(String collection, String language, String key) {
        messageRequests.incrementAndGet();
        String cacheKey = collection + ":" + language + ":" + key;
        CompletableFuture<Object> cachedFuture = asyncMessageCache.getIfPresent(cacheKey);
        if (cachedFuture != null) {
            return cachedFuture.thenApply(cached -> cached != null ? cached.toString() : null);
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletableFuture<String> getMessageAsync(String collection, String language, String key, String defaultValue) {
        return getMessageAsync(collection, language, key)
                .thenApply(result -> result != null ? result : defaultValue);
    }

    public void putMessage(String collection, String language, String key, Object value) {
        if (key == null || key.isEmpty() || value == null) {
            return;
        }
        String cacheKey = collection + ":" + language + ":" + key;
        messageCache.put(cacheKey, value);
        asyncMessageCache.put(cacheKey, CompletableFuture.completedFuture(value));
    }

    public CompletableFuture<Void> putMessageAsync(String collection, String language, String key, Object value) {
        if (key == null || key.isEmpty() || value == null) {
            return CompletableFuture.completedFuture(null);
        }
        String cacheKey = collection + ":" + language + ":" + key;
        return CompletableFuture.runAsync(() -> {
            messageCache.put(cacheKey, value);
            asyncMessageCache.put(cacheKey, CompletableFuture.completedFuture(value));
        });
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
        if (data == null || data.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> putMessageData(collection, language, data));
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

        if (value != null) {
            putMessage(collection, language, key, value);
        }
    }
    public void putConfigData(String collection, Map<String, Object> data) {
        configRequests.incrementAndGet();
        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                configCache.put(collection + ":" + entry.getKey(), entry.getValue());
            }
        }
    }

    public CompletableFuture<Void> putConfigDataAsync(String collection, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            configRequests.incrementAndGet();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                configCache.put(collection + ":" + entry.getKey(), entry.getValue());
            }
        });
    }

    public boolean hasCollection(String collection) {
        return configCache.asMap().keySet().stream().anyMatch(key -> key.startsWith(collection + ":")) ||
               messageCache.asMap().keySet().stream().anyMatch(key -> key.startsWith(collection + ":"));
    }

    public CompletableFuture<Boolean> hasCollectionAsync(String collection) {
        return CompletableFuture.supplyAsync(() -> hasCollection(collection));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        configRequests.incrementAndGet();
        Object cached = configCache.getIfPresent(key);
        return cached != null ? (T) cached : defaultValue;
    }

    public CompletableFuture<Object> getAsync(String key) {
        return CompletableFuture.supplyAsync(() -> {
            configRequests.incrementAndGet();
            return configCache.getIfPresent(key);
        });
    }

    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getAsync(String key, T defaultValue) {
        return getAsync(key).thenApply(cached -> cached != null ? (T) cached : defaultValue);
    }

    public void put(String key, Object value) {
        configCache.put(key, value);
    }

    public CompletableFuture<Void> putAsync(String key, Object value) {
        return CompletableFuture.runAsync(() -> configCache.put(key, value));
    }

    public void invalidate(String key) {
        configCache.invalidate(key);
        messageCache.asMap().keySet().removeIf(cacheKey -> cacheKey.startsWith(key + ":"));
        asyncMessageCache.asMap().keySet().removeIf(cacheKey -> cacheKey.startsWith(key + ":"));
    }

    public CompletableFuture<Void> invalidateAsync(String key) {
        return CompletableFuture.runAsync(() -> invalidate(key));
    }

    public void invalidateCollection(String collection) {
        configCache.asMap().keySet().removeIf(key -> key.startsWith(collection + ":"));
        messageCache.asMap().keySet().removeIf(key -> key.startsWith(collection + ":"));
        asyncMessageCache.asMap().keySet().removeIf(key -> key.startsWith(collection + ":"));
    }

    public CompletableFuture<Void> invalidateCollectionAsync(String collection) {
        return CompletableFuture.runAsync(() -> invalidateCollection(collection));
    }

    public void invalidateAll() {
        configCache.invalidateAll();
        messageCache.invalidateAll();
        asyncMessageCache.asMap().clear();
    }

    public CompletableFuture<Void> invalidateAllAsync() {
        return CompletableFuture.runAsync(this::invalidateAll);
    }

    public void invalidateMessages(String collection) {
        messageCache.asMap().keySet().removeIf(key -> key.startsWith(collection + ":"));
        asyncMessageCache.asMap().keySet().removeIf(key -> key.startsWith(collection + ":"));
    }

    public CompletableFuture<Void> invalidateMessagesAsync(String collection) {
        return CompletableFuture.runAsync(() -> invalidateMessages(collection));
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
        asyncMessageCache.asMap().clear();
        asyncConfigCache.asMap().clear();
    }

    public CompletableFuture<Void> cleanUpAsync() {
        return CompletableFuture.runAsync(this::cleanUp);
    }
}



