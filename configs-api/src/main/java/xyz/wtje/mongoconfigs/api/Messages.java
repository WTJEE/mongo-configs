package xyz.wtje.mongoconfigs.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Messages {
    // Async-first API: get(...) returns CompletableFuture
    <T> CompletableFuture<T> get(String lang, String key, Class<T> type);

    CompletableFuture<String> get(String lang, String key, Object... placeholders);
    CompletableFuture<String> get(String lang, String key, Map<String, Object> placeholders);

    // Backward-compat aliases: getAsync(...) delegate to get(...)
    @Deprecated
    default <T> CompletableFuture<T> getAsync(String lang, String key, Class<T> type) {
        return get(lang, key, type);
    }

    @Deprecated
    default CompletableFuture<String> getAsync(String lang, String key, Object... placeholders) {
        return get(lang, key, placeholders);
    }

    @Deprecated
    default CompletableFuture<String> getAsync(String lang, String key, Map<String, Object> placeholders) {
        return get(lang, key, placeholders);
    }
}
