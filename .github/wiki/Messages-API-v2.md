# Messages API v2

## Overview
The Messages API provides a powerful, **fully asynchronous** system for managing localized messages from MongoDB. All operations are optimized for instant response with real-time synchronization via Change Streams.

## Key Features
- ✅ **100% Async Operations** - All methods return `CompletableFuture` for non-blocking execution
- ✅ **Instant Cache Updates** - Changes apply immediately without blocking
- ✅ **Real-time Sync** - Change Streams detect and apply MongoDB changes instantly
- ✅ **PlaceholderAPI Support** - Full integration with PlaceholderAPI
- ✅ **Thread-Safe** - All operations are thread-safe with proper synchronization

## Basic Usage

### Getting Messages (Async)
```java
// Get a message asynchronously
configManager.getMessageAsync("messages", "en", "welcome.message")
    .thenAccept(message -> {
        player.sendMessage(message);
    });

// With placeholders
configManager.getMessageAsync("messages", playerLang, "kill.message")
    .thenAccept(msg -> {
        String formatted = msg.replace("{player}", player.getName())
                             .replace("{victim}", victim.getName());
        player.sendMessage(formatted);
    });

// With default value
configManager.getMessageAsync("messages", lang, "error.unknown", "An error occurred")
    .thenAccept(player::sendMessage);
```

### Batch Operations
```java
// Get multiple messages at once
CompletableFuture<String> welcomeFuture = configManager.getMessageAsync("messages", lang, "welcome");
CompletableFuture<String> motdFuture = configManager.getMessageAsync("messages", lang, "motd");
CompletableFuture<String> tipFuture = configManager.getMessageAsync("messages", lang, "tip");

CompletableFuture.allOf(welcomeFuture, motdFuture, tipFuture)
    .thenRun(() -> {
        player.sendMessage(welcomeFuture.join());
        player.sendMessage(motdFuture.join());
        player.sendMessage(tipFuture.join());
    });
```

## Language Management

### Player Language (Async)
```java
// Get player's language
languageManager.getPlayerLanguage(player.getUniqueId().toString())
    .thenAccept(lang -> {
        // Use the language
        configManager.getMessageAsync("messages", lang, "welcome")
            .thenAccept(player::sendMessage);
    });

// Set player's language
languageManager.setPlayerLanguage(player.getUniqueId(), "pl")
    .thenRun(() -> {
        player.sendMessage("Language updated!");
    })
    .exceptionally(error -> {
        player.sendMessage("Failed to update language: " + error.getMessage());
        return null;
    });
```

### Language Detection
```java
// Check if language is supported
languageManager.isLanguageSupported("fr")
    .thenAccept(supported -> {
        if (supported) {
            // Set the language
        } else {
            // Use default
        }
    });

// Get all supported languages
languageManager.getSupportedLanguages()
    .thenAccept(languages -> {
        for (String lang : languages) {
            // Process each language
        }
    });
```

## Configuration Values

### Getting Config Values (Async)
```java
// Get config value
configManager.getAsync("settings:max-players")
    .thenAccept(value -> {
        if (value != null) {
            int maxPlayers = (int) value;
            // Use the value
        }
    });

// With type and default
configManager.getAsync("settings:spawn-protection", 16)
    .thenAccept(protection -> {
        // Use the protection radius
    });

// Complex object
configManager.getAsync("rewards:daily")
    .thenAccept(reward -> {
        if (reward instanceof Map) {
            Map<String, Object> rewardData = (Map<String, Object>) reward;
            // Process reward data
        }
    });
```

## Cache Management

### Invalidation
```java
// Invalidate specific collection
configManager.invalidateCollectionAsync("messages")
    .thenRun(() -> {
        getLogger().info("Messages cache cleared");
    });

// Invalidate everything
configManager.invalidateAllAsync()
    .thenRun(() -> {
        getLogger().info("All caches cleared");
    });
```

### Reload Operations
```java
// Reload specific collection
configManager.reloadCollectionAsync("messages")
    .thenRun(() -> {
        getLogger().info("Messages reloaded from MongoDB");
    });

// Full reload
configManager.reloadAll()
    .thenRun(() -> {
        getLogger().info("All collections reloaded");
    });
```

## PlaceholderAPI Integration

The plugin provides these placeholders:

- `%mongoconfigs_language%` - Player's current language code
- `%mongoconfigs_langname%` - Player's language display name
- `%mongoconfigs_message_<collection>_<key>%` - Get message in player's language
- `%mongoconfigs_config_<collection>_<key>%` - Get config value

### Examples:
```yaml
# In any plugin supporting PlaceholderAPI
message: "Your language: %mongoconfigs_langname%"
welcome: "%mongoconfigs_message_messages_welcome%"
max-players: "Max players: %mongoconfigs_config_settings_max-players%"
```

## Commands

### Language Command
```
/language - Open language selection GUI
/language <code> - Set language directly
```

### Hot Reload Commands
```
/hotreload gui - Clear and reload GUI caches
/hotreload cache - Clear all caches
/hotreload all - Full reload (language manager, caches, GUI)
/hotreload status - Show system status
```

### Admin Commands
```
/mongoconfigs reload - Reload plugin
/mongoconfigs info - Show plugin info
/configsmanager - Manage configurations
```

## Event-Driven Updates

### Listen for Language Changes
```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    
    // Get and display welcome message in player's language
    languageManager.getPlayerLanguage(player.getUniqueId().toString())
        .thenCompose(lang -> 
            configManager.getMessageAsync("messages", lang, "join.welcome"))
        .thenAccept(message -> {
            if (message != null) {
                player.sendMessage(message.replace("{player}", player.getName()));
            }
        });
}
```

### Real-time Updates
Changes in MongoDB are detected and applied instantly:

```java
// This happens automatically - no code needed!
// 1. Admin updates message in MongoDB
// 2. Change Stream detects the change
// 3. Cache updates instantly
// 4. Next getMessage() returns new value immediately
```

## Performance Tips

1. **Use Async Methods** - Always prefer async methods for best performance
2. **Batch Operations** - Combine multiple operations with `CompletableFuture.allOf()`
3. **Cache Results** - The plugin caches automatically, but cache results in hot paths
4. **Preload Common Messages** - Load frequently used messages at startup

## Error Handling

```java
// Comprehensive error handling
languageManager.getPlayerLanguage(playerId)
    .thenCompose(lang -> 
        configManager.getMessageAsync("messages", lang, "welcome"))
    .thenAccept(msg -> {
        if (msg != null) {
            player.sendMessage(msg);
        }
    })
    .exceptionally(error -> {
        getLogger().warning("Failed to get message: " + error.getMessage());
        player.sendMessage("Welcome!"); // Fallback message
        return null;
    });
```

## MongoDB Document Structure

### Messages Collection
```json
{
  "_id": "messages_en",
  "lang": "en",
  "welcome": "Welcome to the server, {player}!",
  "commands": {
    "help": "Available commands:",
    "reload": "Configuration reloaded"
  }
}
```

### Player Languages Collection
```json
{
  "_id": "uuid-here",
  "language": "pl",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

### Config Collection
```json
{
  "_id": "config",
  "max-players": 100,
  "spawn-protection": 16,
  "features": {
    "pvp": true,
    "chat": true
  }
}
```