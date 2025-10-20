package xyz.wtje.mongoconfigs.core.config;

import java.util.List;
import java.util.Map;

public class MongoConfig {
    private String connectionString = "mongodb://localhost:27017";
    private String database = "minecraft";
    private int maxPoolSize = 20;
    private int minPoolSize = 5;
    private long maxConnectionIdleTime = 60000; 
    private long maxConnectionLifeTime = 1800000; 
    private int serverSelectionTimeoutMs = 5000;
    private int socketTimeoutMs = 10000;
    private int connectTimeoutMs = 5000;

    private long cacheMaxSize = 0;
    private long cacheTtlSeconds = 0; 
    private long cacheRefreshAfterSeconds = 60; 
    private boolean cacheRecordStats = true;

    private boolean enableChangeStreams = true;
    private int changeStreamResumeRetries = 5;
    private long changeStreamResumeDelayMs = 1000;

    private int ioThreads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private int workerThreads = Runtime.getRuntime().availableProcessors();

    private String defaultLanguage = "en";
    private List<String> supportedLanguages = List.of("en", "pl");
    private Map<String, String> languageDisplayNames = Map.of(
        "en", "English",
        "pl", "Polski"
    );
    private String playerLanguagesCollection = "player_languages";
    private String typedConfigsCollection = "typed_configs";
    private String configsCollection = "configs";
    
    private java.util.Set<String> ignoredDatabases = java.util.Set.of();
    private java.util.Set<String> ignoredCollections = java.util.Set.of();

    private boolean debugLogging = false;
    private boolean verboseLogging = false;

    public String getConnectionString() { return connectionString; }
    public void setConnectionString(String connectionString) { this.connectionString = connectionString; }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

    public int getMinPoolSize() { return minPoolSize; }
    public void setMinPoolSize(int minPoolSize) { this.minPoolSize = minPoolSize; }

    public long getMaxConnectionIdleTime() { return maxConnectionIdleTime; }
    public void setMaxConnectionIdleTime(long maxConnectionIdleTime) { this.maxConnectionIdleTime = maxConnectionIdleTime; }

    public long getMaxConnectionLifeTime() { return maxConnectionLifeTime; }
    public void setMaxConnectionLifeTime(long maxConnectionLifeTime) { this.maxConnectionLifeTime = maxConnectionLifeTime; }

    public int getServerSelectionTimeoutMs() { return serverSelectionTimeoutMs; }
    public void setServerSelectionTimeoutMs(int serverSelectionTimeoutMs) { this.serverSelectionTimeoutMs = serverSelectionTimeoutMs; }

    public int getSocketTimeoutMs() { return socketTimeoutMs; }
    public void setSocketTimeoutMs(int socketTimeoutMs) { this.socketTimeoutMs = socketTimeoutMs; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public long getCacheMaxSize() { return cacheMaxSize; }
    public void setCacheMaxSize(long cacheMaxSize) { this.cacheMaxSize = cacheMaxSize; }

    public long getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(long cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }

    public long getCacheRefreshAfterSeconds() { return cacheRefreshAfterSeconds; }
    public void setCacheRefreshAfterSeconds(long cacheRefreshAfterSeconds) { this.cacheRefreshAfterSeconds = cacheRefreshAfterSeconds; }

    public boolean isCacheRecordStats() { return cacheRecordStats; }
    public void setCacheRecordStats(boolean cacheRecordStats) { this.cacheRecordStats = cacheRecordStats; }

    public boolean isEnableChangeStreams() { return enableChangeStreams; }
    public void setEnableChangeStreams(boolean enableChangeStreams) { this.enableChangeStreams = enableChangeStreams; }

    public int getChangeStreamResumeRetries() { return changeStreamResumeRetries; }
    public void setChangeStreamResumeRetries(int changeStreamResumeRetries) { this.changeStreamResumeRetries = changeStreamResumeRetries; }

    public long getChangeStreamResumeDelayMs() { return changeStreamResumeDelayMs; }
    public void setChangeStreamResumeDelayMs(long changeStreamResumeDelayMs) { this.changeStreamResumeDelayMs = changeStreamResumeDelayMs; }

    public int getIoThreads() { return ioThreads; }
    public void setIoThreads(int ioThreads) { this.ioThreads = ioThreads; }

    public int getWorkerThreads() { return workerThreads; }
    public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }

    public String getDefaultLanguage() { return defaultLanguage; }
    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }

    public List<String> getSupportedLanguages() { return supportedLanguages; }
    public void setSupportedLanguages(List<String> supportedLanguages) { this.supportedLanguages = supportedLanguages; }

    public Map<String, String> getLanguageDisplayNames() { return languageDisplayNames; }
    public void setLanguageDisplayNames(Map<String, String> languageDisplayNames) { this.languageDisplayNames = languageDisplayNames; }

    public String getPlayerLanguagesCollection() { return playerLanguagesCollection; }
    public void setPlayerLanguagesCollection(String playerLanguagesCollection) { this.playerLanguagesCollection = playerLanguagesCollection; }

    public String getTypedConfigsCollection() { return typedConfigsCollection; }
    public void setTypedConfigsCollection(String typedConfigsCollection) { this.typedConfigsCollection = typedConfigsCollection; }

    public String getConfigsCollection() { return configsCollection; }
    public void setConfigsCollection(String configsCollection) { this.configsCollection = configsCollection; }

    public boolean isDebugLogging() { return debugLogging; }
    public void setDebugLogging(boolean debugLogging) { this.debugLogging = debugLogging; }

    public boolean isVerboseLogging() { return verboseLogging; }
    public void setVerboseLogging(boolean verboseLogging) { this.verboseLogging = verboseLogging; }

    public java.util.Set<String> getIgnoredDatabases() { return ignoredDatabases; }
    public void setIgnoredDatabases(java.util.Set<String> ignoredDatabases) { this.ignoredDatabases = (ignoredDatabases != null ? ignoredDatabases : java.util.Set.of()); }

    public java.util.Set<String> getIgnoredCollections() { return ignoredCollections; }
    public void setIgnoredCollections(java.util.Set<String> ignoredCollections) { this.ignoredCollections = (ignoredCollections != null ? ignoredCollections : java.util.Set.of()); }
}


