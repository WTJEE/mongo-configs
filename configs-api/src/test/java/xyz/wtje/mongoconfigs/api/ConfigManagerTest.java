package xyz.wtje.mongoconfigs.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigManagerTest {

    @Mock
    private ConfigManager configManager;

    @Test
    void testReloadAll() {
        when(configManager.reloadAll()).thenReturn(CompletableFuture.completedFuture(null));
        CompletableFuture<Void> result = configManager.reloadAll();
        assertNotNull(result);
        verify(configManager).reloadAll();
    }

    @Test
    void testSetAndGet() {
        String id = "test-id";
        String value = "test-value";
        when(configManager.set(id, value)).thenReturn(CompletableFuture.completedFuture(null));
        when(configManager.get(id, String.class)).thenReturn(CompletableFuture.completedFuture(value));
        configManager.set(id, value).join();
        assertEquals(value, configManager.get(id, String.class).join());
        verify(configManager).set(id, value);
        verify(configManager).get(id, String.class);
    }

    @Test
    void testSetObjectAndGetObject() {
        TestConfig cfg = new TestConfig();
        cfg.setValue("v1");
        when(configManager.setObject(cfg)).thenReturn(CompletableFuture.completedFuture(null));
        when(configManager.getObject(TestConfig.class)).thenReturn(CompletableFuture.completedFuture(cfg));
        configManager.setObject(cfg).join();
        assertEquals(cfg, configManager.getObject(TestConfig.class).join());
        verify(configManager).setObject(cfg);
        verify(configManager).getObject(TestConfig.class);
    }

    @Test
    void testGetConfigOrGenerate() {
        TestConfig cfg = new TestConfig();
        Supplier<TestConfig> generator = () -> cfg;
        when(configManager.getConfigOrGenerate(TestConfig.class, generator)).thenReturn(CompletableFuture.completedFuture(cfg));
        assertEquals(cfg, configManager.getConfigOrGenerate(TestConfig.class, generator).join());
        verify(configManager).getConfigOrGenerate(TestConfig.class, generator);
    }

    @Test
    void testCreateAndGetOrCreateFromObject() {
        Object msgObj = new Object();
        Messages msgs = mock(Messages.class);
        when(configManager.createFromObject(msgObj)).thenReturn(CompletableFuture.completedFuture(null));
        when(configManager.getOrCreateFromObject(msgObj)).thenReturn(CompletableFuture.completedFuture(msgs));
        assertNotNull(configManager.createFromObject(msgObj));
        assertEquals(msgs, configManager.getOrCreateFromObject(msgObj).join());
        verify(configManager).createFromObject(msgObj);
        verify(configManager).getOrCreateFromObject(msgObj);
    }

    @ConfigsFileProperties(name = "test-config")
    static class TestConfig {
        private String value;
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}

