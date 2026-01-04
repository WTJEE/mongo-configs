package xyz.wtje.mongoconfigs.core.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.Document;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.mongoconfigs.core.model.ConfigDocument;
import xyz.wtje.mongoconfigs.core.model.LanguageDocument;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class MongoManager {

    private static final Logger LOGGER = Logger.getLogger(MongoManager.class.getName());

    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final MongoConfig config;
    private final ExecutorService executorService;

    public MongoManager(MongoConfig config) {
        this.config = config;

        System.setProperty("io.netty.eventLoopThreads", String.valueOf(config.getIoThreads()));

        // Create daemon ForkJoinPool threads
        ForkJoinPool.ForkJoinWorkerThreadFactory daemonFactory = pool -> {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setDaemon(true);
            thread.setName("MongoManager-ForkJoin-" + thread.getPoolIndex());
            return thread;
        };

        this.executorService = new ForkJoinPool(
            config.getWorkerThreads(),
            daemonFactory,
            null,
            true
        );

        String connectionStr = config.getConnectionString();
        if (config.isVerboseLogging() || config.isDebugLogging()) {
            LOGGER.info("MongoManager: Creating client with connection string: " +
                (connectionStr.contains("@") ?
                    connectionStr.replaceAll("://[^@]*@", "://***@") :
                    connectionStr));
        }

        ConnectionString connectionString = new ConnectionString(connectionStr);

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToConnectionPoolSettings(builder -> {
                    builder.maxSize(config.getMaxPoolSize())
                           .minSize(config.getMinPoolSize())
                           .maxConnectionIdleTime(config.getMaxConnectionIdleTime(), TimeUnit.MILLISECONDS)
                           .maxConnectionLifeTime(config.getMaxConnectionLifeTime(), TimeUnit.MILLISECONDS);
                })
                .applyToSocketSettings(builder -> {
                    builder.connectTimeout(config.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                           .readTimeout(config.getSocketTimeoutMs(), TimeUnit.MILLISECONDS);
                })
                .applyToServerSettings(builder -> {
                    builder.heartbeatFrequency(10, TimeUnit.SECONDS)
                           .minHeartbeatFrequency(500, TimeUnit.MILLISECONDS);
                })
                .applyToClusterSettings(builder -> {
                    builder.serverSelectionTimeout(config.getServerSelectionTimeoutMs(), TimeUnit.MILLISECONDS);
                })
                .build();

        this.mongoClient = MongoClients.create(settings);
        this.database = mongoClient.getDatabase(config.getDatabase());

        if (config.isVerboseLogging() || config.isDebugLogging()) {
            LOGGER.info("MongoDB connection initialized for database: " + config.getDatabase());
            LOGGER.info("Configured with " + config.getIoThreads() + " I/O threads and " + 
                       config.getWorkerThreads() + " worker threads");
        }
    }

    public CompletableFuture<ConfigDocument> getConfig(String collection) {
        MongoCollection<Document> coll = database.getCollection(collection);

        // Najpierw szukaj dokumentu z _id: "config" (tradycyjny format)
        return PublisherAdapter.toCompletableFuture(
            coll.find(Filters.eq("_id", "config")).first()
        ).thenCompose(doc -> {
            if (doc != null) {
                return CompletableFuture.completedFuture(ConfigDocument.fromDocument(doc));
            }
            
            // Fallback: szukaj dokumentu z _id równym nazwie kolekcji
            return PublisherAdapter.toCompletableFuture(
                coll.find(Filters.eq("_id", collection)).first()
            ).thenCompose(collectionIdDoc -> {
                if (collectionIdDoc != null) {
                    return CompletableFuture.completedFuture(ConfigDocument.fromDocument(collectionIdDoc));
                }
                
                // Ostatni fallback: znajdź dokument bez pola "lang" (config-only document)
                return PublisherAdapter.toCompletableFuture(
                    coll.find(Filters.exists("lang", false)).first()
                ).thenApply(configOnlyDoc -> configOnlyDoc != null ? ConfigDocument.fromDocument(configOnlyDoc) : null);
            });
        });
    }

    public CompletableFuture<Void> saveConfig(String collection, ConfigDocument config) {
        MongoCollection<Document> coll = database.getCollection(collection);
        config.updateTimestamp();

        Document docToSave = config.toDocument();
        docToSave.remove("_id");
        docToSave.put("_id", "config");

        return PublisherAdapter.toCompletableFuture(
            coll.replaceOne(
                Filters.eq("_id", "config"),
                docToSave,
                new ReplaceOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    public CompletableFuture<LanguageDocument> getLanguage(String collection, String language) {
        MongoCollection<Document> coll = database.getCollection(collection);

        return PublisherAdapter.toCompletableFuture(
            coll.find(Filters.eq("lang", language)).first()
        ).thenApply(doc -> doc != null ? LanguageDocument.fromDocument(doc) : null);
    }

    public CompletableFuture<Void> saveLanguage(String collection, LanguageDocument languageDoc) {
        MongoCollection<Document> coll = database.getCollection(collection);
        languageDoc.updateTimestamp();

        Document docToSave = languageDoc.toDocument();
        docToSave.remove("_id");

        return PublisherAdapter.toCompletableFuture(
            coll.replaceOne(
                Filters.eq("lang", languageDoc.getLang()),
                docToSave,
                new ReplaceOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    public CompletableFuture<Boolean> collectionExists(String collection) {
        return PublisherAdapter.toCompletableFutureList(
            database.listCollectionNames()
        ).thenApply(names -> names.contains(collection));
    }

    public CompletableFuture<Boolean> collectionExists(String databaseName, String collection) {
        MongoDatabase targetDatabase = mongoClient.getDatabase(databaseName);
        return PublisherAdapter.toCompletableFutureList(
            targetDatabase.listCollectionNames()
        ).thenApply(names -> names.contains(collection));
    }

    public CompletableFuture<Void> createCollection(String collection) {
        return PublisherAdapter.toCompletableFutureVoid(
            database.createCollection(collection)
        );
    }

    public CompletableFuture<Void> createCollection(String databaseName, String collection) {
        MongoDatabase targetDatabase = mongoClient.getDatabase(databaseName);
        return PublisherAdapter.toCompletableFutureVoid(
            targetDatabase.createCollection(collection)
        );
    }

    public CompletableFuture<Void> dropCollection(String collection) {
        return PublisherAdapter.toCompletableFutureVoid(
            database.getCollection(collection).drop()
        );
    }

    public CompletableFuture<Long> countDocuments(String collection) {
        return PublisherAdapter.toCompletableFuture(
            database.getCollection(collection).countDocuments()
        );
    }

    public CompletableFuture<DeleteResult> deleteDocument(String collection, String id) {
        return PublisherAdapter.toCompletableFuture(
            database.getCollection(collection).deleteOne(Filters.eq("_id", id))
        );
    }

    public MongoClient getClient() {
        return mongoClient;
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public MongoCollection<Document> getCollection(String collection) {
        return database.getCollection(collection);
    }

    public MongoCollection<Document> getReactiveCollection(String collection) {
        return database.getCollection(collection);
    }

    public MongoCollection<Document> getCollection(String databaseName, String collection) {
        return mongoClient.getDatabase(databaseName).getCollection(collection);
    }

    public MongoDatabase getDatabase(String databaseName) {
        return mongoClient.getDatabase(databaseName);
    }

    public MongoCollection<Document> getCollectionForType(Class<?> type) {
        String dbName = xyz.wtje.mongoconfigs.api.core.Annotations.databaseFrom(type);
        String collName = xyz.wtje.mongoconfigs.api.core.Annotations.collectionFrom(type);

        if (dbName != null) {
            return mongoClient.getDatabase(dbName).getCollection(collName);
        } else {
            return database.getCollection(collName);
        }
    }

    public java.util.Set<String> getMongoCollections() {
        try {
            if (config.isDebugLogging()) {
                LOGGER.info("Starting getMongoCollections() - listing all collections from MongoDB...");
            }

            // Fallback synchroniczny - dla zachowania kompatybilności
            // Używaj getMongoCollectionsAsync dla pełnego async
            java.util.Set<String> collections = new java.util.HashSet<>();

            java.util.List<String> collectionList = PublisherAdapter.toCompletableFutureList(
                database.listCollectionNames()
            ).join();

            collections.addAll(collectionList);

            if (config.isDebugLogging()) {
                LOGGER.info("Found " + collections.size() + " collections in MongoDB: " + collections);

                for (String collection : collections) {
                    LOGGER.info("Collection: " + collection);
                }
            }

            if (collections.isEmpty() && config.isVerboseLogging()) {
                LOGGER.warning("No collections found in MongoDB! This might indicate:");
                LOGGER.warning("1. Database is empty");
                LOGGER.warning("2. Connection issues");
                LOGGER.warning("3. Wrong database name");
                LOGGER.warning("4. Collections were not created yet");
            }

            return collections;

        } catch (Exception e) {
            LOGGER.warning("Error listing MongoDB collections: " + e.getMessage());
            if (config.isDebugLogging()) {
                LOGGER.log(java.util.logging.Level.WARNING, "Exception details: ", e);
            }
            return java.util.Set.of();
        }
    }

    public CompletableFuture<java.util.Set<String>> getMongoCollectionsAsync() {
        if (config.isDebugLogging()) {
            LOGGER.info("Starting getMongoCollectionsAsync() - listing all collections from MongoDB...");
        }

        return PublisherAdapter.toCompletableFutureList(database.listCollectionNames())
            .thenApply(collectionList -> {
                java.util.Set<String> collections = new java.util.HashSet<>(collectionList);

                if (config.isDebugLogging()) {
                    LOGGER.info("Found " + collections.size() + " collections in MongoDB: " + collections);

                    for (String collection : collections) {
                        LOGGER.info("Collection: " + collection);
                    }
                }

                if (collections.isEmpty() && config.isVerboseLogging()) {
                    LOGGER.warning("No collections found in MongoDB! This might indicate:");
                    LOGGER.warning("1. Database is empty");
                    LOGGER.warning("2. Connection issues");
                    LOGGER.warning("3. Wrong database name");
                    LOGGER.warning("4. Collections were not created yet");
                }

                return collections;
            })
            .exceptionally(throwable -> {
                LOGGER.warning("Error listing MongoDB collections: " + throwable.getMessage());
                if (config.isDebugLogging()) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Exception details: ", throwable);
                }
                return java.util.Set.of();
            });
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public int getConfiguredIoThreads() {
        return config.getIoThreads();
    }

    public int getConfiguredWorkerThreads() {
        return config.getWorkerThreads();
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            LOGGER.info("MongoDB connection closed");
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
                LOGGER.info("MongoDB executor service closed");
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

