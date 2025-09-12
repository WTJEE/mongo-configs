# Class-Based Configuration

> **Type-safe configuration management with Java classes**

## Overview

Class-based configuration provides a type-safe, object-oriented approach to managing application settings. Instead of working with raw key-value pairs or properties files, you define configuration classes that represent your settings structure.

## Basic Configuration Class

### Creating a Simple Configuration

```java
@ConfigsFileProperties(name = "server-settings")
@ConfigsDatabase("minecraft")
public class ServerConfig extends MongoConfig<ServerConfig> {

    private String serverName = "My Server";
    private int maxPlayers = 100;
    private boolean pvpEnabled = true;
    private List<String> motd = Arrays.asList("Welcome!", "Have fun!");

    // Getters and setters
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public boolean isPvpEnabled() { return pvpEnabled; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }

    public List<String> getMotd() { return motd; }
    public void setMotd(List<String> motd) { this.motd = motd; }
}
```

### Using the Configuration

```java
public class ServerPlugin extends JavaPlugin {

    private ServerConfig config;

    @Override
    public void onEnable() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();

        // Load configuration
        this.config = cm.loadObject(ServerConfig.class);

        // Use configuration values
        getLogger().info("Server: " + config.getServerName());
        getLogger().info("Max players: " + config.getMaxPlayers());
        getLogger().info("PvP enabled: " + config.isPvpEnabled());

        // Display MOTD
        config.getMotd().forEach(line -> getLogger().info("MOTD: " + line));
    }

    public ServerConfig getConfig() {
        return config;
    }
}
```

## Advanced Configuration Features

### Nested Objects

```java
@ConfigsFileProperties(name = "world-settings")
@ConfigsDatabase("minecraft")
public class WorldConfig extends MongoConfig<WorldConfig> {

    private String worldName;
    private Difficulty difficulty = Difficulty.NORMAL;
    private GameMode defaultGamemode = GameMode.SURVIVAL;

    // Nested configuration object
    private WorldBorderSettings borderSettings = new WorldBorderSettings();
    private SpawnSettings spawnSettings = new SpawnSettings();

    public static class WorldBorderSettings {
        private double size = 1000.0;
        private double centerX = 0.0;
        private double centerZ = 0.0;
        private boolean enabled = true;

        // Getters and setters...
    }

    public static class SpawnSettings {
        private Location spawnLocation;
        private boolean spawnProtection = true;
        private int spawnProtectionRadius = 16;

        // Getters and setters...
    }

    // Getters and setters...
}
```

### Collections and Maps

```java
@ConfigsFileProperties(name = "economy-settings")
@ConfigsDatabase("minecraft")
public class EconomyConfig extends MongoConfig<EconomyConfig> {

    private double startingBalance = 100.0;
    private double maxBalance = 1000000.0;
    private boolean allowDebt = false;

    // Collection of items
    private List<String> bannedItems = new ArrayList<>();

    // Map of prices
    private Map<String, Double> itemPrices = new HashMap<>();

    // Set of permissions
    private Set<String> adminPermissions = new HashSet<>();

    // Getters and setters...
}
```

### Configuration with Custom Validation

```java
@ConfigsFileProperties(name = "security-settings")
@ConfigsDatabase("minecraft")
public class SecurityConfig extends MongoConfig<SecurityConfig> {

    private int maxLoginAttempts = 3;
    private long lockoutDurationMinutes = 30;
    private boolean requireStrongPasswords = true;
    private int passwordMinLength = 8;

    // Custom validation
    public boolean isValid() {
        return maxLoginAttempts > 0 &&
               lockoutDurationMinutes > 0 &&
               passwordMinLength >= 6;
    }

    @Override
    public void save() {
        if (!isValid()) {
            throw new IllegalStateException("Configuration validation failed");
        }
        super.save();
    }

    // Getters and setters...
}
```

## Configuration Lifecycle

### Loading Configuration

```java
public class ConfigurationManager {

    private final ConfigManager configManager;

    public ConfigurationManager() {
        this.configManager = MongoConfigsAPI.getConfigManager();
    }

    // Synchronous loading
    public <T extends MongoConfig<T>> T loadConfig(Class<T> configClass) {
        return configManager.loadObject(configClass);
    }

    // Asynchronous loading
    public <T extends MongoConfig<T>> CompletableFuture<T> loadConfigAsync(Class<T> configClass) {
        return configManager.getObject(configClass);
    }

    // Load with default generation
    public <T extends MongoConfig<T>> T loadOrCreateConfig(Class<T> configClass, Supplier<T> defaultGenerator) {
        return configManager.getConfigOrGenerate(configClass, defaultGenerator);
    }
}
```

### Saving Configuration

```java
public class ConfigSaver {

    private final ConfigManager configManager;

    public ConfigSaver() {
        this.configManager = MongoConfigsAPI.getConfigManager();
    }

    // Synchronous save
    public <T extends MongoConfig<T>> void saveConfig(T config) {
        configManager.saveObject(config);
    }

    // Asynchronous save
    public <T extends MongoConfig<T>> CompletableFuture<Void> saveConfigAsync(T config) {
        return configManager.setObject(config);
    }

    // Save with error handling
    public <T extends MongoConfig<T>> boolean safeSaveConfig(T config) {
        try {
            configManager.saveObject(config);
            return true;
        } catch (Exception e) {
            getLogger().error("Failed to save config: " + e.getMessage(), e);
            return false;
        }
    }
}
```

## Configuration Patterns

### Singleton Pattern

```java
public class ConfigRegistry {

    private static ConfigRegistry instance;
    private final ConfigManager configManager;

    private ServerConfig serverConfig;
    private EconomyConfig economyConfig;
    private SecurityConfig securityConfig;

    private ConfigRegistry() {
        this.configManager = MongoConfigsAPI.getConfigManager();
        loadAllConfigs();
    }

    public static ConfigRegistry getInstance() {
        if (instance == null) {
            instance = new ConfigRegistry();
        }
        return instance;
    }

    private void loadAllConfigs() {
        this.serverConfig = configManager.loadObject(ServerConfig.class);
        this.economyConfig = configManager.loadObject(EconomyConfig.class);
        this.securityConfig = configManager.loadObject(SecurityConfig.class);
    }

    public void reloadAll() {
        configManager.reloadAll().thenRun(this::loadAllConfigs);
    }

    // Getters
    public ServerConfig getServerConfig() { return serverConfig; }
    public EconomyConfig getEconomyConfig() { return economyConfig; }
    public SecurityConfig getSecurityConfig() { return securityConfig; }
}
```

### Factory Pattern

```java
public class ConfigurationFactory {

    private final ConfigManager configManager;
    private final Map<Class<?>, MongoConfig<?>> cache = new ConcurrentHashMap<>();

    public ConfigurationFactory() {
        this.configManager = MongoConfigsAPI.getConfigManager();
    }

    @SuppressWarnings("unchecked")
    public <T extends MongoConfig<T>> T getConfig(Class<T> configClass) {
        return (T) cache.computeIfAbsent(configClass, this::loadConfig);
    }

    private <T extends MongoConfig<T>> T loadConfig(Class<T> configClass) {
        return configManager.loadObject(configClass);
    }

    public <T extends MongoConfig<T>> void invalidateConfig(Class<T> configClass) {
        cache.remove(configClass);
    }

    public void invalidateAll() {
        cache.clear();
    }

    public <T extends MongoConfig<T>> T getFreshConfig(Class<T> configClass) {
        invalidateConfig(configClass);
        return getConfig(configClass);
    }
}
```

### Builder Pattern for Complex Configurations

```java
public class ServerConfigBuilder {

    private String serverName = "Default Server";
    private int maxPlayers = 100;
    private boolean pvpEnabled = true;
    private List<String> motd = new ArrayList<>();
    private Difficulty difficulty = Difficulty.NORMAL;

    public ServerConfigBuilder serverName(String name) {
        this.serverName = name;
        return this;
    }

    public ServerConfigBuilder maxPlayers(int max) {
        this.maxPlayers = max;
        return this;
    }

    public ServerConfigBuilder pvpEnabled(boolean enabled) {
        this.pvpEnabled = enabled;
        return this;
    }

    public ServerConfigBuilder addMotdLine(String line) {
        this.motd.add(line);
        return this;
    }

    public ServerConfigBuilder difficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public ServerConfig build() {
        ServerConfig config = new ServerConfig();
        config.setServerName(serverName);
        config.setMaxPlayers(maxPlayers);
        config.setPvpEnabled(pvpEnabled);
        config.setMotd(new ArrayList<>(motd));
        config.setDifficulty(difficulty);
        return config;
    }

    public ServerConfig buildAndSave() {
        ServerConfig config = build();
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        cm.saveObject(config);
        return config;
    }
}

// Usage
ServerConfig config = new ServerConfigBuilder()
    .serverName("My Awesome Server")
    .maxPlayers(500)
    .pvpEnabled(false)
    .addMotdLine("Welcome to the best server!")
    .addMotdLine("Have fun and play fair!")
    .difficulty(Difficulty.NORMAL)
    .buildAndSave();
```

## Configuration Inheritance

### Base Configuration Class

```java
public abstract class BaseConfig<T extends BaseConfig<T>> extends MongoConfig<T> {

    protected String configVersion = "1.0";
    protected long lastModified = System.currentTimeMillis();
    protected String modifiedBy = "system";

    // Common configuration methods
    public void updateMetadata(String modifiedBy) {
        this.lastModified = System.currentTimeMillis();
        this.modifiedBy = modifiedBy;
        save();
    }

    public boolean isOutdated() {
        return System.currentTimeMillis() - lastModified > Duration.ofDays(30).toMillis();
    }

    // Getters and setters...
}
```

### Extended Configuration

```java
@ConfigsFileProperties(name = "game-settings")
@ConfigsDatabase("minecraft")
public class GameConfig extends BaseConfig<GameConfig> {

    private boolean friendlyFire = false;
    private int gameDurationMinutes = 30;
    private List<String> enabledGameModes = Arrays.asList("SURVIVAL", "CREATIVE");

    // Game-specific methods
    public boolean isGameModeEnabled(String mode) {
        return enabledGameModes.contains(mode);
    }

    public void enableGameMode(String mode) {
        if (!enabledGameModes.contains(mode)) {
            enabledGameModes.add(mode);
            updateMetadata("system");
        }
    }

    // Getters and setters...
}
```

## Configuration Validation

### Validation Framework

```java
public class ConfigurationValidator {

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }

        public static ValidationResult success() {
            return new ValidationResult(true, Collections.emptyList());
        }

        public static ValidationResult error(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }

    public ValidationResult validate(ServerConfig config) {
        List<String> errors = new ArrayList<>();

        if (config.getServerName() == null || config.getServerName().trim().isEmpty()) {
            errors.add("Server name cannot be empty");
        }

        if (config.getMaxPlayers() <= 0) {
            errors.add("Max players must be positive");
        }

        if (config.getMaxPlayers() > 1000) {
            errors.add("Max players cannot exceed 1000");
        }

        if (config.getMotd() == null || config.getMotd().isEmpty()) {
            errors.add("MOTD cannot be empty");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
    }

    public ValidationResult validate(EconomyConfig config) {
        List<String> errors = new ArrayList<>();

        if (config.getStartingBalance() < 0) {
            errors.add("Starting balance cannot be negative");
        }

        if (config.getMaxBalance() <= config.getStartingBalance()) {
            errors.add("Max balance must be greater than starting balance");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
    }
}
```

### Configuration with Validation

```java
@ConfigsFileProperties(name = "validated-server-settings")
@ConfigsDatabase("minecraft")
public class ValidatedServerConfig extends MongoConfig<ValidatedServerConfig> {

    private String serverName;
    private int maxPlayers = 100;
    private List<String> motd = new ArrayList<>();

    @Override
    public void save() {
        // Validate before saving
        ValidationResult result = validate();
        if (!result.isValid()) {
            throw new IllegalStateException("Configuration validation failed: " +
                String.join(", ", result.getErrors()));
        }

        super.save();
    }

    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();

        if (serverName == null || serverName.trim().isEmpty()) {
            errors.add("Server name is required");
        }

        if (maxPlayers <= 0) {
            errors.add("Max players must be positive");
        }

        if (motd == null || motd.isEmpty()) {
            errors.add("At least one MOTD line is required");
        }

        return errors.isEmpty() ?
            ValidationResult.success() :
            ValidationResult.error(errors);
    }

    // Getters and setters...
}
```

## Best Practices

### 1. Use Meaningful Names

```java
// ✅ Good
@ConfigsFileProperties(name = "server-configuration")
public class ServerConfig { }

// ❌ Avoid
@ConfigsFileProperties(name = "config")
public class ServerConfig { }
```

### 2. Provide Default Values

```java
// ✅ Good - sensible defaults
private int maxPlayers = 100;
private boolean pvpEnabled = true;
private String serverName = "Default Server";

// ❌ Avoid - null defaults
private Integer maxPlayers;
private Boolean pvpEnabled;
private String serverName;
```

### 3. Use Collections Appropriately

```java
// ✅ Good - immutable defaults
private List<String> motd = Arrays.asList("Welcome!");
private Set<String> bannedItems = new HashSet<>();
private Map<String, Double> prices = new HashMap<>();

// ❌ Avoid - mutable defaults that can cause issues
private List<String> motd = new ArrayList<>();
private Set<String> bannedItems = new HashSet<>();
```

### 4. Document Configuration Options

```java
@ConfigsFileProperties(name = "advanced-server-settings")
@ConfigsDatabase("minecraft")
public class AdvancedServerConfig extends MongoConfig<AdvancedServerConfig> {

    /**
     * Maximum number of players allowed on the server.
     * Range: 1-1000, Default: 100
     */
    private int maxPlayers = 100;

    /**
     * Whether PvP combat is enabled on the server.
     * This affects player vs player interactions.
     */
    private boolean pvpEnabled = true;

    /**
     * Server message of the day displayed to players.
     * Supports multiple lines and color codes.
     */
    private List<String> motd = Arrays.asList("Welcome to our server!");

    // Getters and setters with documentation...
}
```

### 5. Handle Configuration Changes

```java
public class ConfigurationChangeHandler {

    private final ConfigManager configManager;
    private ServerConfig lastKnownConfig;

    public ConfigurationChangeHandler() {
        this.configManager = MongoConfigsAPI.getConfigManager();
        this.lastKnownConfig = configManager.loadObject(ServerConfig.class);
    }

    public void checkForChanges() {
        ServerConfig currentConfig = configManager.loadObject(ServerConfig.class);

        if (hasConfigChanged(lastKnownConfig, currentConfig)) {
            handleConfigurationChange(currentConfig);
            lastKnownConfig = currentConfig;
        }
    }

    private boolean hasConfigChanged(ServerConfig oldConfig, ServerConfig newConfig) {
        return !Objects.equals(oldConfig.getMaxPlayers(), newConfig.getMaxPlayers()) ||
               !Objects.equals(oldConfig.isPvpEnabled(), newConfig.isPvpEnabled()) ||
               !Objects.equals(oldConfig.getServerName(), newConfig.getServerName());
    }

    private void handleConfigurationChange(ServerConfig newConfig) {
        // Update server settings
        if (Bukkit.getServer() != null) {
            Bukkit.getServer().setMaxPlayers(newConfig.getMaxPlayers());

            // Broadcast configuration change
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Server configuration updated!");

            // Log the change
            getLogger().info("Server configuration updated: maxPlayers=" + newConfig.getMaxPlayers());
        }
    }
}
```

---

*Next: Learn about [[Key-Object Storage]] for simple key-value configurations.*