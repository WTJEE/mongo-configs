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
            logVerbose("Using MongoDB connection string: " + 
                (connectionString.contains("@") ? 
                    connectionString.replaceAll("://[^@]*@", "://***@") :
                    connectionString));

            MongoConfig mongoConfig = createMongoConfig();

            configManager = new ConfigManagerImpl(mongoConfig);

            configManager.setColorProcessor(new BukkitColorProcessor());

            languageManager = new LanguageManagerImpl(
                configManager,
                languageConfig,
                configManager.getMongoManager(),
                pluginConfig.getPlayerLanguagesDatabase(),
                pluginConfig.getPlayerLanguagesCollection(),
                pluginConfig.isDebugLogging(),
                pluginConfig.isVerboseLogging()
            );

            MongoConfigsAPI.setConfigManager(configManager);
            MongoConfigsAPI.setLanguageManager(languageManager);

            CompletableFuture.runAsync(() -> {
                try {
                    configManager.initialize();
                    languageManager.initialize();

                    LanguageSelectionGUI.preloadCache(languageManager, languageConfig);

                    logVerbose("MongoDB Configs initialized successfully");
                    logVerbose("Performance configuration: " +
                        pluginConfig.getIoThreads() + " I/O threads, " +
                        pluginConfig.getWorkerThreads() + " worker threads");
                    logVerbose("Cache: Using simple in-memory maps (Caffeine cache settings ignored)");
                    logVerbose("Cache config values: max-size=" + pluginConfig.getCacheMaxSize() +
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
        logDebug("Preparing MongoConfig with connection string: " + 
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

        logVerbose("Change detection enabled (Change Streams + Polling fallback)");

        config.setIoThreads(pluginConfig.getIoThreads());
        config.setWorkerThreads(pluginConfig.getWorkerThreads());

        config.setDebugLogging(pluginConfig.isDebugLogging());
        config.setVerboseLogging(pluginConfig.isVerboseLogging());

        config.setDefaultLanguage(languageConfig.getDefaultLanguage());
        config.setSupportedLanguages(languageConfig.getSupportedLanguages());
        config.setLanguageDisplayNames(languageConfig.getLanguageDisplayNames());

        config.setPlayerLanguagesDatabase(pluginConfig.getPlayerLanguagesDatabase());
        config.setPlayerLanguagesCollection(pluginConfig.getPlayerLanguagesCollection());
        config.setTypedConfigsCollection(pluginConfig.getTypedConfigsCollection());
        config.setConfigsCollection(pluginConfig.getConfigsCollection());
        
        try {
            config.setIgnoredDatabases(new java.util.HashSet<>(pluginConfig.getIgnoredDatabases()));
            config.setIgnoredCollections(new java.util.HashSet<>(pluginConfig.getIgnoredCollections()));
        } catch (Exception ignored) {}

        return config;
    }

    private void registerCommands() {
        
        if (getCommand("language") != null) {
            LanguageCommand languageCommand = new LanguageCommand(languageManager, languageConfig);
            getCommand("language").setExecutor(languageCommand);
            getCommand("language").setTabCompleter(languageCommand);
            logDebug("Language command registered successfully");
        } else {
            getLogger().severe("Failed to register /language command - command not found in plugin.yml");
        }

        
        if (getCommand("mongoconfigs") != null) {
            MongoConfigsCommand adminCommand = new MongoConfigsCommand(configManager, languageManager, this, languageConfig);
            getCommand("mongoconfigs").setExecutor(adminCommand);
            getCommand("mongoconfigs").setTabCompleter(adminCommand);
            logDebug("MongoConfigs admin command registered");
        } else {
            getLogger().warning("Could not register /mongoconfigs command");
        }

        
        if (getCommand("configsmanager") != null) {
            ConfigsManagerCommand configsManagerCommand = new ConfigsManagerCommand(this);
            getCommand("configsmanager").setExecutor(configsManagerCommand);
            getCommand("configsmanager").setTabCompleter(configsManagerCommand);
            logDebug("ConfigsManager command registered");
        } else {
            getLogger().warning("Could not register /configsmanager command");
        }

        
        if (getCommand("hotreload") != null) {
            HotReloadCommand hotReloadCommand = new HotReloadCommand(this, configManager.getTypedConfigManager(), languageManager);
            getCommand("hotreload").setExecutor(hotReloadCommand);
            logDebug("HotReload command registered");
        } else {
            getLogger().warning("Could not register /hotreload command");
        }

        logDebug("Command registration phase completed");
    }

    private void registerListeners() {
        logDebug("Registering event listeners...");
        
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(languageManager), this);
        logDebug("PlayerJoinListener registered");

        getServer().getPluginManager().registerEvents(
                new GUIListener(this), this);
        logDebug("GUIListener registered with plugin instance");

        logDebug("Event listeners registered successfully");
        
        
        if (languageManager != null && languageConfig != null) {
            xyz.wtje.mongoconfigs.paper.gui.LanguageSelectionGUI.preloadGUIElements(languageManager, languageConfig);
            logVerbose("GUI elements preloaded for optimal performance");
        }
    }


    public void reloadPlugin() {
        getLogger().info("Reloading MongoDB Configs plugin...");

        CompletableFuture.runAsync(() -> {
            try {
                reloadConfig();
                pluginConfig = new PluginConfiguration(this);

                if (configManager != null) {
                    
                    configManager.reloadAll().join();
                }

                if (languageManager != null) {
                    languageManager.reload();
                }

                
                refreshGUIMessages();

                logVerbose("Plugin reloaded successfully");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error reloading plugin", e);
            }
        });
    }

    
    public void refreshGUIMessages() {
        try {
            
            if (languageManager != null && languageConfig != null) {
                LanguageSelectionGUI.preloadCache(languageManager, languageConfig);
                logDebug("GUI messages cache refreshed");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error refreshing GUI messages", e);
        }
    }

    
    public ConfigManagerImpl getConfigManager() {
        return configManager;
    }

    
    public LanguageManagerImpl getLanguageManager() {
        return languageManager;
    }
    
    
    public LanguageConfiguration getLanguageConfiguration() {
        return languageConfig;
    }

    private void logDebug(String message) {
        if (pluginConfig != null && pluginConfig.isDebugLogging()) {
            getLogger().info(message);
        }
    }

    private void logVerbose(String message) {
        if (pluginConfig != null && (pluginConfig.isVerboseLogging() || pluginConfig.isDebugLogging())) {
            getLogger().info(message);
        }
    }
}




