package xyz.wtje.mongoconfigs.api;

import xyz.wtje.mongoconfigs.api.event.LanguageUpdateListener;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
