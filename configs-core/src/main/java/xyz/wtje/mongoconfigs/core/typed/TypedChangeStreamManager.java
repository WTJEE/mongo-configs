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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TypedChangeStreamManager {

    private static final Logger LOGGER = Logger.getLogger(TypedChangeStreamManager.class.getName());
    private static final long INITIAL_RETRY_DELAY_MS = 1000L;
    private static final long MAX_RETRY_DELAY_MS = 30_000L;

    private final MongoManager mongoManager;
    private final MongoConfig config;
    private final Executor asyncExecutor;
    private final ConcurrentHashMap<String, Consumer<Document>> listeners;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger retryAttempts = new AtomicInteger(0);
    private volatile Subscription changeStreamSubscription;

    public TypedChangeStreamManager(MongoManager mongoManager, MongoConfig config) {
        this.mongoManager = mongoManager;
        this.config = config;
        this.asyncExecutor = ForkJoinPool.commonPool();
        this.listeners = new ConcurrentHashMap<>();
    }

    public void registerDocumentListener(String documentId, Consumer<Document> listener) {
        listeners.put(documentId, listener);
        startChangeStreamWatcher();
    }

    public void unregisterDocumentListener(String documentId) {
        listeners.remove(documentId);
        if (listeners.isEmpty()) {
            stopChangeStreamWatcher();
        }
    }

    private void startChangeStreamWatcher() {
        if (listeners.isEmpty()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        retryAttempts.set(0);
        CompletableFuture.runAsync(this::subscribeToChangeStream, asyncExecutor);
    }

    private void subscribeToChangeStream() {
        if (!running.get()) {
            return;
        }
        if (listeners.isEmpty()) {
            running.set(false);
            return;
        }

        try {
            if (config.isDebugLogging()) {
                LOGGER.info("Starting Change Stream watcher for typed configs...");
            }

            ChangeStreamPublisher<Document> changeStream = mongoManager
                .getReactiveCollection(config.getTypedConfigsCollection())
                .watch(Arrays.asList(
                    Aggregates.match(Filters.in("operationType", Arrays.asList("insert", "update", "replace")))
                ))
                .fullDocument(FullDocument.UPDATE_LOOKUP);

            changeStream.subscribe(new Subscriber<ChangeStreamDocument<Document>>() {
                @Override
                public void onSubscribe(Subscription s) {
                    changeStreamSubscription = s;
                    retryAttempts.set(0);
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ChangeStreamDocument<Document> change) {
                    if (!running.get()) {
                        return;
                    }

                    try {
                        Document fullDocument = change.getFullDocument();
                        if (fullDocument != null) {
                            String documentId = fullDocument.getString("documentId");
                            Consumer<Document> listener = documentId != null ? listeners.get(documentId) : null;
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
                        retryAttempts.set(0);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error processing change stream event", e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    LOGGER.log(Level.SEVERE, "Change stream error", t);
                    cancelCurrentSubscription();
                    scheduleRestart();
                }

                @Override
                public void onComplete() {
                    if (config.isDebugLogging()) {
                        LOGGER.info("Change Stream completed");
                    }
                    cancelCurrentSubscription();
                    scheduleRestart();
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Change stream watcher failed", e);
            cancelCurrentSubscription();
            scheduleRestart();
        }
    }

    private void scheduleRestart() {
        if (!running.get()) {
            return;
        }
        if (listeners.isEmpty()) {
            running.set(false);
            return;
        }

        int attempt = retryAttempts.incrementAndGet();
        long delay = computeBackoffDelay(attempt);

        if (config.isDebugLogging()) {
            LOGGER.info("Resubscribing typed change stream in " + delay + "ms (attempt " + attempt + ")");
        }

        CompletableFuture.runAsync(this::subscribeToChangeStream,
            CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS, asyncExecutor));
    }

    private long computeBackoffDelay(int attempt) {
        if (attempt <= 1) {
            return INITIAL_RETRY_DELAY_MS;
        }
        long exponent = Math.min(attempt - 1, 5);
        long delay = INITIAL_RETRY_DELAY_MS * (1L << exponent);
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }

    private void cancelCurrentSubscription() {
        Subscription subscription = this.changeStreamSubscription;
        this.changeStreamSubscription = null;
        if (subscription != null) {
            try {
                subscription.cancel();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to cancel change stream subscription cleanly", e);
            }
        }
    }

    private void stopChangeStreamWatcher() {
        running.set(false);
        retryAttempts.set(0);
        cancelCurrentSubscription();
        if (config.isDebugLogging()) {
            LOGGER.info("Stopping Change Stream watcher...");
        }
    }

    public void shutdown() {
        listeners.clear();
        stopChangeStreamWatcher();
    }
}
