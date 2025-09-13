package xyz.wtje.mongoconfigs.api;

import xyz.wtje.mongoconfigs.api.core.Annotations;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface ConfigManager {

    CompletableFuture<Void> reloadAll();

    <T> CompletableFuture<Void> set(String id, T value);

    <T> CompletableFuture<T> get(String id, Class<T> type);

    <T> CompletableFuture<Void> setObject(T pojo);

    <T> CompletableFuture<T> getObject(Class<T> type);

    <T> CompletableFuture<T> getConfigOrGenerate(Class<T> type, Supplier<T> generator);

    Messages findById(String id);

    Messages getMessagesOrGenerate(Class<?> messageClass, Supplier<Void> generator);

    default Messages messagesOf(Class<?> type) {
        return findById(Annotations.idFrom(type));
    }

    default Messages getMessagesOrGenerate(Class<?> messageClass) {
        return getMessagesOrGenerate(messageClass, () -> {
            generateDefaultMessageDocuments(messageClass);
            return null; // ✅ Supplier<Void> requires return
        });
    }

    default void generateDefaultMessageDocuments(Class<?> messageClass) {
        throw new UnsupportedOperationException("This method should be overridden in implementation");
    }

    <T> void createFromObject(T messageObject);

    <T> Messages getOrCreateFromObject(T messageObject);
    
    // === NEW ASYNC METHODS ===
    
    /**
     * Create message structure from an annotated object (asynchronous)
     * @param messageObject the object containing message definitions
     * @return CompletableFuture that completes when creation is done
     */
    default <T> CompletableFuture<Void> createFromObjectAsync(T messageObject) {
        return CompletableFuture.runAsync(() -> createFromObject(messageObject));
    }
    
    /**
     * Get messages or create from object if they don't exist (asynchronous)
     * @param messageObject the object containing message definitions
     * @return CompletableFuture containing Messages instance
     */
    default <T> CompletableFuture<Messages> getOrCreateFromObjectAsync(T messageObject) {
        return CompletableFuture.supplyAsync(() -> getOrCreateFromObject(messageObject));
    }
    
    /**
     * Invalidate all cached data asynchronously
     * @return CompletableFuture that completes when invalidation is done
     */
    default CompletableFuture<Void> invalidateAllAsync() {
        return CompletableFuture.completedFuture(null); // Default empty implementation
    }

    default <T> void saveObject(T pojo) {
        setObject(pojo).join();
    }

    default <T> T loadObject(Class<T> type) {
        return getObject(type).join();
    }

    default <T> void save(String id, T value) {
        set(id, value).join();
    }

    default <T> T load(String id, Class<T> type) {
        return get(id, type).join();
    }
}
