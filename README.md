# MongoDB Configs Library

[![](https://jitpack.io/v/aiikk/mongo-configs.svg)](https://jitpack.io/#aiikk/mongo-configs)

Advanced MongoDB configuration and translation management library for Minecraft Paper/Bukkit plugins.

---

### 🗄️ MongoDB Integration
- Reactive Streams Driver with connection pooling
- Change Streams monitoring for selective hot-reload
- Auto-resume on connection failures
- Configurable timeouts and pool settings
- PlayerLanguageDocument storage for language preferences

### 🌍 Multi-Language Support
- `/language` command with configurable base64 display names
- Auto-save player preferences to MongoDB
- Support for nested message keys (`warrior.openTitle`)
- **Lore support** with comma separation (`"lore": "Line1,Line2,Line3"`)
- Fallback to default language
- Separate `languages.yml` configuration file
- Per-language translations and GUI customization

### 🎨 Advanced Color System
- **All color formats supported**: Legacy (`&6`), Hex (`&#54DAF4`)
- **MiniMessage gradients**: `<gradient:#54daf4:#545eb6>text</gradient>`
- **Bukkit RGB format**: `&x&5&4&D&A&F&4` 
- **High-performance caching**: 10k entries, <0.001ms cached lookups
- **Automatic processing**: Colors applied to all messages automatically
- **Smart fallbacks**: Invalid colors preserved, graceful degradation

### 🔧 Easy API
- Simple, intuitive API design
- Async and sync operation support
- Placeholder replacement with `{key}` syntax
- Collection management with auto-creation

## 📦 Installation

### Add to your project

**Maven:**
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
	<dependency>
	    <groupId>com.github.WTJEE.mongo-configs</groupId>
	    <artifactId>configs-api</artifactId>
	    <version>{ReleaseVersion}</version>
	</dependency>
</dependencies>
```

**Gradle:**
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.WTJEE.mongo-configs:configs-api:{ReleaseVersion}''
}
```

### Add to your server
1. Download the plugin jar from releases
2. Place in your `plugins/` folder
3. Configure MongoDB connection in `config.yml`
4. Restart server

## 📚 API Usage

### Get API Instance
```java
ConfigManager config = MongoConfigsAPI.getConfigManager();
LanguageManager lang = MongoConfigsAPI.getLanguageManager();
```

### Configuration Management
```java
// Get config values with defaults
String dbName = config.getConfig("MyPlugin_Config", "database", "default");
boolean enabled = config.getConfig("MyPlugin_Config", "enabled", true);
int maxPlayers = config.getConfig("MyPlugin_Config", "maxPlayers", 100);

// Set config values (async)
config.setConfig("MyPlugin_Config", "maintenance", false);
config.setConfig("MyPlugin_Config", "spawn.world", "world");

// Async operations
config.getConfigAsync("MyPlugin_Config", "setting", String.class)
      .thenAccept(value -> {
          if (value.isPresent()) {
              // Handle value
          }
      });
```

### Message/Translation Management
```java
// Get messages with placeholders - automatically colored!
String msg = config.getMessage("MyPlugin_Config", "pl", "welcome", 
    "player", player.getName(), 
    "server", "SkyPvP");
// Example: "&#54DAF4Witaj {player} &ana serwerze {server}!" 
// Result: Beautiful cyan "Witaj Gracz123 na serwerze SkyPvP!" with colors

// Get lore (comma-separated becomes list) - also colored!
List<String> lore = config.getMessageLore("MyPlugin_Config", "en", "item.sword.lore");

// Set messages with any color format
config.setMessage("MyPlugin_Config", "pl", "goodbye", 
    "<gradient:#FF0000:#0000FF>Do widzenia {player}!</gradient>");
config.setMessage("MyPlugin_Config", "en", "levelup", 
    "&l&6LEVEL UP! &#54DAF4You are now level &{255,215,0}{level}");

// Get plain text without colors
String plainText = config.getPlainMessage("MyPlugin_Config", "en", "welcome", 
    "player", player.getName());

// Nested keys support with colors
config.setMessage("MyPlugin_Config", "en", "gui.buttons.close", "&#FF0000&lClose");
String closeBtn = config.getMessage("MyPlugin_Config", "en", "gui.buttons.close");
```

### 🎨 Color Format Examples
```java
// All these formats work automatically in your messages:

// Legacy colors (classic Bukkit)
"&6Gold &cRed &aBold &ltext &k&nObfuscated"
"&0Black &1Dark Blue &2Dark Green &3Dark Aqua &4Dark Red"
"&5Dark Purple &6Gold &7Gray &8Dark Gray &9Blue"
"&aGreen &bAqua &cRed &dLight Purple &eYellow &fWhite"
"&lBold &mStrikethrough &nUnderline &oItalic &rReset"

// Hex colors (modern, clean)
"&#54DAF4Beautiful cyan &#FF0000bright red"
"&#FFD700Golden &#32CD32Lime &#FF69B4Hot pink"

// Bukkit RGB format (supported by most plugins)
"&x&5&4&D&A&F&4Custom &x&F&F&0&0&0&0colors"
"&x&F&F&D&7&0&0Gold &x&3&2&C&D&3&2Lime"

// Custom RGB format (easy to use)
"&{54,218,244}RGB blue &{255,0,0}RGB red"
"&{255,215,0}Gold &{50,205,50}Lime &{255,105,180}Pink"

// MiniMessage gradients (beautiful transitions)
"<gradient:#54daf4:#545eb6>Amazing gradient text</gradient>"
"<gradient:#FF0000:#FFFF00:#00FF00>Rainbow transition</gradient>"
"<gradient:#FFD700:#FF8C00>Golden fade</gradient>"

// Mixed formats (all together!)
"&6Gold &#54DAF4hex <gradient:#FF0000:#00FF00>gradient</gradient> &{255,255,0}rgb"
"&l&6SERVER &r&8» <gradient:#54daf4:#545eb6>Welcome</gradient> &#FF0000{player}!"

// Real-world examples
"&l&6LEVEL UP! &r&#54DAF4You reached level &{255,215,0}{level}"
"<gradient:#FF6B6B:#4ECDC4>Thanks for playing!</gradient> &aVisit again soon!"
"&8[&6VIP&8] &#54DAF4{player} &7joined the server"
```

### 🎨 Advanced Color Features
```java
// Automatic color processing in all getMessage calls
String coloredMsg = config.getMessage("MyPlugin", "en", "welcome", 
    "player", player.getName());

// Plain text version (no colors, good for console/logs)
String plainMsg = config.getPlainMessage("MyPlugin", "en", "welcome", 
    "player", player.getName());

// Lore with colors (comma-separated becomes colored list)
List<String> coloredLore = config.getMessageLore("MyPlugin", "en", "item.lore");

// Performance monitoring
var colorStats = config.getColorCacheStats();
System.out.println("Color processing hit rate: " + colorStats.hitRate() * 100 + "%");
```

### Collection Management
```java
// Create new collection with languages
config.createCollection("MyPlugin_Data", Set.of("en", "pl", "de"))
      .thenRun(() -> {
          // Collection created, add data
          config.setConfig("MyPlugin_Data", "version", "1.0.0");
          config.setMessage("MyPlugin_Data", "en", "test", "Hello World!");
      });

// Copy language data
config.copyLanguage("MyPlugin_Data", "en", "es")
      .thenRun(() -> {
          // English messages copied to Spanish
          // Now you can edit Spanish versions
      });

// Check if collection exists
if (config.collectionExists("MyPlugin_Data")) {
    // Collection exists
}

// Get supported languages
Set<String> languages = config.getSupportedLanguages("MyPlugin_Data");
```

### Language Management
```java
// Get player's language
String playerLang = lang.getPlayerLanguage(player.getUniqueId().toString());

// Set player's language (sync operation)
lang.setPlayerLanguage(player.getUniqueId().toString(), "pl");

// Set player's language (async with UUID)
lang.setPlayerLanguage(player.getUniqueId(), "pl")
    .thenRun(() -> {
        // Language saved to database
    });

// Set player's language (async with String)
lang.setPlayerLanguageAsync(player.getUniqueId().toString(), "pl")
    .thenRun(() -> {
        // Language saved to database
    });

// Get default language
String defaultLang = lang.getDefaultLanguage();

// Get all supported languages
String[] supportedLangs = lang.getSupportedLanguages();

// Check supported languages
if (lang.isLanguageSupported("de")) {
    // German is supported
}

// Get display name (supports base64)
String displayName = lang.getLanguageDisplayName("pl"); // "Polski"
```

### Performance Monitoring
```java
// Get cache statistics
CacheStats stats = config.getCacheStats();
double hitRate = stats.getHitRate();
long cacheSize = stats.getSize();
long hitCount = stats.getHitCount();
long missCount = stats.getMissCount();

// Get color cache statistics
var colorStats = config.getColorCacheStats();
System.out.println("Color cache hit rate: " + colorStats.hitRate());

// Get performance metrics
PerformanceMetrics metrics = config.getMetrics();
boolean changeStreamsActive = metrics.isChangeStreamsActive();
int activeConnections = metrics.getActiveConnections();
Duration avgMongoTime = metrics.getAverageMongoTime();
long mongoOpsCount = metrics.getMongoOperationsCount();
```

## 🗄️ MongoDB Document Structure

### Configuration Document
```json
{
  "_id": "config",
  "name": "config",
  "data": {
    "database": "skyPvP",
    "maxPlayers": 100,
    "maintenance": false,
    "spawn": {
      "world": "world",
      "x": 0,
      "y": 64,
      "z": 0
    }
  },
  "updatedAt": {"$date": "2025-08-27T10:00:00Z"}
}
```

### Language Document (with colors!)
```json
{
  "_id": "ObjectId(...)",
  "lang": "pl",
  "data": {
    "welcome": "<gradient:#54daf4:#545eb6>Witaj {player}</gradient> &ana serwerze!",
    "goodbye": "&#FF0000Do widzenia {player}!",
    "levelup": "&l&6AWANS! &r&#54DAF4Jesteś teraz na poziomie &{255,215,0}{level}",
    "gui": {
      "title": "<gradient:#FFD700:#FF8C00>Menu Główne</gradient>",
      "buttons": {
        "close": "&#FF0000&lZamknij",
        "next": "&a&lNext Page"
      }
    },
    "item": {
      "sword": {
        "name": "<gradient:#FFD700:#FF8C00>Magiczny Miecz</gradient>",
        "lore": "&7Powerful weapon,&#54DAF4+10 Attack Damage,<gradient:#FF0000:#8B0000>Fire Aspect III</gradient>"
      }
    }
  },
  "updatedAt": {"$date": "2025-08-28T10:00:00Z"}
}
```

## 📋 Commands

### Player Commands
- `/language [lang]` - Select your language or open GUI (aliases: `/lang`, `/jezyk`)

### Admin Commands (mongoconfigs)
- `/mongoconfigs reload [collection]` - Reload configurations
- `/mongoconfigs stats` - Show cache and performance statistics  
- `/mongoconfigs collections` - List all collections
- `/mongoconfigs create <collection> <languages...>` - Create new collection
- `/mongoconfigs copy <collection> <source> <target>` - Copy language data
- `/mongoconfigs help` - Show command help
- Aliases: `/mconfig`, `/mc`

### Config Management Commands (configsmanager)
- `/configsmanager reload [collection]` - Reload specific or all collections
- `/configsmanager stats` - Show detailed cache statistics
- `/configsmanager collections` - List collections with supported languages
- `/configsmanager create <collection> <languages...>` - Create new collection
- `/configsmanager info [collection]` - Show collection information
- Aliases: `/cfgmgr`, `/cm`

## 🏗️ Example Plugin Integration

```java
public class MyPlugin extends JavaPlugin {
    
    private ConfigManager configManager;
    
    @Override
    public void onEnable() {
        // Wait for MongoDB Configs to load
        if (!MongoConfigsAPI.isInitialized()) {
            getLogger().severe("MongoDB Configs not found! Install the plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        configManager = MongoConfigsAPI.getConfigManager();
        
        // Create your plugin's collection
        configManager.createCollection("MyPlugin_Config", Set.of("en", "pl"))
                .thenRun(this::setupDefaultData);
    }
    
    private void setupDefaultData() {
        // Add default config
        configManager.setConfig("MyPlugin_Config", "enabled", true);
        configManager.setConfig("MyPlugin_Config", "maxLevel", 100);
        
        // Add default messages with beautiful colors!
        configManager.setMessage("MyPlugin_Config", "en", "levelUp", 
                "<gradient:#54daf4:#545eb6>Level up!</gradient> &aYou are now level &#FFD700{level}!");
        configManager.setMessage("MyPlugin_Config", "pl", "levelUp", 
                "<gradient:#54daf4:#545eb6>Awans!</gradient> &aJesteś teraz na poziomie &#FFD700{level}!");
                
        // Add colorful lore example
        configManager.setMessage("MyPlugin_Config", "en", "sword.lore",
                "&7A powerful weapon,&#54DAF4+15 Attack Damage,<gradient:#FF0000:#8B0000>Fire Aspect III</gradient>");
                
        // Complex gradient example
        configManager.setMessage("MyPlugin_Config", "en", "welcome",
                "&l&6SERVER &r&8» <gradient:#54daf4:#545eb6>Welcome {player}</gradient> &ato our amazing server!");
    }
    
    public void sendLevelUpMessage(Player player, int level) {
        LanguageManager langManager = MongoConfigsAPI.getLanguageManager();
        String playerLang = langManager.getPlayerLanguage(player.getUniqueId().toString());
        
        // Automatically colored message!
        String message = configManager.getMessage("MyPlugin_Config", playerLang, "levelUp",
                "player", player.getName(),
                "level", level);
                
        player.sendMessage(message);
        
        // For console/logs - get plain text version
        String plainMessage = configManager.getPlainMessage("MyPlugin_Config", playerLang, "levelUp",
                "player", player.getName(),
                "level", level);
        getLogger().info(plainMessage);
    }
}
}
```

## �🆘 Support

- **GitHub Issues**: [Report bugs or request features](https://github.com/WTJEE/mongo-configs/issues)

---
