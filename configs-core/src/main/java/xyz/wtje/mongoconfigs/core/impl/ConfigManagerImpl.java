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
import java.io.IOException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.bson.Document;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.time.Duration;
public class ConfigManagerImpl implements ConfigManager {
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
    private final ObjectMapper languageMapper;
    private final Map<String, Set<Consumer<String>>> reloadListeners = new ConcurrentHashMap<>();
    private final Map<String, xyz.wtje.mongoconfigs.core.ChangeStreamWatcher> changeStreamWatchers = new ConcurrentHashMap<>();
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

        
        this.cacheManager.addInvalidationListener(coll -> {
            if ("*".equals(coll)) {
                LOGGER.info("🧹 CACHE: PEŁNA INWALIDACJA WSZYSTKICH KOLEKCJI (Timestamp: " + System.currentTimeMillis() + ")");
            } else if (coll != null) {
                LOGGER.info("🧹 CACHE: INWALIDACJA KOLEKCJI -> " + coll + " (Timestamp: " + System.currentTimeMillis() + ")");
            }
        });

        this.messageFormatter = new MessageFormatter();
        this.typedConfigManager = new TypedConfigManager(mongoManager.getCollection(config.getTypedConfigsCollection()), mongoManager);
        this.languageMapper = new ObjectMapper();
        this.languageMapper.registerModule(new JavaTimeModule());
        this.languageMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.languageMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.languageMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        
        LOGGER.info("ConfigManager initialized with MongoDB database: " + config.getDatabase() +
                   ", Cache TTL: " + (ttlSeconds <= 0 ? "no expiration" : ttlSeconds + "s") +
                   ", Max size: " + maxSize);
    }
    public void initialize() {
        preWarmCache()
            .thenRun(() -> {
                setupChangeStreams();
                logConfigurationStatus();
                LOGGER.info("ConfigManager fully initialized and ready");
            });
    }

    public TypedConfigManager getTypedConfigManager() {
        return typedConfigManager;
    }

    public void shutdown() {
        
        for (xyz.wtje.mongoconfigs.core.ChangeStreamWatcher watcher : changeStreamWatchers.values()) {
            try {
                watcher.stop();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error stopping change stream watcher", e);
            }
        }
        changeStreamWatchers.clear();
        
        mongoManager.close();
        LOGGER.info("ConfigManager shutdown complete");
    }
    private boolean isIgnoredCollection(String collectionName) {
        if (collectionName == null) return true;
        if (collectionName.equals("player_languages") || collectionName.equals(config.getPlayerLanguagesCollection())) {
            return true;
        }
        java.util.Set<String> ignored = config.getIgnoredCollections();
        return ignored != null && ignored.contains(collectionName);
    }

    private boolean isIgnoredDatabase() {
        java.util.Set<String> ignoredDbs = config.getIgnoredDatabases();
        if (ignoredDbs == null || ignoredDbs.isEmpty()) return false;
        String db = config.getDatabase();
        return db != null && ignoredDbs.contains(db);
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
    public CompletableFuture<Set<String>> getSupportedLanguages(String collection) {
        return CompletableFuture.completedFuture(collectionLanguages.getOrDefault(collection, Set.of()));
    }
    public CompletableFuture<Boolean> collectionExists(String collection) {
        return CompletableFuture.completedFuture(knownCollections.contains(collection) || cacheManager.hasCollection(collection));
    }
    public CompletableFuture<Void> reloadCollection(String collection) {
        return reloadCollection(collection, true);
    }
    
    public CompletableFuture<Void> reloadCollection(String collection, boolean invalidateCache) {
        if (isIgnoredDatabase() || isIgnoredCollection(collection)) {
            LOGGER.info("Skipping reload for ignored collection: " + collection);
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            
            LOGGER.info("🔁 START RELOAD KOLEKCJI: " + collection + " (invalidateCache=" + invalidateCache + ")");
            
            
            if (invalidateCache) {
                LOGGER.info("🧹 INWALIDACJA CACHE dla kolekcji: " + collection);
                cacheManager.invalidateCollection(collection);
            }
        }, asyncExecutor)
        .thenCompose(v -> {
            
            if (!changeStreamWatchers.containsKey(collection) && !isIgnoredCollection(collection)) {
                setupChangeStreamForCollection(collection);
            }
            
            return mongoManager.getConfig(collection)
                .thenCompose(configDoc -> {
                    if (configDoc == null) {
                        if (config.isDebugLogging()) {
                            LOGGER.info("Config document missing for collection: " + collection + ", skipping auto-create");
                        }
                        return CompletableFuture.completedFuture(null);
                    } else {
                        return CompletableFuture.completedFuture(configDoc);
                    }
                })
                .exceptionally(throwable -> {
                    if (config.isVerboseLogging()) {
                        LOGGER.log(Level.WARNING, "Failed to load config for collection: " + collection, throwable);
                    }
                    return null;
                })
                .thenCompose(configDoc -> {
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

                    final Set<String> finalExpectedLanguages = expectedLanguages;
                    CompletableFuture<Void> ensureLanguagesFuture;
                    
                    if (!expectedLanguages.isEmpty()) {
                        if (config.isDebugLogging()) {
                            LOGGER.info("Ensuring language documents exist for collection: " + collection + " -> " + expectedLanguages);
                        }
                        ensureLanguagesFuture = ensureLanguageDocumentsExist(collection, expectedLanguages);
                    } else {
                        if (config.isDebugLogging()) {
                            LOGGER.info("No expected languages found for collection: " + collection + ", skipping document check");
                        }
                        ensureLanguagesFuture = CompletableFuture.completedFuture(null);
                    }

                    return ensureLanguagesFuture.thenCompose(v2 -> {
            
                        List<CompletableFuture<LanguageDocument>> languageFutures = finalExpectedLanguages.stream()
                                .map(lang -> mongoManager.getLanguage(collection, lang))
                                .collect(Collectors.toList());

                        
                        if (config.isDebugLogging()) {
                            LOGGER.info("Refreshing cache for collection: " + collection);
                        }
                        
                        
                        if (invalidateCache) {
                            cacheManager.invalidateCollection(collection);
                        }
                        
                        
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

                        
                        return CompletableFuture.allOf(languageFutures.toArray(new CompletableFuture[0]))
                            .thenCompose(unused -> {
                                
                                List<CompletableFuture<Void>> cachingFutures = new ArrayList<>();
                                
                                for (CompletableFuture<LanguageDocument> future : languageFutures) {
                                    CompletableFuture<Void> cachingFuture = future
                                        .thenAcceptAsync(langDoc -> {
                                            if (langDoc != null && langDoc.getData() != null) {
                                                cacheManager.putMessageData(collection, langDoc.getLang(), langDoc.getData());
                                            }
                                        }, asyncExecutor)
                                        .exceptionally(throwable -> {
                                            if (config.isVerboseLogging()) {
                                                LOGGER.log(Level.WARNING, "Failed to load language data for collection: " + collection, throwable);
                                            }
                                            return null;
                                        });
                                    cachingFutures.add(cachingFuture);
                                }
                                
                                return CompletableFuture.allOf(cachingFutures.toArray(new CompletableFuture[0]));
                            })
                            .thenRun(() -> {
                                
                                LOGGER.info("✅ ZAKOŃCZONO RELOAD KOLEKCJI: " + collection +
                                            " | Config=" + (configDoc != null ? "ZAŁADOWANY" : "BRAK") +
                                            " | Languages=" + finalExpectedLanguages.size() +
                                            " | Timestamp=" + System.currentTimeMillis());
                                
                                
                                notifyReloadListeners(collection);
                            });
                    });
                });
        })
        .exceptionally(throwable -> {
            LOGGER.log(Level.SEVERE, "Error reloading collection: " + collection, throwable);
            throw new RuntimeException(throwable);
        });
    }
    @Override
    public CompletableFuture<Void> reloadAll() {
        return CompletableFuture.supplyAsync(() -> {
            if (config.isDebugLogging()) {
                LOGGER.info("Starting reloadAll() - getting collections to reload...");
            }
            return null;
        }, asyncExecutor)
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
                .thenCompose(prepared -> {
                    
                    return reloadCollectionsInParallel(allCollections);
                })
                .thenRun(() -> {
                    if (config.isDebugLogging()) {
                        LOGGER.info("Successfully reloaded all " + allCollections.size() + " collections into cache!");
                    }
                    
                    
                    LOGGER.info("🔄 Setting up Change Streams for reloaded collections...");
                    for (String collection : allCollections) {
                        if (!changeStreamWatchers.containsKey(collection)) {
                            setupChangeStreamForCollection(collection);
                        }
                    }
                    LOGGER.info("✅ Change Streams setup completed after reload. Active watchers: " + changeStreamWatchers.size());
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
                        ensureLanguageDocumentsExist(collection, expectedLanguages);
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
    private CompletableFuture<Void> preWarmCache() {
        return CompletableFuture.runAsync(() -> {
            if (config.isDebugLogging()) {
                LOGGER.info("Pre-warming cache - discovering existing collections...");
            }
        }, asyncExecutor)
        .thenCompose(v -> {
            return PublisherAdapter.toCompletableFutureList(
                mongoManager.getDatabase().listCollectionNames()
            );
        })
        .thenCompose(existingCollections -> {
            Set<String> collections = existingCollections.stream()
                .collect(java.util.stream.Collectors.toSet());
            
            if (config.isDebugLogging()) {
                LOGGER.info("Found existing collections: " + collections);
            }
            
            
            List<CompletableFuture<Void>> loadFutures = collections.stream()
                .filter(collection -> !isIgnoredCollection(collection))
                .map(this::loadCollectionIntoCache)
                .collect(Collectors.toList());
            
            knownCollections.addAll(collections);
            
            return CompletableFuture.allOf(loadFutures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    if (config.isDebugLogging()) {
                        LOGGER.info("Pre-warming cache completed for " + collections.size() + " collections");
                    }
                });
        })
        .exceptionally(throwable -> {
            LOGGER.log(Level.WARNING, "Error pre-warming cache", throwable);
            return null;
        });
    }
    
    private void setupChangeStreams() {
        if (isIgnoredDatabase()) {
            LOGGER.info("Skipping Change Streams setup: database is ignored -> " + config.getDatabase());
            return;
        }
        if (!config.isEnableChangeStreams()) {
            LOGGER.info("⚠️ Change Streams DISABLED - manual reload required for updates");
            LOGGER.info("💡 To enable change streams, set enableChangeStreams=true in your MongoConfig");
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            LOGGER.info("📡 Setting up change streams for collections...");
            
            
            Set<String> collections = new HashSet<>(knownCollections);
            
            if (collections.isEmpty()) {
                LOGGER.warning("⚠️ No collections found for Change Streams! knownCollections is empty.");
                LOGGER.warning("💡 Make sure collections exist and are loaded before setting up change streams");
                return;
            }
            
            LOGGER.info("📊 Found " + collections.size() + " collections for Change Streams: " + collections);
            
            int successCount = 0;
            int skippedCount = 0;
            for (String collection : collections) {
                
                if (isIgnoredCollection(collection)) {
                    skippedCount++;
                    continue;
                }
                
                try {
                    setupChangeStreamForCollection(collection);
                    successCount++;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "❌ Failed to setup change stream for collection: " + collection, e);
                }
            }
            
            LOGGER.info("🎉 Change streams setup completed! Active watchers: " + changeStreamWatchers.size() + 
                       "/" + successCount + " (skipped: " + skippedCount + ", total collections: " + collections.size() + ")");
        }, asyncExecutor);
    }
    
    private void setupChangeStreamForCollection(String collectionName) {
        
        if (isIgnoredCollection(collectionName)) {
            LOGGER.info("������ Skipping change stream for ignored collection: " + collectionName);
            return;
        }
        
        if (collectionName.equals("player_languages") || 
            collectionName.equals(config.getPlayerLanguagesCollection())) {
            LOGGER.info("⏭️ Skipping change stream for player_languages collection");
            return;
        }
        
        if (changeStreamWatchers.containsKey(collectionName)) {
            LOGGER.info("🔄 Change stream already exists for collection: " + collectionName);
            return; 
        }
        
        LOGGER.info("🚀 Setting up new change stream for collection: " + collectionName);
        
        try {
            xyz.wtje.mongoconfigs.core.ChangeStreamWatcher watcher = 
                new xyz.wtje.mongoconfigs.core.ChangeStreamWatcher(
                    mongoManager.getCollection(collectionName), 
                    cacheManager
                );
            
            
            watcher.setReloadCallback(changedCollection -> {
                CompletableFuture.runAsync(() -> {
                    try {
                        
                        LOGGER.info("♻️ ROZPOCZYNAM ODŚWIEŻANIE KOLEKCJI po wykryciu zmiany (Change Stream): " + changedCollection);
                        
                        
                        reloadCollection(changedCollection, false)
                            .thenRun(() -> {
                                LOGGER.info("✅ ZAKOŃCZONO ODŚWIEŻANIE KOLEKCJI (cache przeładowany): " + changedCollection);
                                
                                notifyReloadListeners(changedCollection);
                            })
                            .exceptionally(throwable -> {
                                LOGGER.log(Level.WARNING, "❌ BŁĄD podczas odświeżania kolekcji po Change Stream: " + changedCollection, throwable);
                                return null;
                            });
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "❌ BŁĄD obsługi callbacku Change Stream dla: " + changedCollection, e);
                    }
                }, asyncExecutor);
            });
            
            watcher.start();
            changeStreamWatchers.put(collectionName, watcher);
            
            LOGGER.info("✅ Successfully setup change stream watcher for collection: " + collectionName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to setup change stream for collection: " + collectionName, e);
        }
    }
    private CompletableFuture<Void> loadCollectionIntoCache(String collection) {
        if (isIgnoredCollection(collection)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            if (config.isVerboseLogging()) {
                LOGGER.info("Loading collection into cache: " + collection);
            }
            return collection;
        }, asyncExecutor)
        .thenCompose(col -> {
            return mongoManager.getConfig(collection)
                .thenCompose(configDoc -> {
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

                    return PublisherAdapter.toCompletableFutureList(
                        mongoManager.getCollection(collection)
                            .find(Filters.exists("lang"))
                    );
                })
                .thenCompose(documents -> {
                    Set<String> actualLanguages = documents.stream()
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

                    
                    List<CompletableFuture<Void>> languageLoadFutures = actualLanguages.stream()
                        .map(language ->
                            mongoManager.getLanguage(collection, language)
                                .thenAccept(langDoc -> {
                                    if (langDoc != null && langDoc.getData() != null) {
                                        cacheManager.putMessageData(collection, language, langDoc.getData());
                                        if (config.isVerboseLogging()) {
                                            LOGGER.info("Loaded language data for " + collection + ":" + language);
                                        }
                                    }
                                })
                        )
                        .collect(Collectors.toList());

                    return CompletableFuture.allOf(languageLoadFutures.toArray(new CompletableFuture[0]));
                })
                .exceptionally(throwable -> {
                    LOGGER.log(Level.WARNING, "Error loading collection into cache: " + collection, throwable);
                    return null;
                });
        });
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

    
    public void enableChangeStreamForCollection(String collectionName) {
        setupChangeStreamForCollection(collectionName);
    }

    
    public void disableChangeStreamForCollection(String collectionName) {
        xyz.wtje.mongoconfigs.core.ChangeStreamWatcher watcher = changeStreamWatchers.remove(collectionName);
        if (watcher != null) {
            try {
                watcher.stop();
                if (config.isDebugLogging()) {
                    LOGGER.info("Disabled change stream for collection: " + collectionName);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error stopping change stream for collection: " + collectionName, e);
            }
        }
    }

    
    public boolean isChangeStreamEnabled(String collectionName) {
        return changeStreamWatchers.containsKey(collectionName);
    }
    
    
    public Map<String, String> getChangeStreamStatus() {
        Map<String, String> status = new HashMap<>();
        for (Map.Entry<String, xyz.wtje.mongoconfigs.core.ChangeStreamWatcher> entry : changeStreamWatchers.entrySet()) {
            status.put(entry.getKey(), entry.getValue().getStatus());
        }
        return status;
    }
    
    
    public void forceAllToPollingMode() {
        LOGGER.info("🔄 Forcing ALL change streams to polling mode for debugging");
        for (xyz.wtje.mongoconfigs.core.ChangeStreamWatcher watcher : changeStreamWatchers.values()) {
            watcher.forcePollingMode();
        }
    }
    
    
    public void testChangeDetection(String collectionName) {
        LOGGER.info("🧪 Testing change detection for collection: " + collectionName);
        if (changeStreamWatchers.containsKey(collectionName)) {
            xyz.wtje.mongoconfigs.core.ChangeStreamWatcher watcher = changeStreamWatchers.get(collectionName);
            watcher.triggerReload("test-manual");
        } else {
            LOGGER.warning("⚠️ No change stream watcher found for collection: " + collectionName);
        }
    }

    public CompletableFuture<Boolean> hasMessagesAsync(String collection, String language) {
        return CompletableFuture.supplyAsync(() -> {
            String testMessage = cacheManager.getMessage(collection, language, "test.key");
            if (testMessage != null && !testMessage.equals("test.key")) {
                return true;
            }
            return false;
        }, asyncExecutor)
        .thenCompose(cacheResult -> {
            if (cacheResult) {
                return CompletableFuture.completedFuture(true);
            }
            
            return mongoManager.getLanguage(collection, language)
                .thenApply(langDoc -> langDoc != null && langDoc.getData() != null && !langDoc.getData().isEmpty())
                .exceptionally(throwable -> false);
        });
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
        return CompletableFuture.supplyAsync(() -> {
            if (config.isDebugLogging()) {
                LOGGER.info("🔍 Checking language documents for collection: " + collection + ", expected: " + expectedLanguages);
            }
            return expectedLanguages;
        }, asyncExecutor)
        .thenCompose(languages -> {
            return getExistingLanguagesInCollection(collection)
                .thenCompose(existingLanguages -> {
                    if (config.isDebugLogging()) {
                        LOGGER.info("📋 Existing languages: " + existingLanguages);
                    }

                    Set<String> missingLanguages = new HashSet<>(expectedLanguages);
                    missingLanguages.removeAll(existingLanguages);

                    if (missingLanguages.isEmpty()) {
                        if (config.isDebugLogging()) {
                            LOGGER.info("✅ All language documents exist for collection: " + collection);
                        }
                        return CompletableFuture.completedFuture(null);
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

                    return CompletableFuture.allOf(createFutures.toArray(new CompletableFuture[0]))
                        .thenRun(() -> {
                            if (config.isDebugLogging()) {
                                LOGGER.info("🎉 Successfully created " + missingLanguages.size() + " missing language documents for collection: " + collection);
                            }
                        });
                });
        })
        .exceptionally(throwable -> {
            if (config.isVerboseLogging()) {
                LOGGER.log(Level.SEVERE, "💥 Error ensuring language documents exist for collection: " + collection, throwable);
            }
            throw new RuntimeException(throwable);
        });
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
        LOGGER.info("Change Streams: " + (config.isEnableChangeStreams() ? "✅ ENABLED" : "❌ DISABLED") + 
                   ", Active watchers: " + changeStreamWatchers.size());
        LOGGER.info("=== Configuration Status Complete ===");
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

    
    public CompletableFuture<String> getMessageAsync(String collection, String language, String key, Object... placeholders) {
        return getMessageAsync(collection, language, key)
            .thenApply(message -> {
                if (message == null) return key;
                return messageFormatter.format(message, placeholders);
            });
    }

    
    public CompletableFuture<String> getMessageAsync(String collection, String language, String key, Map<String, Object> placeholders) {
        return getMessageAsync(collection, language, key)
            .thenApply(message -> {
                if (message == null) return key;
                if (placeholders == null || placeholders.isEmpty()) return message;
                
                String result = message;
                for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
                    String placeholder = "{" + entry.getKey() + "}";
                    String value = String.valueOf(entry.getValue());
                    result = result.replace(placeholder, value);
                }
                return result;
            });
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

    public Messages findById(String id) {
        return new Messages() {
            
            @Override
            public CompletableFuture<String> get(String path) {
                return get(path, config.getDefaultLanguage());
            }

            @Override
            public CompletableFuture<String> get(String path, String language) {
                try {
                    String message = cacheManager.getMessage(id, language, path, (String) null);
                    if (message == null) {
                        message = cacheManager.getMessage(id, config.getDefaultLanguage(), path, path);
                    }
                    return CompletableFuture.completedFuture(message);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error getting message: " + id + ":" + language + ":" + path, e);
                    return CompletableFuture.completedFuture(path);
                }
            }

            @Override
            public CompletableFuture<String> get(String path, Object... placeholders) {
                return get(path, config.getDefaultLanguage(), placeholders);
            }

            @Override
            public CompletableFuture<String> get(String path, String language, Object... placeholders) {
                try {
                    String message = cacheManager.getMessage(id, language, path, (String) null);
                    if (message == null) {
                        message = cacheManager.getMessage(id, config.getDefaultLanguage(), path, path);
                    }
                    String formatted = messageFormatter.format(message, placeholders);
                    return CompletableFuture.completedFuture(formatted);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error getting message: " + id + ":" + language + ":" + path, e);
                    return CompletableFuture.completedFuture(path);
                }
            }

            @Override
            public CompletableFuture<String> get(String path, Map<String, Object> placeholders) {
                return get(path, config.getDefaultLanguage(), placeholders);
            }

            @Override
            public CompletableFuture<String> get(String path, String language, Map<String, Object> placeholders) {
                try {
                    String message = cacheManager.getMessage(id, language, path, (String) null);
                    if (message == null) {
                        message = cacheManager.getMessage(id, config.getDefaultLanguage(), path, path);
                    }
                    if (placeholders == null || placeholders.isEmpty()) {
                        return CompletableFuture.completedFuture(message);
                    }
                    
                    String result = message;
                    for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
                        String placeholder = "{" + entry.getKey() + "}";
                        String value = String.valueOf(entry.getValue());
                        result = result.replace(placeholder, value);
                    }
                    return CompletableFuture.completedFuture(result);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error getting message: " + id + ":" + language + ":" + path, e);
                    return CompletableFuture.completedFuture(path);
                }
            }

            @Override
            public CompletableFuture<List<String>> getList(String path) {
                return getList(path, config.getDefaultLanguage());
            }

            @Override
            public CompletableFuture<List<String>> getList(String path, String language) {
                try {
                    @SuppressWarnings("unchecked")
                    List<String> list = cacheManager.getMessage(id, language, path, (List<String>) null);
                    if (list == null) {
                        @SuppressWarnings("unchecked")
                        List<String> defaultList = cacheManager.getMessage(id, config.getDefaultLanguage(), path, (List<String>) null);
                        list = defaultList != null ? defaultList : java.util.Collections.singletonList(path);
                    }
                    return CompletableFuture.completedFuture(list);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error getting message list: " + id + ":" + language + ":" + path, e);
                    return CompletableFuture.completedFuture(java.util.Collections.singletonList(path));
                }
            }
        };
    }

    @Override
    public <T> CompletableFuture<T> getLanguageClass(Class<T> type, String language) {
        String collectionName = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(type);
        String requestedLanguage = (language == null || language.isBlank()) ? config.getDefaultLanguage() : language;
        return loadLanguageClass(collectionName, type, requestedLanguage);
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> getLanguageClasses(Class<T> type) {
        String collectionName = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(type);

        return getSupportedLanguages(collectionName).thenCompose(supported -> {
            Set<String> languages = new LinkedHashSet<>();
            if (supported != null) {
                languages.addAll(supported);
            }
            Set<String> annotated = xyz.wtje.mongoconfigs.api.core.Annotations.langsFrom(type);
            if (!annotated.isEmpty()) {
                languages.addAll(annotated);
            }
            languages.add(config.getDefaultLanguage());

            List<CompletableFuture<Map.Entry<String, T>>> futures = languages.stream()
                .map(lang -> getLanguageClass(type, lang)
                    .thenApply(instance -> Map.entry(lang, instance)))
                .collect(Collectors.toList());

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<String, T> result = new LinkedHashMap<>();
                    for (CompletableFuture<Map.Entry<String, T>> future : futures) {
                        Map.Entry<String, T> entry = future.join();
                        result.put(entry.getKey(), entry.getValue());
                    }
                    return result;
                });
        });
    }

    private <T> CompletableFuture<T> loadLanguageClass(String collectionName, Class<T> type, String language) {
        String normalized = (language == null || language.isBlank()) ? config.getDefaultLanguage() : language;

        return mongoManager.getLanguage(collectionName, normalized)
            .thenCompose(doc -> {
                if (doc != null && doc.getData() != null && !doc.getData().isEmpty()) {
                    collectionLanguages.computeIfAbsent(collectionName, key -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                        .add(normalized);
                    return CompletableFuture.completedFuture(mergeLanguageData(type, doc.getData()));
                }
                if (!normalized.equals(config.getDefaultLanguage())) {
                    return mongoManager.getLanguage(collectionName, config.getDefaultLanguage())
                        .thenApply(defaultDoc -> {
                            if (defaultDoc != null && defaultDoc.getData() != null && !defaultDoc.getData().isEmpty()) {
                                collectionLanguages.computeIfAbsent(collectionName, key -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                                    .add(config.getDefaultLanguage());
                                return mergeLanguageData(type, defaultDoc.getData());
                            }
                            return newLanguageInstance(type);
                        });
                }
                return CompletableFuture.completedFuture(newLanguageInstance(type));
            });
    }

    private <T> T mergeLanguageData(Class<T> type, Map<String, Object> data) {
        T base = newLanguageInstance(type);
        if (data == null || data.isEmpty()) {
            return base;
        }
        try {
            byte[] buffer = languageMapper.writeValueAsBytes(data);
            return languageMapper.readerForUpdating(base).readValue(buffer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to map language data for " + type.getName(), e);
        }
    }

    private <T> T newLanguageInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Language class must have a no-args constructor: " + type.getName(), e);
        }
    }

    public Messages getMessagesOrGenerate(Class<?> messageClass, Supplier<Void> generator) {
        String collectionName = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(messageClass);
        
        
        Messages messages = findById(collectionName);
        
        
        
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
    public <T> CompletableFuture<Void> createFromObject(T messageObject) {
        return createFromObjectAsync(messageObject);
    }

    public <T> CompletableFuture<Void> createFromObjectAsync(T messageObject) {
        String collectionName = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(messageObject.getClass());
        Set<String> supportedLanguages = xyz.wtje.mongoconfigs.api.core.Annotations.langsFrom(messageObject.getClass());
        final Set<String> langs = supportedLanguages.isEmpty() ? Set.of("en") : supportedLanguages;

        Map<String, Object> flatMessages = extractFlatMessages(messageObject);

        
        List<CompletableFuture<Void>> saveFutures = langs.stream()
            .map(language -> {
                xyz.wtje.mongoconfigs.core.model.LanguageDocument langDoc = 
                    new xyz.wtje.mongoconfigs.core.model.LanguageDocument(language, flatMessages);
                
                return mongoManager.saveLanguage(collectionName, langDoc)
                    .thenRun(() -> {
                        if (config.isDebugLogging()) {
                            LOGGER.info("Created flat messages for language: " + language + " in collection: " + collectionName);
                        }
                    })
                    .exceptionally(throwable -> {
                        LOGGER.log(Level.SEVERE, "Failed to save messages for language: " + language, throwable);
                        return null;
                    });
            })
            .collect(Collectors.toList());

        
        return CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> cacheManager.invalidateMessages(collectionName));
    }

    @Override
    public <T> CompletableFuture<Messages> getOrCreateFromObject(T messageObject) {
        return getOrCreateFromObjectAsync(messageObject);
    }

    public <T> CompletableFuture<Messages> getOrCreateFromObjectAsync(T messageObject) {
        String collectionName = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(messageObject.getClass());
        Set<String> supportedLanguages = xyz.wtje.mongoconfigs.api.core.Annotations.langsFrom(messageObject.getClass());
        final Set<String> langs = supportedLanguages.isEmpty() ? Set.of("en") : supportedLanguages;

        
        Map<String, Object> expectedKeys = extractFlatMessages(messageObject);
        
        
        List<CompletableFuture<Void>> mergeFutures = langs.stream()
            .map(language -> mergeLanguageDocument(collectionName, language, expectedKeys))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(mergeFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                if (config.isDebugLogging()) {
                    LOGGER.info("Completed merge/create for collection: " + collectionName + " with languages: " + langs);
                }
            })
            .thenApply(v -> findById(collectionName));
    }

    private Map<String, Object> extractFlatMessages(Object messageObject) {
        Map<String, Object> result = new HashMap<>();
        java.util.Set<Object> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        flattenObject(messageObject, "", result, visited, 0);
        return result;
    }

    private void flattenObject(Object obj, String prefix, Map<String, Object> out, java.util.Set<Object> visited, int depth) {
        if (obj == null) return;
        if (depth > 8) return; 
        if (visited.contains(obj)) return; 
        visited.add(obj);

        Class<?> clazz = obj.getClass();

        
        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value == null) continue;

                String key = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();

                if (isSimpleValue(value)) {
                    out.put(key, String.valueOf(value));
                } else if (value instanceof Iterable<?> iterable) {
                    out.put(key, copyToList(iterable));
                } else if (value.getClass().isArray()) {
                    out.put(key, copyArrayToList(value));
                } else if (value instanceof java.util.Map<?, ?> m) {
                    for (var e : m.entrySet()) {
                        Object k = e.getKey();
                        Object v = e.getValue();
                        if (k != null && v != null) {
                            String mapKey = key + "." + k.toString();
                            if (isSimpleValue(v)) {
                                out.put(mapKey, String.valueOf(v));
                            } else {
                                flattenObject(v, mapKey, out, visited, depth + 1);
                            }
                        }
                    }
                } else {
                    flattenObject(value, key, out, visited, depth + 1);
                }
            } catch (Exception e) {
                if (config.isDebugLogging()) {
                    LOGGER.log(Level.WARNING, "Failed to extract field: " + field.getName(), e);
                }
            }
        }

        
        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            String name = method.getName();
            if (!name.startsWith("get") || method.getParameterCount() != 0 || name.equals("getClass")) continue;
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
            try {
                method.setAccessible(true);
                Object value = method.invoke(obj);
                if (value == null) continue;

                String fieldName = name.substring(3);
                String getterKey = camelCaseToSnakeCase(fieldName);
                String key = prefix.isEmpty() ? getterKey : prefix + "." + getterKey;

                if (isSimpleValue(value)) {
                    out.put(key, String.valueOf(value));
                } else if (value instanceof Iterable<?> iterable) {
                    out.put(key, copyToList(iterable));
                } else if (value.getClass().isArray()) {
                    out.put(key, copyArrayToList(value));
                } else if (value instanceof java.util.Map<?, ?> m) {
                    for (var e : m.entrySet()) {
                        Object k = e.getKey();
                        Object v = e.getValue();
                        if (k != null && v != null) {
                            String mapKey = key + "." + k.toString();
                            if (isSimpleValue(v)) {
                                out.put(mapKey, String.valueOf(v));
                            } else {
                                flattenObject(v, mapKey, out, visited, depth + 1);
                            }
                        }
                    }
                } else {
                    flattenObject(value, key, out, visited, depth + 1);
                }
            } catch (Exception e) {
                if (config.isDebugLogging()) {
                    LOGGER.log(Level.WARNING, "Failed to extract method: " + method.getName(), e);
                }
            }
        }
    }

    private java.util.List<String> copyToList(Iterable<?> iterable) {
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        for (Object element : iterable) {
            list.add(element == null ? "null" : element.toString());
        }
        return java.util.Collections.unmodifiableList(list);
    }

    private java.util.List<String> copyArrayToList(Object array) {
        int length = java.lang.reflect.Array.getLength(array);
        java.util.ArrayList<String> list = new java.util.ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            Object element = java.lang.reflect.Array.get(array, i);
            list.add(element == null ? "null" : element.toString());
        }
        return java.util.Collections.unmodifiableList(list);
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof CharSequence ||
               value instanceof Number ||
               value instanceof Boolean ||
               value instanceof Character ||
               value.getClass().isEnum();
    }

    private String camelCaseToSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1.$2").toLowerCase();
    }

    private CompletableFuture<Void> mergeLanguageDocument(String collectionName, String language, Map<String, Object> expectedKeys) {
        return mongoManager.getLanguage(collectionName, language)
            .thenCompose(existingDoc -> {
                Map<String, Object> existingData = (existingDoc != null && existingDoc.getData() != null) 
                    ? existingDoc.getData() : new HashMap<>();
                
                
                Map<String, Object> missingKeys = new HashMap<>();
                for (Map.Entry<String, Object> entry : expectedKeys.entrySet()) {
                    String key = entry.getKey();
                    if (!hasNestedKey(existingData, key)) {
                        missingKeys.put(key, entry.getValue());
                    }
                }
                
                if (missingKeys.isEmpty()) {
                    if (config.isDebugLogging()) {
                        LOGGER.info("No missing keys for " + collectionName + ":" + language);
                    }
                    return CompletableFuture.completedFuture(null);
                }
                
                
                Map<String, Object> mergedData = new HashMap<>(existingData);
                for (Map.Entry<String, Object> entry : missingKeys.entrySet()) {
                    setNestedValue(mergedData, entry.getKey(), entry.getValue());
                }
                
                if (config.isDebugLogging()) {
                    LOGGER.info("Adding " + missingKeys.size() + " missing keys to " + collectionName + ":" + language + ": " + missingKeys.keySet());
                }
                
                
                xyz.wtje.mongoconfigs.core.model.LanguageDocument updatedDoc = 
                    new xyz.wtje.mongoconfigs.core.model.LanguageDocument(language, mergedData);
                
                return mongoManager.saveLanguage(collectionName, updatedDoc)
                    .thenRun(() -> {
                        
                        for (Map.Entry<String, Object> entry : missingKeys.entrySet()) {
                            cacheManager.putMessage(collectionName, language, entry.getKey(), entry.getValue());
                        }
                    });
            })
            .exceptionally(throwable -> {
                LOGGER.log(Level.WARNING, "Error merging language document " + collectionName + ":" + language, throwable);
                return null;
            });
    }
    
    private boolean hasNestedKey(Map<String, Object> data, String key) {
        if (!key.contains(".")) {
            return data.containsKey(key);
        }
        String[] parts = key.split("\\.", 2);
        String currentKey = parts[0];
        String remainingKey = parts[1];
        Object nested = data.get(currentKey);
        if (nested instanceof Map) {
            return hasNestedKey((Map<String, Object>) nested, remainingKey);
        }
        return false;
    }
    
    
    private void notifyReloadListeners(String collection) {
        
        Set<Consumer<String>> listeners = reloadListeners.get(collection);
        if (listeners != null && !listeners.isEmpty()) {
            LOGGER.info("🔔 Powiadamianie " + listeners.size() + " słuchaczy o odświeżeniu kolekcji: " + collection);
            for (Consumer<String> listener : listeners) {
                try {
                    listener.accept(collection);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "❌ Błąd podczas powiadamiania słuchacza o odświeżeniu kolekcji: " + collection, e);
                }
            }
        }
        
        
        Set<Consumer<String>> globalListeners = reloadListeners.get("*");
        if (globalListeners != null && !globalListeners.isEmpty()) {
            LOGGER.info("🔔 Powiadamianie " + globalListeners.size() + " globalnych słuchaczy o odświeżeniu kolekcji: " + collection);
            for (Consumer<String> listener : globalListeners) {
                try {
                    listener.accept(collection);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "❌ Błąd podczas powiadamiania globalnego słuchacza o odświeżeniu kolekcji: " + collection, e);
                }
            }
        }
    }
    
    
    public void addReloadListener(String collection, Consumer<String> listener) {
        if (collection == null || listener == null) return;
        
        reloadListeners.computeIfAbsent(collection, k -> ConcurrentHashMap.newKeySet())
                       .add(listener);
        LOGGER.info("➕ Dodano nowego słuchacza odświeżenia dla kolekcji: " + collection);
    }
    
    
    public void removeReloadListener(String collection, Consumer<String> listener) {
        if (collection == null || listener == null) return;
        
        Set<Consumer<String>> listeners = reloadListeners.get(collection);
        if (listeners != null) {
            listeners.remove(listener);
            LOGGER.info("➖ Usunięto słuchacza odświeżenia dla kolekcji: " + collection);
        }
    }
}

