package xyz.wtje.mongoconfigs.api.core;

import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsDatabase;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsCollection;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

import java.util.Set;

public final class Annotations {

    public static String idFrom(Class<?> type) {
        var a = type.getAnnotation(ConfigsFileProperties.class);
        if (a == null || a.name().isBlank())
            throw new IllegalStateException("Missing @ConfigsFileProperties on " + type.getName());
        return a.name();
    }

    public static String databaseFrom(Class<?> type) {
        var a = type.getAnnotation(ConfigsDatabase.class);
        return a != null ? a.value() : null;  // null = use default from config
    }

    public static String collectionFrom(Class<?> type) {
        var a = type.getAnnotation(ConfigsCollection.class);
        return a != null ? a.value() : idFrom(type);  // fallback to ID
    }

    public static Set<String> langsFrom(Class<?> type) {
        var a = type.getAnnotation(SupportedLanguages.class);
        return a == null ? Set.of() : Set.of(a.value());
    }

    private Annotations() {}
}
