package xyz.wtje.mongoconfigs.core.mongo;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PublisherAdapter {
    
    private static final Logger LOGGER = Logger.getLogger(PublisherAdapter.class.getName());

    private PublisherAdapter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks if the throwable is a shutdown-related error that should be silently ignored.
     */
    private static boolean isShutdownError(Throwable t) {
        if (t == null) return false;
        
        String message = t.getMessage();
        if (message != null) {
            // Check for session pool closed error
            if (message.contains("server session pool is open") ||
                message.contains("session pool") ||
                message.contains("Client has been closed") ||
                message.contains("MongoClient has been closed") ||
                message.contains("Connection pool was closed")) {
                return true;
            }
        }
        
        // Check cause chain
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            return isShutdownError(cause);
        }
        
        return false;
    }

    public static <T> CompletableFuture<T> toCompletableFuture(Publisher<T> publisher) {
        CompletableFuture<T> future = new CompletableFuture<>();
        AtomicReference<T> result = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Subscription> subRef = new AtomicReference<>();

        publisher.subscribe(new Subscriber<T>() {
            private volatile Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                subRef.set(s);
                if (!future.isCancelled()) {
                    s.request(Long.MAX_VALUE);
                } else {
                    s.cancel();
                }
            }

            @Override
            public void onNext(T item) {
                if (!completed.get() && !future.isCancelled()) {
                    result.set(item);
                }
            }

            @Override
            public void onError(Throwable t) {
                if (completed.compareAndSet(false, true)) {
                    future.completeExceptionally(new CompletionException(t));
                }
            }

            @Override
            public void onComplete() {
                if (completed.compareAndSet(false, true)) {
                    future.complete(result.get());
                }
            }
        });

        future.whenComplete((r, t) -> {
            Subscription s = subRef.get();
            if (future.isCancelled() && s != null) {
                s.cancel();
            }
        });

        return future;
    }

    public static <T> CompletableFuture<List<T>> toCompletableFutureList(Publisher<T> publisher) {
        CompletableFuture<List<T>> future = new CompletableFuture<>();
        List<T> results = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Subscription> subRef = new AtomicReference<>();

        publisher.subscribe(new Subscriber<T>() {
            private volatile Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                subRef.set(s);
                if (!future.isCancelled()) {
                    s.request(Long.MAX_VALUE);
                } else {
                    s.cancel();
                }
            }

            @Override
            public void onNext(T item) {
                if (!completed.get() && !future.isCancelled()) {
                    synchronized (results) {
                        results.add(item);
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                if (completed.compareAndSet(false, true)) {
                    // During shutdown, return empty list instead of throwing
                    if (isShutdownError(t)) {
                        LOGGER.log(Level.FINE, "Ignoring shutdown-related error in list publisher", t);
                        future.complete(new ArrayList<>(results));
                    } else {
                        future.completeExceptionally(new CompletionException(t));
                    }
                }
            }

            @Override
            public void onComplete() {
                if (completed.compareAndSet(false, true)) {
                    future.complete(new ArrayList<>(results));
                }
            }
        });

        future.whenComplete((r, t) -> {
            Subscription s = subRef.get();
            if (future.isCancelled() && s != null) {
                s.cancel();
            }
        });

        return future;
    }

    public static CompletableFuture<Void> toCompletableFutureVoid(Publisher<Void> publisher) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Subscription> subRef = new AtomicReference<>();

        publisher.subscribe(new Subscriber<Void>() {
            private volatile Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                subRef.set(s);
                if (!future.isCancelled()) {
                    s.request(Long.MAX_VALUE);
                } else {
                    s.cancel();
                }
            }

            @Override
            public void onNext(Void item) {
            }

            @Override
            public void onError(Throwable t) {
                if (completed.compareAndSet(false, true)) {
                    // During shutdown, complete normally instead of throwing
                    if (isShutdownError(t)) {
                        LOGGER.log(Level.FINE, "Ignoring shutdown-related error in void publisher", t);
                        future.complete(null);
                    } else {
                        future.completeExceptionally(new CompletionException(t));
                    }
                }
            }

            @Override
            public void onComplete() {
                if (completed.compareAndSet(false, true)) {
                    future.complete(null);
                }
            }
        });

        future.whenComplete((r, t) -> {
            Subscription s = subRef.get();
            if (future.isCancelled() && s != null) {
                s.cancel();
            }
        });

        return future;
    }
}

