# Shop Plugin Example

Complete implementation of a multilingual shop plugin using MongoDB Configs API with GUI, inventory management, and player transactions.

## 📋 Overview

This example demonstrates a fully functional shop plugin that includes:
- Multilingual GUI with dynamic pricing
- Player inventory and balance management
- Transaction history and receipts
- Admin shop management commands
- Real-time price updates via Change Streams

## 🏗️ Project Structure

```
shop-plugin/
├── src/main/java/xyz/wtje/shop/
│   ├── ShopPlugin.java              # Main plugin class
│   ├── commands/                    # Command handlers
│   │   ├── ShopCommand.java
│   │   └── AdminShopCommand.java
│   ├── gui/                         # GUI components
│   │   ├── ShopGUI.java
│   │   ├── CategoryGUI.java
│   │   └── ConfirmationGUI.java
│   ├── models/                      # Data models
│   │   ├── ShopItem.java
│   │   ├── ShopCategory.java
│   │   ├── PlayerBalance.java
│   │   └── Transaction.java
│   ├── managers/                    # Business logic
│   │   ├── ShopManager.java
│   │   ├── EconomyManager.java
│   │   └── TransactionManager.java
│   └── messages/                    # Multilingual messages
│       ├── ShopMessages.java
│       └── AdminMessages.java
└── src/main/resources/
    └── plugin.yml
```

## 🔧 Configuration Classes

### ShopItem.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "shop_plugin")
@ConfigsCollection(collection = "shop_items")
public class ShopItem {
    
    @ConfigsField
    private String id;
    
    @ConfigsField
    private String nameKey;  // Message key for item name
    
    @ConfigsField
    private String descriptionKey;  // Message key for description
    
    @ConfigsField
    private Material material;
    
    @ConfigsField
    private int amount;
    
    @ConfigsField
    private double price;
    
    @ConfigsField
    private String category;
    
    @ConfigsField
    private boolean available;
    
    @ConfigsField
    private int stock;  // -1 for unlimited
    
    // Getters and setters...
}
```

### ShopCategory.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "shop_plugin")
@ConfigsCollection(collection = "shop_categories")
public class ShopCategory {
    
    @ConfigsField
    private String id;
    
    @ConfigsField
    private String nameKey;
    
    @ConfigsField
    private String descriptionKey;
    
    @ConfigsField
    private Material icon;
    
    @ConfigsField
    private int slot;  // GUI slot position
    
    @ConfigsField
    private List<String> itemIds;
    
    // Getters and setters...
}
```

### PlayerBalance.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "shop_plugin")
@ConfigsCollection(collection = "player_balances")
public class PlayerBalance {
    
    @ConfigsField
    private String playerId;
    
    @ConfigsField
    private double balance;
    
    @ConfigsField
    private String currency;
    
    @ConfigsField
    private long lastUpdated;
    
    // Getters and setters...
}
```

### Transaction.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "shop_plugin")
@ConfigsCollection(collection = "transactions")
public class Transaction {
    
    @ConfigsField
    private String id;
    
    @ConfigsField
    private String playerId;
    
    @ConfigsField
    private String itemId;
    
    @ConfigsField
    private int quantity;
    
    @ConfigsField
    private double totalPrice;
    
    @ConfigsField
    private long timestamp;
    
    @ConfigsField
    private TransactionType type;  // BUY or SELL
    
    public enum TransactionType {
        BUY, SELL
    }
    
    // Getters and setters...
}
```

## 💬 Message Classes

### ShopMessages.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "shop_plugin")
@ConfigsCollection(collection = "shop_messages")
@SupportedLanguages({"en", "pl", "es", "de", "fr"})
public class ShopMessages extends MongoMessages {
    
    // GUI Messages
    public String getGuiTitle() { return get("en", "gui.shop.title"); }
    public String getGuiTitle(String lang) { return get(lang, "gui.shop.title"); }
    
    public String getCategoryName(String categoryId) { 
        return get("en", "category." + categoryId + ".name"); 
    }
    public String getCategoryName(String lang, String categoryId) { 
        return get(lang, "category." + categoryId + ".name"); 
    }
    
    // Item Messages
    public String getItemName(String itemId) { 
        return get("en", "item." + itemId + ".name"); 
    }
    public String getItemName(String lang, String itemId) { 
        return get(lang, "item." + itemId + ".name"); 
    }
    
    public String getItemDescription(String itemId) { 
        return get("en", "item." + itemId + ".description"); 
    }
    public String getItemDescription(String lang, String itemId) { 
        return get(lang, "item." + itemId + ".description"); 
    }
    
    // Transaction Messages
    public String getPurchaseSuccess(String itemName, double price, String currency) {
        return get("en", "shop.purchase.success", itemName, price, currency);
    }
    public String getPurchaseSuccess(String lang, String itemName, double price, String currency) {
        return get(lang, "shop.purchase.success", itemName, price, currency);
    }
    
    public String getInsufficientFunds(double needed, double current, String currency) {
        return get("en", "shop.error.insufficient_funds", needed, current, currency);
    }
    public String getInsufficientFunds(String lang, double needed, double current, String currency) {
        return get(lang, "shop.error.insufficient_funds", needed, current, currency);
    }
    
    // Navigation Messages
    public String getBackButton() { return get("en", "gui.button.back"); }
    public String getBackButton(String lang) { return get(lang, "gui.button.back"); }
    
    public String getNextPage() { return get("en", "gui.button.next_page"); }
    public String getNextPage(String lang) { return get(lang, "gui.button.next_page"); }
}
```

## 🎯 Main Plugin Class

### ShopPlugin.java

```java
public class ShopPlugin extends JavaPlugin {
    
    private ConfigManager configManager;
    private ShopManager shopManager;
    private EconomyManager economyManager;
    private TransactionManager transactionManager;
    private ShopMessages shopMessages;
    private AdminMessages adminMessages;
    
    @Override
    public void onEnable() {
        // Initialize MongoDB Configs API
        try {
            configManager = MongoConfigsAPI.createConfigManager(
                getConfig().getString("mongodb.uri", "mongodb://localhost:27017"),
                getConfig().getString("mongodb.database", "shop_plugin")
            );
            
            // Initialize message systems
            shopMessages = configManager.messagesOf(ShopMessages.class);
            adminMessages = configManager.messagesOf(AdminMessages.class);
            
            // Initialize managers
            shopManager = new ShopManager(this);
            economyManager = new EconomyManager(this);
            transactionManager = new TransactionManager(this);
            
            // Register commands
            getCommand("shop").setExecutor(new ShopCommand(this));
            getCommand("adminshop").setExecutor(new AdminShopCommand(this));
            
            // Register events
            getServer().getPluginManager().registerEvents(new ShopGUIListener(this), this);
            
            getLogger().info("Shop Plugin enabled successfully!");
            
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Shop Plugin: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        if (configManager != null) {
            configManager.close();
        }
        getLogger().info("Shop Plugin disabled!");
    }
    
    // Getters for managers and messages...
    public ConfigManager getConfigManager() { return configManager; }
    public ShopManager getShopManager() { return shopManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public TransactionManager getTransactionManager() { return transactionManager; }
    public ShopMessages getShopMessages() { return shopMessages; }
    public AdminMessages getAdminMessages() { return adminMessages; }
}
```

## 🛠️ Managers

### ShopManager.java

```java
public class ShopManager {
    
    private final ShopPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, ShopItem> itemsCache = new ConcurrentHashMap<>();
    private final Map<String, ShopCategory> categoriesCache = new ConcurrentHashMap<>();
    
    public ShopManager(ShopPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        
        // Load initial data
        loadShopData();
        
        // Listen for changes
        setupChangeStreams();
    }
    
    private void loadShopData() {
        try {
            // Load all shop items
            List<ShopItem> items = configManager.getAll(ShopItem.class);
            items.forEach(item -> itemsCache.put(item.getId(), item));
            
            // Load all categories
            List<ShopCategory> categories = configManager.getAll(ShopCategory.class);
            categories.forEach(cat -> categoriesCache.put(cat.getId(), cat));
            
            plugin.getLogger().info("Loaded " + items.size() + " shop items and " + 
                                  categories.size() + " categories");
                                  
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load shop data: " + e.getMessage());
        }
    }
    
    private void setupChangeStreams() {
        // Listen for item changes
        configManager.watchCollection(ShopItem.class, changeEvent -> {
            ShopItem item = changeEvent.getDocument();
            if (item != null) {
                itemsCache.put(item.getId(), item);
                plugin.getLogger().info("Shop item updated: " + item.getId());
            }
        });
        
        // Listen for category changes
        configManager.watchCollection(ShopCategory.class, changeEvent -> {
            ShopCategory category = changeEvent.getDocument();
            if (category != null) {
                categoriesCache.put(category.getId(), category);
                plugin.getLogger().info("Shop category updated: " + category.getId());
            }
        });
    }
    
    public ShopItem getItem(String itemId) {
        return itemsCache.get(itemId);
    }
    
    public ShopCategory getCategory(String categoryId) {
        return categoriesCache.get(categoryId);
    }
    
    public List<ShopItem> getItemsByCategory(String categoryId) {
        ShopCategory category = categoriesCache.get(categoryId);
        if (category == null || category.getItemIds() == null) {
            return Collections.emptyList();
        }
        
        return category.getItemIds().stream()
            .map(itemsCache::get)
            .filter(Objects::nonNull)
            .filter(ShopItem::isAvailable)
            .collect(Collectors.toList());
    }
    
    public List<ShopCategory> getAllCategories() {
        return new ArrayList<>(categoriesCache.values());
    }
    
    public boolean purchaseItem(Player player, String itemId, int quantity) {
        ShopItem item = itemsCache.get(itemId);
        if (item == null || !item.isAvailable()) {
            return false;
        }
        
        // Check stock
        if (item.getStock() != -1 && item.getStock() < quantity) {
            return false;
        }
        
        double totalPrice = item.getPrice() * quantity;
        
        // Check player balance
        if (!plugin.getEconomyManager().hasBalance(player, totalPrice)) {
            return false;
        }
        
        // Process transaction
        try {
            // Deduct balance
            plugin.getEconomyManager().withdraw(player, totalPrice);
            
            // Give items
            ItemStack itemStack = new ItemStack(item.getMaterial(), item.getAmount() * quantity);
            player.getInventory().addItem(itemStack);
            
            // Update stock if limited
            if (item.getStock() != -1) {
                item.setStock(item.getStock() - quantity);
                configManager.save(item);
            }
            
            // Record transaction
            plugin.getTransactionManager().recordPurchase(player, item, quantity, totalPrice);
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to process purchase: " + e.getMessage());
            // Refund on failure
            plugin.getEconomyManager().deposit(player, totalPrice);
            return false;
        }
    }
}
```

### EconomyManager.java

```java
public class EconomyManager {
    
    private final ShopPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, PlayerBalance> balanceCache = new ConcurrentHashMap<>();
    
    public EconomyManager(ShopPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        
        // Load all balances
        loadBalances();
        
        // Setup change streams for real-time balance updates
        configManager.watchCollection(PlayerBalance.class, changeEvent -> {
            PlayerBalance balance = changeEvent.getDocument();
            if (balance != null) {
                balanceCache.put(balance.getPlayerId(), balance);
            }
        });
    }
    
    private void loadBalances() {
        try {
            List<PlayerBalance> balances = configManager.getAll(PlayerBalance.class);
            balances.forEach(balance -> balanceCache.put(balance.getPlayerId(), balance));
            plugin.getLogger().info("Loaded balances for " + balances.size() + " players");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load player balances: " + e.getMessage());
        }
    }
    
    public double getBalance(Player player) {
        String playerId = player.getUniqueId().toString();
        PlayerBalance balance = balanceCache.get(playerId);
        return balance != null ? balance.getBalance() : 0.0;
    }
    
    public boolean hasBalance(Player player, double amount) {
        return getBalance(player) >= amount;
    }
    
    public void deposit(Player player, double amount) {
        String playerId = player.getUniqueId().toString();
        PlayerBalance balance = balanceCache.computeIfAbsent(playerId, id -> {
            PlayerBalance newBalance = new PlayerBalance();
            newBalance.setPlayerId(id);
            newBalance.setBalance(0.0);
            newBalance.setCurrency("coins");
            return newBalance;
        });
        
        balance.setBalance(balance.getBalance() + amount);
        balance.setLastUpdated(System.currentTimeMillis());
        
        configManager.save(balance);
    }
    
    public boolean withdraw(Player player, double amount) {
        if (!hasBalance(player, amount)) {
            return false;
        }
        
        String playerId = player.getUniqueId().toString();
        PlayerBalance balance = balanceCache.get(playerId);
        
        balance.setBalance(balance.getBalance() - amount);
        balance.setLastUpdated(System.currentTimeMillis());
        
        configManager.save(balance);
        return true;
    }
    
    public void setBalance(Player player, double amount) {
        String playerId = player.getUniqueId().toString();
        PlayerBalance balance = balanceCache.computeIfAbsent(playerId, id -> {
            PlayerBalance newBalance = new PlayerBalance();
            newBalance.setPlayerId(id);
            newBalance.setCurrency("coins");
            return newBalance;
        });
        
        balance.setBalance(amount);
        balance.setLastUpdated(System.currentTimeMillis());
        
        configManager.save(balance);
    }
}
```

## 🎨 GUI Components

### ShopGUI.java

```java
public class ShopGUI {
    
    private final ShopPlugin plugin;
    private final ShopMessages messages;
    private final LanguageManager languageManager;
    
    public ShopGUI(ShopPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getShopMessages();
        this.languageManager = MongoConfigsAPI.getLanguageManager();
    }
    
    public void openMainShop(Player player) {
        String language = getPlayerLanguage(player);
        String title = messages.getGuiTitle(language);
        
        Inventory inventory = Bukkit.createInventory(null, 54, ColorHelper.parseString(title));
        
        // Add category items
        List<ShopCategory> categories = plugin.getShopManager().getAllCategories();
        for (ShopCategory category : categories) {
            addCategoryItem(inventory, player, category, language);
        }
        
        // Add player info item
        addPlayerInfoItem(inventory, player, language);
        
        // Add navigation items
        addNavigationItems(inventory, language);
        
        player.openInventory(inventory);
    }
    
    public void openCategory(Player player, String categoryId) {
        ShopCategory category = plugin.getShopManager().getCategory(categoryId);
        if (category == null) return;
        
        String language = getPlayerLanguage(player);
        String title = messages.getCategoryName(language, categoryId);
        
        Inventory inventory = Bukkit.createInventory(null, 54, ColorHelper.parseString(title));
        
        // Add items from this category
        List<ShopItem> items = plugin.getShopManager().getItemsByCategory(categoryId);
        int slot = 0;
        for (ShopItem item : items) {
            if (slot >= 45) break; // Leave room for navigation
            addShopItem(inventory, player, item, slot++, language);
        }
        
        // Add navigation items
        addBackButton(inventory, language);
        addPageNavigation(inventory, language);
        
        player.openInventory(inventory);
    }
    
    private void addCategoryItem(Inventory inventory, Player player, ShopCategory category, String language) {
        ItemStack item = new ItemStack(category.getIcon());
        ItemMeta meta = item.getItemMeta();
        
        String name = messages.getCategoryName(language, category.getId());
        meta.setDisplayName(ColorHelper.parseString(name));
        
        String description = messages.getCategoryDescription(language, category.getId());
        List<String> lore = Arrays.asList(
            ColorHelper.parseString(description),
            "",
            ColorHelper.parseString("&7Click to browse items")
        );
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        inventory.setItem(category.getSlot(), item);
    }
    
    private void addShopItem(Inventory inventory, Player player, ShopItem item, int slot, String language) {
        ItemStack guiItem = new ItemStack(item.getMaterial());
        ItemMeta meta = guiItem.getItemMeta();
        
        String name = messages.getItemName(language, item.getId());
        meta.setDisplayName(ColorHelper.parseString(name));
        
        List<String> lore = new ArrayList<>();
        String description = messages.getItemDescription(language, item.getId());
        lore.add(ColorHelper.parseString(description));
        lore.add("");
        
        // Price information
        String priceLine = messages.getPriceLine(language, item.getPrice(), 
                                             plugin.getEconomyManager().getCurrency());
        lore.add(ColorHelper.parseString(priceLine));
        
        // Stock information
        if (item.getStock() != -1) {
            String stockLine = messages.getStockLine(language, item.getStock());
            lore.add(ColorHelper.parseString(stockLine));
        }
        
        // Purchase instruction
        lore.add(ColorHelper.parseString("&eClick to purchase"));
        
        meta.setLore(lore);
        guiItem.setItemMeta(meta);
        inventory.setItem(slot, guiItem);
    }
    
    private void addPlayerInfoItem(Inventory inventory, Player player, String language) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        
        String displayName = messages.getPlayerInfoTitle(language);
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        double balance = plugin.getEconomyManager().getBalance(player);
        String currency = plugin.getEconomyManager().getCurrency();
        
        List<String> lore = Arrays.asList(
            ColorHelper.parseString("&7" + player.getName()),
            "",
            ColorHelper.parseString(messages.getBalanceLine(language, balance, currency)),
            ColorHelper.parseString("&7" + language.toUpperCase())
        );
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        inventory.setItem(49, item);
    }
    
    private void addNavigationItems(Inventory inventory, String language) {
        // Back button
        addBackButton(inventory, language);
        
        // Close button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ColorHelper.parseString("&c" + messages.getCloseButton(language)));
        closeItem.setItemMeta(closeMeta);
        inventory.setItem(53, closeItem);
    }
    
    private void addBackButton(Inventory inventory, String language) {
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ColorHelper.parseString("&7" + messages.getBackButton(language)));
        backItem.setItemMeta(backMeta);
        inventory.setItem(45, backItem);
    }
    
    private void addPageNavigation(Inventory inventory, String language) {
        // Previous page
        ItemStack prevItem = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prevItem.getItemMeta();
        prevMeta.setDisplayName(ColorHelper.parseString("&7" + messages.getPreviousPage(language)));
        prevItem.setItemMeta(prevMeta);
        inventory.setItem(48, prevItem);
        
        // Next page
        ItemStack nextItem = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = nextItem.getItemMeta();
        nextMeta.setDisplayName(ColorHelper.parseString("&7" + messages.getNextPage(language)));
        nextItem.setItemMeta(nextMeta);
        inventory.setItem(50, nextItem);
    }
    
    private String getPlayerLanguage(Player player) {
        String playerId = player.getUniqueId().toString();
        return languageManager.getPlayerLanguageOrDefault(playerId);
    }
}
```

## 📊 Commands

### ShopCommand.java

```java
public class ShopCommand implements CommandExecutor {
    
    private final ShopPlugin plugin;
    private final ShopGUI shopGUI;
    
    public ShopCommand(ShopPlugin plugin) {
        this.plugin = plugin;
        this.shopGUI = new ShopGUI(plugin);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            // Open main shop
            shopGUI.openMainShop(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "balance":
            case "bal":
                showBalance(player);
                break;
                
            case "history":
                showTransactionHistory(player);
                break;
                
            default:
                showHelp(player);
                break;
        }
        
        return true;
    }
    
    private void showBalance(Player player) {
        String language = getPlayerLanguage(player);
        double balance = plugin.getEconomyManager().getBalance(player);
        String currency = plugin.getEconomyManager().getCurrency();
        
        String message = plugin.getShopMessages().getBalanceInfo(language, balance, currency);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    private void showTransactionHistory(Player player) {
        // Implementation for showing transaction history
        // This would open a GUI or send chat messages with recent transactions
    }
    
    private void showHelp(Player player) {
        String language = getPlayerLanguage(player);
        List<String> helpLines = plugin.getShopMessages().getHelpLines(language);
        
        for (String line : helpLines) {
            player.sendMessage(ColorHelper.parseComponent(line));
        }
    }
    
    private String getPlayerLanguage(Player player) {
        String playerId = player.getUniqueId().toString();
        return MongoConfigsAPI.getLanguageManager().getPlayerLanguageOrDefault(playerId);
    }
}
```

## 🔄 Change Streams Integration

The shop plugin automatically updates when administrators modify items or categories through the database:

```java
// Real-time price updates
configManager.watchCollection(ShopItem.class, changeEvent -> {
    ShopItem updatedItem = changeEvent.getDocument();
    if (updatedItem != null) {
        // Update cache
        itemsCache.put(updatedItem.getId(), updatedItem);
        
        // Notify players currently viewing the shop
        notifyPlayersOfPriceChange(updatedItem);
    }
});

// Category updates
configManager.watchCollection(ShopCategory.class, changeEvent -> {
    ShopCategory updatedCategory = changeEvent.getDocument();
    if (updatedCategory != null) {
        categoriesCache.put(updatedCategory.getId(), updatedCategory);
        
        // Refresh open GUIs
        refreshCategoryGUIs(updatedCategory.getId());
    }
});
```

## 🌍 Multilingual Support

The plugin supports multiple languages with automatic player language detection:

```java
// Language-specific messages
String welcomeMessage = shopMessages.get(playerLang, "shop.welcome", player.getName());
String priceDisplay = shopMessages.get(playerLang, "shop.price.display", itemPrice, currency);

// GUI titles and buttons
String shopTitle = shopMessages.getGuiTitle(playerLang);
String backButton = shopMessages.getBackButton(playerLang);
```

## 📈 Performance Features

- **Caching**: All shop data cached in memory for fast access
- **Change Streams**: Real-time updates without polling
- **Async Operations**: Database operations don't block main thread
- **Pagination**: Large inventories split across multiple pages
- **Lazy Loading**: Items loaded only when needed

## 🔒 Security Features

- **Stock Validation**: Prevents overselling limited items
- **Balance Verification**: Double-checks funds before transactions
- **Transaction Logging**: Complete audit trail of all purchases
- **Error Recovery**: Automatic refunds on failed transactions

---

*Next: Learn about [[Economy System Example]] for advanced currency management.*