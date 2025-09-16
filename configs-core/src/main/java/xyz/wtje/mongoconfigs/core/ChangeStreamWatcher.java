package xyz.wtje.mongoconfigs.core;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ChangeStreamWatcher {

    private final MongoCollection<Document> collection;
    private final ConcurrentMap<String, Document> cache = new ConcurrentHashMap<>();
    private final AtomicReference<BsonTimestamp> resumeToken = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile boolean running = false;
    private volatile Subscription changeStreamSubscription;

    public ChangeStreamWatcher(MongoCollection<Document> collection) {
        this.collection = collection;
    }
    private volatile int reconnectAttempts = 0;

    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final int BASE_DELAY_MS = 1000;

    public void start() {
        if (running) return;
        running = true;

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
                System.out.println("Initial load completed. Cached " + cache.size() + " documents");
            }
        });
    }

    private void startChangeStream() {
        var pipeline = List.of(
                Aggregates.match(
                        Filters.in("operationType", List.of("insert", "update", "replace", "delete"))
                )
        );

        var changeStream = collection.watch(pipeline)
                .fullDocument(FullDocument.UPDATE_LOOKUP);

        var token = resumeToken.get();
        if (token != null) {
            changeStream = changeStream.resumeAfter(org.bson.BsonDocument.parse("{\"_data\": \"" + token.toString() + "\"}"));
        }

        changeStream.subscribe(new Subscriber<ChangeStreamDocument<Document>>() {
            @Override
            public void onSubscribe(Subscription s) {
                changeStreamSubscription = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChangeStreamDocument<Document> event) {
                try {
                    processChangeEvent(event);
                    reconnectAttempts = 0;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                if (running) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onComplete() {
                if (running) {
                    scheduleReconnect();
                }
            }
        });
    }

    private void processChangeEvent(ChangeStreamDocument<Document> event) {
        if (event.getResumeToken() != null) {
            resumeToken.set(event.getResumeToken().asTimestamp());
        }

        var operationType = event.getOperationType().getValue();
        var documentKey = event.getDocumentKey();

        if (documentKey == null) return;

        org.bson.BsonValue idValue = documentKey.get("_id");
        if (idValue == null || !idValue.isString()) return;
        String id = idValue.asString().getValue();

        switch (operationType) {
            case "insert":
            case "update":
            case "replace":
                var fullDocument = event.getFullDocument();
                if (fullDocument != null) {
                    cache.put(id, fullDocument);
                }
                break;
            case "delete":
                cache.remove(id);
                break;
        }
    }

    private void scheduleReconnect() {
        if (!running || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            running = false;
            return;
        }

        reconnectAttempts++;
        long delay = calculateBackoffDelay();

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

