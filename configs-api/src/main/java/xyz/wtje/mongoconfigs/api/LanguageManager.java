package xyz.wtje.mongoconfigs.api;

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
}

