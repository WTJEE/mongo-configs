package xyz.wtje.mongoconfigs.core.typed;

import com.mongodb.reactivestreams.client.ChangeStreamPublisher;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.mongoconfigs.core.mongo.MongoManager;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TypedChangeStreamManager {

    private static final Logger LOGGER = Logger.getLogger(TypedChangeStreamManager.class.getName());

    private final MongoManager mongoManager;
    private final MongoConfig config;
    private final Executor asyncExecutor;
    private final ConcurrentHashMap<String, Consumer<Document>> listeners;
    private volatile boolean running = false;
    private Subscription changeStreamSubscription;

    public TypedChangeStreamManager(MongoManager mongoManager, MongoConfig config) {
        this.mongoManager = mongoManager;
        this.config = config;
        this.asyncExecutor = ForkJoinPool.commonPool();
        this.listeners = new ConcurrentHashMap<>();
    }

    public void registerDocumentListener(String documentId, Consumer<Document> listener) {
        listeners.put(documentId, listener);
        if (!running) {
            startChangeStreamWatcher();
        }
    }

    public void unregisterDocumentListener(String documentId) {
        listeners.remove(documentId);
        if (listeners.isEmpty()) {
            stopChangeStreamWatcher();
        }
    }

    private void startChangeStreamWatcher() {
        if (running) {
            return;
        }

        running = true;
        CompletableFuture.runAsync(() -> {
            try {
                if (config.isDebugLogging()) {
                    LOGGER.info("Starting Change Stream watcher for typed configs...");
                }

                ChangeStreamPublisher<Document> changeStream = mongoManager.getReactiveCollection(config.getTypedConfigsCollection())
                    .watch(Arrays.asList(
                        Aggregates.match(Filters.in("operationType", Arrays.asList("insert", "update", "replace")))
                    ))
                    .fullDocument(FullDocument.UPDATE_LOOKUP);

                changeStream.subscribe(new Subscriber<ChangeStreamDocument<Document>>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        changeStreamSubscription = s;
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ChangeStreamDocument<Document> change) {
                        if (!running) {
                            return;
                        }

                        try {
                            Document fullDocument = change.getFullDocument();
                            if (fullDocument != null) {
                                String documentId = fullDocument.getString("documentId");
                                if (documentId != null && listeners.containsKey(documentId)) {
                                    Consumer<Document> listener = listeners.get(documentId);
                                    if (listener != null) {
                                        asyncExecutor.execute(() -> {
                                            try {
                                                listener.accept(fullDocument);
                                            } catch (Exception e) {
                                                LOGGER.log(Level.WARNING, "Error in document listener for: " + documentId, e);
                                            }
                                        });
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error processing change stream event", e);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOGGER.log(Level.SEVERE, "Change stream error", t);
                        running = false;
                    }

                    @Override
                    public void onComplete() {
                        running = false;
                        if (config.isDebugLogging()) {
                            LOGGER.info("Change Stream completed");
                        }
                    }
                });

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Change stream watcher failed", e);
                running = false;
            }
        }, asyncExecutor);
    }

    private void stopChangeStreamWatcher() {
        running = false;
        if (changeStreamSubscription != null) {
            changeStreamSubscription.cancel();
        }
        if (config.isDebugLogging()) {
            LOGGER.info("Stopping Change Stream watcher...");
        }
    }

    public void shutdown() {
        listeners.clear();
        stopChangeStreamWatcher();
    }
}
