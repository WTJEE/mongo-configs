package xyz.wtje.mongoconfigs.core.mongo;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;


public final class PublisherAdapter {
    
    private PublisherAdapter() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T> CompletableFuture<T> toCompletableFuture(Publisher<T> publisher) {
        CompletableFuture<T> future = new CompletableFuture<>();
        AtomicReference<T> result = new AtomicReference<>();
        
        publisher.subscribe(new Subscriber<T>() {
            private Subscription subscription;
            
            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                s.request(1);
            }
            
            @Override
            public void onNext(T item) {
                result.set(item);
                subscription.request(1);
            }
            
            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(new CompletionException(t));
            }
            
            @Override
            public void onComplete() {
                future.complete(result.get());
            }
        });
        
        return future;
    }

    public static <T> CompletableFuture<List<T>> toCompletableFutureList(Publisher<T> publisher) {
        CompletableFuture<List<T>> future = new CompletableFuture<>();
        List<T> results = new ArrayList<>();
        
        publisher.subscribe(new Subscriber<T>() {
            private Subscription subscription;
            
            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                s.request(Long.MAX_VALUE);
            }
            
            @Override
            public void onNext(T item) {
                results.add(item);
            }
            
            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(new CompletionException(t));
            }
            
            @Override
            public void onComplete() {
                future.complete(results);
            }
        });
        
        return future;
    }

    public static CompletableFuture<Void> toCompletableFutureVoid(Publisher<Void> publisher) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        publisher.subscribe(new Subscriber<Void>() {
            private Subscription subscription;
            
            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                s.request(1);
            }
            
            @Override
            public void onNext(Void item) {
                
            }
            
            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(new CompletionException(t));
            }
            
            @Override
            public void onComplete() {
                future.complete(null);
            }
        });
        
        return future;
    }
}