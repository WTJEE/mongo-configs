package xyz.wtje.mongoconfigs.velocity.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class PluginConfiguration {

    private final Map<String, Object> root;

    @SuppressWarnings("unchecked")
    public PluginConfiguration(Path configFile) {
        try (InputStream in = Files.newInputStream(configFile)) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object data = yaml.load(in);
            this.root = (Map<String, Object>) (data != null ? data : Map.of());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }
    }

    private Object get(String path, Object def) {
        String[] parts = path.split("\\.");
        Object cur = root;
        for (String p : parts) {
            if (!(cur instanceof Map)) return def;
            Object next = ((Map<?, ?>) cur).get(p);
            if (next == null) return def;
            cur = next;
        }
        return cur;
    }

    public String getMongoConnectionString() {
        Object v = get("mongodb.connection-string", "mongodb:");
        return String.valueOf(v);
    }

    public String getMongoDatabase() {
        Object v = get("mongodb.database", "minecraft");
        return String.valueOf(v);
    }

    public String getPlayerLanguagesDatabase() {
        Object v = get("mongodb.player-languages.database", getMongoDatabase());
        return String.valueOf(v);
    }

    public String getPlayerLanguagesCollection() {
        Object v = get("mongodb.player-languages.collection", "player_languages");
        return String.valueOf(v);
    }

    public String getTypedConfigsCollection() {
        Object v = get("mongodb.collections.typed-configs", "typed_configs");
        return String.valueOf(v);
    }

    public String getConfigsCollection() {
        Object v = get("mongodb.collections.configs", "configs");
        return String.valueOf(v);
    }

    public int getMongoMaxPoolSize() {
        Object v = get("mongodb.connection-pool.max-size", 20);
        return Integer.parseInt(String.valueOf(v));
    }

    public int getMongoMinPoolSize() {
        Object v = get("mongodb.connection-pool.min-size", 5);
        return Integer.parseInt(String.valueOf(v));
    }

    public long getMongoMaxIdleTime() {
        Object v = get("mongodb.connection-pool.max-idle-time-ms", 60000);
        return Long.parseLong(String.valueOf(v));
    }

    public long getMongoMaxLifeTime() {
        Object v = get("mongodb.connection-pool.max-life-time-ms", 1800000);
        return Long.parseLong(String.valueOf(v));
    }

    public int getMongoServerSelectionTimeout() {
        Object v = get("mongodb.timeouts.server-selection-ms", 5000);
        return Integer.parseInt(String.valueOf(v));
    }

    public int getMongoSocketTimeout() {
        Object v = get("mongodb.timeouts.socket-timeout-ms", 10000);
        return Integer.parseInt(String.valueOf(v));
    }

    public int getMongoConnectTimeout() {
        Object v = get("mongodb.timeouts.connect-timeout-ms", 5000);
        return Integer.parseInt(String.valueOf(v));
    }

    public long getCacheMaxSize() {
        Object v = get("cache.max-size", 10000);
        return Long.parseLong(String.valueOf(v));
    }

    public long getCacheTtlSeconds() {
        Object v = get("cache.ttl-seconds", 0);
        return Long.parseLong(String.valueOf(v));
    }

    public boolean isCacheRecordStats() {
        Object v = get("cache.record-stats", true);
        return Boolean.parseBoolean(String.valueOf(v));
    }

    public long getCacheRefreshAfterSeconds() {
        Object v = get("cache.refresh-after-seconds", 300);
        return Long.parseLong(String.valueOf(v));
    }

    public int getIoThreads() {
        Object v = get("performance.io-threads", 4);
        return Integer.parseInt(String.valueOf(v));
    }

    public int getWorkerThreads() {
        Object v = get("performance.worker-threads", 8);
        return Integer.parseInt(String.valueOf(v));
    }

    public boolean isChangeStreamsEnabled() {
        return true;
    }

    public int getChangeStreamResumeRetries() {
        return 3;
    }

    public long getChangeStreamResumeDelay() {
        return 1000;
    }

    public boolean isDebugLogging() {
        Object v = get("logging.debug", false);
        return Boolean.parseBoolean(String.valueOf(v));
    }

    public boolean isVerboseLogging() {
        Object v = get("logging.verbose", false);
        return Boolean.parseBoolean(String.valueOf(v));
    }

    
    public String getDefaultLanguage() {
        Object v = get("languages.default", "en");
        return String.valueOf(v);
    }

    public java.util.List<String> getSupportedLanguages() {
        Object v = get("languages.supported", java.util.List.of("en"));
        if (v instanceof java.util.List<?> list) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (Object o : list) out.add(String.valueOf(o));
            return out;
        }
        return java.util.List.of("en");
    }

    public java.util.Map<String, String> getLanguageDisplayNames() {
        Object sec = get("languages.display-names", java.util.Map.of());
        java.util.Map<String, String> out = new java.util.HashMap<>();
        if (sec instanceof java.util.Map<?, ?> m) {
            for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        return out;
    }

    public java.util.List<String> getIgnoredDatabases() {
        Object v = get("mongodb.ignore.databases", java.util.List.of());
        if (v instanceof java.util.List<?> list) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (Object o : list) out.add(String.valueOf(o));
            return out;
        }
        return java.util.List.of();
    }

    public java.util.List<String> getIgnoredCollections() {
        Object v = get("mongodb.ignore.collections", java.util.List.of());
        if (v instanceof java.util.List<?> list) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (Object o : list) out.add(String.valueOf(o));
            return out;
        }
        return java.util.List.of();
    }
}
