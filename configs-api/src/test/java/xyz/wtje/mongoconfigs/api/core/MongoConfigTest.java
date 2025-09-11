package xyz.wtje.mongoconfigs.api.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongoConfigTest {

    @Mock
    private ConfigManager configManager;

    private TestMongoConfig testConfig;

    @BeforeEach
    void setUp() {
        testConfig = new TestMongoConfig();
        testConfig.setValue("test-value");
        testConfig.setConfigManager(configManager);
    }

    @Test
    void testDocumentId() {
        String documentId = testConfig.documentId();
        assertEquals("test-config", documentId);
    }

    @Test
    void testSave() {
        assertDoesNotThrow(() -> testConfig.save());
        verify(configManager).saveObject(testConfig);
    }

    @Test
    void testLoad() {
        when(configManager.loadObject(TestMongoConfig.class)).thenReturn(testConfig);

        TestMongoConfig result = testConfig.load();

        assertNotNull(result);
        assertEquals(testConfig, result);
        verify(configManager).loadObject(TestMongoConfig.class);
    }

    @Test
    void testSaveAsync() {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        when(configManager.setObject(testConfig)).thenReturn(future);

        CompletableFuture<Void> result = testConfig.saveAsync();

        assertNotNull(result);
        assertDoesNotThrow(() -> result.join());
        verify(configManager).setObject(testConfig);
    }

    @Test
    void testLoadAsync() {
        CompletableFuture<TestMongoConfig> future = CompletableFuture.completedFuture(testConfig);
        when(configManager.getObject(TestMongoConfig.class)).thenReturn(future);

        CompletableFuture<TestMongoConfig> result = testConfig.loadAsync();

        assertNotNull(result);
        assertEquals(testConfig, result.join());
        verify(configManager).getObject(TestMongoConfig.class);
    }

    @Test
    void testGetConfigOrGenerate() {
        Supplier<TestMongoConfig> generator = TestMongoConfig::new;
        CompletableFuture<TestMongoConfig> future = CompletableFuture.completedFuture(testConfig);
        when(configManager.getConfigOrGenerate(TestMongoConfig.class, generator)).thenReturn(future);

        TestMongoConfig result = MongoConfig.getConfigOrGenerate(configManager, TestMongoConfig.class, generator);

        assertNotNull(result);
        assertEquals(testConfig, result);
        verify(configManager).getConfigOrGenerate(TestMongoConfig.class, generator);
    }

    @Test
    void testConfigManagerGetterSetter() {
        ConfigManager newManager = mock(ConfigManager.class);
        testConfig.setConfigManager(newManager);

        assertEquals(newManager, testConfig.getConfigManager());
    }

    @Test
    void testSaveWithoutConfigManager() {
        testConfig.setConfigManager(null);

        assertDoesNotThrow(() -> testConfig.save());
    }

    @Test
    void testLoadWithoutConfigManager() {
        testConfig.setConfigManager(null);

        TestMongoConfig result = testConfig.load();
        assertEquals(testConfig, result);
    }

    @Test
    void testSaveAsyncWithoutConfigManager() {
        testConfig.setConfigManager(null);

        CompletableFuture<Void> result = testConfig.saveAsync();
        assertNotNull(result);
        assertDoesNotThrow(() -> result.join());
    }

    @Test
    void testLoadAsyncWithoutConfigManager() {
        testConfig.setConfigManager(null);

        CompletableFuture<TestMongoConfig> result = testConfig.loadAsync();
        assertNotNull(result);
        assertEquals(testConfig, result.join());
    }

    @ConfigsFileProperties(name = "test-config")
    static class TestMongoConfig extends MongoConfig<TestMongoConfig> {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestMongoConfig that = (TestMongoConfig) obj;
            return java.util.Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(value);
        }
    }
}
