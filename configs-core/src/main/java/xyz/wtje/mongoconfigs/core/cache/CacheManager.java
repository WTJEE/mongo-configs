package xyz.wtje.mongoconfigs.core.cache;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CacheManager {
    
    private final Map<String, Map<String, Object>> collectionConfigs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, Object>>> collectionMessages = new ConcurrentHashMap<>();

    private final AtomicLong configRequests = new AtomicLong(0);
    private final AtomicLong messageRequests = new AtomicLong(0);
    
    public CacheManager() {
    }

    @SuppressWarnings("unchecked")
    public <T> T getConfig(String collection, String key, T defaultValue) {
        configRequests.incrementAndGet();
        
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
    }
    
    public void putConfigData(String collection, Map<String, Object> data) {
        collectionConfigs.put(collection, new Object2ObjectOpenHashMap<>(data));
    }

    public String getMessage(String collection, String language, String key, String defaultValue) {
        messageRequests.incrementAndGet();
        
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
    }
    
    public void putMessageData(String collection, String language, Map<String, Object> data) {
        collectionMessages.computeIfAbsent(collection, k -> new ConcurrentHashMap<>())
                          .put(language, new Object2ObjectOpenHashMap<>(data));
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
    
    public void invalidateCollection(String collection) {
        collectionConfigs.remove(collection);
        collectionMessages.remove(collection);
    }
    
    public void invalidateAll() {
        collectionConfigs.clear();
        collectionMessages.clear();
    }
    
    public boolean hasCollection(String collection) {
        return collectionConfigs.containsKey(collection) || collectionMessages.containsKey(collection);
    }

    public long getConfigRequests() {
        return configRequests.get();
    }
    
    public long getMessageRequests() {
        return messageRequests.get();
    }
    
    public long getEstimatedSize() {
        return collectionConfigs.size() + collectionMessages.size();
    }
    
    public boolean hasConfigData(String collection) {
        return collectionConfigs.containsKey(collection);
    }
    
    public boolean hasMessageData(String collection, String language) {
        Map<String, Map<String, Object>> collectionLangs = collectionMessages.get(collection);
        return collectionLangs != null && collectionLangs.containsKey(language);
    }
    
    public int getConfigKeyCount(String collection) {
        Map<String, Object> config = collectionConfigs.get(collection);
        return config != null ? config.size() : 0;
    }
    
    public int getMessageKeyCount(String collection, String language) {
        Map<String, Map<String, Object>> collectionLangs = collectionMessages.get(collection);
        if (collectionLangs == null) return 0;
        
        Map<String, Object> langData = collectionLangs.get(language);
        return langData != null ? langData.size() : 0;
    }

}