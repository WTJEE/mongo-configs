package xyz.wtje.mongoconfigs.api;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface ConfigManager {
    
    CompletableFuture<Void> reloadAll();
    <T> CompletableFuture<Void> set(String id, T value);
    <T> CompletableFuture<T> get(String id, Class<T> type);
    <T> CompletableFuture<Void> setObject(T pojo);
    <T> CompletableFuture<T> getObject(Class<T> type);
    <T> CompletableFuture<T> getConfigOrGenerate(Class<T> type, Supplier<T> generator);
    
    
    <T> CompletableFuture<Void> createFromObject(T messageObject);
    <T> CompletableFuture<Messages> getOrCreateFromObject(T messageObject);
    
    
    CompletableFuture<Set<String>> getCollections();
    CompletableFuture<Void> reloadCollection(String collection);
    CompletableFuture<Set<String>> getSupportedLanguages(String collection);
    CompletableFuture<Boolean> collectionExists(String collection);
    
    
    Messages findById(String id);
    
    // Message API with placeholders
    /**
     * Get message asynchronously
     * @param collection Message collection
     * @param language Language code
     * @param key Message key
     * @return CompletableFuture with message
     */
    CompletableFuture<String> getMessageAsync(String collection, String language, String key);
    
    /**
     * Get message asynchronously with default value
     * @param collection Message collection
     * @param language Language code
     * @param key Message key
     * @param defaultValue Default value if message not found
     * @return CompletableFuture with message or default
     */
    CompletableFuture<String> getMessageAsync(String collection, String language, String key, String defaultValue);
    
    /**
     * Get message asynchronously with placeholders (varargs)
     * @param collection Message collection
     * @param language Language code
     * @param key Message key
     * @param placeholders Placeholders in key-value pairs: "key1", value1, "key2", value2, ...
     * @return CompletableFuture with formatted message
     */
    CompletableFuture<String> getMessageAsync(String collection, String language, String key, Object... placeholders);
    
    /**
     * Get message asynchronously with placeholders (Map)
     * @param collection Message collection
     * @param language Language code
     * @param key Message key
     * @param placeholders Map of placeholders
     * @return CompletableFuture with formatted message
     */
    CompletableFuture<String> getMessageAsync(String collection, String language, String key, Map<String, Object> placeholders);
    
    
    
    default <T> CompletableFuture<T> getLanguageClass(Class<T> type, String language) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Language class resolution not implemented"));
    }

    default <T> CompletableFuture<Map<String, T>> getLanguageClasses(Class<T> type) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Language class resolution not implemented"));
    }

    default void setColorProcessor(Object colorProcessor) {
        
        
    }
    
    default Object getMongoManager() {
        
        
        return null;
    }
    
    default Object getTypedConfigManager() {
        
        
        return null;
    }
}


