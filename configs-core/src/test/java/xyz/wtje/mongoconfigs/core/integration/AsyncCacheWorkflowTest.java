package xyz.wtje.mongoconfigs.core.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import xyz.wtje.mongoconfigs.core.cache.CacheManager;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AsyncCacheWorkflowTest {

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void asyncWorkflowThroughCache() {
        MongoConfig cfg = new MongoConfig();
        cfg.setDatabase("test_async_cache");
        cfg.setDebugLogging(true);
        ConfigManagerImpl manager = new ConfigManagerImpl(cfg);
        manager.initialize();

        CacheManager cache = manager.getCacheManager();

        // Simulate pre-populated config + messages
        cache.putConfigData("collectionA", Map.of("key1", "value1", "_system.supported_languages", java.util.List.of("en")));
        cache.putMessageData("collectionA", "en", Map.of("msg1", "Hello"));

        String cfgVal = manager.getConfigAsync("collectionA", "key1", "default").join();
        assertEquals("value1", cfgVal);

        String msgVal = manager.getMessageAsync("collectionA", "en", "msg1").join();
        assertEquals("Hello", msgVal);

        manager.invalidateAllAsync().join();
        String afterInvalidate = manager.getConfigAsync("collectionA", "key1", "fallback").join();
        assertEquals("fallback", afterInvalidate);

        manager.shutdown();
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void batchReloadNoCollectionsSafe() {
        MongoConfig cfg = new MongoConfig();
        cfg.setDatabase("test_empty_batch");
        ConfigManagerImpl manager = new ConfigManagerImpl(cfg);
        manager.initialize();

        assertDoesNotThrow(() -> manager.reloadCollectionsBatchAsync(Set.of(), 2).join());
        manager.shutdown();
    }
}

