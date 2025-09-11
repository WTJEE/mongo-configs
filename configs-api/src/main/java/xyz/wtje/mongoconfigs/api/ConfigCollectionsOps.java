package xyz.wtje.mongoconfigs.api;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface ConfigCollectionsOps {
    CompletableFuture<Set<String>> getCollections();
    Set<String> getSupportedLanguages(String collection);
    boolean collectionExists(String collection);
    CompletableFuture<Void> reloadCollection(String collection);
    CompletableFuture<Void> reloadCollectionsBatch(Set<String> collections);
    CompletableFuture<Void> reloadCollectionsBatch(Set<String> collections, int maxConcurrency);
    void invalidateCache(String collection);
    void invalidateCache();
    CompletableFuture<Void> invalidateAllAsync();
    boolean hasMessages(String collection, String language);
    CompletableFuture<Void> forceRegenerateLanguageDocuments(String collection);
    CompletableFuture<Void> forceRegenerateCollection(String collection, Set<String> expectedLanguages);
    CompletableFuture<Void> updateSupportedLanguages(String collection, Set<String> languages);
}
