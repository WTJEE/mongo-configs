# Messages API

Complete reference for the Messages system - the interface for retrieving and managing multilingual messages with dynamic placeholders.

## 🎯 Getting Messages Instance

```java
// Get Messages instance for specific message class
ConfigManager cm = MongoConfigsAPI.getConfigManager();
Messages guiMessages = cm.messagesOf(GuiMessages.class);
Messages shopMessages = cm.messagesOf(ShopMessages.class);
Messages commandMessages = cm.messagesOf(CommandMessages.class);
```

---

## 📝 Basic Message Retrieval

### `get(String language, String key)`
Retrieves a message for a specific language and key.

```java
// Basic message retrieval
Messages guiMessages = cm.messagesOf(GuiMessages.class);

String welcomeEN = guiMessages.get("en", "welcome.message");
String welcomePL = guiMessages.get("pl", "welcome.message");
String welcomeES = guiMessages.get("es", "welcome.message");

// Example outputs:
// EN: "Welcome to the server!"
// PL: "Witamy na serwerze!"
// ES: "¡Bienvenido al servidor!"
```

### `get(String language, String key, Object... args)`
Retrieves a message with dynamic placeholder replacement.

```java
// Message with placeholders
Messages guiMessages = cm.messagesOf(GuiMessages.class);

// Single placeholder
String playerWelcome = guiMessages.get("en", "welcome.player", "Steve");
// Result: "Welcome, Steve!"

String playerWelcomePL = guiMessages.get("pl", "welcome.player", "Steve");
// Result: "Witaj, Steve!"

// Multiple placeholders
String shopPurchase = guiMessages.get("en", "shop.purchase.success", "Diamond Sword", 100, "coins");
// Result: "You purchased Diamond Sword for 100 coins!"

String shopPurchasePL = guiMessages.get("pl", "shop.purchase.success", "Diamond Sword", 100, "monet");
// Result: "Kupiłeś Diamond Sword za 100 monet!"
```

### Advanced Placeholder Examples

```java
public class MessageExamples {
    
    private final Messages guiMessages;
    private final Messages shopMessages;
    private final LanguageManager languageManager;
    
    public MessageExamples() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        this.guiMessages = cm.messagesOf(GuiMessages.class);
        this.shopMessages = cm.messagesOf(ShopMessages.class);
        this.languageManager = MongoConfigsAPI.getLanguageManager();
    }
    
    public void sendPlayerWelcome(Player player) {
        String playerId = player.getUniqueId().toString();
        String language = languageManager.getPlayerLanguage(playerId);
        if (language == null) {
            language = languageManager.getDefaultLanguage();
        }
        
        // Get player stats for placeholders
        int level = getPlayerLevel(player);
        int coins = getPlayerCoins(player);
        String rank = getPlayerRank(player);
        
        // Welcome message with multiple placeholders
        String message = guiMessages.get(language, "welcome.detailed", 
            player.getName(),           // {0}
            level,                      // {1}
            coins,                      // {2}
            rank,                       // {3}
            Bukkit.getOnlinePlayers().size()  // {4}
        );
        
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    public void sendShopTransaction(Player player, String itemName, int amount, int price, String currency) {
        String playerId = player.getUniqueId().toString();
        String language = languageManager.getPlayerLanguage(playerId);
        if (language == null) {
            language = languageManager.getDefaultLanguage();
        }
        
        // Shop purchase confirmation
        String message = shopMessages.get(language, "shop.purchase.detailed",
            itemName,           // {0} - Item name
            amount,             // {1} - Quantity
            price,              // {2} - Total price
            currency,           // {3} - Currency type
            getPlayerCoins(player) - price  // {4} - Remaining balance
        );
        
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    public void sendTimeBasedGreeting(Player player) {
        String playerId = player.getUniqueId().toString();
        String language = languageManager.getPlayerLanguage(playerId);
        if (language == null) {
            language = languageManager.getDefaultLanguage();
        }
        
        // Get current time
        LocalTime currentTime = LocalTime.now();
        String timeOfDay = getTimeOfDay(currentTime);
        String formattedTime = currentTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        
        String message = guiMessages.get(language, "greeting." + timeOfDay,
            player.getName(),   // {0}
            formattedTime       // {1}
        );
        
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    private String getTimeOfDay(LocalTime time) {
        if (time.isBefore(LocalTime.of(12, 0))) {
            return "morning";
        } else if (time.isBefore(LocalTime.of(18, 0))) {
            return "afternoon";
        } else {
            return "evening";
        }
    }
    
    // Helper methods (would be implemented based on your plugin)
    private int getPlayerLevel(Player player) { return 10; }
    private int getPlayerCoins(Player player) { return 500; }
    private String getPlayerRank(Player player) { return "VIP"; }
}
```

---

## 🎯 Player-Specific Message Helpers

### Convenience Methods for Player Messaging

```java
public class PlayerMessageHelper {
    
    private final Messages guiMessages;
    private final Messages shopMessages;
    private final Messages commandMessages;
    private final LanguageManager languageManager;
    
    public PlayerMessageHelper() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        this.guiMessages = cm.messagesOf(GuiMessages.class);
        this.shopMessages = cm.messagesOf(ShopMessages.class);
        this.commandMessages = cm.messagesOf(CommandMessages.class);
        this.languageManager = MongoConfigsAPI.getLanguageManager();
    }
    
    // Send GUI message to player in their language
    public void sendGuiMessage(Player player, String key, Object... args) {
        String language = getPlayerLanguage(player);
        String message = guiMessages.get(language, key, args);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    // Send shop message to player in their language
    public void sendShopMessage(Player player, String key, Object... args) {
        String language = getPlayerLanguage(player);
        String message = shopMessages.get(language, key, args);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    // Send command message to player in their language
    public void sendCommandMessage(Player player, String key, Object... args) {
        String language = getPlayerLanguage(player);
        String message = commandMessages.get(language, key, args);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    // Get message without sending
    public String getGuiMessage(Player player, String key, Object... args) {
        String language = getPlayerLanguage(player);
        return guiMessages.get(language, key, args);
    }
    
    public String getShopMessage(Player player, String key, Object... args) {
        String language = getPlayerLanguage(player);
        return shopMessages.get(language, key, args);
    }
    
    public String getCommandMessage(Player player, String key, Object... args) {
        String language = getPlayerLanguage(player);
        return commandMessages.get(language, key, args);
    }
    
    // Broadcast message to all players in their respective languages
    public void broadcastGuiMessage(String key, Object... args) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendGuiMessage(player, key, args);
        }
    }
    
    public void broadcastShopMessage(String key, Object... args) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendShopMessage(player, key, args);
        }
    }
    
    // Send message to players with specific permission
    public void broadcastToPermission(String permission, Messages messageSystem, String key, Object... args) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                String language = getPlayerLanguage(player);
                String message = messageSystem.get(language, key, args);
                player.sendMessage(ColorHelper.parseComponent(message));
            }
        }
    }
    
    // Send message to players in specific world
    public void broadcastToWorld(World world, Messages messageSystem, String key, Object... args) {
        for (Player player : world.getPlayers()) {
            String language = getPlayerLanguage(player);
            String message = messageSystem.get(language, key, args);
            player.sendMessage(ColorHelper.parseComponent(message));
        }
    }
    
    private String getPlayerLanguage(Player player) {
        String playerId = player.getUniqueId().toString();
        String language = languageManager.getPlayerLanguage(playerId);
        return language != null ? language : languageManager.getDefaultLanguage();
    }
}
```

---

## 📋 GUI Integration

### Creating Multilingual GUI Components

```java
public class MultilingualShopGUI {
    
    private final Messages shopMessages;
    private final LanguageManager languageManager;
    
    public MultilingualShopGUI() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        this.shopMessages = cm.messagesOf(ShopMessages.class);
        this.languageManager = MongoConfigsAPI.getLanguageManager();
    }
    
    public void openShopGUI(Player player) {
        String language = getPlayerLanguage(player);
        
        // Get localized GUI title
        String title = shopMessages.get(language, "gui.shop.title");
        Inventory inventory = Bukkit.createInventory(null, 54, title);
        
        // Create items with localized names and lore
        createShopItem(inventory, 10, Material.DIAMOND_SWORD, "shop.items.diamond_sword", language, 100);
        createShopItem(inventory, 11, Material.DIAMOND_PICKAXE, "shop.items.diamond_pickaxe", language, 150);
        createShopItem(inventory, 12, Material.DIAMOND_ARMOR, "shop.items.diamond_armor", language, 500);
        
        // Add navigation items
        createNavigationItem(inventory, 45, Material.ARROW, "gui.shop.previous_page", language);
        createNavigationItem(inventory, 53, Material.ARROW, "gui.shop.next_page", language);
        
        // Add info item
        createInfoItem(inventory, 49, Material.BOOK, "gui.shop.info", language, player.getName());
        
        player.openInventory(inventory);
    }
    
    private void createShopItem(Inventory inventory, int slot, Material material, String nameKey, String language, int price) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // Set localized display name
        String displayName = shopMessages.get(language, nameKey + ".name");
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        // Set localized lore
        List<String> lore = new ArrayList<>();
        lore.add(shopMessages.get(language, nameKey + ".description"));
        lore.add("");
        lore.add(shopMessages.get(language, "gui.shop.price", price));
        lore.add(shopMessages.get(language, "gui.shop.click_to_buy"));
        
        meta.setLore(lore.stream()
            .map(ColorHelper::parseString)
            .collect(Collectors.toList()));
        
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    private void createNavigationItem(Inventory inventory, int slot, Material material, String nameKey, String language) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        String displayName = shopMessages.get(language, nameKey);
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    private void createInfoItem(Inventory inventory, int slot, Material material, String nameKey, String language, String playerName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        String displayName = shopMessages.get(language, nameKey + ".name");
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        List<String> lore = new ArrayList<>();
        lore.add(shopMessages.get(language, nameKey + ".welcome", playerName));
        lore.add(shopMessages.get(language, nameKey + ".instructions"));
        
        meta.setLore(lore.stream()
            .map(ColorHelper::parseString)
            .collect(Collectors.toList()));
        
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    private String getPlayerLanguage(Player player) {
        String playerId = player.getUniqueId().toString();
        String language = languageManager.getPlayerLanguage(playerId);
        return language != null ? language : languageManager.getDefaultLanguage();
    }
}
```

---

## 🔤 Advanced Message Formatting

### Rich Text and Color Support

```java
public class RichMessageFormatter {
    
    private final Messages messages;
    private final LanguageManager languageManager;
    
    public RichMessageFormatter(Messages messages) {
        this.messages = messages;
        this.languageManager = MongoConfigsAPI.getLanguageManager();
    }
    
    public void sendFormattedMessage(Player player, String key, Object... args) {
        String language = getPlayerLanguage(player);
        String rawMessage = messages.get(language, key, args);
        
        // Parse colors and components
        Component formattedMessage = ColorHelper.parseComponent(rawMessage);
        player.sendMessage(formattedMessage);
    }
    
    public void sendActionBarMessage(Player player, String key, Object... args) {
        String language = getPlayerLanguage(player);
        String rawMessage = messages.get(language, key, args);
        
        Component actionBarMessage = ColorHelper.parseComponent(rawMessage);
        player.sendActionBar(actionBarMessage);
    }
    
    public void sendTitleMessage(Player player, String titleKey, String subtitleKey, Object... args) {
        String language = getPlayerLanguage(player);
        
        String titleText = messages.get(language, titleKey, args);
        String subtitleText = messages.get(language, subtitleKey, args);
        
        Component title = ColorHelper.parseComponent(titleText);
        Component subtitle = ColorHelper.parseComponent(subtitleText);
        
        player.showTitle(Title.title(title, subtitle));
    }
    
    public void sendClickableMessage(Player player, String messageKey, String command, String hoverKey, Object... args) {
        String language = getPlayerLanguage(player);
        
        String messageText = messages.get(language, messageKey, args);
        String hoverText = messages.get(language, hoverKey, args);
        
        Component message = Component.text(ColorHelper.parseString(messageText))
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(ColorHelper.parseString(hoverText))));
        
        player.sendMessage(message);
    }
    
    private String getPlayerLanguage(Player player) {
        String playerId = player.getUniqueId().toString();
        String language = languageManager.getPlayerLanguage(playerId);
        return language != null ? language : languageManager.getDefaultLanguage();
    }
}

// Usage examples:
public void exampleUsage(Player player) {
    ConfigManager cm = MongoConfigsAPI.getConfigManager();
    Messages guiMessages = cm.messagesOf(GuiMessages.class);
    
    RichMessageFormatter formatter = new RichMessageFormatter(guiMessages);
    
    // Send formatted chat message
    formatter.sendFormattedMessage(player, "welcome.rich", player.getName());
    
    // Send action bar message
    formatter.sendActionBarMessage(player, "actionbar.coins", getPlayerCoins(player));
    
    // Send title and subtitle
    formatter.sendTitleMessage(player, "title.level_up", "subtitle.level_up", getPlayerLevel(player));
    
    // Send clickable message
    formatter.sendClickableMessage(player, 
        "gui.shop.advertisement",           // Message text key
        "/shop",                            // Command to run
        "gui.shop.click_hint",             // Hover text key
        player.getName()                    // Message args
    );
}
```

---

## 📊 Message Statistics and Debugging

### Message Usage Analytics

```java
public class MessageAnalytics {
    
    private final Map<String, Integer> messageUsageCount = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> missingMessages = new ConcurrentHashMap<>();
    private final Messages messages;
    
    public MessageAnalytics(Messages messages) {
        this.messages = messages;
    }
    
    public String getTrackedMessage(String language, String key, Object... args) {
        // Track usage
        String usageKey = language + ":" + key;
        messageUsageCount.merge(usageKey, 1, Integer::sum);
        
        // Get message
        String message = messages.get(language, key, args);
        
        // Check if message is missing (using fallback)
        if (message.startsWith("Missing message:") || message.equals(key)) {
            missingMessages.computeIfAbsent(language, k -> new HashSet<>()).add(key);
        }
        
        return message;
    }
    
    public void printUsageStatistics(CommandSender sender) {
        sender.sendMessage("§a📊 Message Usage Statistics:");
        sender.sendMessage("§e─────────────────────────────");
        
        messageUsageCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> {
                String[] parts = entry.getKey().split(":", 2);
                String language = parts[0];
                String key = parts[1];
                int count = entry.getValue();
                
                sender.sendMessage(String.format("§f%s §7[%s]: §b%d uses", key, language, count));
            });
        
        sender.sendMessage("§e─────────────────────────────");
        sender.sendMessage("§aTotal tracked messages: §b" + messageUsageCount.size());
    }
    
    public void printMissingMessages(CommandSender sender) {
        sender.sendMessage("§c⚠️ Missing Messages Report:");
        sender.sendMessage("§e─────────────────────────────");
        
        if (missingMessages.isEmpty()) {
            sender.sendMessage("§a✅ No missing messages found!");
            return;
        }
        
        missingMessages.forEach((language, keys) -> {
            sender.sendMessage("§c❌ Language: §f" + language.toUpperCase());
            keys.forEach(key -> sender.sendMessage("  §7- " + key));
            sender.sendMessage("");
        });
        
        sender.sendMessage("§e─────────────────────────────");
    }
    
    public Set<String> getMissingKeysForLanguage(String language) {
        return missingMessages.getOrDefault(language, Collections.emptySet());
    }
    
    public void clearStatistics() {
        messageUsageCount.clear();
        missingMessages.clear();
    }
}
```

### Message Validation

```java
public class MessageValidator {
    
    private final Messages messages;
    private final LanguageManager languageManager;
    
    public MessageValidator(Messages messages) {
        this.messages = messages;
        this.languageManager = MongoConfigsAPI.getLanguageManager();
    }
    
    public ValidationReport validateAllMessages() {
        ValidationReport report = new ValidationReport();
        Set<String> supportedLanguages = languageManager.getSupportedLanguages();
        
        // Define required message keys (this would be configured)
        Set<String> requiredKeys = Set.of(
            "welcome.message",
            "gui.shop.title",
            "gui.shop.price",
            "error.no_permission",
            "error.unknown_command"
        );
        
        for (String language : supportedLanguages) {
            for (String key : requiredKeys) {
                String message = messages.get(language, key);
                
                if (message == null || message.trim().isEmpty() || message.equals(key)) {
                    report.addMissingMessage(language, key);
                } else {
                    // Validate placeholder format
                    validatePlaceholders(message, language, key, report);
                }
            }
        }
        
        return report;
    }
    
    private void validatePlaceholders(String message, String language, String key, ValidationReport report) {
        // Check for malformed placeholders
        Pattern placeholderPattern = Pattern.compile("\\{(\\d+)\\}");
        Matcher matcher = placeholderPattern.matcher(message);
        
        Set<Integer> foundPlaceholders = new HashSet<>();
        while (matcher.find()) {
            int placeholderIndex = Integer.parseInt(matcher.group(1));
            foundPlaceholders.add(placeholderIndex);
        }
        
        // Check for sequential placeholders (0, 1, 2, ...)
        if (!foundPlaceholders.isEmpty()) {
            int maxIndex = Collections.max(foundPlaceholders);
            for (int i = 0; i <= maxIndex; i++) {
                if (!foundPlaceholders.contains(i)) {
                    report.addPlaceholderWarning(language, key, "Missing placeholder {" + i + "}");
                }
            }
        }
    }
    
    public static class ValidationReport {
        private final Map<String, Set<String>> missingMessages = new HashMap<>();
        private final Map<String, List<String>> placeholderWarnings = new HashMap<>();
        
        public void addMissingMessage(String language, String key) {
            missingMessages.computeIfAbsent(language, k -> new HashSet<>()).add(key);
        }
        
        public void addPlaceholderWarning(String language, String key, String warning) {
            String warningKey = language + ":" + key;
            placeholderWarnings.computeIfAbsent(warningKey, k -> new ArrayList<>()).add(warning);
        }
        
        public boolean hasIssues() {
            return !missingMessages.isEmpty() || !placeholderWarnings.isEmpty();
        }
        
        public void printReport(CommandSender sender) {
            sender.sendMessage("§a🔍 Message Validation Report:");
            sender.sendMessage("§e════════════════════════════");
            
            if (!hasIssues()) {
                sender.sendMessage("§a✅ All messages are valid!");
                return;
            }
            
            // Print missing messages
            if (!missingMessages.isEmpty()) {
                sender.sendMessage("§c❌ Missing Messages:");
                missingMessages.forEach((language, keys) -> {
                    sender.sendMessage("  §f" + language.toUpperCase() + ":");
                    keys.forEach(key -> sender.sendMessage("    §7- " + key));
                });
                sender.sendMessage("");
            }
            
            // Print placeholder warnings
            if (!placeholderWarnings.isEmpty()) {
                sender.sendMessage("§e⚠️ Placeholder Warnings:");
                placeholderWarnings.forEach((key, warnings) -> {
                    sender.sendMessage("  §f" + key + ":");
                    warnings.forEach(warning -> sender.sendMessage("    §7- " + warning));
                });
            }
            
            sender.sendMessage("§e════════════════════════════");
        }
    }
}
```

---

## 🎯 Best Practices

### 1. Message Key Conventions

```java
// ✅ Good - Hierarchical key structure
"gui.shop.title"
"gui.shop.items.diamond_sword.name"
"gui.shop.items.diamond_sword.description"
"gui.shop.purchase.success"
"gui.shop.purchase.error.insufficient_funds"

"commands.shop.help"
"commands.shop.error.no_permission"
"commands.shop.success.item_purchased"

"errors.general.unknown_error"
"errors.database.connection_failed"
"errors.player.not_found"
```

### 2. Safe Message Retrieval

```java
public class SafeMessageRetrieval {
    
    private final Messages messages;
    private final LanguageManager languageManager;
    
    public String getSafeMessage(Player player, String key, Object... args) {
        try {
            String language = languageManager.getPlayerLanguage(player.getUniqueId().toString());
            if (language == null) {
                language = languageManager.getDefaultLanguage();
            }
            return messages.get(language, key, args);
        } catch (Exception e) {
            getLogger().warning("Failed to get message for key: " + key + " - " + e.getMessage());
            return "Message error: " + key;
        }
    }
    
    public String getSafeMessageWithFallback(Player player, String key, String fallback, Object... args) {
        try {
            String language = languageManager.getPlayerLanguage(player.getUniqueId().toString());
            if (language == null) {
                language = languageManager.getDefaultLanguage();
            }
            String message = messages.get(language, key, args);
            
            // Check if message retrieval failed
            if (message == null || message.trim().isEmpty() || message.equals(key)) {
                return fallback;
            }
            
            return message;
        } catch (Exception e) {
            getLogger().warning("Failed to get message for key: " + key + " - " + e.getMessage());
            return fallback;
        }
    }
}
```

### 3. Efficient Message Caching

```java
public class CachedMessageProvider {
    
    private final Messages messages;
    private final LanguageManager languageManager;
    private final Map<String, String> messageCache = new ConcurrentHashMap<>();
    
    public String getCachedMessage(Player player, String key, Object... args) {
        String language = languageManager.getPlayerLanguage(player.getUniqueId().toString());
        if (language == null) {
            language = languageManager.getDefaultLanguage();
        }
        
        // Create cache key (without args for non-parameterized messages)
        String cacheKey = language + ":" + key;
        
        if (args.length == 0) {
            // Use cache for messages without parameters
            return messageCache.computeIfAbsent(cacheKey, k -> messages.get(language, key));
        } else {
            // Don't cache parameterized messages
            return messages.get(language, key, args);
        }
    }
    
    public void clearCache() {
        messageCache.clear();
    }
    
    public void clearCacheForLanguage(String language) {
        messageCache.entrySet().removeIf(entry -> entry.getKey().startsWith(language + ":"));
    }
}
```

---

*Next: Learn about [[MongoDB Integration]] for database configuration and setup.*