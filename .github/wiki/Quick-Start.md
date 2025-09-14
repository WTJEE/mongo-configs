# Quick Start - Async-first ⚡

> Get up and running with MongoDB Configs API in minutes.

## Prerequisites

Before you begin, ensure you have:

- **Java 17+** installed
- **MongoDB 5.5+** running (with replica set for Change Streams)
- **Paper/Spigot server** (recommended)

## Installation

### 1. Download the API

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>xyz.wtje.mongoconfigs</groupId>
    <artifactId>mongo-configs-api</artifactId>
    <version>1.0.0</version>
    <scope>compile</scope>
</dependency>
```

### 2. Initialize the API

The bundled plugin initializes the API at startup. Access it via `MongoConfigsAPI.getConfigManager()` and `getLanguageManager()`.

## Basic Usage - ASYNC FIRST! 🚀

### Create Your First Configuration

```java
@ConfigsFileProperties(name = "server-settings")
@ConfigsDatabase("minecraft")
public class ServerConfig extends MongoConfig<ServerConfig> {

    private String serverName = "My Awesome Server";
    private int maxPlayers = 100;
    private boolean pvpEnabled = true;
    private List<String> motd = Arrays.asList("Welcome!", "Have fun!");

    // Getters and setters...
}
```

### Load and Use Configuration - ASYNC ⚡

```java
public class ConfigCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();

        // 🚀 ASYNC configuration loading (recommended!)
        cm.getObject(ServerConfig.class)
            .thenAccept(config -> {
                // Config loaded in background thread
                String serverInfo = "Server: " + config.getServerName() + "\n" +
                                  "Max Players: " + config.getMaxPlayers() + "\n" +
                                  "PvP: " + (config.isPvpEnabled() ? "Enabled" : "Disabled");
                
                // Send message on main thread for Bukkit API safety
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(serverInfo);
                });
            })
            .exceptionally(ex -> {
                getLogger().severe("Failed to load config: " + ex.getMessage());
                sender.sendMessage("§cFailed to load server configuration!");
                return null;
            });

        sender.sendMessage("§eLoading configuration...");
        return true;
    }
}
```

### Quick SYNC Access (For Hot Paths) ⚡

```java
public class QuickConfigCheck implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();

        // ⚡ SYNC for immediate response (cache hit = ~0.1ms)
        ServerConfig config = cm.loadObject(ServerConfig.class);
        
        // Quick maintenance check
        if (config.isMaintenanceMode()) {
            sender.sendMessage("§cServer is in maintenance mode!");
            return true;
        }
        
        // Continue with normal logic...
        return true;
    }
}
```

## Multilingual Messages

Use the Messages API for localized strings (named placeholders only):

```java
@ConfigsFileProperties(name = "teleport-messages")
@SupportedLanguages({"en","pl"})
public class TeleportMessages {
  public String playerNotFound = "Player {name} not found!"; // key: playerNotFound
  public String getSuccessTeleportedTo() { return "Teleported to {target}!"; } // success.teleported.to
}

// Initialize/merge and get handle
ConfigManager cm = MongoConfigsAPI.getConfigManager();
Messages tp = cm.getOrCreateFromObject(new TeleportMessages());

// Resolve language and send
LanguageManager lm = MongoConfigsAPI.getLanguageManager();
String lang = java.util.Objects.requireNonNullElse(lm.getPlayerLanguage(player.getUniqueId().toString()), lm.getDefaultLanguage());
player.sendMessage(tp.get(lang, "playerNotFound", "name", targetName));
```

## Advanced Features

### Real-Time Synchronization

```java
// Changes sync automatically across all servers
ServerConfig config = cm.loadObject(ServerConfig.class);
config.setMaxPlayers(200);
config.save(); // Instantly available on all servers!
```

### Multilingual Support

```java
LanguageManager lm = MongoConfigsAPI.getLanguageManager();
String playerLang = java.util.Objects.requireNonNullElse(lm.getPlayerLanguage(player.getUniqueId().toString()), lm.getDefaultLanguage());
Messages msgs = cm.messagesOf(TeleportMessages.class);
String welcomeMsg = msgs.get(playerLang, "gui.title");
```

## Next Steps

- Learn about [[Class-Based Configuration]] for advanced configuration patterns
- Explore [[GUI Development]] for creating multilingual interfaces
- Check out [[Change Streams Tutorial]] for real-time synchronization
- See [[Translation Examples]] for complete multilingual implementations

## Troubleshooting

### Common Issues

**API not available:**
```java
// Make sure the MongoDB Configs plugin is installed and loaded
// The API is automatically initialized by the plugin
if (!MongoConfigsAPI.isInitialized()) {
    getLogger().warning("MongoDB Configs API not available!");
}
```

**Configuration not loading:**
```java
// Verify your class has the correct annotations
@ConfigsFileProperties(name = "your-config-name")
@ConfigsDatabase("your-database-name")
public class YourConfig extends MongoConfig<YourConfig> {
    // ...
}
```

**Messages not translating:**
```java
// Ensure LanguageManager is properly available
LanguageManager lm = MongoConfigsAPI.getLanguageManager();
lm.setPlayerLanguage(player.getUniqueId().toString(), "pl"); // Set player language
```

---

*Ready to dive deeper? Check out [[Installation]] for detailed setup instructions.*