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
        
        this.executorService = new ForkJoinPool(
            config.getWorkerThreads(),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true
        );
        
        String connectionStr = config.getConnectionString();
        LOGGER.info("MongoManager: Creating client with connection string: " + 
            (connectionStr.contains("@") ? 
                connectionStr.replaceAll("://[^@]+@", "://***:***@") : 
                connectionStr));
        
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
        
        LOGGER.info("MongoDB connection initialized for database: " + config.getDatabase());
        LOGGER.info("Configured with " + config.getIoThreads() + " I/O threads and " + 
                   config.getWorkerThreads() + " worker threads");
    }

    public CompletableFuture<ConfigDocument> getConfig(String collection) {
        MongoCollection<Document> coll = database.getCollection(collection);
        
        return PublisherAdapter.toCompletableFuture(
            coll.find(Filters.eq("_id", "config")).first()
        ).thenApply(doc -> doc != null ? ConfigDocument.fromDocument(doc) : null);
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
    
    public CompletableFuture<Void> createCollection(String collection) {
        return PublisherAdapter.toCompletableFutureVoid(
            database.createCollection(collection)
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
    
    public MongoCollection<Document> getCollection(String databaseName, String collection) {
        return mongoClient.getDatabase(databaseName).getCollection(collection);
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