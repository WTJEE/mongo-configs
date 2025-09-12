# Plugin Integration

Complete guide to integrating MongoDB Configs API with Minecraft plugins, including setup, configuration, and best practices.

## 🔌 Integration Overview

This tutorial covers comprehensive integration of MongoDB Configs API with Minecraft plugins, including Paper/Spigot compatibility, dependency management, and production deployment.

## 📋 Core Implementation

### Plugin Base Class

```java
public class MongoConfigsPlugin extends JavaPlugin {
    
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private MessageTranslationService translationService;
    private PlayerLanguageStorage languageStorage;
    private TranslationManager translationManager;
    private LanguageCommandManager commandManager;
    
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(4);
    private final Map<String, Service> services = new ConcurrentHashMap<>();
    
    @Override
    public void onEnable() {
        // Initialize configuration
        saveDefaultConfig();
        
        try {
            // Initialize MongoDB Configs API
            initializeMongoDB();
            
            // Initialize language system
            initializeLanguageSystem();
            
            // Initialize commands
            initializeCommands();
            
            // Register event listeners
            registerEventListeners();
            
            // Load default data
            loadDefaultData();
            
            // Start background services
            startBackgroundServices();
            
            getLogger().info("MongoDB Configs Plugin enabled successfully!");
            
        } catch (Exception e) {
            getLogger().severe("Failed to enable MongoDB Configs Plugin: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    private void initializeMongoDB() {
        String mongoUri = getConfig().getString("mongodb.uri", "mongodb://localhost:27017");
        String database = getConfig().getString("mongodb.database", "minecraft_configs");
        
        // Configure connection settings
        ConnectionConfig connectionConfig = new ConnectionConfig();
        connectionConfig.setMinPoolSize(getConfig().getInt("mongodb.min-pool-size", 5));
        connectionConfig.setMaxPoolSize(getConfig().getInt("mongodb.max-pool-size", 50));
        connectionConfig.setMaxIdleTimeMS(getConfig().getInt("mongodb.max-idle-time", 30000));
        
        // Create config manager
        configManager = MongoConfigsAPI.createConfigManager(mongoUri, database);
        
        getLogger().info("Connected to MongoDB successfully");
    }
    
    private void initializeLanguageSystem() {
        // Initialize language manager
        languageManager = new LanguageManager(this);
        
        // Initialize translation service
        translationService = new MessageTranslationService(configManager);
        
        // Initialize player language storage
        languageStorage = new PlayerLanguageStorage(configManager);
        
        // Initialize translation manager
        translationManager = new TranslationManager(configManager, translationService);
        
        getLogger().info("Language system initialized");
    }
    
    private void initializeCommands() {
        commandManager = new LanguageCommandManager(this);
        commandManager.registerCommands();
        
        getLogger().info("Commands registered");
    }
    
    private void registerEventListeners() {
        getServer().getPluginManager().registerEvents(new PlayerLanguageListener(this), this);
        getServer().getPluginManager().registerEvents(new PluginReloadListener(this), this);
        
        getLogger().info("Event listeners registered");
    }
    
    private void loadDefaultData() {
        // Load default translations
        loadDefaultTranslations();
        
        // Load default configurations
        loadDefaultConfigurations();
        
        getLogger().info("Default data loaded");
    }
    
    private void loadDefaultTranslations() {
        // English translations
        translationManager.addMessage("welcome", "en", "Welcome to the server, {0}!", "general");
        translationManager.addMessage("goodbye", "en", "Goodbye, {0}!", "general");
        translationManager.addMessage("error.no_permission", "en", "You don't have permission to do that!", "error");
        translationManager.addMessage("success.saved", "en", "Configuration saved successfully!", "success");
        
        // Polish translations
        translationManager.addMessage("welcome", "pl", "Witaj na serwerze, {0}!", "general");
        translationManager.addMessage("goodbye", "pl", "Do widzenia, {0}!", "general");
        translationManager.addMessage("error.no_permission", "pl", "Nie masz uprawnień do tego!", "error");
        translationManager.addMessage("success.saved", "pl", "Konfiguracja została zapisana!", "success");
    }
    
    private void loadDefaultConfigurations() {
        // Load or create default language config
        LanguageConfig langConfig = configManager.get(LanguageConfig.class, "global_config");
        if (langConfig == null) {
            langConfig = new LanguageConfig();
            configManager.save(langConfig);
        }
    }
    
    private void startBackgroundServices() {
        // Start cache cleanup service
        asyncExecutor.submit(this::cacheCleanupTask);
        
        // Start metrics collection service
        asyncExecutor.submit(this::metricsCollectionTask);
        
        getLogger().info("Background services started");
    }
    
    private void cacheCleanupTask() {
        while (!asyncExecutor.isShutdown()) {
            try {
                // Cleanup expired cache entries
                translationService.invalidateExpiredCache();
                languageStorage.cleanupExpiredCache();
                
                // Wait for next cleanup cycle
                Thread.sleep(300000); // 5 minutes
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                getLogger().warning("Error in cache cleanup task: " + e.getMessage());
            }
        }
    }
    
    private void metricsCollectionTask() {
        while (!asyncExecutor.isShutdown()) {
            try {
                // Collect and log metrics
                collectMetrics();
                
                // Wait for next collection cycle
                Thread.sleep(600000); // 10 minutes
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                getLogger().warning("Error in metrics collection task: " + e.getMessage());
            }
        }
    }
    
    private void collectMetrics() {
        // Collect language usage statistics
        Map<String, Integer> languageStats = languageStorage.getLanguageUsageStatistics();
        
        getLogger().info("=== Language Usage Statistics ===");
        languageStats.forEach((lang, count) -> 
            getLogger().info(lang + ": " + count + " players"));
        
        // Collect cache statistics
        getLogger().info("Translation cache size: " + translationService.getCacheSize());
        getLogger().info("Language storage cache size: " + languageStorage.getCacheSize());
    }
    
    @Override
    public void onDisable() {
        // Shutdown background services
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
        }
        
        // Save any pending changes
        savePendingChanges();
        
        // Close connections
        if (configManager != null) {
            configManager.close();
        }
        
        getLogger().info("MongoDB Configs Plugin disabled");
    }
    
    private void savePendingChanges() {
        try {
            // Flush any pending language changes
            languageStorage.flushPendingChanges();
            
            // Save translation updates
            translationManager.flushPendingUpdates();
            
        } catch (Exception e) {
            getLogger().warning("Error saving pending changes: " + e.getMessage());
        }
    }
    
    // Public API methods
    public String getMessage(Player player, String key, Object... args) {
        String language = languageStorage.getPlayerEffectiveLanguage(player.getUniqueId().toString());
        return translationService.translate(language, key, args);
    }
    
    public void setPlayerLanguage(Player player, String language) {
        languageStorage.setPlayerSelectedLanguage(player.getUniqueId().toString(), language, "plugin");
    }
    
    public ConfigManager getConfigManager() { return configManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public MessageTranslationService getTranslationService() { return translationService; }
    public PlayerLanguageStorage getLanguageStorage() { return languageStorage; }
    public TranslationManager getTranslationManager() { return translationManager; }
}
```

### Configuration File

```yaml
# config.yml
# MongoDB Configs Plugin Configuration

# MongoDB Connection Settings
mongodb:
  uri: "mongodb://localhost:27017"
  database: "minecraft_configs"
  min-pool-size: 5
  max-pool-size: 50
  max-idle-time: 30000
  max-life-time: 300000
  connection-timeout: 10000
  server-selection-timeout: 5000
  socket-timeout: 20000

# Language System Settings
language:
  default-language: "en"
  supported-languages:
    - "en"
    - "pl"
    - "es"
    - "de"
    - "fr"
  auto-detect: true
  cache-expiry-minutes: 30

# Translation Settings
translation:
  cache-size: 1000
  cache-expiry-minutes: 60
  enable-real-time-updates: true

# Performance Settings
performance:
  async-operations: true
  batch-size: 100
  metrics-enabled: true
  metrics-interval-minutes: 10

# Debug Settings
debug:
  enabled: false
  log-queries: false
  log-translations: false
```

## 🔧 Service Management

### Service Interface

```java
public interface Service {
    void start() throws Exception;
    void stop() throws Exception;
    boolean isRunning();
    String getServiceName();
    default void restart() throws Exception {
        stop();
        start();
    }
}
```

### Language Service

```java
public class LanguageService implements Service {
    
    private final MongoConfigsPlugin plugin;
    private final LanguageManager languageManager;
    private final PlayerLanguageStorage languageStorage;
    private volatile boolean running = false;
    
    public LanguageService(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.languageStorage = plugin.getLanguageStorage();
    }
    
    @Override
    public void start() throws Exception {
        if (running) {
            throw new IllegalStateException("Language service is already running");
        }
        
        // Initialize language configurations
        initializeLanguageConfigs();
        
        // Setup change streams
        setupChangeStreams();
        
        // Start background tasks
        startBackgroundTasks();
        
        running = true;
        plugin.getLogger().info("Language service started");
    }
    
    @Override
    public void stop() throws Exception {
        if (!running) {
            return;
        }
        
        // Stop background tasks
        stopBackgroundTasks();
        
        // Close change streams
        closeChangeStreams();
        
        running = false;
        plugin.getLogger().info("Language service stopped");
    }
    
    @Override
    public boolean isRunning() {
        return running;
    }
    
    @Override
    public String getServiceName() {
        return "LanguageService";
    }
    
    private void initializeLanguageConfigs() {
        // Load or create default language configuration
        LanguageConfig config = plugin.getConfigManager().get(LanguageConfig.class, "global_config");
        if (config == null) {
            config = new LanguageConfig();
            plugin.getConfigManager().save(config);
        }
    }
    
    private void setupChangeStreams() {
        // Setup change streams for real-time language updates
        plugin.getConfigManager().watchCollection(PlayerLanguage.class, changeEvent -> {
            PlayerLanguage updated = changeEvent.getDocument();
            if (updated != null) {
                // Handle language change
                handleLanguageChange(updated);
            }
        });
    }
    
    private void handleLanguageChange(PlayerLanguage playerLanguage) {
        // Notify other services of language change
        String playerId = playerLanguage.getPlayerId();
        String newLanguage = playerLanguage.getEffectiveLanguage();
        
        // Update any cached data for this player
        plugin.getTranslationService().invalidatePlayerCache(playerId);
        
        // Log the change
        plugin.getLogger().info("Player " + playerId + " changed language to " + newLanguage);
    }
    
    private void startBackgroundTasks() {
        // Start language statistics collection
        // Start cache cleanup
        // Start health checks
    }
    
    private void stopBackgroundTasks() {
        // Stop all background tasks
    }
    
    private void closeChangeStreams() {
        // Close all change stream listeners
    }
}
```

## 📊 Metrics and Monitoring

### Metrics Service

```java
public class MetricsService implements Service {
    
    private final MongoConfigsPlugin plugin;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    
    private final AtomicLong totalTranslations = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final Map<String, AtomicLong> languageUsage = new ConcurrentHashMap<>();
    
    public MetricsService(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    @Override
    public void start() throws Exception {
        if (running) {
            throw new IllegalStateException("Metrics service is already running");
        }
        
        // Schedule metrics collection
        scheduler.scheduleAtFixedRate(this::collectMetrics, 1, 10, TimeUnit.MINUTES);
        
        // Schedule metrics reporting
        scheduler.scheduleAtFixedRate(this::reportMetrics, 5, 30, TimeUnit.MINUTES);
        
        running = true;
        plugin.getLogger().info("Metrics service started");
    }
    
    @Override
    public void stop() throws Exception {
        if (!running) {
            return;
        }
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        running = false;
        plugin.getLogger().info("Metrics service stopped");
    }
    
    @Override
    public boolean isRunning() {
        return running;
    }
    
    @Override
    public String getServiceName() {
        return "MetricsService";
    }
    
    public void recordTranslation(String language) {
        totalTranslations.incrementAndGet();
        languageUsage.computeIfAbsent(language, k -> new AtomicLong()).incrementAndGet();
    }
    
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }
    
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }
    
    private void collectMetrics() {
        try {
            // Collect MongoDB connection pool stats
            collectConnectionPoolStats();
            
            // Collect cache statistics
            collectCacheStats();
            
            // Collect language usage stats
            collectLanguageStats();
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error collecting metrics: " + e.getMessage());
        }
    }
    
    private void collectConnectionPoolStats() {
        // Implementation depends on MongoDB driver version
        // This would collect active connections, pool size, etc.
    }
    
    private void collectCacheStats() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;
        
        plugin.getLogger().info(String.format("Cache stats - Hits: %d, Misses: %d, Hit Rate: %.2f%%", 
            hits, misses, hitRate));
    }
    
    private void collectLanguageStats() {
        plugin.getLogger().info("=== Language Usage (Last 10 minutes) ===");
        languageUsage.forEach((lang, count) -> 
            plugin.getLogger().info(lang + ": " + count.get()));
        
        // Reset counters
        languageUsage.clear();
    }
    
    private void reportMetrics() {
        // Send metrics to monitoring system (optional)
        // Could integrate with Prometheus, Grafana, etc.
    }
    
    public Map<String, Object> getCurrentMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalTranslations", totalTranslations.get());
        metrics.put("cacheHits", cacheHits.get());
        metrics.put("cacheMisses", cacheMisses.get());
        metrics.put("languageUsage", new HashMap<>(languageUsage));
        
        return metrics;
    }
}
```

## 🔄 Hot Reload System

### Configuration Reloader

```java
public class ConfigurationReloader {
    
    private final MongoConfigsPlugin plugin;
    private final File configFile;
    private long lastModified;
    
    public ConfigurationReloader(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.lastModified = configFile.lastModified();
    }
    
    public boolean checkForChanges() {
        long currentModified = configFile.lastModified();
        if (currentModified > lastModified) {
            lastModified = currentModified;
            return true;
        }
        return false;
    }
    
    public void reloadConfiguration() {
        try {
            // Reload the configuration
            plugin.reloadConfig();
            
            // Update MongoDB settings if changed
            updateMongoDBSettings();
            
            // Update language settings if changed
            updateLanguageSettings();
            
            // Update translation settings if changed
            updateTranslationSettings();
            
            // Clear caches if necessary
            clearCachesIfNeeded();
            
            plugin.getLogger().info("Configuration reloaded successfully");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
        }
    }
    
    private void updateMongoDBSettings() {
        // Update connection pool settings
        // This might require recreating the ConfigManager
    }
    
    private void updateLanguageSettings() {
        // Update language manager settings
        LanguageConfig langConfig = plugin.getConfigManager().get(LanguageConfig.class, "global_config");
        if (langConfig != null) {
            // Update settings from config.yml
            langConfig.setDefaultLanguage(plugin.getConfig().getString("language.default-language", "en"));
            langConfig.setAutoDetectLanguage(plugin.getConfig().getBoolean("language.auto-detect", true));
            
            plugin.getConfigManager().save(langConfig);
        }
    }
    
    private void updateTranslationSettings() {
        // Update translation service settings
        // This might involve updating cache settings
    }
    
    private void clearCachesIfNeeded() {
        // Clear caches if configuration changed significantly
        plugin.getTranslationService().invalidateAllCache();
    }
}
```

### Reload Listener

```java
public class PluginReloadListener implements Listener {
    
    private final MongoConfigsPlugin plugin;
    private final ConfigurationReloader configReloader;
    
    public PluginReloadListener(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.configReloader = new ConfigurationReloader(plugin);
    }
    
    @EventHandler
    public void onPluginReload(PluginReloadEvent event) {
        // Check if our plugin was reloaded
        if (event.getPlugin().equals(plugin)) {
            handlePluginReload();
        }
    }
    
    private void handlePluginReload() {
        plugin.getLogger().info("Plugin reload detected, checking for configuration changes...");
        
        if (configReloader.checkForChanges()) {
            plugin.getLogger().info("Configuration changes detected, reloading...");
            configReloader.reloadConfiguration();
        } else {
            plugin.getLogger().info("No configuration changes detected");
        }
        
        // Restart services if needed
        restartServicesIfNeeded();
    }
    
    private void restartServicesIfNeeded() {
        // Restart services that might need it after reload
        try {
            plugin.getLanguageService().restart();
            plugin.getMetricsService().restart();
        } catch (Exception e) {
            plugin.getLogger().warning("Error restarting services: " + e.getMessage());
        }
    }
}
```

## 🚨 Error Handling

### Error Handler

```java
public class ErrorHandler {
    
    private final MongoConfigsPlugin plugin;
    private final Map<String, ErrorRecoveryStrategy> recoveryStrategies = new HashMap<>();
    
    public ErrorHandler(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        
        // Register recovery strategies
        recoveryStrategies.put("mongodb_connection", new MongoDBConnectionRecovery());
        recoveryStrategies.put("cache_error", new CacheErrorRecovery());
        recoveryStrategies.put("translation_error", new TranslationErrorRecovery());
    }
    
    public void handleError(String errorType, Exception exception, Map<String, Object> context) {
        // Log the error
        plugin.getLogger().severe("Error occurred: " + errorType + " - " + exception.getMessage());
        
        // Try to recover
        ErrorRecoveryStrategy strategy = recoveryStrategies.get(errorType);
        if (strategy != null) {
            try {
                strategy.recover(exception, context);
                plugin.getLogger().info("Successfully recovered from " + errorType);
            } catch (Exception recoveryException) {
                plugin.getLogger().severe("Failed to recover from " + errorType + ": " + recoveryException.getMessage());
                // Escalation logic here
            }
        } else {
            plugin.getLogger().warning("No recovery strategy for error type: " + errorType);
        }
        
        // Notify administrators if critical
        if (isCriticalError(errorType)) {
            notifyAdministrators(errorType, exception);
        }
    }
    
    private boolean isCriticalError(String errorType) {
        return Arrays.asList("mongodb_connection", "service_failure").contains(errorType);
    }
    
    private void notifyAdministrators(String errorType, Exception exception) {
        // Send notification to online administrators
        String message = "Critical error occurred: " + errorType + " - " + exception.getMessage();
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("mongoconfigs.admin")) {
                player.sendMessage(ChatColor.RED + "[CRITICAL] " + message);
            }
        }
    }
    
    public interface ErrorRecoveryStrategy {
        void recover(Exception exception, Map<String, Object> context) throws Exception;
    }
    
    public class MongoDBConnectionRecovery implements ErrorRecoveryStrategy {
        @Override
        public void recover(Exception exception, Map<String, Object> context) throws Exception {
            // Attempt to reconnect to MongoDB
            plugin.getConfigManager().reconnect();
        }
    }
    
    public class CacheErrorRecovery implements ErrorRecoveryStrategy {
        @Override
        public void recover(Exception exception, Map<String, Object> context) throws Exception {
            // Clear corrupted cache
            plugin.getTranslationService().invalidateAllCache();
        }
    }
    
    public class TranslationErrorRecovery implements ErrorRecoveryStrategy {
        @Override
        public void recover(Exception exception, Map<String, Object> context) throws Exception {
            // Fallback to default language
            String playerId = (String) context.get("playerId");
            if (playerId != null) {
                plugin.getLanguageStorage().setPlayerSelectedLanguage(playerId, "en", "error_recovery");
            }
        }
    }
}
```

## 🔧 Integration Examples

### Dependency Management

```xml
<!-- pom.xml -->
<dependencies>
    <!-- MongoDB Configs API -->
    <dependency>
        <groupId>xyz.wtje.mongoconfigs</groupId>
        <artifactId>mongo-configs-api</artifactId>
        <version>1.0.0</version>
        <scope>compile</scope>
    </dependency>
    
    <!-- MongoDB Driver -->
    <dependency>
        <groupId>org.mongodb</groupId>
        <artifactId>mongodb-driver-sync</artifactId>
        <version>4.8.0</version>
        <scope>compile</scope>
    </dependency>
    
    <!-- Caffeine Cache -->
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
        <version>3.1.6</version>
        <scope>compile</scope>
    </dependency>
    
    <!-- Paper/Spigot API -->
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.19.4-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Plugin.yml Configuration

```yaml
# plugin.yml
name: MongoConfigsPlugin
version: 1.0.0
main: xyz.wtje.mongoconfigs.MongoConfigsPlugin
api-version: 1.19

commands:
  lang:
    description: "Manage your language preferences"
    usage: "/lang [set|current|list|detect|auto|help]"
    permission: "mongoconfigs.use"
  translate:
    description: "Manage translations"
    usage: "/translate [test|add|update|remove|list|search]"
    permission: "mongoconfigs.translate"
  langadmin:
    description: "Admin language management"
    usage: "/langadmin [stats|setplayer|resetplayer|broadcast|reload|export|import]"
    permission: "mongoconfigs.admin"

permissions:
  mongoconfigs.use:
    description: "Allows players to manage their language preferences"
    default: true
  mongoconfigs.translate:
    description: "Allows managing translations"
    default: op
  mongoconfigs.admin:
    description: "Allows admin language management"
    default: op
```

---

*Next: Learn about [[Hot Reload System]] for dynamic configuration updates.*