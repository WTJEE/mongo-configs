package xyz.wtje.mongoconfigs.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Messages {
    CompletableFuture<String> get(String path);
    CompletableFuture<String> get(String path, String language);
    CompletableFuture<String> get(String path, Object... placeholders);
    CompletableFuture<String> get(String path, String language, Object... placeholders);
    CompletableFuture<List<String>> getList(String path);
    CompletableFuture<List<String>> getList(String path, String language);
}
