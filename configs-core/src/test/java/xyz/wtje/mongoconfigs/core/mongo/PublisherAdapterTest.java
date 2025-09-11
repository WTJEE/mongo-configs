package xyz.wtje.mongoconfigs.core.mongo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PublisherAdapterTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testToCompletableFutureSuccess() {
        String expectedValue = "test-value";
        Publisher<String> publisher = createSuccessfulPublisher(expectedValue);

        CompletableFuture<String> future = PublisherAdapter.toCompletableFuture(publisher);

        assertNotNull(future);
        String result = future.join();
        assertEquals(expectedValue, result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testToCompletableFutureError() {
        RuntimeException expectedException = new RuntimeException("Test error");
        Publisher<String> publisher = createErrorPublisher(expectedException);

        CompletableFuture<String> future = PublisherAdapter.toCompletableFuture(publisher);

        assertNotNull(future);
        CompletionException exception = assertThrows(CompletionException.class, () -> future.join());
        assertEquals(expectedException, exception.getCause());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testToCompletableFutureEmpty() {
        Publisher<String> publisher = createEmptyPublisher();

        CompletableFuture<String> future = PublisherAdapter.toCompletableFuture(publisher);

        assertNotNull(future);
        String result = future.join();
        assertNull(result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testToCompletableFutureListSuccess() {
        List<String> expectedValues = Arrays.asList("value1", "value2", "value3");
        Publisher<String> publisher = createListPublisher(expectedValues);

        CompletableFuture<List<String>> future = PublisherAdapter.toCompletableFutureList(publisher);

        assertNotNull(future);
        List<String> result = future.join();
        assertEquals(expectedValues, result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testToCompletableFutureListError() {
        RuntimeException expectedException = new RuntimeException("List error");
        Publisher<String> publisher = createErrorPublisher(expectedException);

        CompletableFuture<List<String>> future = PublisherAdapter.toCompletableFutureList(publisher);

        assertNotNull(future);
        CompletionException exception = assertThrows(CompletionException.class, () -> future.join());
        assertEquals(expectedException, exception.getCause());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testToCompletableFutureListEmpty() {
        Publisher<String> publisher = createEmptyPublisher();

        CompletableFuture<List<String>> future = PublisherAdapter.toCompletableFutureList(publisher);

        assertNotNull(future);
        List<String> result = future.join();
        assertTrue(result.isEmpty());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testToCompletableFutureVoidSuccess() {
        Publisher<Void> publisher = createVoidPublisher();

        CompletableFuture<Void> future = PublisherAdapter.toCompletableFutureVoid(publisher);

        assertNotNull(future);
        assertDoesNotThrow(() -> future.join());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testToCompletableFutureVoidError() {
        RuntimeException expectedException = new RuntimeException("Void error");
        Publisher<Void> publisher = createVoidErrorPublisher(expectedException);

        CompletableFuture<Void> future = PublisherAdapter.toCompletableFutureVoid(publisher);

        assertNotNull(future);
        CompletionException exception = assertThrows(CompletionException.class, () -> future.join());
        assertEquals(expectedException, exception.getCause());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testToCompletableFutureMultipleValues() {
        Publisher<String> publisher = subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                private int count = 0;

                @Override
                public void request(long n) {
                    if (count == 0) {
                        subscriber.onNext("first");
                        count++;
                    } else if (count == 1) {
                        subscriber.onNext("second");
                        count++;
                    } else {
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                }
            });
        };

        CompletableFuture<String> future = PublisherAdapter.toCompletableFuture(publisher);

        assertNotNull(future);
        String result = future.join();
        assertEquals("second", result);
    }

    private Publisher<String> createSuccessfulPublisher(String value) {
        return subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onNext(value);
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                }
            });
        };
    }

    private Publisher<String> createErrorPublisher(Exception error) {
        return subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onError(error);
                }

                @Override
                public void cancel() {
                }
            });
        };
    }

    private Publisher<String> createEmptyPublisher() {
        return subscriber -> {
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
    }

    private Publisher<String> createListPublisher(List<String> values) {
        return subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                private int index = 0;

                @Override
                public void request(long n) {
                    for (int i = 0; i < n && index < values.size(); i++, index++) {
                        subscriber.onNext(values.get(index));
                    }
                    if (index >= values.size()) {
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                }
            });
        };
    }

    private Publisher<Void> createVoidPublisher() {
        return subscriber -> {
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
    }

    private Publisher<Void> createVoidErrorPublisher(Exception error) {
        return subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onError(error);
                }

                @Override
                public void cancel() {
                }
            });
        };
    }
}
