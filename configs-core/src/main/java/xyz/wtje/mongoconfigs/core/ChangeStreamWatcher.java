package xyz.wtje.mongoconfigs.core;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import xyz.wtje.mongoconfigs.core.cache.CacheManager;

import java.util.HashSet;
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
    private final ScheduledExecutorService scheduler;
    
    
    private CacheManager cacheManager;
    private Consumer<String> reloadCallback;
    
    
    private volatile boolean usePolling = false;
    private volatile long lastPollingCheck = 0; 
    private volatile int lastKnownDocumentCount = 0; 
    private static final long POLLING_INTERVAL_MS = 3000; 

    private volatile boolean running = false;
    private volatile Subscription changeStreamSubscription;

    public ChangeStreamWatcher(MongoCollection<Document> collection) {
        this.collection = collection;
        this.collectionName = collection.getNamespace().getCollectionName();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MongoConfigs-CSW-" + this.collectionName);
            t.setDaemon(true);
            return t;
        });
    }
    
    public ChangeStreamWatcher(MongoCollection<Document> collection, CacheManager cacheManager) {
        this.collection = collection;
        this.collectionName = collection.getNamespace().getCollectionName();
        this.cacheManager = cacheManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MongoConfigs-CSW-" + this.collectionName);
            t.setDaemon(true);
            return t;
        });
    }
    
    
    public void setReloadCallback(Consumer<String> reloadCallback) {
        this.reloadCallback = reloadCallback;
    }
    private volatile int reconnectAttempts = 0;

    private static final int MAX_RECONNECT_ATTEMPTS = 3; 
    private static final int BASE_DELAY_MS = 1000; 

    public void start() {
        if (running) return;
        running = true;
        
        
        lastPollingCheck = System.currentTimeMillis();

        LOGGER.info("🚀 Starting ChangeStreamWatcher for collection: " + collectionName);
        
        performInitialLoad();
        
        
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
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {}
    }

    private void performInitialLoad() {
        
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
        
        
        collection.find().subscribe(new Subscriber<Document>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Document doc) {
                
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
                    reconnectAttempts = 0; 
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
        if (idValue == null) {
            return false;
        }
        
        if (idValue instanceof String) {
            return "config".equals(idValue);
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
