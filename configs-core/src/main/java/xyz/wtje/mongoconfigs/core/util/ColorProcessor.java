package xyz.wtje.mongoconfigs.core.util;


public interface ColorProcessor {

    String colorize(String message);

    String stripColors(String message);

    Object getCacheStats();

    void clearCache();

    long getCacheSize();
}