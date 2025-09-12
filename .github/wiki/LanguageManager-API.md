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

#### Helper Method for Default Fallback
Since there's no built-in `getPlayerLanguageOrDefault()` method, you need to handle null values manually:

```java
// Manual fallback to default
String playerId = player.getUniqueId().toString();
String language = lm.getPlayerLanguage(playerId);
if (language == null) {
    language = lm.getDefaultLanguage();
}

// Always returns a valid language
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
            String language = lm.getPlayerLanguage(playerId);
            if (language == null) {
                language = lm.getDefaultLanguage();
            }
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
String[] supportedLanguages = lm.getSupportedLanguages();  // Returns String[]

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
    
    // You would need to create your own language selection GUI
    // The LanguageSelectionGUI class is implementation-specific
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
        String[] supported = lm.getSupportedLanguages();
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
        String[] supported = lm.getSupportedLanguages();
        sender.sendMessage("§e📋 Available: " + String.join(", ", supported));
        return true;
    }
    
    String language = args[0].toLowerCase();
    return setPlayerLanguageSafely(player, language);
}
```

### Language Support Information

**Note:** The current API only provides read-only access to supported languages. Language configuration is managed server-side through configuration files.



---





---



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
            String language = lm.getPlayerLanguage(playerId);
            if (language == null) {
                language = lm.getDefaultLanguage();
            }
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

### 1. Always Handle Null Values

```java
// ✅ Good - Manual null handling
String language = lm.getPlayerLanguage(playerId);
if (language == null) {
    language = lm.getDefaultLanguage();
}

// ❌ Avoid - Assuming non-null return
String language = lm.getPlayerLanguage(playerId);
// This could be null!
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
            id -> {
                String lang = lm.getPlayerLanguage(id);
                return lang != null ? lang : lm.getDefaultLanguage();
            });
    }
    
    public void clearPlayerCache(String playerId) {
        playerLanguageCache.remove(playerId);
    }
}
```

---

*Next: Learn about the [[Messages API]] for handling multilingual content.*