package xyz.wtje.mongoconfigs.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import xyz.wtje.mongoconfigs.api.PerformanceMetrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsManager implements PerformanceMetrics {
    
    private final MeterRegistry meterRegistry;
    
    private final Timer mongoOperationTimer;
    private final Counter mongoOperationCounter;
    private final Counter mongoErrorCounter;
    
    private final Timer cacheOperationTimer;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    
    private final AtomicInteger connectionPoolSize = new AtomicInteger(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    
    private final AtomicLong lastChangeStreamEvent = new AtomicLong(0);
    private final AtomicInteger monitoredCollections = new AtomicInteger(0);
    private volatile boolean changeStreamsActive = false;
    
    public MetricsManager() {
        this.meterRegistry = new SimpleMeterRegistry();
        
        this.mongoOperationTimer = Timer.builder("mongodb.operation.duration")
                .description("MongoDB operation execution time")
                .register(meterRegistry);
        
        this.mongoOperationCounter = Counter.builder("mongodb.operation.count")
                .description("Total MongoDB operations")
                .register(meterRegistry);
        
        this.mongoErrorCounter = Counter.builder("mongodb.operation.errors")
                .description("MongoDB operation errors")
                .register(meterRegistry);
        
        this.cacheOperationTimer = Timer.builder("cache.operation.duration")
                .description("Cache operation execution time")
                .register(meterRegistry);
        
        this.cacheHitCounter = Counter.builder("cache.hits")
                .description("Cache hits")
                .register(meterRegistry);
        
        this.cacheMissCounter = Counter.builder("cache.misses")
                .description("Cache misses")
                .register(meterRegistry);
    }

    public Timer.Sample startMongoOperation() {
        return Timer.start(meterRegistry);
    }
    
    public void recordMongoOperation(Timer.Sample sample, String collection, String operation, String outcome) {
        sample.stop(Timer.builder("mongodb.operation.duration")
                .tag("collection", collection)
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(meterRegistry));
        
        mongoOperationCounter.increment();
        
        if ("error".equals(outcome)) {
            mongoErrorCounter.increment();
        }
    }

    public Timer.Sample startCacheOperation() {
        return Timer.start(meterRegistry);
    }
    
    public void recordCacheHit(Timer.Sample sample, String type) {
        sample.stop(Timer.builder("cache.operation.duration")
                .tag("type", type)
                .tag("outcome", "hit")
                .register(meterRegistry));
        
        cacheHitCounter.increment();
    }
    
    public void recordCacheMiss(Timer.Sample sample, String type) {
        sample.stop(Timer.builder("cache.operation.duration")
                .tag("type", type)
                .tag("outcome", "miss")
                .register(meterRegistry));
        
        cacheMissCounter.increment();
    }

    public void updateConnectionPoolSize(int size) {
        connectionPoolSize.set(size);
    }
    
    public void updateActiveConnections(int count) {
        activeConnections.set(count);
    }

    public void recordChangeStreamEvent() {
        lastChangeStreamEvent.set(System.currentTimeMillis());
    }
    
    public void setChangeStreamsActive(boolean active) {
        this.changeStreamsActive = active;
    }
    
    public void updateMonitoredCollections(int count) {
        monitoredCollections.set(count);
    }

    @Override
    public Duration getAverageMongoTime() {
        return Duration.ofNanos((long) mongoOperationTimer.mean(TimeUnit.NANOSECONDS));
    }
    
    @Override
    public Duration getAverageCacheTime() {
        return Duration.ofNanos((long) cacheOperationTimer.mean(TimeUnit.NANOSECONDS));
    }
    
    @Override
    public long getMongoOperationsCount() {
        return (long) mongoOperationCounter.count();
    }
    
    @Override
    public long getCacheOperationsCount() {
        return (long) (cacheHitCounter.count() + cacheMissCounter.count());
    }
    
    @Override
    public int getConnectionPoolSize() {
        return connectionPoolSize.get();
    }
    
    @Override
    public int getActiveConnections() {
        return activeConnections.get();
    }
    
    @Override
    public boolean isChangeStreamsActive() {
        return false;
    }
    
    @Override
    public long getLastChangeStreamEvent() {
        return lastChangeStreamEvent.get();
    }
    
    @Override
    public int getMonitoredCollections() {
        return monitoredCollections.get();
    }

    public double getCacheHitRate() {
        double hits = cacheHitCounter.count();
        double total = hits + cacheMissCounter.count();
        return total > 0 ? hits / total : 0.0;
    }
    
    public double getMongoErrorRate() {
        double errors = mongoErrorCounter.count();
        double total = mongoOperationCounter.count();
        return total > 0 ? errors / total : 0.0;
    }
    
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public Timer createTimer(String name, String... tags) {
        Timer.Builder builder = Timer.builder(name);
        for (int i = 0; i < tags.length; i += 2) {
            if (i + 1 < tags.length) {
                builder.tag(tags[i], tags[i + 1]);
            }
        }
        return builder.register(meterRegistry);
    }

    public Counter createCounter(String name, String... tags) {
        Counter.Builder builder = Counter.builder(name);
        for (int i = 0; i < tags.length; i += 2) {
            if (i + 1 < tags.length) {
                builder.tag(tags[i], tags[i + 1]);
            }
        }
        return builder.register(meterRegistry);
    }

    public void recordCacheOperation(String operation, String outcome) {
        cacheOperationTimer.record(() -> {
        });
        
        if ("hit".equals(outcome)) {
            cacheHitCounter.increment();
        } else if ("miss".equals(outcome)) {
            cacheMissCounter.increment();
        }
    }
}