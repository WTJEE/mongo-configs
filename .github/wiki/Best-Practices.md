# Best Practices

Comprehensive best practices guide for optimizing your MongoDB Configs API implementation, including performance optimization, code organization, and maintenance strategies.

## 📋 Best Practices Overview

The Best Practices guide provides essential recommendations for building robust, maintainable, and high-performance applications with the MongoDB Configs API.

## 🏗️ Architecture Best Practices

### Service Layer Architecture

#### Clean Service Separation

```java
public class ConfigServiceManager {
    
    private final ConfigManager configManager;
    private final TranslationService translationService;
    private final CacheManager cacheManager;
    private final ValidationService validationService;
    
    public ConfigServiceManager(MongoConfigsPlugin plugin) {
        this.configManager = plugin.getConfigManager();
        this.translationService = plugin.getTranslationService();
        this.cacheManager = plugin.getCacheManager();
        this.validationService = new ValidationService();
    }
    
    // Business logic methods
    public <T> T getValidatedConfig(Class<T> configClass, String configKey, Player player) {
        // Validate permissions
        if (!hasPermission(player, "read", configKey)) {
            throw new SecurityException("Access denied");
        }
        
        // Get config with validation
        T config = configManager.get(configClass, configKey);
        
        // Post-process config
        return validationService.validateAndSanitize(config);
    }
    
    public void saveValidatedConfig(Object config, String configKey, Player player) {
        // Pre-validate config
        validationService.validateConfig(config);
        
        // Check permissions
        if (!hasPermission(player, "write", configKey)) {
            throw new SecurityException("Access denied");
        }
        
        // Save with audit trail
        configManager.save(config);
        auditService.logConfigChange(player, configKey, "UPDATE");
    }
    
    private boolean hasPermission(Player player, String action, String configKey) {
        // Centralized permission checking
        return permissionService.hasPermission(player, "mongoconfigs." + action, configKey);
    }
}
```

#### Repository Pattern Implementation

```java
public interface ConfigRepository<T> {
    T findById(String id);
    List<T> findAll();
    T save(T entity);
    void delete(String id);
    boolean exists(String id);
    long count();
}

public class PlayerConfigRepository implements ConfigRepository<PlayerConfig> {
    
    private final ConfigManager configManager;
    private final Class<PlayerConfig> configClass = PlayerConfig.class;
    
    public PlayerConfigRepository(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    @Override
    public PlayerConfig findById(String id) {
        return configManager.get(configClass, "player_" + id);
    }
    
    @Override
    public List<PlayerConfig> findAll() {
        // This would require a more complex query implementation
        // For now, return empty list as example
        return new ArrayList<>();
    }
    
    @Override
    public PlayerConfig save(PlayerConfig entity) {
        configManager.save(entity);
        return entity;
    }
    
    @Override
    public void delete(String id) {
        configManager.delete("player_" + id);
    }
    
    @Override
    public boolean exists(String id) {
        return configManager.exists("player_" + id);
    }
    
    @Override
    public long count() {
        // This would require database-level counting
        return 0;
    }
    
    // Additional business methods
    public PlayerConfig findByPlayer(Player player) {
        return findById(player.getUniqueId().toString());
    }
    
    public PlayerConfig createDefault(Player player) {
        PlayerConfig config = new PlayerConfig();
        config.playerId = player.getUniqueId();
        config.playerName = player.getName();
        config.language = "en";
        config.createdAt = System.currentTimeMillis();
        config.lastLogin = System.currentTimeMillis();
        
        return save(config);
    }
}
```

### Configuration Class Design

#### Well-Structured Configuration Classes

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "mongoconfigs")
@ConfigsCollection(collection = "player_configs")
public class PlayerConfig {
    
    // Primary identifiers
    public UUID playerId;
    public String playerName;
    
    // Basic settings
    public String language = "en";
    public boolean notifications = true;
    public boolean soundEffects = true;
    
    // Game preferences
    public GamePreferences gamePrefs = new GamePreferences();
    
    // Statistics
    public PlayerStats stats = new PlayerStats();
    
    // Metadata
    public long createdAt;
    public long lastLogin;
    public long lastModified;
    
    // Nested configuration classes
    public static class GamePreferences {
        public Difficulty difficulty = Difficulty.NORMAL;
        public boolean autoSave = true;
        public int renderDistance = 10;
        public List<String> favoriteItems = new ArrayList<>();
    }
    
    public static class PlayerStats {
        public int gamesPlayed = 0;
        public int gamesWon = 0;
        public long totalPlayTime = 0;
        public Map<String, Integer> achievements = new HashMap<>();
    }
    
    public enum Difficulty {
        EASY, NORMAL, HARD, EXPERT
    }
    
    // Business logic methods
    public void updateLastLogin() {
        this.lastLogin = System.currentTimeMillis();
        this.lastModified = this.lastLogin;
    }
    
    public void incrementGamesPlayed() {
        this.stats.gamesPlayed++;
        this.lastModified = System.currentTimeMillis();
    }
    
    public void addAchievement(String achievement) {
        this.stats.achievements.put(achievement, 
            this.stats.achievements.getOrDefault(achievement, 0) + 1);
        this.lastModified = System.currentTimeMillis();
    }
    
    // Validation methods
    public boolean isValid() {
        return playerId != null && 
               playerName != null && 
               !playerName.trim().isEmpty() &&
               createdAt > 0;
    }
}
```

#### Configuration Inheritance

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "mongoconfigs")
@ConfigsCollection(collection = "server_configs")
public abstract class BaseServerConfig {
    
    // Common fields for all server configs
    public String serverId;
    public String serverName;
    public String serverType;
    public boolean enabled = true;
    
    // Metadata
    public long createdAt;
    public long lastModified;
    public String modifiedBy;
    
    // Common methods
    public void updateModified(String modifier) {
        this.lastModified = System.currentTimeMillis();
        this.modifiedBy = modifier;
    }
    
    public boolean isValid() {
        return serverId != null && 
               serverName != null && 
               !serverName.trim().isEmpty();
    }
    
    public abstract void validateSpecific();
}

@ConfigsFileProperties
@ConfigsDatabase(database = "mongoconfigs")
@ConfigsCollection(collection = "server_configs")
public class GameServerConfig extends BaseServerConfig {
    
    // Game-specific configuration
    public String gameMode;
    public int maxPlayers = 100;
    public int minPlayers = 1;
    public boolean pvpEnabled = true;
    public List<String> allowedCommands = new ArrayList<>();
    
    // Game settings
    public GameSettings gameSettings = new GameSettings();
    
    @Override
    public void validateSpecific() {
        if (maxPlayers < minPlayers) {
            throw new IllegalStateException("Max players cannot be less than min players");
        }
        
        if (gameMode == null || gameMode.trim().isEmpty()) {
            throw new IllegalStateException("Game mode cannot be empty");
        }
        
        gameSettings.validate();
    }
    
    public static class GameSettings {
        public Difficulty difficulty = Difficulty.NORMAL;
        public boolean friendlyFire = false;
        public int timeLimit = 30; // minutes
        public List<String> disabledItems = new ArrayList<>();
        
        public void validate() {
            if (timeLimit < 0) {
                throw new IllegalStateException("Time limit cannot be negative");
            }
        }
    }
}

@ConfigsFileProperties
@ConfigsDatabase(database = "mongoconfigs")
@ConfigsCollection(collection = "server_configs")
public class LobbyServerConfig extends BaseServerConfig {
    
    // Lobby-specific configuration
    public int maxQueueSize = 1000;
    public boolean autoBalance = true;
    public List<String> welcomeMessages = new ArrayList<>();
    public Map<String, String> serverLinks = new HashMap<>();
    
    @Override
    public void validateSpecific() {
        if (maxQueueSize < 0) {
            throw new IllegalStateException("Max queue size cannot be negative");
        }
        
        if (welcomeMessages.isEmpty()) {
            welcomeMessages.add("Welcome to the lobby!");
        }
    }
}
```

## 🚀 Performance Best Practices

### Connection Pool Optimization

#### Advanced Connection Pool Configuration

```java
public class OptimizedMongoConfigManager {
    
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final CodecRegistry codecRegistry;
    
    public OptimizedMongoConfigManager(String connectionString, String databaseName) {
        // Configure connection pool
        ConnectionPoolSettings poolSettings = ConnectionPoolSettings.builder()
            .maxSize(50)                    // Maximum connections
            .minSize(10)                    // Minimum connections
            .maxWaitTime(10000, TimeUnit.MILLISECONDS)  // Max wait time
            .maxConnectionIdleTime(60, TimeUnit.SECONDS)  // Max idle time
            .maxConnectionLifeTime(300, TimeUnit.SECONDS) // Max life time
            .build();
        
        // Configure server settings
        ServerSettings serverSettings = ServerSettings.builder()
            .heartbeatFrequency(10000, TimeUnit.MILLISECONDS)  // Heartbeat every 10s
            .minHeartbeatFrequency(500, TimeUnit.MILLISECONDS)  // Min heartbeat
            .build();
        
        // Configure cluster settings
        ClusterSettings clusterSettings = ClusterSettings.builder()
            .serverSelectionTimeout(30000, TimeUnit.MILLISECONDS)  // 30s timeout
            .build();
        
        // Configure socket settings
        SocketSettings socketSettings = SocketSettings.builder()
            .connectTimeout(10000, TimeUnit.MILLISECONDS)     // 10s connect timeout
            .readTimeout(30000, TimeUnit.MILLISECONDS)        // 30s read timeout
            .build();
        
        // Create MongoDB client with optimized settings
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(connectionString))
            .applyToConnectionPoolSettings(builder -> builder.applySettings(poolSettings))
            .applyToServerSettings(builder -> builder.applySettings(serverSettings))
            .applyToClusterSettings(builder -> builder.applySettings(clusterSettings))
            .applyToSocketSettings(builder -> builder.applySettings(socketSettings))
            .codecRegistry(getCustomCodecRegistry())
            .build();
        
        this.mongoClient = MongoClients.create(settings);
        this.database = mongoClient.getDatabase(databaseName);
        this.codecRegistry = settings.getCodecRegistry();
    }
    
    private CodecRegistry getCustomCodecRegistry() {
        return CodecRegistries.fromRegistries(
            CodecRegistries.fromCodecs(
                new PlayerConfigCodec(),
                new GameServerConfigCodec(),
                new LobbyServerConfigCodec()
            ),
            MongoClientSettings.getDefaultCodecRegistry()
        );
    }
    
    // Optimized save with retry logic
    public <T> void saveWithRetry(T config, String configKey, int maxRetries) {
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                save(config, configKey);
                return;
            } catch (MongoException e) {
                attempts++;
                if (attempts >= maxRetries) {
                    throw new RuntimeException("Failed to save after " + maxRetries + " attempts", e);
                }
                
                // Exponential backoff
                try {
                    Thread.sleep((long) (Math.pow(2, attempts) * 100));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
    }
    
    // Batch operations for better performance
    public void saveBatch(List<ConfigEntry<?>> configs) {
        List<WriteModel<Document>> writes = new ArrayList<>();
        
        for (ConfigEntry<?> entry : configs) {
            Document doc = Document.parse(new ObjectMapper().writeValueAsString(entry.config));
            doc.put("_id", entry.configKey);
            
            writes.add(new ReplaceOneModel<>(
                Filters.eq("_id", entry.configKey),
                doc,
                new ReplaceOptions().upsert(true)
            ));
        }
        
        try {
            BulkWriteResult result = database.getCollection("configs")
                .bulkWrite(writes, new BulkWriteOptions().ordered(false));
            
            MongoConfigsPlugin.getInstance().getLogger().info(
                "Batch save completed: " + result.getModifiedCount() + " modified, " + 
                result.getInsertedCount() + " inserted");
                
        } catch (Exception e) {
            throw new RuntimeException("Batch save failed", e);
        }
    }
    
    public static class ConfigEntry<T> {
        public final T config;
        public final String configKey;
        
        public ConfigEntry(T config, String configKey) {
            this.config = config;
            this.configKey = configKey;
        }
    }
}
```

### Caching Strategies

#### Multi-Level Caching Implementation

```java
public class AdvancedCacheManager {
    
    private final Cache<String, Object> l1Cache;  // Caffeine L1 cache
    private final ConfigManager l2Cache;          // MongoDB L2 cache
    private final Cache<String, Long> accessTimes; // Track access patterns
    
    public AdvancedCacheManager(ConfigManager configManager) {
        // L1 Cache - Fast in-memory cache
        this.l1Cache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .removalListener((String key, Object value, RemovalCause cause) -> {
                if (cause == RemovalCause.EXPIRED) {
                    // Write back to L2 if modified
                    writeBackToL2(key, value);
                }
            })
            .build();
        
        this.l2Cache = configManager;
        
        // Track access times for cache optimization
        this.accessTimes = Caffeine.newBuilder()
            .maximumSize(50000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    }
    
    public <T> T get(Class<T> configClass, String configKey) {
        // Record access time
        accessTimes.put(configKey, System.currentTimeMillis());
        
        // Try L1 cache first
        Object cached = l1Cache.getIfPresent(configKey);
        if (cached != null) {
            return configClass.cast(cached);
        }
        
        // Try L2 cache
        T config = l2Cache.get(configClass, configKey);
        if (config != null) {
            // Populate L1 cache
            l1Cache.put(configKey, config);
        }
        
        return config;
    }
    
    public void put(String configKey, Object config) {
        // Update both caches
        l1Cache.put(configKey, config);
        l2Cache.save(config);
        
        // Record access time
        accessTimes.put(configKey, System.currentTimeMillis());
    }
    
    public void invalidate(String configKey) {
        l1Cache.invalidate(configKey);
        l2Cache.delete(configKey);
        accessTimes.invalidate(configKey);
    }
    
    public void invalidateAll() {
        l1Cache.invalidateAll();
        accessTimes.invalidateAll();
        // Note: L2 cache invalidation would require more complex logic
    }
    
    private void writeBackToL2(String key, Object value) {
        try {
            l2Cache.save(value);
        } catch (Exception e) {
            MongoConfigsPlugin.getInstance().getLogger().warning(
                "Failed to write back to L2 cache: " + key);
        }
    }
    
    // Cache statistics and monitoring
    public CacheStats getCacheStats() {
        return new CacheStats(
            l1Cache.stats().hitCount(),
            l1Cache.stats().missCount(),
            l1Cache.stats().evictionCount(),
            accessTimes.estimatedSize()
        );
    }
    
    // Adaptive cache sizing based on access patterns
    public void optimizeCacheSize() {
        long totalAccesses = accessTimes.estimatedSize();
        if (totalAccesses > 1000) {
            // Analyze access patterns
            Map<String, Long> accessPattern = new HashMap<>();
            accessTimes.asMap().forEach((key, time) -> {
                String pattern = getAccessPattern(key);
                accessPattern.put(pattern, accessPattern.getOrDefault(pattern, 0L) + 1);
            });
            
            // Adjust cache size based on patterns
            long hotDataSize = accessPattern.getOrDefault("HOT", 0L);
            long warmDataSize = accessPattern.getOrDefault("WARM", 0L);
            
            // Dynamically adjust cache sizes
            adjustCacheSizes(hotDataSize, warmDataSize);
        }
    }
    
    private String getAccessPattern(String key) {
        Long lastAccess = accessTimes.getIfPresent(key);
        if (lastAccess == null) return "COLD";
        
        long timeSinceAccess = System.currentTimeMillis() - lastAccess;
        
        if (timeSinceAccess < 5 * 60 * 1000) return "HOT";      // < 5 minutes
        if (timeSinceAccess < 30 * 60 * 1000) return "WARM";    // < 30 minutes
        return "COLD";
    }
    
    private void adjustCacheSizes(long hotDataSize, long warmDataSize) {
        // Implement dynamic cache sizing logic
        long newMaxSize = Math.max(1000, hotDataSize + (warmDataSize / 2));
        
        // Rebuild cache with new size
        Cache<String, Object> newCache = Caffeine.newBuilder()
            .maximumSize(newMaxSize)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
        
        // Migrate data
        l1Cache.asMap().forEach(newCache::put);
        this.l1Cache = newCache;
    }
    
    public static class CacheStats {
        public final long hits;
        public final long misses;
        public final long evictions;
        public final long trackedKeys;
        
        public CacheStats(long hits, long misses, long evictions, long trackedKeys) {
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
            this.trackedKeys = trackedKeys;
        }
        
        public double getHitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }
    }
}
```

## 🔧 Code Organization Best Practices

### Package Structure

#### Recommended Package Organization

```
xyz.wtje.mongoconfigs
├── api/                    # Public API interfaces
│   ├── ConfigManager.java
│   ├── Messages.java
│   └── LanguageManager.java
├── core/                   # Core implementation
│   ├── impl/
│   │   ├── ConfigManagerImpl.java
│   │   └── LanguageManagerImpl.java
│   ├── cache/
│   │   ├── CacheManager.java
│   │   └── MultiLevelCache.java
│   ├── codec/
│   │   └── JacksonCodec.java
│   └── async/
│       └── AsyncConfigManager.java
├── gui/                    # GUI components
│   ├── BaseGUI.java
│   ├── PaginatedGUI.java
│   ├── RealtimeGUI.java
│   └── components/
│       ├── ConfirmationGUI.java
│       ├── InputGUI.java
│       └── SearchableGUI.java
├── messages/               # Message handling
│   ├── MessageTranslationService.java
│   ├── TranslatedMessage.java
│   └── formatters/
│       ├── PluralFormatter.java
│       └── DateTimeFormatter.java
├── security/               # Security components
│   ├── PermissionManager.java
│   ├── DataSanitizer.java
│   └── audit/
│       ├── SecurityAuditLogger.java
│       └── IntrusionDetector.java
├── commands/               # Command handlers
│   ├── ConfigCommand.java
│   ├── LanguageCommand.java
│   └── admin/
│       ├── AdminCommand.java
│       └── DebugCommand.java
├── services/               # Service layer
│   ├── ConfigService.java
│   ├── TranslationService.java
│   └── ValidationService.java
├── models/                 # Data models
│   ├── config/
│   │   ├── PlayerConfig.java
│   │   └── ServerConfig.java
│   └── dto/
│       ├── ConfigDTO.java
│       └── TranslationDTO.java
├── utils/                  # Utility classes
│   ├── ColorHelper.java
│   ├── TimeUtils.java
│   └── ValidationUtils.java
├── exceptions/             # Custom exceptions
│   ├── ConfigException.java
│   ├── ValidationException.java
│   └── SecurityException.java
└── MongoConfigsPlugin.java # Main plugin class
```

### Error Handling Patterns

#### Comprehensive Error Handling

```java
public class ErrorHandlingManager {
    
    private final Logger logger;
    private final Map<Class<? extends Exception>, ErrorHandler> errorHandlers;
    
    public ErrorHandlingManager(Logger logger) {
        this.logger = logger;
        this.errorHandlers = new HashMap<>();
        
        // Register default error handlers
        registerErrorHandler(MongoException.class, new MongoErrorHandler());
        registerErrorHandler(ValidationException.class, new ValidationErrorHandler());
        registerErrorHandler(SecurityException.class, new SecurityErrorHandler());
        registerErrorHandler(IOException.class, new IOExceptionHandler());
    }
    
    public void registerErrorHandler(Class<? extends Exception> exceptionClass, ErrorHandler handler) {
        errorHandlers.put(exceptionClass, handler);
    }
    
    public <T> T executeWithErrorHandling(Callable<T> operation, String operationName) {
        try {
            return operation.call();
        } catch (Exception e) {
            return handleException(e, operationName);
        }
    }
    
    public void executeWithErrorHandling(Runnable operation, String operationName) {
        try {
            operation.run();
        } catch (Exception e) {
            handleException(e, operationName);
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> T handleException(Exception exception, String operationName) {
        // Log the error
        logger.severe("Error in operation '" + operationName + "': " + exception.getMessage());
        
        // Find appropriate error handler
        ErrorHandler handler = findErrorHandler(exception.getClass());
        
        if (handler != null) {
            try {
                return (T) handler.handle(exception, operationName);
            } catch (Exception handlerException) {
                logger.severe("Error handler failed: " + handlerException.getMessage());
            }
        }
        
        // Default error handling
        return handleDefaultError(exception, operationName);
    }
    
    private ErrorHandler findErrorHandler(Class<? extends Exception> exceptionClass) {
        // Check for exact match first
        ErrorHandler handler = errorHandlers.get(exceptionClass);
        if (handler != null) {
            return handler;
        }
        
        // Check for superclass match
        for (Map.Entry<Class<? extends Exception>, ErrorHandler> entry : errorHandlers.entrySet()) {
            if (entry.getKey().isAssignableFrom(exceptionClass)) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private <T> T handleDefaultError(Exception exception, String operationName) {
        // Log stack trace for debugging
        logger.severe("Unhandled error in '" + operationName + "': " + exception.getMessage());
        exception.printStackTrace();
        
        // Return null for operations that expect a return value
        return null;
    }
    
    // Error handler interface
    public interface ErrorHandler {
        Object handle(Exception exception, String operationName) throws Exception;
    }
    
    // Specific error handlers
    private class MongoErrorHandler implements ErrorHandler {
        @Override
        public Object handle(Exception exception, String operationName) throws Exception {
            MongoException mongoException = (MongoException) exception;
            
            switch (mongoException.getCode()) {
                case 11000: // Duplicate key error
                    logger.warning("Duplicate key error in '" + operationName + "'");
                    throw new ValidationException("Configuration already exists");
                    
                case 121: // Document validation error
                    logger.warning("Document validation error in '" + operationName + "'");
                    throw new ValidationException("Configuration validation failed");
                    
                default:
                    logger.severe("MongoDB error in '" + operationName + "': " + mongoException.getMessage());
                    throw new ConfigException("Database operation failed", mongoException);
            }
        }
    }
    
    private class ValidationErrorHandler implements ErrorHandler {
        @Override
        public Object handle(Exception exception, String operationName) throws Exception {
            ValidationException validationException = (ValidationException) exception;
            logger.warning("Validation error in '" + operationName + "': " + validationException.getMessage());
            
            // Return null to indicate validation failure
            return null;
        }
    }
    
    private class SecurityErrorHandler implements ErrorHandler {
        @Override
        public Object handle(Exception exception, String operationName) throws Exception {
            SecurityException securityException = (SecurityException) exception;
            logger.warning("Security error in '" + operationName + "': " + securityException.getMessage());
            
            // Log security incident
            SecurityAuditLogger.logSecurityViolation("system", "OPERATION_ACCESS_DENIED", 
                "Access denied for operation: " + operationName);
            
            throw securityException; // Re-throw security exceptions
        }
    }
    
    private class IOExceptionHandler implements ErrorHandler {
        @Override
        public Object handle(Exception exception, String operationName) throws Exception {
            IOException ioException = (IOException) exception;
            logger.severe("IO error in '" + operationName + "': " + ioException.getMessage());
            
            throw new ConfigException("IO operation failed", ioException);
        }
    }
}
```

## 📊 Monitoring and Maintenance

### Health Checks

#### Comprehensive Health Monitoring

```java
public class HealthCheckManager {
    
    private final MongoConfigsPlugin plugin;
    private final List<HealthCheck> healthChecks;
    private final ScheduledExecutorService scheduler;
    
    public HealthCheckManager(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.healthChecks = new ArrayList<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Register default health checks
        registerHealthCheck(new MongoDBHealthCheck());
        registerHealthCheck(new CacheHealthCheck());
        registerHealthCheck(new MemoryHealthCheck());
        registerHealthCheck(new TranslationHealthCheck());
        
        // Schedule regular health checks
        scheduler.scheduleAtFixedRate(this::performHealthChecks, 1, 5, TimeUnit.MINUTES);
    }
    
    public void registerHealthCheck(HealthCheck healthCheck) {
        healthChecks.add(healthCheck);
    }
    
    public HealthStatus getOverallHealth() {
        List<HealthStatus> statuses = healthChecks.stream()
            .map(HealthCheck::check)
            .collect(Collectors.toList());
        
        // Determine overall status
        boolean allHealthy = statuses.stream().allMatch(HealthStatus::isHealthy);
        boolean hasWarnings = statuses.stream().anyMatch(status -> status.getSeverity() == Severity.WARNING);
        
        Severity overallSeverity = allHealthy ? Severity.INFO : 
                                 hasWarnings ? Severity.WARNING : Severity.ERROR;
        
        String overallMessage = allHealthy ? "All systems healthy" : 
                              hasWarnings ? "Some systems have warnings" : "Critical system issues detected";
        
        return new HealthStatus(overallSeverity, overallMessage, statuses);
    }
    
    private void performHealthChecks() {
        HealthStatus overallHealth = getOverallHealth();
        
        // Log health status
        plugin.getLogger().info("Health check completed: " + overallHealth.getMessage());
        
        // Alert on critical issues
        if (overallHealth.getSeverity() == Severity.ERROR) {
            plugin.getLogger().severe("Critical health issues detected!");
            // Send alerts to administrators
            alertAdministrators(overallHealth);
        } else if (overallHealth.getSeverity() == Severity.WARNING) {
            plugin.getLogger().warning("Health warnings detected");
        }
        
        // Store health metrics
        storeHealthMetrics(overallHealth);
    }
    
    private void alertAdministrators(HealthStatus healthStatus) {
        // Implementation would send alerts via Discord, email, etc.
        plugin.getLogger().severe("ADMIN ALERT: " + healthStatus.getMessage());
        
        for (HealthStatus componentStatus : healthStatus.getComponents()) {
            if (!componentStatus.isHealthy()) {
                plugin.getLogger().severe("  - " + componentStatus.getMessage());
            }
        }
    }
    
    private void storeHealthMetrics(HealthStatus healthStatus) {
        // Store health metrics for monitoring dashboards
        HealthMetrics metrics = new HealthMetrics();
        metrics.timestamp = System.currentTimeMillis();
        metrics.overallStatus = healthStatus.getSeverity().name();
        metrics.componentStatuses = healthStatus.getComponents().stream()
            .collect(Collectors.toMap(
                HealthStatus::getComponentName,
                status -> status.getSeverity().name()
            ));
        
        // Save metrics (would typically go to a time-series database)
        plugin.getConfigManager().save(metrics);
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Health check interface
    public interface HealthCheck {
        HealthStatus check();
    }
    
    // MongoDB health check
    private class MongoDBHealthCheck implements HealthCheck {
        @Override
        public HealthStatus check() {
            try {
                long startTime = System.currentTimeMillis();
                plugin.getConfigManager().testConnection();
                long responseTime = System.currentTimeMillis() - startTime;
                
                if (responseTime > 5000) { // 5 second threshold
                    return new HealthStatus(Severity.WARNING, 
                        "MongoDB response time is slow: " + responseTime + "ms", "MongoDB");
                }
                
                return new HealthStatus(Severity.INFO, "MongoDB is healthy", "MongoDB");
                
            } catch (Exception e) {
                return new HealthStatus(Severity.ERROR, 
                    "MongoDB connection failed: " + e.getMessage(), "MongoDB");
            }
        }
    }
    
    // Cache health check
    private class CacheHealthCheck implements HealthCheck {
        @Override
        public HealthStatus check() {
            try {
                MultiLevelCache.CacheStats stats = plugin.getCache().getStats();
                double hitRate = stats.getHitRate();
                
                if (hitRate < 0.5) { // Less than 50% hit rate
                    return new HealthStatus(Severity.WARNING, 
                        "Cache hit rate is low: " + String.format("%.1f%%", hitRate * 100), "Cache");
                }
                
                return new HealthStatus(Severity.INFO, 
                    "Cache is healthy (hit rate: " + String.format("%.1f%%", hitRate * 100) + ")", "Cache");
                    
            } catch (Exception e) {
                return new HealthStatus(Severity.ERROR, 
                    "Cache health check failed: " + e.getMessage(), "Cache");
            }
        }
    }
    
    // Memory health check
    private class MemoryHealthCheck implements HealthCheck {
        @Override
        public HealthStatus check() {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryUsage = (double) usedMemory / maxMemory;
            
            if (memoryUsage > 0.9) { // Over 90% memory usage
                return new HealthStatus(Severity.ERROR, 
                    "Memory usage is critical: " + String.format("%.1f%%", memoryUsage * 100), "Memory");
            } else if (memoryUsage > 0.8) { // Over 80% memory usage
                return new HealthStatus(Severity.WARNING, 
                    "Memory usage is high: " + String.format("%.1f%%", memoryUsage * 100), "Memory");
            }
            
            return new HealthStatus(Severity.INFO, 
                "Memory usage is normal: " + String.format("%.1f%%", memoryUsage * 100), "Memory");
        }
    }
    
    // Translation health check
    private class TranslationHealthCheck implements HealthCheck {
        @Override
        public HealthStatus check() {
            try {
                // Test basic translation
                String testTranslation = plugin.getTranslationService().translate("en", "welcome");
                
                if (testTranslation == null) {
                    return new HealthStatus(Severity.WARNING, 
                        "Translation service returned null for basic key", "Translation");
                }
                
                return new HealthStatus(Severity.INFO, "Translation service is healthy", "Translation");
                
            } catch (Exception e) {
                return new HealthStatus(Severity.ERROR, 
                    "Translation service failed: " + e.getMessage(), "Translation");
            }
        }
    }
    
    // Health status classes
    public static class HealthStatus {
        private final Severity severity;
        private final String message;
        private final String componentName;
        private final List<HealthStatus> components;
        
        public HealthStatus(Severity severity, String message, String componentName) {
            this.severity = severity;
            this.message = message;
            this.componentName = componentName;
            this.components = null;
        }
        
        public HealthStatus(Severity severity, String message, List<HealthStatus> components) {
            this.severity = severity;
            this.message = message;
            this.componentName = "Overall";
            this.components = components;
        }
        
        public boolean isHealthy() {
            return severity != Severity.ERROR;
        }
        
        public Severity getSeverity() { return severity; }
        public String getMessage() { return message; }
        public String getComponentName() { return componentName; }
        public List<HealthStatus> getComponents() { return components; }
    }
    
    public enum Severity {
        INFO, WARNING, ERROR
    }
    
    @ConfigsFileProperties
    @ConfigsDatabase(database = "mongoconfigs")
    @ConfigsCollection(collection = "health_metrics")
    public static class HealthMetrics {
        public long timestamp;
        public String overallStatus;
        public Map<String, String> componentStatuses;
    }
}
```

---

*Next: Learn about [[Migration Guide]] for upgrading your existing configuration systems.*