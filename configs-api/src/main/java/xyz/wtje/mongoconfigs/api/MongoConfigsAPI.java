package xyz.wtje.mongoconfigs.api;

/**
 * Central API access point for MongoDB Configs
 */
public class MongoConfigsAPI {
    private static ConfigManager configManager;
    private static LanguageManager languageManager;
    
    public static void setConfigManager(ConfigManager configManager) {
        MongoConfigsAPI.configManager = configManager;
    }
    
    public static void setLanguageManager(LanguageManager languageManager) {
        MongoConfigsAPI.languageManager = languageManager;
    }
    
    public static ConfigManager getConfigManager() {
        if (configManager == null) {
            throw new IllegalStateException("ConfigManager not initialized");
        }
        return configManager;
    }
    
    public static LanguageManager getLanguageManager() {
        if (languageManager == null) {
            throw new IllegalStateException("LanguageManager not initialized");
        }
        return languageManager;
    }
    
    public static void reset() {
        configManager = null;
        languageManager = null;
    }
}