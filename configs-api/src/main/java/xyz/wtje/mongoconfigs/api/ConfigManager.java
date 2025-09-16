package xyz.wtje.mongoconfigs.api;

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
    
    
    
    default void setColorProcessor(Object colorProcessor) {
        
        
    }
    
    default Object getMongoManager() {
        
        
        return null;
    }
    
    default Object getTypedConfigManager() {
        
        
        return null;
    }
}

