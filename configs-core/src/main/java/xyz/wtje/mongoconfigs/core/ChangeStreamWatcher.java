package xyz.wtje.mongoconfigs.core;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import xyz.wtje.mongoconfigs.core.cache.CacheManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
    
    // Polling-based change detection (fallback for non-replica sets)
    private volatile boolean usePolling = false;
    private volatile long lastPollingCheck = 0; // Use timestamp instead of ObjectId
    private volatile int lastKnownDocumentCount = 0; // Track document count
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
        
        // Initialize polling baseline
        lastPollingCheck = System.currentTimeMillis();

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
        // Get initial document count
        collection.countDocuments().subscribe(new Subscriber<Long>() {
            @Override
            public void onSubscribe(Subscription s) { s.request(1); }
            
            @Override
            public void onNext(Long count) {
                lastKnownDocumentCount = count.intValue();
                LOGGER.info("📊 Initial document count for " + collectionName + ": " + count);
            }
            
            @Override
            public void onError(Throwable t) {
                LOGGER.log(Level.WARNING, "Error getting initial document count", t);
                lastKnownDocumentCount = 0;
            }
            
            @Override
            public void onComplete() {}
        });
        
        // Load documents into cache
        collection.find().subscribe(new Subscriber<Document>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Document doc) {
                String id = doc.get("_id") != null ? doc.get("_id").toString() : "unknown";
                cache.put(id, doc);
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.log(Level.WARNING, "Error during initial load", t);
            }

            @Override
            public void onComplete() {
                LOGGER.info("✅ Initial load completed. Cached " + cache.size() + " documents for collection: " + collectionName);
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
                applyDocumentToCache(fullDocument, docId);
                triggerCacheReload("Document " + opType + " detected");
                break;

            case "delete": {
                Document removedDocument = docId != null ? cache.remove(docId) : null;
                handleDocumentDeletion(removedDocument, docId);
                triggerCacheReload("Document deletion detected");
                break;
            }

            case "drop":
                cache.clear();
                triggerCacheReload("Collection dropped");
                break;

            case "rename":
                cache.clear();
                triggerCacheReload("Collection renamed");
                break;

            case "dropDatabase":
                cache.clear();
                triggerCacheReload("Database dropped");
                break;

            default:
                triggerCacheReload("Other operation: " + opType);
                break;
        }
    }

    private void applyDocumentToCache(Document fullDocument, String docId) {
        if (cacheManager == null || fullDocument == null) {
            return;
        }
        try {
            if (isConfigDocument(docId, fullDocument)) {
                Map<String, Object> configData = copyDocumentExcluding(fullDocument, Set.of("_id", "updatedAt"));
                cacheManager.replaceConfigData(collectionName, configData);
                return;
            }

            String lang = fullDocument.getString("lang");
            if (lang != null && !lang.isEmpty()) {
                Map<String, Object> messageData = copyDocumentExcluding(fullDocument, Set.of("_id", "lang", "updatedAt"));
                cacheManager.replaceLanguageData(collectionName, lang, messageData);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error applying change stream document to cache for collection: " + collectionName, e);
        }
    }

    private void handleDocumentDeletion(Document previousDocument, String docId) {
        if (cacheManager == null) {
            return;
        }
        try {
            if (previousDocument != null) {
                String lang = previousDocument.getString("lang");
                if (lang != null && !lang.isEmpty()) {
                    cacheManager.replaceLanguageData(collectionName, lang, null);
                    return;
                }
                if (isConfigDocument(docId, previousDocument)) {
                    cacheManager.replaceConfigData(collectionName, null);
                    return;
                }
            }

            if ("config".equals(docId)) {
                cacheManager.replaceConfigData(collectionName, null);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error applying cache removal for collection: " + collectionName, e);
        }
    }

    private boolean isConfigDocument(String docId, Document document) {
        if ("config".equals(docId)) {
            return true;
        }
        if (document == null) {
            return false;
        }
        Object idValue = document.get("_id");
        return idValue != null && "config".equals(String.valueOf(idValue));
    }

    private Map<String, Object> copyDocumentExcluding(Document source, Set<String> keysToSkip) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (!keysToSkip.contains(entry.getKey())) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
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
     * Check for changes using document count and content hash comparison
     * This detects updates, inserts, and deletes without requiring updatedAt fields
     */
    private void checkForChanges() {
        long currentTime = System.currentTimeMillis();
        
        // Get current document count
        collection.countDocuments().subscribe(new Subscriber<Long>() {
            @Override
            public void onSubscribe(Subscription s) { s.request(1); }

            @Override
            public void onNext(Long currentCount) {
                boolean countChanged = (currentCount.intValue() != lastKnownDocumentCount);
                
                if (countChanged) {
                    // Document count changed - definitely a change
                    lastKnownDocumentCount = currentCount.intValue();
                    LOGGER.info("🔄 Document count changed in " + collectionName + 
                               " (now: " + currentCount + ") - triggering reload");
                    triggerCacheReload("📊 Document count changed: " + currentCount);
                    lastPollingCheck = currentTime;
                } else {
                    // Count same - check for updates by comparing a sample of documents
                    checkForDocumentUpdates(currentTime);
                }
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.log(Level.WARNING, "🚨 Error checking document count for: " + collectionName, t);
                // Fallback - trigger reload anyway in case of error
                triggerCacheReload("🚨 Error checking count - forcing reload");
                lastPollingCheck = currentTime;
            }

            @Override
            public void onComplete() {}
        });
    }
    
    /**
     * Check for document updates by comparing content
     */
    private void checkForDocumentUpdates(long currentTime) {
        // Sample a few documents and compare with cache
        collection.find()
                .limit(20) // Check only first 20 docs for performance
                .subscribe(new Subscriber<Document>() {
                    private boolean changesDetected = false;
                    
                    @Override
                    public void onSubscribe(Subscription s) { s.request(Long.MAX_VALUE); }

                    @Override
                    public void onNext(Document doc) {
                        String docId = doc.get("_id") != null ? doc.get("_id").toString() : "unknown";
                        Document cachedDoc = cache.get(docId);
                        
                        // If document not in cache or content changed
                        if (cachedDoc == null || !documentsEqual(doc, cachedDoc)) {
                            cache.put(docId, doc);
                            changesDetected = true;
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOGGER.log(Level.WARNING, "🚨 Error during document update check for: " + collectionName, t);
                    }

                    @Override
                    public void onComplete() {
                        if (changesDetected) {
                            LOGGER.info("🔄 Document updates detected in: " + collectionName);
                            triggerCacheReload("📝 Document content updates detected");
                        }
                        lastPollingCheck = currentTime;
                    }
                });
    }
    
    /**
     * Compare two documents for equality (ignoring _id field)
     */
    private boolean documentsEqual(Document doc1, Document doc2) {
        if (doc1 == null && doc2 == null) return true;
        if (doc1 == null || doc2 == null) return false;
        
        // Create copies without _id for comparison
        Document copy1 = new Document(doc1);
        Document copy2 = new Document(doc2);
        copy1.remove("_id");
        copy2.remove("_id");
        
        return copy1.equals(copy2);
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
