# Annotations Reference

> **Complete guide to all MongoDB Configs API annotations**

## Core Annotations

### @ConfigsFileProperties

Marks a class as a configuration class that maps to a MongoDB document.

```java
@ConfigsFileProperties(name = "server-settings")
public class ServerConfig extends MongoConfig<ServerConfig> {
    // Configuration fields...
}
```

**Parameters:**
- `name`: Document ID in MongoDB collection (required)

### @ConfigsDatabase

Specifies the MongoDB database for the configuration class.

```java
@ConfigsDatabase("minecraft")
public class ServerConfig extends MongoConfig<ServerConfig> {
    // This config will be stored in 'minecraft' database
}
```

**Parameters:**
- `value`: Database name (required, default: "configs")

### @ConfigsCollection

Specifies the MongoDB collection for the configuration class.

```java
@ConfigsCollection("servers")
public class ServerConfig extends MongoConfig<ServerConfig> {
    // This config will be stored in 'servers' collection
}
```

**Parameters:**
- `value`: Collection name (optional, defaults to document ID from @ConfigsFileProperties)

## Message Annotations

### @SupportedLanguages

Defines which languages are supported for message translation in MongoMessages classes.

```java
@SupportedLanguages({"en", "pl", "de", "fr", "es"})
@ConfigsFileProperties(name = "messages")
public class GameMessages {

    private String welcome = "Welcome!";
    private String goodbye = "Goodbye!";
    // Messages will be available in all 5 languages
}
```

**Parameters:**
- `value`: Array of language codes (required)

## Usage Examples

### Basic Configuration Class

```java
@ConfigsFileProperties(name = "server-settings")
@ConfigsDatabase("minecraft")
public class ServerConfig extends MongoConfig<ServerConfig> {

    private String serverName = "My Server";
    private int maxPlayers = 100;
    private List<String> motd = Arrays.asList("Welcome!");
    private boolean pvpEnabled = true;

    // Getters and setters...
}
```

### Multilingual Messages Class

```java
@SupportedLanguages({"en", "pl", "de"})
@ConfigsFileProperties(name = "game-messages")
@ConfigsDatabase("minecraft")
public class GameMessages {

    // Messages will be stored and retrieved based on language
    private String playerJoined = "Player joined!";
    private String playerLeft = "Player left!";
    private String noPermission = "No permission!";

    @Override
    public String getMessage(String lang, String key, Map<String, Object> params) {
        // Implementation for retrieving messages with placeholders
        String message = getMessage(lang, key);
        if (message == null) return key;

        // Simple placeholder replacement
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue().toString());
        }
        return message;
    }

    @Override
    public void putMessage(String lang, String key, String value) {
        // Implementation for storing messages
    }

    @Override
    public Map<String, String> allKeys(String lang) {
        // Implementation for getting all message keys for a language
        return Map.of();
    }

    // Getters and setters...
}
```

### Advanced Configuration with Collection Override

```java
@ConfigsFileProperties(name = "world-settings")
@ConfigsDatabase("minecraft")
@ConfigsCollection("worlds")
public class WorldConfig extends MongoConfig<WorldConfig> {

    private String worldName;
    private Difficulty difficulty = Difficulty.NORMAL;
    private boolean allowMonsters = true;
    private long seed;

    // Getters and setters...
}
```

## Annotation Processing

The `Annotations` utility class provides helper methods to extract annotation values:

```java
public class ConfigProcessor {

    public static void processConfigClass(Class<?> configClass) {
        // Extract document ID
        String documentId = Annotations.idFrom(configClass);

        // Extract database (null means use default)
        String database = Annotations.databaseFrom(configClass);

        // Extract collection (falls back to document ID if not specified)
        String collection = Annotations.collectionFrom(configClass);

        // Extract supported languages (empty set if not specified)
        Set<String> languages = Annotations.langsFrom(configClass);

        // Use the extracted values...
        System.out.println("Processing config: " + documentId +
                          " in database: " + database +
                          " collection: " + collection +
                          " languages: " + languages);
    }
}
```

## Best Practices

### 1. Use Descriptive Document IDs

```java
// ✅ Good
@ConfigsFileProperties(name = "server-configuration")
public class ServerConfig { }

// ✅ Good
@ConfigsFileProperties(name = "player-messages")
public class PlayerMessages { }

// ❌ Avoid
@ConfigsFileProperties(name = "config")
public class ServerConfig { }
```

### 2. Consistent Database Organization

```java
// ✅ Good - group related configs in same database
@ConfigsDatabase("minecraft")
@ConfigsFileProperties(name = "server-settings")
public class ServerConfig { }

@ConfigsDatabase("minecraft")
@ConfigsFileProperties(name = "world-settings")
public class WorldConfig { }

// ❌ Avoid - scattered databases
@ConfigsDatabase("server")
@ConfigsFileProperties(name = "settings")
public class ServerConfig { }

@ConfigsDatabase("worlds")
@ConfigsFileProperties(name = "config")
public class WorldConfig { }
```

### 3. Use SupportedLanguages for I18n

```java
// ✅ Good - explicit language support
@SupportedLanguages({"en", "es", "fr", "de", "pl"})
@ConfigsFileProperties(name = "ui-messages")
public class UIMessages { }

// ✅ Good - single language
@SupportedLanguages({"en"})
@ConfigsFileProperties(name = "system-messages")
public class SystemMessages { }
```

### 4. Collection Naming

```java
// ✅ Good - use @ConfigsCollection for custom collections
@ConfigsCollection("player-data")
@ConfigsFileProperties(name = "player-stats")
public class PlayerStats { }

// ✅ Good - let it default to document ID
@ConfigsFileProperties(name = "server-config")
public class ServerConfig { } // Will use "server-config" collection
```

## Common Patterns

### Configuration Hierarchy

```java
// Global configuration
@ConfigsFileProperties(name = "global-config")
@ConfigsDatabase("minecraft")
public class GlobalConfig extends MongoConfig<GlobalConfig> {
    private boolean maintenanceMode = false;
    private int globalMaxPlayers = 1000;
}

// Server-specific configuration
@ConfigsFileProperties(name = "server-config")
@ConfigsDatabase("minecraft")
public class ServerConfig extends MongoConfig<ServerConfig> {
    private String serverName;
    private int maxPlayers = 100;
    private List<String> motd;
}

// World-specific configuration
@ConfigsFileProperties(name = "world-config")
@ConfigsDatabase("minecraft")
@ConfigsCollection("worlds")
public class WorldConfig extends MongoConfig<WorldConfig> {
    private String worldName;
    private Difficulty difficulty = Difficulty.NORMAL;
    private boolean pvpEnabled = true;
}
```

### Message Organization

```java
@SupportedLanguages({"en", "pl", "de"})
@ConfigsFileProperties(name = "chat-messages")
@ConfigsDatabase("minecraft")
public class ChatMessages {

    // Chat-related messages
    private String playerJoined = "Player {player} joined!";
    private String playerLeft = "Player {player} left!";
    private String privateMessage = "[PM] {sender}: {message}";
}

@SupportedLanguages({"en", "pl", "de"})
@ConfigsFileProperties(name = "command-messages")
@ConfigsDatabase("minecraft")
public class CommandMessages {

    // Command-related messages
    private String noPermission = "You don't have permission!";
    private String playerNotFound = "Player not found!";
    private String teleportSuccess = "Teleported to {location}!";
}
```

---

*Need more details? Check out [[Configuration Classes]] for complete examples.*