package xyz.wtje.mongoconfigs.core.impl;
import io.micrometer.core.instrument.Timer;
import xyz.wtje.mongoconfigs.api.CacheStats;
import xyz.wtje.mongoconfigs.api.CollectionSetupData;
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
        this.cacheManager = new CacheManager();
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
                if (config.isVerboseLogging()) {
                    LOGGER.info("Updated config: " + collection + ":" + key);
                }
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
                if (config.isVerboseLogging()) {
                    LOGGER.info("Updated message: " + collection + ":" + language + ":" + key);
                }
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, collection, "setMessage", "error");
                LOGGER.log(Level.SEVERE, "Error setting message: " + collection + ":" + language + ":" + key, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    @Override
    public <T> CompletableFuture<Void> setConfigBatch(String collection, Map<String, T> configValues) {
        return CompletableFuture.runAsync(() -> {
            Timer.Sample sample = metricsManager.startMongoOperation();
            try {
                for (Map.Entry<String, T> entry : configValues.entrySet()) {
                    cacheManager.putConfig(collection, entry.getKey(), entry.getValue());
                }
                updateConfigBatchInMongo(collection, configValues).join();
                metricsManager.recordMongoOperation(sample, collection, "setConfigBatch", "success");
                if (config.isVerboseLogging()) {
                    LOGGER.info("Updated config batch for collection: " + collection + " (" + configValues.size() + " items)");
                }
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, collection, "setConfigBatch", "error");
                LOGGER.log(Level.SEVERE, "Error setting config batch for collection: " + collection, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    @Override
    public CompletableFuture<Void> setMessageBatch(String collection, String language, Map<String, String> messages) {
        return CompletableFuture.runAsync(() -> {
            Timer.Sample sample = metricsManager.startMongoOperation();
            try {
                for (Map.Entry<String, String> entry : messages.entrySet()) {
                    cacheManager.putMessage(collection, language, entry.getKey(), entry.getValue());
                }
                updateMessageBatchInMongo(collection, language, messages).join();
                metricsManager.recordMongoOperation(sample, collection, "setMessageBatch", "success");
                if (config.isVerboseLogging()) {
                    LOGGER.info("Updated message batch: " + collection + ":" + language + " (" + messages.size() + " items)");
                }
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, collection, "setMessageBatch", "error");
                LOGGER.log(Level.SEVERE, "Error setting message batch: " + collection + ":" + language, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    @Override
    public CompletableFuture<Void> setMessageBatchMultiLang(String collection, Map<String, Map<String, String>> languageMessages) {
        return CompletableFuture.runAsync(() -> {
            Timer.Sample sample = metricsManager.startMongoOperation();
            try {
                for (Map.Entry<String, Map<String, String>> langEntry : languageMessages.entrySet()) {
                    String language = langEntry.getKey();
                    for (Map.Entry<String, String> msgEntry : langEntry.getValue().entrySet()) {
                        cacheManager.putMessage(collection, language, msgEntry.getKey(), msgEntry.getValue());
                    }
                }
                List<CompletableFuture<Void>> updateFutures = languageMessages.entrySet().stream()
                        .map(entry -> updateMessageBatchInMongo(collection, entry.getKey(), entry.getValue()))
                        .toList();
                CompletableFuture.allOf(updateFutures.toArray(new CompletableFuture[0])).join();
                int totalMessages = languageMessages.values().stream().mapToInt(Map::size).sum();
                metricsManager.recordMongoOperation(sample, collection, "setMessageBatchMultiLang", "success");
                if (config.isVerboseLogging()) {
                    LOGGER.info("Updated message batch for collection: " + collection + " (" + 
                               languageMessages.size() + " languages, " + totalMessages + " total messages)");
                }
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, collection, "setMessageBatchMultiLang", "error");
                LOGGER.log(Level.SEVERE, "Error setting message batch multi-lang for collection: " + collection, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    @Override
    public CompletableFuture<Void> createCollectionsBatch(Map<String, CollectionSetupData> collectionsData) {
        return createCollectionsBatch(collectionsData, 3);
    }
    @Override
    public CompletableFuture<Void> createCollectionsBatch(Map<String, CollectionSetupData> collectionsData, int maxConcurrency) {
        return CompletableFuture.runAsync(() -> {
            Timer.Sample sample = metricsManager.startMongoOperation();
            try {
                if (config.isDebugLogging()) {
                    LOGGER.info("Starting batch creation of " + collectionsData.size() + " collections with max concurrency: " + maxConcurrency);
                }
                List<String> collectionNames = new ArrayList<>(collectionsData.keySet());
                List<CompletableFuture<Void>> allFutures = new ArrayList<>();
                for (int i = 0; i < collectionNames.size(); i += maxConcurrency) {
                    int endIndex = Math.min(i + maxConcurrency, collectionNames.size());
                    List<String> batchCollections = collectionNames.subList(i, endIndex);
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Processing batch " + ((i / maxConcurrency) + 1) + ": collections " + batchCollections);
                    }
                    List<CompletableFuture<Void>> batchFutures = batchCollections.stream()
                            .map(collectionName -> {
                                CollectionSetupData setupData = collectionsData.get(collectionName);
                                return createSingleCollectionWithData(collectionName, setupData);
                            })
                            .toList();
                    CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
                    allFutures.addAll(batchFutures);
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Completed batch " + ((i / maxConcurrency) + 1) + ", processed " + batchCollections.size() + " collections");
                    }
                    if (endIndex < collectionNames.size()) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Batch processing interrupted", e);
                        }
                    }
                }
                metricsManager.recordMongoOperation(sample, "batch", "createCollectionsBatch", "success");
                if (config.isDebugLogging()) {
                    LOGGER.info("Successfully completed batch creation of " + collectionsData.size() + " collections");
                }
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, "batch", "createCollectionsBatch", "error");
                LOGGER.log(Level.SEVERE, "Error in batch collection creation", e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    private CompletableFuture<Void> createSingleCollectionWithData(String collectionName, CollectionSetupData setupData) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (config.isDebugLogging()) {
                    LOGGER.info("Setting up collection: " + collectionName + " with " + 
                               setupData.getLanguages().size() + " languages");
                }
                createCollection(collectionName, setupData.getLanguages()).join();
                if (setupData.getConfigValues() != null && !setupData.getConfigValues().isEmpty()) {
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Setting " + setupData.getConfigValues().size() + " config values for: " + collectionName);
                    }
                    setConfigBatch(collectionName, setupData.getConfigValues()).join();
                }
                if (setupData.getLanguageMessages() != null && !setupData.getLanguageMessages().isEmpty()) {
                    int totalMessages = setupData.getLanguageMessages().values().stream().mapToInt(Map::size).sum();
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Setting " + totalMessages + " messages for: " + collectionName);
                    }
                    setMessageBatchMultiLang(collectionName, setupData.getLanguageMessages()).join();
                }
                if (config.isDebugLogging()) {
                    LOGGER.info("Successfully completed setup for collection: " + collectionName);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error setting up collection: " + collectionName, e);
                throw new RuntimeException("Failed to setup collection: " + collectionName, e);
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
                    if (config.isDebugLogging()) {
                        LOGGER.info("Collection already exists: " + collection + ", updating supported languages");
                    }
                    knownCollections.add(collection);
                    collectionLanguages.put(collection, new HashSet<>(languages));
                    
                    ConfigDocument existingConfigDoc = mongoManager.getConfig(collection).join();
                    if (existingConfigDoc != null && existingConfigDoc.getData() != null) {
                        existingConfigDoc.getData().put("_system.supported_languages", new ArrayList<>(languages));
                        mongoManager.saveConfig(collection, existingConfigDoc).join();
                        if (config.isDebugLogging()) {
                            LOGGER.info("Updated supported languages in config for collection: " + collection + " -> " + languages);
                        }
                    }
                    
                    metricsManager.recordMongoOperation(sample, collection, "createCollection", "already-exists");
                    return;
                }
                mongoManager.createCollection(collection).join();
                ConfigDocument existingConfigDoc = mongoManager.getConfig(collection).join();
                Map<String, Object> configData = new HashMap<>();
                if (existingConfigDoc != null && existingConfigDoc.getData() != null) {
                    configData.putAll(existingConfigDoc.getData());
                }
                
                configData.put("_system.supported_languages", new ArrayList<>(languages));
                ConfigDocument configDoc = new ConfigDocument("config", configData);
                mongoManager.saveConfig(collection, configDoc).join();
                if (config.isDebugLogging()) {
                    LOGGER.info("Created config document for collection: " + collection + " with supported languages: " + languages);
                }
                
                for (String language : languages) {
                    LanguageDocument existingLangDoc = mongoManager.getLanguage(collection, language).join();
                    if (existingLangDoc == null) {
                        LanguageDocument langDoc = new LanguageDocument(language, new HashMap<>());
                        mongoManager.saveLanguage(collection, langDoc).join();
                        if (config.isDebugLogging()) {
                            LOGGER.info("Created language document: " + collection + ":" + language);
                        }
                    } else {
                        if (config.isDebugLogging()) {
                            LOGGER.info("Language document already exists: " + collection + ":" + language);
                        }
                    }
                }
                knownCollections.add(collection);
                collectionLanguages.put(collection, new HashSet<>(languages));
                metricsManager.recordMongoOperation(sample, collection, "createCollection", "success");
                if (config.isDebugLogging()) {
                    LOGGER.info("Collection setup completed: " + collection + " with languages: " + languages);
                }
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
                if (config.isDebugLogging()) {
                    LOGGER.info("Copied language " + sourceLanguage + " to " + targetLanguage + " in collection: " + collection);
                }
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, collection, "copyLanguage", "error");
                LOGGER.log(Level.SEVERE, "Error copying language: " + sourceLanguage + " to " + targetLanguage, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    @Override
    public CompletableFuture<Set<String>> getCollections() {
        return CompletableFuture.supplyAsync(() -> {
            Timer.Sample sample = metricsManager.startMongoOperation();
            try {
                Set<String> mongoCollections = mongoManager.getMongoCollections();
                
                Set<String> allCollections = new HashSet<>(knownCollections);
                allCollections.addAll(mongoCollections);
                
                knownCollections.addAll(mongoCollections);
                
                if (config.isDebugLogging()) {
                    LOGGER.info("Found " + allCollections.size() + " total collections (MongoDB: " + 
                               mongoCollections.size() + ", Known: " + knownCollections.size() + ")");
                }
                
                metricsManager.recordMongoOperation(sample, "ALL", "getCollections", "success");
                return allCollections;
                
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, "ALL", "getCollections", "error");
                if (config.isVerboseLogging()) {
                    LOGGER.log(Level.WARNING, "Error getting MongoDB collections, returning known collections", e);
                }
                return new HashSet<>(knownCollections);
            }
        }, asyncExecutor);
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
                if (config.isDebugLogging()) {
                    LOGGER.info("Reloading collection: " + collection);
                }
                cacheManager.invalidateCollection(collection);
                
                CompletableFuture<ConfigDocument> configFuture = mongoManager.getConfig(collection);
                ConfigDocument configDoc = null;
                try {
                    configDoc = configFuture.join();
                    if (configDoc == null) {
                        if (config.isDebugLogging()) {
                            LOGGER.info("Config document missing for collection: " + collection + ", creating new one");
                        }
                        ConfigDocument newConfigDoc = new ConfigDocument("config", new HashMap<>());
                        mongoManager.saveConfig(collection, newConfigDoc).join();
                        configDoc = newConfigDoc;
                    }
                } catch (Exception e) {
                    if (config.isVerboseLogging()) {
                        LOGGER.log(Level.WARNING, "Failed to load config for collection: " + collection, e);
                    }
                }
                
                Set<String> expectedLanguages = Set.of();
                if (configDoc != null && configDoc.getData() != null) {
                    Object supportedLanguagesObj = configDoc.getData().get("_system.supported_languages");
                    if (supportedLanguagesObj instanceof List) {
                        expectedLanguages = new HashSet<>((List<String>) supportedLanguagesObj);
                        collectionLanguages.put(collection, expectedLanguages);
                        if (config.isDebugLogging()) {
                            LOGGER.info("Found supported languages in config for " + collection + ": " + expectedLanguages);
                        }
                    } else {
                        expectedLanguages = collectionLanguages.getOrDefault(collection, Set.of());
                        if (config.isDebugLogging()) {
                            LOGGER.info("No supported languages in config, using known languages for " + collection + ": " + expectedLanguages);
                        }
                    }
                }
                
                if (!expectedLanguages.isEmpty()) {
                    if (config.isDebugLogging()) {
                        LOGGER.info("Ensuring language documents exist for collection: " + collection + " -> " + expectedLanguages);
                    }
                    ensureLanguageDocumentsExist(collection, expectedLanguages).join();
                } else {
                    if (config.isDebugLogging()) {
                        LOGGER.info("No expected languages found for collection: " + collection + ", skipping document check");
                    }
                }
                
                List<CompletableFuture<LanguageDocument>> languageFutures = expectedLanguages.stream()
                        .map(lang -> mongoManager.getLanguage(collection, lang))
                        .toList();
                
                if (configDoc != null && configDoc.getData() != null) {
                    cacheManager.putConfigData(collection, configDoc.getData());
                    if (config.isDebugLogging()) {
                        LOGGER.info("Successfully cached config for collection: " + collection);
                    }
                } else {
                    if (config.isVerboseLogging()) {
                        LOGGER.warning("No config data found for collection: " + collection);
                    }
                }
                
                int loadedLanguages = 0;
                for (CompletableFuture<LanguageDocument> future : languageFutures) {
                    try {
                        LanguageDocument langDoc = future.join();
                        if (langDoc != null && langDoc.getData() != null) {
                            cacheManager.putMessageData(collection, langDoc.getLang(), langDoc.getData());
                            loadedLanguages++;
                        }
                    } catch (Exception e) {
                        if (config.isVerboseLogging()) {
                            LOGGER.log(Level.WARNING, "Failed to load language data for collection: " + collection, e);
                        }
                    }
                }
                
                if (config.isDebugLogging()) {
                    LOGGER.info("Successfully reloaded collection " + collection + " - Config: " + 
                               (configDoc != null ? "loaded" : "missing") + ", Languages: " + 
                               loadedLanguages + "/" + expectedLanguages.size());
                }
                metricsManager.recordMongoOperation(sample, collection, "reloadCollection", "success");
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
            if (config.isDebugLogging()) {
                LOGGER.info("Starting reloadAll() - clearing cache first...");
            }
            cacheManager.invalidateAll();

            Set<String> allCollections;
            try {
                allCollections = mongoManager.getMongoCollections();
                if (config.isDebugLogging()) {
                    LOGGER.info("Found " + allCollections.size() + " collections directly from MongoDB: " + allCollections);
                }

                if (allCollections.isEmpty()) {
                    allCollections = new HashSet<>(knownCollections);
                    if (config.isDebugLogging()) {
                        LOGGER.info("Using known collections: " + allCollections);
                    }
                } else {
                    knownCollections.addAll(allCollections);
                }

            } catch (Exception e) {
                if (config.isVerboseLogging()) {
                    LOGGER.log(Level.SEVERE, "Error getting collections from MongoDB", e);
                }
                allCollections = new HashSet<>(knownCollections);
                if (config.isDebugLogging()) {
                    LOGGER.info("Fallback to known collections: " + allCollections);
                }
            }

            if (allCollections.isEmpty()) {
                LOGGER.warning("No collections found to reload!");
                return;
            }

            if (config.isDebugLogging()) {
                LOGGER.info("Reloading " + allCollections.size() + " collections...");
            }
            
            if (config.isDebugLogging()) {
                LOGGER.info("Phase 1: Loading config documents and checking for missing language documents...");
            }
            for (String collection : allCollections) {
                try {
                    ConfigDocument configDoc = mongoManager.getConfig(collection).join();
                    Set<String> expectedLanguages = Set.of();
                    
                    if (configDoc != null && configDoc.getData() != null) {
                        Object supportedLanguagesObj = configDoc.getData().get("_system.supported_languages");
                        if (supportedLanguagesObj instanceof List) {
                            expectedLanguages = new HashSet<>((List<String>) supportedLanguagesObj);
                            collectionLanguages.put(collection, expectedLanguages);
                            if (config.isVerboseLogging()) {
                                LOGGER.info("Found supported languages in config for " + collection + ": " + expectedLanguages);
                            }
                        }
                    }
                    
                    if (expectedLanguages.isEmpty()) {
                        expectedLanguages = collectionLanguages.getOrDefault(collection, Set.of());
                        if (config.isVerboseLogging() && !expectedLanguages.isEmpty()) {
                            LOGGER.info("Using known languages for collection: " + collection + " -> " + expectedLanguages);
                        }
                    }
                    
                    if (!expectedLanguages.isEmpty()) {
                        if (config.isVerboseLogging()) {
                            LOGGER.info("Ensuring language documents exist for collection: " + collection + " -> " + expectedLanguages);
                        }
                        ensureLanguageDocumentsExist(collection, expectedLanguages).join();
                    } else if (config.isVerboseLogging()) {
                        LOGGER.info("No expected languages found for collection: " + collection + ", skipping document check");
                    }
                } catch (Exception e) {
                    if (config.isVerboseLogging()) {
                        LOGGER.log(Level.SEVERE, "Error checking/creating language documents for collection: " + collection, e);
                    }
                }
            }
            
            if (config.isDebugLogging()) {
                LOGGER.info("Phase 2: Reloading all collection data...");
            }
            List<CompletableFuture<Void>> reloadFutures = allCollections.stream()
                    .map(collection -> {
                        try {
                            if (config.isVerboseLogging()) {
                                LOGGER.info("Reloading collection: " + collection);
                            }
                            return reloadCollection(collection);
                        } catch (Exception e) {
                            if (config.isVerboseLogging()) {
                                LOGGER.log(Level.SEVERE, "Error queuing reload for collection: " + collection, e);
                            }
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                    })
                    .toList();

            try {
                CompletableFuture.allOf(reloadFutures.toArray(new CompletableFuture[0])).join();
                LOGGER.info("Reloaded all " + allCollections.size() + " collections successfully!");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during reload operations", e);
                throw e;
            }
        }, asyncExecutor);
    }
    @Override
    public CompletableFuture<Void> reloadCollectionsBatch(Set<String> collections) {
        return reloadCollectionsBatch(collections, 3);
    }
    @Override
    public CompletableFuture<Void> reloadCollectionsBatch(Set<String> collections, int maxConcurrency) {
        return CompletableFuture.runAsync(() -> {
            Timer.Sample sample = metricsManager.startMongoOperation();
            try {
                if (config.isDebugLogging()) {
                    LOGGER.info("Starting batch reload of " + collections.size() + " collections with max concurrency: " + maxConcurrency);
                }
                List<String> collectionNames = new ArrayList<>(collections);
                for (int i = 0; i < collectionNames.size(); i += maxConcurrency) {
                    int endIndex = Math.min(i + maxConcurrency, collectionNames.size());
                    List<String> batchCollections = collectionNames.subList(i, endIndex);
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Reloading batch " + ((i / maxConcurrency) + 1) + ": " + batchCollections);
                    }
                    List<CompletableFuture<Void>> batchFutures = batchCollections.stream()
                            .map(this::reloadCollection)
                            .toList();
                    CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Completed reload batch " + ((i / maxConcurrency) + 1));
                    }
                    if (endIndex < collectionNames.size()) {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Batch reload interrupted", e);
                        }
                    }
                }
                metricsManager.recordMongoOperation(sample, "batch", "reloadCollectionsBatch", "success");
                if (config.isDebugLogging()) {
                    LOGGER.info("Successfully completed batch reload of " + collections.size() + " collections");
                }
            } catch (Exception e) {
                metricsManager.recordMongoOperation(sample, "batch", "reloadCollectionsBatch", "error");
                LOGGER.log(Level.SEVERE, "Error in batch collection reload", e);
                throw new RuntimeException(e);
            }
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
                        if (config.isVerboseLogging()) {
                            LOGGER.info("Creating new config document for collection: " + collection);
                        }
                    }
                    Object currentValue = configDoc.getData().get(key);
                    if (java.util.Objects.equals(currentValue, value)) {
                        if (config.isVerboseLogging()) {
                            LOGGER.info("Config value unchanged for " + collection + ":" + key + ", skipping update");
                        }
                        return CompletableFuture.completedFuture(null);
                    }
                    configDoc.getData().put(key, value);
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Updating config value for " + collection + ":" + key);
                    }
                    return mongoManager.saveConfig(collection, configDoc);
                });
    }
    private CompletableFuture<Void> updateMessageInMongo(String collection, String language, String key, String value) {
        return mongoManager.getLanguage(collection, language)
                .thenCompose(langDoc -> {
                    if (langDoc == null) {
                        langDoc = new LanguageDocument(language, new HashMap<>());
                        if (config.isVerboseLogging()) {
                            LOGGER.info("Creating new language document for " + collection + ":" + language);
                        }
                    }
                    Map<String, Object> data = langDoc.getData();
                    Object currentValue = getNestedValue(data, key);
                    if (java.util.Objects.equals(currentValue, value)) {
                        if (config.isVerboseLogging()) {
                            LOGGER.info("Message value unchanged for " + collection + ":" + language + ":" + key + ", skipping update");
                        }
                        return CompletableFuture.completedFuture(null);
                    }
                    setNestedValue(data, key, value);
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Updating message value for " + collection + ":" + language + ":" + key);
                    }
                    return mongoManager.saveLanguage(collection, langDoc);
                });
    }
    private <T> CompletableFuture<Void> updateConfigBatchInMongo(String collection, Map<String, T> configValues) {
        return mongoManager.getConfig(collection)
                .thenCompose(configDoc -> {
                    if (configDoc == null) {
                        configDoc = new ConfigDocument("config", new HashMap<>());
                        if (config.isVerboseLogging()) {
                            LOGGER.info("Creating new config document for collection: " + collection);
                        }
                    }
                    Map<String, Object> data = configDoc.getData();
                    boolean hasChanges = false;
                    for (Map.Entry<String, T> entry : configValues.entrySet()) {
                        Object currentValue = data.get(entry.getKey());
                        if (!java.util.Objects.equals(currentValue, entry.getValue())) {
                            data.put(entry.getKey(), entry.getValue());
                            hasChanges = true;
                        }
                    }
                    if (!hasChanges) {
                        if (config.isVerboseLogging()) {
                            LOGGER.info("No config changes detected for collection: " + collection + ", skipping update");
                        }
                        return CompletableFuture.completedFuture(null);
                    }
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Batch updating config values for collection: " + collection + " (" + configValues.size() + " items)");
                    }
                    return mongoManager.saveConfig(collection, configDoc);
                });
    }
    private CompletableFuture<Void> updateMessageBatchInMongo(String collection, String language, Map<String, String> messages) {
        return mongoManager.getLanguage(collection, language)
                .thenCompose(langDoc -> {
                    if (langDoc == null) {
                        langDoc = new LanguageDocument(language, new HashMap<>());
                        if (config.isVerboseLogging()) {
                            LOGGER.info("Creating new language document for " + collection + ":" + language);
                        }
                    }
                    Map<String, Object> data = langDoc.getData();
                    boolean hasChanges = false;
                    for (Map.Entry<String, String> entry : messages.entrySet()) {
                        Object currentValue = getNestedValue(data, entry.getKey());
                        if (!java.util.Objects.equals(currentValue, entry.getValue())) {
                            setNestedValue(data, entry.getKey(), entry.getValue());
                            hasChanges = true;
                        }
                    }
                    if (!hasChanges) {
                        if (config.isVerboseLogging()) {
                            LOGGER.info("No message changes detected for " + collection + ":" + language + ", skipping update");
                        }
                        return CompletableFuture.completedFuture(null);
                    }
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Batch updating message values for " + collection + ":" + language + " (" + messages.size() + " items)");
                    }
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
                if (config.isDebugLogging()) {
                    LOGGER.info("Pre-warming cache - discovering existing collections...");
                }
                Set<String> existingCollections = PublisherAdapter.toCompletableFutureList(
                    mongoManager.getDatabase().listCollectionNames()
                ).join()
                .stream()
                .collect(java.util.stream.Collectors.toSet());
                if (config.isDebugLogging()) {
                    LOGGER.info("Found existing collections: " + existingCollections);
                }
                for (String collection : existingCollections) {
                    if (!collection.equals(config.getPlayerLanguagesCollection())) {
                        loadCollectionIntoCache(collection).join();
                    }
                }
                knownCollections.addAll(existingCollections);
                if (config.isDebugLogging()) {
                    LOGGER.info("Pre-warming cache completed for " + existingCollections.size() + " collections");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error pre-warming cache", e);
            }
        }, asyncExecutor);
    }
    private CompletableFuture<Void> loadCollectionIntoCache(String collection) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (config.isVerboseLogging()) {
                    LOGGER.info("Loading collection into cache: " + collection);
                }
                ConfigDocument configDoc = mongoManager.getConfig(collection).join();
                if (configDoc != null && configDoc.getData() != null) {
                    cacheManager.putConfigData(collection, configDoc.getData());
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Loaded config data for collection: " + collection);
                    }
                    
                    Object supportedLanguagesObj = configDoc.getData().get("_system.supported_languages");
                    if (supportedLanguagesObj instanceof List) {
                        Set<String> supportedLanguages = new HashSet<>((List<String>) supportedLanguagesObj);
                        collectionLanguages.put(collection, supportedLanguages);
                        if (config.isDebugLogging()) {
                            LOGGER.info("Found supported languages in config for " + collection + ": " + supportedLanguages);
                        }
                    }
                }
                
                Set<String> actualLanguages = PublisherAdapter.toCompletableFutureList(
                    mongoManager.getCollection(collection)
                        .find(Filters.exists("lang"))
                ).join()
                .stream()
                .map(doc -> doc.getString("lang"))
                .filter(lang -> lang != null)
                .collect(java.util.stream.Collectors.toSet());
                
                if (config.isVerboseLogging()) {
                    LOGGER.info("Found actual languages in collection " + collection + ": " + actualLanguages);
                }
                
                Set<String> expectedLanguages = collectionLanguages.getOrDefault(collection, actualLanguages);
                if (actualLanguages.isEmpty() && !expectedLanguages.isEmpty()) {
                    if (config.isDebugLogging()) {
                        LOGGER.info("No actual languages found but expected languages exist for " + collection + ", will regenerate during reload");
                    }
                } else if (!expectedLanguages.isEmpty()) {
                    collectionLanguages.put(collection, expectedLanguages);
                } else {
                    collectionLanguages.put(collection, actualLanguages);
                }
                
                for (String language : actualLanguages) {
                    LanguageDocument langDoc = mongoManager.getLanguage(collection, language).join();
                    if (langDoc != null && langDoc.getData() != null) {
                        cacheManager.putMessageData(collection, language, langDoc.getData());
                        if (config.isVerboseLogging()) {
                            LOGGER.info("Loaded language data for " + collection + ":" + language);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error loading collection into cache: " + collection, e);
            }
        }, asyncExecutor);
    }
    @Override
    public void invalidateCache(String collection) {
        cacheManager.invalidateCollection(collection);
        metricsManager.recordCacheOperation("invalidate_collection", "success");
        if (config.isDebugLogging()) {
            LOGGER.info("Cache invalidated for collection: " + collection);
        }
    }
    @Override
    public void invalidateCache() {
        cacheManager.invalidateAll();
        metricsManager.recordCacheOperation("invalidate_all", "success");
        if (config.isDebugLogging()) {
            LOGGER.info("All cache invalidated");
        }
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
    
    private CompletableFuture<Void> ensureLanguageDocumentsExist(String collection, Set<String> expectedLanguages) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (config.isDebugLogging()) {
                    LOGGER.info("Checking language documents for collection: " + collection + ", expected: " + expectedLanguages);
                }
                
                Set<String> existingLanguages = getExistingLanguagesInCollection(collection).join();
                
                Set<String> missingLanguages = new HashSet<>(expectedLanguages);
                missingLanguages.removeAll(existingLanguages);
                
                if (missingLanguages.isEmpty()) {
                    if (config.isDebugLogging()) {
                        LOGGER.info("All language documents exist for collection: " + collection);
                    }
                    return;
                }
                
                if (config.isDebugLogging()) {
                    LOGGER.info("Missing language documents in collection " + collection + ": " + missingLanguages);
                    LOGGER.info("Creating missing language documents...");
                }
                
                List<CompletableFuture<Void>> createFutures = missingLanguages.stream()
                        .map(language -> {
                            LanguageDocument langDoc = new LanguageDocument(language, new HashMap<>());
                            return mongoManager.saveLanguage(collection, langDoc)
                                    .thenRun(() -> {
                                        if (config.isVerboseLogging()) {
                                            LOGGER.info("Created language document: " + collection + ":" + language);
                                        }
                                    });
                        })
                        .toList();
                
                CompletableFuture.allOf(createFutures.toArray(new CompletableFuture[0])).join();
                if (config.isDebugLogging()) {
                    LOGGER.info("Successfully created " + missingLanguages.size() + " missing language documents for collection: " + collection);
                }
                
            } catch (Exception e) {
                if (config.isVerboseLogging()) {
                    LOGGER.log(Level.SEVERE, "Error ensuring language documents exist for collection: " + collection, e);
                }
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    
    private CompletableFuture<Set<String>> getExistingLanguagesInCollection(String collection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Set<String> languages = PublisherAdapter.toCompletableFutureList(
                    mongoManager.getCollection(collection)
                        .find(Filters.exists("lang"))
                ).join()
                .stream()
                .map(doc -> doc.getString("lang"))
                .filter(lang -> lang != null)
                .collect(java.util.stream.Collectors.toSet());
                
                if (config.isVerboseLogging()) {
                    LOGGER.info("Found existing languages in collection " + collection + ": " + languages);
                }
                
                return languages;
                
            } catch (Exception e) {
                if (config.isDebugLogging()) {
                    LOGGER.log(Level.WARNING, "Error getting existing languages for collection: " + collection, e);
                }
                return Set.of();
            }
        }, asyncExecutor);
    }
    
    public CompletableFuture<Void> updateSupportedLanguages(String collection, Set<String> languages) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (config.isDebugLogging()) {
                    LOGGER.info("Updating supported languages for collection: " + collection + " -> " + languages);
                }
                
                ConfigDocument configDoc = mongoManager.getConfig(collection).join();
                Map<String, Object> configData = new HashMap<>();
                if (configDoc != null && configDoc.getData() != null) {
                    configData.putAll(configDoc.getData());
                }
                
                configData.put("_system.supported_languages", new ArrayList<>(languages));
                ConfigDocument updatedConfigDoc = new ConfigDocument("config", configData);
                mongoManager.saveConfig(collection, updatedConfigDoc).join();
                
                collectionLanguages.put(collection, new HashSet<>(languages));
                
                if (config.isDebugLogging()) {
                    LOGGER.info("Successfully updated supported languages for collection: " + collection);
                }
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error updating supported languages for collection: " + collection, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    
    public void logConfigurationStatus() {
        LOGGER.info("=== MongoDB Configs Configuration Status ===");
        LOGGER.info("Database: " + config.getDatabase());
        LOGGER.info("Connection Pool: max=" + config.getMaxPoolSize() + ", min=" + config.getMinPoolSize());
        LOGGER.info("Connection Timeouts: connect=" + config.getConnectTimeoutMs() + "ms, socket=" + config.getSocketTimeoutMs() + "ms, selection=" + config.getServerSelectionTimeoutMs() + "ms");
        LOGGER.info("Cache: Simple in-memory maps, size=" + cacheManager.getEstimatedSize());
        LOGGER.info("Performance: io-threads=" + config.getIoThreads() + ", worker-threads=" + config.getWorkerThreads());
        LOGGER.info("Cache requests - Config: " + cacheManager.getConfigRequests() + ", Messages: " + cacheManager.getMessageRequests());
        LOGGER.info("Known collections: " + knownCollections.size());
        LOGGER.info("=== Configuration simplified - removed unused Caffeine cache ===");
    }
}
