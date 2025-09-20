package xyz.wtje.mongoconfigs.core;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import xyz.wtje.mongoconfigs.core.cache.CacheManager;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChangeStreamWatcher {
    private static final Logger LOGGER = Logger.getLogger(ChangeStreamWatcher.class.getName());

    private final MongoCollection<Document> collection;
    private final String collectionName;
    private final ConcurrentMap<String, Document> cache = new ConcurrentHashMap<>();
    private final AtomicReference<BsonDocument> resumeToken = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    // Integration with CacheManager
    private CacheManager cacheManager;
    private Consumer<String> reloadCallback;

    private volatile boolean running = false;
    private volatile Subscription changeStreamSubscription;

    public ChangeStreamWatcher(MongoCollection<Document> collection) {
        this.collection = collection;
        this.collectionName = collection.getNamespace().getCollectionName();
    }
    
    public ChangeStreamWatcher(MongoCollection<Document> collection, CacheManager cacheManager) {
        this.collection = collection;
        this.collectionName = collection.getNamespace().getCollectionName();
        this.cacheManager = cacheManager;
    }
    
    /**
     * Set callback for when collection needs reloading
     */
    public void setReloadCallback(Consumer<String> reloadCallback) {
        this.reloadCallback = reloadCallback;
    }
    private volatile int reconnectAttempts = 0;

    private static final int MAX_RECONNECT_ATTEMPTS = 5; // Zmniejszone z 10
    private static final int BASE_DELAY_MS = 500; // Zmniejszone z 1000

    public void start() {
        if (running) return;
        running = true;

        LOGGER.info("Starting ChangeStreamWatcher for collection: " + collectionName);
        
        performInitialLoad();
        startChangeStream();
    }

    public void stop() {
        running = false;
        if (changeStreamSubscription != null) {
            changeStreamSubscription.cancel();
        }
        scheduler.shutdown();
    }

    private void performInitialLoad() {
        collection.find().subscribe(new Subscriber<Document>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Document doc) {
                String id = doc.getString("_id");
                if (id != null) {
                    cache.put(id, doc);
                }
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }

            @Override
            public void onComplete() {
                LOGGER.info("Initial load completed. Cached " + cache.size() + " documents for collection: " + collectionName);
            }
        });
    }

    private void startChangeStream() {
        LOGGER.info("Setting up change stream monitoring for collection: " + collectionName);
        
        var pipeline = List.of(
                Aggregates.match(
                        Filters.in("operationType", List.of("insert", "update", "replace", "delete"))
                )
        );

        var changeStream = collection.watch(pipeline)
                .fullDocument(FullDocument.UPDATE_LOOKUP);

        BsonDocument token = resumeToken.get();
        if (token != null) {
            changeStream = changeStream.resumeAfter(token);
            LOGGER.info("Resuming change stream from token for collection: " + collectionName);
        }

        changeStream.subscribe(new Subscriber<ChangeStreamDocument<Document>>() {
            @Override
            public void onSubscribe(Subscription s) {
                changeStreamSubscription = s;
                s.request(Long.MAX_VALUE);
                LOGGER.info("Successfully subscribed to change stream for collection: " + collectionName);
            }

            @Override
            public void onNext(ChangeStreamDocument<Document> event) {
                try {
                    processChangeEvent(event);
                    reconnectAttempts = 0;
                } catch (Exception e) {
                    // Zmniejszone logowanie błędów żeby nie spamować
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "Error processing change event for " + collectionName, e);
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.log(Level.WARNING, "Error in change stream for collection: " + collectionName, t);
                if (running) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onComplete() {
                LOGGER.info("Change stream completed for collection: " + collectionName);
                if (running) {
                    scheduleReconnect();
                }
            }
        });
    }

    private void processChangeEvent(ChangeStreamDocument<Document> event) {
        BsonDocument token = event.getResumeToken();
        if (token != null) {
            resumeToken.set(token);
        }
        var operationType = event.getOperationType().getValue();
        var documentKey = event.getDocumentKey();

        if (documentKey == null) return;

        org.bson.BsonValue idValue = documentKey.get("_id");
        if (idValue == null || !idValue.isString()) return;
        String id = idValue.asString().getValue();

        LOGGER.info("Processing change event: " + operationType + " for document: " + id + " in collection: " + collectionName);

        switch (operationType) {
            case "insert":
            case "update":
            case "replace":
                var fullDocument = event.getFullDocument();
                if (fullDocument != null) {
                    cache.put(id, fullDocument);
                    
                    // Invalidate and reload cache for this collection (async)
                    if (cacheManager != null && reloadCallback != null) {
                        CompletableFuture.runAsync(() -> {
                            try {
                                if (LOGGER.isLoggable(Level.FINE)) {
                                    LOGGER.fine("Invalidating cache for collection: " + collectionName);
                                }
                                cacheManager.invalidateCollection(collectionName);
                                reloadCallback.accept(collectionName);
                            } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "Error in change stream callback for: " + collectionName, e);
                            }
                        });
                    }
                }
                break;
            case "delete":
                cache.remove(id);
                
                // Invalidate cache for this collection (async)
                if (cacheManager != null && reloadCallback != null) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            if (LOGGER.isLoggable(Level.FINE)) {
                                LOGGER.fine("Invalidating cache after deletion: " + collectionName);
                            }
                            cacheManager.invalidateCollection(collectionName);
                            reloadCallback.accept(collectionName);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error in change stream callback for: " + collectionName, e);
                        }
                    });
                }
                break;
        }
    }

    private void scheduleReconnect() {
        if (!running || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            LOGGER.warning("Change stream reconnection failed for collection: " + collectionName + " after " + reconnectAttempts + " attempts");
            running = false;
            return;
        }

        reconnectAttempts++;
        long delay = calculateBackoffDelay();
        
        LOGGER.info("Scheduling reconnect attempt " + reconnectAttempts + " for collection: " + collectionName + " in " + delay + "ms");

        scheduler.schedule(this::startChangeStream, delay, TimeUnit.MILLISECONDS);
    }

    private long calculateBackoffDelay() {
        long exponentialDelay = BASE_DELAY_MS * (1L << Math.min(reconnectAttempts - 1, 10));
        long jitter = (long) (Math.random() * exponentialDelay * 0.1);
        return exponentialDelay + jitter;
    }

    public void triggerReload(String id) {
        var update = new Document("$currentDate", new Document("updatedAt", true))
                .append("$inc", new Document("_version", 1));

        Asyncs.one(collection.updateOne(new Document("_id", id), update));
    }

    public Document getCachedDocument(String id) {
        return cache.get(id);
    }

    public boolean hasDocument(String id) {
        return cache.containsKey(id);
    }

    public int getCacheSize() {
        return cache.size();
    }
}
