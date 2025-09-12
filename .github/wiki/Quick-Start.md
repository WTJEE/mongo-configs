# Quick Start

> **Get up and running with MongoDB Configs API in 5 minutes**

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

```java
public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Initialize MongoDB Configs API
        MongoConfigsAPI.initialize(this);

        // That's it! API is ready to use
        getLogger().info("MongoDB Configs API initialized!");
    }
}
```

## Basic Usage

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

### Load and Use Configuration

```java
public class ConfigCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();

        // Load configuration (from cache if available)
        ServerConfig config = cm.loadObject(ServerConfig.class);

        // Use configuration
        sender.sendMessage("Server: " + config.getServerName());
        sender.sendMessage("Max Players: " + config.getMaxPlayers());
        sender.sendMessage("PvP: " + (config.isPvpEnabled() ? "Enabled" : "Disabled"));

        return true;
    }
}
```

## Multilingual Messages

### Create Message Class

```java
@ConfigsFileProperties(name = "messages")
@ConfigsDatabase("minecraft")
public class Messages extends MongoConfig<Messages> {

    private String welcome = "Welcome to the server!";
    private String goodbye = "Goodbye!";
    private String noPermission = "You don't have permission!";

    // Getters and setters...
}
```

### Use Messages

```java
public class WelcomeListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        Messages messages = cm.loadObject(Messages.class);

        player.sendMessage(ChatColor.GREEN + messages.getWelcome());
    }
}
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
// Automatic language detection and translation
LanguageManager lm = MongoConfigsAPI.getLanguageManager();
String playerLang = lm.getPlayerLanguage(player);

Messages messages = cm.loadObject(Messages.class);
String welcomeMsg = messages.getWelcome(); // Automatically translated
```

## Next Steps

- Learn about [[Class-Based Configuration]] for advanced configuration patterns
- Explore [[GUI Development]] for creating multilingual interfaces
- Check out [[Change Streams Tutorial]] for real-time synchronization
- See [[Translation Examples]] for complete multilingual implementations

## Troubleshooting

### Common Issues

**API not initializing:**
```java
// Make sure MongoDB is running
// Check your connection string in config.yml
MongoConfigsAPI.initialize(this);
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
// Ensure LanguageManager is properly initialized
LanguageManager lm = MongoConfigsAPI.getLanguageManager();
lm.setPlayerLanguage(player, "pl"); // Set player language
```

---

*Ready to dive deeper? Check out [[Installation]] for detailed setup instructions.*