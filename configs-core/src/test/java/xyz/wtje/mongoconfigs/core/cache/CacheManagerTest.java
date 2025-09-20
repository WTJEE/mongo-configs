package xyz.wtje.mongoconfigs.core.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CacheManagerTest {

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new CacheManager();
    }

    @Test
    void testBasicMessageOperations() {
        String collection = "test-collection";
        String language = "en";
        String key = "test.key";
        String value = "Test Value";

        cacheManager.putMessage(collection, language, key, value);
        String result = cacheManager.getMessage(collection, language, key);

        assertEquals(value, result);
    }

    @Test
    void testMessageWithDefault() {
        String collection = "test-collection";
        String language = "en";
        String key = "nonexistent.key";
        String defaultValue = "Default Value";

        String result = cacheManager.getMessage(collection, language, key, defaultValue);

        assertEquals(defaultValue, result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testAsyncMessageOperations() {
        String collection = "test-collection";
        String language = "en";
        String key = "test.key";
        String value = "Test Value";

        CompletableFuture<Void> putFuture = cacheManager.putMessageAsync(collection, language, key, value);

        assertDoesNotThrow(() -> putFuture.join());

        CompletableFuture<String> getFuture = cacheManager.getMessageAsync(collection, language, key);
        String result = getFuture.join();

        assertEquals(value, result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testAsyncMessageWithDefault() {
        String collection = "test-collection";
        String language = "en";
        String key = "nonexistent.key";
        String defaultValue = "Default Value";

        CompletableFuture<String> getFuture = cacheManager.getMessageAsync(collection, language, key, defaultValue);
        String result = getFuture.join();

        assertEquals(defaultValue, result);
    }

    @Test
    void testPutMessageData() {
        String collection = "test-collection";
        String language = "en";
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", "value2");

        cacheManager.putMessageData(collection, language, data);

        assertEquals("value1", cacheManager.getMessage(collection, language, "key1"));
        assertEquals("value2", cacheManager.getMessage(collection, language, "key2"));
    }

    @Test
    void testPutMessageDataFlattensNestedMaps() {
        String collection = "test-collection";
        String language = "en";

        Map<String, Object> data = new HashMap<>();
        Map<String, Object> gui = new HashMap<>();
        gui.put("title", "Select Language");
        gui.put("description", List.of("Line 1", "Line 2"));
        data.put("commands", Map.of("gui", gui));

        cacheManager.putMessageData(collection, language, data);

        assertEquals("Select Language", cacheManager.getMessage(collection, language, "commands.gui.title"));
        List<String> description = cacheManager.getMessage(collection, language, "commands.gui.description", List.of());
        assertEquals(List.of("Line 1", "Line 2"), description);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPutMessageDataAsync() {
        String collection = "test-collection";
        String language = "en";
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", "value2");

        CompletableFuture<Void> putFuture = cacheManager.putMessageDataAsync(collection, language, data);

        assertDoesNotThrow(() -> putFuture.join());

        assertEquals("value1", cacheManager.getMessage(collection, language, "key1"));
        assertEquals("value2", cacheManager.getMessage(collection, language, "key2"));
    }

    @Test
    void testConfigOperations() {
        String collection = "test-collection";
        Map<String, Object> data = new HashMap<>();
        data.put("config1", "value1");
        data.put("config2", 42);

        cacheManager.putConfigData(collection, data);

        assertEquals("value1", cacheManager.get(collection + ":config1", "default"));
        assertEquals(42, cacheManager.get(collection + ":config2", 0));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testAsyncConfigOperations() {
        String key = "test-key";
        String value = "test-value";

        CompletableFuture<Void> putFuture = cacheManager.putAsync(key, value);
        assertDoesNotThrow(() -> putFuture.join());

        CompletableFuture<String> getFuture = cacheManager.getAsync(key, "default");
        String result = getFuture.join();

        assertEquals(value, result);
    }

    @Test
    void testHasCollection() {
        String collection = "test-collection";
        assertFalse(cacheManager.hasCollection(collection));

        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        cacheManager.putConfigData(collection, data);

        assertTrue(cacheManager.hasCollection(collection));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testHasCollectionAsync() {
        String collection = "test-collection";

        CompletableFuture<Boolean> hasFuture1 = cacheManager.hasCollectionAsync(collection);
        assertFalse(hasFuture1.join());

        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        cacheManager.putConfigData(collection, data);

        CompletableFuture<Boolean> hasFuture2 = cacheManager.hasCollectionAsync(collection);
        assertTrue(hasFuture2.join());
    }

    @Test
    void testInvalidateCollection() {
        String collection = "test-collection";
        String language = "en";

        cacheManager.putMessage(collection, language, "msg1", "value1");
        Map<String, Object> configData = new HashMap<>();
        configData.put("config1", "value1");
        cacheManager.putConfigData(collection, configData);

        assertTrue(cacheManager.hasCollection(collection));

        cacheManager.invalidateCollection(collection);

        assertFalse(cacheManager.hasCollection(collection));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testInvalidateCollectionAsync() {
        String collection = "test-collection";
        String language = "en";

        cacheManager.putMessage(collection, language, "msg1", "value1");
        Map<String, Object> configData = new HashMap<>();
        configData.put("config1", "value1");
        cacheManager.putConfigData(collection, configData);

        assertTrue(cacheManager.hasCollection(collection));

        CompletableFuture<Void> invalidateFuture = cacheManager.invalidateCollectionAsync(collection);
        assertDoesNotThrow(() -> invalidateFuture.join());

        assertFalse(cacheManager.hasCollection(collection));
    }

    @Test
    void testInvalidateAll() {
        cacheManager.putMessage("col1", "en", "key1", "value1");
        cacheManager.put("test-key", "test-value");

        assertTrue(cacheManager.getEstimatedSize() > 0);

        cacheManager.invalidateAll();

        assertEquals(0, cacheManager.getEstimatedSize());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testInvalidateAllAsync() {
        cacheManager.putMessage("col1", "en", "key1", "value1");
        cacheManager.put("test-key", "test-value");

        assertTrue(cacheManager.getEstimatedSize() > 0);

        CompletableFuture<Void> invalidateFuture = cacheManager.invalidateAllAsync();
        assertDoesNotThrow(() -> invalidateFuture.join());

        assertEquals(0, cacheManager.getEstimatedSize());
    }

    @Test
    void testStatistics() {
        long initialConfigRequests = cacheManager.getConfigRequests();
        long initialMessageRequests = cacheManager.getMessageRequests();

        cacheManager.get("test-key", "default");
        cacheManager.getMessage("col", "en", "key");

        assertEquals(initialConfigRequests + 1, cacheManager.getConfigRequests());
        assertEquals(initialMessageRequests + 1, cacheManager.getMessageRequests());
    }

    @Test
    void testCustomConfiguration() {
        CacheManager customCache = new CacheManager(1000, Duration.ofMinutes(10));

        customCache.putMessage("col", "en", "key", "value");
        String result = customCache.getMessage("col", "en", "key");

        assertEquals("value", result);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCleanUpAsync() {
        cacheManager.putMessage("col1", "en", "key1", "value1");

        CompletableFuture<Void> cleanupFuture = cacheManager.cleanUpAsync();
        assertDoesNotThrow(() -> cleanupFuture.join());
    }

    @Test
    void testEmptyDataHandling() {
        cacheManager.putMessageData("col", "en", null);
        cacheManager.putConfigData("col", null);

        assertDoesNotThrow(() -> cacheManager.putMessageDataAsync("col", "en", null).join());
        assertDoesNotThrow(() -> cacheManager.putConfigDataAsync("col", null).join());

        Map<String, Object> emptyData = new HashMap<>();
        assertDoesNotThrow(() -> cacheManager.putMessageDataAsync("col", "en", emptyData).join());
        assertDoesNotThrow(() -> cacheManager.putConfigDataAsync("col", emptyData).join());
    }
}


