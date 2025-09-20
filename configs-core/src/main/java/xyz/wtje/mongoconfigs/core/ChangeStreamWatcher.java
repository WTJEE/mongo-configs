package xyz.wtje.mongoconfigs.core;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import xyz.wtje.mongoconfigs.core.cache.CacheManager;

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
import java.util.Date;

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
    
    // Polling-based change detection (fallback for non-replica sets)
    private volatile boolean usePolling = false;
    private volatile ObjectId lastPolledId;
    private static final long POLLING_INTERVAL_MS = 3000; // 3 seconds - faster detection

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

    private static final int MAX_RECONNECT_ATTEMPTS = 3; // Mniej prób, szybsze wykrycie problemów
    private static final int BASE_DELAY_MS = 1000; // 1 sekunda base delay

    public void start() {
        if (running) return;
        running = true;
        
        // Initialize lastPolledId for polling
        try {
            // Get the most recent document's ObjectId for polling baseline
            collection.find()
                    .sort(new Document("_id", -1))
                    .limit(1)
                    .subscribe(new Subscriber<Document>() {
                        @Override
                        public void onSubscribe(Subscription s) { s.request(1); }
                        
                        @Override
                        public void onNext(Document doc) {
                            Object id = doc.get("_id");
                            if (id instanceof ObjectId) {
                                lastPolledId = (ObjectId) id;
                            }
                        }
                        
                        @Override
                        public void onError(Throwable t) {
                            LOGGER.log(Level.WARNING, "Failed to get baseline ObjectId for polling", t);
                        }
                        
                        @Override
                        public void onComplete() {}
                    });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error initializing polling baseline", e);
        }

        LOGGER.info("🚀 Starting ChangeStreamWatcher for collection: " + collectionName);
        
        performInitialLoad();
        
        // Try Change Streams first, fallback to polling if it fails
        try {
            startChangeStream();
        } catch (Exception e) {
            LOGGER.info("⚠️ Change Streams failed for " + collectionName + ", switching to polling mode");
            usePolling = true;
            startPolling();
        }
    }

    public void stop() {
        running = false;
        usePolling = false;
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
        LOGGER.info("🔗 Setting up change stream monitoring for collection: " + collectionName);
        
        // USUNIĘTO FILTRY! Teraz wykrywa WSZYSTKIE operacje
        var changeStream = collection.watch()
                .fullDocument(FullDocument.UPDATE_LOOKUP);

        BsonDocument token = resumeToken.get();
        if (token != null) {
            changeStream = changeStream.resumeAfter(token);
            LOGGER.info("🔄 Resuming change stream from token for collection: " + collectionName);
        }

        changeStream.subscribe(new Subscriber<ChangeStreamDocument<Document>>() {
            @Override
            public void onSubscribe(Subscription s) {
                changeStreamSubscription = s;
                s.request(Long.MAX_VALUE);
                LOGGER.info("✅ Successfully subscribed to change stream for collection: " + collectionName);
            }

            @Override
            public void onNext(ChangeStreamDocument<Document> event) {
                try {
                    processChangeEvent(event);
                    reconnectAttempts = 0; // Reset attempts on successful event
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "❌ Error processing change event for " + collectionName, e);
                }
            }

            @Override
            public void onError(Throwable t) {
                String errorMsg = t.getMessage();
                if (errorMsg != null && (errorMsg.contains("only supported on replica sets") || 
                                       errorMsg.contains("not supported") ||
                                       errorMsg.contains("replica set"))) {
                    LOGGER.info("📡 Change Streams not supported (no replica set), switching to polling for: " + collectionName);
                    usePolling = true;
                    startPolling();
                } else {
                    LOGGER.log(Level.WARNING, "💥 Error in change stream for collection: " + collectionName, t);
                    if (running) {
                        scheduleReconnect();
                    }
                }
            }

            @Override
            public void onComplete() {
                LOGGER.info("⚡ Change stream completed for collection: " + collectionName);
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
        
        var operationType = event.getOperationType();
        if (operationType == null) return;
        
        String opType = operationType.getValue();
        var documentKey = event.getDocumentKey();

        LOGGER.info("🔄 Processing change event: " + opType + " in collection: " + collectionName);

        // Handle document key extraction (support different ID types)
        String docId = null;
        if (documentKey != null) {
            var idValue = documentKey.get("_id");
            if (idValue != null) {
                if (idValue.isString()) {
                    docId = idValue.asString().getValue();
                } else if (idValue.isObjectId()) {
                    docId = idValue.asObjectId().getValue().toString();
                } else {
                    docId = idValue.toString();
                }
            }
        }

        // Process all change types
        switch (opType) {
            case "insert":
            case "update":
            case "replace":
                var fullDocument = event.getFullDocument();
                if (fullDocument != null && docId != null) {
                    cache.put(docId, fullDocument);
                }
                triggerCacheReload("📝 Document " + opType + " detected");
                break;
                
            case "delete":
                if (docId != null) {
                    cache.remove(docId);
                }
                triggerCacheReload("🗑️ Document deletion detected");
                break;
                
            case "drop":
                cache.clear();
                triggerCacheReload("💥 Collection dropped");
                break;
                
            case "rename":
                cache.clear();
                triggerCacheReload("📛 Collection renamed");
                break;
                
            case "dropDatabase":
                cache.clear();
                triggerCacheReload("💀 Database dropped");
                break;
                
            default:
                triggerCacheReload("🔄 Other operation: " + opType);
                break;
        }
    }

    /**
     * Trigger cache reload with consistent error handling
     */
    private void triggerCacheReload(String reason) {
        if (cacheManager != null && reloadCallback != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    LOGGER.info("🎯 " + reason + " - refreshing cache for: " + collectionName);
                    cacheManager.invalidateCollection(collectionName);
                    reloadCallback.accept(collectionName);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "💥 CRITICAL: Cache reload failed for " + collectionName, e);
                }
            });
        } else {
            LOGGER.warning("⚠️ No cache manager or reload callback set for collection: " + collectionName);
        }
    }

    private void scheduleReconnect() {
        if (!running || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            LOGGER.warning("🚫 Change stream reconnection failed for collection: " + collectionName + 
                          " after " + reconnectAttempts + " attempts. Switching to polling mode.");
            usePolling = true;
            startPolling();
            return;
        }

        reconnectAttempts++;
        long delay = calculateBackoffDelay();
        
        LOGGER.info("🔄 Scheduling reconnect attempt " + reconnectAttempts + " for collection: " + 
                   collectionName + " in " + delay + "ms");

        scheduler.schedule(this::startChangeStream, delay, TimeUnit.MILLISECONDS);
    }

    private long calculateBackoffDelay() {
        long exponentialDelay = BASE_DELAY_MS * (1L << Math.min(reconnectAttempts - 1, 10));
        long jitter = (long) (Math.random() * exponentialDelay * 0.1);
        return exponentialDelay + jitter;
    }

    /**
     * Polling-based change detection for MongoDB without replica sets
     * Uses ObjectId timestamp comparison instead of non-existent updatedAt field
     */
    private void startPolling() {
        if (!running || !usePolling) return;
        
        scheduler.scheduleWithFixedDelay(() -> {
            if (!running || !usePolling) return;
            
            try {
                checkForChanges();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "🚨 Error during polling check for collection: " + collectionName, e);
            }
        }, POLLING_INTERVAL_MS, POLLING_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        LOGGER.info("🔍 Started polling for changes in collection: " + collectionName + 
                   " (every " + (POLLING_INTERVAL_MS/1000) + "s)");
    }
    
    /**
     * Check for changes using ObjectId timestamp comparison
     * This is more reliable than checking non-existent updatedAt fields
     */
    private void checkForChanges() {
        // Query for documents with _id greater than our last known ID
        Document query = new Document();
        if (lastPolledId != null) {
            query.append("_id", new Document("$gt", lastPolledId));
        }
        
        // Find newest documents first
        collection.find(query)
                .sort(new Document("_id", -1))
                .limit(100) // Limit to avoid overwhelming
                .subscribe(new Subscriber<Document>() {
                    private ObjectId newestId = lastPolledId;
                    private int changesDetected = 0;
                    
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(Document doc) {
                        Object idObj = doc.get("_id");
                        if (idObj instanceof ObjectId) {
                            ObjectId docId = (ObjectId) idObj;
                            if (newestId == null || docId.compareTo(newestId) > 0) {
                                newestId = docId;
                            }
                        }
                        
                        String idStr = idObj != null ? idObj.toString() : "unknown";
                        cache.put(idStr, doc);
                        changesDetected++;
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOGGER.log(Level.WARNING, "🚨 Error during polling query for: " + collectionName, t);
                    }

                    @Override
                    public void onComplete() {
                        // Update our polling baseline
                        if (newestId != null) {
                            lastPolledId = newestId;
                        }
                        
                        // Trigger reload if changes detected
                        if (changesDetected > 0) {
                            LOGGER.info("🔄 Polling detected " + changesDetected + " changes in: " + collectionName);
                            triggerCacheReload("📊 Polling detected " + changesDetected + " changes");
                        }
                    }
                });
    }

    public void triggerReload(String id) {
        try {
            // Manually trigger a change detection for testing/debugging
            LOGGER.info("🔧 Manual reload triggered for document: " + id + " in collection: " + collectionName);
            triggerCacheReload("🔧 Manual reload requested");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error in manual reload trigger", e);
        }
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
    
    /**
     * Get current status for debugging
     */
    public String getStatus() {
        return String.format("Collection: %s, Running: %s, UsePolling: %s, Cache size: %d, Reconnect attempts: %d", 
                           collectionName, running, usePolling, cache.size(), reconnectAttempts);
    }
    
    /**
     * Force switch to polling mode (for testing/debugging)
     */
    public void forcePollingMode() {
        LOGGER.info("🔄 Forcing polling mode for collection: " + collectionName);
        if (changeStreamSubscription != null) {
            changeStreamSubscription.cancel();
        }
        usePolling = true;
        startPolling();
    }
}
