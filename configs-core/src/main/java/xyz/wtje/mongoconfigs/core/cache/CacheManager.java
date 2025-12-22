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

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheManager {
    private static final Logger LOGGER = Logger.getLogger(CacheManager.class.getName());

    private final Cache<String, Object> messageCache;
    private final Cache<String, Object> configCache;
    private final AtomicLong configRequests;
    private final AtomicLong messageRequests;

    // Listeners for invalidation events
    private final Set<Consumer<String>> invalidationListeners = new CopyOnWriteArraySet<>();

    public CacheManager() {
        this(0, null, true);
    }

    public CacheManager(long maxSize, Duration ttl) {
        this(maxSize, ttl, true);
    }

    public CacheManager(long maxSize, Duration ttl, boolean recordStats) {

        this.configRequests = new AtomicLong(0);
        this.messageRequests = new AtomicLong(0);

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
        return CompletableFuture.completedFuture(getMessage(collection, language, key));
    }

    public CompletableFuture<String> getMessageAsync(String collection, String language, String key,
            String defaultValue) {
        return CompletableFuture.completedFuture(getMessage(collection, language, key, defaultValue));
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
        // Caffeine doesn't support prefix removal efficiently, so we must iterate keys
        // provided keys
        // However, invalidation by prefix implies we know what is in cache or we
        // iterate.
        // For correctness we should probably iterate.
        // Or if 'replace' implies clearing old ones first? yes.
        invalidateMessagesWithPrefix(prefix);

        if (data != null && !data.isEmpty()) {
            putMessageData(collection, language, data);
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
        invalidateConfigWithPrefix(collection + ":");
        if (data != null && !data.isEmpty()) {
            putConfigData(collection, data);
        }
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
        Object cached = configCache.getIfPresent(key);
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
        return CompletableFuture.runAsync(() -> invalidateCollection(collection));
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
        invalidateAll();
        return CompletableFuture.completedFuture(null);
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
        cleanUp();
        return CompletableFuture.completedFuture(null);
    }
}
