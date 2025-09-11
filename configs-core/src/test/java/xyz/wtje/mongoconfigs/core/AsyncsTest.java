package xyz.wtje.mongoconfigs.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AsyncsTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOneWithSuccessfulPublisher() {
        String expectedValue = "test-value";
        Publisher<String> publisher = subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onNext(expectedValue);
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                }
            });
        };

        CompletableFuture<String> future = Asyncs.one(publisher);

        assertNotNull(future);
        String result = future.join();
        assertEquals(expectedValue, result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOneWithError() {
        RuntimeException expectedException = new RuntimeException("Test error");
        Publisher<String> publisher = subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onError(expectedException);
                }

                @Override
                public void cancel() {
                }
            });
        };

        CompletableFuture<String> future = Asyncs.one(publisher);

        assertNotNull(future);
        assertThrows(RuntimeException.class, () -> future.join());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOneWithEmptyPublisher() {
        Publisher<String> publisher = subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                }
            });
        };

        CompletableFuture<String> future = Asyncs.one(publisher);

        assertNotNull(future);
        String result = future.join();
        assertNull(result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOneWithMultipleValues() {
        String firstValue = "first";
        String secondValue = "second";
        Publisher<String> publisher = subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                private boolean firstSent = false;

                @Override
                public void request(long n) {
                    if (!firstSent) {
                        subscriber.onNext(firstValue);
                        firstSent = true;
                    } else {
                        subscriber.onNext(secondValue);
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                }
            });
        };

        CompletableFuture<String> future = Asyncs.one(publisher);

        assertNotNull(future);
        String result = future.join();
        assertEquals(firstValue, result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOneWithCancellation() {
        Publisher<String> publisher = subscriber -> {
            Subscription subscription = new Subscription() {
                private boolean cancelled = false;

                @Override
                public void request(long n) {
                    if (!cancelled) {
                        subscriber.onNext("value");
                    }
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            };
            subscriber.onSubscribe(subscription);
        };

        CompletableFuture<String> future = Asyncs.one(publisher);

        assertNotNull(future);
        String result = future.join();
        assertEquals("value", result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOneWithNullValue() {
        Publisher<String> publisher = subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onNext(null);
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                }
            });
        };

        CompletableFuture<String> future = Asyncs.one(publisher);

        assertNotNull(future);
        String result = future.join();
        assertNull(result);
    }
}
