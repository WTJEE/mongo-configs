package xyz.wtje.mongoconfigs.core;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class Asyncs {

    static <T> CompletableFuture<T> one(Publisher<T> pub) {
        CompletableFuture<T> future = new CompletableFuture<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<Subscription> subRef = new java.util.concurrent.atomic.AtomicReference<>();

        pub.subscribe(new Subscriber<T>() {
            private volatile Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                subRef.set(s);
                if (!future.isCancelled()) {
                    s.request(1);
                } else {
                    s.cancel();
                }
            }

            @Override
            public void onNext(T t) {
                if (completed.compareAndSet(false, true)) {
                    future.complete(t);
                    if (subscription != null) {
                        subscription.cancel();
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                if (completed.compareAndSet(false, true)) {
                    future.completeExceptionally(t);
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

    private Asyncs() {}
}
