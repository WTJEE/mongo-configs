# Player Language Storage

Persistent storage system for managing player language preferences with MongoDB integration.

## 💾 Storage Overview

The Player Language Storage system provides persistent storage for player language preferences, including selected language, auto-detection settings, and language history.

## 📋 Core Implementation

### PlayerLanguage Entity

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "language_system")
@ConfigsCollection(collection = "player_languages")
public class PlayerLanguage {
    
    @ConfigsField
    private String playerId;
    
    @ConfigsField
    private String selectedLanguage;
    
    @ConfigsField
    private String detectedLanguage;
    
    @ConfigsField
    private String currentLanguage;
    
    @ConfigsField
    private long lastUpdated;
    
    @ConfigsField
    private boolean autoDetectEnabled = true;
    
    @ConfigsField
    private List<String> preferredLanguages = new ArrayList<>();
    
    @ConfigsField
    private Map<String, Long> languageUsage = new HashMap<>();
    
    @ConfigsField
    private long firstJoinTime;
    
    @ConfigsField
    private int languageChangeCount = 0;
    
    @ConfigsField
    private String lastChangedBy; // "player", "auto_detect", "admin"
    
    @ConfigsField
    private Map<String, Object> metadata = new HashMap<>();
    
    // Constructors
    public PlayerLanguage() {
        this.firstJoinTime = System.currentTimeMillis();
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public PlayerLanguage(String playerId) {
        this();
        this.playerId = playerId;
    }
    
    // Core methods
    public String getEffectiveLanguage() {
        if (selectedLanguage != null && !selectedLanguage.isEmpty()) {
            return selectedLanguage;
        }
        if (autoDetectEnabled && detectedLanguage != null) {
            return detectedLanguage;
        }
        return "en"; // fallback
    }
    
    public void setSelectedLanguage(String language, String changedBy) {
        this.selectedLanguage = language;
        this.currentLanguage = language;
        this.lastUpdated = System.currentTimeMillis();
        this.lastChangedBy = changedBy;
        this.languageChangeCount++;
        
        // Update usage statistics
        updateLanguageUsage(language);
    }
    
    public void setDetectedLanguage(String language) {
        this.detectedLanguage = language;
        if (autoDetectEnabled && (selectedLanguage == null || selectedLanguage.isEmpty())) {
            this.currentLanguage = language;
        }
        this.lastUpdated = System.currentTimeMillis();
        
        // Update usage statistics
        updateLanguageUsage(language);
    }
    
    private void updateLanguageUsage(String language) {
        languageUsage.put(language, System.currentTimeMillis());
        
        // Keep only recent languages in preferred list
        if (!preferredLanguages.contains(language)) {
            preferredLanguages.add(language);
        }
        
        // Limit preferred languages to 5 most recent
        if (preferredLanguages.size() > 5) {
            preferredLanguages = preferredLanguages.subList(preferredLanguages.size() - 5, preferredLanguages.size());
        }
    }
    
    public List<String> getPreferredLanguages() {
        return new ArrayList<>(preferredLanguages);
    }
    
    public long getLanguageLastUsed(String language) {
        return languageUsage.getOrDefault(language, 0L);
    }
    
    public boolean hasUsedLanguage(String language) {
        return languageUsage.containsKey(language);
    }
    
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    public void removeMetadata(String key) {
        metadata.remove(key);
    }
    
    // Getters and setters...
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    
    public String getSelectedLanguage() { return selectedLanguage; }
    
    public String getDetectedLanguage() { return detectedLanguage; }
    
    public String getCurrentLanguage() { return currentLanguage; }
    public void setCurrentLanguage(String currentLanguage) { this.currentLanguage = currentLanguage; }
    
    public long getLastUpdated() { return lastUpdated; }
    
    public boolean isAutoDetectEnabled() { return autoDetectEnabled; }
    public void setAutoDetectEnabled(boolean autoDetectEnabled) { this.autoDetectEnabled = autoDetectEnabled; }
    
    public long getFirstJoinTime() { return firstJoinTime; }
    
    public int getLanguageChangeCount() { return languageChangeCount; }
    
    public String getLastChangedBy() { return lastChangedBy; }
    
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
}
```

### PlayerLanguageStorage Manager

```java
public class PlayerLanguageStorage {
    
    private final ConfigManager configManager;
    private final Map<String, PlayerLanguage> cache = new ConcurrentHashMap<>();
    private final ExecutorService asyncLoader;
    private final Cache<String, PlayerLanguage> persistentCache;
    
    public PlayerLanguageStorage(ConfigManager configManager) {
        this.configManager = configManager;
        this.asyncLoader = Executors.newCachedThreadPool();
        
        // Initialize persistent cache
        persistentCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .removalListener(this::onCacheRemoval)
            .build();
        
        // Setup change streams for real-time updates
        setupChangeStreams();
    }
    
    private void setupChangeStreams() {
        configManager.watchCollection(PlayerLanguage.class, changeEvent -> {
            PlayerLanguage updated = changeEvent.getDocument();
            if (updated != null) {
                String playerId = updated.getPlayerId();
                
                // Update cache
                cache.put(playerId, updated);
                persistentCache.put(playerId, updated);
                
                // Notify listeners
                notifyLanguageChangeListeners(playerId, updated);
            }
        });
    }
    
    public PlayerLanguage getPlayerLanguage(String playerId) {
        // Try memory cache first
        PlayerLanguage cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }
        
        // Try persistent cache
        cached = persistentCache.getIfPresent(playerId);
        if (cached != null) {
            cache.put(playerId, cached);
            return cached;
        }
        
        // Load from database
        try {
            PlayerLanguage loaded = configManager.findFirst(PlayerLanguage.class, 
                "playerId", playerId);
            
            if (loaded == null) {
                // Create new player language entry
                loaded = new PlayerLanguage(playerId);
                savePlayerLanguage(loaded);
            }
            
            // Cache the result
            cache.put(playerId, loaded);
            persistentCache.put(playerId, loaded);
            
            return loaded;
            
        } catch (Exception e) {
            // Log error and return default
            return new PlayerLanguage(playerId);
        }
    }
    
    public CompletableFuture<PlayerLanguage> getPlayerLanguageAsync(String playerId) {
        return CompletableFuture.supplyAsync(() -> getPlayerLanguage(playerId), asyncLoader);
    }
    
    public void savePlayerLanguage(PlayerLanguage playerLanguage) {
        try {
            configManager.save(playerLanguage);
            
            // Update caches
            String playerId = playerLanguage.getPlayerId();
            cache.put(playerId, playerLanguage);
            persistentCache.put(playerId, playerLanguage);
            
        } catch (Exception e) {
            // Log error
            throw new RuntimeException("Failed to save player language", e);
        }
    }
    
    public void savePlayerLanguageAsync(PlayerLanguage playerLanguage) {
        asyncLoader.submit(() -> savePlayerLanguage(playerLanguage));
    }
    
    public void updatePlayerLanguage(String playerId, Consumer<PlayerLanguage> updater) {
        PlayerLanguage playerLanguage = getPlayerLanguage(playerId);
        updater.accept(playerLanguage);
        playerLanguage.setLastUpdated(System.currentTimeMillis());
        savePlayerLanguage(playerLanguage);
    }
    
    public void setPlayerSelectedLanguage(String playerId, String language, String changedBy) {
        updatePlayerLanguage(playerId, pl -> pl.setSelectedLanguage(language, changedBy));
    }
    
    public void setPlayerDetectedLanguage(String playerId, String language) {
        updatePlayerLanguage(playerId, pl -> pl.setDetectedLanguage(language));
    }
    
    public void setAutoDetectEnabled(String playerId, boolean enabled) {
        updatePlayerLanguage(playerId, pl -> pl.setAutoDetectEnabled(enabled));
    }
    
    public String getPlayerEffectiveLanguage(String playerId) {
        PlayerLanguage pl = getPlayerLanguage(playerId);
        return pl.getEffectiveLanguage();
    }
    
    public List<PlayerLanguage> getAllPlayerLanguages() {
        try {
            return configManager.getAll(PlayerLanguage.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    public List<PlayerLanguage> getPlayerLanguagesByLanguage(String language) {
        try {
            return configManager.find(PlayerLanguage.class, 
                Filters.or(
                    Filters.eq("selectedLanguage", language),
                    Filters.and(
                        Filters.eq("detectedLanguage", language),
                        Filters.eq("autoDetectEnabled", true)
                    )
                ));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    public void deletePlayerLanguage(String playerId) {
        try {
            PlayerLanguage playerLanguage = getPlayerLanguage(playerId);
            configManager.delete(PlayerLanguage.class, playerLanguage.getPlayerId());
            
            // Remove from caches
            cache.remove(playerId);
            persistentCache.invalidate(playerId);
            
        } catch (Exception e) {
            // Log error
        }
    }
    
    public Map<String, Integer> getLanguageUsageStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        
        for (PlayerLanguage pl : getAllPlayerLanguages()) {
            String effectiveLang = pl.getEffectiveLanguage();
            stats.merge(effectiveLang, 1, Integer::sum);
        }
        
        return stats;
    }
    
    public List<String> getMostPopularLanguages(int limit) {
        return getLanguageUsageStatistics().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    public void preloadPlayerLanguages(Collection<String> playerIds) {
        for (String playerId : playerIds) {
            asyncLoader.submit(() -> getPlayerLanguage(playerId));
        }
    }
    
    public void invalidateCache(String playerId) {
        cache.remove(playerId);
        persistentCache.invalidate(playerId);
    }
    
    public void invalidateAllCache() {
        cache.clear();
        persistentCache.invalidateAll();
    }
    
    private void onCacheRemoval(String playerId, PlayerLanguage playerLanguage, RemovalCause cause) {
        // Handle cache eviction
        if (cause == RemovalCause.EXPIRED) {
            // Could save to disk if needed
        }
    }
    
    public void shutdown() {
        asyncLoader.shutdown();
        try {
            if (!asyncLoader.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncLoader.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncLoader.shutdownNow();
        }
    }
    
    // Event listener management
    private final List<LanguageChangeListener> listeners = new CopyOnWriteArrayList<>();
    
    public void addLanguageChangeListener(LanguageChangeListener listener) {
        listeners.add(listener);
    }
    
    public void removeLanguageChangeListener(LanguageChangeListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyLanguageChangeListeners(String playerId, PlayerLanguage newLanguage) {
        for (LanguageChangeListener listener : listeners) {
            asyncLoader.submit(() -> {
                try {
                    listener.onLanguageChanged(playerId, newLanguage);
                } catch (Exception e) {
                    // Log error
                }
            });
        }
    }
    
    public interface LanguageChangeListener {
        void onLanguageChanged(String playerId, PlayerLanguage newLanguage);
    }
}
```

## 🔄 Advanced Features

### Language History Tracking

```java
public class LanguageHistoryManager {
    
    @ConfigsFileProperties
    @ConfigsDatabase(database = "language_system")
    @ConfigsCollection(collection = "language_history")
    public static class LanguageChangeEvent {
        
        @ConfigsField
        private String eventId;
        
        @ConfigsField
        private String playerId;
        
        @ConfigsField
        private String oldLanguage;
        
        @ConfigsField
        private String newLanguage;
        
        @ConfigsField
        private String changeType; // "selected", "detected", "auto"
        
        @ConfigsField
        private long timestamp;
        
        @ConfigsField
        private String changedBy;
        
        @ConfigsField
        private Map<String, Object> context = new HashMap<>();
        
        // Constructors and getters/setters...
    }
    
    private final ConfigManager configManager;
    private final PlayerLanguageStorage storage;
    
    public LanguageHistoryManager(ConfigManager configManager, PlayerLanguageStorage storage) {
        this.configManager = configManager;
        this.storage = storage;
    }
    
    public void recordLanguageChange(String playerId, String oldLanguage, String newLanguage, 
                                   String changeType, String changedBy) {
        LanguageChangeEvent event = new LanguageChangeEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setPlayerId(playerId);
        event.setOldLanguage(oldLanguage);
        event.setNewLanguage(newLanguage);
        event.setChangeType(changeType);
        event.setTimestamp(System.currentTimeMillis());
        event.setChangedBy(changedBy);
        
        try {
            configManager.save(event);
        } catch (Exception e) {
            // Log error
        }
    }
    
    public List<LanguageChangeEvent> getPlayerLanguageHistory(String playerId, int limit) {
        try {
            return configManager.find(LanguageChangeEvent.class, 
                Filters.eq("playerId", playerId))
                .stream()
                .sorted(Comparator.comparing(LanguageChangeEvent::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    public Map<String, Integer> getLanguageChangeStatistics(long sinceTimestamp) {
        Map<String, Integer> stats = new HashMap<>();
        
        try {
            List<LanguageChangeEvent> events = configManager.find(LanguageChangeEvent.class,
                Filters.gte("timestamp", sinceTimestamp));
            
            for (LanguageChangeEvent event : events) {
                stats.merge(event.getChangeType(), 1, Integer::sum);
            }
        } catch (Exception e) {
            // Log error
        }
        
        return stats;
    }
}
```

### Bulk Operations

```java
public class BulkLanguageOperations {
    
    private final ConfigManager configManager;
    private final PlayerLanguageStorage storage;
    
    public BulkLanguageOperations(ConfigManager configManager, PlayerLanguageStorage storage) {
        this.configManager = configManager;
        this.storage = storage;
    }
    
    public void migrateAllPlayersToLanguage(String targetLanguage, String reason) {
        List<PlayerLanguage> allPlayers = storage.getAllPlayerLanguages();
        
        for (PlayerLanguage player : allPlayers) {
            player.setSelectedLanguage(targetLanguage, "admin_migration");
            player.addMetadata("migration_reason", reason);
            player.addMetadata("migrated_at", System.currentTimeMillis());
            
            storage.savePlayerLanguage(player);
        }
    }
    
    public void updateAutoDetectForAllPlayers(boolean enabled) {
        List<PlayerLanguage> allPlayers = storage.getAllPlayerLanguages();
        
        for (PlayerLanguage player : allPlayers) {
            player.setAutoDetectEnabled(enabled);
            storage.savePlayerLanguage(player);
        }
    }
    
    public void cleanupOldLanguageData(long olderThanMs) {
        long cutoffTime = System.currentTimeMillis() - olderThanMs;
        
        // Find players who haven't been online recently
        List<PlayerLanguage> oldPlayers = storage.getAllPlayerLanguages().stream()
            .filter(pl -> pl.getLastUpdated() < cutoffTime)
            .collect(Collectors.toList());
        
        for (PlayerLanguage player : oldPlayers) {
            // Archive instead of delete
            archivePlayerLanguage(player);
        }
    }
    
    private void archivePlayerLanguage(PlayerLanguage player) {
        // Move to archive collection
        try {
            configManager.save(player); // This would be to an archive collection
            storage.deletePlayerLanguage(player.getPlayerId());
        } catch (Exception e) {
            // Log error
        }
    }
    
    public void exportLanguageData(Path exportPath) {
        List<PlayerLanguage> allPlayers = storage.getAllPlayerLanguages();
        
        try (BufferedWriter writer = Files.newBufferedWriter(exportPath)) {
            writer.write("player_id,selected_language,detected_language,current_language,last_updated,auto_detect,change_count\n");
            
            for (PlayerLanguage player : allPlayers) {
                writer.write(String.format("%s,%s,%s,%s,%d,%b,%d\n",
                    player.getPlayerId(),
                    player.getSelectedLanguage(),
                    player.getDetectedLanguage(),
                    player.getCurrentLanguage(),
                    player.getLastUpdated(),
                    player.isAutoDetectEnabled(),
                    player.getLanguageChangeCount()
                ));
            }
        } catch (IOException e) {
            // Log error
        }
    }
}
```

## 📊 Analytics and Reporting

### Language Analytics

```java
public class LanguageAnalytics {
    
    private final PlayerLanguageStorage storage;
    private final ConfigManager configManager;
    
    public LanguageAnalytics(PlayerLanguageStorage storage, ConfigManager configManager) {
        this.storage = storage;
        this.configManager = configManager;
    }
    
    public LanguageStatistics getLanguageStatistics() {
        List<PlayerLanguage> allPlayers = storage.getAllPlayerLanguages();
        
        LanguageStatistics stats = new LanguageStatistics();
        stats.setTotalPlayers(allPlayers.size());
        
        Map<String, Integer> languageCounts = new HashMap<>();
        Map<String, Integer> changeTypeCounts = new HashMap<>();
        
        for (PlayerLanguage player : allPlayers) {
            String effectiveLang = player.getEffectiveLanguage();
            languageCounts.merge(effectiveLang, 1, Integer::sum);
            
            if (player.getLastChangedBy() != null) {
                changeTypeCounts.merge(player.getLastChangedBy(), 1, Integer::sum);
            }
        }
        
        stats.setLanguageDistribution(languageCounts);
        stats.setChangeTypeDistribution(changeTypeCounts);
        
        return stats;
    }
    
    public PlayerEngagementMetrics getPlayerEngagementMetrics() {
        List<PlayerLanguage> allPlayers = storage.getAllPlayerLanguages();
        
        PlayerEngagementMetrics metrics = new PlayerEngagementMetrics();
        
        long totalChanges = allPlayers.stream()
            .mapToLong(PlayerLanguage::getLanguageChangeCount)
            .sum();
        metrics.setTotalLanguageChanges(totalChanges);
        
        double avgChanges = (double) totalChanges / allPlayers.size();
        metrics.setAverageChangesPerPlayer(avgChanges);
        
        long autoDetectUsers = allPlayers.stream()
            .filter(PlayerLanguage::isAutoDetectEnabled)
            .count();
        metrics.setAutoDetectUsers(autoDetectUsers);
        
        return metrics;
    }
    
    public static class LanguageStatistics {
        private int totalPlayers;
        private Map<String, Integer> languageDistribution = new HashMap<>();
        private Map<String, Integer> changeTypeDistribution = new HashMap<>();
        
        // Getters and setters...
    }
    
    public static class PlayerEngagementMetrics {
        private long totalLanguageChanges;
        private double averageChangesPerPlayer;
        private long autoDetectUsers;
        
        // Getters and setters...
    }
}
```

## 🔧 Integration Examples

### Plugin Integration

```java
public class MultilingualPlugin extends JavaPlugin {
    
    private ConfigManager configManager;
    private PlayerLanguageStorage languageStorage;
    private LanguageManager languageManager;
    
    @Override
    public void onEnable() {
        // Initialize MongoDB Configs API
        configManager = MongoConfigsAPI.createConfigManager(
            getConfig().getString("mongodb.uri"),
            getConfig().getString("mongodb.database")
        );
        
        // Initialize language storage
        languageStorage = new PlayerLanguageStorage(configManager);
        
        // Initialize language manager
        languageManager = new LanguageManager(this, languageStorage);
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerLanguageListener(this), this);
        
        // Register commands
        getCommand("lang").setExecutor(new LanguageCommand(this));
        
        getLogger().info("Multilingual Plugin enabled!");
    }
    
    public String getPlayerLanguage(Player player) {
        return languageStorage.getPlayerEffectiveLanguage(player.getUniqueId().toString());
    }
    
    public void setPlayerLanguage(Player player, String language) {
        languageStorage.setPlayerSelectedLanguage(player.getUniqueId().toString(), language, "player");
    }
    
    // Getters...
    public PlayerLanguageStorage getLanguageStorage() { return languageStorage; }
    public LanguageManager getLanguageManager() { return languageManager; }
}
```

### Event Listener

```java
public class PlayerLanguageListener implements Listener {
    
    private final MultilingualPlugin plugin;
    private final PlayerLanguageStorage storage;
    
    public PlayerLanguageListener(MultilingualPlugin plugin) {
        this.plugin = plugin;
        this.storage = plugin.getLanguageStorage();
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerId = player.getUniqueId().toString();
        
        // Load or create player language data
        PlayerLanguage playerLanguage = storage.getPlayerLanguage(playerId);
        
        // Detect language if auto-detect is enabled and no language is set
        if (playerLanguage.isAutoDetectEnabled() && 
            (playerLanguage.getSelectedLanguage() == null || playerLanguage.getSelectedLanguage().isEmpty())) {
            
            String detected = detectPlayerLanguage(player);
            if (detected != null) {
                storage.setPlayerDetectedLanguage(playerId, detected);
                playerLanguage = storage.getPlayerLanguage(playerId);
            }
        }
        
        // Send welcome message in player's language
        String language = playerLanguage.getEffectiveLanguage();
        String welcomeMessage = plugin.getMessages().getWelcomeMessage(language, player.getName());
        player.sendMessage(ColorHelper.parseComponent(welcomeMessage));
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerId = player.getUniqueId().toString();
        
        // Update last seen time
        storage.updatePlayerLanguage(playerId, pl -> {
            pl.addMetadata("last_seen", System.currentTimeMillis());
        });
    }
    
    private String detectPlayerLanguage(Player player) {
        String locale = player.getLocale();
        if (locale != null && !locale.isEmpty()) {
            return plugin.getLanguageManager().detectFromLocale(locale);
        }
        return null;
    }
}
```

---

*Next: Learn about [[Message Translation]] for dynamic message handling.*