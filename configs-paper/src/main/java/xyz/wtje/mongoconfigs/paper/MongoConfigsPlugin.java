package xyz.wtje.mongoconfigs.paper;

import org.bukkit.plugin.java.JavaPlugin;
import xyz.wtje.mongoconfigs.api.MongoConfigsAPI;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;
import xyz.wtje.mongoconfigs.paper.commands.ConfigsManagerCommand;
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
                    connectionString.replaceAll("://[^@]+@", "://***:***@") : 
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
                    getLogger().info("Cache configuration: max-size=" + pluginConfig.getCacheMaxSize() + 
                        ", ttl=" + pluginConfig.getCacheTtlSeconds() + "s" +
                        ", refresh-after=" + pluginConfig.getCacheRefreshAfterSeconds() + "s");
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Failed to initialize MongoDB Configs", e);
                    getServer().getPluginManager().disablePlugin(this);
                }
            });

            registerCommands();

            registerListeners();

            startStatisticsTask();
            
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
                connectionString.replaceAll("://[^@]+@", "://***:***@") : 
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
        
        config.setIoThreads(pluginConfig.getIoThreads());
        config.setWorkerThreads(pluginConfig.getWorkerThreads());
        
        config.setDefaultLanguage(languageConfig.getDefaultLanguage());
        config.setSupportedLanguages(languageConfig.getSupportedLanguages());
        config.setLanguageDisplayNames(languageConfig.getLanguageDisplayNames());
        
        config.setPlayerLanguagesCollection(pluginConfig.getPlayerLanguagesCollection());
        
        return config;
    }
    
    private void registerCommands() {
        LanguageCommand languageCommand = new LanguageCommand(languageManager, languageConfig);
        getCommand("language").setExecutor(languageCommand);
        getCommand("language").setTabCompleter(languageCommand);

        MongoConfigsCommand adminCommand = new MongoConfigsCommand(configManager, languageManager, this, languageConfig);
        getCommand("mongoconfigs").setExecutor(adminCommand);
        getCommand("mongoconfigs").setTabCompleter(adminCommand);

        ConfigsManagerCommand configsManagerCommand = new ConfigsManagerCommand();
        getCommand("configsmanager").setExecutor(configsManagerCommand);
        getCommand("configsmanager").setTabCompleter(configsManagerCommand);
        
        getLogger().info("Commands registered successfully");
    }
    
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(languageManager), this);

        getServer().getPluginManager().registerEvents(
                new GUIListener(), this);
        
        getLogger().info("Event listeners registered successfully");
    }
    
    private void startStatisticsTask() {
        if (!pluginConfig.isLogCacheStats()) {
            return;
        }
        
        long interval = pluginConfig.getCacheStatsInterval() * 20L;
        
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                if (configManager != null) {
                    var cacheStats = configManager.getCacheStats();
                    var metrics = configManager.getMetrics();
                    
                    getLogger().info(String.format(
                            "Cache Stats - Hit Rate: %.2f%%, Size: %d, Requests: %d, Hits: %d, Misses: %d",
                            cacheStats.getHitRate() * 100,
                            cacheStats.getSize(),
                            cacheStats.getRequestCount(),
                            cacheStats.getHitCount(),
                            cacheStats.getMissCount()
                    ));
                    
                    getLogger().info(String.format(
                            "Performance - MongoDB Ops: %d, Cache Ops: %d, Connections: %d/%d, Change Streams: %s",
                            metrics.getMongoOperationsCount(),
                            metrics.getCacheOperationsCount(),
                            metrics.getActiveConnections(),
                            metrics.getConnectionPoolSize(),
                            metrics.isChangeStreamsActive() ? "Active" : "Inactive"
                    ));
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error logging statistics", e);
            }
        }, interval, interval);
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
                
                getLogger().info("Plugin reloaded successfully");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error reloading plugin", e);
            }
        });
    }

}