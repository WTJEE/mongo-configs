# MongoConfigs Library

Advanced MongoDB configuration and translation management library with full async support, designed for high-performance Minecraft servers (Paper/Velocity).

## 🌟 Core Features

- **🚀 Async First**: Built from the ground up using `CompletableFuture` and Reactive Streams. No blocking the main thread.
- **⚡ Caffeine Caching**: Intelligent caching with "Smart Refresh" (Read-Through) - messages never disappear, even if cache expires.
- **🔄 Auto-Updates**: Change Streams support means changes in MongoDB are instantly reflected on all servers without reloads.
- **📦 Bulk Operations**: Optimized database writes using bulk operations to minimize network overhead.
- **🧩 Cross-Platform**: Native support for Paper and Velocity, sharing the same core logic.

---

## 📚 API Reference & Imports

Here is a breakdown of the key classes you will use, their imports, and what they do.

### 1. Main Managers
**Package**: `xyz.wtje.mongoconfigs.api`

| Class | Import | Description |
|-------|--------|-------------|
| **MongoConfigManager** | `import xyz.wtje.mongoconfigs.api.MongoConfigManager;` | The entry point for the API. Use `getInstance()` to get the singleton instance. |
| **ConfigManager** | `import xyz.wtje.mongoconfigs.api.ConfigManager;` | The main interface you interact with. Contains methods like `getMessageAsync`, `set`, `get`. |

### 2. Annotations (For Typed Configs)
**Package**: `xyz.wtje.mongoconfigs.api.annotations`

| Annotation | Import | Description |
|------------|--------|-------------|
| **@Config** | `import xyz.wtje.mongoconfigs.api.annotations.Config;` | Marks a class as a configuration POJO. You specify the `id` (document ID) here. |
| **@Collection** | `import xyz.wtje.mongoconfigs.api.annotations.Collection;` | (Optional) Specifies which MongoDB collection to store this config in. |

### 3. Core Implementation (Advanced)
**Package**: `xyz.wtje.mongoconfigs.core`

| Class | Import | Description |
|-------|--------|-------------|
| **ConfigManagerImpl** | `import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;` | The internal implementation used by plugins. You usually don't need this unless initializing manually. |
| **MongoConfig** | `import xyz.wtje.mongoconfigs.core.config.MongoConfig;` | Configuration object for the library itself (connection string, cache settings, threads). |

---

## 💻 Usage Examples


### 1. Getting Localized Messages (The "Smart" Way)
This is the most common use case. It retrieves a message from cache. If missing/expired, it fetches from DB instantly.

```java
import xyz.wtje.mongoconfigs.api.MongoConfigManager;
import xyz.wtje.mongoconfigs.api.ConfigManager;

public class MessageHandler {
    private final ConfigManager configManager = MongoConfigManager.getInstance();

    public void sendMessage(Player player, String key) {
        String lang = player.getLocale().toString(); // e.g., "en_us"
        
        configManager.getMessageAsync("messages_collection", lang, key, "&cDefault Message")
            .thenAccept(message -> {
                player.sendMessage(ColorUtils.colorize(message));
            });
    }
}
```

### 2. Using Typed Configs (POJOs)
Map your Java classes directly to MongoDB documents.

**The Config Class:**
```java
import xyz.wtje.mongoconfigs.api.annotations.Config;
import xyz.wtje.mongoconfigs.api.annotations.Collection;

@Config(id = "server-settings")
@Collection(name = "configurations")
public class ServerSettings {
    private int maxPlayers = 100;
    private String welcomeMessage = "Welcome!";
    private boolean maintenanceMode = false;

    // Getters and Setters...
}
```

**Loading/Saving:**
```java
import xyz.wtje.mongoconfigs.api.MongoConfigManager;

// Save
ServerSettings settings = new ServerSettings();
settings.setMaxPlayers(200);
MongoConfigManager.getInstance().setObject(settings);

// Load
MongoConfigManager.getInstance().getObject(ServerSettings.class)
    .thenAccept(loadedSettings -> {
        System.out.println("Max Players: " + loadedSettings.getMaxPlayers());
    });
```

### 3. Raw Key-Value Access
For simple values not tied to a POJO.

```java
// Set
configManager.set("global. multiplier", 2.5);

// Get
configManager.get("global.multiplier", Double.class)
    .thenAccept(multiplier -> {
        System.out.println("Current multiplier: " + multiplier);
    });
```

---

## 🔧 Platform Support

### Paper/Spigot
The `configs-paper` plugin automatically initializes the core. You just need to depend on it.
- **Command**: `/mongoconfigs reload` - Refreshes local cache if Change Streams are off.

### Velocity
The `configs-velocity` plugin provides the same functionality for proxies.
- **Command**: `/mongoconfigs reload`

## ⚙️ Performance Tuning
In your `plugins/MongoConfigs/config.yml`:

```yaml
mongo:
  url: "mongodb://localhost:27017"
  database: "minecraft"
  
cache:
  max-size: 10000       # Max entries in RAM
  ttl-seconds: 1800     # 30 minutes before "soft expire"
  
performance:
  io-threads: 4         # Parallel DB operations
  worker-threads: 4     # Parallel processing

---

## 🤖 AI / Developer Cheatsheet

**Copy-paste this context for your AI assistant:**

### 1. Essential Imports
```java
// Core Managers
import xyz.wtje.mongoconfigs.api.MongoConfigManager; // Singleton Access
import xyz.wtje.mongoconfigs.api.ConfigManager;      // Main Interface
import xyz.wtje.mongoconfigs.api.LanguageManager;    // Player Language Handling

// Annotations (for POJOs)
import xyz.wtje.mongoconfigs.api.annotations.Config;
import xyz.wtje.mongoconfigs.api.annotations.Collection;

// Utilities
import java.util.concurrent.CompletableFuture;       // Async return type
```

### 2. Retrieving Messages (With Placeholders)
**Syntax:** `{placeholder}` in MongoDB document.
**Java Usage:** `varargs` in `key, value` pairs.

**Code:**
```java
// MongoDB: "welcome": "Hello {player}, you have {coins} coins!"
configManager.getMessageAsync("messages", "en", "welcome", 
    "player", "Steve", 
    "coins", 100
).thenAccept(serialized -> {
    player.sendMessage(ColorUtils.colorize(serialized));
});
```

### 3. Adding/Updating Messages Programmatically
Use this to inject default messages if they don't exist.

```java
// Asynchronous Set
Map<String, Object> defaults = Map.of(
    "welcome", "&aHello {player}!",
    "error.not_found", "&cPlayer not found."
);
configManager.getCacheManager().putMessageDataAsync("messages", "en", defaults); 
// Note: This puts into cache. For permanent DB storage, use mongoManager direct access or saveConfig.
```

### 4. Typed Configuration (Best Practice)
Don't use raw `get/set` for everything. Use Classes.

```java
@Config(id = "economy_settings") 
public class EconomyConfig {
    public double startingBalance = 100.0;
    public boolean enabled = true;
}

// Load
manager.getObject(EconomyConfig.class).thenAccept(config -> ...);

// Save
manager.setObject(new EconomyConfig());
```

### 5. Common Methods Reference
- `getMessageAsync(coll, lang, key)`: Get unformatted message.
- `getMessageAsync(coll, lang, key, k1, v1...)`: Get + Format placeholders.
- `set(key, value)`: Save generic config value.
- `get(key, Class<T>)`: Get generic config value.
- `reloadAll()`: Forces refresh from MongoDB (useful for manual edits).
```
