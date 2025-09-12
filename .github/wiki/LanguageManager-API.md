# LanguageManager API

Complete reference for the LanguageManager - the interface for managing multilingual player preferences and language data.

## 🎯 Getting LanguageManager Instance

```java
// Get the global LanguageManager instance
LanguageManager lm = MongoConfigsAPI.getLanguageManager();
```

---

## 👤 Player Language Management

### Setting Player Language

#### `setPlayerLanguage(String playerId, String language)`
Sets the language preference for a specific player (synchronous).

```java
// Set player language
String playerId = player.getUniqueId().toString();
lm.setPlayerLanguage(playerId, "pl");  // Polish
lm.setPlayerLanguage(playerId, "en");  // English
lm.setPlayerLanguage(playerId, "es");  // Spanish

// With validation
if (lm.isLanguageSupported("fr")) {
    lm.setPlayerLanguage(playerId, "fr");
    player.sendMessage("§a✅ Language set to French!");
} else {
    player.sendMessage("§c❌ French is not supported yet.");
}
```

#### `setPlayerLanguage(UUID playerId, String language)`
Sets the language preference for a specific player using UUID (asynchronous).

```java
// Async language setting with UUID
CompletableFuture<Void> languageFuture = lm.setPlayerLanguage(player.getUniqueId(), "de");

languageFuture.thenRun(() -> {
    player.sendMessage("§a✅ Language updated successfully!");
}).exceptionally(error -> {
    player.sendMessage("§c❌ Failed to update language: " + error.getMessage());
    return null;
});
```

#### `setPlayerLanguageAsync(String playerId, String language)`
Sets the language preference for a specific player asynchronously using String ID.

```java
// Async language setting with String ID
String playerId = player.getUniqueId().toString();
CompletableFuture<Void> languageFuture = lm.setPlayerLanguageAsync(playerId, "de");

languageFuture.thenRun(() -> {
    player.sendMessage("§a✅ Language updated successfully!");
}).exceptionally(error -> {
    player.sendMessage("§c❌ Failed to update language: " + error.getMessage());
    return null;
});
```

### Getting Player Language

#### `getPlayerLanguage(String playerId)`
Retrieves the language preference for a specific player.

```java
// Get player language
String playerId = player.getUniqueId().toString();
String playerLang = lm.getPlayerLanguage(playerId);

if (playerLang != null) {
    getLogger().info("Player " + player.getName() + " uses: " + playerLang);
} else {
    // Player has no language set, use default
    String defaultLang = lm.getDefaultLanguage();
    getLogger().info("Player " + player.getName() + " using default: " + defaultLang);
}
```

#### `getPlayerLanguageOrDefault(String playerId)`
Gets player language or returns the default if not set.

```java
// Get with automatic fallback to default
String playerId = player.getUniqueId().toString();
String language = lm.getPlayerLanguageOrDefault(playerId);

// This will never return null - always returns a valid language
getLogger().info("Player language: " + language);
```

### Bulk Player Operations

```java
public class PlayerLanguageManager {
    
    private final LanguageManager lm;
    
    public PlayerLanguageManager() {
        this.lm = MongoConfigsAPI.getLanguageManager();
    }
    
    public void setLanguageForGroup(List<Player> players, String language) {
        // Batch set language for multiple players
        for (Player player : players) {
            String playerId = player.getUniqueId().toString();
            lm.setPlayerLanguageAsync(playerId, language)
                .thenRun(() -> {
                    // Send confirmation in their new language
                    Messages messages = MongoConfigsAPI.getConfigManager().messagesOf(GuiMessages.class);
                    String message = messages.get(language, "language.changed");
                    player.sendMessage(ColorHelper.parseComponent(message));
                });
        }
    }
    
    public Map<String, Integer> getLanguageStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        
        // Get all online players and their languages
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerId = player.getUniqueId().toString();
            String language = lm.getPlayerLanguageOrDefault(playerId);
            stats.merge(language, 1, Integer::sum);
        }
        
        return stats;
    }
    
    public void migratePlayerLanguages(String fromLanguage, String toLanguage) {
        // Bulk migration (would need additional methods in the API)
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerId = player.getUniqueId().toString();
            String currentLang = lm.getPlayerLanguage(playerId);
            
            if (fromLanguage.equals(currentLang)) {
                lm.setPlayerLanguageAsync(playerId, toLanguage);
            }
        }
    }
}
```

---

## 🌍 Language Information

### Default Language

#### `getDefaultLanguage()`
Gets the server's default language.

```java
// Get current default language
String defaultLang = lm.getDefaultLanguage();
getLogger().info("Server default language: " + defaultLang);

// Use default language for new players
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    String playerId = player.getUniqueId().toString();
    String playerLang = lm.getPlayerLanguage(playerId);
    
    if (playerLang == null) {
        // New player - set to default
        String defaultLang = lm.getDefaultLanguage();
        lm.setPlayerLanguageAsync(playerId, defaultLang);
        
        // Welcome message in default language
        Messages messages = MongoConfigsAPI.getConfigManager().messagesOf(GuiMessages.class);
        String welcomeMsg = messages.get(defaultLang, "welcome.first_join", player.getName());
        player.sendMessage(welcomeMsg);
    }
}
```

### Supported Languages

#### `getSupportedLanguages()`
Gets all languages supported by the server.

```java
// Get all supported languages
String[] supportedLanguages = lm.getSupportedLanguages();  // Returns String[] not Set<String>

getLogger().info("Supported languages: " + String.join(", ", supportedLanguages));

// Display in GUI
public void showLanguageSelection(Player player) {
    String[] languages = lm.getSupportedLanguages();
    
    if (languages.length == 0) {
        player.sendMessage("§c❌ No languages configured!");
        return;
    }
    
    if (languages.length == 1) {
        player.sendMessage("§e⚠️ Only one language available: " + languages[0]);
        return;
    }
    
    // Open language selection GUI
    LanguageSelectionGUI gui = new LanguageSelectionGUI(player, languageManager, config);
    gui.open();
}
```

#### `isLanguageSupported(String language)`
Checks if a specific language is supported.

```java
// Validate language before setting
public boolean setPlayerLanguageSafely(Player player, String language) {
    if (!lm.isLanguageSupported(language)) {
        player.sendMessage("§c❌ Language '" + language + "' is not supported!");
        
        // Show available languages
        Set<String> supported = lm.getSupportedLanguages();
        player.sendMessage("§e📋 Available languages: " + String.join(", ", supported));
        return false;
    }
    
    String playerId = player.getUniqueId().toString();
    lm.setPlayerLanguage(playerId, language);
    player.sendMessage("§a✅ Language set to: " + language);
    return true;
}

// Command with language validation
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
        sender.sendMessage("§c❌ Only players can use this command!");
        return true;
    }
    
    if (args.length != 1) {
        sender.sendMessage("§c❌ Usage: /setlang <language>");
        
        // Show supported languages
        Set<String> supported = lm.getSupportedLanguages();
        sender.sendMessage("§e📋 Available: " + String.join(", ", supported));
        return true;
    }
    
    String language = args[0].toLowerCase();
    return setPlayerLanguageSafely(player, language);
}
```

#### `addSupportedLanguage(String language)`
Adds a new supported language.

```java
// Add new language support
lm.addSupportedLanguage("fr");  // French
lm.addSupportedLanguage("de");  // German
lm.addSupportedLanguage("jp");  // Japanese

getLogger().info("Added support for new languages");

// Dynamic language addition
public void addLanguageWithValidation(String language, CommandSender sender) {
    if (lm.isLanguageSupported(language)) {
        sender.sendMessage("§e⚠️ Language '" + language + "' is already supported!");
        return;
    }
    
    // Validate language code format
    if (!language.matches("^[a-z]{2}$")) {
        sender.sendMessage("§c❌ Invalid language code! Use 2-letter codes like 'en', 'pl', 'fr'");
        return;
    }
    
    lm.addSupportedLanguage(language);
    sender.sendMessage("§a✅ Added support for language: " + language);
    
    // Broadcast to admins
    for (Player player : Bukkit.getOnlinePlayers()) {
        if (player.hasPermission("mongoconfigs.admin.notify")) {
            player.sendMessage("§a📢 New language added: " + language);
        }
    }
}
```

#### `removeSupportedLanguage(String language)`
Removes a supported language.

```java
// Remove language support
boolean removed = lm.removeSupportedLanguage("de");

if (removed) {
    getLogger().info("Removed support for German");
    
    // Handle players using this language
    String defaultLang = lm.getDefaultLanguage();
    for (Player player : Bukkit.getOnlinePlayers()) {
        String playerId = player.getUniqueId().toString();
        String playerLang = lm.getPlayerLanguage(playerId);
        
        if ("de".equals(playerLang)) {
            lm.setPlayerLanguage(playerId, defaultLang);
            player.sendMessage("§e⚠️ Your language was changed to " + defaultLang + " (German support removed)");
        }
    }
} else {
    getLogger().warning("Failed to remove language support for German");
}
```

---

## 📊 Language Configuration Management

### `getLanguageConfiguration()`
Gets the current language configuration object.

```java
// Get language configuration
LanguageConfiguration config = lm.getLanguageConfiguration();

if (config != null) {
    getLogger().info("Default language: " + config.getDefaultLanguage());
    getLogger().info("Supported languages: " + config.getSupportedLanguages());
    getLogger().info("Auto-detect enabled: " + config.isAutoDetectEnabled());
} else {
    getLogger().warning("Language configuration not loaded!");
}
```

### `setLanguageConfiguration(LanguageConfiguration config)`
Updates the language configuration.

```java
// Update language configuration
LanguageConfiguration config = lm.getLanguageConfiguration();

if (config != null) {
    // Modify configuration
    config.setDefaultLanguage("en");
    config.getSupportedLanguages().add("fr");
    config.getSupportedLanguages().add("de");
    config.setAutoDetectEnabled(true);
    
    // Save changes
    lm.setLanguageConfiguration(config);
    getLogger().info("Language configuration updated");
}

// Create new configuration
LanguageConfiguration newConfig = new LanguageConfiguration();
newConfig.setDefaultLanguage("en");
newConfig.setSupportedLanguages(Set.of("en", "pl", "fr", "de", "es"));
newConfig.setAutoDetectEnabled(false);

lm.setLanguageConfiguration(newConfig);
```

### Advanced Configuration Management

```java
public class LanguageConfigManager {
    
    private final LanguageManager lm;
    
    public LanguageConfigManager() {
        this.lm = MongoConfigsAPI.getLanguageManager();
    }
    
    public void initializeDefaultConfiguration() {
        LanguageConfiguration config = new LanguageConfiguration();
        
        // Set default language
        config.setDefaultLanguage("en");
        
        // Add commonly supported languages
        Set<String> languages = new HashSet<>();
        languages.add("en");  // English
        languages.add("pl");  // Polish
        languages.add("es");  // Spanish
        languages.add("fr");  // French
        languages.add("de");  // German
        config.setSupportedLanguages(languages);
        
        // Enable auto-detection based on client locale
        config.setAutoDetectEnabled(true);
        
        lm.setLanguageConfiguration(config);
        getLogger().info("Initialized default language configuration");
    }
    
    public void addLanguagePackage(String language, String displayName, boolean setAsDefault) {
        LanguageConfiguration config = lm.getLanguageConfiguration();
        
        if (config == null) {
            initializeDefaultConfiguration();
            config = lm.getLanguageConfiguration();
        }
        
        // Add to supported languages
        config.getSupportedLanguages().add(language);
        
        // Set as default if requested
        if (setAsDefault) {
            config.setDefaultLanguage(language);
        }
        
        lm.setLanguageConfiguration(config);
        
        getLogger().info("Added language package: " + displayName + " (" + language + ")");
        
        // Broadcast to online admins
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("mongoconfigs.admin.notify")) {
                player.sendMessage("§a📦 New language package: " + displayName);
            }
        }
    }
    
    public void removeLanguagePackage(String language) {
        if (lm.getDefaultLanguage().equals(language)) {
            getLogger().warning("Cannot remove default language: " + language);
            return;
        }
        
        LanguageConfiguration config = lm.getLanguageConfiguration();
        if (config != null && config.getSupportedLanguages().remove(language)) {
            lm.setLanguageConfiguration(config);
            
            // Migrate affected players
            migratePlayersFromLanguage(language);
            
            getLogger().info("Removed language package: " + language);
        }
    }
    
    private void migratePlayersFromLanguage(String removedLanguage) {
        String defaultLang = lm.getDefaultLanguage();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerId = player.getUniqueId().toString();
            String playerLang = lm.getPlayerLanguage(playerId);
            
            if (removedLanguage.equals(playerLang)) {
                lm.setPlayerLanguageAsync(playerId, defaultLang)
                    .thenRun(() -> {
                        player.sendMessage("§e⚠️ Your language was changed to " + defaultLang + 
                            " (support for " + removedLanguage + " was removed)");
                    });
            }
        }
    }
    
    public void performConfigurationHealthCheck() {
        LanguageConfiguration config = lm.getLanguageConfiguration();
        
        if (config == null) {
            getLogger().warning("❌ Language configuration is null!");
            return;
        }
        
        // Check default language
        String defaultLang = config.getDefaultLanguage();
        if (defaultLang == null || defaultLang.trim().isEmpty()) {
            getLogger().warning("❌ Default language is not set!");
        } else if (!config.getSupportedLanguages().contains(defaultLang)) {
            getLogger().warning("❌ Default language '" + defaultLang + "' is not in supported languages!");
        }
        
        // Check supported languages
        Set<String> supported = config.getSupportedLanguages();
        if (supported == null || supported.isEmpty()) {
            getLogger().warning("❌ No supported languages configured!");
        } else {
            getLogger().info("✅ Languages configured: " + String.join(", ", supported));
        }
        
        // Check for orphaned player languages
        checkOrphanedPlayerLanguages(supported);
    }
    
    private void checkOrphanedPlayerLanguages(Set<String> supportedLanguages) {
        List<String> orphanedPlayers = new ArrayList<>();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerId = player.getUniqueId().toString();
            String playerLang = lm.getPlayerLanguage(playerId);
            
            if (playerLang != null && !supportedLanguages.contains(playerLang)) {
                orphanedPlayers.add(player.getName() + " (" + playerLang + ")");
            }
        }
        
        if (!orphanedPlayers.isEmpty()) {
            getLogger().warning("⚠️ Players with unsupported languages: " + String.join(", ", orphanedPlayers));
        }
    }
}
```

---

## 🔄 Cache Management

### `invalidateLanguageCache()`
Clears the language configuration cache.

```java
// Clear language cache
lm.invalidateLanguageCache();
getLogger().info("Language cache cleared");

// Reload fresh configuration
LanguageConfiguration config = lm.getLanguageConfiguration();
```

### `refreshPlayerLanguageCache(String playerId)`
Refreshes language cache for a specific player.

```java
// Refresh specific player's language cache
String playerId = player.getUniqueId().toString();
lm.refreshPlayerLanguageCache(playerId);

// Get fresh language data
String language = lm.getPlayerLanguage(playerId);
```

---

## 🛠️ Utility Methods

### Language Detection

```java
public class LanguageDetector {
    
    private final LanguageManager lm;
    
    public LanguageDetector() {
        this.lm = MongoConfigsAPI.getLanguageManager();
    }
    
    public String detectPlayerLanguage(Player player) {
        // Try to detect from client locale (if available)
        String clientLocale = getClientLocale(player);
        
        if (clientLocale != null) {
            String languageCode = clientLocale.split("_")[0].toLowerCase();
            
            if (lm.isLanguageSupported(languageCode)) {
                return languageCode;
            }
        }
        
        // Fallback to default
        return lm.getDefaultLanguage();
    }
    
    private String getClientLocale(Player player) {
        // This would require additional implementation to get client locale
        // Could use player data, previous sessions, or client mod communication
        return null;  // Placeholder
    }
    
    public void autoSetPlayerLanguage(Player player) {
        String playerId = player.getUniqueId().toString();
        String currentLang = lm.getPlayerLanguage(playerId);
        
        if (currentLang == null) {
            String detectedLang = detectPlayerLanguage(player);
            lm.setPlayerLanguageAsync(playerId, detectedLang)
                .thenRun(() -> {
                    Messages messages = MongoConfigsAPI.getConfigManager().messagesOf(GuiMessages.class);
                    String welcomeMsg = messages.get(detectedLang, "welcome.auto_language", detectedLang);
                    player.sendMessage(ColorHelper.parseComponent(welcomeMsg));
                });
        }
    }
}
```

### Language Statistics

```java
public class LanguageStatistics {
    
    private final LanguageManager lm;
    
    public LanguageStatistics() {
        this.lm = MongoConfigsAPI.getLanguageManager();
    }
    
    public Map<String, Integer> getOnlinePlayerLanguageStats() {
        Map<String, Integer> stats = new HashMap<>();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerId = player.getUniqueId().toString();
            String language = lm.getPlayerLanguageOrDefault(playerId);
            stats.merge(language, 1, Integer::sum);
        }
        
        return stats;
    }
    
    public void displayLanguageStats(CommandSender sender) {
        Map<String, Integer> stats = getOnlinePlayerLanguageStats();
        
        sender.sendMessage("§a📊 Language Statistics:");
        sender.sendMessage("§e─────────────────────");
        
        if (stats.isEmpty()) {
            sender.sendMessage("§7No players online");
            return;
        }
        
        int totalPlayers = stats.values().stream().mapToInt(Integer::intValue).sum();
        
        stats.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                String language = entry.getKey();
                int count = entry.getValue();
                double percentage = (count * 100.0) / totalPlayers;
                
                sender.sendMessage(String.format("§f%s: §b%d §7(%.1f%%)", 
                    language.toUpperCase(), count, percentage));
            });
        
        sender.sendMessage("§e─────────────────────");
        sender.sendMessage("§aTotal: §b" + totalPlayers + " players");
    }
    
    public String getMostPopularLanguage() {
        Map<String, Integer> stats = getOnlinePlayerLanguageStats();
        
        return stats.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(lm.getDefaultLanguage());
    }
}
```

---

## 🎯 Best Practices

### 1. Always Use Safe Methods

```java
// ✅ Good - Safe language retrieval
String language = lm.getPlayerLanguageOrDefault(playerId);

// ❌ Avoid - Can return null
String language = lm.getPlayerLanguage(playerId);
if (language == null) {
    language = lm.getDefaultLanguage();
}
```

### 2. Validate Languages Before Setting

```java
// ✅ Good - Validate before setting
public boolean setPlayerLanguageSafe(Player player, String language) {
    if (!lm.isLanguageSupported(language)) {
        return false;
    }
    
    String playerId = player.getUniqueId().toString();
    lm.setPlayerLanguage(playerId, language);
    return true;
}
```

### 3. Handle Async Operations Properly

```java
// ✅ Good - Proper async handling
lm.setPlayerLanguageAsync(playerId, language)
    .thenRun(() -> {
        // Success callback
        player.sendMessage("§a✅ Language updated!");
    })
    .exceptionally(error -> {
        // Error handling
        player.sendMessage("§c❌ Failed to update language");
        getLogger().severe("Language update error: " + error.getMessage());
        return null;
    });
```

### 4. Cache Frequently Accessed Data

```java
public class CachedLanguageManager {
    
    private final LanguageManager lm;
    private final Map<String, String> playerLanguageCache = new ConcurrentHashMap<>();
    
    public String getPlayerLanguageCached(String playerId) {
        return playerLanguageCache.computeIfAbsent(playerId, 
            id -> lm.getPlayerLanguageOrDefault(id));
    }
    
    public void clearPlayerCache(String playerId) {
        playerLanguageCache.remove(playerId);
    }
}
```

---

*Next: Learn about the [[Messages API]] for handling multilingual content.*