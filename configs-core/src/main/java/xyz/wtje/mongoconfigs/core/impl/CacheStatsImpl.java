package xyz.wtje.mongoconfigs.core.impl;

import xyz.wtje.mongoconfigs.api.CacheStats;
import xyz.wtje.mongoconfigs.core.cache.CacheManager;

public class CacheStatsImpl implements CacheStats {
    
    private final CacheManager cacheManager;
    
    public CacheStatsImpl(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    @Override
    public double getHitRate() {

        long totalRequests = getRequestCount();
        return totalRequests > 0 ? 1.0 : 0.0;
    }
    
    @Override
    public long getRequestCount() {
        return cacheManager.getConfigRequests() + cacheManager.getMessageRequests();
    }
    
    @Override
    public long getHitCount() {
        return getRequestCount();
    }
    
    @Override
    public long getMissCount() {
        return 0L;
    }
    
    @Override
    public long getSize() {
        return cacheManager.getEstimatedSize();
    }

    @Override
    public long getEstimatedSize() {
        return cacheManager.getEstimatedSize();
    }

    @Override
    public long getEvictionCount() {
        return 0L;
    }

    @Override
    public double getAverageLoadPenalty() {
        return 0.0;
    }
}