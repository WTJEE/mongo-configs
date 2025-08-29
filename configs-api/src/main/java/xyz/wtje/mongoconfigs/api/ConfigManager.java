package xyz.wtje.mongoconfigs.api;

import java.util.List;
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

    CompletableFuture<Void> createCollection(String collection, Set<String> languages);

    CompletableFuture<Void> copyLanguage(String collection, String sourceLanguage, String targetLanguage);

    CompletableFuture<Set<String>> getCollections();

    Set<String> getSupportedLanguages(String collection);

    boolean collectionExists(String collection);

    CompletableFuture<Void> reloadCollection(String collection);

    CompletableFuture<Void> reloadAll();

    void invalidateCache(String collection);

    void invalidateCache();

    CacheStats getCacheStats();

    PerformanceMetrics getMetrics();

    String getPlainMessage(String collection, String language, String key, Object... placeholders);
    

    Object getColorCacheStats();
}