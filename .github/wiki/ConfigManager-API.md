# ConfigManager API

Complete reference for the ConfigManager - the core interface for configuration management in MongoDB Configs API.

## 🎯 Getting ConfigManager Instance

```java
// Get the global ConfigManager instance
ConfigManager cm = MongoConfigsAPI.getConfigManager();
```

---

## 📋 Class-Based Configuration Methods

### Loading Configurations

#### `loadObject(Class<T> type)`
Synchronously loads a configuration object.

```java
// Basic loading
ServerConfig config = cm.loadObject(ServerConfig.class);

// With error handling
try {
    ServerConfig config = cm.loadObject(ServerConfig.class);
    // Use config...
} catch (Exception e) {
    // Handle loading error
    getLogger().warning("Failed to load config: " + e.getMessage());
}
```

#### `getObject(Class<T> type)` 
Asynchronously loads a configuration object.

```java
// Async loading
CompletableFuture<ServerConfig> future = cm.getObject(ServerConfig.class);
future.thenAccept(config -> {
    // Use config when loaded
    getLogger().info("Max players: " + config.getMaxPlayers());
}).exceptionally(error -> {
    getLogger().severe("Failed to load config: " + error.getMessage());
    return null;
});

// Async with timeout
CompletableFuture<ServerConfig> config = cm.getObject(ServerConfig.class)
    .orTimeout(5, TimeUnit.SECONDS);
```

#### `getConfigOrGenerate(Class<T> type, Supplier<T> generator)`
Loads configuration or creates default if not exists.

```java
// Load or create with defaults
ServerConfig config = cm.getConfigOrGenerate(
    ServerConfig.class,
    () -> {
        ServerConfig defaultConfig = new ServerConfig();
        defaultConfig.setMaxPlayers(100);
        defaultConfig.setServerName("Default Server");
        return defaultConfig;
    }
);

// Lambda expression for simple defaults
PlayerSettings settings = cm.getConfigOrGenerate(
    PlayerSettings.class,
    PlayerSettings::new  // Use default constructor
);

// Async version
CompletableFuture<ServerConfig> configFuture = cm.getConfigOrGenerateAsync(
    ServerConfig.class,
    () -> createDefaultServerConfig()
);
```

### Saving Configurations

#### `saveObject(T object)`
Synchronously saves a configuration object.

```java
ServerConfig config = cm.loadObject(ServerConfig.class);
config.setMaxPlayers(200);
config.getBannedItems().add("bedrock");

// Save changes
cm.saveObject(config);  // ⚡ Syncs to all servers with Change Streams!
```

#### `setObject(T object)`
Asynchronously saves a configuration object.

```java
ServerConfig config = cm.loadObject(ServerConfig.class);
config.setMaxPlayers(200);

// Async save with callback
cm.setObject(config).thenRun(() -> {
    getLogger().info("✅ Configuration saved successfully!");
}).exceptionally(error -> {
    getLogger().severe("❌ Failed to save config: " + error.getMessage());
    return null;
});

// Async save with retry mechanism
CompletableFuture<Void> saveResult = cm.setObject(config)
    .handle((result, error) -> {
        if (error != null) {
            // Retry logic
            return cm.setObject(config);
        }
        return CompletableFuture.completedFuture(null);
    })
    .thenCompose(Function.identity());
```

---

## 🗝️ Key-Object Storage Methods

### Storing Data

#### `set(String key, T value)`
Stores a value with the specified key.

```java
// Store primitive values
cm.set("server.max_players", 100);
cm.set("server.name", "My Server");
cm.set("server.pvp_enabled", true);

// Store complex objects
PlayerData playerData = new PlayerData();
cm.set("player:" + playerId, playerData);

// Store collections
List<String> bannedItems = Arrays.asList("bedrock", "barrier");
cm.set("server.banned_items", bannedItems);

Map<String, Integer> worldSettings = Map.of(
    "spawn_radius", 100,
    "max_height", 256
);
cm.set("world.settings", worldSettings);
```

#### Async Storage

```java
// Async key-value storage
CompletableFuture<Void> future = cm.setAsync("player:" + playerId, playerData);

future.thenRun(() -> {
    getLogger().info("Player data saved!");
}).exceptionally(error -> {
    getLogger().severe("Failed to save player data: " + error.getMessage());
    return null;
});
```

### Retrieving Data

#### `get(String key, Class<T> type)`
Retrieves a value by key with type safety.

```java
// Get primitive values
Integer maxPlayers = cm.get("server.max_players", Integer.class);
String serverName = cm.get("server.name", String.class);
Boolean pvpEnabled = cm.get("server.pvp_enabled", Boolean.class);

// Get complex objects
PlayerData playerData = cm.get("player:" + playerId, PlayerData.class);

// Get collections with type erasure handling
@SuppressWarnings("unchecked")
List<String> bannedItems = cm.get("server.banned_items", List.class);

@SuppressWarnings("unchecked")
Map<String, Integer> worldSettings = cm.get("world.settings", Map.class);
```

#### `getOrDefault(String key, Class<T> type, T defaultValue)`
Gets value or returns default if not found.

```java
// Get with fallback values
Integer maxPlayers = cm.getOrDefault("server.max_players", Integer.class, 100);
String serverName = cm.getOrDefault("server.name", String.class, "Default Server");
Boolean pvpEnabled = cm.getOrDefault("server.pvp_enabled", Boolean.class, true);

// Get player data with default
PlayerData playerData = cm.getOrDefault(
    "player:" + playerId, 
    PlayerData.class, 
    new PlayerData()  // Default if not found
);
```

#### Async Retrieval

```java
// Async get
CompletableFuture<PlayerData> playerDataFuture = cm.getAsync("player:" + playerId, PlayerData.class);

playerDataFuture.thenAccept(playerData -> {
    if (playerData != null) {
        // Use player data
        getLogger().info("Player level: " + playerData.getLevel());
    } else {
        getLogger().info("Player data not found");
    }
});
```

---

## 📧 Message System Methods

### `messagesOf(Class<?> messageClass)`
Gets a Messages instance for multilingual support.

```java
// Get message system
Messages guiMessages = cm.messagesOf(GuiMessages.class);
Messages shopMessages = cm.messagesOf(ShopMessages.class);
Messages commandMessages = cm.messagesOf(CommandMessages.class);

// Use messages
String playerLang = languageManager.getPlayerLanguage(player.getUniqueId().toString());
String welcomeMsg = guiMessages.get(playerLang, "welcome.message", player.getName());
String shopTitle = shopMessages.get(playerLang, "shop.title");

player.sendMessage(ColorHelper.parseComponent(welcomeMsg));
```

### Advanced Message Usage

```java
public class MessageManager {
    
    private final Messages guiMessages;
    private final Messages shopMessages;
    private final LanguageManager languageManager;
    
    public MessageManager() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        this.guiMessages = cm.messagesOf(GuiMessages.class);
        this.shopMessages = cm.messagesOf(ShopMessages.class);
        this.languageManager = MongoConfigsAPI.getLanguageManager();
    }
    
    public void sendGuiMessage(Player player, String key, Object... args) {
        String lang = languageManager.getPlayerLanguage(player.getUniqueId().toString());
        String message = guiMessages.get(lang, key, args);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    public void sendShopMessage(Player player, String key, Object... args) {
        String lang = languageManager.getPlayerLanguage(player.getUniqueId().toString());
        String message = shopMessages.get(lang, key, args);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    public String getLocalizedMessage(Player player, Messages messageSystem, String key, Object... args) {
        String lang = languageManager.getPlayerLanguage(player.getUniqueId().toString());
        return messageSystem.get(lang, key, args);
    }
}
```

---

## 🔄 Cache Management Methods

### `invalidateCache()`
Clears all cached configurations.

```java
// Clear entire cache
cm.invalidateCache();
getLogger().info("All configuration cache cleared");
```

### `evictFromCache(Class<?> configClass)`
Removes specific configuration from cache.

```java
// Remove specific config from cache
cm.evictFromCache(ServerConfig.class);
cm.evictFromCache(EconomyConfig.class);

// Next access will reload from MongoDB
ServerConfig config = cm.loadObject(ServerConfig.class);  // Fresh from DB
```

### `isCached(Class<?> configClass)`
Checks if a configuration is currently cached.

```java
// Check cache status
boolean serverConfigCached = cm.isCached(ServerConfig.class);
boolean economyCached = cm.isCached(EconomyConfig.class);

getLogger().info("Server config cached: " + serverConfigCached);
getLogger().info("Economy config cached: " + economyCached);
```

### Advanced Caching

```java
public class CacheManager {
    
    private final ConfigManager cm;
    
    public CacheManager(ConfigManager cm) {
        this.cm = cm;
    }
    
    public void warmupCache() {
        // Pre-load frequently used configs
        cm.loadObject(ServerConfig.class);
        cm.loadObject(EconomyConfig.class);
        cm.loadObject(PvPConfig.class);
        
        getLogger().info("Configuration cache warmed up");
    }
    
    public void refreshCache(Class<?> configClass) {
        cm.evictFromCache(configClass);
        cm.loadObject(configClass);  // Reload fresh data
        
        getLogger().info("Refreshed cache for: " + configClass.getSimpleName());
    }
    
    public void smartCacheRefresh() {
        // Only refresh if data is stale (custom logic)
        if (shouldRefreshCache()) {
            cm.invalidateCache();
            warmupCache();
        }
    }
    
    private boolean shouldRefreshCache() {
        // Your cache staleness detection logic
        return System.currentTimeMillis() - lastCacheRefresh > Duration.ofMinutes(30).toMillis();
    }
}
```

---

## 🔄 Collection Management

### `reloadCollection(String collectionName)`
Reloads a specific configuration collection.

```java
// Reload specific collections
cm.reloadCollection("server-settings");
cm.reloadCollection("gui-messages");
cm.reloadCollection("economy-config");

getLogger().info("Configuration collection reloaded");
```

### `reloadCollectionsBatch(Set<String> collections, int maxConcurrency)`
Reloads multiple collections with concurrency control.

```java
// Batch reload with controlled concurrency
Set<String> collections = Set.of(
    "server-settings",
    "economy-config", 
    "pvp-config",
    "gui-messages"
);

cm.reloadCollectionsBatch(collections, 3);  // Max 3 concurrent reloads
getLogger().info("Batch reload completed");
```

### `reloadAll()`
Reloads all configurations asynchronously.

```java
// Reload everything
CompletableFuture<Void> reloadFuture = cm.reloadAll();

reloadFuture.thenRun(() -> {
    getLogger().info("✅ All configurations reloaded successfully!");
}).exceptionally(error -> {
    getLogger().severe("❌ Failed to reload configurations: " + error.getMessage());
    return null;
});

// Synchronous reload (blocking)
try {
    cm.reloadAll().join();  // Wait for completion
    getLogger().info("All configurations reloaded");
} catch (Exception e) {
    getLogger().severe("Reload failed: " + e.getMessage());
}
```

---

## 🧠 Advanced Usage Patterns

### Configuration Factory

```java
public class ConfigurationFactory {
    
    private final ConfigManager cm;
    private final Map<Class<?>, Object> configCache = new ConcurrentHashMap<>();
    
    public ConfigurationFactory() {
        this.cm = MongoConfigsAPI.getConfigManager();
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getConfig(Class<T> configClass) {
        return (T) configCache.computeIfAbsent(configClass, k -> cm.loadObject(configClass));
    }
    
    public <T> T getFreshConfig(Class<T> configClass) {
        configCache.remove(configClass);
        cm.evictFromCache(configClass);
        T config = cm.loadObject(configClass);
        configCache.put(configClass, config);
        return config;
    }
    
    public void refreshAll() {
        configCache.clear();
        cm.invalidateCache();
    }
}
```

### Configuration Validator

```java
public class ConfigurationValidator {
    
    private final ConfigManager cm;
    
    public ConfigurationValidator(ConfigManager cm) {
        this.cm = cm;
    }
    
    public <T> ValidationResult validateConfig(Class<T> configClass) {
        try {
            T config = cm.loadObject(configClass);
            return validateObject(config);
        } catch (Exception e) {
            return ValidationResult.error("Failed to load config: " + e.getMessage());
        }
    }
    
    private <T> ValidationResult validateObject(T config) {
        List<String> errors = new ArrayList<>();
        
        if (config instanceof ServerConfig serverConfig) {
            if (serverConfig.getMaxPlayers() <= 0) {
                errors.add("Max players must be positive");
            }
            if (serverConfig.getServerName() == null || serverConfig.getServerName().trim().isEmpty()) {
                errors.add("Server name cannot be empty");
            }
        }
        
        return errors.isEmpty() ? 
            ValidationResult.success() : 
            ValidationResult.error(String.join(", ", errors));
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}
```

### Configuration Monitor

```java
public class ConfigurationMonitor {
    
    private final ConfigManager cm;
    private final ScheduledExecutorService scheduler;
    private final Map<Class<?>, Object> lastKnownStates = new ConcurrentHashMap<>();
    
    public ConfigurationMonitor(ConfigManager cm) {
        this.cm = cm;
        this.scheduler = Executors.newScheduledThreadPool(2);
        startMonitoring();
    }
    
    private void startMonitoring() {
        // Monitor for changes every 30 seconds
        scheduler.scheduleAtFixedRate(this::checkForChanges, 0, 30, TimeUnit.SECONDS);
    }
    
    private void checkForChanges() {
        // Check specific configurations
        checkConfigChange(ServerConfig.class);
        checkConfigChange(EconomyConfig.class);
        // Add more as needed
    }
    
    private <T> void checkConfigChange(Class<T> configClass) {
        try {
            T currentConfig = cm.loadObject(configClass);
            T lastKnownConfig = (T) lastKnownStates.get(configClass);
            
            if (lastKnownConfig == null) {
                lastKnownStates.put(configClass, currentConfig);
                return;
            }
            
            if (!configsEqual(currentConfig, lastKnownConfig)) {
                onConfigurationChanged(configClass, currentConfig, lastKnownConfig);
                lastKnownStates.put(configClass, currentConfig);
            }
            
        } catch (Exception e) {
            getLogger().warning("Failed to check config changes for " + configClass.getSimpleName() + ": " + e.getMessage());
        }
    }
    
    private <T> void onConfigurationChanged(Class<T> configClass, T newConfig, T oldConfig) {
        getLogger().info("Configuration changed: " + configClass.getSimpleName());
        
        // Fire change events
        if (configClass == ServerConfig.class) {
            handleServerConfigChange((ServerConfig) newConfig, (ServerConfig) oldConfig);
        }
        // Handle other config types...
    }
    
    private void handleServerConfigChange(ServerConfig newConfig, ServerConfig oldConfig) {
        if (newConfig.getMaxPlayers() != oldConfig.getMaxPlayers()) {
            getLogger().info("Max players changed: " + oldConfig.getMaxPlayers() + " -> " + newConfig.getMaxPlayers());
        }
        
        if (!newConfig.getServerName().equals(oldConfig.getServerName())) {
            getLogger().info("Server name changed: " + oldConfig.getServerName() + " -> " + newConfig.getServerName());
        }
    }
    
    private <T> boolean configsEqual(T config1, T config2) {
        // Use JSON serialization for deep comparison
        return config1.toString().equals(config2.toString());
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}
```

---

## 🎯 Best Practices

### 1. Error Handling

```java
public class SafeConfigManager {
    
    private final ConfigManager cm;
    
    public SafeConfigManager() {
        this.cm = MongoConfigsAPI.getConfigManager();
    }
    
    public <T> Optional<T> safeLoadConfig(Class<T> configClass) {
        try {
            T config = cm.loadObject(configClass);
            return Optional.ofNullable(config);
        } catch (Exception e) {
            getLogger().severe("Failed to load " + configClass.getSimpleName() + ": " + e.getMessage());
            return Optional.empty();
        }
    }
    
    public <T> boolean safeSaveConfig(T config) {
        try {
            cm.saveObject(config);
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to save " + config.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }
}
```

### 2. Performance Optimization

```java
public class OptimizedConfigAccess {
    
    private final ConfigManager cm;
    private final Map<Class<?>, Object> localCache = new ConcurrentHashMap<>();
    
    public <T> T getCachedConfig(Class<T> configClass) {
        @SuppressWarnings("unchecked")
        T config = (T) localCache.get(configClass);
        
        if (config == null) {
            config = cm.loadObject(configClass);
            localCache.put(configClass, config);
        }
        
        return config;
    }
    
    public void refreshCache() {
        localCache.clear();
        cm.invalidateCache();
    }
}
```

---

*Next: Learn about the [[LanguageManager API]] for multilingual support.*