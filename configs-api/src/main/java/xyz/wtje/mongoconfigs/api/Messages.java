package xyz.wtje.mongoconfigs.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Messages {
    <T> T get(String lang, String key, Class<T> type);

    String get(String lang, String key, Object... placeholders);
    String get(String lang, String key, Map<String, Object> placeholders);
    
    // 🚀 ASYNC METHODS - NO MAIN THREAD BLOCKING!
    <T> CompletableFuture<T> getAsync(String lang, String key, Class<T> type);
    CompletableFuture<String> getAsync(String lang, String key, Object... placeholders);
    CompletableFuture<String> getAsync(String lang, String key, Map<String, Object> placeholders);
}
