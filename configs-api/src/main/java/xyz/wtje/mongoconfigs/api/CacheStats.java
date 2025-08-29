package xyz.wtje.mongoconfigs.api;


public interface CacheStats {

    double getHitRate();

    long getRequestCount();

    long getHitCount();

    long getMissCount();

    long getSize();

    long getEstimatedSize();

    long getEvictionCount();

    double getAverageLoadPenalty();
}