# Translation Examples

> **Complete multilingual examples with language switching, message translation, and localization**

## Table of Contents
- [Basic Message Translation](#basic-message-translation)
- [Language Selection GUI](#language-selection-gui)
- [Dynamic Message Loading](#dynamic-message-loading)
- [Player Language Preferences](#player-language-preferences)
- [Advanced Translation Features](#advanced-translation-features)
- [Complete Plugin Example](#complete-plugin-example)
- [Best Practices](#best-practices)

---

## Basic Message Translation

### Simple Message Class

```java
@ConfigsFileProperties(name = "messages")
@ConfigsDatabase("minecraft")
public class Messages extends MongoConfig<Messages> {
    
    // Basic messages
    private String welcomeMessage = "Welcome to the server!";
    private String goodbyeMessage = "Goodbye!";
    private String noPermission = "You don't have permission to do this.";
    
    // Command messages
    private String commandUsage = "Usage: {command}";
    private String playerNotFound = "Player {player} not found.";
    private String teleportSuccess = "Teleported to {location}.";
    
    // Getters and setters...
    
    // Translation methods
    public String getWelcomeMessage() {
        return translateColorCodes(welcomeMessage);
    }
    
    public String getGoodbyeMessage() {
        return translateColorCodes(goodbyeMessage);
    }
    
    public String getNoPermission() {
        return translateColorCodes(noPermission);
    }
    
    public String getCommandUsage(String command) {
        return translateColorCodes(commandUsage.replace("{command}", command));
    }
    
    public String getPlayerNotFound(String playerName) {
        return translateColorCodes(playerNotFound.replace("{player}", playerName));
    }
    
    public String getTeleportSuccess(String location) {
        return translateColorCodes(teleportSuccess.replace("{location}", location));
    }
    
    private String translateColorCodes(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
```

### Usage in Plugin

```java
public class MyPlugin extends JavaPlugin {
    
    private Messages messages;
    private ConfigManager configManager;
    
    @Override
    public void onEnable() {
        // Initialize MongoDB Configs API
        MongoConfigsAPI.initialize(this);
        
        configManager = MongoConfigsAPI.getConfigManager();
        messages = configManager.loadObject(Messages.class);
        
        // Register commands
        getCommand("welcome").setExecutor(new WelcomeCommand());
    }
    
    public Messages getMessages() {
        return messages;
    }
}

public class WelcomeCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        MyPlugin plugin = MyPlugin.getInstance();
        Messages messages = plugin.getMessages();
        
        // Send welcome message
        player.sendMessage(messages.getWelcomeMessage());
        
        return true;
    }
}
```

---

## Language Selection GUI

### Language Selection GUI Class

```java
public class LanguageSelectionGUI {
    
    private final MyPlugin plugin;
    private final Messages messages;
    private final LanguageManager languageManager;
    
    public LanguageSelectionGUI(MyPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
        this.languageManager = MongoConfigsAPI.getLanguageManager();
    }
    
    public void openLanguageSelection(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, 
            ChatColor.DARK_BLUE + "Select Language / Wybierz Język");
        
        // Available languages
        addLanguageItem(inventory, 10, "English", "en", Material.BOOK);
        addLanguageItem(inventory, 12, "Polski", "pl", Material.WRITABLE_BOOK);
        addLanguageItem(inventory, 14, "Deutsch", "de", Material.KNOWLEDGE_BOOK);
        addLanguageItem(inventory, 16, "Français", "fr", Material.ENCHANTED_BOOK);
        
        player.openInventory(inventory);
    }
    
    private void addLanguageItem(Inventory inventory, int slot, String displayName, 
            String languageCode, Material material) {
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // Set display name based on player's current language
        String currentLang = languageManager.getPlayerLanguage(player);
        String localizedName = getLocalizedLanguageName(displayName, languageCode, currentLang);
        
        meta.setDisplayName(ChatColor.GREEN + localizedName);
        
        // Add lore with language code
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Language Code: " + languageCode.toUpperCase());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to select this language");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        inventory.setItem(slot, item);
    }
    
    private String getLocalizedLanguageName(String englishName, String languageCode, String currentLang) {
        // Return localized name based on current language
        switch (currentLang) {
            case "pl":
                switch (languageCode) {
                    case "en": return "Angielski";
                    case "pl": return "Polski";
                    case "de": return "Niemiecki";
                    case "fr": return "Francuski";
                    default: return englishName;
                }
            case "de":
                switch (languageCode) {
                    case "en": return "Englisch";
                    case "pl": return "Polnisch";
                    case "de": return "Deutsch";
                    case "fr": return "Französisch";
                    default: return englishName;
                }
            case "fr":
                switch (languageCode) {
                    case "en": return "Anglais";
                    case "pl": return "Polonais";
                    case "de": return "Allemand";
                    case "fr": return "Français";
                    default: return englishName;
                }
            default:
                return englishName;
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("Select Language")) return;
        
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        
        ItemMeta meta = clickedItem.getItemMeta();
        String displayName = ChatColor.stripColor(meta.getDisplayName());
        
        // Determine selected language
        String selectedLanguage = getLanguageFromDisplayName(displayName, player);
        
        if (selectedLanguage != null) {
            // Set player's language
            languageManager.setPlayerLanguage(player, selectedLanguage);
            
            // Send confirmation message
            String confirmMessage = getConfirmationMessage(selectedLanguage, player);
            player.sendMessage(ChatColor.GREEN + confirmMessage);
            
            // Close inventory
            player.closeInventory();
            
            // Reload messages for player
            reloadPlayerMessages(player, selectedLanguage);
        }
    }
    
    private String getLanguageFromDisplayName(String displayName, Player player) {
        String currentLang = languageManager.getPlayerLanguage(player);
        
        // Map localized names back to language codes
        switch (currentLang) {
            case "pl":
                switch (displayName) {
                    case "Angielski": return "en";
                    case "Polski": return "pl";
                    case "Niemiecki": return "de";
                    case "Francuski": return "fr";
                }
                break;
            case "de":
                switch (displayName) {
                    case "Englisch": return "en";
                    case "Polnisch": return "pl";
                    case "Deutsch": return "de";
                    case "Französisch": return "fr";
                }
                break;
            case "fr":
                switch (displayName) {
                    case "Anglais": return "en";
                    case "Polonais": return "pl";
                    case "Allemand": return "de";
                    case "Français": return "fr";
                }
                break;
            default:
                switch (displayName) {
                    case "English": return "en";
                    case "Polski": return "pl";
                    case "Deutsch": return "de";
                    case "Français": return "fr";
                }
                break;
        }
        return null;
    }
    
    private String getConfirmationMessage(String languageCode, Player player) {
        switch (languageCode) {
            case "en": return "Language set to English!";
            case "pl": return "Język został ustawiony na polski!";
            case "de": return "Sprache auf Deutsch gesetzt!";
            case "fr": return "Langue définie en français!";
            default: return "Language updated!";
        }
    }
    
    private void reloadPlayerMessages(Player player, String languageCode) {
        // Reload messages for this player
        // This would typically involve updating the player's message cache
        plugin.getLogger().info("Reloaded messages for player " + player.getName() + " to " + languageCode);
    }
}
```

### Language Command

```java
public class LanguageCommand implements CommandExecutor {
    
    private final LanguageSelectionGUI languageGUI;
    
    public LanguageCommand(LanguageSelectionGUI languageGUI) {
        this.languageGUI = languageGUI;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            // Open language selection GUI
            languageGUI.openLanguageSelection(player);
            return true;
        }
        
        // Direct language setting
        String languageCode = args[0].toLowerCase();
        
        if (!isValidLanguage(languageCode)) {
            player.sendMessage(ChatColor.RED + "Invalid language code! Available: en, pl, de, fr");
            return true;
        }
        
        // Set language directly
        LanguageManager languageManager = MongoConfigsAPI.getLanguageManager();
        languageManager.setPlayerLanguage(player, languageCode);
        
        // Send confirmation
        String confirmMessage = getConfirmationMessage(languageCode);
        player.sendMessage(ChatColor.GREEN + confirmMessage);
        
        return true;
    }
    
    private boolean isValidLanguage(String languageCode) {
        return Arrays.asList("en", "pl", "de", "fr").contains(languageCode);
    }
    
    private String getConfirmationMessage(String languageCode) {
        switch (languageCode) {
            case "en": return "Language set to English!";
            case "pl": return "Język został ustawiony na polski!";
            case "de": return "Sprache auf Deutsch gesetzt!";
            case "fr": return "Langue définie en français!";
            default: return "Language updated!";
        }
    }
}
```

---

## Dynamic Message Loading

### Multi-Language Message System

```java
@ConfigsFileProperties(name = "multilingual-messages")
@ConfigsDatabase("minecraft")
public class MultilingualMessages extends MongoConfig<MultilingualMessages> {
    
    // English messages
    private Map<String, String> en = new HashMap<>();
    
    // Polish messages
    private Map<String, String> pl = new HashMap<>();
    
    // German messages
    private Map<String, String> de = new HashMap<>();
    
    // French messages
    private Map<String, String> fr = new HashMap<>();
    
    public MultilingualMessages() {
        initializeDefaultMessages();
    }
    
    private void initializeDefaultMessages() {
        // English
        en.put("welcome", "&aWelcome to the server!");
        en.put("goodbye", "&eGoodbye!");
        en.put("no-permission", "&cYou don't have permission to do this.");
        en.put("player-not-found", "&cPlayer {player} not found.");
        en.put("teleport-success", "&aTeleported to {location}.");
        
        // Polish
        pl.put("welcome", "&aWitaj na serwerze!");
        pl.put("goodbye", "&eDo widzenia!");
        pl.put("no-permission", "&cNie masz uprawnień do wykonania tej komendy.");
        pl.put("player-not-found", "&cGracz {player} nie został znaleziony.");
        pl.put("teleport-success", "&aPrzeteleportowano do {location}.");
        
        // German
        de.put("welcome", "&aWillkommen auf dem Server!");
        de.put("goodbye", "&eAuf Wiedersehen!");
        de.put("no-permission", "&cDu hast keine Berechtigung dafür.");
        de.put("player-not-found", "&cSpieler {player} nicht gefunden.");
        de.put("teleport-success", "&aTeleportiert zu {location}.");
        
        // French
        fr.put("welcome", "&aBienvenue sur le serveur!");
        fr.put("goodbye", "&eAu revoir!");
        fr.put("no-permission", "&cVous n'avez pas la permission de faire ça.");
        fr.put("player-not-found", "&cJoueur {player} introuvable.");
        fr.put("teleport-success", "&aTéléporté vers {location}.");
    }
    
    public String getMessage(String key, String language) {
        Map<String, String> languageMap = getLanguageMap(language);
        String message = languageMap.get(key);
        
        if (message == null) {
            // Fallback to English
            message = en.get(key);
            if (message == null) {
                return "&cMessage not found: " + key;
            }
        }
        
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public String getMessage(String key, String language, Map<String, String> placeholders) {
        String message = getMessage(key, language);
        
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        
        return message;
    }
    
    private Map<String, String> getLanguageMap(String language) {
        switch (language.toLowerCase()) {
            case "pl": return pl;
            case "de": return de;
            case "fr": return fr;
            default: return en;
        }
    }
    
    public Set<String> getSupportedLanguages() {
        return Set.of("en", "pl", "de", "fr");
    }
    
    public String getLanguageDisplayName(String languageCode) {
        switch (languageCode.toLowerCase()) {
            case "en": return "English";
            case "pl": return "Polski";
            case "de": return "Deutsch";
            case "fr": return "Français";
            default: return languageCode.toUpperCase();
        }
    }
    
    // Getters and setters...
}
```

### Message Manager

```java
public class MessageManager {
    
    private final MultilingualMessages messages;
    private final LanguageManager languageManager;
    private final Map<String, Map<String, String>> messageCache = new ConcurrentHashMap<>();
    
    public MessageManager(MultilingualMessages messages, LanguageManager languageManager) {
        this.messages = messages;
        this.languageManager = languageManager;
        preloadMessages();
    }
    
    private void preloadMessages() {
        for (String language : messages.getSupportedLanguages()) {
            Map<String, String> languageMessages = new HashMap<>();
            
            // Preload all messages for this language
            for (String key : getAllMessageKeys()) {
                languageMessages.put(key, messages.getMessage(key, language));
            }
            
            messageCache.put(language, languageMessages);
        }
    }
    
    public String getMessage(Player player, String key) {
        String language = languageManager.getPlayerLanguage(player);
        return getMessage(language, key);
    }
    
    public String getMessage(String language, String key) {
        Map<String, String> languageMessages = messageCache.get(language);
        if (languageMessages == null) {
            languageMessages = messageCache.get("en"); // Fallback
        }
        
        String message = languageMessages.get(key);
        return message != null ? message : "&cMessage not found: " + key;
    }
    
    public String getMessage(Player player, String key, Map<String, String> placeholders) {
        String message = getMessage(player, key);
        return replacePlaceholders(message, placeholders);
    }
    
    public String getMessage(String language, String key, Map<String, String> placeholders) {
        String message = getMessage(language, key);
        return replacePlaceholders(message, placeholders);
    }
    
    private String replacePlaceholders(String message, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }
    
    public void sendMessage(Player player, String key) {
        player.sendMessage(getMessage(player, key));
    }
    
    public void sendMessage(Player player, String key, Map<String, String> placeholders) {
        player.sendMessage(getMessage(player, key, placeholders));
    }
    
    public void broadcastMessage(String key) {
        String message = getMessage("en", key); // Default to English for broadcasts
        Bukkit.broadcastMessage(message);
    }
    
    public void broadcastMessage(String key, Map<String, String> placeholders) {
        String message = replacePlaceholders(getMessage("en", key), placeholders);
        Bukkit.broadcastMessage(message);
    }
    
    private Set<String> getAllMessageKeys() {
        // This would return all available message keys
        return Set.of("welcome", "goodbye", "no-permission", "player-not-found", "teleport-success");
    }
    
    public void reloadMessages() {
        messageCache.clear();
        preloadMessages();
    }
}
```

---

## Player Language Preferences

### Player Language Storage

```java
@ConfigsFileProperties(name = "player-languages")
@ConfigsDatabase("minecraft")
public class PlayerLanguagePreferences extends MongoConfig<PlayerLanguagePreferences> {
    
    private Map<String, String> playerLanguages = new ConcurrentHashMap<>();
    private Map<String, Long> lastLanguageChange = new ConcurrentHashMap<>();
    
    public String getPlayerLanguage(String playerId) {
        return playerLanguages.getOrDefault(playerId, "en");
    }
    
    public void setPlayerLanguage(String playerId, String language) {
        playerLanguages.put(playerId, language);
        lastLanguageChange.put(playerId, System.currentTimeMillis());
        save();
    }
    
    public long getLastLanguageChange(String playerId) {
        return lastLanguageChange.getOrDefault(playerId, 0L);
    }
    
    public boolean canChangeLanguage(String playerId) {
        long lastChange = getLastLanguageChange(playerId);
        long cooldownMs = 30000; // 30 seconds cooldown
        
        return System.currentTimeMillis() - lastChange > cooldownMs;
    }
    
    public Map<String, Integer> getLanguageStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        
        for (String language : playerLanguages.values()) {
            stats.put(language, stats.getOrDefault(language, 0) + 1);
        }
        
        return stats;
    }
    
    // Getters and setters...
}
```

### Language Manager

```java
public class LanguageManager {
    
    private final PlayerLanguagePreferences preferences;
    private final Set<String> supportedLanguages = Set.of("en", "pl", "de", "fr");
    
    public LanguageManager() {
        ConfigManager configManager = MongoConfigsAPI.getConfigManager();
        this.preferences = configManager.loadObject(PlayerLanguagePreferences.class);
    }
    
    public String getPlayerLanguage(Player player) {
        return getPlayerLanguage(player.getUniqueId().toString());
    }
    
    public String getPlayerLanguage(String playerId) {
        return preferences.getPlayerLanguage(playerId);
    }
    
    public void setPlayerLanguage(Player player, String language) {
        setPlayerLanguage(player.getUniqueId().toString(), language);
    }
    
    public void setPlayerLanguage(String playerId, String language) {
        if (!supportedLanguages.contains(language)) {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }
        
        preferences.setPlayerLanguage(playerId, language);
    }
    
    public boolean canChangeLanguage(Player player) {
        return canChangeLanguage(player.getUniqueId().toString());
    }
    
    public boolean canChangeLanguage(String playerId) {
        return preferences.canChangeLanguage(playerId);
    }
    
    public Set<String> getSupportedLanguages() {
        return supportedLanguages;
    }
    
    public String getLanguageDisplayName(String languageCode) {
        switch (languageCode) {
            case "en": return "English";
            case "pl": return "Polski";
            case "de": return "Deutsch";
            case "fr": return "Français";
            default: return languageCode;
        }
    }
    
    public Map<String, Integer> getLanguageStatistics() {
        return preferences.getLanguageStatistics();
    }
    
    public void resetPlayerLanguage(String playerId) {
        setPlayerLanguage(playerId, "en");
    }
    
    public long getCooldownRemaining(Player player) {
        String playerId = player.getUniqueId().toString();
        long lastChange = preferences.getLastLanguageChange(playerId);
        long cooldownMs = 30000; // 30 seconds
        
        long timePassed = System.currentTimeMillis() - lastChange;
        return Math.max(0, cooldownMs - timePassed);
    }
}
```

---

## Advanced Translation Features

### Pluralization Support

```java
public class PluralizationHelper {
    
    public String pluralize(String language, String singular, String plural, int count) {
        switch (language) {
            case "en":
                return count == 1 ? singular : plural;
            case "pl":
                return pluralizePolish(singular, plural, count);
            case "de":
                return pluralizeGerman(singular, plural, count);
            case "fr":
                return pluralizeFrench(singular, plural, count);
            default:
                return count == 1 ? singular : plural;
        }
    }
    
    private String pluralizePolish(String singular, String plural, int count) {
        if (count == 1) return singular;
        
        // Polish pluralization rules are complex
        // This is a simplified version
        return plural;
    }
    
    private String pluralizeGerman(String singular, String plural, int count) {
        if (count == 1) return singular;
        return plural;
    }
    
    private String pluralizeFrench(String singular, String plural, int count) {
        if (count <= 1) return singular;
        return plural;
    }
}
```

### Date and Time Formatting

```java
public class DateTimeFormatter {
    
    public String formatDate(String language, Date date) {
        Locale locale = getLocaleForLanguage(language);
        java.text.DateFormat formatter = java.text.DateFormat.getDateInstance(
            java.text.DateFormat.MEDIUM, locale);
        return formatter.format(date);
    }
    
    public String formatTime(String language, Date date) {
        Locale locale = getLocaleForLanguage(language);
        java.text.DateFormat formatter = java.text.DateFormat.getTimeInstance(
            java.text.DateFormat.SHORT, locale);
        return formatter.format(date);
    }
    
    public String formatDateTime(String language, Date date) {
        Locale locale = getLocaleForLanguage(language);
        java.text.DateFormat formatter = java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale);
        return formatter.format(date);
    }
    
    public String formatRelativeTime(String language, long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        if (diff < 60000) { // Less than 1 minute
            return getMessage(language, "time.just-now");
        } else if (diff < 3600000) { // Less than 1 hour
            int minutes = (int) (diff / 60000);
            return getMessage(language, "time.minutes-ago", Map.of("count", String.valueOf(minutes)));
        } else if (diff < 86400000) { // Less than 1 day
            int hours = (int) (diff / 3600000);
            return getMessage(language, "time.hours-ago", Map.of("count", String.valueOf(hours)));
        } else {
            int days = (int) (diff / 86400000);
            return getMessage(language, "time.days-ago", Map.of("count", String.valueOf(days)));
        }
    }
    
    private Locale getLocaleForLanguage(String language) {
        switch (language) {
            case "pl": return Locale.forLanguageTag("pl");
            case "de": return Locale.forLanguageTag("de");
            case "fr": return Locale.forLanguageTag("fr");
            default: return Locale.ENGLISH;
        }
    }
    
    private String getMessage(String language, String key) {
        return getMessage(language, key, Map.of());
    }
    
    private String getMessage(String language, String key, Map<String, String> placeholders) {
        // This would integrate with your message system
        return key; // Placeholder
    }
}
```

### Currency Formatting

```java
public class CurrencyFormatter {
    
    public String formatCurrency(String language, double amount, String currencyCode) {
        Locale locale = getLocaleForLanguage(language);
        NumberFormat formatter = NumberFormat.getCurrencyInstance(locale);
        
        // Set currency
        Currency currency = Currency.getInstance(currencyCode);
        formatter.setCurrency(currency);
        
        return formatter.format(amount);
    }
    
    public String formatCurrency(String language, double amount) {
        // Default to USD for English, PLN for Polish, etc.
        String currencyCode = getDefaultCurrency(language);
        return formatCurrency(language, amount, currencyCode);
    }
    
    private String getDefaultCurrency(String language) {
        switch (language) {
            case "pl": return "PLN";
            case "de": return "EUR";
            case "fr": return "EUR";
            default: return "USD";
        }
    }
    
    private Locale getLocaleForLanguage(String language) {
        switch (language) {
            case "pl": return Locale.forLanguageTag("pl");
            case "de": return Locale.forLanguageTag("de");
            case "fr": return Locale.forLanguageTag("fr");
            default: return Locale.ENGLISH;
        }
    }
}
```

---

## Complete Plugin Example

### Main Plugin Class

```java
public class MultilingualPlugin extends JavaPlugin {
    
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private MessageManager messageManager;
    private LanguageSelectionGUI languageGUI;
    
    @Override
    public void onEnable() {
        // Initialize MongoDB Configs API
        MongoConfigsAPI.initialize(this);
        
        // Initialize managers
        configManager = MongoConfigsAPI.getConfigManager();
        languageManager = new LanguageManager();
        
        // Load multilingual messages
        MultilingualMessages messages = configManager.loadObject(MultilingualMessages.class);
        messageManager = new MessageManager(messages, languageManager);
        
        // Initialize GUI
        languageGUI = new LanguageSelectionGUI(this);
        
        // Register commands
        getCommand("lang").setExecutor(new LanguageCommand(languageGUI));
        getCommand("welcome").setExecutor(new WelcomeCommand());
        
        // Register events
        getServer().getPluginManager().registerEvents(languageGUI, this);
        
        getLogger().info("MultilingualPlugin enabled!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("MultilingualPlugin disabled!");
    }
    
    public LanguageManager getLanguageManager() {
        return languageManager;
    }
    
    public MessageManager getMessageManager() {
        return messageManager;
    }
    
    public LanguageSelectionGUI getLanguageGUI() {
        return languageGUI;
    }
}
```

### Welcome Command with Translation

```java
public class WelcomeCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        MultilingualPlugin plugin = (MultilingualPlugin) Bukkit.getPluginManager().getPlugin("MultilingualPlugin");
        MessageManager messageManager = plugin.getMessageManager();
        
        // Send welcome message in player's language
        messageManager.sendMessage(player, "welcome");
        
        // Send additional info
        Map<String, String> placeholders = Map.of(
            "player", player.getName(),
            "online", String.valueOf(Bukkit.getOnlinePlayers().size())
        );
        
        messageManager.sendMessage(player, "server-info", placeholders);
        
        return true;
    }
}
```

### Plugin Configuration

```yaml
# plugin.yml
name: MultilingualPlugin
version: 1.0.0
main: com.example.MultilingualPlugin
api-version: 1.19

commands:
  lang:
    description: Change your language
    usage: /lang [language]
  welcome:
    description: Get a welcome message
    usage: /welcome

permissions:
  multilingual.lang:
    description: Allows changing language
    default: true
  multilingual.admin:
    description: Admin permissions for language management
    default: op
```

---

## Best Practices

### 1. Message Key Naming Convention

```java
// ✅ Good naming convention
messages.put("player.join", "Player {player} joined the game!");
messages.put("player.quit", "Player {player} left the game!");
messages.put("command.usage", "Usage: {usage}");
messages.put("error.no-permission", "You don't have permission!");

// ❌ Avoid generic names
messages.put("msg1", "Welcome!");
messages.put("text", "Error occurred!");
```

### 2. Placeholder Consistency

```java
// ✅ Consistent placeholder naming
messages.put("teleport.success", "Teleported {player} to {location}!");
messages.put("teleport.failed", "Failed to teleport {player} to {location}!");
messages.put("balance.show", "{player} has {amount} {currency}!");

// ❌ Inconsistent placeholders
messages.put("teleport.success", "Teleported {name} to {loc}!");
messages.put("balance.show", "Player {p} has {money} coins!");
```

### 3. Fallback Handling

```java
public String getMessageSafe(String language, String key) {
    try {
        String message = getMessage(language, key);
        return message != null ? message : getFallbackMessage(key);
    } catch (Exception e) {
        return getFallbackMessage(key);
    }
}

private String getFallbackMessage(String key) {
    // Always have English fallbacks
    return englishMessages.getOrDefault(key, "&cMessage not found: " + key);
}
```

### 4. Performance Optimization

```java
public class MessageCache {
    
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final MultilingualMessages messages;
    
    public MessageCache(MultilingualMessages messages) {
        this.messages = messages;
        preloadCache();
    }
    
    private void preloadCache() {
        for (String language : messages.getSupportedLanguages()) {
            Map<String, String> languageCache = new HashMap<>();
            // Preload all messages
            cache.put(language, languageCache);
        }
    }
    
    public String getCachedMessage(String language, String key) {
        Map<String, String> languageCache = cache.get(language);
        if (languageCache == null) {
            languageCache = cache.get("en"); // Fallback
        }
        return languageCache.get(key);
    }
}
```

### 5. Language File Organization

```
messages/
├── en.yml     # English messages
├── pl.yml     # Polish messages
├── de.yml     # German messages
└── fr.yml     # French messages
```

### 6. Testing Translations

```java
public class TranslationTest {
    
    @Test
    public void testAllLanguagesHaveRequiredMessages() {
        Set<String> requiredKeys = Set.of("welcome", "goodbye", "error");
        
        for (String language : messages.getSupportedLanguages()) {
            for (String key : requiredKeys) {
                String message = messages.getMessage(key, language);
                assertNotNull("Missing message for key: " + key + " in language: " + language, message);
            }
        }
    }
    
    @Test
    public void testPlaceholdersAreReplaced() {
        String template = "Hello {name}!";
        Map<String, String> placeholders = Map.of("name", "World");
        
        String result = replacePlaceholders(template, placeholders);
        assertEquals("Hello World!", result);
    }
}
```

---

*Next: Learn about [[Multi-Server Architecture]] for advanced server synchronization patterns.*