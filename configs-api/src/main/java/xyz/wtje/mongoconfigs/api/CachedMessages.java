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

final class CachedMessages implements Messages {
    private final String defaultLanguage;
    private final Set<String> supportedLanguages;
    private final Map<String, Map<String, MessageValue>> values = new ConcurrentHashMap<>();
    private volatile boolean needsRefresh = false;

    CachedMessages(String id, String defaultLanguage, String[] supportedLanguages) {
        this.defaultLanguage = defaultLanguage;
        this.supportedLanguages = new LinkedHashSet<>(Arrays.asList(supportedLanguages));
        this.supportedLanguages.add(defaultLanguage);
    }

    void replaceDefaults(Map<String, Object> rawValues) {
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
    }

    void mergeLanguage(String language, Map<String, Object> rawValues) {
        Map<String, MessageValue> normalized = normalize(rawValues);
        values.compute(language, (lang, existing) -> {
            Map<String, MessageValue> next = existing != null ? new ConcurrentHashMap<>(existing) : new ConcurrentHashMap<>();
            next.putAll(normalized);
            return next;
        });
        needsRefresh = false;
    }

    /**
     * Clear all cached values - used when cache needs to be refreshed
     */
    void invalidate() {
        values.clear();
        needsRefresh = true;
    }

    /**
     * Check if this cached messages instance needs refreshing
     */
    boolean needsRefresh() {
        return needsRefresh || values.isEmpty();
    }

    /**
     * Mark as needing refresh without clearing values immediately
     */
    void markForRefresh() {
        needsRefresh = true;
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
        MessageValue value = resolve(language, path);
        String result = value != null ? value.asString(path, language, defaultLanguage) : missing(path, language);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<String> get(String path, Object... placeholders) {
        return get(path, defaultLanguage, placeholders);
    }

    @Override
    public CompletableFuture<String> get(String path, String language, Object... placeholders) {
        MessageValue value = resolve(language, path);
        String template = value != null ? value.asString(path, language, defaultLanguage) : missing(path, language);
        return CompletableFuture.completedFuture(applyPlaceholders(template, placeholders));
    }

    @Override
    public CompletableFuture<List<String>> getList(String path) {
        return getList(path, defaultLanguage);
    }

    @Override
    public CompletableFuture<List<String>> getList(String path, String language) {
        MessageValue value = resolve(language, path);
        List<String> list = value != null ? value.asList(missing(path, language)) : List.of(missing(path, language));
        return CompletableFuture.completedFuture(list);
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
