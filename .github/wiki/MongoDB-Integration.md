# MongoDB Integration

Complete guide to MongoDB database configuration, setup, and optimization for the MongoDB Configs API.

## 🚀 Database Setup

### MongoDB Connection Configuration

```java
@ConfigsDatabase(
    uri = "mongodb://localhost:27017",
    database = "minecraft_configs"
)
public class DatabaseConfig {
    // Configuration properties
}
```

### Advanced Connection Options

```java
@ConfigsDatabase(
    uri = "mongodb+srv://username:password@cluster.mongodb.net",
    database = "production_configs",
    connectionTimeout = 30000,      // 30 seconds
    socketTimeout = 60000,          // 60 seconds
    maxPoolSize = 100,              // Maximum connections
    minPoolSize = 5,                // Minimum connections
    maxIdleTime = 300000,           // 5 minutes
    maxLifeTime = 1800000,          // 30 minutes
    retryWrites = true,
    retryReads = true
)
public class ProductionDatabaseConfig {
    
    @ConfigsFileProperties(
        fileName = "database.yml",
        resource = true
    )
    private boolean ssl = true;
    private String authDatabase = "admin";
    private int connectionsPerHost = 20;
    private int threadsAllowedToBlockForConnectionMultiplier = 5;
    
    // Getters and setters...
}
```

---

## 🔧 Connection URI Formats

### Local MongoDB

```java
// Basic local connection
"mongodb://localhost:27017"

// Local with authentication
"mongodb://username:password@localhost:27017"

// Local with specific database
"mongodb://localhost:27017/minecraft_configs"

// Local replica set
"mongodb://localhost:27017,localhost:27018,localhost:27019/?replicaSet=rs0"
```

### MongoDB Atlas (Cloud)

```java
// Atlas cluster
"mongodb+srv://username:password@cluster0.xxxxx.mongodb.net"

// Atlas with database
"mongodb+srv://username:password@cluster0.xxxxx.mongodb.net/minecraft_configs"

// Atlas with options
"mongodb+srv://username:password@cluster0.xxxxx.mongodb.net/minecraft_configs?retryWrites=true&w=majority"
```

### Advanced URI Options

```java
// Production-ready URI with all options
String uri = "mongodb+srv://username:password@cluster.mongodb.net/minecraft_configs?" +
    "retryWrites=true&" +
    "w=majority&" +
    "readPreference=primary&" +
    "maxPoolSize=100&" +
    "minPoolSize=5&" +
    "maxIdleTimeMS=300000&" +
    "connectTimeoutMS=30000&" +
    "socketTimeoutMS=60000&" +
    "ssl=true&" +
    "authSource=admin";
```

---

## 📊 Collection Management

### Collection Naming Strategies

```java
// Strategy 1: Simple naming
@ConfigsCollection("server-settings")
public class ServerConfig { }

@ConfigsCollection("economy-config")
public class EconomyConfig { }

// Strategy 2: Environment-based naming
@ConfigsCollection("${environment}-server-settings")  // dev-server-settings, prod-server-settings
public class ServerConfig { }

// Strategy 3: Server-specific naming
@ConfigsCollection("server-${server.id}-settings")  // server-1-settings, server-2-settings
public class ServerConfig { }
```

### Dynamic Collection Names

```java
public class CollectionNamingStrategy {
    
    public static String getCollectionName(String baseSettings, String environment, String serverId) {
        return String.format("%s-%s-%s", environment, serverId, baseSettings);
        // Result: "prod-server1-economy-config"
    }
    
    // Example usage in configuration
    @ConfigsCollection("${naming.strategy.collection}")
    public class DynamicConfig {
        // This would resolve based on your naming configuration
    }
}
```

---

## 🔄 Change Streams & Real-time Sync

### How Change Streams Work

MongoDB Configs API uses Change Streams for real-time synchronization across multiple servers:

```java
// When you save a configuration on Server A:
ServerConfig config = cm.loadObject(ServerConfig.class);
config.setMaxPlayers(200);
cm.saveObject(config);  // ⚡ Triggers change stream

// Servers B, C, D automatically receive the update!
// Their cached configurations are invalidated and reloaded
```

### Change Stream Configuration

```java
@ConfigsDatabase(
    uri = "mongodb+srv://cluster.mongodb.net",
    database = "minecraft_configs",
    enableChangeStreams = true,      // Enable real-time sync
    changeStreamBatchSize = 100,     // Batch size for change events
    changeStreamMaxAwaitTime = 5000  // Max wait time in milliseconds
)
public class ChangeStreamConfig {
    
    // Additional change stream settings
    private boolean resumeAfterDisconnect = true;
    private int changeStreamHeartbeat = 30000;  // 30 seconds
    
    // Getters and setters...
}
```

### Manual Change Stream Handling

```java
public class ChangeStreamListener {
    
    private final ConfigManager cm;
    
    public ChangeStreamListener() {
        this.cm = MongoConfigsAPI.getConfigManager();
        
        // Register change stream listeners
        registerChangeListeners();
    }
    
    private void registerChangeListeners() {
        // Listen for ServerConfig changes
        cm.addChangeStreamListener(ServerConfig.class, this::onServerConfigChange);
        
        // Listen for EconomyConfig changes
        cm.addChangeStreamListener(EconomyConfig.class, this::onEconomyConfigChange);
    }
    
    private void onServerConfigChange(ChangeStreamEvent<ServerConfig> event) {
        ServerConfig newConfig = event.getFullDocument();
        String operation = event.getOperationType();
        
        getLogger().info("ServerConfig changed: " + operation);
        
        if (newConfig != null) {
            // React to configuration changes
            if (newConfig.getMaxPlayers() != getCurrentMaxPlayers()) {
                updateServerMaxPlayers(newConfig.getMaxPlayers());
            }
            
            if (!newConfig.getServerName().equals(getCurrentServerName())) {
                updateServerName(newConfig.getServerName());
            }
        }
    }
    
    private void onEconomyConfigChange(ChangeStreamEvent<EconomyConfig> event) {
        EconomyConfig newConfig = event.getFullDocument();
        
        if (newConfig != null) {
            // Update economy settings
            reloadEconomySettings(newConfig);
            
            // Notify online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage("§a💰 Economy settings updated!");
            }
        }
    }
    
    private void updateServerMaxPlayers(int newMaxPlayers) {
        // Implementation specific to your server
        getLogger().info("Max players updated to: " + newMaxPlayers);
    }
    
    private void updateServerName(String newServerName) {
        // Implementation specific to your server
        getLogger().info("Server name updated to: " + newServerName);
    }
    
    private void reloadEconomySettings(EconomyConfig config) {
        // Reload economy plugin settings
        getLogger().info("Economy settings reloaded");
    }
    
    // Placeholder methods - implement based on your server setup
    private int getCurrentMaxPlayers() { return 100; }
    private String getCurrentServerName() { return "Default Server"; }
}
```

---

## 🏗️ Database Optimization

### Connection Pool Optimization

```java
@ConfigsDatabase(
    uri = "mongodb+srv://cluster.mongodb.net/minecraft_configs",
    
    // Connection Pool Settings
    maxPoolSize = 50,                    // Max connections (high-traffic servers)
    minPoolSize = 10,                    // Always keep minimum connections
    maxIdleTime = 600000,               // 10 minutes idle timeout
    maxLifeTime = 3600000,              // 1 hour max connection lifetime
    
    // Timeout Settings
    connectionTimeout = 20000,           // 20 seconds to establish connection
    socketTimeout = 90000,               // 90 seconds socket timeout
    serverSelectionTimeout = 15000,      // 15 seconds server selection
    
    // Performance Settings
    retryWrites = true,                  // Retry failed writes
    retryReads = true,                   // Retry failed reads
    readPreference = "primaryPreferred", // Read from primary, fallback to secondary
    writeConcern = "majority"            // Ensure majority write acknowledgment
)
public class OptimizedDatabaseConfig { }
```

### Read Preferences for Multi-Server Setup

```java
// Primary Only (default) - Always read from primary
@ConfigsDatabase(readPreference = "primary")

// Primary Preferred - Read from primary, fallback to secondary
@ConfigsDatabase(readPreference = "primaryPreferred")

// Secondary Preferred - Read from secondary when possible
@ConfigsDatabase(readPreference = "secondaryPreferred")

// Nearest - Read from nearest member (lowest latency)
@ConfigsDatabase(readPreference = "nearest")
```

### Write Concerns for Data Safety

```java
// Majority - Wait for majority of replica set members
@ConfigsDatabase(writeConcern = "majority")

// Acknowledged - Wait for acknowledgment from primary
@ConfigsDatabase(writeConcern = "acknowledged")

// Unacknowledged - Fire and forget (fastest, least safe)
@ConfigsDatabase(writeConcern = "unacknowledged")

// Custom - Wait for specific number of members
@ConfigsDatabase(writeConcern = "w=2")  // Wait for 2 members
```

---

## 🌐 Multi-Server Architecture

### Shared Configuration Strategy

```java
// Strategy 1: Shared configurations for all servers
@ConfigsCollection("global-server-settings")
public class GlobalServerConfig {
    private String motd = "Welcome to our network!";
    private List<String> bannedItems = Arrays.asList("bedrock", "barrier");
    private Map<String, String> worldSettings = new HashMap<>();
    // All servers use these settings
}

// Strategy 2: Server-specific configurations
@ConfigsCollection("server-${server.id}-settings")
public class ServerSpecificConfig {
    private int maxPlayers = 100;
    private String serverName = "Server 1";
    private Location spawnLocation;
    // Each server has its own settings
}

// Strategy 3: Environment-based configurations
@ConfigsCollection("${environment}-economy-config")
public class EnvironmentConfig {
    private double startingBalance = 1000.0;
    private Map<String, Double> shopPrices = new HashMap<>();
    // Different settings for dev/staging/production
}
```

### Server Identification

```java
public class ServerIdentificationConfig {
    
    @ConfigsFileProperties(fileName = "server-id.yml", resource = true)
    private String serverId = "server-1";
    private String environment = "production";
    private String region = "us-east";
    private String cluster = "minecraft-cluster-1";
    
    public String getFullServerIdentifier() {
        return String.format("%s-%s-%s-%s", environment, region, cluster, serverId);
        // Result: "production-us-east-minecraft-cluster-1-server-1"
    }
    
    // Use in collection names
    public String getServerSpecificCollection(String baseName) {
        return String.format("%s-%s", getFullServerIdentifier(), baseName);
        // Result: "production-us-east-minecraft-cluster-1-server-1-player-data"
    }
}
```

### Cross-Server Communication

```java
public class CrossServerConfigManager {
    
    private final ConfigManager cm;
    
    public CrossServerConfigManager() {
        this.cm = MongoConfigsAPI.getConfigManager();
    }
    
    // Send configuration update to all servers
    public void broadcastConfigUpdate(Object config) {
        cm.saveObject(config);  // Change streams automatically notify other servers
        
        getLogger().info("Configuration broadcasted to all servers");
    }
    
    // Get server-specific configuration
    public <T> T getServerConfig(Class<T> configClass, String serverId) {
        // Temporarily override collection name for specific server
        return cm.loadObjectFromCollection(configClass, "server-" + serverId + "-config");
    }
    
    // Copy configuration from one server to another
    public <T> void copyConfigBetweenServers(Class<T> configClass, String fromServerId, String toServerId) {
        T sourceConfig = getServerConfig(configClass, fromServerId);
        
        if (sourceConfig != null) {
            // Save to target server's collection
            cm.saveObjectToCollection(sourceConfig, "server-" + toServerId + "-config");
            getLogger().info("Copied config from " + fromServerId + " to " + toServerId);
        }
    }
    
    // Synchronize specific setting across all servers
    public void synchronizeSetting(String settingKey, Object value) {
        // This would require a global settings collection
        cm.set("global." + settingKey, value);
        
        // Broadcast notification
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("mongoconfigs.admin.notify")) {
                player.sendMessage("§a🔄 Global setting updated: " + settingKey);
            }
        }
    }
}
```

---

## 🔍 Database Monitoring

### Connection Health Monitoring

```java
public class DatabaseHealthMonitor {
    
    private final ConfigManager cm;
    private final ScheduledExecutorService scheduler;
    
    public DatabaseHealthMonitor() {
        this.cm = MongoConfigsAPI.getConfigManager();
        this.scheduler = Executors.newScheduledThreadPool(1);
        startMonitoring();
    }
    
    private void startMonitoring() {
        // Check database health every 60 seconds
        scheduler.scheduleAtFixedRate(this::checkDatabaseHealth, 0, 60, TimeUnit.SECONDS);
    }
    
    private void checkDatabaseHealth() {
        try {
            // Perform a simple operation to test connectivity
            long startTime = System.currentTimeMillis();
            cm.get("health.check", String.class);
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (responseTime > 5000) {  // 5 seconds threshold
                getLogger().warning("Database response time high: " + responseTime + "ms");
            } else {
                getLogger().fine("Database health check passed: " + responseTime + "ms");
            }
            
        } catch (Exception e) {
            getLogger().severe("Database health check failed: " + e.getMessage());
            handleDatabaseConnectionIssue(e);
        }
    }
    
    private void handleDatabaseConnectionIssue(Exception error) {
        // Notify administrators
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("mongoconfigs.admin.alerts")) {
                player.sendMessage("§c🚨 Database connection issue detected!");
                player.sendMessage("§7Error: " + error.getMessage());
            }
        }
        
        // Log detailed error information
        getLogger().severe("Database Error Details:");
        getLogger().severe("Message: " + error.getMessage());
        getLogger().severe("Cause: " + (error.getCause() != null ? error.getCause().getMessage() : "Unknown"));
        
        // Could implement automatic failover or retry logic here
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}
```

### Performance Metrics

```java
public class DatabasePerformanceMonitor {
    
    private final Map<String, Long> operationTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> operationCounts = new ConcurrentHashMap<>();
    
    public void recordOperation(String operation, long executionTime) {
        operationTimes.put(operation, executionTime);
        operationCounts.merge(operation, 1, Integer::sum);
    }
    
    public void printPerformanceReport(CommandSender sender) {
        sender.sendMessage("§a📊 Database Performance Report:");
        sender.sendMessage("§e═══════════════════════════════");
        
        operationTimes.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String operation = entry.getKey();
                long avgTime = entry.getValue();
                int count = operationCounts.getOrDefault(operation, 0);
                
                String status = avgTime > 1000 ? "§c" : avgTime > 500 ? "§e" : "§a";
                sender.sendMessage(String.format("%s%s: §f%dms §7(%d ops)", 
                    status, operation, avgTime, count));
            });
        
        sender.sendMessage("§e═══════════════════════════════");
    }
    
    public void clearMetrics() {
        operationTimes.clear();
        operationCounts.clear();
    }
}
```

---

## 🚨 Error Handling & Recovery

### Connection Retry Strategy

```java
public class DatabaseConnectionRecovery {
    
    private final ConfigManager cm;
    private volatile boolean connectionHealthy = true;
    
    public <T> T safeOperation(Supplier<T> operation, String operationName) {
        int maxRetries = 3;
        int retryDelay = 1000; // 1 second
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                T result = operation.get();
                
                if (!connectionHealthy) {
                    getLogger().info("Database connection recovered on attempt " + attempt);
                    connectionHealthy = true;
                }
                
                return result;
                
            } catch (Exception e) {
                connectionHealthy = false;
                getLogger().warning("Database operation failed (attempt " + attempt + "/" + maxRetries + "): " + operationName);
                
                if (attempt == maxRetries) {
                    getLogger().severe("Database operation failed permanently: " + operationName);
                    throw new RuntimeException("Database operation failed after " + maxRetries + " attempts", e);
                }
                
                try {
                    Thread.sleep(retryDelay * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Operation interrupted", ie);
                }
            }
        }
        
        return null; // Should never reach here
    }
    
    public <T> T safeLoadConfig(Class<T> configClass) {
        return safeOperation(() -> cm.loadObject(configClass), "loadObject:" + configClass.getSimpleName());
    }
    
    public void safeSaveConfig(Object config) {
        safeOperation(() -> {
            cm.saveObject(config);
            return null;
        }, "saveObject:" + config.getClass().getSimpleName());
    }
    
    public boolean isConnectionHealthy() {
        return connectionHealthy;
    }
}
```

### Graceful Degradation

```java
public class GracefulDegradationHandler {
    
    private final Map<Class<?>, Object> fallbackConfigs = new ConcurrentHashMap<>();
    private final DatabaseConnectionRecovery recovery;
    
    public GracefulDegradationHandler() {
        this.recovery = new DatabaseConnectionRecovery();
        initializeFallbackConfigs();
    }
    
    private void initializeFallbackConfigs() {
        // Initialize with sensible defaults
        ServerConfig defaultServerConfig = new ServerConfig();
        defaultServerConfig.setMaxPlayers(100);
        defaultServerConfig.setServerName("Default Server");
        fallbackConfigs.put(ServerConfig.class, defaultServerConfig);
        
        EconomyConfig defaultEconomyConfig = new EconomyConfig();
        defaultEconomyConfig.setStartingBalance(1000.0);
        fallbackConfigs.put(EconomyConfig.class, defaultEconomyConfig);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getConfigWithFallback(Class<T> configClass) {
        try {
            // Try to load from database
            return recovery.safeLoadConfig(configClass);
            
        } catch (Exception e) {
            getLogger().warning("Using fallback configuration for " + configClass.getSimpleName());
            
            // Return fallback configuration
            T fallback = (T) fallbackConfigs.get(configClass);
            if (fallback == null) {
                try {
                    fallback = configClass.getDeclaredConstructor().newInstance();
                } catch (Exception constructorException) {
                    throw new RuntimeException("Cannot create fallback configuration", constructorException);
                }
            }
            
            return fallback;
        }
    }
    
    public void setFallbackConfig(Class<?> configClass, Object fallbackConfig) {
        fallbackConfigs.put(configClass, fallbackConfig);
    }
}
```

---

## 🎯 Best Practices

### 1. Database Security

```java
// ✅ Good - Use environment variables for credentials
@ConfigsDatabase(
    uri = "${MONGODB_URI}",  // mongodb+srv://username:password@cluster.mongodb.net
    database = "${MONGODB_DATABASE}"
)
public class SecureDatabaseConfig { }

// ✅ Good - Use connection string with auth parameters
"mongodb+srv://username:password@cluster.mongodb.net/database?authSource=admin&ssl=true"

// ❌ Avoid - Hardcoded credentials
"mongodb://myuser:mypassword@localhost:27017/database"
```

### 2. Collection Organization

```java
// ✅ Good - Logical collection naming
@ConfigsCollection("server-configuration")
@ConfigsCollection("player-data")
@ConfigsCollection("economy-settings")
@ConfigsCollection("gui-messages-en")
@ConfigsCollection("gui-messages-pl")

// ✅ Good - Environment-based separation
@ConfigsCollection("${environment}-player-data")  // dev-player-data, prod-player-data

// ❌ Avoid - Generic naming
@ConfigsCollection("data")
@ConfigsCollection("config")
@ConfigsCollection("stuff")
```

### 3. Performance Optimization

```java
// ✅ Good - Appropriate connection pool sizing
maxPoolSize = 50,      // Scale based on server load
minPoolSize = 10,      // Keep minimum connections ready

// ✅ Good - Reasonable timeouts
connectionTimeout = 20000,  // 20 seconds
socketTimeout = 60000,      // 60 seconds

// ✅ Good - Use read preferences appropriately
readPreference = "primaryPreferred"  // Balance consistency and performance
```

---

*Next: Learn about [[Example Usage]] patterns and common implementation scenarios.*