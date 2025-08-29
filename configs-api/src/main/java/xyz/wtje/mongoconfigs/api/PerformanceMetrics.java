package xyz.wtje.mongoconfigs.api;

import java.time.Duration;

public interface PerformanceMetrics {

    Duration getAverageMongoTime();

    Duration getAverageCacheTime();

    long getMongoOperationsCount();

    long getCacheOperationsCount();

    int getConnectionPoolSize();

    int getActiveConnections();

    boolean isChangeStreamsActive();

    long getLastChangeStreamEvent();

    int getMonitoredCollections();
}