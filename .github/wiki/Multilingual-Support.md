# Multilingual Support

Complete guide to implementing multilingual support in your Minecraft plugins using MongoDB Configs API with dynamic language switching and player preferences.

## 📋 Overview

This tutorial covers implementing comprehensive multilingual support with automatic language detection, dynamic message loading, and seamless player experience.

## 🌍 Language System Architecture

### Core Components

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "language_system")
@ConfigsCollection(collection = "language_config")
public class LanguageConfig {
    
    @ConfigsField
    private String defaultLanguage = "en";
    
    @ConfigsField
    private List<String> supportedLanguages = Arrays.asList("en", "pl", "es", "de", "fr");
    
    @ConfigsField
    private boolean autoDetectLanguage = true;
    
    @ConfigsField
    private Map<String, String> languageNames = new HashMap<>();
    
    @ConfigsField
    private Map<String, String> countryMappings = new HashMap<>();
    
    public LanguageConfig() {
        // Initialize language names
        languageNames.put("en", "English");
        languageNames.put("pl", "Polski");
        languageNames.put("es", "Español");
        languageNames.put("de", "Deutsch");
        languageNames.put("fr", "Français");
        
        // Initialize country mappings
        countryMappings.put("US", "en");
        countryMappings.put("GB", "en");
        countryMappings.put("PL", "pl");
        countryMappings.put("ES", "es");
        countryMappings.put("DE", "de");
        countryMappings.put("FR", "fr");
    }
    
    // Getters and setters...
}
```

### Player Language Preferences

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
    private long lastUpdated;
    
    @ConfigsField
    private boolean autoDetectEnabled = true;
    
    @ConfigsField
    private List<String> preferredLanguages = new ArrayList<>();
    
    // Getters and setters...
    
    public String getEffectiveLanguage() {
        if (selectedLanguage != null && !selectedLanguage.isEmpty()) {
            return selectedLanguage;
        }
        if (autoDetectEnabled && detectedLanguage != null) {
            return detectedLanguage;
        }
        return "en"; // fallback
    }
}
```

## 🎯 Language Manager

### Core Language Manager

```java
public class LanguageManager {
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private LanguageConfig config;
    private final Map<String, PlayerLanguage> playerLanguages = new ConcurrentHashMap<>();
    
    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = MongoConfigsAPI.getConfigManager();
        loadConfig();
        loadPlayerLanguages();
        setupChangeStreams();
    }
    
    private void loadConfig() {
        try {
            config = configManager.get(LanguageConfig.class, "global_config");
            if (config == null) {
                config = new LanguageConfig();
                configManager.save(config);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load language config: " + e.getMessage());
            config = new LanguageConfig();
        }
    }
    
    private void loadPlayerLanguages() {
        try {
            List<PlayerLanguage> languages = configManager.getAll(PlayerLanguage.class);
            languages.forEach(lang -> playerLanguages.put(lang.getPlayerId(), lang));
            plugin.getLogger().info("Loaded language preferences for " + languages.size() + " players");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load player languages: " + e.getMessage());
        }
    }
    
    private void setupChangeStreams() {
        configManager.watchCollection(PlayerLanguage.class, changeEvent -> {
            PlayerLanguage lang = changeEvent.getDocument();
            if (lang != null) {
                playerLanguages.put(lang.getPlayerId(), lang);
            }
        });
    }
    
    public String getPlayerLanguageOrDefault(String playerId) {
        PlayerLanguage playerLang = playerLanguages.get(playerId);
        if (playerLang != null) {
            return playerLang.getEffectiveLanguage();
        }
        
        // Auto-detect language for new players
        String detected = detectPlayerLanguage(playerId);
        setPlayerLanguage(playerId, detected, true);
        return detected;
    }
    
    public void setPlayerLanguage(String playerId, String language, boolean isSelected) {
        PlayerLanguage playerLang = playerLanguages.computeIfAbsent(playerId, k -> {
            PlayerLanguage pl = new PlayerLanguage();
            pl.setPlayerId(k);
            pl.setPreferredLanguages(new ArrayList<>());
            return pl;
        });
        
        if (isSelected) {
            playerLang.setSelectedLanguage(language);
        } else {
            playerLang.setDetectedLanguage(language);
        }
        
        playerLang.setLastUpdated(System.currentTimeMillis());
        
        try {
            configManager.save(playerLang);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save player language: " + e.getMessage());
        }
    }
    
    private String detectPlayerLanguage(String playerId) {
        Player player = plugin.getServer().getPlayer(UUID.fromString(playerId));
        if (player == null) return config.getDefaultLanguage();
        
        // Try to detect from client locale
        String locale = player.getLocale();
        if (locale != null && !locale.isEmpty()) {
            String detected = detectFromLocale(locale);
            if (detected != null) {
                return detected;
            }
        }
        
        // Try to detect from IP (if available)
        // This would require additional plugins/services
        
        return config.getDefaultLanguage();
    }
    
    private String detectFromLocale(String locale) {
        // Parse locale (e.g., "en_US" -> "en")
        String language = locale.split("_")[0].toLowerCase();
        
        if (config.getSupportedLanguages().contains(language)) {
            return language;
        }
        
        // Try country mapping
        if (locale.contains("_")) {
            String country = locale.split("_")[1].toUpperCase();
            String mappedLanguage = config.getCountryMappings().get(country);
            if (mappedLanguage != null) {
                return mappedLanguage;
            }
        }
        
        return null;
    }
    
    public List<String> getSupportedLanguages() {
        return config.getSupportedLanguages();
    }
    
    public String getLanguageName(String languageCode) {
        return config.getLanguageNames().getOrDefault(languageCode, languageCode.toUpperCase());
    }
    
    public boolean isLanguageSupported(String language) {
        return config.getSupportedLanguages().contains(language);
    }
}
```

## 💬 Multilingual Messages

### Message Class Structure

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "language_system")
@ConfigsCollection(collection = "messages")
@SupportedLanguages({"en", "pl", "es", "de", "fr"})
public class PluginMessages extends MongoMessages {
    
    // Command messages
    public String getCommandHelp(String command) {
        return get("en", "command." + command + ".help");
    }
    
    public String getCommandHelp(String lang, String command) {
        return get(lang, "command." + command + ".help");
    }
    
    public String getCommandNoPermission() {
        return get("en", "command.no_permission");
    }
    
    public String getCommandNoPermission(String lang) {
        return get(lang, "command.no_permission");
    }
    
    // GUI messages
    public String getGuiTitle(String guiName) {
        return get("en", "gui." + guiName + ".title");
    }
    
    public String getGuiTitle(String lang, String guiName) {
        return get(lang, "gui." + guiName + ".title");
    }
    
    public String getButtonLabel(String buttonName) {
        return get("en", "gui.button." + buttonName);
    }
    
    public String getButtonLabel(String lang, String buttonName) {
        return get(lang, "gui.button." + buttonName);
    }
    
    // Error messages
    public String getErrorPlayerNotFound(String playerName) {
        return get("en", "error.player_not_found", playerName);
    }
    
    public String getErrorPlayerNotFound(String lang, String playerName) {
        return get(lang, "error.player_not_found", playerName);
    }
    
    // Success messages
    public String getSuccessActionCompleted(String action) {
        return get("en", "success." + action + "_completed");
    }
    
    public String getSuccessActionCompleted(String lang, String action) {
        return get(lang, "success." + action + "_completed");
    }
}
```

## 🎨 Language Selection GUI

### Language Selector

```java
public class LanguageSelectionGUI extends BaseGUI {
    
    private final LanguageManager languageManager;
    private final PluginMessages messages;
    
    public LanguageSelectionGUI(JavaPlugin plugin, Player player) {
        super(plugin, player);
        this.languageManager = MongoConfigsAPI.getLanguageManager();
        this.messages = plugin.getMessages(); // Assuming plugin has messages
    }
    
    @Override
    public void open() {
        initializeInventory(27, getTitle());
    }
    
    @Override
    public String getTitle() {
        return messages.getGuiTitle(language, "language_selection");
    }
    
    @Override
    protected void setupItems() {
        List<String> supportedLanguages = languageManager.getSupportedLanguages();
        int slot = 0;
        
        for (String langCode : supportedLanguages) {
            if (slot >= 18) break; // Leave room for controls
            
            ItemStack langItem = createLanguageItem(langCode);
            addItem(slot++, langItem);
        }
        
        // Add current language display
        addCurrentLanguageDisplay();
        
        // Add control buttons
        addControlButtons();
        
        // Fill empty slots
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, 
            "&7", Collections.emptyList());
        fillEmptySlots(filler);
    }
    
    private ItemStack createLanguageItem(String langCode) {
        String langName = languageManager.getLanguageName(langCode);
        String flagEmoji = getFlagEmoji(langCode);
        
        Material icon = getLanguageIcon(langCode);
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(ColorHelper.parseString("&e" + langName + " " + flagEmoji));
        
        List<String> lore = new ArrayList<>();
        lore.add(ColorHelper.parseString("&7Language code: &f" + langCode.toUpperCase()));
        
        String currentLang = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        if (langCode.equals(currentLang)) {
            lore.add(ColorHelper.parseString("&a✓ Currently selected"));
        } else {
            lore.add(ColorHelper.parseString("&7Click to select"));
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    private void addCurrentLanguageDisplay() {
        String currentLang = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        String langName = languageManager.getLanguageName(currentLang);
        
        ItemStack currentItem = createItem(Material.PLAYER_HEAD, 
            "&aCurrent Language", 
            Arrays.asList(
                "&f" + langName,
                "&7(" + currentLang.toUpperCase() + ")"
            ));
        addItem(22, currentItem);
    }
    
    private void addControlButtons() {
        // Auto-detect toggle
        ItemStack autoDetectItem = createItem(Material.COMPASS, 
            "&bAuto-Detect Language", 
            Arrays.asList(
                "&7Currently: &f" + (isAutoDetectEnabled() ? "Enabled" : "Disabled"),
                "&7Click to toggle"
            ));
        addItem(24, autoDetectItem);
        
        // Close button
        ItemStack closeItem = createItem(Material.BARRIER, 
            "&cClose", 
            Arrays.asList("&7Click to close"));
        addItem(26, closeItem);
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;
        
        int slot = event.getSlot();
        
        if (slot >= 0 && slot < 18) {
            // Language selection
            List<String> supportedLanguages = languageManager.getSupportedLanguages();
            if (slot < supportedLanguages.size()) {
                String selectedLang = supportedLanguages.get(slot);
                selectLanguage(selectedLang);
            }
        } else if (slot == 24) {
            // Toggle auto-detect
            toggleAutoDetect();
        } else if (slot == 26) {
            // Close
            player.closeInventory();
        }
    }
    
    private void selectLanguage(String langCode) {
        languageManager.setPlayerLanguage(player.getUniqueId().toString(), langCode, true);
        
        String langName = languageManager.getLanguageName(langCode);
        String message = messages.getLanguageChanged(language, langName);
        player.sendMessage(ColorHelper.parseComponent(message));
        
        // Refresh GUI to show new selection
        refreshInventory();
    }
    
    private void toggleAutoDetect() {
        // This would require extending PlayerLanguage to store auto-detect preference
        // Implementation depends on your PlayerLanguage structure
    }
    
    private boolean isAutoDetectEnabled() {
        // Implementation depends on your PlayerLanguage structure
        return true;
    }
    
    private String getFlagEmoji(String langCode) {
        switch (langCode) {
            case "en": return "🇺🇸";
            case "pl": return "🇵🇱";
            case "es": return "🇪🇸";
            case "de": return "🇩🇪";
            case "fr": return "🇫🇷";
            default: return "🏳️";
        }
    }
    
    private Material getLanguageIcon(String langCode) {
        switch (langCode) {
            case "en": return Material.WRITABLE_BOOK;
            case "pl": return Material.BOOK;
            case "es": return Material.KNOWLEDGE_BOOK;
            case "de": return Material.ENCHANTED_BOOK;
            case "fr": return Material.BOOKSHELF;
            default: return Material.PAPER;
        }
    }
}
```

## 🔄 Dynamic Message Loading

### Message Cache System

```java
public class MessageCache {
    
    private final ConfigManager configManager;
    private final Map<String, Map<String, String>> messageCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private final long cacheExpiryMs = 5 * 60 * 1000; // 5 minutes
    
    public MessageCache(ConfigManager configManager) {
        this.configManager = configManager;
        setupChangeStreams();
    }
    
    private void setupChangeStreams() {
        configManager.watchCollection(PluginMessages.class, changeEvent -> {
            PluginMessages messages = changeEvent.getDocument();
            if (messages != null) {
                // Invalidate cache for affected languages
                messageCache.clear();
            }
        });
    }
    
    public String getMessage(String language, String key, Object... args) {
        // Check cache first
        Map<String, String> langCache = getLanguageCache(language);
        String cachedMessage = langCache.get(key);
        
        if (cachedMessage != null) {
            return formatMessage(cachedMessage, args);
        }
        
        // Load from database
        try {
            PluginMessages messages = configManager.findFirst(PluginMessages.class, 
                "language", language);
            
            if (messages != null) {
                // This is a simplified example - in reality you'd need to 
                // access the message through the Messages interface
                String message = getMessageFromObject(messages, key);
                
                if (message != null) {
                    langCache.put(key, message);
                    return formatMessage(message, args);
                }
            }
        } catch (Exception e) {
            // Log error
        }
        
        // Fallback to English
        if (!language.equals("en")) {
            return getMessage("en", key, args);
        }
        
        // Ultimate fallback
        return key;
    }
    
    private Map<String, String> getLanguageCache(String language) {
        Long timestamp = cacheTimestamps.get(language);
        
        if (timestamp == null || System.currentTimeMillis() - timestamp > cacheExpiryMs) {
            // Cache expired or doesn't exist
            messageCache.remove(language);
            cacheTimestamps.put(language, System.currentTimeMillis());
        }
        
        return messageCache.computeIfAbsent(language, k -> new ConcurrentHashMap<>());
    }
    
    private String formatMessage(String message, Object... args) {
        if (args.length == 0) return message;
        
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }
        
        return message;
    }
    
    private String getMessageFromObject(PluginMessages messages, String key) {
        // This is a simplified example - in a real implementation you'd need
        // reflection or a proper message storage system
        try {
            // Use reflection to find the appropriate method
            String methodName = "get" + key.replace(".", "_");
            Method method = messages.getClass().getMethod(methodName);
            return (String) method.invoke(messages);
        } catch (Exception e) {
            return null;
        }
    }
    
    public void invalidateCache(String language) {
        messageCache.remove(language);
        cacheTimestamps.remove(language);
    }
    
    public void invalidateAllCache() {
        messageCache.clear();
        cacheTimestamps.clear();
    }
}
```

## 🌐 Advanced Language Features

### Pluralization Support

```java
public class PluralizationHelper {
    
    public static String pluralize(String language, String singular, String plural, int count) {
        switch (language) {
            case "en":
                return count == 1 ? singular : plural;
            case "pl":
                return pluralizePolish(singular, plural, count);
            case "es":
                return count == 1 ? singular : plural;
            case "de":
                return count == 1 ? singular : plural;
            case "fr":
                return count == 1 ? singular : plural;
            default:
                return count == 1 ? singular : plural;
        }
    }
    
    private static String pluralizePolish(String singular, String plural, int count) {
        if (count == 1) return singular;
        
        // Polish pluralization rules are complex
        // This is a simplified version
        int lastDigit = count % 10;
        int lastTwoDigits = count % 100;
        
        if (lastTwoDigits >= 12 && lastTwoDigits <= 14) return plural;
        if (lastDigit >= 2 && lastDigit <= 4) return singular + "y"; // or other forms
        return plural;
    }
    
    public static String getCountMessage(String language, String key, int count, Object... args) {
        String form = pluralize(language, "singular", "plural", count);
        String messageKey = key + "." + form;
        
        // Get message using your message system
        PluginMessages messages = getMessages();
        return messages.get(messageKey, args);
    }
}
```

### Date and Time Formatting

```java
public class DateTimeFormatter {
    
    private static final Map<String, String> datePatterns = new HashMap<>();
    private static final Map<String, String> timePatterns = new HashMap<>();
    
    static {
        // Date patterns
        datePatterns.put("en", "MM/dd/yyyy");
        datePatterns.put("pl", "dd.MM.yyyy");
        datePatterns.put("es", "dd/MM/yyyy");
        datePatterns.put("de", "dd.MM.yyyy");
        datePatterns.put("fr", "dd/MM/yyyy");
        
        // Time patterns
        timePatterns.put("en", "hh:mm a");
        timePatterns.put("pl", "HH:mm");
        timePatterns.put("es", "HH:mm");
        timePatterns.put("de", "HH:mm");
        timePatterns.put("fr", "HH:mm");
    }
    
    public static String formatDate(String language, long timestamp) {
        String pattern = datePatterns.getOrDefault(language, "yyyy-MM-dd");
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.format(new Date(timestamp));
    }
    
    public static String formatTime(String language, long timestamp) {
        String pattern = timePatterns.getOrDefault(language, "HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.format(new Date(timestamp));
    }
    
    public static String formatDateTime(String language, long timestamp) {
        return formatDate(language, timestamp) + " " + formatTime(language, timestamp);
    }
    
    public static String formatRelativeTime(String language, long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return getRelativeTimeMessage(language, "days_ago", days);
        } else if (hours > 0) {
            return getRelativeTimeMessage(language, "hours_ago", hours);
        } else if (minutes > 0) {
            return getRelativeTimeMessage(language, "minutes_ago", minutes);
        } else {
            return getRelativeTimeMessage(language, "seconds_ago", seconds);
        }
    }
    
    private static String getRelativeTimeMessage(String language, String key, long value) {
        PluginMessages messages = getMessages();
        return messages.get("time." + key, value);
    }
}
```

## 🎯 Language Commands

### Language Command Implementation

```java
public class LanguageCommand implements CommandExecutor {
    
    private final JavaPlugin plugin;
    private final LanguageManager languageManager;
    private final PluginMessages messages;
    
    public LanguageCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.languageManager = MongoConfigsAPI.getLanguageManager();
        this.messages = plugin.getMessages();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        
        if (args.length == 0) {
            // Open language selection GUI
            new LanguageSelectionGUI(plugin, player).open();
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "set":
                return handleSetLanguage(player, args, language);
            case "list":
                return handleListLanguages(player, language);
            case "current":
                return handleCurrentLanguage(player, language);
            case "detect":
                return handleDetectLanguage(player, language);
            default:
                sendHelpMessage(player, language);
                return true;
        }
    }
    
    private boolean handleSetLanguage(Player player, String[] args, String language) {
        if (args.length < 2) {
            player.sendMessage(ColorHelper.parseComponent(
                messages.getCommandUsage(language, "/lang set <language>")));
            return true;
        }
        
        String targetLang = args[1].toLowerCase();
        
        if (!languageManager.isLanguageSupported(targetLang)) {
            String message = messages.getLanguageNotSupported(language, targetLang);
            player.sendMessage(ColorHelper.parseComponent(message));
            return true;
        }
        
        languageManager.setPlayerLanguage(player.getUniqueId().toString(), targetLang, true);
        
        String langName = languageManager.getLanguageName(targetLang);
        String message = messages.getLanguageChanged(language, langName);
        player.sendMessage(ColorHelper.parseComponent(message));
        
        return true;
    }
    
    private boolean handleListLanguages(Player player, String language) {
        List<String> supportedLanguages = languageManager.getSupportedLanguages();
        
        String message = messages.getSupportedLanguagesHeader(language);
        player.sendMessage(ColorHelper.parseComponent(message));
        
        for (String langCode : supportedLanguages) {
            String langName = languageManager.getLanguageName(langCode);
            String currentIndicator = language.equals(langCode) ? " &a(✓)" : "";
            player.sendMessage(ColorHelper.parseComponent(
                "&f- " + langName + " &7(" + langCode.toUpperCase() + ")" + currentIndicator));
        }
        
        return true;
    }
    
    private boolean handleCurrentLanguage(Player player, String language) {
        String currentLang = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        String langName = languageManager.getLanguageName(currentLang);
        
        String message = messages.getCurrentLanguage(language, langName, currentLang.toUpperCase());
        player.sendMessage(ColorHelper.parseComponent(message));
        
        return true;
    }
    
    private boolean handleDetectLanguage(Player player, String language) {
        String detected = detectPlayerLanguage(player);
        languageManager.setPlayerLanguage(player.getUniqueId().toString(), detected, false);
        
        String langName = languageManager.getLanguageName(detected);
        String message = messages.getLanguageDetected(language, langName);
        player.sendMessage(ColorHelper.parseComponent(message));
        
        return true;
    }
    
    private void sendHelpMessage(Player player, String language) {
        List<String> helpLines = Arrays.asList(
            messages.getCommandHelp(language, "lang"),
            "&f/lang &7- Open language selection GUI",
            "&f/lang set <language> &7- Set your language",
            "&f/lang list &7- List supported languages",
            "&f/lang current &7- Show your current language",
            "&f/lang detect &7- Auto-detect your language"
        );
        
        for (String line : helpLines) {
            player.sendMessage(ColorHelper.parseComponent(line));
        }
    }
    
    private String detectPlayerLanguage(Player player) {
        String locale = player.getLocale();
        if (locale != null && !locale.isEmpty()) {
            String detected = languageManager.detectFromLocale(locale);
            if (detected != null) {
                return detected;
            }
        }
        return languageManager.getDefaultLanguage();
    }
}
```

## 📊 Language Analytics

### Language Usage Statistics

```java
public class LanguageAnalytics {
    
    private final ConfigManager configManager;
    private final Map<String, Integer> languageUsage = new ConcurrentHashMap<>();
    
    public LanguageAnalytics(ConfigManager configManager) {
        this.configManager = configManager;
        calculateUsageStats();
    }
    
    private void calculateUsageStats() {
        try {
            List<PlayerLanguage> playerLanguages = configManager.getAll(PlayerLanguage.class);
            
            for (PlayerLanguage pl : playerLanguages) {
                String effectiveLang = pl.getEffectiveLanguage();
                languageUsage.merge(effectiveLang, 1, Integer::sum);
            }
        } catch (Exception e) {
            // Log error
        }
    }
    
    public Map<String, Integer> getLanguageUsage() {
        return new HashMap<>(languageUsage);
    }
    
    public String getMostPopularLanguage() {
        return languageUsage.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("en");
    }
    
    public double getLanguageDiversity() {
        int totalPlayers = languageUsage.values().stream().mapToInt(Integer::intValue).sum();
        if (totalPlayers == 0) return 0.0;
        
        double diversity = 0.0;
        for (int count : languageUsage.values()) {
            double proportion = (double) count / totalPlayers;
            diversity -= proportion * Math.log(proportion);
        }
        
        return diversity;
    }
    
    public void recordLanguageUsage(String playerId, String language) {
        // Could track per-session usage
    }
}
```

## 🎨 Integration Examples

### Multilingual Plugin Integration

```java
public class MultilingualPlugin extends JavaPlugin {
    
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private MessageCache messageCache;
    private PluginMessages messages;
    
    @Override
    public void onEnable() {
        // Initialize MongoDB Configs API
        configManager = MongoConfigsAPI.createConfigManager(
            getConfig().getString("mongodb.uri", "mongodb://localhost:27017"),
            getConfig().getString("mongodb.database", "multilingual_plugin")
        );
        
        // Initialize language system
        languageManager = new LanguageManager(this);
        messageCache = new MessageCache(configManager);
        messages = configManager.messagesOf(PluginMessages.class);
        
        // Register commands
        getCommand("lang").setExecutor(new LanguageCommand(this));
        
        // Register events
        getServer().getPluginManager().registerEvents(new PlayerLanguageListener(this), this);
        
        getLogger().info("Multilingual Plugin enabled!");
    }
    
    // Utility methods for other classes
    public String getMessage(Player player, String key, Object... args) {
        String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        return messageCache.getMessage(language, key, args);
    }
    
    public String getMessage(String playerId, String key, Object... args) {
        String language = languageManager.getPlayerLanguageOrDefault(playerId);
        return messageCache.getMessage(language, key, args);
    }
    
    // Getters...
    public LanguageManager getLanguageManager() { return languageManager; }
    public MessageCache getMessageCache() { return messageCache; }
    public PluginMessages getMessages() { return messages; }
}
```

---

*Next: Learn about [[Performance Optimization]] for scaling your multilingual applications.*