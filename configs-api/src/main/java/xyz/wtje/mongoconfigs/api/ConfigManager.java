package xyz.wtje.mongoconfigs.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface ConfigManager {

    <T> T getConfig(String collection, String key, T defaultValue);

    <T> CompletableFuture<Optional<T>> getConfigAsync(String collection, String key, Class<T> type);

    <T> CompletableFuture<Void> setConfig(String collection, String key, T value);

    String getMessage(String collection, String language, String key, Object... placeholders);

    List<String> getMessageLore(String collection, String language, String key, Object... placeholders);

    CompletableFuture<String> getMessageAsync(String collection, String language, String key, Object... placeholders);

    CompletableFuture<Void> setMessage(String collection, String language, String key, String value);

    /**
     * Set multiple configuration values in a single batch operation
     */
    <T> CompletableFuture<Void> setConfigBatch(String collection, Map<String, T> configValues);

    /**
     * Set multiple messages for a specific language in a single batch operation
     */
    CompletableFuture<Void> setMessageBatch(String collection, String language, Map<String, String> messages);

    /**
     * Set multiple messages for multiple languages in a single batch operation
     */
    CompletableFuture<Void> setMessageBatchMultiLang(String collection, Map<String, Map<String, String>> languageMessages);

    /**
     * Create multiple collections with their configurations and messages in an optimized batch operation
     * @param collectionsData Map of collection name to CollectionSetupData
     * @param maxConcurrency Maximum number of collections to process concurrently (default: 3)
     */
    CompletableFuture<Void> createCollectionsBatch(Map<String, CollectionSetupData> collectionsData, int maxConcurrency);

    /**
     * Create multiple collections with their configurations and messages in an optimized batch operation
     * Uses default concurrency limit of 3
     */
    CompletableFuture<Void> createCollectionsBatch(Map<String, CollectionSetupData> collectionsData);

    CompletableFuture<Void> createCollection(String collection, Set<String> languages);

    CompletableFuture<Void> copyLanguage(String collection, String sourceLanguage, String targetLanguage);

    CompletableFuture<Set<String>> getCollections();

    Set<String> getSupportedLanguages(String collection);

    boolean collectionExists(String collection);

    CompletableFuture<Void> reloadCollection(String collection);

    CompletableFuture<Void> reloadAll();

    /**
     * Reload multiple collections in batch with controlled concurrency
     */
    CompletableFuture<Void> reloadCollectionsBatch(Set<String> collections, int maxConcurrency);

    /**
     * Reload multiple collections in batch with default concurrency (3)
     */
    CompletableFuture<Void> reloadCollectionsBatch(Set<String> collections);

    void invalidateCache(String collection);

    void invalidateCache();

    CacheStats getCacheStats();

    PerformanceMetrics getMetrics();

    String getPlainMessage(String collection, String language, String key, Object... placeholders);
    

    Object getColorCacheStats();
}