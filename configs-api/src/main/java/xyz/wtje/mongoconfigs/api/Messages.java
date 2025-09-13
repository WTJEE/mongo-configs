package xyz.wtje.mongoconfigs.api;

import java.util.Map;

public interface Messages {
    <T> T get(String lang, String key, Class<T> type);

    String get(String lang, String key, Object... placeholders);
    String get(String lang, String key, Map<String, Object> placeholders);
}
