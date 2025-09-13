package xyz.wtje.mongoconfigs.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.core.Annotations;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigManagerTest {

    @Mock
    private ConfigManager configManager;

    private TestConfig testConfig;
    private TestMessage testMessage;

    @BeforeEach
    void setUp() {
        testConfig = new TestConfig();
        testConfig.setValue("test-value");

        testMessage = new TestMessage();
    }

    @Test
    void testReloadAll() {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        when(configManager.reloadAll()).thenReturn(future);

        CompletableFuture<Void> result = configManager.reloadAll();

        assertNotNull(result);
        verify(configManager).reloadAll();
    }

    @Test
    void testSetAndGet() {
        String id = "test-id";
        String value = "test-value";
        CompletableFuture<Void> setFuture = CompletableFuture.completedFuture(null);
        CompletableFuture<String> getFuture = CompletableFuture.completedFuture(value);

        when(configManager.set(id, value)).thenReturn(setFuture);
        when(configManager.get(id, String.class)).thenReturn(getFuture);

        CompletableFuture<Void> setResult = configManager.set(id, value);
        CompletableFuture<String> getResult = configManager.get(id, String.class);

        assertNotNull(setResult);
        assertNotNull(getResult);
        assertEquals(value, getResult.join());

        verify(configManager).set(id, value);
        verify(configManager).get(id, String.class);
    }

    @Test
    void testSetObjectAndGetObject() {
        CompletableFuture<Void> setFuture = CompletableFuture.completedFuture(null);
        CompletableFuture<TestConfig> getFuture = CompletableFuture.completedFuture(testConfig);

        when(configManager.setObject(testConfig)).thenReturn(setFuture);
        when(configManager.getObject(TestConfig.class)).thenReturn(getFuture);

        CompletableFuture<Void> setResult = configManager.setObject(testConfig);
        CompletableFuture<TestConfig> getResult = configManager.getObject(TestConfig.class);

        assertNotNull(setResult);
        assertNotNull(getResult);
        assertEquals(testConfig, getResult.join());

        verify(configManager).setObject(testConfig);
        verify(configManager).getObject(TestConfig.class);
    }

    @Test
    void testGetConfigOrGenerate() {
        Supplier<TestConfig> generator = () -> testConfig;
        CompletableFuture<TestConfig> future = CompletableFuture.completedFuture(testConfig);

        when(configManager.getConfigOrGenerate(TestConfig.class, generator)).thenReturn(future);

        CompletableFuture<TestConfig> result = configManager.getConfigOrGenerate(TestConfig.class, generator);

        assertNotNull(result);
        assertEquals(testConfig, result.join());
        verify(configManager).getConfigOrGenerate(TestConfig.class, generator);
    }

    @Test
    void testFindById() {
        String id = "test-messages";
        Messages messages = mock(Messages.class);

        when(configManager.findById(id)).thenReturn(messages);

        Messages result = configManager.findById(id);

        assertNotNull(result);
        assertEquals(messages, result);
        verify(configManager).findById(id);
    }

    @Test
    void testMessagesOf() {
        Messages messages = mock(Messages.class);

        when(configManager.messagesOf(TestConfig.class)).thenReturn(messages);

        Messages result = configManager.messagesOf(TestConfig.class);

        assertNotNull(result);
        assertEquals(messages, result);
        verify(configManager).messagesOf(TestConfig.class);
    }

    @Test
    void testSynchronousWrappers() {
        ConfigManager realManager = new ConfigManager() {
            @Override
            public CompletableFuture<Void> reloadAll() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public <T> CompletableFuture<Void> set(String id, T value) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public <T> CompletableFuture<T> get(String id, Class<T> type) {
                return CompletableFuture.completedFuture(type.cast("test-value"));
            }

            @Override
            public <T> CompletableFuture<Void> setObject(T pojo) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public <T> CompletableFuture<T> getObject(Class<T> type) {
                return CompletableFuture.completedFuture(type.cast(testConfig));
            }

            @Override
            public <T> CompletableFuture<T> getConfigOrGenerate(Class<T> type, Supplier<T> generator) {
                return CompletableFuture.completedFuture(generator.get());
            }
            
            @Override
            public Messages getMessagesOrGenerate(Class<?> messageClass, Supplier<Void> generator) {
                return findById("test-messages");
            }
            
            @Override
            public <T> void createFromObject(T messageObject) {
                // Test implementation
            }
            
            @Override
            public <T> Messages getOrCreateFromObject(T messageObject) {
                return findById("test-messages");
            }

            @Override
            public Messages findById(String id) {
                return mock(Messages.class);
            }
        };

        assertDoesNotThrow(() -> realManager.saveObject(testConfig));
        assertDoesNotThrow(() -> realManager.loadObject(TestConfig.class));
        assertDoesNotThrow(() -> realManager.save("test-id", "test-value"));
        assertDoesNotThrow(() -> realManager.load("test-id", String.class));
    }

    @ConfigsFileProperties(name = "test-config")
    static class TestConfig {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @ConfigsFileProperties(name = "test-messages")
    static class TestMessage {
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
