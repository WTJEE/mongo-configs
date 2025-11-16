# MongoDB Configs Library

Advanced MongoDB configuration and translation management library with full async support.

## 🚀 Features

- ✅ **Full Async** - Zero main thread blocking, all operations use `CompletableFuture`
- ✅ **Auto Cache Refresh** - Change Streams automatically update cache when MongoDB changes
- ✅ **Multi-language Support** - Easy translation management
- ✅ **Type-safe API** - Store and retrieve POJOs with Jackson
- ✅ **High Performance** - Caffeine cache, reactive MongoDB driver
- ✅ **Paper & Velocity** - Ready-to-use plugins included

## 📦 Installation

### Using JitPack (API + Core only)

Add repository:
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add dependency:
```xml
<dependencies>
    <dependency>
        <groupId>com.github.WTJEE.mongo-configs</groupId>
        <artifactId>configs-core</artifactId>
        <version>2.0.0-beta.5</version>
    </dependency>
</dependencies>
```

### Building Plugins Locally

To build Paper and Velocity plugins:

```bash
# Build with plugins
mvn clean package -P with-plugins

# Or build everything
mvn clean package
```

Output JARs:
- `configs-paper/target/configs-paper-1.0.0.jar` - Paper plugin
- `configs-velocity/target/configs-velocity-1.0.0.jar` - Velocity plugin

## 🎮 Commands

### Paper
- `/mongoconfigs reload` - Reload configuration
- `/mongoconfigs reloadall` - Reload ALL collections from MongoDB
- `/configsmanager reloadall` - Same as above

### Velocity
- `/mongoconfigs reload` - Reload configuration
- `/mongoconfigs reloadall` - Reload ALL collections from MongoDB
- `/mongoconfigsproxy reloadall` - Same as above

## 🔧 Configuration

### Enable Change Streams (Auto Cache Refresh)

```java
MongoConfig config = MongoConfig.builder()
    .connectionString("mongodb://localhost:27017")
    .database("mydb")
    .enableChangeStreams(true)  // ✅ Enable auto-refresh
    .build();
```

When enabled, cache automatically refreshes when you update MongoDB documents - **no restart needed**!

## 📝 Usage Example

```java
// Initialize
ConfigManager manager = new ConfigManagerImpl(config);
manager.initialize();

// Get message async
CompletableFuture<String> message = manager.getMessageAsync("mycollection", "en", "welcome.message");

// Reload all collections
manager.reloadAll().thenRun(() -> {
    System.out.println("All collections reloaded!");
});

// Type-safe config
public class ServerConfig {
    public String serverName;
    public int maxPlayers;
}

manager.setObject(new ServerConfig(...));
CompletableFuture<ServerConfig> config = manager.getObject(ServerConfig.class);
```

## 🐛 Troubleshooting

### Cache not refreshing after MongoDB update?

1. Check if Change Streams are enabled in config
2. Check logs for `✅ Successfully setup change stream watcher`
3. Use `/mongoconfigs reloadall` to force refresh

### Build fails with Paper/Velocity dependencies?

Use the profile: `mvn clean package -P with-plugins`

## 📄 License

MIT License - see LICENSE file

## 🤝 Contributing

PRs welcome! Please ensure all async operations use `CompletableFuture` without `.join()` blocking.
