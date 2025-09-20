package xyz.wtje.mongoconfigs.paper;

import org.bukkit.plugin.java.JavaPlugin;
import xyz.wtje.mongoconfigs.api.MongoConfigsAPI;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;
import xyz.wtje.mongoconfigs.paper.commands.ConfigsManagerCommand;
import xyz.wtje.mongoconfigs.paper.commands.HotReloadCommand;
import xyz.wtje.mongoconfigs.paper.commands.LanguageCommand;
import xyz.wtje.mongoconfigs.paper.commands.MongoConfigsCommand;
import xyz.wtje.mongoconfigs.paper.config.LanguageConfiguration;
import xyz.wtje.mongoconfigs.paper.config.PluginConfiguration;
import xyz.wtje.mongoconfigs.paper.gui.LanguageSelectionGUI;
import xyz.wtje.mongoconfigs.paper.impl.LanguageManagerImpl;
import xyz.wtje.mongoconfigs.paper.listeners.GUIListener;
import xyz.wtje.mongoconfigs.paper.listeners.PlayerJoinListener;
import xyz.wtje.mongoconfigs.paper.util.BukkitColorProcessor;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MongoConfigsPlugin extends JavaPlugin {

    private PluginConfiguration pluginConfig;
    private LanguageConfiguration languageConfig;
    private ConfigManagerImpl configManager;
    private LanguageManagerImpl languageManager;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            reloadConfig();
            pluginConfig = new PluginConfiguration(this);
            languageConfig = new LanguageConfiguration(this);

            String connectionString = pluginConfig.getMongoConnectionString();
            getLogger().info("Using MongoDB connection string: " + 
                (connectionString.contains("@") ? 
                    connectionString.replaceAll("://[^@]*@", "://***@") :
                    connectionString));

            MongoConfig mongoConfig = createMongoConfig();

            configManager = new ConfigManagerImpl(mongoConfig);

            configManager.setColorProcessor(new BukkitColorProcessor());

            languageManager = new LanguageManagerImpl(configManager, languageConfig, 
                configManager.getMongoManager(), pluginConfig.getPlayerLanguagesDatabase(), pluginConfig.getPlayerLanguagesCollection());

            MongoConfigsAPI.setConfigManager(configManager);
            MongoConfigsAPI.setLanguageManager(languageManager);

            CompletableFuture.runAsync(() -> {
                try {
                    configManager.initialize();
                    languageManager.initialize();

                    LanguageSelectionGUI.preloadCache(languageManager, languageConfig);

                    getLogger().info("MongoDB Configs initialized successfully");
                    getLogger().info("Performance configuration: " + 
                        pluginConfig.getIoThreads() + " I/O threads, " + 
                        pluginConfig.getWorkerThreads() + " worker threads");
                    getLogger().info("Cache: Using simple in-memory maps (Caffeine cache settings ignored)");
                    getLogger().info("Cache config values: max-size=" + pluginConfig.getCacheMaxSize() + 
                        ", ttl=" + pluginConfig.getCacheTtlSeconds() + "s" +
                        ", refresh-after=" + pluginConfig.getCacheRefreshAfterSeconds() + "s (NOT USED)");
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Failed to initialize MongoDB Configs", e);
                    getServer().getPluginManager().disablePlugin(this);
                }
            });

            registerCommands();

            registerListeners();


            getLogger().info("MongoDB Configs plugin enabled successfully");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable MongoDB Configs plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            MongoConfigsAPI.reset();

            if (configManager != null) {
                configManager.shutdown();
            }

            if (languageManager != null) {
                languageManager.shutdown();
            }

            getLogger().info("MongoDB Configs plugin disabled successfully");

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error during plugin shutdown", e);
        }
    }

    private MongoConfig createMongoConfig() {
        MongoConfig config = new MongoConfig();

        String connectionString = pluginConfig.getMongoConnectionString();
        getLogger().info("createMongoConfig: Setting connection string to: " + 
            (connectionString.contains("@") ? 
                connectionString.replaceAll("://[^@]*@", "://***@") :
                connectionString));

        config.setConnectionString(connectionString);
        config.setDatabase(pluginConfig.getMongoDatabase());
        config.setMaxPoolSize(pluginConfig.getMongoMaxPoolSize());
        config.setMinPoolSize(pluginConfig.getMongoMinPoolSize());
        config.setMaxConnectionIdleTime(pluginConfig.getMongoMaxIdleTime());
        config.setMaxConnectionLifeTime(pluginConfig.getMongoMaxLifeTime());
        config.setServerSelectionTimeoutMs(pluginConfig.getMongoServerSelectionTimeout());
        config.setSocketTimeoutMs(pluginConfig.getMongoSocketTimeout());
        config.setConnectTimeoutMs(pluginConfig.getMongoConnectTimeout());

        config.setCacheMaxSize(pluginConfig.getCacheMaxSize());
        config.setCacheTtlSeconds(pluginConfig.getCacheTtlSeconds());
        config.setCacheRefreshAfterSeconds(pluginConfig.getCacheRefreshAfterSeconds());
        config.setCacheRecordStats(pluginConfig.isCacheRecordStats());

        config.setEnableChangeStreams(pluginConfig.isChangeStreamsEnabled());
        config.setChangeStreamResumeRetries(pluginConfig.getChangeStreamResumeRetries());
        config.setChangeStreamResumeDelayMs(pluginConfig.getChangeStreamResumeDelay());

        getLogger().info("Change Streams enabled for real-time cache updates");

        config.setIoThreads(pluginConfig.getIoThreads());
        config.setWorkerThreads(pluginConfig.getWorkerThreads());

        config.setDebugLogging(pluginConfig.isDebugLogging());
        config.setVerboseLogging(pluginConfig.isVerboseLogging());

        config.setDefaultLanguage(languageConfig.getDefaultLanguage());
        config.setSupportedLanguages(languageConfig.getSupportedLanguages());
        config.setLanguageDisplayNames(languageConfig.getLanguageDisplayNames());

        config.setPlayerLanguagesCollection(pluginConfig.getPlayerLanguagesCollection());
        config.setTypedConfigsCollection(pluginConfig.getTypedConfigsCollection());
        config.setConfigsCollection(pluginConfig.getConfigsCollection());

        return config;
    }

    private void registerCommands() {
        LanguageCommand languageCommand = new LanguageCommand(languageManager, languageConfig);
        getCommand("language").setExecutor(languageCommand);
        getCommand("language").setTabCompleter(languageCommand);

        MongoConfigsCommand adminCommand = new MongoConfigsCommand(configManager, languageManager, this, languageConfig);
        getCommand("mongoconfigs").setExecutor(adminCommand);
        getCommand("mongoconfigs").setTabCompleter(adminCommand);

        ConfigsManagerCommand configsManagerCommand = new ConfigsManagerCommand(this);
        getCommand("configsmanager").setExecutor(configsManagerCommand);
        getCommand("configsmanager").setTabCompleter(configsManagerCommand);

        HotReloadCommand hotReloadCommand = new HotReloadCommand(this, configManager.getTypedConfigManager());
        getCommand("hotreload").setExecutor(hotReloadCommand);

        getLogger().info("Commands registered successfully");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(languageManager), this);

        getServer().getPluginManager().registerEvents(
                new GUIListener(), this);

        getLogger().info("Event listeners registered successfully");
    }


    public void reloadPlugin() {
        getLogger().info("Reloading MongoDB Configs plugin...");

        CompletableFuture.runAsync(() -> {
            try {
                reloadConfig();
                pluginConfig = new PluginConfiguration(this);

                if (configManager != null) {
                    // Reload all collections and refresh cache
                    configManager.reloadAll().join();
                }

                if (languageManager != null) {
                    languageManager.reload();
                }

                // Refresh GUI cache to ensure updated messages are used
                refreshGUIMessages();

                getLogger().info("Plugin reloaded successfully");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error reloading plugin", e);
            }
        });
    }

    /**
     * Refresh cached messages for GUI components
     */
    public void refreshGUIMessages() {
        try {
            // Force refresh the LanguageSelectionGUI cache
            if (languageManager != null && languageConfig != null) {
                LanguageSelectionGUI.preloadCache(languageManager, languageConfig);
                getLogger().info("GUI messages cache refreshed");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error refreshing GUI messages", e);
        }
    }

    /**
     * Get the config manager instance
     */
    public ConfigManagerImpl getConfigManager() {
        return configManager;
    }

    /**
     * Get the language manager instance  
     */
    public LanguageManagerImpl getLanguageManager() {
        return languageManager;
    }

}

