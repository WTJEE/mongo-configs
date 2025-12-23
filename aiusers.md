# MongoConfigs AI Developer Guide

**Context for AI Agent**: This library (`mongo-configs`) is a high-performance, asynchronous configuration management system for Minecraft servers (Paper/Velocity) backed by MongoDB and Caffeine Cache.

**Core Principles**:
1.  **Async-Only**: All I/O return `CompletableFuture`. Never call `.join()` on the main thread.
2.  **POJO-First**: Prefer Typed Configs (`@Config`) over raw values.
3.  **Smart Caching**: Messages are cached. Retreival handles cache misses automatically.
4.  **Bulk Ops**: The system optimizes writes automatically using bulk operations.

---

## 1. Crash Course: Imports

Include these imports when working with the library:

```java
// Managers
import xyz.wtje.mongoconfigs.api.MongoConfigManager; // Singleton Access
import xyz.wtje.mongoconfigs.api.ConfigManager;      // Primary Interface
import xyz.wtje.mongoconfigs.api.LanguageManager;    // Localization

// Annotations
import xyz.wtje.mongoconfigs.api.annotations.Config;
import xyz.wtje.mongoconfigs.api.annotations.Collection;
import xyz.wtje.mongoconfigs.api.annotations.Id;     // Optional, for custom IDs

// Utilities
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
```

---

## 2. Initialization

### Getting the Instance
In a Plugin (Paper/Velocity), the API is already initialized. Just get it:

```java
ConfigManager configManager = MongoConfigManager.getInstance();
LanguageManager langManager = MongoConfigsAPI.getLanguageManager(); // Platform specific helper
```

---

## 3. Typed Configurations (POJOs)

This is the recommended way to store data.

### Step 3a: Create the Class
Map a Java class to a MongoDB document.

```java
@Config(id = "server-economics") // The document _id
@Collection(name = "configurations") // (Optional) Collection name, defaults to 'configs'
public class EconomicsConfig {

    private double startingBalance = 100.0;
    private int maxObtainableHouses = 3;
    private List<String> blacklistedWorlds = List.of("world_nether");

    // Must have a no-args constructor (default or explicit)
    public EconomicsConfig() {}

    // Getters
    public double getStartingBalance() { return startingBalance; }
    
    // Setters
    public void setStartingBalance(double val) { this.startingBalance = val; }
}
```

### Step 3b: Save (Async)
```java
EconomicsConfig config = new EconomicsConfig();
config.setStartingBalance(500.0);

// Returns CompletableFuture<Void>
configManager.setObject(config).thenRun(() -> {
    plugin.getLogger().info("Economics saved!");
});
```

### Step 3c: Load (Async)
```java
// Returns CompletableFuture<EconomicsConfig>
configManager.getObject(EconomicsConfig.class).thenAccept(config -> {
    if (config == null) config = new EconomicsConfig(); // Handle new setup
    
    double balance = config.getStartingBalance();
});
```

---

## 4. Messages & Localization

### Step 4a: Retrieving Messages
Use `getMessageAsync`. Handles caching + DB lookup.

**Arguments**: `(collection, language, key, [placeholders...])`

```java
public void greetPlayer(Player player) {
    // 1. Get Player's Language
    langManager.getPlayerLanguage(player.getUniqueId()).thenCompose(lang -> {
        
        // 2. Get Message
        return configManager.getMessageAsync(
            "messages",       // Collection
            lang,            // Language (e.g. "en", "pl")
            "welcome_msg",    // Key
            // Placeholders (Key, Value pairs)
            "player", player.getName(),
            "server", "MyServer"
        );
        
    }).thenAccept(message -> {
        // 3. Send (Colorize uses internal utility or Adventure)
        player.sendMessage(ColorUtils.colorize(message));
    });
}
```

### Step 4b: Adding Default Messages
Inject defaults into the cache/DB if they don't exist.

```java
Map<String, Object> polandDefaults = new HashMap<>();
polandDefaults.put("welcome_msg", "&aWitaj {player} na serwerze {server}!");
polandDefaults.put("error", "&cWystapil blad.");

// Helper to push data
// Note: This updates cache. For robust setups, insert into MongoDB directly via 'mongoManager'.
configManager.getCacheManager().putMessageDataAsync("messages", "pl", polandDefaults);
```

---

## 5. Player Language Management

Manage per-player language preferences.

```java
// Get Language (UUID) -> returns "en", "pl", etc.
CompletableFuture<String> langFuture = langManager.getPlayerLanguage(uuid);

// Set Language -> Updates DB and Cache
langManager.setPlayerLanguage(uuid, "pl").thenRun(() -> {
    player.sendMessage("Jezyk zmieniony na Polski!");
});

// Check if supported
langManager.isLanguageSupported("fr").thenAccept(isSupported -> ...);
```

---

## 6. Raw Configuration

For simple key-value storage without POJOs.

```java
// Set
configManager.set("maintenance.enabled", true);

// Get (Type-safe)
configManager.get("maintenance.enabled", Boolean.class).thenAccept(enabled -> {
    if (Boolean.TRUE.equals(enabled)) {
       // ...
    }
});
```

---

## 7. Change Streams & Auto-Reload

If `enableChangeStreams` is true in `config.yml`:
1.  **No Code Required**: Just edit MongoDB (Compass/Atlas).
2.  **Instant Update**: All servers receive update signal.
3.  **Cache Invalidated**: Next `get` call fetches fresh data.

To force manual reload via API:
```java
configManager.reloadAll().thenRun(() -> System.out.println("Reloaded!"));
```

---

## 8. PlayerLanguageUpdateEvent

(Paper/Bukkit Only)

This event is fired **immediately** when a player's language is updated via `LanguageManager.setPlayerLanguage()`. It is synchronous and allows for instant updates of GUIs, Scoreboards, and Items.

### Features
1.  **Zero Latency**: Fired immediately after the local cache update, before the async database write completes.
2.  **Instant Reflection**: Calls to `LanguageManager.getPlayerLanguage()` inside this event will return the **new** language.
3.  **Thread Safety**: Fired on the main server thread.

### Usage Example

```java
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import xyz.wtje.mongoconfigs.paper.events.PlayerLanguageUpdateEvent;

public class LanguageListener implements Listener {

    @EventHandler
    public void onLanguageChange(PlayerLanguageUpdateEvent event) {
        String playerId = event.getPlayerId();
        String newLang = event.getNewLanguage();
        String oldLang = event.getOldLanguage();

        Player player = event.getPlayer();
        if (player != null) {
            // Update items, scoreboards, titles instantly
            // Example:
            // player.sendMessage(getMsg(newLang, "lang_changed"));
            // MyScoreboardManager.refresh(player);
            // MyGuiManager.refresh(player);
        }
    }
}
```
