package xyz.wtje.mongoconfigs.api.core;

import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsDatabase;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsCollection;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Annotations {
    private static final ConcurrentHashMap<Class<?>, String> ID_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, String> DB_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, String> COLLECTION_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Set<String>> LANGS_CACHE = new ConcurrentHashMap<>();

    public static String idFrom(Class<?> type) {
        return ID_CACHE.computeIfAbsent(type, t -> {
            var a = t.getAnnotation(ConfigsFileProperties.class);
            if (a == null || a.name().isBlank())
                throw new IllegalStateException("Missing @ConfigsFileProperties on " + t.getName());
            return a.name();
        });
    }

    public static String databaseFrom(Class<?> type) {
        return DB_CACHE.computeIfAbsent(type, t -> {
            var a = t.getAnnotation(ConfigsDatabase.class);
            return a != null ? a.value() : null;
        });
    }

    public static String collectionFrom(Class<?> type) {
        return COLLECTION_CACHE.computeIfAbsent(type, t -> {
            var a = t.getAnnotation(ConfigsCollection.class);
            return a != null ? a.value() : idFrom(t);
        });
    }

    public static Set<String> langsFrom(Class<?> type) {
        return LANGS_CACHE.computeIfAbsent(type, t -> {
            var a = t.getAnnotation(SupportedLanguages.class);
            return a == null ? Set.of() : Set.of(a.value());
        });
    }

    private Annotations() {}
}

