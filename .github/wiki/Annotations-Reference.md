# Annotations Reference

> **Complete guide to all MongoDB Configs API annotations**

## Core Annotations

### @ConfigsFileProperties

Marks a class as a configuration class that maps to a MongoDB collection.

```java
@ConfigsFileProperties(
    name = "server-settings",           // Collection name in MongoDB
    cache = true,                       // Enable caching (default: true)
    cacheTime = 300,                    // Cache TTL in seconds (default: 300)
    async = true                        // Async operations (default: true)
)
public class ServerConfig extends MongoConfig<ServerConfig> {
    // Configuration fields...
}
```

**Parameters:**
- `name`: MongoDB collection name (required)
- `cache`: Enable caching for this config (default: true)
- `cacheTime`: Cache time-to-live in seconds (default: 300)
- `async`: Use async operations for save/load (default: true)

### @ConfigsDatabase

Specifies the MongoDB database for the configuration class.

```java
@ConfigsDatabase("minecraft")  // Use 'minecraft' database
public class ServerConfig extends MongoConfig<ServerConfig> {
    // This config will be stored in 'minecraft' database
}
```

**Parameters:**
- `value`: Database name (required)

### @ConfigsField

Customizes how individual fields are stored and loaded.

```java
public class ServerConfig extends MongoConfig<ServerConfig> {

    @ConfigsField(
        name = "max_players",            // Custom field name in MongoDB
        defaultValue = "100",            // Default value as string
        required = true,                 // Field must be present
        encrypted = false                // Encrypt field value
    )
    private int maxPlayers = 100;

    @ConfigsField(
        name = "server_motd",
        defaultValue = "[\"Welcome!\", \"Have fun!\"]"
    )
    private List<String> motd;
}
```

**Parameters:**
- `name`: Custom field name in MongoDB (default: field name)
- `defaultValue`: Default value as JSON string
- `required`: Field must be present in document (default: false)
- `encrypted`: Encrypt field value (default: false)

## Message Annotations

### @SupportedLanguages

Defines which languages are supported for message translation.

```java
@SupportedLanguages({"en", "pl", "de", "fr", "es"})
@ConfigsFileProperties(name = "messages")
public class Messages extends MongoConfig<Messages> {

    private String welcome = "Welcome!";
    private String goodbye = "Goodbye!";
    // Messages will be available in all 5 languages
}
```

**Parameters:**
- `value`: Array of language codes (required)

### @MessageKey

Customizes message keys for translation.

```java
public class Messages extends MongoConfig<Messages> {

    @MessageKey("player.join")
    private String playerJoined = "Player {player} joined!";

    @MessageKey("player.quit")
    private String playerLeft = "Player {player} left!";
}
```

**Parameters:**
- `value`: Custom message key (default: field name)

### @Translation

Provides translations for specific languages inline.

```java
public class Messages extends MongoConfig<Messages> {

    @Translation({
        @TranslationEntry(language = "en", value = "Welcome!"),
        @TranslationEntry(language = "pl", value = "Witaj!"),
        @TranslationEntry(language = "de", value = "Willkommen!"),
        @TranslationEntry(language = "fr", value = "Bienvenue!")
    })
    private String welcome;
}
```

## Validation Annotations

### @NotNull

Ensures a field cannot be null.

```java
public class ServerConfig extends MongoConfig<ServerConfig> {

    @NotNull
    private String serverName;

    @NotNull(message = "Max players cannot be null")
    private Integer maxPlayers;
}
```

**Parameters:**
- `message`: Custom error message (optional)

### @Min / @Max

Validates numeric field ranges.

```java
public class ServerConfig extends MongoConfig<ServerConfig> {

    @Min(value = 1, message = "Max players must be at least 1")
    @Max(value = 1000, message = "Max players cannot exceed 1000")
    private int maxPlayers = 100;

    @Min(value = 0.0)
    @Max(value = 1.0)
    private double tpsThreshold = 0.5;
}
```

**Parameters:**
- `value`: Minimum/maximum value (required)
- `message`: Custom error message (optional)

### @Size

Validates collection sizes.

```java
public class ServerConfig extends MongoConfig<ServerConfig> {

    @Size(min = 1, max = 10, message = "MOTD must have 1-10 lines")
    private List<String> motd;

    @Size(min = 3, message = "Server name must be at least 3 characters")
    private String serverName;
}
```

**Parameters:**
- `min`: Minimum size (default: 0)
- `max`: Maximum size (default: Integer.MAX_VALUE)
- `message`: Custom error message (optional)

### @Pattern

Validates string patterns using regex.

```java
public class ServerConfig extends MongoConfig<ServerConfig> {

    @Pattern(
        regexp = "^[a-zA-Z0-9_-]+$",
        message = "Server name can only contain letters, numbers, underscores, and hyphens"
    )
    private String serverName;

    @Pattern(
        regexp = "^#[0-9A-Fa-f]{6}$",
        message = "Chat color must be a valid hex color (e.g., #FF0000)"
    )
    private String chatColor = "#FFFFFF";
}
```

**Parameters:**
- `regexp`: Regular expression pattern (required)
- `message`: Custom error message (optional)

## Advanced Annotations

### @Encrypted

Marks fields that should be encrypted in the database.

```java
public class ServerConfig extends MongoConfig<ServerConfig> {

    @Encrypted
    private String databasePassword;

    @Encrypted(algorithm = "AES256")
    private String apiKey;
}
```

**Parameters:**
- `algorithm`: Encryption algorithm (default: AES256)

### @Transient

Excludes fields from database storage.

```java
public class ServerConfig extends MongoConfig<ServerConfig> {

    private String serverName;

    @Transient
    private long lastModified; // Won't be saved to database

    @Transient
    private transient Object cache; // Runtime-only field
}
```

### @Indexed

Creates database indexes for better query performance.

```java
public class PlayerData extends MongoConfig<PlayerData> {

    @Indexed(unique = true)
    private String playerId;

    @Indexed
    private String playerName;

    @Indexed(expireAfterSeconds = 86400) // 24 hours
    private long lastSeen;
}
```

**Parameters:**
- `unique`: Create unique index (default: false)
- `expireAfterSeconds`: TTL index in seconds (default: -1, no expiry)

### @Version

Enables optimistic locking for concurrent updates.

```java
public class ServerConfig extends MongoConfig<ServerConfig> {

    private String serverName;

    @Version
    private long version; // Automatically managed by MongoDB
}
```

## Usage Examples

### Complete Configuration Class

```java
@ConfigsFileProperties(
    name = "server-settings",
    cache = true,
    cacheTime = 600,
    async = true
)
@ConfigsDatabase("minecraft")
public class ServerConfig extends MongoConfig<ServerConfig> {

    @NotNull(message = "Server name is required")
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9 _-]+$", message = "Invalid server name format")
    private String serverName;

    @Min(value = 1)
    @Max(value = 1000)
    private int maxPlayers = 100;

    @Size(min = 1, max = 10)
    private List<String> motd = Arrays.asList("Welcome!");

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    private String primaryColor = "#FFFFFF";

    @Encrypted
    private String databasePassword;

    @Indexed(unique = true)
    private String serverId;

    @Version
    private long version;

    // Getters and setters...
}
```

### Multilingual Messages Class

```java
@SupportedLanguages({"en", "pl", "de", "fr", "es"})
@ConfigsFileProperties(name = "messages")
@ConfigsDatabase("minecraft")
public class Messages extends MongoConfig<Messages> {

    @MessageKey("player.join")
    @Translation({
        @TranslationEntry(language = "en", value = "Player {player} joined the game!"),
        @TranslationEntry(language = "pl", value = "Gracz {player} dołączył do gry!"),
        @TranslationEntry(language = "de", value = "Spieler {player} ist dem Spiel beigetreten!"),
        @TranslationEntry(language = "fr", value = "Le joueur {player} a rejoint la partie!"),
        @TranslationEntry(language = "es", value = "¡El jugador {player} se unió al juego!")
    })
    private String playerJoined;

    @MessageKey("command.no-permission")
    private String noPermission = "You don't have permission to use this command.";

    @MessageKey("teleport.success")
    private String teleportSuccess = "Teleported to {location}.";

    // Getters and setters...
}
```

## Annotation Processing

### Custom Annotation Processor

```java
public class ConfigAnnotationProcessor {

    public static void processAnnotations(Class<?> configClass) {
        // Get all annotations
        ConfigsFileProperties fileProps = configClass.getAnnotation(ConfigsFileProperties.class);
        ConfigsDatabase database = configClass.getAnnotation(ConfigsDatabase.class);
        SupportedLanguages languages = configClass.getAnnotation(SupportedLanguages.class);

        // Process configuration
        String collectionName = fileProps.name();
        String databaseName = database.value();
        String[] supportedLangs = languages != null ? languages.value() : new String[]{"en"};

        // Validate annotations
        validateAnnotations(configClass);

        // Generate metadata
        ConfigMetadata metadata = generateMetadata(configClass, collectionName, databaseName, supportedLangs);
    }

    private static void validateAnnotations(Class<?> configClass) {
        // Check for required annotations
        if (!configClass.isAnnotationPresent(ConfigsFileProperties.class)) {
            throw new IllegalArgumentException("Class must be annotated with @ConfigsFileProperties");
        }

        // Validate field annotations
        for (Field field : configClass.getDeclaredFields()) {
            validateFieldAnnotations(field);
        }
    }

    private static void validateFieldAnnotations(Field field) {
        // Check for conflicting annotations
        boolean hasNotNull = field.isAnnotationPresent(NotNull.class);
        boolean hasEncrypted = field.isAnnotationPresent(Encrypted.class);
        boolean hasTransient = field.isAnnotationPresent(Transient.class);

        if (hasEncrypted && hasTransient) {
            throw new IllegalArgumentException("Field cannot be both @Encrypted and @Transient: " + field.getName());
        }

        // Validate annotation combinations
        if (hasNotNull && field.getType().isPrimitive()) {
            // Primitive types are never null, @NotNull is redundant but allowed
        }
    }
}
```

## Best Practices

### 1. Use Descriptive Names

```java
// ✅ Good
@ConfigsFileProperties(name = "server-configuration")
private String serverName;

// ❌ Avoid
@ConfigsFileProperties(name = "config")
private String sn;
```

### 2. Group Related Annotations

```java
// ✅ Good - all validation together
@NotNull
@Size(min = 3, max = 50)
@Pattern(regexp = "^[a-zA-Z0-9_-]+$")
private String serverName;

// ❌ Avoid - scattered annotations
@NotNull
private String serverName;

@Size(min = 3, max = 50)
private String serverName;

@Pattern(regexp = "^[a-zA-Z0-9_-]+$")
private String serverName;
```

### 3. Use Consistent Patterns

```java
// ✅ Consistent regex patterns
@Pattern(regexp = "^#[0-9A-Fa-f]{6}$")  // Hex colors
@Pattern(regexp = "^[a-zA-Z0-9_-]+$")   // Server names
@Pattern(regexp = "^\\d+\\.\\d+\\.\\d+\\.\\d+$")  // IP addresses
```

### 4. Document Custom Annotations

```java
/**
 * Custom annotation for server configuration fields.
 * This field will be automatically encrypted and cached.
 */
@ConfigsField(name = "api_key", encrypted = true)
@Indexed(unique = true)
private String apiKey;
```

---

*Need more details? Check out [[Configuration Classes]] for complete examples.*