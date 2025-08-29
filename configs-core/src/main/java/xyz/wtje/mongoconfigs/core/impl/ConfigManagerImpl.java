package xyz.wtje.mongoconfigs.core.impl;

import io.micrometer.core.instrument.Timer;
import xyz.wtje.mongoconfigs.api.CacheStats;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.PerformanceMetrics;
import xyz.wtje.mongoconfigs.core.cache.CacheManager;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.mongoconfigs.core.metrics.MetricsManager;
import xyz.wtje.mongoconfigs.core.model.ConfigDocument;
import xyz.wtje.mongoconfigs.core.model.LanguageDocument;
import xyz.wtje.mongoconfigs.core.mongo.MongoManager;
import xyz.wtje.mongoconfigs.core.mongo.PublisherAdapter;
import xyz.wtje.mongoconfigs.core.util.ColorProcessor;
import xyz.wtje.mongoconfigs.core.util.MessageFormatter;
import com.mongodb.client.model.Filters;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ConfigManagerImpl implements ConfigManager {
    
    private static final Logger LOGGER = Logger.getLogger(ConfigManagerImpl.class.getName());
    
    private final MongoManager mongoManager;
    private final CacheManager cacheManager;
    private final MetricsManager metricsManager;
    private final MessageFormatter messageFormatter;
    private final MongoConfig config;
    private final Executor asyncExecutor;
    private ColorProcessor colorProcessor;

    private final Set<String> knownCollections = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<String>> collectionLanguages = new ConcurrentHashMap<>();
    
    public ConfigManagerImpl(MongoConfig config) {
        this.config = config;

        this.mongoManager = new MongoManager(config);
        this.asyncExecutor = mongoManager.getExecutorService();
        this.cacheManager = new CacheManager(config);
        this.metricsManager = new MetricsManager();
        this.messageFormatter = new MessageFormatter();
        
        metricsManager.updateConnectionPoolSize(config.getMaxPoolSize());
        
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        
        LOGGER.info("ConfigManager initialized with MongoDB database: " + config.getDatabase());
    }
    
    public void initialize() {
        preWarmCache();
        logConfigurationStatus();
        
        LOGGER.info("ConfigManager fully initialized and ready");
    }
    
    public void shutdown() {
        mongoManager.close();
        LOGGER.info("ConfigManager shutdown complete");
    }
    
    public void setColorProcessor(ColorProcessor colorProcessor) {
        this.colorProcessor = colorProcessor;
        this.messageFormatter.setColorProcessor(colorProcessor);
    }

    @Override
    public <T> T getConfig(String collection, String key, T defaultValue) {
        Timer.Sample sample = metricsManager.startCacheOperation();
        
        try {
            T result = cacheManager.getConfig(collection, key, defaultValue);
            metricsManager.recordCacheHit(sample, "config");
            return result;
        } catch (Exception e) {
            metricsManager.recordCacheMiss(sample, "config");
            LOGGER.log(Level.WARNING, "Error getting config: " + collection + ":" + key, e);
            return defaultValue;
        }
    }
    
    @Override
    public <T> CompletableFuture<Optional<T>> getConfigAsync(String collection, String key, Class<T> type) {
        return CompletableFuture.supplyAsync(() -> {
            Timer.Sample sample = metricsManager.startMongoOperation();
            
            try {
                T cached = cacheManager.getConfig(collection, key, null);
                if (cached != null && type.isInstance(cached)) {
                    metricsManager.recordMongoOperation(sample, collection, "getConfig", "cache-hit");
                    return Optional.of(cached);
                }
                
                return loadConfigFromMongo(collection, key, type, sample);
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, collection, "getConfig", "error");
                LOGGER.log(Level.WARNING, "Error getting config async: " + collection + ":" + key, e);
                return Optional.empty();
            }
        }, asyncExecutor);
    }
    
    @Override
    public <T> CompletableFuture<Void> setConfig(String collection, String key, T value) {
        return CompletableFuture.runAsync(() -> {
            Timer.Sample sample = metricsManager.startMongoOperation();
            
            try {
                cacheManager.putConfig(collection, key, value);

                updateConfigInMongo(collection, key, value).join();
                
                metricsManager.recordMongoOperation(sample, collection, "setConfig", "success");
                LOGGER.fine("Updated config: " + collection + ":" + key);
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, collection, "setConfig", "error");
                LOGGER.log(Level.SEVERE, "Error setting config: " + collection + ":" + key, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    @Override
    public String getMessage(String collection, String language, String key, Object... placeholders) {
        Timer.Sample sample = metricsManager.startCacheOperation();
        
        try {
            String message = cacheManager.getMessage(collection, language, key, null);
            if (message == null) {
                message = cacheManager.getMessage(collection, config.getDefaultLanguage(), key, key);
                metricsManager.recordCacheMiss(sample, "message");
            } else {
                metricsManager.recordCacheHit(sample, "message");
            }
            
            return messageFormatter.format(message, placeholders);
        } catch (Exception e) {
            metricsManager.recordCacheMiss(sample, "message");
            LOGGER.log(Level.WARNING, "Error getting message: " + collection + ":" + language + ":" + key, e);
            return key;
        }
    }
    
    @Override
    public List<String> getMessageLore(String collection, String language, String key, Object... placeholders) {
        String message = getMessage(collection, language, key, placeholders);

        if (message.contains(",")) {
            return Arrays.asList(message.split(","));
        }
        
        return List.of(message);
    }
    
    @Override
    public CompletableFuture<String> getMessageAsync(String collection, String language, String key, Object... placeholders) {
        return CompletableFuture.supplyAsync(() -> 
                getMessage(collection, language, key, placeholders), asyncExecutor);
    }
    
    @Override
    public CompletableFuture<Void> setMessage(String collection, String language, String key, String value) {
        return CompletableFuture.runAsync(() -> {
            Timer.Sample sample = metricsManager.startMongoOperation();
            
            try {
                cacheManager.putMessage(collection, language, key, value);

                updateMessageInMongo(collection, language, key, value).join();
                
                metricsManager.recordMongoOperation(sample, collection, "setMessage", "success");
                LOGGER.fine("Updated message: " + collection + ":" + language + ":" + key);
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, collection, "setMessage", "error");
                LOGGER.log(Level.SEVERE, "Error setting message: " + collection + ":" + language + ":" + key, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> createCollection(String collection, Set<String> languages) {
        return CompletableFuture.runAsync(() -> {
            Timer.Sample sample = metricsManager.startMongoOperation();
            
            try {
                boolean exists = mongoManager.collectionExists(collection).join();
                if (exists) {
                    LOGGER.info("Collection already exists: " + collection + ", skipping creation");
                    knownCollections.add(collection);
                    collectionLanguages.put(collection, new HashSet<>(languages));
                    metricsManager.recordMongoOperation(sample, collection, "createCollection", "already-exists");
                    return;
                }

                mongoManager.createCollection(collection).join();

                ConfigDocument existingConfigDoc = mongoManager.getConfig(collection).join();
                if (existingConfigDoc == null) {
                    ConfigDocument configDoc = new ConfigDocument("config", new HashMap<>());
                    mongoManager.saveConfig(collection, configDoc).join();
                    LOGGER.info("Created config document for collection: " + collection);
                } else {
                    LOGGER.info("Config document already exists for collection: " + collection);
                }

                for (String language : languages) {
                    LanguageDocument existingLangDoc = mongoManager.getLanguage(collection, language).join();
                    if (existingLangDoc == null) {
                        LanguageDocument langDoc = new LanguageDocument(language, new HashMap<>());
                        mongoManager.saveLanguage(collection, langDoc).join();
                        LOGGER.info("Created language document: " + collection + ":" + language);
                    } else {
                        LOGGER.info("Language document already exists: " + collection + ":" + language);
                    }
                }

                knownCollections.add(collection);
                collectionLanguages.put(collection, new HashSet<>(languages));
                metricsManager.recordMongoOperation(sample, collection, "createCollection", "success");
                LOGGER.info("Collection setup completed: " + collection + " with languages: " + languages);
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, collection, "createCollection", "error");
                LOGGER.log(Level.SEVERE, "Error creating collection: " + collection, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    
    @Override
    public CompletableFuture<Void> copyLanguage(String collection, String sourceLanguage, String targetLanguage) {
        return CompletableFuture.runAsync(() -> {
            Timer.Sample sample = metricsManager.startMongoOperation();
            
            try {
                LanguageDocument sourceDoc = mongoManager.getLanguage(collection, sourceLanguage).join();
                if (sourceDoc == null) {
                    throw new IllegalArgumentException("Source language not found: " + sourceLanguage);
                }

                LanguageDocument targetDoc = new LanguageDocument(targetLanguage, 
                        new HashMap<>(sourceDoc.getData()));
                mongoManager.saveLanguage(collection, targetDoc).join();

                cacheManager.putMessageData(collection, targetLanguage, targetDoc.getData());

                collectionLanguages.computeIfAbsent(collection, k -> new HashSet<>()).add(targetLanguage);
                
                metricsManager.recordMongoOperation(sample, collection, "copyLanguage", "success");
                LOGGER.info("Copied language " + sourceLanguage + " to " + targetLanguage + " in collection: " + collection);
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, collection, "copyLanguage", "error");
                LOGGER.log(Level.SEVERE, "Error copying language: " + sourceLanguage + " to " + targetLanguage, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    
    @Override
    public CompletableFuture<Set<String>> getCollections() {
        return CompletableFuture.supplyAsync(() -> new HashSet<>(knownCollections), asyncExecutor);
    }
    
    @Override
    public Set<String> getSupportedLanguages(String collection) {
        return collectionLanguages.getOrDefault(collection, Set.of());
    }
    
    @Override
    public boolean collectionExists(String collection) {
        return knownCollections.contains(collection) || cacheManager.hasCollection(collection);
    }
    
    @Override
    public CompletableFuture<Void> reloadCollection(String collection) {
        return CompletableFuture.runAsync(() -> {
            Timer.Sample sample = metricsManager.startMongoOperation();
            
            try {
                cacheManager.invalidateCollection(collection);

                CompletableFuture<ConfigDocument> configFuture = mongoManager.getConfig(collection);

                Set<String> languages = collectionLanguages.getOrDefault(collection, Set.of());
                List<CompletableFuture<LanguageDocument>> languageFutures = languages.stream()
                        .map(lang -> mongoManager.getLanguage(collection, lang))
                        .toList();

                ConfigDocument configDoc = configFuture.join();
                if (configDoc != null && configDoc.getData() != null) {
                    cacheManager.putConfigData(collection, configDoc.getData());
                }
                
                for (CompletableFuture<LanguageDocument> future : languageFutures) {
                    LanguageDocument langDoc = future.join();
                    if (langDoc != null && langDoc.getData() != null) {
                        cacheManager.putMessageData(collection, langDoc.getLang(), langDoc.getData());
                    }
                }
                
                metricsManager.recordMongoOperation(sample, collection, "reloadCollection", "success");
                LOGGER.info("Reloaded collection: " + collection);
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, collection, "reloadCollection", "error");
                LOGGER.log(Level.SEVERE, "Error reloading collection: " + collection, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    
    @Override
    public CompletableFuture<Void> reloadAll() {
        return CompletableFuture.runAsync(() -> {
            cacheManager.invalidateAll();
            
            List<CompletableFuture<Void>> reloadFutures = knownCollections.parallelStream()
                    .map(this::reloadCollection)
                    .toList();
            
            CompletableFuture.allOf(reloadFutures.toArray(new CompletableFuture[0])).join();
            LOGGER.info("Reloaded all collections");
        }, asyncExecutor);
    }


    
    @Override
    public CacheStats getCacheStats() {
        return new CacheStatsImpl(cacheManager);
    }
    
    @Override
    public PerformanceMetrics getMetrics() {
        return metricsManager;
    }
    
    private <T> Optional<T> loadConfigFromMongo(String collection, String key, Class<T> type, Timer.Sample sample) {
        try {
            ConfigDocument configDoc = mongoManager.getConfig(collection).join();
            if (configDoc != null && configDoc.getData() != null) {
                cacheManager.putConfigData(collection, configDoc.getData());
                
                Object value = configDoc.getData().get(key);
                if (value != null && type.isInstance(value)) {
                    metricsManager.recordMongoOperation(sample, collection, "getConfig", "success");
                    return Optional.of(type.cast(value));
                }
            }
            
            metricsManager.recordMongoOperation(sample, collection, "getConfig", "not-found");
            return Optional.empty();
        } catch (Exception e) {
            metricsManager.recordMongoOperation(sample, collection, "getConfig", "error");
            throw e;
        }
    }
    
    private <T> CompletableFuture<Void> updateConfigInMongo(String collection, String key, T value) {
        return mongoManager.getConfig(collection)
                .thenCompose(configDoc -> {
                    if (configDoc == null) {
                        configDoc = new ConfigDocument("config", new HashMap<>());
                        LOGGER.fine("Creating new config document for collection: " + collection);
                    }
                    
                    Object currentValue = configDoc.getData().get(key);
                    if (java.util.Objects.equals(currentValue, value)) {
                        LOGGER.fine("Config value unchanged for " + collection + ":" + key + ", skipping update");
                        return CompletableFuture.completedFuture(null);
                    }
                    
                    configDoc.getData().put(key, value);
                    LOGGER.fine("Updating config value for " + collection + ":" + key);
                    return mongoManager.saveConfig(collection, configDoc);
                });
    }
    
    private CompletableFuture<Void> updateMessageInMongo(String collection, String language, String key, String value) {
        return mongoManager.getLanguage(collection, language)
                .thenCompose(langDoc -> {
                    if (langDoc == null) {
                        langDoc = new LanguageDocument(language, new HashMap<>());
                        LOGGER.fine("Creating new language document for " + collection + ":" + language);
                    }
                    
                    Map<String, Object> data = langDoc.getData();
                    Object currentValue = getNestedValue(data, key);
                    if (java.util.Objects.equals(currentValue, value)) {
                        LOGGER.fine("Message value unchanged for " + collection + ":" + language + ":" + key + ", skipping update");
                        return CompletableFuture.completedFuture(null);
                    }
                    
                    setNestedValue(data, key, value);
                    LOGGER.fine("Updating message value for " + collection + ":" + language + ":" + key);
                    return mongoManager.saveLanguage(collection, langDoc);
                });
    }
    
    private Object getNestedValue(Map<String, Object> data, String key) {
        if (!key.contains(".")) {
            return data.get(key);
        }
        
        String[] parts = key.split("\\.", 2);
        String currentKey = parts[0];
        String remainingKey = parts[1];
        
        Object nested = data.get(currentKey);
        if (nested instanceof Map) {
            return getNestedValue((Map<String, Object>) nested, remainingKey);
        }
        
        return null;
    }
    
    private void setNestedValue(Map<String, Object> data, String key, Object value) {
        if (!key.contains(".")) {
            data.put(key, value);
            return;
        }
        
        String[] parts = key.split("\\.", 2);
        String currentKey = parts[0];
        String remainingKey = parts[1];
        
        Map<String, Object> nested = (Map<String, Object>) data.computeIfAbsent(currentKey, k -> new HashMap<>());
        setNestedValue(nested, remainingKey, value);
    }
    
    private void preWarmCache() {
        CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("Pre-warming cache - discovering existing collections...");
                
                Set<String> existingCollections = PublisherAdapter.toCompletableFutureList(
                    mongoManager.getDatabase().listCollectionNames()
                ).join()
                .stream()
                .collect(java.util.stream.Collectors.toSet());
                
                LOGGER.info("Found existing collections: " + existingCollections);
                
                for (String collection : existingCollections) {
                    if (!collection.equals(config.getPlayerLanguagesCollection())) {
                        loadCollectionIntoCache(collection).join();
                    }
                }
                
                knownCollections.addAll(existingCollections);
                
                LOGGER.info("Pre-warming cache completed for " + existingCollections.size() + " collections");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error pre-warming cache", e);
            }
        }, asyncExecutor);
    }
    
    private CompletableFuture<Void> loadCollectionIntoCache(String collection) {
        return CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("Loading collection into cache: " + collection);
                
                ConfigDocument configDoc = mongoManager.getConfig(collection).join();
                if (configDoc != null && configDoc.getData() != null) {
                    cacheManager.putConfigData(collection, configDoc.getData());
                    LOGGER.fine("Loaded config data for collection: " + collection);
                }
                
                Set<String> languages = PublisherAdapter.toCompletableFutureList(
                    mongoManager.getCollection(collection)
                        .find(Filters.exists("lang"))
                ).join()
                .stream()
                .map(doc -> doc.getString("lang"))
                .filter(lang -> lang != null)
                .collect(java.util.stream.Collectors.toSet());
                
                LOGGER.info("Found languages in collection " + collection + ": " + languages);
                
                for (String language : languages) {
                    LanguageDocument langDoc = mongoManager.getLanguage(collection, language).join();
                    if (langDoc != null && langDoc.getData() != null) {
                        cacheManager.putMessageData(collection, language, langDoc.getData());
                        LOGGER.fine("Loaded language data for " + collection + ":" + language);
                    }
                }
                
                collectionLanguages.put(collection, languages);
                
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error loading collection into cache: " + collection, e);
            }
        }, asyncExecutor);
    }
    
    @Override
    public void invalidateCache(String collection) {
        cacheManager.invalidateCollection(collection);
        metricsManager.recordCacheOperation("invalidate_collection", "success");
        LOGGER.info("Cache invalidated for collection: " + collection);
    }
    
    @Override
    public void invalidateCache() {
        cacheManager.invalidateAll();
        metricsManager.recordCacheOperation("invalidate_all", "success");
        LOGGER.info("All cache invalidated");
    }
    
    @Override
    public String getPlainMessage(String collection, String language, String key, Object... placeholders) {
        Timer.Sample sample = metricsManager.startCacheOperation();
        
        try {
            String message = cacheManager.getMessage(collection, language, key, null);
            if (message == null) {
                message = cacheManager.getMessage(collection, config.getDefaultLanguage(), key, key);
                metricsManager.recordCacheMiss(sample, "message");
            } else {
                metricsManager.recordCacheHit(sample, "message");
            }
            
            String formatted = messageFormatter.formatPlain(message, placeholders);
            return colorProcessor != null ? colorProcessor.stripColors(formatted) : formatted;
        } catch (Exception e) {
            metricsManager.recordCacheMiss(sample, "message");
            LOGGER.log(Level.WARNING, "Error getting plain message: " + collection + ":" + language + ":" + key, e);
            return key;
        }
    }
    
    @Override
    public Object getColorCacheStats() {
        return colorProcessor != null ? colorProcessor.getCacheStats() : "No color processor available";
    }
    
    public MongoManager getMongoManager() {
        return mongoManager;
    }
    
    public void logConfigurationStatus() {
        LOGGER.info("=== MongoDB Configs Configuration Status ===");
        LOGGER.info("Database: " + config.getDatabase());
        LOGGER.info("Connection Pool: max=" + config.getMaxPoolSize() + ", min=" + config.getMinPoolSize());
        LOGGER.info("Connection Timeouts: connect=" + config.getConnectTimeoutMs() + "ms, socket=" + config.getSocketTimeoutMs() + "ms, selection=" + config.getServerSelectionTimeoutMs() + "ms");
        LOGGER.info("Cache: max-size=" + config.getCacheMaxSize() + ", ttl=" + config.getCacheTtlSeconds() + "s, refresh-after=" + config.getCacheRefreshAfterSeconds() + "s, stats=" + config.isCacheRecordStats());
        LOGGER.info("Performance: io-threads=" + config.getIoThreads() + ", worker-threads=" + config.getWorkerThreads());
        LOGGER.info("Cache estimated size: " + cacheManager.getEstimatedSize());
        LOGGER.info("Known collections: " + knownCollections.size());
        LOGGER.info("=== All configuration values are being used ===");
    }
}