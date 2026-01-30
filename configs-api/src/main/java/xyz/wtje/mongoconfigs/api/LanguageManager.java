package xyz.wtje.mongoconfigs.api;

import xyz.wtje.mongoconfigs.api.event.LanguageUpdateListener;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for managing player language preferences.
 * All async methods use virtual threads (Java 21+) and are completely non-blocking,
 * making them safe to call from any thread including the main server thread.
 */
public interface LanguageManager {
    CompletableFuture<String> getPlayerLanguage(String playerId);

    CompletableFuture<Void> setPlayerLanguage(String playerId, String language);

    default CompletableFuture<String> getPlayerLanguage(UUID playerId) {
        return getPlayerLanguage(playerId.toString());
    }

    default CompletableFuture<Void> setPlayerLanguage(UUID playerId, String language) {
        return setPlayerLanguage(playerId.toString(), language);
    }

    CompletableFuture<String> getDefaultLanguage();

    CompletableFuture<String[]> getSupportedLanguages();

    CompletableFuture<Boolean> isLanguageSupported(String language);

    CompletableFuture<String> getLanguageDisplayName(String language);
    
    /**
     * Gets a player's language and passes it to the consumer.
     * Non-blocking and safe for main thread usage.
     * 
     * @param playerId The player's ID
     * @param consumer The consumer to receive the language
     */
    default void usePlayerLanguage(String playerId, Consumer<String> consumer) {
        getPlayerLanguage(playerId).thenAccept(consumer);
    }
    
    /**
     * Gets a player's language and passes it to the consumer.
     * Non-blocking and safe for main thread usage.
     * 
     * @param playerId The player's UUID
     * @param consumer The consumer to receive the language
     */
    default void usePlayerLanguage(UUID playerId, Consumer<String> consumer) {
        getPlayerLanguage(playerId).thenAccept(consumer);
    }
    
    /**
     * Gets the default language and passes it to the consumer.
     * Non-blocking and safe for main thread usage.
     * 
     * @param consumer The consumer to receive the language
     */
    default void useDefaultLanguage(Consumer<String> consumer) {
        getDefaultLanguage().thenAccept(consumer);
    }
    
    /**
     * Gets all supported languages and passes them to the consumer.
     * Non-blocking and safe for main thread usage.
     * 
     * @param consumer The consumer to receive the languages array
     */
    default void useSupportedLanguages(Consumer<String[]> consumer) {
        getSupportedLanguages().thenAccept(consumer);
    }

    /**
     * Registers a listener to be notified when a player's language is updated.
     * This is platform-agnostic and works on both Paper and Velocity.
     * 
     * @param listener The listener to register
     */
    void registerListener(LanguageUpdateListener listener);

    /**
     * Unregisters a previously registered listener.
     * 
     * @param listener The listener to unregister
     */
    void unregisterListener(LanguageUpdateListener listener);
}
