package xyz.wtje.mongoconfigs.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.command.CommandManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import xyz.wtje.mongoconfigs.api.MongoConfigsAPI;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;
import xyz.wtje.mongoconfigs.velocity.commands.LanguageCommand;
import xyz.wtje.mongoconfigs.velocity.commands.MongoConfigsCommand;
import xyz.wtje.mongoconfigs.velocity.config.PluginConfiguration;
import xyz.wtje.mongoconfigs.velocity.impl.LanguageManagerImpl;
import xyz.wtje.mongoconfigs.velocity.util.VelocityColorProcessor;
import xyz.wtje.mongoconfigs.velocity.commands.MongoConfigsProxyCommand;

@Plugin(id = "mongoconfigs", name = "MongoConfigs", version = "${project.version}")
public class MongoConfigsVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private PluginConfiguration pluginConfig;
    private ConfigManagerImpl configManager;
    private LanguageManagerImpl languageManager;

    @Inject
    public MongoConfigsVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            ensureDefaults();
            this.pluginConfig = new PluginConfiguration(dataDirectory.resolve("config.yml"));

            MongoConfig mongoConfig = createMongoConfig();

            this.configManager = new ConfigManagerImpl(mongoConfig);
            this.configManager.setColorProcessor(new VelocityColorProcessor());

            this.languageManager = new LanguageManagerImpl(
                configManager,
                pluginConfig,
                configManager.getMongoManager(),
                pluginConfig.getPlayerLanguagesDatabase(),
                pluginConfig.getPlayerLanguagesCollection()
            );

            MongoConfigsAPI.setConfigManager(configManager);
            MongoConfigsAPI.setLanguageManager(languageManager);

            CompletableFuture.runAsync(() -> {
                try {
                    configManager.initialize();
                    languageManager.initialize();
                    logger.info("MongoDB Configs initialized successfully on Velocity");
                    logger.info("Performance configuration: {} I/O threads, {} worker threads", pluginConfig.getIoThreads(), pluginConfig.getWorkerThreads());
                } catch (Exception e) {
                    logger.error("Failed to initialize MongoDB Configs", e);
                }
            });

            registerCommands();
            logger.info("MongoDB Configs Velocity plugin enabled");
        } catch (Exception e) {
            logger.error("Failed to enable MongoDB Configs Velocity plugin", e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        try {
            MongoConfigsAPI.reset();
            if (configManager != null) configManager.shutdown();
            if (languageManager != null) languageManager.shutdown();
            logger.info("MongoDB Configs Velocity plugin disabled");
        } catch (Exception e) {
            logger.warn("Error during plugin shutdown", e);
        }
    }

    private void registerCommands() {
        CommandManager cm = server.getCommandManager();
        cm.register(cm.metaBuilder("language").aliases("lang", "jezyk").build(), new LanguageCommand(languageManager));
        cm.register(cm.metaBuilder("mongoconfigs").aliases("mconfig", "mc").build(), new MongoConfigsCommand(configManager, languageManager));
        cm.register(cm.metaBuilder("mongoconfigsproxy").build(), new MongoConfigsProxyCommand(configManager, languageManager));
    }

    private MongoConfig createMongoConfig() {
        MongoConfig config = new MongoConfig();

        String connectionString = pluginConfig.getMongoConnectionString();
        if (connectionString != null) {
            logger.info("Using MongoDB connection string: {}", connectionString.contains("@") ? connectionString.replaceAll("://[^@]*@", "://***@") : connectionString);
        }

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

        config.setDebugLogging(pluginConfig.isDebugLogging());
        config.setVerboseLogging(pluginConfig.isVerboseLogging());

        config.setDefaultLanguage(pluginConfig.getDefaultLanguage());
        config.setSupportedLanguages(pluginConfig.getSupportedLanguages());
        config.setLanguageDisplayNames(pluginConfig.getLanguageDisplayNames());

        config.setPlayerLanguagesCollection(pluginConfig.getPlayerLanguagesCollection());
        config.setTypedConfigsCollection(pluginConfig.getTypedConfigsCollection());
        config.setConfigsCollection(pluginConfig.getConfigsCollection());
        return config;
    }

    private void ensureDefaults() throws IOException {
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }
        copyIfMissing("config.yml");
    }

    private void copyIfMissing(String name) throws IOException {
        Path target = dataDirectory.resolve(name);
        if (Files.exists(target)) return;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            if (in == null) return;
            Files.copy(in, target);
        }
    }
}
