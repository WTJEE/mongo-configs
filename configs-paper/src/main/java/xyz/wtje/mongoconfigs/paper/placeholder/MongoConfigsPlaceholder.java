package xyz.wtje.mongoconfigs.paper.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.wtje.mongoconfigs.paper.MongoConfigsPlugin;
import xyz.wtje.mongoconfigs.paper.impl.LanguageManagerImpl;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MongoConfigsPlaceholder extends PlaceholderExpansion {

    private final MongoConfigsPlugin plugin;
    private final LanguageManagerImpl languageManager;
    private final ConfigManagerImpl configManager;
    
    // Cache for async placeholder results
    private final ConcurrentHashMap<String, CachedValue> cache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 5000; // 5 seconds cache

    public MongoConfigsPlaceholder(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mongoconfigs";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        // Parse the placeholder format: %mongoconfigs_<type>_<collection>_<key>%
        String[] parts = params.split("_", 3);
        
        if (parts.length < 2) {
            return "";
        }

        String type = parts[0];
        
        switch (type) {
            case "language":
                return getPlayerLanguage(player);
                
            case "msg":
            case "message":
                if (parts.length < 3) return "";
                String collection = parts[1];
                String key = parts[2];
                return getMessage(player, collection, key);
                
            case "config":
                if (parts.length < 3) return "";
                String configCollection = parts[1];
                String configKey = parts[2];
                return getConfig(configCollection, configKey);
                
            case "langname":
                return getLanguageName(player);
                
            default:
                return "";
        }
    }

    private String getPlayerLanguage(Player player) {
        String cacheKey = "lang_" + player.getUniqueId();
        CachedValue cached = getCached(cacheKey);
        
        if (cached != null) {
            return cached.value;
        }
        
        // Start async load
        CompletableFuture.supplyAsync(() -> {
            try {
                return languageManager.getPlayerLanguage(player.getUniqueId().toString())
                    .get(100, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                return languageManager.getLanguageConfiguration().getDefaultLanguage();
            }
        }).thenAccept(lang -> cache.put(cacheKey, new CachedValue(lang)));
        
        // Return default for now
        return languageManager.getLanguageConfiguration().getDefaultLanguage();
    }
    
    private String getLanguageName(Player player) {
        String lang = getPlayerLanguage(player);
        String cacheKey = "langname_" + lang;
        CachedValue cached = getCached(cacheKey);
        
        if (cached != null) {
            return cached.value;
        }
        
        // Start async load
        CompletableFuture.supplyAsync(() -> {
            try {
                return languageManager.getLanguageDisplayName(lang)
                    .get(100, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                return lang;
            }
        }).thenAccept(name -> cache.put(cacheKey, new CachedValue(name)));
        
        return lang;
    }

    private String getMessage(Player player, String collection, String key) {
        String lang = getPlayerLanguage(player);
        String cacheKey = "msg_" + collection + "_" + lang + "_" + key;
        CachedValue cached = getCached(cacheKey);
        
        if (cached != null) {
            return cached.value;
        }
        
        // Start async load
        CompletableFuture.supplyAsync(() -> {
            try {
                return configManager.getMessageAsync(collection, lang, key)
                    .get(100, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                return "";
            }
        }).thenAccept(msg -> {
            if (msg != null) {
                cache.put(cacheKey, new CachedValue(msg));
            }
        });
        
        // Try sync get with timeout
        String result = configManager.getMessage(collection, lang, key);
        return result != null ? result : "";
    }

    private String getConfig(String collection, String key) {
        String cacheKey = "cfg_" + collection + "_" + key;
        CachedValue cached = getCached(cacheKey);
        
        if (cached != null) {
            return cached.value;
        }
        
        // Start async load
        CompletableFuture.supplyAsync(() -> {
            try {
                Object value = configManager.getAsync(collection + ":" + key)
                    .get(100, TimeUnit.MILLISECONDS);
                return value != null ? value.toString() : "";
            } catch (Exception e) {
                return "";
            }
        }).thenAccept(cfg -> cache.put(cacheKey, new CachedValue(cfg)));
        
        // Try sync get
        Object result = configManager.get(collection + ":" + key, null);
        return result != null ? result.toString() : "";
    }

    private CachedValue getCached(String key) {
        CachedValue cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        if (cached != null) {
            cache.remove(key); // Remove expired
        }
        return null;
    }
    
    // Clear cache on reload
    public void clearCache() {
        cache.clear();
    }

    private static class CachedValue {
        final String value;
        final long timestamp;
        
        CachedValue(String value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }
    }
}