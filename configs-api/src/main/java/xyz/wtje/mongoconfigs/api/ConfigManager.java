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
        });
    }

    default void generateDefaultMessageDocuments(Class<?> messageClass) {
        throw new UnsupportedOperationException("This method should be overridden in implementation");
    }

    <T> void createFromObject(T messageObject);

    <T> Messages getOrCreateFromObject(T messageObject);

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
