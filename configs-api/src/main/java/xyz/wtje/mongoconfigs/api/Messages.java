package xyz.wtje.mongoconfigs.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for retrieving localized messages.
 * All async methods use virtual threads (Java 21+) and are completely non-blocking,
 * making them safe to call from any thread including the main server thread.
 */
public interface Messages {
    CompletableFuture<String> get(String path);
    CompletableFuture<String> get(String path, String language);
    CompletableFuture<String> get(String path, Object... placeholders);
    CompletableFuture<String> get(String path, String language, Object... placeholders);
    
    
    CompletableFuture<String> get(String path, Map<String, Object> placeholders);
    
    
    CompletableFuture<String> get(String path, String language, Map<String, Object> placeholders);
    
    CompletableFuture<List<String>> getList(String path);
    CompletableFuture<List<String>> getList(String path, String language);
    
    /**
     * Gets a message and passes it to the consumer.
     * Non-blocking and safe for main thread usage.
     * 
     * @param path The message path
     * @param consumer The consumer to receive the message
     */
    default void use(String path, Consumer<String> consumer) {
        get(path).thenAccept(consumer);
    }
    
    /**
     * Gets a message for a specific language and passes it to the consumer.
     * Non-blocking and safe for main thread usage.
     * 
     * @param path The message path
     * @param language The language code
     * @param consumer The consumer to receive the message
     */
    default void use(String path, String language, Consumer<String> consumer) {
        get(path, language).thenAccept(consumer);
    }
    
    /**
     * Gets a formatted message and passes it to the consumer.
     * Non-blocking and safe for main thread usage.
     * 
     * @param path The message path
     * @param consumer The consumer to receive the message
     * @param placeholders The placeholder values
     */
    default void useFormat(String path, Consumer<String> consumer, Object... placeholders) {
        get(path, placeholders).thenAccept(consumer);
    }
    
    /**
     * Gets a formatted message for a specific language and passes it to the consumer.
     * Non-blocking and safe for main thread usage.
     * 
     * @param path The message path
     * @param language The language code
     * @param consumer The consumer to receive the message
     * @param placeholders The placeholder values
     */
    default void useFormat(String path, String language, Consumer<String> consumer, Object... placeholders) {
        get(path, language, placeholders).thenAccept(consumer);
    }
    
    default View view() {
        return new View(this, null);
    }

    default View view(String language) {
        return new View(this, language);
    }

    final class View {
        private final Messages delegate;
        private final String language;

        View(Messages delegate, String language) {
            this.delegate = delegate;
            this.language = language;
        }

        public CompletableFuture<String> future(String path) {
            return language == null ? delegate.get(path) : delegate.get(path, language);
        }

        public CompletableFuture<String> future(String path, Object... placeholders) {
            return language == null
                ? delegate.get(path, placeholders)
                : delegate.get(path, language, placeholders);
        }

        
        public CompletableFuture<String> future(String path, Map<String, Object> placeholders) {
            return language == null
                ? delegate.get(path, placeholders)
                : delegate.get(path, language, placeholders);
        }

        public String get(String path) {
            return future(path).join();
        }

        public String format(String path, Object... placeholders) {
            return future(path, placeholders).join();
        }

        
        public String format(String path, Map<String, Object> placeholders) {
            return future(path, placeholders).join();
        }

        public void use(String path, Consumer<String> consumer) {
            future(path).thenAccept(consumer);
        }

        public CompletableFuture<List<String>> listFuture(String path) {
            return language == null ? delegate.getList(path) : delegate.getList(path, language);
        }

        public List<String> list(String path) {
            return listFuture(path).join();
        }

        public void useList(String path, Consumer<List<String>> consumer) {
            listFuture(path).thenAccept(consumer);
        }

        public String language() {
            return language;
        }
    }

}

