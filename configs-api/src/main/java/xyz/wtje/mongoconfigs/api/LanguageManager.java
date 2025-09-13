package xyz.wtje.mongoconfigs.api;

import java.util.concurrent.CompletableFuture;

public interface LanguageManager {

    String getPlayerLanguage(String playerId);
    default java.util.concurrent.CompletableFuture<String> getPlayerLanguageAsync(String playerId) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> getPlayerLanguage(playerId));
    }

    void setPlayerLanguage(String playerId, String language);

    CompletableFuture<Void> setPlayerLanguage(java.util.UUID playerId, String language);

    CompletableFuture<Void> setPlayerLanguageAsync(String playerId, String language);

    String getDefaultLanguage();

    String[] getSupportedLanguages();

    boolean isLanguageSupported(String language);

    String getLanguageDisplayName(String language);
}
