# Annotations Reference

Complete guide to all annotations in MongoDB Configs API.

## 🎯 Core Annotations

### @ConfigsFileProperties

**Purpose**: Defines the MongoDB collection name for your configuration class.

```java
@ConfigsFileProperties(name = "server-settings")
public class ServerConfig {
    private int maxPlayers = 100;
    private String serverName = "My Server";
    // getters/setters...
}
```

**Parameters**:
- `name` (required) - MongoDB collection name where this config will be stored

**MongoDB Result**: Creates a document in the `server-settings` collection with your configuration data.

---

### @ConfigsDatabase

**Purpose**: Specifies a custom database name (overrides default from plugin config).

```java
@ConfigsDatabase("economy-server")
@ConfigsFileProperties(name = "shop-data")
public class ShopConfig {
    private Map<String, Double> prices = new HashMap<>();
}
```

**Parameters**:
- `value` (required) - Custom database name

**Use Cases**:
- Separating different server types (economy, pvp, creative)
- Multi-tenant setups
- Database organization

---

### @ConfigsCollection

**Purpose**: Specifies a custom collection name (overrides the one from @ConfigsFileProperties).

```java
@ConfigsCollection("advanced-shop-data")
@ConfigsFileProperties(name = "shop")  // This will be ignored
public class ShopConfig {
    private Map<String, Double> prices = new HashMap<>();
}
```

**Parameters**:
- `value` (required) - Custom collection name

**Priority**: @ConfigsCollection > @ConfigsFileProperties name parameter

---

## 🔧 Advanced Annotation Combinations

### Multi-Database Setup

```java
// Main server database
@ConfigsFileProperties(name = "server-config")
public class ServerConfig { }

// Economy database
@ConfigsDatabase("economy")
@ConfigsFileProperties(name = "shop-config")
public class ShopConfig { }

// Player data database
@ConfigsDatabase("playerdata")
@ConfigsCollection("player-stats")
@ConfigsFileProperties(name = "ignored")  // This name is ignored
public class PlayerStats { }
```

**Result**:
- `ServerConfig` → `minecraft.server-config` (default database)
- `ShopConfig` → `economy.shop-config`
- `PlayerStats` → `playerdata.player-stats`

---

## 📝 Message Class Annotations

### For Message Classes

```java
@ConfigsFileProperties(name = "gui-messages")
public class GuiMessages {
    // Empty class - system handles all languages automatically
}

@ConfigsDatabase("translations")
@ConfigsFileProperties(name = "shop-messages")
public class ShopMessages {
    // Will store messages in translations.shop-messages
}
```

**MongoDB Structure** for messages:

```javascript
// Collection: gui-messages
{
  "_id": "en",
  "welcome": {
    "title": "Welcome!",
    "message": "Hello {player}!"
  },
  "gui": {
    "buttons": {
      "confirm": "Confirm",
      "cancel": "Cancel"
    }
  }
}

{
  "_id": "pl", 
  "welcome": {
    "title": "Witaj!",
    "message": "Cześć {player}!"
  },
  "gui": {
    "buttons": {
      "confirm": "Potwierdź",
      "cancel": "Anuluj"
    }
  }
}
```

---

## ⚠️ Important Notes

### Annotation Requirements

1. **@ConfigsFileProperties is mandatory** for all config classes
2. **@ConfigsDatabase and @ConfigsCollection are optional**
3. **Order doesn't matter** - annotations can be in any order

### Naming Conventions

```java
// ✅ Good names
@ConfigsFileProperties(name = "server-settings")
@ConfigsFileProperties(name = "player-data")
@ConfigsFileProperties(name = "gui-messages")

// ❌ Avoid these
@ConfigsFileProperties(name = "ServerSettings")  // PascalCase
@ConfigsFileProperties(name = "player_data")     // snake_case with underscores
@ConfigsFileProperties(name = "GUI Messages")    // Spaces
```

**Best Practice**: Use lowercase with hyphens (`kebab-case`)

---

## 🔄 Runtime Behavior

### Collection Resolution Order

1. **@ConfigsCollection value** (highest priority)
2. **@ConfigsFileProperties name parameter**
3. **Class name** (if no annotations - NOT RECOMMENDED)

### Database Resolution Order

1. **@ConfigsDatabase value** (if present)
2. **Plugin configuration default database**
3. **"minecraft"** (fallback)

---

## 📊 Complete Example

```java
// Complex configuration with all annotations
@ConfigsDatabase("mmo-server")
@ConfigsCollection("character-progression")
@ConfigsFileProperties(name = "character-config")  // Ignored due to @ConfigsCollection
public class CharacterConfig {
    
    private int maxLevel = 100;
    private Map<String, ClassData> classes = new HashMap<>();
    private List<LevelReward> rewards = new ArrayList<>();
    
    public static class ClassData {
        private String name;
        private Map<String, Integer> baseStats;
        private List<String> allowedWeapons;
    }
    
    public static class LevelReward {
        private int level;
        private String rewardType;
        private int amount;
    }
    
    // getters/setters...
}
```

**Result**: Stored in `mmo-server.character-progression` collection

**Usage**:
```java
ConfigManager cm = MongoConfigsAPI.getConfigManager();
CharacterConfig config = cm.loadObject(CharacterConfig.class);  // ⚡ One line!

// Modify
config.setMaxLevel(150);
config.getClasses().put("warrior", new ClassData());

// Save
cm.saveObject(config);  // ⚡ Auto-sync to all servers!
```

---

## 🎯 Best Practices

### 1. Consistent Naming
```java
// ✅ Good - consistent project naming
@ConfigsFileProperties(name = "mmo-character-config")
@ConfigsFileProperties(name = "mmo-guild-config") 
@ConfigsFileProperties(name = "mmo-economy-config")
```

### 2. Database Organization
```java
// ✅ Good - logical database separation
@ConfigsDatabase("mmo-main")        // Core game configs
@ConfigsDatabase("mmo-economy")     // Economy-related
@ConfigsDatabase("mmo-player-data") // Player-specific data
```

### 3. Message Organization
```java
// ✅ Good - feature-based message grouping
@ConfigsFileProperties(name = "gui-messages")     // All GUI text
@ConfigsFileProperties(name = "command-messages") // Command responses  
@ConfigsFileProperties(name = "game-messages")    // Gameplay messages
```

---

*Next: Learn about [[Configuration Classes]] and how to structure your config data.*