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

// Use with blocking call
ServerConfig config = cm.getConfigOrGenerate(
    ServerConfig.class,
    ServerConfig::new
).join();
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

#### `set(String key, T value)`
Asynchronously stores a value with the specified key.

```java
// Async key-value storage
CompletableFuture<Void> future = cm.set("player:" + playerId, playerData);

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

---

## 📧 Message System Methods

### `findById(String id)`
Gets a Messages instance by document ID.

```java
// Get message system by ID
Messages guiMessages = cm.findById("gui-messages");
Messages shopMessages = cm.findById("shop-messages");
Messages commandMessages = cm.findById("command-messages");

// Use messages
LanguageManager lm = MongoConfigsAPI.getLanguageManager();
String playerLang = lm.getPlayerLanguage(player.getUniqueId().toString());
String welcomeMsg = guiMessages.get(playerLang, "welcome.message", player.getName());
String shopTitle = shopMessages.get(playerLang, "shop.title");

player.sendMessage(welcomeMsg);
```

### `messagesOf(Class<?> messageClass)`
Gets a Messages instance for a message class (uses annotation to get ID).

```java
// Get message system from class (uses @ConfigsFileProperties annotation)
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

## 🔄 Collection Management

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
        T config = cm.loadObject(configClass);
        configCache.put(configClass, config);
        return config;
    }

    public void refreshAll() {
        configCache.clear();
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
    }
}
```

---

*Next: Learn about the [[LanguageManager API]] for multilingual support.*

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