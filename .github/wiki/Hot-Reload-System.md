# Hot Reload System

Dynamic configuration reloading system for runtime updates without server restarts, including change detection and graceful updates.

## 🔥 Hot Reload Overview

The Hot Reload System enables dynamic updates to configurations, translations, and settings without requiring server restarts, improving development workflow and user experience.

## 📋 Core Implementation

### HotReloadManager

```java
public class HotReloadManager {
    
    private final MongoConfigsPlugin plugin;
    private final ConfigManager configManager;
    private final ScheduledExecutorService reloadScheduler;
    private final Map<String, ReloadHandler> reloadHandlers = new ConcurrentHashMap<>();
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    
    private volatile long lastConfigCheck = 0;
    private volatile long lastTranslationCheck = 0;
    private final long checkIntervalMs = 5000; // 5 seconds
    
    public HotReloadManager(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.reloadScheduler = Executors.newScheduledThreadPool(2);
        
        // Register default reload handlers
        registerReloadHandler("language_config", new LanguageConfigReloadHandler());
        registerReloadHandler("translation_messages", new TranslationReloadHandler());
        registerReloadHandler("plugin_config", new PluginConfigReloadHandler());
        
        // Start automatic checking
        startAutoReload();
    }
    
    private void startAutoReload() {
        reloadScheduler.scheduleAtFixedRate(this::checkForChanges, 0, checkIntervalMs, TimeUnit.MILLISECONDS);
    }
    
    private void checkForChanges() {
        if (reloadInProgress.get()) {
            return; // Skip if reload already in progress
        }
        
        try {
            // Check for configuration file changes
            checkConfigFileChanges();
            
            // Check for database changes
            checkDatabaseChanges();
            
            // Check for translation changes
            checkTranslationChanges();
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking for changes: " + e.getMessage());
        }
    }
    
    private void checkConfigFileChanges() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        long lastModified = configFile.lastModified();
        
        if (lastModified > lastConfigCheck) {
            plugin.getLogger().info("Configuration file changed, triggering reload...");
            triggerReload("plugin_config", null);
            lastConfigCheck = lastModified;
        }
    }
    
    private void checkDatabaseChanges() {
        // Check for changes in configuration collections
        try {
            // This would require tracking last modification times
            // Implementation depends on your change tracking system
        } catch (Exception e) {
            // Log error
        }
    }
    
    private void checkTranslationChanges() {
        // Check for new or updated translations
        try {
            long latestTranslationUpdate = getLatestTranslationUpdateTime();
            if (latestTranslationUpdate > lastTranslationCheck) {
                plugin.getLogger().info("Translations updated, triggering reload...");
                triggerReload("translation_messages", null);
                lastTranslationCheck = latestTranslationUpdate;
            }
        } catch (Exception e) {
            // Log error
        }
    }
    
    private long getLatestTranslationUpdateTime() {
        // Query database for latest translation modification time
        // This is a simplified example
        return System.currentTimeMillis(); // Placeholder
    }
    
    public void triggerReload(String handlerName, Map<String, Object> context) {
        if (!reloadInProgress.compareAndSet(false, true)) {
            plugin.getLogger().info("Reload already in progress, skipping...");
            return;
        }
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                performReload(handlerName, context);
            } catch (Exception e) {
                plugin.getLogger().severe("Error during hot reload: " + e.getMessage());
            } finally {
                reloadInProgress.set(false);
            }
        });
    }
    
    private void performReload(String handlerName, Map<String, Object> context) {
        ReloadHandler handler = reloadHandlers.get(handlerName);
        if (handler == null) {
            plugin.getLogger().warning("No reload handler found for: " + handlerName);
            return;
        }
        
        plugin.getLogger().info("Starting hot reload for: " + handlerName);
        
        try {
            // Pre-reload preparations
            handler.preReload(context);
            
            // Perform the reload
            handler.reload(context);
            
            // Post-reload cleanup
            handler.postReload(context);
            
            plugin.getLogger().info("Hot reload completed successfully for: " + handlerName);
            
            // Notify players of the reload
            notifyPlayersOfReload(handlerName);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Hot reload failed for " + handlerName + ": " + e.getMessage());
            // Attempt rollback if supported
            try {
                handler.rollback(context);
            } catch (Exception rollbackException) {
                plugin.getLogger().severe("Rollback also failed: " + rollbackException.getMessage());
            }
        }
    }
    
    private void notifyPlayersOfReload(String handlerName) {
        String message = plugin.getTranslationService().translate("en", "reload.completed", handlerName);
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("mongoconfigs.admin")) {
                String playerMessage = plugin.getMessage(player, "reload.completed", handlerName);
                player.sendMessage(ColorHelper.parseComponent(playerMessage));
            }
        }
    }
    
    public void registerReloadHandler(String name, ReloadHandler handler) {
        reloadHandlers.put(name, handler);
        plugin.getLogger().info("Registered reload handler: " + name);
    }
    
    public void unregisterReloadHandler(String name) {
        reloadHandlers.remove(name);
        plugin.getLogger().info("Unregistered reload handler: " + name);
    }
    
    public boolean isReloadInProgress() {
        return reloadInProgress.get();
    }
    
    public Set<String> getRegisteredHandlers() {
        return new HashSet<>(reloadHandlers.keySet());
    }
    
    public void shutdown() {
        reloadScheduler.shutdown();
        try {
            if (!reloadScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                reloadScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            reloadScheduler.shutdownNow();
        }
    }
    
    // Reload Handler Interface
    public interface ReloadHandler {
        void preReload(Map<String, Object> context) throws Exception;
        void reload(Map<String, Object> context) throws Exception;
        void postReload(Map<String, Object> context) throws Exception;
        default void rollback(Map<String, Object> context) throws Exception {
            // Default no-op rollback
        }
    }
}
```

### Language Configuration Reload Handler

```java
public class LanguageConfigReloadHandler implements HotReloadManager.ReloadHandler {
    
    @Override
    public void preReload(Map<String, Object> context) throws Exception {
        // Backup current language configuration
        context.put("backup_config", 
            MongoConfigsPlugin.getInstance().getConfigManager()
                .get(LanguageConfig.class, "global_config"));
        
        // Notify services of impending reload
        MongoConfigsPlugin.getInstance().getLogger().info("Preparing language config reload...");
    }
    
    @Override
    public void reload(Map<String, Object> context) throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        ConfigManager configManager = plugin.getConfigManager();
        
        // Load new language configuration
        LanguageConfig newConfig = configManager.get(LanguageConfig.class, "global_config");
        if (newConfig == null) {
            throw new Exception("Failed to load language configuration");
        }
        
        // Update language manager with new config
        plugin.getLanguageManager().updateConfiguration(newConfig);
        
        // Clear related caches
        plugin.getTranslationService().invalidateAllCache();
        
        // Update supported languages list
        updateSupportedLanguages(newConfig);
        
        plugin.getLogger().info("Language configuration reloaded successfully");
    }
    
    @Override
    public void postReload(Map<String, Object> context) throws Exception {
        // Validate the reload
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        // Check if all supported languages are still available
        LanguageConfig config = plugin.getConfigManager().get(LanguageConfig.class, "global_config");
        for (String language : config.getSupportedLanguages()) {
            if (!plugin.getTranslationManager().hasTranslationsForLanguage(language)) {
                plugin.getLogger().warning("Missing translations for language: " + language);
            }
        }
        
        // Update online players with new language settings
        updateOnlinePlayers();
    }
    
    @Override
    public void rollback(Map<String, Object> context) throws Exception {
        LanguageConfig backupConfig = (LanguageConfig) context.get("backup_config");
        if (backupConfig != null) {
            MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
            plugin.getConfigManager().save(backupConfig);
            plugin.getLanguageManager().updateConfiguration(backupConfig);
            
            plugin.getLogger().info("Language config rollback completed");
        }
    }
    
    private void updateSupportedLanguages(LanguageConfig config) {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        // Update language manager's supported languages
        plugin.getLanguageManager().setSupportedLanguages(config.getSupportedLanguages());
        
        // Validate that default language is still supported
        if (!config.getSupportedLanguages().contains(config.getDefaultLanguage())) {
            plugin.getLogger().warning("Default language '" + config.getDefaultLanguage() + 
                "' is not in supported languages list");
        }
    }
    
    private void updateOnlinePlayers() {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        // Update language settings for online players
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            String playerId = player.getUniqueId().toString();
            String currentLanguage = plugin.getLanguageStorage().getPlayerEffectiveLanguage(playerId);
            
            // Re-validate player's language preference
            if (!plugin.getLanguageManager().isLanguageSupported(currentLanguage)) {
                // Reset to default language
                plugin.getLanguageStorage().setPlayerSelectedLanguage(playerId, 
                    plugin.getLanguageManager().getDefaultLanguage(), "config_reload");
                
                String message = plugin.getMessage(player, "language.reset_due_to_config_change");
                player.sendMessage(ColorHelper.parseComponent(message));
            }
        }
    }
}
```

### Translation Reload Handler

```java
public class TranslationReloadHandler implements HotReloadManager.ReloadHandler {
    
    @Override
    public void preReload(Map<String, Object> context) throws Exception {
        // Cache current translation statistics
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        Map<String, Integer> stats = plugin.getTranslationManager().getTranslationStatistics();
        context.put("translation_stats", stats);
        
        plugin.getLogger().info("Preparing translation reload...");
    }
    
    @Override
    public void reload(Map<String, Object> context) throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        // Clear translation cache
        plugin.getTranslationService().invalidateAllCache();
        
        // Reload translation templates
        plugin.getTranslationManager().reloadAllTranslations();
        
        // Validate translations
        validateTranslations();
        
        plugin.getLogger().info("Translation reload completed");
    }
    
    @Override
    public void postReload(Map<String, Object> context) throws Exception {
        // Compare translation statistics
        @SuppressWarnings("unchecked")
        Map<String, Integer> oldStats = (Map<String, Integer>) context.get("translation_stats");
        Map<String, Integer> newStats = MongoConfigsPlugin.getInstance()
            .getTranslationManager().getTranslationStatistics();
        
        // Log changes
        logTranslationChanges(oldStats, newStats);
        
        // Notify administrators of significant changes
        notifySignificantChanges(oldStats, newStats);
    }
    
    private void validateTranslations() throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        List<String> criticalKeys = Arrays.asList("error", "welcome", "goodbye");
        
        for (String language : plugin.getLanguageManager().getSupportedLanguages()) {
            for (String key : criticalKeys) {
                String translation = plugin.getTranslationService().translate(language, key);
                if (translation.equals(key)) {
                    throw new Exception("Missing critical translation: " + key + " for language: " + language);
                }
            }
        }
    }
    
    private void logTranslationChanges(Map<String, Integer> oldStats, Map<String, Integer> newStats) {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        for (String language : newStats.keySet()) {
            int oldCount = oldStats.getOrDefault(language, 0);
            int newCount = newStats.get(language);
            int difference = newCount - oldCount;
            
            if (difference != 0) {
                plugin.getLogger().info("Translation count for " + language + ": " + 
                    oldCount + " -> " + newCount + " (" + 
                    (difference > 0 ? "+" : "") + difference + ")");
            }
        }
    }
    
    private void notifySignificantChanges(Map<String, Integer> oldStats, Map<String, Integer> newStats) {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        for (String language : newStats.keySet()) {
            int oldCount = oldStats.getOrDefault(language, 0);
            int newCount = newStats.get(language);
            
            // Notify if translations decreased significantly
            if (oldCount > 0 && newCount < oldCount * 0.8) {
                String message = "Significant translation loss for " + language + ": " + 
                    oldCount + " -> " + newCount;
                
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.hasPermission("mongoconfigs.admin")) {
                        player.sendMessage(ColorHelper.parseComponent("&c[WARNING] " + message));
                    }
                }
            }
        }
    }
}
```

### Plugin Configuration Reload Handler

```java
public class PluginConfigReloadHandler implements HotReloadManager.ReloadHandler {
    
    @Override
    public void preReload(Map<String, Object> context) throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        // Backup current configuration values
        Map<String, Object> backup = new HashMap<>();
        backup.put("mongodb.uri", plugin.getConfig().getString("mongodb.uri"));
        backup.put("mongodb.database", plugin.getConfig().getString("mongodb.database"));
        backup.put("language.default-language", plugin.getConfig().getString("language.default-language"));
        
        context.put("config_backup", backup);
        
        plugin.getLogger().info("Backing up current configuration...");
    }
    
    @Override
    public void reload(Map<String, Object> context) throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        // Reload the configuration file
        plugin.reloadConfig();
        
        // Validate configuration
        validateConfiguration();
        
        // Update services with new configuration
        updateServices();
        
        plugin.getLogger().info("Plugin configuration reloaded");
    }
    
    @Override
    public void postReload(Map<String, Object> context) throws Exception {
        // Verify that services are working with new configuration
        verifyServices();
        
        // Log configuration changes
        logConfigurationChanges(context);
    }
    
    @Override
    public void rollback(Map<String, Object> context) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> backup = (Map<String, Object>) context.get("config_backup");
        if (backup != null) {
            MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
            
            // Restore configuration values
            for (Map.Entry<String, Object> entry : backup.entrySet()) {
                plugin.getConfig().set(entry.getKey(), entry.getValue());
            }
            plugin.saveConfig();
            
            plugin.getLogger().info("Configuration rollback completed");
        }
    }
    
    private void validateConfiguration() throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        // Validate MongoDB URI
        String mongoUri = plugin.getConfig().getString("mongodb.uri");
        if (mongoUri == null || mongoUri.trim().isEmpty()) {
            throw new Exception("MongoDB URI cannot be empty");
        }
        
        // Validate database name
        String database = plugin.getConfig().getString("mongodb.database");
        if (database == null || database.trim().isEmpty()) {
            throw new Exception("Database name cannot be empty");
        }
        
        // Validate default language
        String defaultLang = plugin.getConfig().getString("language.default-language");
        if (defaultLang == null || defaultLang.trim().isEmpty()) {
            throw new Exception("Default language cannot be empty");
        }
    }
    
    private void updateServices() throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        // Update MongoDB connection if URI changed
        String newUri = plugin.getConfig().getString("mongodb.uri");
        String currentUri = plugin.getConfigManager().getConnectionString();
        
        if (!newUri.equals(currentUri)) {
            plugin.getLogger().info("MongoDB URI changed, reconnecting...");
            plugin.reconnectMongoDB(newUri);
        }
        
        // Update language settings
        String newDefaultLang = plugin.getConfig().getString("language.default-language");
        plugin.getLanguageManager().setDefaultLanguage(newDefaultLang);
        
        // Update cache settings
        updateCacheSettings();
        
        // Update performance settings
        updatePerformanceSettings();
    }
    
    private void updateCacheSettings() {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        int cacheExpiry = plugin.getConfig().getInt("translation.cache-expiry-minutes", 60);
        plugin.getTranslationService().setCacheExpiryMinutes(cacheExpiry);
        
        int languageCacheExpiry = plugin.getConfig().getInt("language.cache-expiry-minutes", 30);
        plugin.getLanguageStorage().setCacheExpiryMinutes(languageCacheExpiry);
    }
    
    private void updatePerformanceSettings() {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        boolean asyncOps = plugin.getConfig().getBoolean("performance.async-operations", true);
        plugin.setAsyncOperationsEnabled(asyncOps);
        
        int batchSize = plugin.getConfig().getInt("performance.batch-size", 100);
        plugin.setBatchSize(batchSize);
    }
    
    private void verifyServices() throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        // Test MongoDB connection
        if (!plugin.getConfigManager().testConnection()) {
            throw new Exception("MongoDB connection test failed");
        }
        
        // Test translation service
        String testTranslation = plugin.getTranslationService().translate("en", "test");
        if (testTranslation == null) {
            throw new Exception("Translation service test failed");
        }
        
        // Test language service
        if (!plugin.getLanguageManager().isLanguageSupported("en")) {
            throw new Exception("Language service test failed");
        }
    }
    
    private void logConfigurationChanges(Map<String, Object> context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> backup = (Map<String, Object>) context.get("config_backup");
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        for (String key : backup.keySet()) {
            Object oldValue = backup.get(key);
            Object newValue = plugin.getConfig().get(key);
            
            if (!Objects.equals(oldValue, newValue)) {
                plugin.getLogger().info("Config changed: " + key + " = " + oldValue + " -> " + newValue);
            }
        }
    }
}
```

## 🔄 Change Detection

### File Change Monitor

```java
public class FileChangeMonitor {
    
    private final Path monitoredDirectory;
    private final WatchService watchService;
    private final Map<Path, Long> fileModificationTimes = new ConcurrentHashMap<>();
    private final ExecutorService monitorExecutor;
    private volatile boolean monitoring = false;
    
    public FileChangeMonitor(Path monitoredDirectory) throws IOException {
        this.monitoredDirectory = monitoredDirectory;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.monitorExecutor = Executors.newSingleThreadExecutor();
        
        // Register directory for monitoring
        monitoredDirectory.register(watchService, 
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE, 
            StandardWatchEventKinds.ENTRY_MODIFY);
        
        // Initialize modification times
        initializeFileModificationTimes();
    }
    
    private void initializeFileModificationTimes() throws IOException {
        Files.walk(monitoredDirectory)
            .filter(Files::isRegularFile)
            .forEach(path -> {
                try {
                    fileModificationTimes.put(path, Files.getLastModifiedTime(path).toMillis());
                } catch (IOException e) {
                    // Log error
                }
            });
    }
    
    public void startMonitoring(Consumer<Path> changeHandler) {
        if (monitoring) {
            throw new IllegalStateException("Already monitoring");
        }
        
        monitoring = true;
        monitorExecutor.submit(() -> monitorChanges(changeHandler));
    }
    
    private void monitorChanges(Consumer<Path> changeHandler) {
        try {
            while (monitoring) {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path changedFile = monitoredDirectory.resolve(filename);
                    
                    // Handle the change
                    handleFileChange(changedFile, kind, changeHandler);
                }
                
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Log error
        }
    }
    
    private void handleFileChange(Path filePath, WatchEvent.Kind<?> kind, Consumer<Path> changeHandler) {
        try {
            if (Files.isRegularFile(filePath)) {
                long currentTime = Files.getLastModifiedTime(filePath).toMillis();
                Long lastTime = fileModificationTimes.get(filePath);
                
                // Check if this is a real modification (not just access time change)
                if (lastTime == null || currentTime > lastTime + 1000) { // 1 second threshold
                    fileModificationTimes.put(filePath, currentTime);
                    
                    // Notify change handler
                    changeHandler.accept(filePath);
                }
            }
        } catch (IOException e) {
            // Log error
        }
    }
    
    public void stopMonitoring() {
        monitoring = false;
        monitorExecutor.shutdown();
        
        try {
            if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorExecutor.shutdownNow();
        }
    }
    
    public boolean isMonitoring() {
        return monitoring;
    }
}
```

### Database Change Monitor

```java
public class DatabaseChangeMonitor {
    
    private final ConfigManager configManager;
    private final Map<String, Consumer<Document>> changeHandlers = new ConcurrentHashMap<>();
    private final Map<String, MongoCursor<ChangeStreamDocument<Document>>> activeCursors = new ConcurrentHashMap<>();
    private final ExecutorService changeProcessor;
    
    public DatabaseChangeMonitor(ConfigManager configManager) {
        this.configManager = configManager;
        this.changeProcessor = Executors.newCachedThreadPool();
    }
    
    public void monitorCollection(String collectionName, Consumer<Document> changeHandler) {
        changeHandlers.put(collectionName, changeHandler);
        
        // Start monitoring in background
        changeProcessor.submit(() -> startCollectionMonitoring(collectionName));
    }
    
    private void startCollectionMonitoring(String collectionName) {
        try {
            MongoCursor<ChangeStreamDocument<Document>> cursor = 
                configManager.watchCollection(collectionName, changeEvent -> {
                    Document document = changeEvent.getDocument();
                    if (document != null) {
                        Consumer<Document> handler = changeHandlers.get(collectionName);
                        if (handler != null) {
                            changeProcessor.submit(() -> {
                                try {
                                    handler.accept(document);
                                } catch (Exception e) {
                                    // Log error
                                }
                            });
                        }
                    }
                });
            
            activeCursors.put(collectionName, cursor);
            
        } catch (Exception e) {
            // Log error and retry
            changeProcessor.schedule(() -> startCollectionMonitoring(collectionName), 5, TimeUnit.SECONDS);
        }
    }
    
    public void stopMonitoring(String collectionName) {
        MongoCursor<ChangeStreamDocument<Document>> cursor = activeCursors.remove(collectionName);
        if (cursor != null) {
            cursor.close();
        }
        changeHandlers.remove(collectionName);
    }
    
    public void stopAllMonitoring() {
        for (MongoCursor<ChangeStreamDocument<Document>> cursor : activeCursors.values()) {
            cursor.close();
        }
        activeCursors.clear();
        changeHandlers.clear();
    }
    
    public Set<String> getMonitoredCollections() {
        return new HashSet<>(changeHandlers.keySet());
    }
    
    public void shutdown() {
        stopAllMonitoring();
        changeProcessor.shutdown();
        
        try {
            if (!changeProcessor.awaitTermination(10, TimeUnit.SECONDS)) {
                changeProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            changeProcessor.shutdownNow();
        }
    }
}
```

## 🎯 Reload Strategies

### Graceful Reload Strategy

```java
public class GracefulReloadStrategy implements HotReloadManager.ReloadHandler {
    
    private final MongoConfigsPlugin plugin;
    private final long gracePeriodMs;
    private final Map<String, ReloadPhase> reloadPhases = new LinkedHashMap<>();
    
    public GracefulReloadStrategy(MongoConfigsPlugin plugin, long gracePeriodMs) {
        this.plugin = plugin;
        this.gracePeriodMs = gracePeriodMs;
        
        // Define reload phases
        reloadPhases.put("prepare", this::preparePhase);
        reloadPhases.put("pause_services", this::pauseServicesPhase);
        reloadPhases.put("reload_config", this::reloadConfigPhase);
        reloadPhases.put("resume_services", this::resumeServicesPhase);
        reloadPhases.put("validate", this::validatePhase);
    }
    
    @Override
    public void preReload(Map<String, Object> context) throws Exception {
        // Notify players of impending reload
        broadcastMessage("reload.starting_soon", gracePeriodMs / 1000);
        
        // Wait for grace period
        Thread.sleep(gracePeriodMs);
        
        // Prepare context
        context.put("start_time", System.currentTimeMillis());
        context.put("affected_players", getOnlinePlayerCount());
    }
    
    @Override
    public void reload(Map<String, Object> context) throws Exception {
        long reloadStartTime = System.currentTimeMillis();
        
        for (Map.Entry<String, ReloadPhase> phase : reloadPhases.entrySet()) {
            String phaseName = phase.getKey();
            ReloadPhase phaseAction = phase.getValue();
            
            plugin.getLogger().info("Executing reload phase: " + phaseName);
            
            try {
                phaseAction.execute(context);
                plugin.getLogger().info("Phase " + phaseName + " completed successfully");
            } catch (Exception e) {
                plugin.getLogger().severe("Phase " + phaseName + " failed: " + e.getMessage());
                throw e;
            }
        }
        
        long reloadDuration = System.currentTimeMillis() - reloadStartTime;
        context.put("reload_duration", reloadDuration);
    }
    
    @Override
    public void postReload(Map<String, Object> context) throws Exception {
        // Broadcast reload completion
        Long duration = (Long) context.get("reload_duration");
        broadcastMessage("reload.completed", duration);
        
        // Log reload statistics
        logReloadStatistics(context);
        
        // Send detailed report to administrators
        sendAdminReport(context);
    }
    
    private void preparePhase(Map<String, Object> context) {
        // Save current state
        context.put("services_state", captureServicesState());
        
        // Prepare temporary storage for pending operations
        context.put("pending_operations", new ArrayList<>());
    }
    
    private void pauseServicesPhase(Map<String, Object> context) {
        // Pause non-critical services
        plugin.getMetricsService().pause();
        
        // Wait for ongoing operations to complete
        waitForOperationsToComplete();
        
        // Disable new operations
        plugin.setAcceptingOperations(false);
    }
    
    private void reloadConfigPhase(Map<String, Object> context) {
        // Perform the actual configuration reload
        plugin.reloadConfiguration();
        
        // Update services with new configuration
        plugin.updateServicesWithNewConfig();
    }
    
    private void resumeServicesPhase(Map<String, Object> context) {
        // Re-enable operations
        plugin.setAcceptingOperations(true);
        
        // Resume services
        plugin.getMetricsService().resume();
        
        // Process any operations that were queued during reload
        processPendingOperations(context);
    }
    
    private void validatePhase(Map<String, Object> context) {
        // Validate that everything is working
        if (!plugin.validateConfiguration()) {
            throw new Exception("Configuration validation failed");
        }
        
        if (!plugin.testServices()) {
            throw new Exception("Service validation failed");
        }
    }
    
    private void broadcastMessage(String key, Object... args) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            String message = plugin.getMessage(player, key, args);
            player.sendMessage(ColorHelper.parseComponent(message));
        }
    }
    
    private int getOnlinePlayerCount() {
        return plugin.getServer().getOnlinePlayers().size();
    }
    
    private Object captureServicesState() {
        // Capture current state of all services
        Map<String, Object> state = new HashMap<>();
        state.put("metrics_enabled", plugin.getMetricsService().isRunning());
        state.put("language_service_enabled", plugin.getLanguageService().isRunning());
        return state;
    }
    
    private void waitForOperationsToComplete() {
        // Wait for ongoing operations with timeout
        long timeout = System.currentTimeMillis() + 30000; // 30 seconds
        
        while (plugin.hasOngoingOperations() && System.currentTimeMillis() < timeout) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void processPendingOperations(Map<String, Object> context) {
        @SuppressWarnings("unchecked")
        List<Runnable> pendingOps = (List<Runnable>) context.get("pending_operations");
        
        for (Runnable operation : pendingOps) {
            plugin.getServer().getScheduler().runTask(plugin, operation);
        }
    }
    
    private void logReloadStatistics(Map<String, Object> context) {
        Long startTime = (Long) context.get("start_time");
        Long duration = (Long) context.get("reload_duration");
        Integer affectedPlayers = (Integer) context.get("affected_players");
        
        plugin.getLogger().info("=== Reload Statistics ===");
        plugin.getLogger().info("Duration: " + duration + "ms");
        plugin.getLogger().info("Affected players: " + affectedPlayers);
        plugin.getLogger().info("Start time: " + new Date(startTime));
        plugin.getLogger().info("End time: " + new Date(startTime + duration));
    }
    
    private void sendAdminReport(Map<String, Object> context) {
        StringBuilder report = new StringBuilder();
        report.append("&6=== Hot Reload Report ===\n");
        report.append("&fDuration: &e").append(context.get("reload_duration")).append("ms\n");
        report.append("&fAffected players: &e").append(context.get("affected_players")).append("\n");
        report.append("&fStatus: &aSuccessful");
        
        String reportMessage = report.toString();
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("mongoconfigs.admin")) {
                player.sendMessage(ColorHelper.parseComponent(reportMessage));
            }
        }
    }
    
    @FunctionalInterface
    public interface ReloadPhase {
        void execute(Map<String, Object> context) throws Exception;
    }
}
```

## 🔧 Integration Examples

### Reload Command

```java
public class ReloadCommand implements CommandExecutor {
    
    private final MongoConfigsPlugin plugin;
    private final HotReloadManager reloadManager;
    
    public ReloadCommand(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.reloadManager = plugin.getHotReloadManager();
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
        
        String reloadType = args[0].toLowerCase();
        
        switch (reloadType) {
            case "config":
                return handleConfigReload(sender);
            case "translations":
                return handleTranslationsReload(sender);
            case "all":
                return handleFullReload(sender);
            case "status":
                return handleStatusCheck(sender);
            default:
                sendUsage(sender);
                return true;
        }
    }
    
    private boolean handleConfigReload(CommandSender sender) {
        if (reloadManager.isReloadInProgress()) {
            sender.sendMessage(ColorHelper.parseComponent("&cA reload is already in progress!"));
            return true;
        }
        
        sender.sendMessage(ColorHelper.parseComponent("&aStarting configuration reload..."));
        reloadManager.triggerReload("plugin_config", Map.of("requester", sender.getName()));
        
        return true;
    }
    
    private boolean handleTranslationsReload(CommandSender sender) {
        if (reloadManager.isReloadInProgress()) {
            sender.sendMessage(ColorHelper.parseComponent("&cA reload is already in progress!"));
            return true;
        }
        
        sender.sendMessage(ColorHelper.parseComponent("&aStarting translations reload..."));
        reloadManager.triggerReload("translation_messages", Map.of("requester", sender.getName()));
        
        return true;
    }
    
    private boolean handleFullReload(CommandSender sender) {
        if (reloadManager.isReloadInProgress()) {
            sender.sendMessage(ColorHelper.parseComponent("&cA reload is already in progress!"));
            return true;
        }
        
        sender.sendMessage(ColorHelper.parseComponent("&aStarting full reload..."));
        
        // Trigger multiple reloads in sequence
        Map<String, Object> context = Map.of("requester", sender.getName(), "full_reload", true);
        reloadManager.triggerReload("plugin_config", context);
        
        // Schedule other reloads after the first completes
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            reloadManager.triggerReload("translation_messages", context);
        }, 40L); // 2 seconds delay
        
        return true;
    }
    
    private boolean handleStatusCheck(CommandSender sender) {
        boolean inProgress = reloadManager.isReloadInProgress();
        Set<String> handlers = reloadManager.getRegisteredHandlers();
        
        sender.sendMessage(ColorHelper.parseComponent("&6=== Reload Status ==="));
        sender.sendMessage(ColorHelper.parseComponent("&fIn progress: &e" + inProgress));
        sender.sendMessage(ColorHelper.parseComponent("&fRegistered handlers: &e" + handlers.size()));
        
        for (String handler : handlers) {
            sender.sendMessage(ColorHelper.parseComponent("&f- " + handler));
        }
        
        return true;
    }
    
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&6Hot Reload Commands:"));
        sender.sendMessage(ColorHelper.parseComponent("&f/mongoreload config &7- Reload plugin configuration"));
        sender.sendMessage(ColorHelper.parseComponent("&f/mongoreload translations &7- Reload translations"));
        sender.sendMessage(ColorHelper.parseComponent("&f/mongoreload all &7- Reload everything"));
        sender.sendMessage(ColorHelper.parseComponent("&f/mongoreload status &7- Check reload status"));
    }
}
```

### Automatic Reload Configuration

```yaml
# config.yml
hot_reload:
  enabled: true
  check_interval_seconds: 5
  file_monitoring: true
  database_monitoring: true
  graceful_reload: true
  grace_period_seconds: 10
  auto_reload_on_file_change: true
  auto_reload_on_db_change: true
  notify_players: true
  admin_notifications: true
  reload_timeout_seconds: 60
  backup_before_reload: true
  rollback_on_failure: true
```

---

*Next: Learn about [[Error Handling]] for robust error management and recovery.*