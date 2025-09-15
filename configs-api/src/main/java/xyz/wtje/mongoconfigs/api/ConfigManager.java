package xyz.wtje.mongoconfigs.api;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface ConfigManager {
    // Basic async config operations
    CompletableFuture<Void> reloadAll();
    <T> CompletableFuture<Void> set(String id, T value);
    <T> CompletableFuture<T> get(String id, Class<T> type);
    <T> CompletableFuture<Void> setObject(T pojo);
    <T> CompletableFuture<T> getObject(Class<T> type);
    <T> CompletableFuture<T> getConfigOrGenerate(Class<T> type, Supplier<T> generator);
    
    // Messages operations
    <T> CompletableFuture<Void> createFromObject(T messageObject);
    <T> CompletableFuture<Messages> getOrCreateFromObject(T messageObject);
    
    // Collection management
    CompletableFuture<Set<String>> getCollections();
    CompletableFuture<Void> reloadCollection(String collection);
    CompletableFuture<Set<String>> getSupportedLanguages(String collection);
    CompletableFuture<Boolean> collectionExists(String collection);
    
    // Messages finder
    Messages findById(String id);
    
    // Extension methods for implementation-specific features
    // These provide access to implementation details when needed
    default void setColorProcessor(Object colorProcessor) {
        // Default implementation does nothing
        // Override in implementation classes
    }
    
    default Object getMongoManager() {
        // Default implementation returns null
        // Override in implementation classes
        return null;
    }
    
    default Object getTypedConfigManager() {
        // Default implementation returns null  
        // Override in implementation classes
        return null;
    }
}
