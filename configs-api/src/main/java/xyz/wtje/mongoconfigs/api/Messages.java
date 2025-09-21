package xyz.wtje.mongoconfigs.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface Messages {
    CompletableFuture<String> get(String path);
    CompletableFuture<String> get(String path, String language);
    CompletableFuture<String> get(String path, Object... placeholders);
    CompletableFuture<String> get(String path, String language, Object... placeholders);
    
    /**
     * Get message with placeholders as Map
     * @param path Message key path
     * @param placeholders Map of placeholders
     * @return CompletableFuture with formatted message
     */
    CompletableFuture<String> get(String path, Map<String, Object> placeholders);
    
    /**
     * Get message with placeholders as Map for specific language
     * @param path Message key path
     * @param language Language code
     * @param placeholders Map of placeholders
     * @return CompletableFuture with formatted message
     */
    CompletableFuture<String> get(String path, String language, Map<String, Object> placeholders);
    
    CompletableFuture<List<String>> getList(String path);
    CompletableFuture<List<String>> getList(String path, String language);
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

        /**
         * Get message future with placeholders as Map
         */
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

        /**
         * Get formatted message synchronously with Map placeholders
         */
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

