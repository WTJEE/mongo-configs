package xyz.wtje.mongoconfigs.api;

public final class MongoConfigsAPI {

    private static ConfigManager configManager;
    private static LanguageManager languageManager;

    private MongoConfigsAPI() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ConfigManager getConfigManager() {
        if (configManager == null) {
            throw new IllegalStateException("MongoDB Configs not initialized! Ensure the plugin is loaded.");
        }
        return configManager;
    }

    public static LanguageManager getLanguageManager() {
        if (languageManager == null) {
            throw new IllegalStateException("MongoDB Configs not initialized! Ensure the plugin is loaded.");
        }
        return languageManager;
    }

    public static boolean isInitialized() {
        return configManager != null && languageManager != null;
    }

    public static void setConfigManager(ConfigManager manager) {
        configManager = manager;
    }

    public static void setLanguageManager(LanguageManager manager) {
        languageManager = manager;
    }

    public static void reset() {
        configManager = null;
        languageManager = null;
    }
}
