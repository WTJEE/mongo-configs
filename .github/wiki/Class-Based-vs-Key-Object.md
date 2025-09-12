# Class Based vs Key Object

Learn when to use class-based configurations vs key-based object storage for optimal performance and maintainability.

## 🎯 Overview

MongoDB Configs API offers two primary approaches for data management:

1. **Class-Based**: Structured configuration classes with annotations
2. **Key-Object**: Direct key-value storage for dynamic data

## 📋 Class-Based Approach

### When to Use Class-Based

- ✅ **Structured configuration data**
- ✅ **Type safety requirements**
- ✅ **Complex nested objects**
- ✅ **IDE autocompletion**
- ✅ **Compile-time validation**
- ✅ **Hot reload capabilities**

### Class-Based Example

```java
@ConfigsFileProperties(name = "server-config")
public class ServerConfig {
    private int maxPlayers = 100;
    private String serverName = "My Server";
    private boolean pvpEnabled = true;
    private List<String> bannedItems = new ArrayList<>();
    private Map<String, WorldSettings> worlds = new HashMap<>();
    
    @Data
    public static class WorldSettings {
        private boolean monstersEnabled = true;
        private Difficulty difficulty = Difficulty.NORMAL;
        private int spawnRadius = 100;
    }
    
    // getters/setters...
}

// Usage
ConfigManager cm = MongoConfigsAPI.getConfigManager();
ServerConfig config = cm.loadObject(ServerConfig.class);  // ⚡ Type-safe!

// Modify with full IDE support
config.setMaxPlayers(200);
config.getWorlds().put("survival", new WorldSettings());
cm.saveObject(config);
```

**Benefits**:
- 🔥 **One line loading**: `cm.loadObject(ServerConfig.class)`
- 🛡️ **Type safety**: Compile-time error checking
- 🧠 **IDE support**: Autocompletion, refactoring
- 📝 **Documentation**: Self-documenting code structure
- 🔄 **Hot reload**: Built-in configuration reloading

---

## 🗝️ Key-Object Approach

### When to Use Key-Object

- ✅ **Dynamic data storage**
- ✅ **Unknown structure at compile time**
- ✅ **Simple key-value pairs**
- ✅ **Player-specific data**
- ✅ **Temporary caching**
- ✅ **Rapid prototyping**

### Key-Object Example

```java
public class PlayerDataManager {
    
    private final ConfigManager cm = MongoConfigsAPI.getConfigManager();
    
    // Store player-specific data
    public void savePlayerData(UUID playerId, String key, Object value) {
        String dataKey = "player:" + playerId + ":" + key;
        cm.set(dataKey, value);
    }
    
    public <T> T getPlayerData(UUID playerId, String key, Class<T> type) {
        String dataKey = "player:" + playerId + ":" + key;
        return cm.get(dataKey, type);
    }
    
    // Store dynamic configuration
    public void setDynamicConfig(String path, Object value) {
        cm.set("dynamic.config." + path, value);
    }
    
    public <T> T getDynamicConfig(String path, Class<T> type) {
        return cm.get("dynamic.config." + path, type);
    }
    
    // Cache management
    public void cacheData(String key, Object data, long ttlMillis) {
        cm.set("cache:" + key, data);
        // Note: TTL handling would be implemented in your cache layer
    }
}

// Usage
PlayerDataManager manager = new PlayerDataManager();

// Store various player data
manager.savePlayerData(playerId, "last_login", LocalDateTime.now());
manager.savePlayerData(playerId, "preferred_language", "pl");
manager.savePlayerData(playerId, "achievements", Arrays.asList("first_kill", "first_death"));

// Retrieve with type safety
LocalDateTime lastLogin = manager.getPlayerData(playerId, "last_login", LocalDateTime.class);
String language = manager.getPlayerData(playerId, "preferred_language", String.class);
List<String> achievements = manager.getPlayerData(playerId, "achievements", List.class);
```

**Benefits**:
- 🚀 **Flexibility**: Store any data structure
- ⚡ **Speed**: Direct key-value access
- 🔧 **Dynamic**: Runtime data structure changes
- 💾 **Simple**: No class definitions needed

---

## 🔀 Hybrid Approach

### Combining Both Methods

```java
// Main server config - structured (Class-Based)
@ConfigsFileProperties(name = "server-config")
public class ServerConfig {
    private int maxPlayers = 100;
    private String serverName = "My Server";
    private boolean maintenanceMode = false;
    // ... structured settings
}

// Dynamic data manager - flexible (Key-Object)
public class DynamicDataManager {
    
    private final ConfigManager cm = MongoConfigsAPI.getConfigManager();
    private final ServerConfig serverConfig;
    
    public DynamicDataManager() {
        this.serverConfig = cm.loadObject(ServerConfig.class);
    }
    
    // Player statistics (dynamic)
    public void updatePlayerStat(UUID playerId, String stat, int value) {
        String key = "stats:" + playerId + ":" + stat;
        cm.set(key, value);
    }
    
    // Temporary event data (dynamic)
    public void setEventData(String eventId, Map<String, Object> data) {
        cm.set("events:" + eventId, data);
    }
    
    // Server announcements (dynamic)
    public void setAnnouncement(String id, String message, long expiry) {
        Map<String, Object> announcement = Map.of(
            "message", message,
            "expiry", expiry,
            "created", System.currentTimeMillis()
        );
        cm.set("announcements:" + id, announcement);
    }
    
    // Use structured config for core settings
    public boolean isMaintenanceMode() {
        return serverConfig.isMaintenanceMode();
    }
    
    public void setMaintenanceMode(boolean enabled) {
        serverConfig.setMaintenanceMode(enabled);
        cm.saveObject(serverConfig);  // Structured save
    }
}
```

---

## 📊 Comparison Table

| Aspect | Class-Based | Key-Object |
|--------|-------------|------------|
| **Type Safety** | ✅ Compile-time | ⚠️ Runtime only |
| **IDE Support** | ✅ Full autocompletion | ❌ Limited |
| **Performance** | ✅ Optimized serialization | ✅ Direct access |
| **Flexibility** | ⚠️ Fixed structure | ✅ Completely dynamic |
| **Code Maintenance** | ✅ Self-documenting | ⚠️ Manual documentation |
| **Hot Reload** | ✅ Built-in | ⚠️ Manual implementation |
| **Complex Objects** | ✅ Nested classes | ⚠️ Maps/Lists only |
| **Validation** | ✅ Class-level validation | ⚠️ Manual validation |
| **Learning Curve** | ⚠️ Annotations required | ✅ Simple key-value |

---

## 🎯 Decision Guide

### Choose Class-Based When:

```java
// ✅ Server configuration
@ConfigsFileProperties(name = "server-settings")
public class ServerSettings {
    private NetworkConfig network;
    private SecurityConfig security;
    private PerformanceConfig performance;
}

// ✅ Game mechanics
@ConfigsFileProperties(name = "pvp-config")
public class PvPConfig {
    private ArenaSettings arenas;
    private TournamentSettings tournaments;
    private RankingSettings rankings;
}

// ✅ Feature configurations
@ConfigsFileProperties(name = "economy-config")
public class EconomyConfig {
    private ShopSettings shop;
    private AuctionSettings auction;
    private CurrencySettings currency;
}
```

### Choose Key-Object When:

```java
public class DynamicDataExamples {
    
    private final ConfigManager cm = MongoConfigsAPI.getConfigManager();
    
    // ✅ Player statistics
    public void savePlayerStats(UUID playerId, Map<String, Integer> stats) {
        cm.set("player_stats:" + playerId, stats);
    }
    
    // ✅ Temporary event data
    public void createEvent(String eventId, Object eventData) {
        cm.set("events:" + eventId, eventData);
    }
    
    // ✅ Dynamic pricing
    public void updateItemPrice(String item, double price) {
        cm.set("prices:" + item, price);
    }
    
    // ✅ Session data
    public void saveSession(String sessionId, Map<String, Object> sessionData) {
        cm.set("sessions:" + sessionId, sessionData);
    }
    
    // ✅ Caching API responses
    public void cacheApiResponse(String endpoint, Object response) {
        cm.set("api_cache:" + endpoint, response);
    }
}
```

---

## 🏗️ Advanced Patterns

### 1. Configuration Factory Pattern

```java
public class ConfigFactory {
    
    private final ConfigManager cm = MongoConfigsAPI.getConfigManager();
    
    // Class-based configs
    public <T> T getConfig(Class<T> configClass) {
        return cm.loadObject(configClass);
    }
    
    // Dynamic configs
    public <T> T getDynamicConfig(String key, Class<T> type, Supplier<T> defaultSupplier) {
        T value = cm.get(key, type);
        if (value == null) {
            value = defaultSupplier.get();
            cm.set(key, value);
        }
        return value;
    }
    
    // Hybrid access
    public void updateConfig(String configName, String path, Object value) {
        // Try class-based first
        try {
            Object config = getConfigByName(configName);
            updateFieldByPath(config, path, value);
            cm.saveObject(config);
        } catch (Exception e) {
            // Fallback to key-object
            cm.set(configName + "." + path, value);
        }
    }
}
```

### 2. Versioned Configuration

```java
@ConfigsFileProperties(name = "versioned-config")
public class VersionedConfig {
    private int configVersion = 1;
    private Map<String, Object> settings = new HashMap<>();
    
    // Migration logic
    public void migrate(ConfigManager cm) {
        if (configVersion < 2) {
            // Migrate old key-object data to structured format
            migrateV1ToV2(cm);
            configVersion = 2;
            cm.saveObject(this);
        }
    }
    
    private void migrateV1ToV2(ConfigManager cm) {
        // Move key-object data to structured format
        String oldMaxPlayers = cm.get("config.max_players", String.class);
        if (oldMaxPlayers != null) {
            settings.put("maxPlayers", Integer.parseInt(oldMaxPlayers));
            cm.delete("config.max_players");
        }
    }
}
```

### 3. Conditional Configuration Loading

```java
public class ConditionalConfigManager {
    
    private final ConfigManager cm = MongoConfigsAPI.getConfigManager();
    
    public <T> T loadConfig(Class<T> configClass, boolean useCache) {
        if (useCache) {
            return cm.loadObject(configClass);  // Uses cache
        } else {
            // Force reload from MongoDB
            cm.evictFromCache(configClass);
            return cm.loadObject(configClass);
        }
    }
    
    public Object loadDynamicConfig(String key, boolean createIfMissing) {
        Object value = cm.get(key, Object.class);
        if (value == null && createIfMissing) {
            value = createDefaultValue(key);
            cm.set(key, value);
        }
        return value;
    }
    
    private Object createDefaultValue(String key) {
        return switch (key) {
            case "player_settings" -> new HashMap<String, Object>();
            case "server_stats" -> Map.of("uptime", 0L, "players", 0);
            default -> null;
        };
    }
}
```

---

## 🚀 Performance Considerations

### Class-Based Performance

```java
// ✅ Good - efficient structured access
ServerConfig config = cm.loadObject(ServerConfig.class);
int maxPlayers = config.getMaxPlayers();           // Direct field access
String serverName = config.getServerName();        // Type-safe access

// ❌ Less efficient - repeated loading
int maxPlayers = cm.loadObject(ServerConfig.class).getMaxPlayers();
String serverName = cm.loadObject(ServerConfig.class).getServerName();
```

### Key-Object Performance

```java
// ✅ Good - direct key access
String playerLang = cm.get("player:" + playerId + ":language", String.class);

// ✅ Good - batch operations
Map<String, Object> playerData = Map.of(
    "player:" + playerId + ":language", "en",
    "player:" + playerId + ":level", 10,
    "player:" + playerId + ":coins", 1000
);
// Batch save (if supported by implementation)

// ❌ Less efficient - many small operations
cm.set("player:" + playerId + ":language", "en");
cm.set("player:" + playerId + ":level", 10);
cm.set("player:" + playerId + ":coins", 1000);
```

---

## 🎯 Best Practices Summary

### For Class-Based Configurations:
1. Use for **structured, well-defined data**
2. Provide **sensible default values**
3. Group **related settings** in nested classes
4. Implement **validation methods**
5. Use **Lombok** for cleaner code

### For Key-Object Storage:
1. Use for **dynamic, changing data**
2. Implement **consistent key naming**
3. Add **type safety** through wrapper methods
4. Consider **TTL and cleanup** for temporary data
5. Document **key patterns** thoroughly

### Hybrid Approach:
1. **Structured configs** for core application settings
2. **Key-object storage** for user data and dynamic content
3. **Clear separation** of concerns
4. **Consistent access patterns**
5. **Migration strategies** for evolving schemas

---

*Next: Explore the [[ConfigManager API]] for advanced configuration management.*