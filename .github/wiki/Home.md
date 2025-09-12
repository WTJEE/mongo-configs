# MongoDB Configs API - Developer Wiki

Welcome to the comprehensive developer documentation for **MongoDB Configs API** - the most advanced configuration management library for Minecraft servers!

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
- **[[MongoDB Integration]]** - Direct MongoDB operations and advanced features

### 💡 **Practical Examples**
- **[[Example Usage]]** - Real-world examples and use cases
- **[[GUI Development]]** - Creating multilingual GUIs
- **[[Plugin Integration]]** - How to integrate with your plugin
- **[[Performance Tips]]** - Optimization and best practices

### 🔧 **Advanced Topics**
- **[[Hot Reload System]]** - Dynamic configuration reloading
- **[[Multi-Server Setup]]** - Change Streams and synchronization
- **[[Error Handling]]** - Robust error management
- **[[Troubleshooting]]** - Common issues and solutions

## 🎮 **Language System**

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

## 💎 **Why MongoDB Configs?**

| 🏆 MongoDB Configs | 💀 Traditional Config |
|-------------------|---------------------|
| **1 line** of code | 20-50 lines boilerplate |
| ✅ Type Safety | ❌ Runtime errors |
| ✅ Auto-sync servers | ❌ Manual file sync |
| ✅ Complex objects | ❌ Limited support |
| ✅ Smart caching | ❌ Slow file I/O |
| ✅ Hot reload | ❌ Server restart |

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

## 🌟 **Featured Examples**

- **[[Shop Plugin Example]]** - Dynamic pricing and multilingual shop
- **[[Economy System Example]]** - Player data, transactions, multi-currency
- **[[Parkour Plugin Example]]** - Mini-game configuration and progress tracking
- **[[Home System Example]]** - Teleportation and player homes management

---

**Get started with [[Class-Based Configuration]] or explore [[Key-Object Storage]]!**