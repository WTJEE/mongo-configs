# GUI Development

> **Creating multilingual graphical user interfaces with MongoDB Configs API**

## Table of Contents
- [Overview](#overview)
- [Language Selection GUI](#language-selection-gui)
- [Dynamic GUI Creation](#dynamic-gui-creation)
- [Message Integration](#message-integration)
- [Color Processing](#color-processing)
- [Interactive Elements](#interactive-elements)
- [Best Practices](#best-practices)
- [Complete Examples](#complete-examples)

---

## Overview

MongoDB Configs API provides comprehensive support for creating multilingual GUIs with automatic message loading, color processing, and player language detection.

### Key Features
- **Automatic Language Detection** - Uses player's selected language
- **Dynamic Message Loading** - Messages loaded from MongoDB
- **Color Processing** - Support for hex colors, gradients, and legacy colors
- **Interactive Elements** - Click handlers and item management
- **Performance Optimized** - Cached translations and efficient rendering

---

## Language Selection GUI

### Basic Implementation

```java
public class LanguageSelectionGUI {
    
    private final LanguageManager languageManager;
    private final Messages messages;
    private final Map<UUID, Inventory> playerGUIs = new ConcurrentHashMap<>();
    
    public LanguageSelectionGUI(LanguageManager languageManager, Messages messages) {
        this.languageManager = languageManager;
        this.messages = messages;
    }
    
    public void open(Player player) {
        String playerId = player.getUniqueId().toString();
        String currentLanguage = languageManager.getPlayerLanguage(playerId);
        
        Inventory inventory = createLanguageGUI(player, currentLanguage);
        playerGUIs.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
    }
    
    private Inventory createLanguageGUI(Player player, String currentLanguage) {
        // Create 27-slot inventory (3 rows)
        Inventory inventory = Bukkit.createInventory(null, 27, 
            messages.get(currentLanguage, "gui.language.title"));
        
        // Add language items
        addLanguageItems(inventory, currentLanguage);
        
        // Add navigation items
        addNavigationItems(inventory, currentLanguage);
        
        return inventory;
    }
    
    private void addLanguageItems(Inventory inventory, String currentLanguage) {
        String[] languages = languageManager.getSupportedLanguages();
        int slot = 10; // Start at second row, second column
        
        for (String language : languages) {
            if (slot >= 17) break; // Don't overflow the GUI
            
            ItemStack languageItem = createLanguageItem(language, currentLanguage);
            inventory.setItem(slot, languageItem);
            slot += 2; // Leave space between items
        }
    }
    
    private ItemStack createLanguageItem(String language, String currentLanguage) {
        Material material = getLanguageMaterial(language);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // Set display name with color
        String displayName = messages.get(language, "language.name");
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        // Create lore
        List<String> lore = new ArrayList<>();
        
        // Current selection indicator
        if (language.equals(currentLanguage)) {
            lore.add(messages.get(language, "gui.language.selected"));
        } else {
            lore.add(messages.get(language, "gui.language.click_to_select"));
        }
        
        // Language description
        String description = messages.get(language, "language.description");
        if (description != null && !description.isEmpty()) {
            lore.add("");
            lore.add(ColorHelper.parseString("&7" + description));
        }
        
        meta.setLore(lore.stream()
            .map(ColorHelper::parseString)
            .collect(Collectors.toList()));
        
        // Add persistent data for click handling
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(new NamespacedKey(getPlugin(), "language"), 
                PersistentDataType.STRING, language);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private void addNavigationItems(Inventory inventory, String currentLanguage) {
        // Close button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ColorHelper.parseString(
            messages.get(currentLanguage, "gui.close")));
        
        List<String> closeLore = Arrays.asList(
            messages.get(currentLanguage, "gui.close.description")
        );
        closeMeta.setLore(closeLore.stream()
            .map(ColorHelper::parseString)
            .collect(Collectors.toList()));
        
        closeItem.setItemMeta(closeMeta);
        inventory.setItem(22, closeItem); // Bottom right corner
    }
    
    private Material getLanguageMaterial(String language) {
        return switch (language) {
            case "en" -> Material.WHITE_WOOL;    // UK flag colors
            case "pl" -> Material.RED_WOOL;      // Poland flag
            case "de" -> Material.YELLOW_WOOL;   // Germany flag
            case "fr" -> Material.BLUE_WOOL;     // France flag
            case "es" -> Material.ORANGE_WOOL;   // Spain flag
            default -> Material.GRAY_WOOL;
        };
    }
    
    private JavaPlugin getPlugin() {
        // Return your plugin instance
        return null;
    }
}
```

### GUI Listener

```java
public class LanguageGUIListener implements Listener {
    
    private final LanguageSelectionGUI gui;
    
    public LanguageGUIListener(LanguageSelectionGUI gui) {
        this.gui = gui;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().getTitle();
        if (!title.contains("Language") && !title.contains("Język") && 
            !title.contains("Sprache")) return; // Multi-language title check
        
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;
        
        PersistentDataContainer data = meta.getPersistentDataContainer();
        NamespacedKey languageKey = new NamespacedKey(gui.getPlugin(), "language");
        
        if (data.has(languageKey, PersistentDataType.STRING)) {
            String selectedLanguage = data.get(languageKey, PersistentDataType.STRING);
            handleLanguageSelection(player, selectedLanguage);
        } else if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
        }
    }
    
    private void handleLanguageSelection(Player player, String language) {
        String playerId = player.getUniqueId().toString();
        String currentLanguage = gui.getLanguageManager().getPlayerLanguage(playerId);
        
        if (language.equals(currentLanguage)) {
            // Already selected language
            String message = gui.getMessages().get(currentLanguage, 
                "language.already_selected", language);
            player.sendMessage(ColorHelper.parseComponent(message));
            return;
        }
        
        // Set new language
        gui.getLanguageManager().setPlayerLanguage(playerId, language);
        
        // Send confirmation message in new language
        String confirmMessage = gui.getMessages().get(language, 
            "language.changed", language);
        player.sendMessage(ColorHelper.parseComponent(confirmMessage));
        
        // Close and reopen GUI to refresh
        player.closeInventory();
        gui.open(player);
    }
}
```

---

## Dynamic GUI Creation

### Shop GUI with Dynamic Items

```java
public class ShopGUI {
    
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final Messages messages;
    
    public ShopGUI(ConfigManager configManager, LanguageManager languageManager, 
                  Messages messages) {
        this.configManager = configManager;
        this.languageManager = languageManager;
        this.messages = messages;
    }
    
    public void openShop(Player player) {
        String language = languageManager.getPlayerLanguageOrDefault(
            player.getUniqueId().toString());
        
        // Load shop configuration
        ShopConfig shopConfig = configManager.loadObject(ShopConfig.class);
        
        // Create dynamic inventory
        String title = messages.get(language, "shop.title");
        Inventory inventory = Bukkit.createInventory(null, 54, title);
        
        // Add shop items dynamically
        addShopItems(inventory, shopConfig, language);
        
        // Add player info
        addPlayerInfo(inventory, player, language);
        
        player.openInventory(inventory);
    }
    
    private void addShopItems(Inventory inventory, ShopConfig shopConfig, String language) {
        int slot = 0;
        
        for (ShopConfig.ShopCategory category : shopConfig.getCategories().values()) {
            for (ShopConfig.ShopItem item : category.getItems()) {
                if (slot >= 45) break; // Reserve bottom row for navigation
                
                ItemStack shopItem = createShopItem(item, language);
                inventory.setItem(slot, shopItem);
                slot++;
            }
        }
    }
    
    private ItemStack createShopItem(ShopConfig.ShopItem item, String language) {
        ItemStack shopItem = new ItemStack(item.getMaterial());
        ItemMeta meta = shopItem.getItemMeta();
        
        // Set display name
        meta.setDisplayName(ColorHelper.parseString(item.getDisplayName()));
        
        // Create lore with price and description
        List<String> lore = new ArrayList<>();
        
        // Price
        String priceLine = messages.get(language, "shop.item.price", 
            item.getPrice(), getCurrencySymbol());
        lore.add(ColorHelper.parseString(priceLine));
        
        // Description
        if (item.getLore() != null && !item.getLore().isEmpty()) {
            lore.add(""); // Empty line
            for (String line : item.getLore()) {
                lore.add(ColorHelper.parseString("&7" + line));
            }
        }
        
        // Click instruction
        lore.add("");
        String clickInstruction = messages.get(language, "shop.item.click_to_buy");
        lore.add(ColorHelper.parseString("&e" + clickInstruction));
        
        meta.setLore(lore);
        
        // Add item data for purchase handling
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(new NamespacedKey(getPlugin(), "shop_item"), 
                PersistentDataType.STRING, item.getItemId());
        data.set(new NamespacedKey(getPlugin(), "price"), 
                PersistentDataType.DOUBLE, item.getPrice());
        
        shopItem.setItemMeta(meta);
        return shopItem;
    }
    
    private void addPlayerInfo(Inventory inventory, Player player, String language) {
        // Player balance display
        ItemStack balanceItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta balanceMeta = balanceItem.getItemMeta();
        
        String balanceTitle = messages.get(language, "shop.balance.title");
        balanceMeta.setDisplayName(ColorHelper.parseString(balanceTitle));
        
        double balance = getPlayerBalance(player);
        List<String> balanceLore = Arrays.asList(
            messages.get(language, "shop.balance.amount", balance, getCurrencySymbol())
        );
        balanceMeta.setLore(balanceLore.stream()
            .map(ColorHelper::parseString)
            .collect(Collectors.toList()));
        
        inventory.setItem(49, balanceItem); // Bottom center
        
        // Close button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ColorHelper.parseString(
            messages.get(language, "gui.close")));
        closeItem.setItemMeta(closeMeta);
        inventory.setItem(53, closeItem); // Bottom right
    }
    
    private double getPlayerBalance(Player player) {
        // Integration with economy plugin
        return 1000.0; // Placeholder
    }
    
    private String getCurrencySymbol() {
        return "$"; // Placeholder
    }
    
    private JavaPlugin getPlugin() {
        return null; // Return your plugin instance
    }
}
```

---

## Message Integration

### Loading Messages from MongoDB

```java
// Message configuration class
@ConfigsFileProperties(name = "gui-messages")
@SupportedLanguages({"en", "pl", "de", "fr", "es"})
public class GUIMessages extends MongoMessages<GUIMessages> {
    // Messages are automatically loaded from MongoDB
}

// Usage in GUI
public class DynamicGUI {
    
    private final Messages guiMessages;
    
    public DynamicGUI() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        this.guiMessages = cm.messagesOf(GUIMessages.class);
    }
    
    public void createLocalizedGUI(Player player) {
        String language = getPlayerLanguage(player);
        
        // All text automatically localized
        String title = guiMessages.get(language, "gui.title");
        String closeButton = guiMessages.get(language, "gui.close");
        String confirmButton = guiMessages.get(language, "gui.confirm");
        
        // Create inventory with localized title
        Inventory inventory = Bukkit.createInventory(null, 27, title);
        
        // Add localized buttons
        addButton(inventory, 11, confirmButton, 
                 guiMessages.get(language, "gui.confirm.lore"));
        addButton(inventory, 15, closeButton, 
                 guiMessages.get(language, "gui.close.lore"));
        
        player.openInventory(inventory);
    }
    
    private void addButton(Inventory inventory, int slot, String name, String lore) {
        ItemStack button = new ItemStack(Material.PAPER);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(ColorHelper.parseString(name));
        meta.setLore(Arrays.asList(ColorHelper.parseString(lore)));
        button.setItemMeta(meta);
        inventory.setItem(slot, button);
    }
}
```

### MongoDB Message Structure

```javascript
// Collection: gui-messages
{
  "_id": "en",
  "gui": {
    "title": "&6&lShop",
    "close": "&cClose",
    "confirm": "&aConfirm",
    "cancel": "&cCancel",
    "back": "&7Back"
  },
  "shop": {
    "title": "&6&lItem Shop",
    "balance": "&eBalance: &6{balance} {currency}",
    "item": {
      "price": "&ePrice: &6{price} {currency}",
      "click_to_buy": "&7Click to purchase"
    }
  }
}

{
  "_id": "pl",
  "gui": {
    "title": "&6&lSklep",
    "close": "&cZamknij",
    "confirm": "&aPotwierdź",
    "cancel": "&cAnuluj",
    "back": "&7Wstecz"
  },
  "shop": {
    "title": "&6&lSklep z przedmiotami",
    "balance": "&eSaldo: &6{balance} {currency}",
    "item": {
      "price": "&eCena: &6{price} {currency}",
      "click_to_buy": "&7Kliknij aby kupić"
    }
  }
}
```

---

## Color Processing

### Advanced Color Formats

```java
public class ColorHelper {
    
    // Legacy colors (&c, &a, &6, etc.)
    public static String parseLegacyColors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    // Hex colors (&#FF0000)
    public static String parseHexColors(String text) {
        Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String hexColor = matcher.group(1);
            String replacement = net.md_5.bungee.api.ChatColor.of("#" + hexColor).toString();
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
    
    // RGB colors (&{255,0,0})
    public static String parseRGBColors(String text) {
        Pattern rgbPattern = Pattern.compile("&\\{(\\d+),(\\d+),(\\d+)\\}");
        Matcher matcher = rgbPattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            int r = Integer.parseInt(matcher.group(1));
            int g = Integer.parseInt(matcher.group(2));
            int b = Integer.parseInt(matcher.group(3));
            String replacement = net.md_5.bungee.api.ChatColor.of(java.awt.Color(r, g, b)).toString();
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
    
    // Gradients (gradient:#54daf4:#545eb6)
    public static String parseGradients(String text) {
        Pattern gradientPattern = Pattern.compile("gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})");
        Matcher matcher = gradientPattern.matcher(text);
        
        if (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            return applyGradient(text.replaceAll(gradientPattern.pattern(), "%s"), 
                               startHex, endHex);
        }
        
        return text;
    }
    
    // Combined parser
    public static String parseString(String text) {
        if (text == null) return "";
        return parseGradients(parseRGBColors(parseHexColors(parseLegacyColors(text))));
    }
    
    // Adventure API support
    public static Component parseComponent(String text) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacyAmpersand()
            .deserialize(parseString(text));
    }
    
    private static String applyGradient(String text, String startHex, String endHex) {
        // Remove color codes for length calculation
        String cleanText = text.replaceAll("&[0-9a-fk-or]", "");
        
        if (cleanText.isEmpty()) return text;
        
        java.awt.Color startColor = hexToColor(startHex);
        java.awt.Color endColor = hexToColor(endHex);
        
        StringBuilder result = new StringBuilder();
        int length = cleanText.length();
        
        for (int i = 0; i < length; i++) {
            double ratio = (double) i / (length - 1);
            java.awt.Color currentColor = interpolateColor(startColor, endColor, ratio);
            String hex = colorToHex(currentColor);
            
            result.append("&#").append(hex).append(cleanText.charAt(i));
        }
        
        return result.toString();
    }
    
    private static java.awt.Color hexToColor(String hex) {
        return new java.awt.Color(
            Integer.valueOf(hex.substring(0, 2), 16),
            Integer.valueOf(hex.substring(2, 4), 16),
            Integer.valueOf(hex.substring(4, 6), 16)
        );
    }
    
    private static String colorToHex(java.awt.Color color) {
        return String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
    
    private static java.awt.Color interpolateColor(java.awt.Color start, java.awt.Color end, double ratio) {
        int r = (int) (start.getRed() + (end.getRed() - start.getRed()) * ratio);
        int g = (int) (start.getGreen() + (end.getGreen() - start.getGreen()) * ratio);
        int b = (int) (start.getBlue() + (end.getBlue() - start.getBlue()) * ratio);
        return new java.awt.Color(r, g, b);
    }
}
```

---

## Interactive Elements

### Click Handlers

```java
public class GUIInteractionHandler implements Listener {
    
    private final Map<UUID, GUISession> activeSessions = new ConcurrentHashMap<>();
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        GUISession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;
        
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        
        handleItemClick(player, clickedItem, session);
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        activeSessions.remove(player.getUniqueId());
    }
    
    private void handleItemClick(Player player, ItemStack item, GUISession session) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        PersistentDataContainer data = meta.getPersistentDataContainer();
        
        // Handle different item types
        if (data.has(new NamespacedKey(getPlugin(), "action"), PersistentDataType.STRING)) {
            String action = data.get(new NamespacedKey(getPlugin(), "action"), PersistentDataType.STRING);
            handleAction(player, action, data, session);
        }
    }
    
    private void handleAction(Player player, String action, PersistentDataContainer data, GUISession session) {
        switch (action) {
            case "confirm_purchase" -> handlePurchase(player, data, session);
            case "change_page" -> handlePageChange(player, data, session);
            case "select_option" -> handleOptionSelection(player, data, session);
            case "close_gui" -> player.closeInventory();
        }
    }
    
    private void handlePurchase(Player player, PersistentDataContainer data, GUISession session) {
        String itemId = data.get(new NamespacedKey(getPlugin(), "item_id"), PersistentDataType.STRING);
        double price = data.get(new NamespacedKey(getPlugin(), "price"), PersistentDataType.DOUBLE);
        
        // Check if player can afford
        if (!canAfford(player, price)) {
            sendMessage(player, "shop.insufficient_funds");
            return;
        }
        
        // Process purchase
        deductMoney(player, price);
        giveItem(player, itemId);
        sendMessage(player, "shop.purchase_success", itemId);
        
        // Update GUI
        refreshGUI(player, session);
    }
    
    private void handlePageChange(Player player, PersistentDataContainer data, GUISession session) {
        int newPage = data.get(new NamespacedKey(getPlugin(), "page"), PersistentDataType.INTEGER);
        session.setCurrentPage(newPage);
        updateGUI(player, session);
    }
    
    private void handleOptionSelection(Player player, PersistentDataContainer data, GUISession session) {
        String optionId = data.get(new NamespacedKey(getPlugin(), "option_id"), PersistentDataType.STRING);
        session.setSelectedOption(optionId);
        
        // Provide visual feedback
        sendMessage(player, "gui.option_selected", optionId);
        updateGUI(player, session);
    }
    
    private void updateGUI(Player player, GUISession session) {
        // Recreate and reopen inventory with updated state
        Inventory newInventory = createUpdatedInventory(session);
        player.openInventory(newInventory);
    }
    
    private void refreshGUI(Player player, GUISession session) {
        // Refresh current inventory contents
        Inventory inventory = player.getOpenInventory().getTopInventory();
        // Update specific slots as needed
    }
    
    private JavaPlugin getPlugin() {
        return null; // Return your plugin instance
    }
    
    private void sendMessage(Player player, String key, Object... args) {
        // Send localized message to player
    }
    
    // Other helper methods...
}
```

### GUI Session Management

```java
public class GUISession {
    
    private final UUID playerId;
    private final String guiType;
    private int currentPage = 0;
    private String selectedOption;
    private final Map<String, Object> sessionData = new ConcurrentHashMap<>();
    private final long createdTime = System.currentTimeMillis();
    
    public GUISession(UUID playerId, String guiType) {
        this.playerId = playerId;
        this.guiType = guiType;
    }
    
    // Getters and setters...
    
    public void setData(String key, Object value) {
        sessionData.put(key, value);
    }
    
    public Object getData(String key) {
        return sessionData.get(key);
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() - createdTime > 300000; // 5 minutes
    }
}
```

---

## Best Practices

### 1. Message Organization

```java
// ✅ Good - Organized message structure
{
  "_id": "en",
  "gui": {
    "shop": {
      "title": "Shop",
      "balance": "Balance: {balance}",
      "insufficient_funds": "&cInsufficient funds!"
    },
    "buttons": {
      "confirm": "&aConfirm",
      "cancel": "&cCancel"
    }
  }
}

// ❌ Avoid - Flat structure
{
  "_id": "en",
  "shop_title": "Shop",
  "shop_balance": "Balance: {balance}",
  "shop_insufficient_funds": "&cInsufficient funds!",
  "confirm_button": "&aConfirm",
  "cancel_button": "&cCancel"
}
```

### 2. Performance Optimization

```java
public class GUICache {
    
    private final Cache<String, ItemStack> itemCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();
    
    public ItemStack getCachedItem(String language, String itemKey) {
        String cacheKey = language + ":" + itemKey;
        return itemCache.get(cacheKey, key -> createItem(language, itemKey));
    }
    
    private ItemStack createItem(String language, String itemKey) {
        // Expensive item creation logic
        return new ItemStack(Material.PAPER);
    }
}
```

### 3. Error Handling

```java
public class GUISafeExecutor {
    
    public void executeSafe(Player player, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            getLogger().error("GUI action failed for player " + player.getName(), e);
            
            // Send user-friendly error message
            String errorMessage = getMessages().get(getPlayerLanguage(player), 
                "gui.error.generic");
            player.sendMessage(ColorHelper.parseComponent(errorMessage));
            
            // Close GUI to prevent further errors
            player.closeInventory();
        }
    }
    
    private Logger getLogger() {
        return null; // Return your logger
    }
    
    private Messages getMessages() {
        return null; // Return your messages instance
    }
    
    private String getPlayerLanguage(Player player) {
        return "en"; // Return player's language
    }
}
```

---

## Complete Examples

### Multilingual Settings GUI

```java
@ConfigsFileProperties(name = "settings-gui-messages")
@SupportedLanguages({"en", "pl", "de", "fr", "es"})
public class SettingsGUIMessages extends MongoMessages<SettingsGUIMessages> {}

public class SettingsGUI {
    
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final Messages messages;
    
    public SettingsGUI() {
        this.configManager = MongoConfigsAPI.getConfigManager();
        this.languageManager = MongoConfigsAPI.getLanguageManager();
        this.messages = configManager.messagesOf(SettingsGUIMessages.class);
    }
    
    public void openSettings(Player player) {
        String language = languageManager.getPlayerLanguageOrDefault(
            player.getUniqueId().toString());
        
        String title = messages.get(language, "settings.title");
        Inventory inventory = Bukkit.createInventory(null, 27, title);
        
        // Add setting toggles
        addSettingToggle(inventory, 10, "particles", player, language);
        addSettingToggle(inventory, 12, "sounds", player, language);
        addSettingToggle(inventory, 14, "auto_save", player, language);
        addSettingToggle(inventory, 16, "notifications", player, language);
        
        // Add navigation
        addNavigationButtons(inventory, language);
        
        player.openInventory(inventory);
    }
    
    private void addSettingToggle(Inventory inventory, int slot, String settingKey, 
                                Player player, String language) {
        PlayerSettings settings = getPlayerSettings(player);
        boolean enabled = getSettingValue(settings, settingKey);
        
        Material material = enabled ? Material.LIME_WOOL : Material.RED_WOOL;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        String displayName = messages.get(language, "settings." + settingKey + ".name");
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        List<String> lore = new ArrayList<>();
        String status = messages.get(language, 
            enabled ? "settings.enabled" : "settings.disabled");
        lore.add(ColorHelper.parseString(status));
        
        String description = messages.get(language, "settings." + settingKey + ".description");
        lore.add("");
        lore.add(ColorHelper.parseString("&7" + description));
        
        String clickInstruction = messages.get(language, "settings.click_to_toggle");
        lore.add("");
        lore.add(ColorHelper.parseString("&e" + clickInstruction));
        
        meta.setLore(lore);
        
        // Store setting data
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(new NamespacedKey(getPlugin(), "setting_key"), 
                PersistentDataType.STRING, settingKey);
        data.set(new NamespacedKey(getPlugin(), "current_value"), 
                PersistentDataType.INTEGER, enabled ? 1 : 0);
        
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    private void addNavigationButtons(Inventory inventory, String language) {
        // Save button
        ItemStack saveItem = new ItemStack(Material.EMERALD);
        ItemMeta saveMeta = saveItem.getItemMeta();
        saveMeta.setDisplayName(ColorHelper.parseString(
            messages.get(language, "settings.save")));
        saveItem.setItemMeta(saveMeta);
        inventory.setItem(22, saveItem);
        
        // Close button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ColorHelper.parseString(
            messages.get(language, "gui.close")));
        closeItem.setItemMeta(closeMeta);
        inventory.setItem(26, closeItem);
    }
    
    private PlayerSettings getPlayerSettings(Player player) {
        String playerId = player.getUniqueId().toString();
        return configManager.getConfigOrGenerate(
            PlayerSettings.class,
            () -> new PlayerSettings(playerId)
        );
    }
    
    private boolean getSettingValue(PlayerSettings settings, String settingKey) {
        return switch (settingKey) {
            case "particles" -> settings.isParticlesEnabled();
            case "sounds" -> settings.isSoundsEnabled();
            case "auto_save" -> settings.isAutoSaveEnabled();
            case "notifications" -> settings.isNotificationsEnabled();
            default -> false;
        };
    }
    
    private JavaPlugin getPlugin() {
        return null; // Return your plugin instance
    }
}
```

---

*Next: Learn about [[Change Streams Tutorial]] for real-time multi-server synchronization.*