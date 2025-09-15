package xyz.wtje.mongoconfigs.api;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for operations on MongoDB collections
 */
public interface ConfigCollectionsOps {
    
    /**
     * Get all available collections
     * @return Future with set of collection names
     */
    CompletableFuture<Set<String>> getCollections();
    
    /**
     * Check if a collection exists
     * @param collection collection name
     * @return true if collection exists
     */
    boolean collectionExists(String collection);
    
    /**
     * Reload a specific collection from MongoDB
     * @param collection collection name
     * @return Future that completes when reload is done
     */
    CompletableFuture<Void> reloadCollection(String collection);
    
    /**
     * Get supported languages for a collection
     * @param collection collection name
     * @return set of supported language codes
     */
    Set<String> getSupportedLanguages(String collection);
}