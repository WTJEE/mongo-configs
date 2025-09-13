package xyz.wtje.mongoconfigs.api.core;

import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.Messages;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class MongoMessages<T extends MongoMessages<T>> {

    protected transient ConfigManager configManager;
    protected String defaultLang = "en";
    protected Set<String> langs = new HashSet<>();

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public String getDefaultLang() {
        return defaultLang;
    }

    public void setDefaultLang(String defaultLang) {
        this.defaultLang = defaultLang;
    }

    public Set<String> getLangs() {
        return langs;
    }

    public void setLangs(Set<String> langs) {
        this.langs = langs;
    }

    public String documentId() {
        ConfigsFileProperties annotation = getClass().getAnnotation(ConfigsFileProperties.class);
        if (annotation != null) {
            return annotation.name();
        }
        return getClass().getSimpleName().toLowerCase();
    }

    public Set<String> supportedLanguages() {
        SupportedLanguages annotation = getClass().getAnnotation(SupportedLanguages.class);
        if (annotation != null) {
            return Set.of(annotation.value());
        }
        return langs;
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

    public Messages messages() {
        if (configManager != null) {
            return configManager.findById(documentId());
        }
        return null;
    }

    public abstract String getMessage(String lang, String key, Map<String, Object> params);

    public abstract void putMessage(String lang, String key, String value);

    public abstract Map<String, String> allKeys(String lang);
}
// removed per request
