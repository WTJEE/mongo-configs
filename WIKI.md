# MongoConfigs API Documentation

MongoConfigs is a high-performance, asynchronous configuration library for Minecraft plugins (Paper, Velocity) using MongoDB and Caffeine caching.

## Key Features
- **Fully Asynchronous**: Built on reactive streams and `CompletableFuture`, ensuring no main-thread blocking.
- **High Performance**: Uses Caffeine for efficient local caching and bulk operations for database writes.
- **Cross-Platform**: Supports Paper and Velocity.
- **Typed Configurations**: Easily map POJOs to MongoDB documents using Jackson.
- **Change Streams**: Real-time configuration updates across all servers.

## Getting Started

### Dependency

Add the API dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>xyz.wtje</groupId>
    <artifactId>configs-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### Retrieving the Instance

```java
ConfigManager configManager = MongoConfigManager.getInstance();
```

Or via the platform specific plugins (recommended for Paper/Velocity plugins):
- **Paper**: `MongoConfigsAPI.getConfigManager()`
- **Velocity**: `MongoConfigsAPI.getConfigManager()`

## Basic Usage

### Getting a Config Value
```java
configManager.get("my.config.key", String.class).thenAccept(value -> {
    System.out.println("Value: " + value);
});
```

### Setting a Config Value
```java
configManager.set("my.config.key", "newValue").thenRun(() -> {
    System.out.println("Value saved!");
});
```

### Messages (i18n)
Retrieve localized messages asynchronously:

```java
configManager.getMessageAsync("messages_collection", "en", "welcome.message", "{player}", "Steve")
    .thenAccept(message -> player.sendMessage(message));
```

### Typed Configurations (POJOs)

Define your configuration class:
```java
@Config(id = "my-plugin-config")
public class MyConfig {
    private String databaseName = "minecraft";
    private int maxConnections = 100;
    // Getters and Setters
}
```

Load and save:
```java
// Save
MyConfig config = new MyConfig();
configManager.setObject(config);

// Load
configManager.getObject(MyConfig.class).thenAccept(loaded -> {
    // used loaded config
});
```

## Performance Tuning
The library is pre-configured for high performance, but you can tune parameters in `config.yml` of the plugin:
- `cache.max-size`: Maximum number of entries in local cache.
- `cache.ttl-seconds`: Time to live for cached entries.
- `mongo.min-pool-size` / `max-pool-size`: MongoDB connection pool settings.

## Best Practices
1. **Always use async**: Never block on `get(...)` or `toFuture().join()` on the main server thread.
2. **Use Typed Configs**: For complex plugins, prefer POJOs over raw key-value access.
3. **Listen for Reloads**: Use `MongoConfigsAPI.getLanguageManager()` to handle dynamic reloads if you cache messages locally in your plugin.
