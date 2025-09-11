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

public final class PublisherAdapter {

    private PublisherAdapter() {
        throw new UnsupportedOperationException("Utility class");
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
                    future.completeExceptionally(new CompletionException(t));
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
                // Void publisher doesn't emit values
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
