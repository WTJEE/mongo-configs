package xyz.wtje.mongoconfigs.core.impl;

import org.junit.jupiter.api.Test;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.mongoconfigs.core.util.ColorProcessor;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerSmokeTest {

    @Test
    void constructAndConfigureColorProcessor() {
        MongoConfig cfg = new MongoConfig();
        cfg.setDatabase("test_db_smoke");
        cfg.setDebugLogging(false);
        cfg.setVerboseLogging(false);

        ConfigManagerImpl manager = new ConfigManagerImpl(cfg);
        manager.setColorProcessor(new ColorProcessor() {
            @Override
            public String colorize(String message) {
                return message;
            }

            @Override
            public String stripColors(String message) {
                return message;
            }

            @Override
            public void clearCache() {
            }
        });

        manager.initialize();

        assertNotNull(manager.getMongoManager());
        assertNotNull(manager.getTypedConfigManager());

        manager.shutdown();
    }
}

