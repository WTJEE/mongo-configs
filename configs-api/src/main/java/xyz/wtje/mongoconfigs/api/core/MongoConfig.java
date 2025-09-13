package xyz.wtje.mongoconfigs.api.core;

import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public abstract class MongoConfig<T extends MongoConfig<T>> {

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

    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> saveAsync() {
        if (configManager != null) {
            return configManager.setObject(this);
        }
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<T> loadAsync() {
        if (configManager != null) {
            return configManager.getObject(getClass()).thenApply(result -> (T) result);
        }
        return CompletableFuture.completedFuture((T) this);
    }

    @SuppressWarnings("unchecked")
    public static <X extends MongoConfig<X>> X getConfigOrGenerate(ConfigManager cm, Class<X> type, Supplier<X> generator) {
        return cm.getConfigOrGenerate(type, generator).join();
    }
}
