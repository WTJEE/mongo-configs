package xyz.wtje.mongoconfigs.paper.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;


public class PluginConfiguration {
    
    private final FileConfiguration config;
    
    public PluginConfiguration(Plugin plugin) {
        this.config = plugin.getConfig();
    }

    public String getMongoConnectionString() {
        return config.getString("mongodb.connection-string", "mongodb://localhost:27017");
    }
    
    public String getMongoDatabase() {
        return config.getString("mongodb.database", "minecraft");
    }
    
    public String getPlayerLanguagesDatabase() {
        return config.getString("mongodb.player-languages.database", getMongoDatabase());
    }
    
    public String getPlayerLanguagesCollection() {
        return config.getString("mongodb.player-languages.collection", "player_languages");
    }
    
    public int getMongoMaxPoolSize() {
        return config.getInt("mongodb.connection-pool.max-size", 20);
    }
    
    public int getMongoMinPoolSize() {
        return config.getInt("mongodb.connection-pool.min-size", 5);
    }
    
    public long getMongoMaxIdleTime() {
        return config.getLong("mongodb.connection-pool.max-idle-time-ms", 60000);
    }
    
    public long getMongoMaxLifeTime() {
        return config.getLong("mongodb.connection-pool.max-life-time-ms", 1800000);
    }
    
    public int getMongoServerSelectionTimeout() {
        return config.getInt("mongodb.timeouts.server-selection-ms", 5000);
    }
    
    public int getMongoSocketTimeout() {
        return config.getInt("mongodb.timeouts.socket-timeout-ms", 10000);
    }
    
    public int getMongoConnectTimeout() {
        return config.getInt("mongodb.timeouts.connect-timeout-ms", 5000);
    }

    public long getCacheMaxSize() {
        return config.getLong("cache.max-size", 10000);
    }
    
    public long getCacheTtlSeconds() {
        return config.getLong("cache.ttl-seconds", 300);
    }
    
    public boolean isCacheRecordStats() {
        return config.getBoolean("cache.record-stats", true);
    }
    
    public long getCacheRefreshAfterSeconds() {
        return config.getLong("cache.refresh-after-seconds", 300);
    }

    public int getIoThreads() {
        return config.getInt("performance.io-threads", 4);
    }
    
    public int getWorkerThreads() {
        return config.getInt("performance.worker-threads", 8);
    }

    public boolean isChangeStreamsEnabled() {
        return false;
    }
    
    public int getChangeStreamResumeRetries() {
        return 3;
    }
    
    public long getChangeStreamResumeDelay() {
        return 5000;
    }

    public boolean isLogCacheStats() {
        return config.getBoolean("logging.log-cache-stats", true);
    }
    
    public long getCacheStatsInterval() {
        return config.getLong("logging.cache-stats-interval", 300);
    }

}
