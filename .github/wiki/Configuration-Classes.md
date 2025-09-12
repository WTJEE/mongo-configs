# Configuration Classes

Learn how to create powerful configuration classes that automatically sync with MongoDB.

## 🎯 Basic Configuration Class

### Simple Config Class

```java
@ConfigsFileProperties(name = "server-settings")
public class ServerConfig {
    
    // Primitive types
    private int maxPlayers = 100;
    private boolean pvpEnabled = true;
    private double economyMultiplier = 1.0;
    private String serverName = "My Server";
    
    // Collections
    private List<String> bannedItems = new ArrayList<>();
    private Set<String> allowedCommands = new HashSet<>();
    private Map<String, Integer> worldSettings = new HashMap<>();
    
    // Custom objects
    private ServerRegion spawnRegion = new ServerRegion();
    
    // Getters and setters (or use Lombok @Data)
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    // ... more getters/setters
    
    public static class ServerRegion {
        private int x, y, z;
        private int radius = 100;
        
        // getters/setters...
    }
}
```

**Usage**:
```java
ConfigManager cm = MongoConfigsAPI.getConfigManager();

// 🔥 One line loading!
ServerConfig config = cm.loadObject(ServerConfig.class);

// Use the configuration
int max = config.getMaxPlayers();
boolean pvp = config.isPvpEnabled();
List<String> banned = config.getBannedItems();

// Modify and save
config.setMaxPlayers(200);
config.getBannedItems().add("bedrock");
cm.saveObject(config);  // ⚡ Auto-sync to all servers!
```

---

## 🏗️ Advanced Configuration Structures

### Complex Game Configuration

```java
@ConfigsFileProperties(name = "mmo-config")
public class MMOConfig {
    
    // Nested configuration sections
    private CharacterSettings characters = new CharacterSettings();
    private EconomySettings economy = new EconomySettings();
    private PvPSettings pvp = new PvPSettings();
    private Map<String, WorldConfig> worlds = new HashMap<>();
    
    // Time-based settings
    private LocalDateTime lastReset = LocalDateTime.now();
    private Duration sessionTimeout = Duration.ofMinutes(30);
    
    // Advanced collections
    private Map<UUID, PlayerData> playerCache = new HashMap<>();
    private List<ScheduledEvent> events = new ArrayList<>();
    
    public static class CharacterSettings {
        private int maxLevel = 100;
        private double expMultiplier = 1.0;
        private Map<String, ClassConfig> classes = new HashMap<>();
        private List<LevelReward> levelRewards = new ArrayList<>();
        
        public static class ClassConfig {
            private String displayName;
            private Material icon = Material.IRON_SWORD;
            private Map<String, Integer> baseStats = new HashMap<>();
            private List<String> allowedWeapons = new ArrayList<>();
            private Map<Integer, String> classSkills = new HashMap<>();
        }
        
        public static class LevelReward {
            private int level;
            private RewardType type;
            private String item;
            private int amount;
            private String command;
        }
    }
    
    public static class EconomySettings {
        private double startingMoney = 1000.0;
        private Map<String, Double> itemPrices = new HashMap<>();
        private ShopConfig shop = new ShopConfig();
        private TaxSettings taxes = new TaxSettings();
        
        public static class ShopConfig {
            private boolean enabled = true;
            private Location shopLocation;
            private Map<String, ShopItem> items = new HashMap<>();
            private List<DailyDeal> dailyDeals = new ArrayList<>();
        }
    }
    
    public static class PvPSettings {
        private boolean enabled = true;
        private List<String> disabledWorlds = new ArrayList<>();
        private Map<String, ArenaConfig> arenas = new HashMap<>();
        private TournamentSettings tournaments = new TournamentSettings();
    }
    
    // getters/setters...
}
```

---

## 🎮 Lombok Integration

### Using Lombok for Clean Code

```java
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data  // Generates getters, setters, toString, equals, hashCode
@ConfigsFileProperties(name = "player-settings")
public class PlayerSettings {
    
    private String defaultLanguage = "en";
    private boolean soundEnabled = true;
    private int renderDistance = 8;
    private Map<String, Keybind> keybinds = new HashMap<>();
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Keybind {
        private String action;
        private String key;
        private boolean shift;
        private boolean ctrl;
    }
}
```

**Benefits of Lombok**:
- No boilerplate getter/setter code
- Automatic `toString()` for debugging
- Builder pattern for complex objects
- Cleaner, more readable code

---

## 🔄 Extending MongoConfig (Optional)

### Convenience Methods

```java
@ConfigsFileProperties(name = "guild-config")
public class GuildConfig extends MongoConfig<GuildConfig> {
    
    private Map<String, Guild> guilds = new HashMap<>();
    private GuildSettings settings = new GuildSettings();
    
    // Inherited methods from MongoConfig:
    // - save() / saveAsync()
    // - load() / loadAsync() 
    // - reload()
    // - delete()
    
    // Custom convenience methods
    public Guild getGuild(String name) {
        return guilds.get(name.toLowerCase());
    }
    
    public void addGuild(Guild guild) {
        guilds.put(guild.getName().toLowerCase(), guild);
        save();  // Automatically save after modification
    }
    
    public boolean guildExists(String name) {
        return guilds.containsKey(name.toLowerCase());
    }
    
    public int getMaxMembers() {
        return settings.getMaxMembers();
    }
    
    @Data
    public static class Guild {
        private String name;
        private UUID leader;
        private List<UUID> members = new ArrayList<>();
        private Location homeLocation;
        private double bank = 0.0;
        private LocalDateTime created = LocalDateTime.now();
        private Map<String, Integer> stats = new HashMap<>();
    }
    
    @Data
    public static class GuildSettings {
        private int maxMembers = 50;
        private double creationCost = 10000.0;
        private boolean pvpEnabled = true;
        private List<String> allowedWorlds = new ArrayList<>();
    }
}
```

**Usage with Extended Class**:
```java
// Load
GuildConfig config = cm.loadObject(GuildConfig.class);

// Use convenience methods
Guild guild = config.getGuild("Warriors");
if (!config.guildExists("NewGuild")) {
    Guild newGuild = new Guild();
    newGuild.setName("NewGuild");
    config.addGuild(newGuild);  // Automatically saves!
}

// Or use inherited methods
config.save();           // Save this config
config.saveAsync();      // Async save
config.reload();         // Reload from MongoDB
```

---

## 🗂️ Configuration Organization Patterns

### 1. Feature-Based Organization

```java
// Separate configs for each major feature
@ConfigsFileProperties(name = "economy-config")
public class EconomyConfig { }

@ConfigsFileProperties(name = "pvp-config") 
public class PvPConfig { }

@ConfigsFileProperties(name = "guild-config")
public class GuildConfig { }
```

### 2. Layer-Based Organization

```java
// Core server settings
@ConfigsFileProperties(name = "core-settings")
public class CoreSettings { }

// Game mechanics
@ConfigsFileProperties(name = "game-mechanics")
public class GameMechanics { }

// User interface
@ConfigsFileProperties(name = "ui-settings")
public class UISettings { }
```

### 3. Environment-Based Organization

```java
// Different configs for different environments
@ConfigsDatabase("production")
@ConfigsFileProperties(name = "server-config")
public class ProductionConfig { }

@ConfigsDatabase("testing")
@ConfigsFileProperties(name = "server-config") 
public class TestingConfig { }
```

---

## 🎯 Best Practices

### 1. Use Default Values

```java
@ConfigsFileProperties(name = "server-config")
public class ServerConfig {
    // ✅ Always provide sensible defaults
    private int maxPlayers = 100;           // Not 0!
    private String serverName = "My Server"; // Not null!
    private boolean pvpEnabled = true;      // Explicit default
    private List<String> admins = new ArrayList<>(); // Empty, not null
}
```

### 2. Group Related Settings

```java
// ✅ Good - grouped settings
public class ServerConfig {
    private NetworkSettings network = new NetworkSettings();
    private SecuritySettings security = new SecuritySettings();
    private PerformanceSettings performance = new PerformanceSettings();
}

// ❌ Bad - flat structure
public class ServerConfig {
    private int maxPlayers;
    private String serverIP;
    private boolean antiCheatEnabled;
    private int cacheSize;
    private boolean loggingEnabled;
    // ... 50+ more fields
}
```

### 3. Use Enums for Fixed Values

```java
public class ServerConfig {
    private Difficulty difficulty = Difficulty.NORMAL;
    private GameMode defaultGameMode = GameMode.SURVIVAL;
    private ChatFormat chatFormat = ChatFormat.MODERN;
    
    public enum Difficulty {
        PEACEFUL, EASY, NORMAL, HARD, NIGHTMARE
    }
    
    public enum ChatFormat {
        LEGACY, MODERN, MINIMALIST
    }
}
```

### 4. Validation Methods

```java
@ConfigsFileProperties(name = "server-config")
public class ServerConfig {
    private int maxPlayers = 100;
    private String serverName = "My Server";
    
    // Custom validation
    public void setMaxPlayers(int maxPlayers) {
        if (maxPlayers < 1 || maxPlayers > 1000) {
            throw new IllegalArgumentException("Max players must be between 1 and 1000");
        }
        this.maxPlayers = maxPlayers;
    }
    
    public void setServerName(String serverName) {
        if (serverName == null || serverName.trim().isEmpty()) {
            throw new IllegalArgumentException("Server name cannot be empty");
        }
        this.serverName = serverName.trim();
    }
    
    // Validation method
    public boolean isValid() {
        return maxPlayers > 0 && 
               maxPlayers <= 1000 && 
               serverName != null && 
               !serverName.trim().isEmpty();
    }
}
```

---

## 🚀 Performance Tips

### 1. Lazy Loading for Large Objects

```java
@ConfigsFileProperties(name = "player-data")
public class PlayerDataConfig {
    
    // Load only when needed
    private transient Map<UUID, PlayerStats> cachedStats;
    
    public PlayerStats getPlayerStats(UUID playerId) {
        if (cachedStats == null) {
            cachedStats = loadPlayerStatsFromDB();
        }
        return cachedStats.get(playerId);
    }
}
```

### 2. Separate Large Collections

```java
// ❌ Don't put everything in one config
public class MegaConfig {
    private Map<UUID, PlayerData> allPlayers;     // Could be huge!
    private Map<String, GuildData> allGuilds;     // Could be huge!
    private Map<String, ShopData> allShops;       // Could be huge!
}

// ✅ Separate by concern
@ConfigsFileProperties(name = "player-data")
public class PlayerDataConfig { }

@ConfigsFileProperties(name = "guild-data")
public class GuildDataConfig { }

@ConfigsFileProperties(name = "shop-data")
public class ShopDataConfig { }
```

---

*Next: Learn about [[Message Classes]] for multilingual support.*