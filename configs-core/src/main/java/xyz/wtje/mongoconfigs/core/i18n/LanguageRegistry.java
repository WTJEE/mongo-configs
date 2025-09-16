package xyz.wtje.mongoconfigs.core.i18n;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LanguageRegistry {

    private static final ConcurrentMap<String, String> LANGUAGES = new ConcurrentHashMap<>();

    static {
        register("en", "English");
        register("pl", "Polski");
        register("de", "Deutsch");
        register("es", "Español");
        register("fr", "Français");
        register("ru", "Русский");
    }

    public static void register(String code, String label) {
        LANGUAGES.put(code, label);
    }

    public static Set<String> codes() {
        return Set.copyOf(LANGUAGES.keySet());
    }

    public static String label(String code) {
        return LANGUAGES.getOrDefault(code, code);
    }

    public static boolean isRegistered(String code) {
        return LANGUAGES.containsKey(code);
    }

    public static ConcurrentMap<String, String> getAll() {
        return new ConcurrentHashMap<>(LANGUAGES);
    }

    private LanguageRegistry() {}
}

