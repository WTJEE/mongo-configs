# Class-Based Configuration

Complete guide to using class-based configuration approach in MongoDB Configs API.

## 🎯 Overview

Class-based configuration allows you to define your configuration using Java classes with annotations. This approach provides:

- **Type Safety** - Compile-time validation of configuration properties
- **IDE Support** - Auto-completion and refactoring
- **Validation** - Built-in validation of configuration values
- **Documentation** - Self-documenting configuration structure

## 📝 Basic Class-Based Configuration

### Simple Configuration Class

```java
@ConfigsDatabase(
    uri = "mongodb://localhost:27017",
    database = "minecraft_configs"
)
@ConfigsCollection("server-settings")
@ConfigsFileProperties(fileName = "server-config.yml", resource = true)
public class ServerConfig {

    private String serverName = "My Server";
    private int maxPlayers = 100;
    private boolean pvpEnabled = true;
    private List<String> bannedItems = Arrays.asList("bedrock", "barrier");
    private Map<String, Integer> worldSettings = new HashMap<>();

    // Constructors, getters, setters...
}
```

### Loading Configuration

```java
// Get ConfigManager instance
ConfigManager cm = MongoConfigsAPI.getConfigManager();

// Load configuration synchronously
ServerConfig config = cm.loadObject(ServerConfig.class);

// Load configuration asynchronously
CompletableFuture<ServerConfig> configFuture = cm.getObject(ServerConfig.class);
configFuture.thenAccept(config -> {
    getLogger().info("Server name: " + config.getServerName());
    getLogger().info("Max players: " + config.getMaxPlayers());
});
```

### Saving Configuration

```java
// Modify configuration
ServerConfig config = cm.loadObject(ServerConfig.class);
config.setMaxPlayers(200);
config.getBannedItems().add("tnt");

// Save synchronously
cm.saveObject(config);

// Save asynchronously
cm.setObject(config).thenRun(() -> {
    getLogger().info("Configuration saved!");
});
```

## 🔧 Advanced Class-Based Features

### Nested Configuration Objects

```java
@ConfigsCollection("server-settings")
public class ServerConfig {

    private String serverName;
    private DatabaseConfig database = new DatabaseConfig();
    private EconomyConfig economy = new EconomyConfig();
    private List<WorldConfig> worlds = new ArrayList<>();

    public static class DatabaseConfig {
        private String host = "localhost";
        private int port = 27017;
        private String database = "minecraft";
        private String username;
        private String password;

        // Getters, setters...
    }

    public static class EconomyConfig {
        private double startingBalance = 1000.0;
        private String currencySymbol = "$";
        private boolean enableShop = true;

        // Getters, setters...
    }

    public static class WorldConfig {
        private String worldName;
        private boolean pvpEnabled = true;
        private Difficulty difficulty = Difficulty.NORMAL;

        // Getters, setters...
    }

    // Getters, setters...
}
```

### Configuration with Validation

```java
@ConfigsCollection("server-settings")
public class ValidatedServerConfig {

    @NotNull
    @Size(min = 3, max = 50)
    private String serverName;

    @Min(1)
    @Max(1000)
    private int maxPlayers;

    @NotEmpty
    private List<String> adminPlayers;

    @Valid
    private DatabaseSettings databaseSettings;

    // Custom validation
    @AssertTrue(message = "Server name cannot contain special characters")
    public boolean isServerNameValid() {
        return serverName != null && serverName.matches("^[a-zA-Z0-9\\s]+$");
    }

    // Getters, setters...
}
```

### Configuration Inheritance

```java
// Base configuration
public abstract class BaseConfig {
    private String version = "1.0.0";
    private long lastModified = System.currentTimeMillis();

    // Common methods...
}

// Server-specific configuration
@ConfigsCollection("server-config")
public class ServerConfig extends BaseConfig {

    private String serverName;
    private int maxPlayers;

    // Server-specific methods...
}

// Plugin configuration
@ConfigsCollection("plugin-config")
public class PluginConfig extends BaseConfig {

    private boolean enabled = true;
    private Map<String, Object> settings = new HashMap<>();

    // Plugin-specific methods...
}
```

## 📊 Complex Data Structures

### Configuration with Collections

```java
@ConfigsCollection("shop-config")
public class ShopConfig {

    private Map<String, ShopCategory> categories = new HashMap<>();
    private List<ShopItem> featuredItems = new ArrayList<>();
    private Set<String> bannedPlayers = new HashSet<>();

    public static class ShopCategory {
        private String name;
        private String displayName;
        private Material icon;
        private List<ShopItem> items = new ArrayList<>();

        // Getters, setters...
    }

    public static class ShopItem {
        private String id;
        private String displayName;
        private Material material;
        private double price;
        private int maxQuantity;
        private List<String> lore = new ArrayList<>();
        private Map<Enchantment, Integer> enchantments = new HashMap<>();

        // Getters, setters...
    }

    // Getters, setters...
}
```

### Time-Based Configuration

```java
@ConfigsCollection("time-based-config")
public class TimeBasedConfig {

    private Map<DayOfWeek, DailySchedule> schedules = new HashMap<>();
    private List<SpecialEvent> events = new ArrayList<>();
    private TimeZone timezone = TimeZone.getDefault();

    public static class DailySchedule {
        private LocalTime openTime = LocalTime.of(9, 0);
        private LocalTime closeTime = LocalTime.of(17, 0);
        private boolean isHoliday = false;
        private String specialMessage;

        // Getters, setters...
    }

    public static class SpecialEvent {
        private String eventName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Map<String, Object> eventSettings = new HashMap<>();

        // Getters, setters...
    }

    // Getters, setters...
}
```

## 🔄 Dynamic Configuration Updates

### Hot-Reload Configuration

```java
public class DynamicConfigManager {

    private final ConfigManager cm;
    private ServerConfig currentConfig;

    public DynamicConfigManager() {
        this.cm = MongoConfigsAPI.getConfigManager();
        loadConfiguration();
        setupChangeListener();
    }

    private void loadConfiguration() {
        try {
            currentConfig = cm.loadObject(ServerConfig.class);
            applyConfiguration(currentConfig);
        } catch (Exception e) {
            getLogger().severe("Failed to load configuration: " + e.getMessage());
        }
    }

    private void setupChangeListener() {
        // Listen for configuration changes (requires Change Streams)
        cm.addChangeStreamListener(ServerConfig.class, event -> {
            ServerConfig newConfig = event.getFullDocument();
            if (newConfig != null) {
                getLogger().info("Configuration updated, applying changes...");
                applyConfiguration(newConfig);
                currentConfig = newConfig;
            }
        });
    }

    private void applyConfiguration(ServerConfig config) {
        // Apply configuration changes to server
        updateMaxPlayers(config.getMaxPlayers());
        updateServerName(config.getServerName());
        updateBannedItems(config.getBannedItems());
    }

    private void updateMaxPlayers(int maxPlayers) {
        // Implementation to update server max players
        getLogger().info("Max players updated to: " + maxPlayers);
    }

    private void updateServerName(String serverName) {
        // Implementation to update server name
        getLogger().info("Server name updated to: " + serverName);
    }

    private void updateBannedItems(List<String> bannedItems) {
        // Implementation to update banned items
        getLogger().info("Banned items updated: " + String.join(", ", bannedItems));
    }

    public ServerConfig getCurrentConfig() {
        return currentConfig;
    }
}
```

## 🎯 Best Practices

### 1. Configuration Structure

```java
// ✅ Good - Logical grouping
@ConfigsCollection("server-core-settings")
public class ServerCoreConfig {
    private String serverName;
    private int maxPlayers;
    private Difficulty difficulty;
}

@ConfigsCollection("server-economy-settings")
public class ServerEconomyConfig {
    private double startingBalance;
    private String currencyName;
    private boolean enableTrading;
}

// ❌ Avoid - Monolithic configuration
@ConfigsCollection("server-settings")
public class ServerConfig {
    // Everything in one class - hard to manage
    private String serverName;
    private int maxPlayers;
    private double startingBalance;
    private String currencyName;
    private Difficulty difficulty;
    private boolean enableTrading;
    // ... many more fields
}
```

### 2. Default Values

```java
// ✅ Good - Sensible defaults
@ConfigsCollection("server-config")
public class ServerConfig {

    private String serverName = "Default Server";
    private int maxPlayers = 100;
    private boolean pvpEnabled = true;
    private List<String> bannedItems = new ArrayList<>();

    // Constructor with defaults
    public ServerConfig() {
        bannedItems.add("bedrock");
        bannedItems.add("barrier");
    }
}

// ❌ Avoid - Null defaults
@ConfigsCollection("server-config")
public class ServerConfig {

    private String serverName;  // null
    private Integer maxPlayers; // null
    private Boolean pvpEnabled; // null
}
```

### 3. Validation

```java
// ✅ Good - Input validation
@ConfigsCollection("server-config")
public class ServerConfig {

    @NotNull
    @Size(min = 3, max = 50)
    private String serverName;

    @Min(1)
    @Max(1000)
    private int maxPlayers;

    @NotEmpty
    private List<String> adminPlayers;

    // Custom validation
    @AssertTrue(message = "Max players cannot exceed server capacity")
    public boolean isMaxPlayersValid() {
        return maxPlayers <= getServerCapacity();
    }

    private int getServerCapacity() {
        return 500; // Server-specific logic
    }
}
```

### 4. Documentation

```java
@ConfigsCollection("server-config")
public class ServerConfig {

    /**
     * The display name of the server shown to players.
     * Must be between 3-50 characters and contain only alphanumeric characters and spaces.
     */
    @NotNull
    @Size(min = 3, max = 50)
    private String serverName = "My Server";

    /**
     * Maximum number of players allowed on the server simultaneously.
     * Must be between 1 and the server's maximum capacity.
     */
    @Min(1)
    @Max(1000)
    private int maxPlayers = 100;

    /**
     * Whether PvP (Player vs Player) combat is enabled on the server.
     * When disabled, players cannot damage each other.
     */
    private boolean pvpEnabled = true;

    /**
     * List of items that are banned from being used or obtained on the server.
     * Items are specified by their Minecraft material names (e.g., "bedrock", "barrier").
     */
    @NotEmpty
    private List<String> bannedItems = Arrays.asList("bedrock", "barrier");
}
```

---

*Next: Learn about [[Key-Object Storage]] for simple key-value configuration.*