package xyz.wtje.mongoconfigs.core.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@Tag("integration")
public class PerformanceTest {

    @Container
    final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0");

    @Test
    public void testAsyncWriteThroughput() throws Exception {
        MongoConfig config = new MongoConfig();
        config.setConnectionString(mongoDBContainer.getReplicaSetUrl());
        config.setDatabase("perf_test");
        config.setConfigsCollection("configs");
        config.setIoThreads(4);
        config.setWorkerThreads(4);
        config.setCacheMaxSize(0);

        ConfigManagerImpl manager = new ConfigManagerImpl(config);
        manager.initialize();

        int operationCount = 1000;
        AtomicInteger successCount = new AtomicInteger();

        long startTime = System.currentTimeMillis();

        CompletableFuture<?>[] futures = IntStream.range(0, operationCount)
                .mapToObj(i -> manager.set("key_" + i, "value_" + i)
                        .thenRun(successCount::incrementAndGet))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Inserted " + operationCount + " configs in " + duration + "ms");

        assertTrue(duration < 10000, "Should handle 1000 inserts in under 10s");
        assertEquals(operationCount, successCount.get());

        manager.shutdown();
    }

    @Test
    public void testConcurrentReadPerformanceWithCache() throws Exception {
        MongoConfig config = new MongoConfig();
        config.setConnectionString(mongoDBContainer.getReplicaSetUrl());
        config.setDatabase("perf_test_read");
        config.setCacheMaxSize(10000); // Enable cache

        ConfigManagerImpl manager = new ConfigManagerImpl(config);
        manager.initialize();

        // Seed data
        manager.set("hot_key", "hot_value").get();

        int readCount = 100000;
        long startTime = System.currentTimeMillis();

        // Simulating heavy concurrent read load
        CompletableFuture<?>[] futures = IntStream.range(0, readCount)
                .mapToObj(i -> manager.get("hot_key", String.class)
                        .thenAccept(val -> {
                            if (!"hot_value".equals(val))
                                throw new IllegalStateException("Wrong value: " + val);
                        }))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Performed " + readCount + " cached reads in " + duration + "ms");

        assertTrue(duration < 2000, "Should handle 100k cached reads in under 2s");

        manager.shutdown();
    }
}
