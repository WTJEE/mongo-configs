# MongoDB Configs API - Developer Wiki

> **Advanced MongoDB configuration and translation management library for Minecraft servers**

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/WTJEE/mongo-configs)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-17+-orange.svg)](https://openjdk.java.net/)
[![MongoDB](https://img.shields.io/badge/mongodb-5.5+-brightgreen.svg)](https://www.mongodb.com/)

## 🚀 Quick Start

```java
// One line = entire configuration system!
ServerConfig config = MongoConfigsAPI.getConfigManager().loadObject(ServerConfig.class);
```

## 📚 Documentation Overview

### 🎯 **Core Concepts**
- **[[Class-Based Configuration]]** - Type-safe configuration with Java classes
- **[[Key-Object Storage]]** - Simple key-value storage for dynamic data
- **[[Annotations Reference]]** - Complete guide to all annotations

### 🛠️ **Developer API**
- **[[ConfigManager API]]** - Core configuration management methods
- **[[LanguageManager API]]** - Multilingual support and player language management
- **[[Messages API]]** - Working with multilingual messages
- **[[MongoDB-Setup|MongoDB Setup]]** - Direct MongoDB operations and advanced features

### 💡 **Practical Examples**
- **[[Shop-Plugin-Example|Shop Plugin Example]]** - Real-world examples and use cases
- **[[Creating-GUI-Components|GUI Development]]** - Creating multilingual GUIs
- **[[Translation Examples]]** - Complete multilingual implementation guide
- **[[Plugin Integration]]** - How to integrate with your plugin

### 🔧 **Advanced Topics**
- **[[Hot Reload System]]** - Dynamic configuration reloading
- **[[Multi-Server-Architecture|Multi-Server Setup]]** - Change Streams and synchronization
- **[[Multi-Server Architecture]]** - Advanced multi-server setup and management
- **[[Error Handling]]** - Robust error management

## � **Featured Examples**

### 🎨 **Multilingual GUI Creation**
```java
// Create language selection GUI with automatic translations
LanguageSelectionGUI gui = new LanguageSelectionGUI(languageManager, messages);
gui.open(player);

// Messages automatically loaded from MongoDB:
// EN: "Select Language", PL: "Wybierz Język", DE: "Sprache Wählen"
```

### 🔄 **Real-Time Multi-Server Sync**
```java
// Changes sync instantly across all servers via Change Streams
@ConfigsFileProperties(name = "global-settings")
public class GlobalConfig extends MongoConfig<GlobalConfig> {
    private boolean maintenanceMode = false;
    private String motd = "Welcome!";
}

// Update on one server, instantly available on all others
GlobalConfig config = cm.loadObject(GlobalConfig.class);
config.setMaintenanceMode(true);
config.save(); // ⚡ Auto-sync to all servers!
```

### 🌍 **Advanced Translation System**
```java
// Complex translations with placeholders and formatting
@ConfigsFileProperties(name = "game-messages")
@SupportedLanguages({"en", "pl", "de", "fr", "es"})
public class GameMessages extends MongoMessages<GameMessages> { }

// Usage with rich formatting
Messages msg = cm.messagesOf(GameMessages.class);
String welcome = msg.get(playerLang, "welcome.player", 
    player.getName(), player.getLevel(), serverName);
// Result: "Welcome Steve! Level: 25 on MyServer"
```

### ⚡ **Performance Optimized Caching**
```java
// Smart caching with TTL and automatic invalidation
PlayerData data = cm.loadObject(PlayerData.class); // ⚡ From cache if available
data.setLastLogin(System.currentTimeMillis());
cm.saveObject(data); // ⚡ Async save, cache updated

// Cache statistics
cm.getCacheStats(); // Hit rate, size, evictions, etc.
```

## 💎 **Why MongoDB Configs?**

| 🏆 MongoDB Configs | 💀 Traditional Config |
|-------------------|---------------------|
| **1 line** of code | 20-50 lines boilerplate |
| ✅ Type Safety | ❌ Runtime errors |
| ✅ Auto-sync servers | ❌ Manual file sync |
| ✅ Complex objects | ❌ Limited support |
| ✅ Smart caching | ❌ Slow file I/O |
| ✅ Hot reload | ❌ Server restart |
| ✅ Multilingual | ❌ Single language |
| ✅ Change Streams | ❌ Polling required |
| ✅ Async operations | ❌ Blocking I/O |

## 🔥 **Example: Complete Plugin in 10 Lines**

```java
public class MyPlugin extends JavaPlugin {

    private ServerConfig config;
    private Messages messages;

    @Override
    public void onEnable() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();

        // 🔥 Two lines = entire configuration system!
        this.config = cm.loadObject(ServerConfig.class);                    // ⚡
        this.messages = cm.messagesOf(MyMessages.class);                   // ⚡

        getLogger().info("Max players: " + config.getMaxPlayers());
        String welcomeMsg = messages.get("en", "welcome.player", "Steve");
        getLogger().info("Welcome message: " + welcomeMsg);
    }
}
```

## � **Language System**

### Player Commands
```bash
/language          # Open language selection GUI
/lang             # Alias for /language
/jezyk            # Polish alias
/language <code>  # Set language directly (en, pl, de, fr, es)
```

### Admin Commands
```bash
/mongoconfigs reload <collection>    # Reload specific config
/mongoconfigs reloadall             # Reload all configs
/mongoconfigs collections           # List available collections
/hotreload test                     # Test hot reload system
```

## 🏗️ **System Architecture**

```
┌─────────────────────────────────────────────────────────────┐
│                      Application Layer                       │
│  • Your Plugin Code  • Commands  • GUI  • Event Handlers    │
├─────────────────────────────────────────────────────────────┤
│                       API Layer                             │
│  ConfigManager │ LanguageManager │ Messages │ Annotations  │
├─────────────────────────────────────────────────────────────┤
│                      Core Layer                             │
│  • ConfigManagerImpl    • Caching (Caffeine)               │
│  • LanguageManagerImpl  • Validation                       │
│  • MongoManager         • Change Streams                   │
├─────────────────────────────────────────────────────────────┤
│                    MongoDB Driver                           │
│  • Reactive Streams  • Connection Pool  • JSON Codecs      │
├─────────────────────────────────────────────────────────────┤
│                   MongoDB Database                          │
│   • Collections  • Documents  • Indexes  • Change Streams  │
└─────────────────────────────────────────────────────────────┘
```

## 🔗 **Quick Links**

- [GitHub Repository](https://github.com/WTJEE/mongo-configs)
- [Issues & Bug Reports](https://github.com/WTJEE/mongo-configs/issues)
- [Discussions](https://github.com/WTJEE/mongo-configs/discussions)
- [Release Notes](https://github.com/WTJEE/mongo-configs/releases)

---

**Get started with [[Class-Based Configuration]] or explore [[Key-Object Storage]]!**