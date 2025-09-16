package xyz.wtje.mongoconfigs.api;

import java.util.Set;
import java.util.concurrent.CompletableFuture;


public interface ConfigCollectionsOps {
    
    
    CompletableFuture<Set<String>> getCollections();
    
    
    boolean collectionExists(String collection);
    
    
    CompletableFuture<Void> reloadCollection(String collection);
    
    
    Set<String> getSupportedLanguages(String collection);
}
