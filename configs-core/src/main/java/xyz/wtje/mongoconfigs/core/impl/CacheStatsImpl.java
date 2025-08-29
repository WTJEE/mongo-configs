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
        com.github.benmanes.caffeine.cache.stats.CacheStats configStats = cacheManager.getConfigCacheStats();
        com.github.benmanes.caffeine.cache.stats.CacheStats messageStats = cacheManager.getMessageCacheStats();
        
        long totalHits = configStats.hitCount() + messageStats.hitCount();
        long totalRequests = configStats.requestCount() + messageStats.requestCount();
        
        return totalRequests > 0 ? (double) totalHits / totalRequests : 0.0;
    }
    
    @Override
    public long getRequestCount() {
        return cacheManager.getConfigCacheStats().requestCount() + 
               cacheManager.getMessageCacheStats().requestCount();
    }
    
    @Override
    public long getHitCount() {
        return cacheManager.getConfigCacheStats().hitCount() + 
               cacheManager.getMessageCacheStats().hitCount();
    }
    
    @Override
    public long getMissCount() {
        return cacheManager.getConfigCacheStats().missCount() + 
               cacheManager.getMessageCacheStats().missCount();
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
        return cacheManager.getConfigCacheStats().evictionCount() + 
               cacheManager.getMessageCacheStats().evictionCount();
    }

    @Override
    public double getAverageLoadPenalty() {
        com.github.benmanes.caffeine.cache.stats.CacheStats configStats = cacheManager.getConfigCacheStats();
        com.github.benmanes.caffeine.cache.stats.CacheStats messageStats = cacheManager.getMessageCacheStats();

        long totalLoadTime = (long) (configStats.totalLoadTime() + messageStats.totalLoadTime());
        long totalLoads = configStats.loadCount() + messageStats.loadCount();

        return totalLoads > 0 ? (double) totalLoadTime / totalLoads : 0.0;
    }
}