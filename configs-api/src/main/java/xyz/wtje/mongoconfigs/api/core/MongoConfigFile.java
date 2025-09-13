package xyz.wtje.mongoconfigs.api.core;

import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;

public abstract class MongoConfigFile<T extends MongoConfigFile<T>> {

    protected transient ConfigManager configManager;

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public String documentId() {
        ConfigsFileProperties annotation = getClass().getAnnotation(ConfigsFileProperties.class);
        if (annotation != null) {
            return annotation.name();
        }
        return getClass().getSimpleName().toLowerCase();
    }

    public void save() {
        if (configManager != null) {
            configManager.saveObject(this);
        }
    }

    @SuppressWarnings("unchecked")
    public T load() {
        if (configManager != null) {
            return (T) configManager.loadObject(getClass());
        }
        return (T) this;
    }
}
