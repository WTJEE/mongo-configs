package xyz.wtje.mongoconfigs.core;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import xyz.wtje.mongoconfigs.core.cache.CacheManager;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final ScheduledExecutorService scheduler;
    
    // Configuration - configurable via MongoConfig
    private final long pollingIntervalMs;
    private final int maxReconnectAttempts;
    private final int baseDelayMs;
    private final long shutdownTimeoutMs;
    
    private CacheManager cacheManager;
    private Consumer<String> reloadCallback;
    
    // Shutdown flag to prevent operations after close
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    private volatile boolean usePolling = false;
    private volatile long lastPollingCheck = 0; 
    private volatile int lastKnownDocumentCount = 0; 

    private volatile boolean running = false;
    private volatile Subscription changeStreamSubscription;
    private volatile int reconnectAttempts = 0;

    /**
     * Creates a ChangeStreamWatcher with default configuration values.
     */
    public ChangeStreamWatcher(MongoCollection<Document> collection) {
        this(collection, null, null);
    }
    
    /**
     * Creates a ChangeStreamWatcher with a CacheManager and default configuration.
     */
    public ChangeStreamWatcher(MongoCollection<Document> collection, CacheManager cacheManager) {
        this(collection, cacheManager, null);
    }
    
    /**
     * Creates a ChangeStreamWatcher with full configuration support.
     */
    public ChangeStreamWatcher(MongoCollection<Document> collection, CacheManager cacheManager, MongoConfig config) {
        this.collection = collection;
        this.collectionName = collection.getNamespace().getCollectionName();
        this.cacheManager = cacheManager;
        
        // Use config values or defaults
        if (config != null) {
            this.pollingIntervalMs = config.getChangeStreamPollingIntervalMs();
            this.maxReconnectAttempts = config.getChangeStreamMaxReconnectAttempts();
            this.baseDelayMs = config.getChangeStreamBaseDelayMs();
            this.shutdownTimeoutMs = config.getShutdownTimeoutMs();
        } else {
            // Defaults
            this.pollingIntervalMs = 3000;
            this.maxReconnectAttempts = 3;
            this.baseDelayMs = 1000;
            this.shutdownTimeoutMs = 5000;
        }
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MongoConfigs-CSW-" + this.collectionName);
            t.setDaemon(true);
            return t;
        });
    }
    
    
    public void setReloadCallback(Consumer<String> reloadCallback) {
        this.reloadCallback = reloadCallback;
    } 

    public void start() {
        if (running || closed.get()) return;
        running = true;
        
        
        lastPollingCheck = System.currentTimeMillis();

        LOGGER.info("[START] Starting ChangeStreamWatcher for collection: " + collectionName);
        
        performInitialLoad();
        
        
        try {
            startChangeStream();
        } catch (Exception e) {
            LOGGER.info("[WARN] Change Streams failed for " + collectionName + ", switching to polling mode");
            usePolling = true;
            startPolling();
        }
    }

    /**
     * Stops the watcher with proper resource cleanup.
     * Waits for pending operations to complete before returning.
     */
    public void stop() {
        if (!closed.compareAndSet(false, true)) {
            return; // Already closed
        }
        
        running = false;
        usePolling = false;
        
        // Cancel change stream subscription
        Subscription sub = changeStreamSubscription;
        if (sub != null) {
            try {
                sub.cancel();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error canceling change stream subscription", e);
            }
        }
        
        // Properly shutdown scheduler with await
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(shutdownTimeoutMs, TimeUnit.MILLISECONDS)) {
                    LOGGER.fine("Scheduler did not terminate in time, forcing shutdown for: " + collectionName);
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        cache.clear();
        LOGGER.info("[STOP] ChangeStreamWatcher stopped for collection: " + collectionName);
    }
    
    /**
     * Returns whether this watcher has been closed.
     */
    public boolean isClosed() {
        return closed.get();
    }

    private void performInitialLoad() {
        if (closed.get()) return;
        
        collection.countDocuments().subscribe(new Subscriber<Long>() {
            @Override
            public void onSubscribe(Subscription s) { 
                if (closed.get()) { s.cancel(); return; }
                s.request(1); 
            }
            
            @Override
            public void onNext(Long count) {
                if (closed.get()) return;
                lastKnownDocumentCount = count.intValue();
                LOGGER.info("[INIT] Initial document count for " + collectionName + ": " + count);
            }
            
            @Override
            public void onError(Throwable t) {
                if (closed.get()) return;
                LOGGER.log(Level.WARNING, "Error getting initial document count", t);
                lastKnownDocumentCount = 0;
            }
            
            @Override
            public void onComplete() {}
        });
        
        
        collection.find().subscribe(new Subscriber<Document>() {
            @Override
            public void onSubscribe(Subscription s) {
                if (closed.get()) { s.cancel(); return; }
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Document doc) {
                if (closed.get()) return;
                Object idObj = doc.get("_id");
                String id;
                if (idObj == null) {
                    id = "unknown";
                } else if (idObj instanceof String) {
                    id = (String) idObj;
                } else if (idObj instanceof org.bson.types.ObjectId) {
                    id = ((org.bson.types.ObjectId) idObj).toHexString();
                } else if (idObj instanceof Number) {
                    id = idObj.toString();
                } else {
                    
                    id = "doc-" + System.identityHashCode(doc);
                }
                cache.put(id, doc);
            }

            @Override
            public void onError(Throwable t) {
                if (!closed.get()) {
                    LOGGER.log(Level.WARNING, "Error during initial load", t);
                }
            }

            @Override
            public void onComplete() {
                if (!closed.get()) {
                    LOGGER.info("[OK] Initial load completed. Cached " + cache.size() + " documents for collection: " + collectionName);
                }
            }
        });
    }

    private void startChangeStream() {
        if (closed.get()) return;
        LOGGER.info("[STREAM] Setting up change stream monitoring for collection: " + collectionName);
        
        
        var changeStream = collection.watch()
                .fullDocument(FullDocument.UPDATE_LOOKUP);

        BsonDocument token = resumeToken.get();
        if (token != null) {
            changeStream = changeStream.resumeAfter(token);
            LOGGER.info("[RESUME] Resuming change stream from token for collection: " + collectionName);
        }

        changeStream.subscribe(new Subscriber<ChangeStreamDocument<Document>>() {
            @Override
            public void onSubscribe(Subscription s) {
                changeStreamSubscription = s;
                if (closed.get()) {
                    s.cancel();
                    return;
                }
                s.request(Long.MAX_VALUE);
                LOGGER.info("[OK] Successfully subscribed to change stream for collection: " + collectionName);
            }

            @Override
            public void onNext(ChangeStreamDocument<Document> event) {
                if (closed.get()) return;
                try {
                    processChangeEvent(event);
                    reconnectAttempts = 0; 
                } catch (Exception e) {
                    if (!closed.get()) {
                        LOGGER.log(Level.WARNING, "[ERROR] Error processing change event for " + collectionName, e);
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                if (closed.get()) return; // Suppress errors during shutdown
                
                String errorMsg = t.getMessage();
                if (errorMsg != null && (errorMsg.contains("only supported on replica sets") || 
                                       errorMsg.contains("not supported") ||
                                       errorMsg.contains("replica set"))) {
                    LOGGER.info("[FALLBACK] Change Streams not supported (no replica set), switching to polling for: " + collectionName);
                    usePolling = true;
                    startPolling();
                } else {
                    LOGGER.log(Level.WARNING, "[ERROR] Error in change stream for collection: " + collectionName, t);
                    if (running && !closed.get()) {
                        scheduleReconnect();
                    }
                }
            }

            @Override
            public void onComplete() {
                if (closed.get()) return;
                LOGGER.info("[COMPLETE] Change stream completed for collection: " + collectionName);
                if (running && !closed.get()) {
                    scheduleReconnect();
                }
            }
        });
    }

    private void processChangeEvent(ChangeStreamDocument<Document> event) {
        if (closed.get()) return;
        
        BsonDocument token = event.getResumeToken();
        if (token != null) {
            resumeToken.set(token);
        }
        
        var operationType = event.getOperationType();
        if (operationType == null) return;
        
        String opType = operationType.getValue();
        var documentKey = event.getDocumentKey();

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("🔄 Processing change event: " + opType + " in collection: " + collectionName);
        }

        
        String docId = null;
        if (documentKey != null) {
            var idValue = documentKey.get("_id");
            if (idValue != null) {
                if (idValue.isString()) {
                    docId = idValue.asString().getValue();
                } else if (idValue.isObjectId()) {
                    docId = idValue.asObjectId().getValue().toString();
                } else if (idValue.isInt32()) {
                    docId = String.valueOf(idValue.asInt32().getValue());
                } else if (idValue.isInt64()) {
                    docId = String.valueOf(idValue.asInt64().getValue());
                } else if (idValue.isDouble()) {
                    docId = String.valueOf(idValue.asDouble().getValue());
                } else if (idValue.isDecimal128()) {
                    docId = idValue.asDecimal128().getValue().toString();
                } else {
                    
                    docId = "doc-" + System.nanoTime(); 
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine("Complex document ID type, using generated ID: " + docId);
                    }
                }
            }
        }

        
        processDocumentChangeAsync(opType, docId, event);
    }
    
    private void processDocumentChangeAsync(String opType, String docId, ChangeStreamDocument<Document> event) {
        
        CompletableFuture.runAsync(() -> {
            processDocumentChangeSync(opType, docId, event);
        }, java.util.concurrent.ForkJoinPool.commonPool());
    }
    
    private void processDocumentChangeSync(String opType, String docId, ChangeStreamDocument<Document> event) {
        switch (opType) {
            case "insert":
            case "update":
            case "replace":
                var fullDocument = event.getFullDocument();
                if (fullDocument != null && docId != null) {
                    
                    cache.put(docId, fullDocument);
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine("🧠 ZAKTUALIZOWANO LOKALNY CACHE dla dokumentu: " + docId + " w kolekcji: " + collectionName);
                    }
                    
                    
                    CompletableFuture.runAsync(() -> applyDocumentToCache(fullDocument, docId));
                    
                    CompletableFuture.runAsync(() -> reloadDocumentFromMongoDB(docId));
                }
                
                triggerCacheReload("🔄 Document " + opType + " detected", true);
                break;

            case "delete": {
                Document removedDocument = docId != null ? cache.remove(docId) : null;
                handleDocumentDeletion(removedDocument, docId);
                triggerCacheReload("🗑️ Document deletion detected", true);
                break;
            }

            case "drop":
                cache.clear();
                triggerCacheReload("💥 Collection dropped", true);
                break;

            case "rename":
                cache.clear();
                triggerCacheReload("📛 Collection renamed", true);
                break;

            case "dropDatabase":
                cache.clear();
                triggerCacheReload("💀 Database dropped", true);
                break;

            default:
                triggerCacheReload("🔄 Other operation: " + opType, true);
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
                LOGGER.info("✅ APPLIED CONFIG DOCUMENT to cache: " + collectionName + " (docId=" + docId + ", keys=" + configData.keySet().size() + ")");
                return;
            }

            String lang = fullDocument.getString("lang");
            if (lang != null && !lang.isEmpty()) {
                Map<String, Object> messageData = copyDocumentExcluding(fullDocument, Set.of("_id", "lang", "updatedAt"));
                cacheManager.replaceLanguageData(collectionName, lang, messageData);
                LOGGER.info("✅ APPLIED LANGUAGE DOCUMENT to cache: " + collectionName + ":" + lang + " (keys=" + messageData.keySet().size() + ")");
            } else {
                LOGGER.info("⚠️ Document has no 'lang' field and was not recognized as config: " + collectionName + " (docId=" + docId + ")");
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

            // Fallback - jeśli docId to "config" lub nazwa kolekcji, usuń config data
            if ("config".equals(docId) || (docId != null && docId.equals(collectionName))) {
                cacheManager.replaceConfigData(collectionName, null);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error applying cache removal for collection: " + collectionName, e);
        }
    }

    private boolean isConfigDocument(String docId, Document document) {
        // Dokument jest configiem jeśli:
        // 1. _id == "config" (tradycyjny format)
        // 2. _id == nazwa kolekcji (nowy format)
        // 3. Dokument nie ma pola "lang" (co oznacza że to nie jest dokument językowy)
        
        if ("config".equals(docId)) {
            return true;
        }
        
        // Sprawdź czy _id równa się nazwie kolekcji
        if (docId != null && docId.equals(collectionName)) {
            return true;
        }
        
        if (document == null) {
            return false;
        }
        
        // Jeśli dokument nie ma pola "lang", traktuj jako config
        String lang = document.getString("lang");
        if (lang == null || lang.isEmpty()) {
            // Dodatkowe sprawdzenie - nie traktuj pustych dokumentów jako config
            if (document.size() > 1) { // więcej niż tylko _id
                return true;
            }
        }
        
        Object idValue = document.get("_id");
        if (idValue == null) {
            return false;
        }
        
        if (idValue instanceof String strId) {
            return "config".equals(strId) || strId.equals(collectionName);
        }
        
        return false;
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

    
    private void reloadDocumentFromMongoDB(String docId) {
        if (docId == null || docId.isEmpty()) return;
        
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("🔄 ROZPOCZYNAM RELOAD DOKUMENTU z MongoDB: " + docId + " w kolekcji: " + collectionName);
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                
                Object bsonId;
                try {
                    
                    bsonId = new org.bson.types.ObjectId(docId);
                } catch (Exception e) {
                    
                    bsonId = docId;
                }
                
                Document filter = new Document("_id", bsonId);
                collection.find(filter).first().subscribe(new Subscriber<Document>() {
                    @Override
                    public void onSubscribe(Subscription s) { s.request(1); }

                    @Override
                    public void onNext(Document freshDoc) {
                        if (freshDoc != null) {
                            
                            cache.put(docId, freshDoc);
                            
                            
                            if (cacheManager != null) {
                                if (isConfigDocument(docId, freshDoc)) {
                                    Map<String, Object> configData = copyDocumentExcluding(freshDoc, Set.of("_id", "updatedAt"));
                                    cacheManager.replaceConfigData(collectionName, configData);
                                    if (LOGGER.isLoggable(Level.FINE)) {
                                        LOGGER.fine("⭐ ZAKTUALIZOWANO CACHE CONFIG (docId=" + docId + ") dla kolekcji: " + collectionName);
                                    }
                                } else {
                                    String lang = freshDoc.getString("lang");
                                    if (lang != null && !lang.isEmpty()) {
                                        Map<String, Object> messageData = copyDocumentExcluding(freshDoc, Set.of("_id", "lang", "updatedAt"));
                                        cacheManager.replaceLanguageData(collectionName, lang, messageData);
                                        if (LOGGER.isLoggable(Level.FINE)) {
                                            LOGGER.fine("⭐ ZAKTUALIZOWANO CACHE LANGUAGE (lang=" + lang + ", docId=" + docId + ") dla kolekcji: " + collectionName);
                                        }
                                    }
                                }
                            }
                        } else {
                            LOGGER.warning("⚠️ Nie znaleziono dokumentu " + docId + " w MongoDB");
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOGGER.log(Level.WARNING, "❌ Błąd podczas ładowania dokumentu " + docId + " z MongoDB", t);
                    }

                    @Override
                    public void onComplete() {
                        LOGGER.info("✅ ZAKOŃCZONO RELOAD DOKUMENTU: " + docId + " w kolekcji: " + collectionName);
                    }
                });
                
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "❌ Błąd podczas przeładowania dokumentu " + docId + " z MongoDB", e);
            }
        });
    }

    
    private void triggerCacheReload(String reason) {
        triggerCacheReload(reason, true);
    }

    private void triggerCacheReload(String reason, boolean invalidateCache) {
        if (cacheManager == null || reloadCallback == null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("⚠️ No cacheManager or reloadCallback for collection: " + collectionName);
            }
            return;
        }
        
        // Callback (ConfigManagerImpl.reloadCollection) sam zadba o invalidację cache
        // Więc nie robimy tutaj podwójnej invalidacji!
        CompletableFuture.runAsync(() -> {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("🎯 Triggering reload callback: " + reason + " for: " + collectionName);
            }
            reloadCallback.accept(collectionName);
        }, java.util.concurrent.ForkJoinPool.commonPool())
        .exceptionally(throwable -> {
            LOGGER.log(Level.WARNING, "Reload callback failed for collection: " + collectionName, throwable);
            return null;
        });
    }

    private void scheduleReconnect() {
        if (!running || closed.get() || reconnectAttempts >= maxReconnectAttempts) {
            LOGGER.warning("[WARN] Change stream reconnection failed for collection: " + collectionName + 
                          " after " + reconnectAttempts + " attempts. Switching to polling mode.");
            usePolling = true;
            startPolling();
            return;
        }

        reconnectAttempts++;
        long delay = calculateBackoffDelay();
        
        LOGGER.info("[RECONNECT] Scheduling reconnect attempt " + reconnectAttempts + " for collection: " + 
                   collectionName + " in " + delay + "ms");

        scheduler.schedule(this::startChangeStream, delay, TimeUnit.MILLISECONDS);
    }

    private long calculateBackoffDelay() {
        long exponentialDelay = baseDelayMs * (1L << Math.min(reconnectAttempts - 1, 10));
        long jitter = (long) (Math.random() * exponentialDelay * 0.1);
        return exponentialDelay + jitter;
    }

    
    private void startPolling() {
        if (!running || !usePolling || closed.get()) return;
        
        scheduler.scheduleWithFixedDelay(() -> {
            if (!running || !usePolling || closed.get()) return;
            
            try {
                checkForChanges();
            } catch (Exception e) {
                if (!closed.get()) {
                    LOGGER.log(Level.WARNING, "[ERROR] Error during polling check for collection: " + collectionName, e);
                }
            }
        }, pollingIntervalMs, pollingIntervalMs, TimeUnit.MILLISECONDS);
        
        LOGGER.info("[POLL] Started polling for changes in collection: " + collectionName + 
                   " (every " + (pollingIntervalMs/1000) + "s)");
    }
    
    
    private void checkForChanges() {
        long currentTime = System.currentTimeMillis();
        
        
        collection.countDocuments().subscribe(new Subscriber<Long>() {
            @Override
            public void onSubscribe(Subscription s) { s.request(1); }

            @Override
            public void onNext(Long currentCount) {
                boolean countChanged = (currentCount.intValue() != lastKnownDocumentCount);
                
                if (countChanged) {
                    
                    lastKnownDocumentCount = currentCount.intValue();
                    LOGGER.info("🔄 Document count changed in " + collectionName + 
                               " (now: " + currentCount + ") - triggering reload");
                    triggerCacheReload("📊 Document count changed: " + currentCount);
                    lastPollingCheck = currentTime;
                } else {
                    
                    checkForDocumentUpdates(currentTime);
                }
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.log(Level.WARNING, "🚨 Error checking document count for: " + collectionName, t);
                
                triggerCacheReload("🚨 Error checking count - forcing reload");
                lastPollingCheck = currentTime;
            }

            @Override
            public void onComplete() {}
        });
    }
    
    
    private void checkForDocumentUpdates(long currentTime) {
        
        collection.find()
                .limit(20) 
                .subscribe(new Subscriber<Document>() {
                    private boolean changesDetected = false;
                    private Set<String> changedDocIds = new HashSet<>();
                    
                    @Override
                    public void onSubscribe(Subscription s) { s.request(Long.MAX_VALUE); }

                    @Override
                    public void onNext(Document doc) {
                        String docId = doc.get("_id") != null ? doc.get("_id").toString() : "unknown";
                        Document cachedDoc = cache.get(docId);
                        
                        
                        if (cachedDoc == null || !documentsEqual(doc, cachedDoc)) {
                            
                            cache.put(docId, doc);
                            
                            
                            changedDocIds.add(docId);
                            changesDetected = true;
                            
                            
                            applyDocumentToCache(doc, docId);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOGGER.log(Level.WARNING, "🚨 BŁĄD podczas sprawdzania aktualizacji dokumentów dla: " + collectionName, t);
                    }

                    @Override
                    public void onComplete() {
                        if (changesDetected) {
                            LOGGER.info("🔄 WYKRYTO ZMIANY W DOKUMENTACH: " + collectionName + 
                                       " (zmienione dokumenty: " + changedDocIds + ")");
                            triggerCacheReload("📝 Wykryto zmiany zawartości dokumentów");
                        }
                        lastPollingCheck = currentTime;
                    }
                });
    }
    
    
    private boolean documentsEqual(Document doc1, Document doc2) {
        if (doc1 == null && doc2 == null) return true;
        if (doc1 == null || doc2 == null) return false;
        
        
        Document copy1 = new Document(doc1);
        Document copy2 = new Document(doc2);
        copy1.remove("_id");
        copy2.remove("_id");
        
        return copy1.equals(copy2);
    }

    public void triggerReload(String id) {
        try {
            
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
    
    
    public String getStatus() {
        return String.format("Collection: %s, Running: %s, UsePolling: %s, Cache size: %d, Reconnect attempts: %d", 
                           collectionName, running, usePolling, cache.size(), reconnectAttempts);
    }
    
    
    public void forcePollingMode() {
        LOGGER.info("🔄 Forcing polling mode for collection: " + collectionName);
        if (changeStreamSubscription != null) {
            changeStreamSubscription.cancel();
        }
        usePolling = true;
        startPolling();
    }
}
