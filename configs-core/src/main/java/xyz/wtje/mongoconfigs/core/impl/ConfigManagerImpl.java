package xyz.wtje.mongoconfigs.core.impl;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.Messages;
import xyz.wtje.mongoconfigs.core.cache.CacheManager;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.mongoconfigs.core.model.ConfigDocument;
import xyz.wtje.mongoconfigs.core.model.LanguageDocument;
import xyz.wtje.mongoconfigs.core.mongo.MongoManager;
import xyz.wtje.mongoconfigs.core.mongo.PublisherAdapter;
import xyz.wtje.mongoconfigs.core.TypedConfigManager;
import xyz.wtje.mongoconfigs.core.util.ColorProcessor;
import xyz.wtje.mongoconfigs.core.util.MessageFormatter;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.time.Duration;
public class ConfigManagerImpl implements ConfigManager, xyz.wtje.mongoconfigs.api.ConfigCollectionsOps {
    private static final Logger LOGGER = Logger.getLogger(ConfigManagerImpl.class.getName());
    private final MongoManager mongoManager;
    private final CacheManager cacheManager;
    private final MessageFormatter messageFormatter;
    private final MongoConfig config;
    private final Executor asyncExecutor;
    private ColorProcessor colorProcessor;
    private final Set<String> knownCollections = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<String>> collectionLanguages = new ConcurrentHashMap<>();
    private final TypedConfigManager typedConfigManager;
    public ConfigManagerImpl(MongoConfig config) {
        this.config = config;
        this.mongoManager = new MongoManager(config);
        this.asyncExecutor = mongoManager.getExecutorService();

        long ttlSeconds = config.getCacheTtlSeconds();
        long maxSize = config.getCacheMaxSize();
        boolean recordStats = config.isCacheRecordStats();

        if (ttlSeconds <= 0) {
            this.cacheManager = new CacheManager(maxSize, null, recordStats);
        } else {
            this.cacheManager = new CacheManager(maxSize, Duration.ofSeconds(ttlSeconds), recordStats);
        }

        this.messageFormatter = new MessageFormatter();
        this.typedConfigManager = new TypedConfigManager(mongoManager.getCollection(config.getTypedConfigsCollection()), mongoManager);
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        LOGGER.info("ConfigManager initialized with MongoDB database: " + config.getDatabase() +
                   ", Cache TTL: " + (ttlSeconds <= 0 ? "no expiration" : ttlSeconds + "s") +
                   ", Max size: " + maxSize);
    }
    public void initialize() {
        preWarmCache();
        logConfigurationStatus();
        LOGGER.info("ConfigManager fully initialized and ready");
    }

    public TypedConfigManager getTypedConfigManager() {
        return typedConfigManager;
    }

    public void shutdown() {
        mongoManager.close();
        LOGGER.info("ConfigManager shutdown complete");
    }
    public void setColorProcessor(ColorProcessor colorProcessor) {
        this.colorProcessor = colorProcessor;
        this.messageFormatter.setColorProcessor(colorProcessor);
    }
    public CompletableFuture<Set<String>> getCollections() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Set<String> mongoCollections = mongoManager.getMongoCollections();

                Set<String> allCollections = new HashSet<>(knownCollections);
                allCollections.addAll(mongoCollections);

                knownCollections.addAll(mongoCollections);

                if (config.isDebugLogging()) {
                    LOGGER.info("Found " + allCollections.size() + " total collections (MongoDB: " + 
                               mongoCollections.size() + ", Known: " + knownCollections.size() + ")");
                }

                return allCollections;

            } catch (Exception e) {
                if (config.isVerboseLogging()) {
                    LOGGER.log(Level.WARNING, "Error getting MongoDB collections, returning known collections", e);
                }
                return new HashSet<>(knownCollections);
            }
        }, asyncExecutor);
    }
    public Set<String> getSupportedLanguages(String collection) {
        return collectionLanguages.getOrDefault(collection, Set.of());
    }
    public boolean collectionExists(String collection) {
        return knownCollections.contains(collection) || cacheManager.hasCollection(collection);
    }
    public CompletableFuture<Void> reloadCollection(String collection) {
        return CompletableFuture.runAsync(() -> {
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
                        .collect(Collectors.toList());

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
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error reloading collection: " + collection, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    @Override
    public CompletableFuture<Void> reloadAll() {
        return CompletableFuture.supplyAsync(() -> {
            if (config.isDebugLogging()) {
                LOGGER.info("Starting reloadAll() - clearing cache first...");
            }
            return null;
        }, asyncExecutor)
        .thenCompose(v -> cacheManager.invalidateAllAsync())
        .thenCompose(v -> getCollectionsAsync())
        .thenCompose(allCollections -> {
            if (allCollections.isEmpty()) {
                LOGGER.warning("No collections found to reload!");
                return CompletableFuture.completedFuture(null);
            }

            if (config.isDebugLogging()) {
                LOGGER.info("Reloading " + allCollections.size() + " collections...");
            }

            return prepareCollectionsAsync(allCollections)
                .thenCompose(prepared -> reloadCollectionsInParallel(allCollections))
                .thenRun(() -> {
                    if (config.isDebugLogging()) {
                        LOGGER.info("Successfully reloaded all " + allCollections.size() + " collections!");
                    }
                });
        })
        .exceptionally(throwable -> {
            LOGGER.log(Level.SEVERE, "Error during reload operations", throwable);
            throw new RuntimeException(throwable);
        });
    }

    private CompletableFuture<Set<String>> getCollectionsAsync() {
        return CompletableFuture.supplyAsync(() -> {
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
            return allCollections;
        }, asyncExecutor);
    }

    private CompletableFuture<Void> prepareCollectionsAsync(Set<String> allCollections) {
        if (config.isDebugLogging()) {
            LOGGER.info("Phase 1: Loading config documents and checking for missing language documents...");
        }

        List<CompletableFuture<Void>> preparationFutures = allCollections.stream()
            .map(collection -> CompletableFuture.runAsync(() -> {
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
            }, asyncExecutor))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(preparationFutures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Void> reloadCollectionsInParallel(Set<String> allCollections) {
        if (config.isDebugLogging()) {
            LOGGER.info("Phase 2: Reloading all collection data in parallel...");
        }

        List<CompletableFuture<Void>> reloadFutures = allCollections.stream()
                .map(collection -> {
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Queuing reload for collection: " + collection);
                    }
                    return reloadCollectionAsync(collection)
                        .exceptionally(throwable -> {
                            if (config.isVerboseLogging()) {
                                LOGGER.log(Level.SEVERE, "Error reloading collection: " + collection, throwable);
                            }
                            return null;
                        });
                })
                .collect(Collectors.toList());

        return CompletableFuture.allOf(reloadFutures.toArray(new CompletableFuture[0]));
    }
    public CompletableFuture<Void> reloadCollectionsBatch(Set<String> collections) {
        return reloadCollectionsBatch(collections, 3);
    }

    public CompletableFuture<Void> reloadCollectionsBatch(Set<String> collections, int maxConcurrency) {
        if (collections.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            if (config.isDebugLogging()) {
                LOGGER.info("Starting batch reload of " + collections.size() + " collections with max concurrency: " + maxConcurrency);
            }
            return new ArrayList<>(collections);
        }, asyncExecutor).thenCompose(collectionList -> {

            return processBatchesSequentially(collectionList, maxConcurrency, 0)
                    .thenRun(() -> {
                        if (config.isDebugLogging()) {
                            LOGGER.info("Successfully completed batch reload of " + collectionList.size() + " collections");
                        }
                    });
        });
    }

    private CompletableFuture<Void> processBatchesSequentially(List<String> collections, int maxConcurrency, int startIndex) {
        if (startIndex >= collections.size()) {
            return CompletableFuture.completedFuture(null);
        }

        int endIndex = Math.min(startIndex + maxConcurrency, collections.size());
        List<String> batchCollections = collections.subList(startIndex, endIndex);

        if (config.isVerboseLogging()) {
            LOGGER.info("Reloading batch " + ((startIndex / maxConcurrency) + 1) + ": " + batchCollections);
        }

        List<CompletableFuture<Void>> batchFutures = batchCollections.stream()
                .map(this::reloadCollection)
                .collect(Collectors.toList());

        return CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
                .thenCompose(result -> {
                    if (config.isVerboseLogging()) {
                        LOGGER.info("Completed reload batch " + ((startIndex / maxConcurrency) + 1));
                    }

                    if (endIndex < collections.size()) {
                        return CompletableFuture.runAsync(() -> {}, 
                                CompletableFuture.delayedExecutor(300, java.util.concurrent.TimeUnit.MILLISECONDS))
                                .thenCompose(v -> processBatchesSequentially(collections, maxConcurrency, endIndex));
                    } else {
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.log(Level.SEVERE, "Error in batch collection reload", throwable);
                    throw new RuntimeException(throwable);
                });
    }

    public CompletableFuture<Void> reloadCollectionAsync(String collection) {
        return reloadCollection(collection);
    }
    private <T> CompletableFuture<Void> putConfigValues(String collection, Map<String, T> configValues) {
        return mongoManager.getConfig(collection)
                .thenCompose(configDoc -> {
                    if (configDoc == null) {
                        configDoc = new ConfigDocument("config", new HashMap<>());
                        if (config.isVerboseLogging()) {
                            LOGGER.info("Creating new config document for collection: " + collection);
                        }
                    }
                    Map<String, Object> data = configDoc.getData();
                    data.putAll(configValues);
                    cacheManager.putConfigData(collection, data);
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
    private CompletableFuture<Void> putMessagesMultiLang(String collection, Map<String, Map<String, String>> languageMessages) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : languageMessages.entrySet()) {
            String language = entry.getKey();
            Map<String, String> messages = entry.getValue();
            futures.add(
                mongoManager.getLanguage(collection, language)
                        .thenCompose(langDoc -> {
                            if (langDoc == null) {
                                langDoc = new LanguageDocument(language, new HashMap<>());
                            }
                            Map<String, Object> data = langDoc.getData();
                            for (Map.Entry<String, String> e : messages.entrySet()) {
                                setNestedValue(data, e.getKey(), e.getValue());
                                cacheManager.putMessage(collection, language, e.getKey(), e.getValue());
                            }
                            return mongoManager.saveLanguage(collection, langDoc);
                        })
            );
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
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
    public void invalidateCache(String collection) {
        cacheManager.invalidateCollection(collection);
        if (config.isDebugLogging()) {
            LOGGER.info("Cache invalidated for collection: " + collection);
        }
    }
    public void invalidateCache() {
        cacheManager.invalidateAll();
        if (config.isDebugLogging()) {
            LOGGER.info("All cache invalidated");
        }
    }

    @Override
    public CompletableFuture<Void> invalidateAllAsync() {
        return cacheManager.invalidateAllAsync()
                .thenRun(() -> {
                    if (config.isDebugLogging()) {
                        LOGGER.info("All cache invalidated asynchronously");
                    }
                });
    }

    public boolean hasMessages(String collection, String language) {
        try {
            String testMessage = cacheManager.getMessage(collection, language, "test.key");
            if (testMessage != null && !testMessage.equals("test.key")) {
                return true;
            }

            try {
                LanguageDocument langDoc = mongoManager.getLanguage(collection, language).join();
                boolean hasData = langDoc != null && langDoc.getData() != null && !langDoc.getData().isEmpty();
                return hasData;
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public CompletableFuture<Void> forceRegenerateLanguageDocuments(String collection) {
        return CompletableFuture.runAsync(() -> {
            if (config.isDebugLogging()) {
                LOGGER.info("Force regenerating language documents for collection: " + collection);
            }

            try {
                Set<String> expectedLanguages = collectionLanguages.getOrDefault(collection, Set.of());

                if (expectedLanguages.isEmpty()) {
                    ConfigDocument configDoc = mongoManager.getConfig(collection).join();
                    if (configDoc != null && configDoc.getData() != null) {
                        Object supportedLanguagesObj = configDoc.getData().get("_system.supported_languages");
                        if (supportedLanguagesObj instanceof List) {
                            expectedLanguages = new HashSet<>((List<String>) supportedLanguagesObj);
                        }
                    }
                }

                if (expectedLanguages.isEmpty()) {
                    LOGGER.warning("No expected languages found for collection: " + collection + ". Cannot regenerate.");
                    return;
                }

                if (config.isDebugLogging()) {
                    LOGGER.info("Force creating language documents for: " + expectedLanguages);
                }

                ensureLanguageDocumentsExist(collection, expectedLanguages).join();

                if (config.isDebugLogging()) {
                    LOGGER.info("Reloading collection after regeneration: " + collection);
                }
                reloadCollection(collection).join();

                if (config.isDebugLogging()) {
                    LOGGER.info("Force regeneration completed for collection: " + collection);
                }

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error force regenerating language documents for collection: " + collection, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Void> forceRegenerateCollection(String collection, Set<String> expectedLanguages) {
        return CompletableFuture.runAsync(() -> {
            if (config.isDebugLogging()) {
                LOGGER.info("Force regenerating collection: " + collection + " with languages: " + expectedLanguages);
            }

            try {
                collectionLanguages.put(collection, expectedLanguages);

                ConfigDocument configDoc = mongoManager.getConfig(collection).join();
                if (configDoc == null) {
                    if (config.isDebugLogging()) {
                        LOGGER.info("Creating new config document for collection: " + collection);
                    }
                    Map<String, Object> configData = new HashMap<>();
                    configData.put("_system.supported_languages", new ArrayList<>(expectedLanguages));
                    configDoc = new ConfigDocument("config", configData);
                    mongoManager.saveConfig(collection, configDoc).join();
                } else {
                    if (config.isDebugLogging()) {
                        LOGGER.info("Updating supported languages in existing config document");
                    }
                    Map<String, Object> data = configDoc.getData();
                    if (data == null) {
                        data = new HashMap<>();
                        configDoc.setData(data);
                    }
                    data.put("_system.supported_languages", new ArrayList<>(expectedLanguages));
                    mongoManager.saveConfig(collection, configDoc).join();
                }

                ensureLanguageDocumentsExist(collection, expectedLanguages).join();

                reloadCollection(collection).join();

                if (config.isDebugLogging()) {
                    LOGGER.info("Force regeneration completed for collection: " + collection);
                }

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error force regenerating collection: " + collection, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }
    public MongoManager getMongoManager() {
        return mongoManager;
    }

    private CompletableFuture<Void> ensureLanguageDocumentsExist(String collection, Set<String> expectedLanguages) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (config.isDebugLogging()) {
                    LOGGER.info("🔍 Checking language documents for collection: " + collection + ", expected: " + expectedLanguages);
                }

                Set<String> existingLanguages = getExistingLanguagesInCollection(collection).join();

                if (config.isDebugLogging()) {
                    LOGGER.info("📋 Existing languages: " + existingLanguages);
                }

                Set<String> missingLanguages = new HashSet<>(expectedLanguages);
                missingLanguages.removeAll(existingLanguages);

                if (missingLanguages.isEmpty()) {
                    if (config.isDebugLogging()) {
                        LOGGER.info("✅ All language documents exist for collection: " + collection);
                    }
                    return;
                }

                if (config.isDebugLogging()) {
                    LOGGER.info("❌ Missing language documents in collection " + collection + ": " + missingLanguages);
                    LOGGER.info("🔧 Creating missing language documents...");
                }

                List<CompletableFuture<Void>> createFutures = missingLanguages.stream()
                        .map(language -> {
                            if (config.isVerboseLogging()) {
                                LOGGER.info("🆕 Creating language document: " + collection + ":" + language);
                            }
                            LanguageDocument langDoc = new LanguageDocument(language, new HashMap<>());
                            return mongoManager.saveLanguage(collection, langDoc)
                                    .thenRun(() -> {
                                        if (config.isVerboseLogging()) {
                                            LOGGER.info("✅ Created language document: " + collection + ":" + language);
                                        }
                                    })
                                    .exceptionally(throwable -> {
                                        LOGGER.log(Level.SEVERE, "❌ Failed to create language document " + collection + ":" + language, throwable);
                                        return null;
                                    });
                        })
                        .collect(Collectors.toList());

                CompletableFuture.allOf(createFutures.toArray(new CompletableFuture[0])).join();
                if (config.isDebugLogging()) {
                    LOGGER.info("🎉 Successfully created " + missingLanguages.size() + " missing language documents for collection: " + collection);
                }

            } catch (Exception e) {
                if (config.isVerboseLogging()) {
                    LOGGER.log(Level.SEVERE, "💥 Error ensuring language documents exist for collection: " + collection, e);
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

    public CacheManager getCacheManager() { return cacheManager; }

    public CompletableFuture<Void> reloadCollectionsBatchAsync(Set<String> collections, int maxConcurrency) {
        return reloadCollectionsBatch(collections, maxConcurrency);
    }

    public <T> CompletableFuture<T> getConfigAsync(String collection, String key, T defaultValue) {
        return cacheManager.getAsync(collection + ":" + key, defaultValue);
    }

    public CompletableFuture<String> getMessageAsync(String collection, String language, String key) {
        return cacheManager.getMessageAsync(collection, language, key);
    }

    public CompletableFuture<String> getMessageAsync(String collection, String language, String key, String defaultValue) {
        return cacheManager.getMessageAsync(collection, language, key, defaultValue);
    }

    @Override
    public <T> CompletableFuture<Void> set(String id, T value) {
        return typedConfigManager.setObject(id, value);
    }

    @Override
    public <T> CompletableFuture<T> get(String id, Class<T> type) {
        return typedConfigManager.getObject(id, type);
    }

    public <T> CompletableFuture<Void> set(String id, String key, T value) {
        return typedConfigManager.set(id, key, value);
    }

    public <T> CompletableFuture<T> get(String id, String key, Class<T> type) {
        return typedConfigManager.get(id, key, type);
    }

    @Override
    public <T> CompletableFuture<Void> setObject(T pojo) {
        return typedConfigManager.setObject(pojo);
    }

    @Override
    public <T> CompletableFuture<T> getObject(Class<T> type) {
        return typedConfigManager.getObject(type);
    }

    @Override
    public <T> CompletableFuture<T> getConfigOrGenerate(Class<T> type, Supplier<T> generator) {
        return typedConfigManager.getConfigOrGenerate(type, generator);
    }

    public <T> CompletableFuture<Void> setObject(String id, T pojo) {
        return typedConfigManager.setObject(id, pojo);
    }

    public <T> CompletableFuture<T> getObject(String id, Class<T> type) {
        return typedConfigManager.getObject(id, type);
    }

    @Override
    public Messages findById(String id) {
        return new Messages() {
            private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            @Override
            public <T> T get(String lang, String key, Class<T> type) {
                try {
                    Object raw = cacheManager.getMessage(id, lang, key, (Object) null);
                    if (raw == null) {
                        raw = cacheManager.getMessage(id, config.getDefaultLanguage(), key, (Object) null);
                    }
                    if (raw == null) return null;
                    if (type.isInstance(raw)) return type.cast(raw);
                    return mapper.convertValue(raw, type);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error getting typed message: " + id + ":" + lang + ":" + key, e);
                    return null;
                }
            }
            @Override
            public String get(String lang, String key, Object... placeholders) {
                try {
                    String message = cacheManager.getMessage(id, lang, key, (String) null);
                    if (message == null) {
                        message = cacheManager.getMessage(id, config.getDefaultLanguage(), key, key);
                    }
                    return messageFormatter.format(message, placeholders);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error getting message: " + id + ":" + lang + ":" + key, e);
                    return key;
                }
            }

            @Override
            public String get(String lang, String key, java.util.Map<String, Object> placeholders) {
                if (placeholders == null || placeholders.isEmpty()) {
                    return get(lang, key);
                }
                Object[] flat = new Object[placeholders.size() * 2];
                int i = 0;
                for (java.util.Map.Entry<String, Object> e : placeholders.entrySet()) {
                    flat[i++] = e.getKey();
                    flat[i++] = e.getValue();
                }
                return get(lang, key, flat);
            }
            
            // 🚀 ASYNC METHODS - NO MAIN THREAD BLOCKING!
            @Override
            public <T> java.util.concurrent.CompletableFuture<T> getAsync(String lang, String key, Class<T> type) {
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        Object raw = cacheManager.getMessage(id, lang, key, (Object) null);
                        if (raw == null) {
                            raw = cacheManager.getMessage(id, config.getDefaultLanguage(), key, (Object) null);
                        }
                        if (raw == null) return null;
                        if (type.isInstance(raw)) return type.cast(raw);
                        return mapper.convertValue(raw, type);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error getting async typed message: " + id + ":" + lang + ":" + key, e);
                        return null;
                    }
                }, asyncExecutor);
            }
            
            @Override
            public java.util.concurrent.CompletableFuture<String> getAsync(String lang, String key, Object... placeholders) {
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        String message = cacheManager.getMessage(id, lang, key, (String) null);
                        if (message == null) {
                            message = cacheManager.getMessage(id, config.getDefaultLanguage(), key, key);
                        }
                        return messageFormatter.format(message, placeholders);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error getting async message: " + id + ":" + lang + ":" + key, e);
                        return key;
                    }
                }, asyncExecutor);
            }
            
            @Override
            public java.util.concurrent.CompletableFuture<String> getAsync(String lang, String key, java.util.Map<String, Object> placeholders) {
                if (placeholders == null || placeholders.isEmpty()) {
                    return getAsync(lang, key);
                }
                Object[] flat = new Object[placeholders.size() * 2];
                int i = 0;
                for (java.util.Map.Entry<String, Object> e : placeholders.entrySet()) {
                    flat[i++] = e.getKey();
                    flat[i++] = e.getValue();
                }
                return getAsync(lang, key, flat);
            }
        };
    }

    @Override
    public Messages getMessagesOrGenerate(Class<?> messageClass, Supplier<Void> generator) {
        String collectionName = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(messageClass);
        
        // Check if messages exist
        Messages messages = findById(collectionName);
        
        // For simplicity, always return the messages instance
        // The generator logic would be implemented here if needed
        if (generator != null) {
            try {
                generator.get();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Generator failed for " + collectionName, e);
            }
        }
        
        return messages;
    }

    @Override
    public <T> void createFromObject(T messageObject) {
        String collectionName = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(messageObject.getClass());
        Set<String> supportedLanguages = xyz.wtje.mongoconfigs.api.core.Annotations.langsFrom(messageObject.getClass());

        if (supportedLanguages.isEmpty()) {
            supportedLanguages = Set.of("en");
        }

        Map<String, Object> flatMessages = extractFlatMessages(messageObject);

        for (String language : supportedLanguages) {
            xyz.wtje.mongoconfigs.core.model.LanguageDocument langDoc = 
                new xyz.wtje.mongoconfigs.core.model.LanguageDocument(language, flatMessages);

            try {
                mongoManager.saveLanguage(collectionName, langDoc).join();

                if (config.isDebugLogging()) {
                    LOGGER.info("Created flat messages for language: " + language + " in collection: " + collectionName);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to save messages for language: " + language, e);
            }
        }

        cacheManager.invalidateMessages(collectionName);
    }

    @Override
    public <T> Messages getOrCreateFromObject(T messageObject) {
        String collectionName = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(messageObject.getClass());

        Set<String> supportedLanguages = xyz.wtje.mongoconfigs.api.core.Annotations.langsFrom(messageObject.getClass());

        if (supportedLanguages.isEmpty()) {
            supportedLanguages = Set.of("en");
        }

        boolean hasMessages = false;
        for (String language : supportedLanguages) {
            try {
                xyz.wtje.mongoconfigs.core.model.LanguageDocument langDoc = 
                    mongoManager.getLanguage(collectionName, language).join();
                if (langDoc != null && langDoc.getData() != null && !langDoc.getData().isEmpty()) {
                    hasMessages = true;
                    break;
                }
            } catch (Exception e) {
            }
        }

        if (!hasMessages) {
            if (config.isDebugLogging()) {
                LOGGER.info("No messages found for " + collectionName + ", creating from object...");
            }
            createFromObject(messageObject);
        }

        return findById(collectionName);
    }

    private Map<String, Object> extractFlatMessages(Object messageObject) {
        Map<String, Object> result = new HashMap<>();
        Class<?> clazz = messageObject.getClass();

        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(messageObject);

                if (value != null) {
                    result.put(field.getName(), value.toString());
                }
            } catch (Exception e) {
                if (config.isDebugLogging()) {
                    LOGGER.log(Level.WARNING, "Failed to extract field: " + field.getName(), e);
                }
            }
        }

        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            if (method.getName().startsWith("get") && method.getParameterCount() == 0 && 
                !method.getName().equals("getClass")) {
                try {
                    method.setAccessible(true);
                    Object value = method.invoke(messageObject);

                    if (value != null) {
                        String fieldName = method.getName().substring(3); // Remove "get"
                        String key = camelCaseToSnakeCase(fieldName);
                        result.put(key, value.toString());
                    }
                } catch (Exception e) {
                    if (config.isDebugLogging()) {
                        LOGGER.log(Level.WARNING, "Failed to extract method: " + method.getName(), e);
                    }
                }
            }
        }

        return result;
    }

    private String camelCaseToSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1.$2").toLowerCase();
    }
}
