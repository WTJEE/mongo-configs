package xyz.wtje.mongoconfigs.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {
    
    private final Cache<String, Object> configCache;
    private final Cache<String, String> messageCache;
    private final Map<String, Map<String, Object>> collectionConfigs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, Object>>> collectionMessages = new ConcurrentHashMap<>();
    
    public CacheManager(MongoConfig config) {
        Caffeine<Object, Object> configCacheBuilder = Caffeine.newBuilder()
                .maximumSize(config.getCacheMaxSize())
                .expireAfterWrite(Duration.ofSeconds(config.getCacheTtlSeconds()))
                .refreshAfterWrite(Duration.ofSeconds(config.getCacheRefreshAfterSeconds()));
        
        if (config.isCacheRecordStats()) {
            configCacheBuilder.recordStats();
        }
        
        this.configCache = configCacheBuilder.build();
                
        Caffeine<Object, Object> messageCacheBuilder = Caffeine.newBuilder()
                .maximumSize(config.getCacheMaxSize() * 2)
                .expireAfterWrite(Duration.ofSeconds(config.getCacheTtlSeconds()))
                .refreshAfterWrite(Duration.ofSeconds(config.getCacheRefreshAfterSeconds()));
        
        if (config.isCacheRecordStats()) {
            messageCacheBuilder.recordStats();
        }
        
        this.messageCache = messageCacheBuilder.build();
    }

    @SuppressWarnings("unchecked")
    public <T> T getConfig(String collection, String key, T defaultValue) {
        Map<String, Object> collectionConfig = collectionConfigs.get(collection);
        if (collectionConfig == null) {
            return defaultValue;
        }
        
        Object value = collectionConfig.get(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
    
    public void putConfig(String collection, String key, Object value) {
        collectionConfigs.computeIfAbsent(collection, k -> new Object2ObjectOpenHashMap<>())
                         .put(key, value);
        configCache.put(collection + ":" + key, value);
    }
    
    public void putConfigData(String collection, Map<String, Object> data) {
        collectionConfigs.put(collection, new Object2ObjectOpenHashMap<>(data));
        data.forEach((key, value) -> configCache.put(collection + ":" + key, value));
    }

    public String getMessage(String collection, String language, String key, String defaultValue) {
        Map<String, Map<String, Object>> collectionLangs = collectionMessages.get(collection);
        if (collectionLangs == null) {
            return defaultValue;
        }
        
        Map<String, Object> langData = collectionLangs.get(language);
        if (langData == null) {
            return defaultValue;
        }
        
        Object value = getNestedValue(langData, key);
        return value != null ? value.toString() : defaultValue;
    }
    
    public void putMessage(String collection, String language, String key, String value) {
        collectionMessages.computeIfAbsent(collection, k -> new ConcurrentHashMap<>())
                          .computeIfAbsent(language, k -> new Object2ObjectOpenHashMap<>())
                          .put(key, value);
        messageCache.put(collection + ":" + language + ":" + key, value);
    }
    
    public void putMessageData(String collection, String language, Map<String, Object> data) {
        collectionMessages.computeIfAbsent(collection, k -> new ConcurrentHashMap<>())
                          .put(language, new Object2ObjectOpenHashMap<>(data));
        
        flattenAndCache(data, "", collection, language);
    }

    private Object getNestedValue(Map<String, Object> data, String key) {
        if (!key.contains(".")) {
            return data.get(key);
        }
        
        String[] parts = key.split("\\.");
        Object current = data;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    private void flattenAndCache(Map<String, Object> data, String prefix, String collection, String language) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                flattenAndCache((Map<String, Object>) value, key, collection, language);
            } else {
                messageCache.put(collection + ":" + language + ":" + key, value.toString());
            }
        }
    }
    
    public void invalidateCollection(String collection) {
        collectionConfigs.remove(collection);
        collectionMessages.remove(collection);

        configCache.asMap().keySet().removeIf(key -> key.startsWith(collection + ":"));
        messageCache.asMap().keySet().removeIf(key -> key.startsWith(collection + ":"));
    }
    
    public void invalidateAll() {
        collectionConfigs.clear();
        collectionMessages.clear();
        configCache.invalidateAll();
        messageCache.invalidateAll();
    }
    
    public boolean hasCollection(String collection) {
        return collectionConfigs.containsKey(collection) || collectionMessages.containsKey(collection);
    }

    public CacheStats getConfigCacheStats() {
        return configCache.stats();
    }
    
    public CacheStats getMessageCacheStats() {
        return messageCache.stats();
    }
    
    public long getEstimatedSize() {
        return configCache.estimatedSize() + messageCache.estimatedSize();
    }

}