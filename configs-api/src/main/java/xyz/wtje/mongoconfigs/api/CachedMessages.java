package xyz.wtje.mongoconfigs.api;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class CachedMessages implements Messages {
    private final String id;
    private final String defaultLanguage;
    private final Set<String> supportedLanguages;
    private final Map<String, Map<String, MessageValue>> values = new ConcurrentHashMap<>();
    private volatile boolean needsRefresh = false;
    
    // Lock for atomic cache operations - prevents stale reads during reload
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // Virtual thread executor for non-blocking operations (Java 21+)
    // Each task runs on its own lightweight virtual thread - zero main thread blocking!
    private static final Executor ASYNC_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    CachedMessages(String id, String defaultLanguage, String[] supportedLanguages) {
        this.id = id;
        this.defaultLanguage = defaultLanguage;
        this.supportedLanguages = new LinkedHashSet<>(Arrays.asList(supportedLanguages));
        this.supportedLanguages.add(defaultLanguage);
    }
    
    /**
     * Returns the collection ID for this messages instance.
     */
    String getId() {
        return id;
    }

    /**
     * Atomically replaces default language values.
     * Uses write lock to ensure no stale reads during update.
     */
    void replaceDefaults(Map<String, Object> rawValues) {
        cacheLock.writeLock().lock();
        try {
            Map<String, MessageValue> normalized = normalize(rawValues);
            values.put(defaultLanguage, new ConcurrentHashMap<>(normalized));
            for (String language : supportedLanguages) {
                if (language.equals(defaultLanguage)) {
                    continue;
                }
                values.compute(language, (lang, existing) -> {
                    Map<String, MessageValue> next = existing != null ? new ConcurrentHashMap<>(existing) : new ConcurrentHashMap<>();
                    normalized.forEach(next::putIfAbsent);
                    return next;
                });
            }
            needsRefresh = false;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Atomically replaces default values using virtual threads.
     * Non-blocking and safe for main thread usage.
     */
    CompletableFuture<Void> replaceDefaultsAsync(Map<String, Object> rawValues) {
        return CompletableFuture.runAsync(() -> replaceDefaults(rawValues), ASYNC_EXECUTOR);
    }

    /**
     * Atomically merges language-specific values.
     * Uses write lock to ensure no stale reads during update.
     */
    void mergeLanguage(String language, Map<String, Object> rawValues) {
        cacheLock.writeLock().lock();
        try {
            Map<String, MessageValue> normalized = normalize(rawValues);
            values.compute(language, (lang, existing) -> {
                Map<String, MessageValue> next = existing != null ? new ConcurrentHashMap<>(existing) : new ConcurrentHashMap<>();
                next.putAll(normalized);
                return next;
            });
            needsRefresh = false;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Atomically merges language values using virtual threads.
     * Non-blocking and safe for main thread usage.
     */
    CompletableFuture<Void> mergeLanguageAsync(String language, Map<String, Object> rawValues) {
        return CompletableFuture.runAsync(() -> mergeLanguage(language, rawValues), ASYNC_EXECUTOR);
    }

    /**
     * Invalidates all cached values atomically.
     * Uses write lock to ensure no stale reads during invalidation.
     */
    void invalidate() {
        cacheLock.writeLock().lock();
        try {
            values.clear();
            needsRefresh = true;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Invalidates cached values for a specific language.
     */
    void invalidateLanguage(String language) {
        cacheLock.writeLock().lock();
        try {
            values.remove(language);
            needsRefresh = true;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    
    boolean needsRefresh() {
        cacheLock.readLock().lock();
        try {
            return needsRefresh || values.isEmpty();
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    
    void markForRefresh() {
        cacheLock.writeLock().lock();
        try {
            needsRefresh = true;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    private Map<String, MessageValue> normalize(Map<String, Object> raw) {
        Map<String, MessageValue> normalized = new ConcurrentHashMap<>();
        if (raw != null) {
            raw.forEach((path, value) -> {
                if (path != null) {
                    normalized.put(path, MessageValue.from(value));
                }
            });
        }
        return normalized;
    }

    private MessageValue resolve(String language, String path) {
        cacheLock.readLock().lock();
        try {
            if (language != null) {
                Map<String, MessageValue> langValues = values.get(language);
                if (langValues != null) {
                    MessageValue value = langValues.get(path);
                    if (value != null) {
                        return value;
                    }
                }
            }
            Map<String, MessageValue> defaults = values.get(defaultLanguage);
            if (defaults != null) {
                MessageValue value = defaults.get(path);
                if (value != null) {
                    return value;
                }
            }
            return null;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    private String missing(String path, String language) {
        String lang = (language == null || language.isBlank()) ? defaultLanguage : language;
        return "Missing message: " + path + " for language: " + lang;
    }

    @Override
    public CompletableFuture<String> get(String path) {
        return get(path, defaultLanguage);
    }

    @Override
    public CompletableFuture<String> get(String path, String language) {
        // Use virtual threads for non-blocking async resolution
        return CompletableFuture.supplyAsync(() -> {
            MessageValue value = resolve(language, path);
            return value != null ? value.asString(path, language, defaultLanguage) : missing(path, language);
        }, ASYNC_EXECUTOR);
    }

    @Override
    public CompletableFuture<String> get(String path, Object... placeholders) {
        return get(path, defaultLanguage, placeholders);
    }

    @Override
    public CompletableFuture<String> get(String path, String language, Object... placeholders) {
        // Use virtual threads for non-blocking async resolution
        return CompletableFuture.supplyAsync(() -> {
            MessageValue value = resolve(language, path);
            String template = value != null ? value.asString(path, language, defaultLanguage) : missing(path, language);
            return applyPlaceholders(template, placeholders);
        }, ASYNC_EXECUTOR);
    }

    @Override
    public CompletableFuture<List<String>> getList(String path) {
        return getList(path, defaultLanguage);
    }

    @Override
    public CompletableFuture<List<String>> getList(String path, String language) {
        // Use virtual threads for non-blocking async resolution
        return CompletableFuture.supplyAsync(() -> {
            MessageValue value = resolve(language, path);
            return value != null ? value.asList(missing(path, language)) : List.of(missing(path, language));
        }, ASYNC_EXECUTOR);
    }

    @Override
    public CompletableFuture<String> get(String path, String language, Map<String, Object> placeholders) {
        // Use virtual threads for non-blocking async resolution
        return CompletableFuture.supplyAsync(() -> {
            MessageValue value = resolve(language, path);
            String template = value != null ? value.asString(path, language, defaultLanguage) : missing(path, language);
            return applyMapPlaceholders(template, placeholders);
        }, ASYNC_EXECUTOR);
    }

    @Override
    public CompletableFuture<String> get(String path, Map<String, Object> placeholders) {
        return get(path, defaultLanguage, placeholders);
    }

    private String applyPlaceholders(String template, Object... placeholders) {
        if (template == null || template.isEmpty() || placeholders == null || placeholders.length == 0) {
            return template;
        }
        String result = template;
        for (int i = 0; i < placeholders.length; i++) {
            Object placeholder = placeholders[i];
            result = result.replace("{" + i + "}", placeholder == null ? "null" : placeholder.toString());
        }
        return result;
    }

    private String applyMapPlaceholders(String template, Map<String, Object> placeholders) {
        if (template == null || template.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            result = result.replace("{" + key + "}", value == null ? "null" : value.toString());
        }
        return result;
    }

    private record MessageValue(String text, List<String> list) {
        static MessageValue from(Object value) {
            if (value == null) {
                return new MessageValue(null, null);
            }
            if (value instanceof MessageValue mv) {
                return mv;
            }
            if (value instanceof List<?> rawList) {
                return new MessageValue(null, copyList(rawList));
            }
            if (value.getClass().isArray()) {
                return new MessageValue(null, copyArray(value));
            }
            return new MessageValue(value.toString(), null);
        }

        private static List<String> copyList(List<?> rawList) {
            List<String> copy = new ArrayList<>(rawList.size());
            for (Object element : rawList) {
                copy.add(element == null ? "null" : element.toString());
            }
            return List.copyOf(copy);
        }

        private static List<String> copyArray(Object array) {
            int length = Array.getLength(array);
            List<String> copy = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(array, i);
                copy.add(element == null ? "null" : element.toString());
            }
            return List.copyOf(copy);
        }

        String asString(String path, String language, String defaultLanguage) {
            if (text != null) {
                return text;
            }
            if (list != null && !list.isEmpty()) {
                return String.join("\n", list);
            }
            String lang = language == null || language.isBlank() ? defaultLanguage : language;
            return "Missing message: " + path + " for language: " + lang;
        }

        List<String> asList(String fallback) {
            if (list != null) {
                return list;
            }
            if (text != null) {
                return List.of(text);
            }
            return List.of(fallback);
        }
    }
}
