# Troubleshooting

Comprehensive guide for diagnosing and resolving common issues with the MongoDB Configs API, including debugging tools and recovery procedures.

## 🔧 Troubleshooting Overview

The Troubleshooting guide provides systematic approaches to diagnose and resolve common issues encountered when using the MongoDB Configs API.

## 📋 Common Issues and Solutions

### Connection Issues

#### MongoDB Connection Failed

**Symptoms:**
- Plugin fails to start with connection errors
- "Failed to connect to MongoDB" messages in console
- Configuration operations fail

**Diagnosis Steps:**
```java
// Test connection manually
public void diagnoseConnection() {
    MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
    
    try {
        // Check if MongoDB is reachable
        boolean reachable = plugin.getConfigManager().testConnection();
        
        if (!reachable) {
            plugin.getLogger().warning("MongoDB server is not reachable");
            
            // Check connection string
            String connectionString = plugin.getConfig().getString("mongodb.uri");
            plugin.getLogger().info("Connection string: " + connectionString);
            
            // Try to parse connection string
            ConnectionString connString = new ConnectionString(connectionString);
            plugin.getLogger().info("Host: " + connString.getHosts());
            plugin.getLogger().info("Database: " + connString.getDatabase());
            
            // Check authentication
            if (connString.getCredential() != null) {
                plugin.getLogger().info("Authentication: Configured");
            } else {
                plugin.getLogger().warning("Authentication: Not configured");
            }
        }
        
    } catch (Exception e) {
        plugin.getLogger().severe("Connection diagnosis failed: " + e.getMessage());
    }
}
```

**Common Solutions:**

1. **Network Connectivity:**
   ```yaml
   # Check config.yml
   mongodb:
     uri: "mongodb://localhost:27017"
     # Try with explicit host
     # uri: "mongodb://127.0.0.1:27017"
   ```

2. **Authentication Issues:**
   ```yaml
   # Ensure proper authentication
   mongodb:
     uri: "mongodb://username:password@localhost:27017/database"
   ```

3. **Firewall/Security Groups:**
   - Ensure MongoDB port (27017) is open
   - Check if MongoDB is bound to correct interface
   - Verify security group rules allow connections

4. **MongoDB Server Issues:**
   - Check if MongoDB service is running
   - Verify MongoDB logs for errors
   - Ensure sufficient disk space
   - Check MongoDB version compatibility

#### Connection Pool Exhausted

**Symptoms:**
- "Connection pool exhausted" errors
- Slow response times
- Operations timing out

**Solutions:**
```yaml
# config.yml - Increase connection pool size
mongodb:
  pool:
    max-size: 50  # Increase from default 20
    min-size: 10  # Increase from default 5
    max-wait-time: 10000  # 10 seconds
    max-connection-idle-time: 60000  # 1 minute
```

### Configuration Issues

#### Configuration Not Loading

**Symptoms:**
- Default values used instead of saved configuration
- Configuration changes not persisting

**Diagnosis:**
```java
public void diagnoseConfigLoading() {
    MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
    
    try {
        // Check if configuration exists
        boolean exists = plugin.getConfigManager().exists("global_config");
        plugin.getLogger().info("Global config exists: " + exists);
        
        if (exists) {
            // Try to load configuration
            GlobalConfig config = plugin.getConfigManager().get(GlobalConfig.class, "global_config");
            
            if (config != null) {
                plugin.getLogger().info("Config loaded successfully: " + config);
            } else {
                plugin.getLogger().warning("Config exists but failed to load");
                
                // Check for deserialization errors
                try {
                    Document doc = plugin.getConfigManager().getDatabase()
                        .getCollection("configs")
                        .find(Filters.eq("_id", "global_config"))
                        .first();
                    
                    if (doc != null) {
                        plugin.getLogger().info("Raw document: " + doc.toJson());
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to read raw document: " + e.getMessage());
                }
            }
        }
        
    } catch (Exception e) {
        plugin.getLogger().severe("Config diagnosis failed: " + e.getMessage());
    }
}
```

**Common Solutions:**

1. **Annotation Issues:**
   ```java
   // Ensure proper annotations
   @ConfigsFileProperties
   @ConfigsDatabase(database = "mongoconfigs")
   @ConfigsCollection(collection = "configs")
   public class GlobalConfig {
       // Fields must be public or have getters/setters
       public String serverName;
       public boolean debugMode;
   }
   ```

2. **Collection Name Mismatch:**
   ```java
   // Check collection name matches
   @ConfigsCollection(collection = "configs")  // Must match actual collection
   public class GlobalConfig {
   }
   ```

3. **Serialization Issues:**
   - Ensure all fields are serializable
   - Check for circular references
   - Verify field types are supported

#### Configuration Corruption

**Symptoms:**
- Partial configuration loading
- Unexpected default values
- Deserialization errors

**Recovery:**
```java
public void recoverCorruptedConfig() {
    MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
    
    try {
        String configKey = "global_config";
        
        // Create backup of corrupted config
        Document corrupted = plugin.getConfigManager().getDatabase()
            .getCollection("configs")
            .find(Filters.eq("_id", configKey))
            .first();
        
        if (corrupted != null) {
            // Move to backup collection
            plugin.getConfigManager().getDatabase()
                .getCollection("configs_backup")
                .insertOne(new Document("_id", configKey + "_backup_" + System.currentTimeMillis())
                    .append("original", corrupted)
                    .append("backup_time", new Date()));
            
            plugin.getLogger().info("Corrupted config backed up");
        }
        
        // Delete corrupted config
        plugin.getConfigManager().delete(configKey);
        
        // Create fresh config with defaults
        GlobalConfig defaultConfig = new GlobalConfig();
        plugin.getConfigManager().save(defaultConfig);
        
        plugin.getLogger().info("Fresh config created with defaults");
        
    } catch (Exception e) {
        plugin.getLogger().severe("Config recovery failed: " + e.getMessage());
    }
}
```

### Translation Issues

#### Missing Translations

**Symptoms:**
- Keys displayed instead of translated text
- "Translation not found" messages
- Inconsistent language display

**Diagnosis:**
```java
public void diagnoseTranslationIssues() {
    MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
    
    try {
        // Check supported languages
        List<String> languages = plugin.getLanguageManager().getSupportedLanguages();
        plugin.getLogger().info("Supported languages: " + languages);
        
        // Test translation loading
        for (String language : languages) {
            String testKey = "welcome";
            String translation = plugin.getTranslationService().translate(language, testKey);
            
            if (translation == null) {
                plugin.getLogger().warning("Missing translation for key '" + testKey + "' in language '" + language + "'");
                
                // Check if translation document exists
                boolean exists = plugin.getConfigManager().exists("translation_" + language + "_" + testKey);
                plugin.getLogger().info("Translation document exists: " + exists);
            } else {
                plugin.getLogger().info("Translation for '" + testKey + "' in '" + language + "': " + translation);
            }
        }
        
    } catch (Exception e) {
        plugin.getLogger().severe("Translation diagnosis failed: " + e.getMessage());
    }
}
```

**Solutions:**

1. **Reload Translations:**
   ```java
   // Force reload all translations
   plugin.getTranslationService().invalidateAllCache();
   plugin.getTranslationManager().reloadAllTranslations();
   ```

2. **Check Translation Files:**
   - Verify translation files are in correct format
   - Ensure proper encoding (UTF-8)
   - Check for syntax errors in translation files

3. **Language Configuration:**
   ```yaml
   # config.yml
   language:
     default-language: "en"
     supported-languages:
       - "en"
       - "es"
       - "fr"
   ```

#### Placeholder Issues

**Symptoms:**
- Placeholders not replaced in translations
- "{0}", "{1}" displayed instead of values
- Formatting errors

**Diagnosis:**
```java
public void diagnosePlaceholderIssues() {
    MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
    
    try {
        String testKey = "welcome.player";
        String translation = plugin.getTranslationService().translate("en", testKey);
        
        if (translation != null) {
            plugin.getLogger().info("Raw translation: " + translation);
            
            // Test placeholder replacement
            String formatted = plugin.getTranslationService().translate("en", testKey, "Steve");
            plugin.getLogger().info("Formatted translation: " + formatted);
            
            // Check if placeholders are correctly defined
            if (translation.contains("{0}")) {
                plugin.getLogger().info("Translation uses indexed placeholders");
            } else if (translation.contains("%s")) {
                plugin.getLogger().info("Translation uses printf-style placeholders");
            } else {
                plugin.getLogger().warning("No placeholders found in translation");
            }
        }
        
    } catch (Exception e) {
        plugin.getLogger().severe("Placeholder diagnosis failed: " + e.getMessage());
    }
}
```

### Performance Issues

#### Slow Operations

**Symptoms:**
- Operations taking longer than expected
- UI lag during configuration operations
- High CPU/memory usage

**Diagnosis:**
```java
public void diagnosePerformanceIssues() {
    MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
    
    try {
        // Check cache performance
        MultiLevelCache.CacheStats cacheStats = plugin.getCache().getStats();
        plugin.getLogger().info("Cache hit rate: " + String.format("%.2f%%", cacheStats.getL1HitRate() * 100));
        
        // Check database performance
        long startTime = System.currentTimeMillis();
        plugin.getConfigManager().testConnection();
        long connectionTime = System.currentTimeMillis() - startTime;
        plugin.getLogger().info("Database connection time: " + connectionTime + "ms");
        
        // Check memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsage = (double) usedMemory / maxMemory;
        plugin.getLogger().info("Memory usage: " + String.format("%.2f%%", memoryUsage * 100));
        
        // Check thread pool status
        if (plugin.getAsyncConfigManager() != null) {
            int pendingOps = plugin.getAsyncConfigManager().getPendingOperationCount();
            plugin.getLogger().info("Pending async operations: " + pendingOps);
        }
        
    } catch (Exception e) {
        plugin.getLogger().severe("Performance diagnosis failed: " + e.getMessage());
    }
}
```

**Optimization Solutions:**

1. **Cache Optimization:**
   ```yaml
   # config.yml
   cache:
     l1-size: 20000  # Increase cache size
     l1-expiry-minutes: 60  # Increase TTL
     l2-enabled: true
   ```

2. **Connection Pool Tuning:**
   ```yaml
   mongodb:
     pool:
       max-size: 100  # Increase for high load
       min-size: 20
   ```

3. **Async Operations:**
   ```yaml
   async:
     thread-pool-size: 20  # Increase thread pool
     operation-timeout-ms: 10000
   ```

#### Memory Leaks

**Symptoms:**
- Gradually increasing memory usage
- OutOfMemoryError exceptions
- Server performance degradation over time

**Diagnosis:**
```java
public void diagnoseMemoryLeaks() {
    MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
    
    try {
        // Force garbage collection
        System.gc();
        Thread.sleep(1000);
        
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();
        
        plugin.getLogger().info("Memory after GC:");
        plugin.getLogger().info("  Used: " + (usedMemory / 1024 / 1024) + "MB");
        plugin.getLogger().info("  Free: " + (freeMemory / 1024 / 1024) + "MB");
        plugin.getLogger().info("  Total: " + (totalMemory / 1024 / 1024) + "MB");
        plugin.getLogger().info("  Max: " + (maxMemory / 1024 / 1024) + "MB");
        
        // Check cache sizes
        long cacheSize = plugin.getCache().getStats().getL1Size();
        plugin.getLogger().info("Cache size: " + cacheSize + " items");
        
        // Check pending operations
        if (plugin.getAsyncConfigManager() != null) {
            int pendingOps = plugin.getAsyncConfigManager().getPendingOperationCount();
            plugin.getLogger().info("Pending operations: " + pendingOps);
        }
        
        // Check for large collections
        Map<String, Object> collectionStats = plugin.getIndexManager().getIndexStats();
        plugin.getLogger().info("Collection stats: " + collectionStats);
        
    } catch (Exception e) {
        plugin.getLogger().severe("Memory diagnosis failed: " + e.getMessage());
    }
}
```

### Cache Issues

#### Cache Invalidation Problems

**Symptoms:**
- Stale data being served
- Configuration changes not reflected
- Cache not updating after changes

**Solutions:**
```java
public void fixCacheInvalidation() {
    MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
    
    try {
        // Clear all caches
        plugin.getCache().invalidateAll();
        plugin.getTranslationService().invalidateAllCache();
        
        // Reset cache statistics
        plugin.getPerformanceMetrics().resetMetrics();
        
        // Force reload of critical configurations
        plugin.reloadConfiguration();
        
        plugin.getLogger().info("Cache invalidation completed");
        
    } catch (Exception e) {
        plugin.getLogger().severe("Cache invalidation failed: " + e.getMessage());
    }
}
```

#### Cache Performance Issues

**Symptoms:**
- Low cache hit rates
- High cache miss rates
- Slow cache operations

**Optimization:**
```java
public void optimizeCachePerformance() {
    MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
    
    try {
        // Analyze current cache performance
        MultiLevelCache.CacheStats stats = plugin.getCache().getStats();
        double hitRate = stats.getL1HitRate();
        
        plugin.getLogger().info("Current cache hit rate: " + String.format("%.2f%%", hitRate * 100));
        
        if (hitRate < 0.7) {
            // Increase cache size
            plugin.getLogger().info("Increasing cache size to improve hit rate");
            
            // This would require cache reconfiguration
            // Implementation depends on cache implementation
        }
        
        // Preload frequently accessed items
        plugin.getCache().preloadCache(Arrays.asList(
            "global_config",
            "language_config", 
            "server_config"
        ));
        
        plugin.getLogger().info("Cache optimization completed");
        
    } catch (Exception e) {
        plugin.getLogger().severe("Cache optimization failed: " + e.getMessage());
    }
}
```

## 🔧 Debugging Tools

### Debug Command

```java
public class DebugCommand implements CommandExecutor {
    
    private final MongoConfigsPlugin plugin;
    
    public DebugCommand(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mongoconfigs.admin")) {
            sender.sendMessage(ColorHelper.parseComponent("&cYou don't have permission to use this command!"));
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "connection":
                return debugConnection(sender);
            case "config":
                return debugConfig(sender, Arrays.copyOfRange(args, 1, args.length));
            case "translation":
                return debugTranslation(sender, Arrays.copyOfRange(args, 1, args.length));
            case "cache":
                return debugCache(sender);
            case "performance":
                return debugPerformance(sender);
            case "memory":
                return debugMemory(sender);
            default:
                sendUsage(sender);
                return true;
        }
    }
    
    private boolean debugConnection(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&6=== Connection Debug ==="));
        
        try {
            boolean connected = plugin.getConfigManager().testConnection();
            sender.sendMessage(ColorHelper.parseComponent("&fConnection test: &e" + (connected ? "SUCCESS" : "FAILED")));
            
            String uri = plugin.getConfig().getString("mongodb.uri");
            sender.sendMessage(ColorHelper.parseComponent("&fURI: &e" + maskPassword(uri)));
            
            // Test basic operations
            long startTime = System.currentTimeMillis();
            plugin.getConfigManager().exists("test");
            long duration = System.currentTimeMillis() - startTime;
            sender.sendMessage(ColorHelper.parseComponent("&fResponse time: &e" + duration + "ms"));
            
        } catch (Exception e) {
            sender.sendMessage(ColorHelper.parseComponent("&cConnection debug failed: &e" + e.getMessage()));
        }
        
        return true;
    }
    
    private boolean debugConfig(CommandSender sender, String[] args) {
        sender.sendMessage(ColorHelper.parseComponent("&6=== Configuration Debug ==="));
        
        try {
            if (args.length == 0) {
                // Show general config info
                boolean globalExists = plugin.getConfigManager().exists("global_config");
                sender.sendMessage(ColorHelper.parseComponent("&fGlobal config exists: &e" + globalExists));
                
                long configCount = plugin.getConfigManager().getDatabase()
                    .getCollection("configs")
                    .countDocuments();
                sender.sendMessage(ColorHelper.parseComponent("&fTotal configs: &e" + configCount));
                
            } else {
                // Debug specific config
                String configKey = args[0];
                boolean exists = plugin.getConfigManager().exists(configKey);
                sender.sendMessage(ColorHelper.parseComponent("&fConfig '" + configKey + "' exists: &e" + exists));
                
                if (exists) {
                    Object config = plugin.getConfigManager().get(Object.class, configKey);
                    sender.sendMessage(ColorHelper.parseComponent("&fConfig type: &e" + 
                        (config != null ? config.getClass().getSimpleName() : "null")));
                }
            }
            
        } catch (Exception e) {
            sender.sendMessage(ColorHelper.parseComponent("&cConfig debug failed: &e" + e.getMessage()));
        }
        
        return true;
    }
    
    private boolean debugTranslation(CommandSender sender, String[] args) {
        sender.sendMessage(ColorHelper.parseComponent("&6=== Translation Debug ==="));
        
        try {
            List<String> languages = plugin.getLanguageManager().getSupportedLanguages();
            sender.sendMessage(ColorHelper.parseComponent("&fSupported languages: &e" + languages));
            
            if (args.length >= 1) {
                String language = args[0];
                String key = args.length >= 2 ? args[1] : "welcome";
                
                String translation = plugin.getTranslationService().translate(language, key);
                sender.sendMessage(ColorHelper.parseComponent("&fTranslation for '" + key + "' in '" + language + "': &e" + 
                    (translation != null ? translation : "NOT FOUND")));
            }
            
        } catch (Exception e) {
            sender.sendMessage(ColorHelper.parseComponent("&cTranslation debug failed: &e" + e.getMessage()));
        }
        
        return true;
    }
    
    private boolean debugCache(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&6=== Cache Debug ==="));
        
        try {
            MultiLevelCache.CacheStats stats = plugin.getCache().getStats();
            
            sender.sendMessage(ColorHelper.parseComponent("&fL1 Cache Size: &e" + stats.getL1Size()));
            sender.sendMessage(ColorHelper.parseComponent("&fL2 Cache Size: &e" + stats.getL2Size()));
            sender.sendMessage(ColorHelper.parseComponent("&fL1 Hit Rate: &e" + 
                String.format("%.2f%%", stats.getL1HitRate() * 100)));
            sender.sendMessage(ColorHelper.parseComponent("&fTotal Requests: &e" + 
                (stats.getL1Hits() + stats.getL1Misses())));
            
        } catch (Exception e) {
            sender.sendMessage(ColorHelper.parseComponent("&cCache debug failed: &e" + e.getMessage()));
        }
        
        return true;
    }
    
    private boolean debugPerformance(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&6=== Performance Debug ==="));
        
        try {
            Map<String, Object> metrics = plugin.getPerformanceMetrics().getMetricsSnapshot();
            
            // Memory info
            @SuppressWarnings("unchecked")
            Map<String, Object> memory = (Map<String, Object>) metrics.get("memory");
            if (memory != null) {
                long used = (Long) memory.get("used");
                long total = (Long) memory.get("total");
                sender.sendMessage(ColorHelper.parseComponent("&fMemory: &e" + 
                    String.format("%.2fMB / %.2fMB", used / 1024.0 / 1024.0, total / 1024.0 / 1024.0)));
            }
            
            // Operation counts
            @SuppressWarnings("unchecked")
            Map<String, PerformanceMetrics.OperationMetrics> operations = 
                (Map<String, PerformanceMetrics.OperationMetrics>) metrics.get("operations");
            
            if (operations != null && !operations.isEmpty()) {
                sender.sendMessage(ColorHelper.parseComponent("&fTop Operations:"));
                operations.entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(e2.getValue().getTotalCount(), e1.getValue().getTotalCount()))
                    .limit(3)
                    .forEach(entry -> {
                        String opName = entry.getKey();
                        PerformanceMetrics.OperationMetrics opMetrics = entry.getValue();
                        sender.sendMessage(ColorHelper.parseComponent("&f- " + opName + ": &e" + 
                            opMetrics.getTotalCount() + " calls"));
                    });
            }
            
        } catch (Exception e) {
            sender.sendMessage(ColorHelper.parseComponent("&cPerformance debug failed: &e" + e.getMessage()));
        }
        
        return true;
    }
    
    private boolean debugMemory(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&6=== Memory Debug ==="));
        
        try {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            long free = runtime.freeMemory();
            long total = runtime.totalMemory();
            long max = runtime.maxMemory();
            
            sender.sendMessage(ColorHelper.parseComponent("&fUsed: &e" + 
                String.format("%.2fMB", used / 1024.0 / 1024.0)));
            sender.sendMessage(ColorHelper.parseComponent("&fFree: &e" + 
                String.format("%.2fMB", free / 1024.0 / 1024.0)));
            sender.sendMessage(ColorHelper.parseComponent("&fTotal: &e" + 
                String.format("%.2fMB", total / 1024.0 / 1024.0)));
            sender.sendMessage(ColorHelper.parseComponent("&fMax: &e" + 
                String.format("%.2fMB", max / 1024.0 / 1024.0)));
            sender.sendMessage(ColorHelper.parseComponent("&fUsage: &e" + 
                String.format("%.1f%%", (double) used / total * 100)));
            
        } catch (Exception e) {
            sender.sendMessage(ColorHelper.parseComponent("&cMemory debug failed: &e" + e.getMessage()));
        }
        
        return true;
    }
    
    private String maskPassword(String uri) {
        // Simple password masking for display
        return uri.replaceAll(":[^:@/]+@", ":****@");
    }
    
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&6Debug Commands:"));
        sender.sendMessage(ColorHelper.parseComponent("&f/debug connection &7- Debug database connection"));
        sender.sendMessage(ColorHelper.parseComponent("&f/debug config [key] &7- Debug configuration loading"));
        sender.sendMessage(ColorHelper.parseComponent("&f/debug translation [lang] [key] &7- Debug translations"));
        sender.sendMessage(ColorHelper.parseComponent("&f/debug cache &7- Debug cache performance"));
        sender.sendMessage(ColorHelper.parseComponent("&f/debug performance &7- Debug performance metrics"));
        sender.sendMessage(ColorHelper.parseComponent("&f/debug memory &7- Debug memory usage"));
    }
}
```

### Recovery Procedures

#### Emergency Recovery

```java
public class EmergencyRecovery {
    
    private final MongoConfigsPlugin plugin;
    
    public EmergencyRecovery(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void performEmergencyRecovery() {
        plugin.getLogger().severe("=== EMERGENCY RECOVERY INITIATED ===");
        
        try {
            // Step 1: Stop all operations
            plugin.getLogger().info("Stopping all operations...");
            stopAllOperations();
            
            // Step 2: Clear caches
            plugin.getLogger().info("Clearing all caches...");
            clearAllCaches();
            
            // Step 3: Reset connections
            plugin.getLogger().info("Resetting database connections...");
            resetConnections();
            
            // Step 4: Load minimal configuration
            plugin.getLogger().info("Loading minimal configuration...");
            loadMinimalConfig();
            
            // Step 5: Restart services
            plugin.getLogger().info("Restarting services...");
            restartServices();
            
            plugin.getLogger().severe("=== EMERGENCY RECOVERY COMPLETED ===");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Emergency recovery failed: " + e.getMessage());
            // Last resort: disable plugin
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }
    
    private void stopAllOperations() {
        // Cancel pending async operations
        if (plugin.getAsyncConfigManager() != null) {
            plugin.getAsyncConfigManager().cancelAllPendingOperations();
        }
        
        // Stop health monitoring
        if (plugin.getHealthChecker() != null) {
            plugin.getHealthChecker().shutdown();
        }
    }
    
    private void clearAllCaches() {
        if (plugin.getCache() != null) {
            plugin.getCache().invalidateAll();
        }
        
        if (plugin.getTranslationService() != null) {
            plugin.getTranslationService().invalidateAllCache();
        }
    }
    
    private void resetConnections() {
        // Force reconnection to MongoDB
        plugin.reconnectMongoDB();
    }
    
    private void loadMinimalConfig() {
        // Load only essential configuration
        try {
            GlobalConfig minimalConfig = new GlobalConfig();
            minimalConfig.debugMode = true;
            minimalConfig.serverName = "RECOVERY_MODE";
            
            plugin.getConfigManager().save(minimalConfig);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load minimal config: " + e.getMessage());
        }
    }
    
    private void restartServices() {
        // Restart essential services only
        plugin.initializeMinimalServices();
    }
}
```

---

*Next: Learn about [[Security Guidelines]] for securing your MongoDB Configs implementation.*