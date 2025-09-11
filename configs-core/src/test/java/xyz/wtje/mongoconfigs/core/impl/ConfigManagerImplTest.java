package xyz.wtje.mongoconfigs.core.impl;

import org.junit.jupiter.api.Test;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigManagerImplTest {
    @Test
    void constructInitializeShutdown() {
        MongoConfig cfg = new MongoConfig();
        cfg.setDatabase("clean_placeholder_cfg_mgr_impl");
        ConfigManagerImpl impl = new ConfigManagerImpl(cfg);
        impl.initialize();
        assertNotNull(impl.getMongoManager());
        impl.shutdown();
    }
}
