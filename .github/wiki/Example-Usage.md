# Example Usage

Real-world examples and implementation patterns for the MongoDB Configs API in various Minecraft plugin scenarios.

## 🏪 Shop Plugin Implementation

### Complete Shop System

```java
// Shop Configuration
@ConfigsDatabase(
    uri = "mongodb+srv://cluster.mongodb.net",
    database = "minecraft_shop"
)
@ConfigsCollection("shop-configuration")
@ConfigsFileProperties(fileName = "shop-config.yml", resource = true)
public class ShopConfig {
    
    private Map<String, ShopCategory> categories = new HashMap<>();
    private double taxRate = 0.05;  // 5% tax
    private boolean enableVipDiscount = true;
    private double vipDiscountRate = 0.10;  // 10% VIP discount
    private int maxPurchaseQuantity = 64;
    
    // Constructors, getters, setters...
    
    public static class ShopCategory {
        private String displayName;
        private String iconMaterial;
        private List<ShopItem> items = new ArrayList<>();
        
        // Getters, setters...
    }
    
    public static class ShopItem {
        private String itemId;
        private String displayName;
        private Material material;
        private double price;
        private int maxQuantity;
        private List<String> lore;
        private Map<String, Object> enchantments = new HashMap<>();
        
        // Getters, setters...
    }
}

// Shop Messages
@ConfigsCollection("shop-messages")
@ConfigsFileProperties(fileName = "shop-messages.yml", resource = true)
public class ShopMessages {
    // Messages are stored in MongoDB per language
}

// Shop Plugin Main Class
public class ShopPlugin extends JavaPlugin {
    
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private Messages shopMessages;
    private ShopConfig shopConfig;
    
    @Override
    public void onEnable() {
        // Initialize MongoDB Configs API
        this.configManager = MongoConfigsAPI.getConfigManager();
        this.languageManager = MongoConfigsAPI.getLanguageManager();
        this.shopMessages = configManager.messagesOf(ShopMessages.class);
        
        // Load shop configuration
        loadShopConfiguration();
        
        // Register commands and listeners
        getCommand("shop").setExecutor(new ShopCommand(this));
        Bukkit.getPluginManager().registerEvents(new ShopGUIListener(this), this);
        
        getLogger().info("Shop plugin enabled!");
    }
    
    private void loadShopConfiguration() {
        try {
            shopConfig = configManager.loadObject(ShopConfig.class);
            getLogger().info("Shop configuration loaded successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to load shop configuration: " + e.getMessage());
            
            // Create default configuration
            createDefaultShopConfig();
        }
    }
    
    private void createDefaultShopConfig() {
        shopConfig = new ShopConfig();
        
        // Create default categories
        ShopConfig.ShopCategory weapons = new ShopConfig.ShopCategory();
        weapons.setDisplayName("Weapons");
        weapons.setIconMaterial("DIAMOND_SWORD");
        
        // Add default items
        ShopConfig.ShopItem diamondSword = new ShopConfig.ShopItem();
        diamondSword.setItemId("diamond_sword");
        diamondSword.setDisplayName("&bDiamond Sword");
        diamondSword.setMaterial(Material.DIAMOND_SWORD);
        diamondSword.setPrice(100.0);
        diamondSword.setMaxQuantity(1);
        diamondSword.setLore(Arrays.asList("&7A powerful diamond sword", "&7Perfect for combat"));
        
        weapons.getItems().add(diamondSword);
        shopConfig.getCategories().put("weapons", weapons);
        
        // Save default configuration
        configManager.saveObject(shopConfig);
        getLogger().info("Created default shop configuration");
    }
    
    // Getters for other classes
    public ShopConfig getShopConfig() { return shopConfig; }
    public Messages getShopMessages() { return shopMessages; }
    public LanguageManager getLanguageManager() { return languageManager; }
}

// Shop Command
public class ShopCommand implements CommandExecutor {
    
    private final ShopPlugin plugin;
    
    public ShopCommand(ShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use the shop!");
            return true;
        }
        
        // Get player language
        String playerId = player.getUniqueId().toString();
        String language = plugin.getLanguageManager().getPlayerLanguageOrDefault(playerId);
        
        // Send shop opening message
        Messages messages = plugin.getShopMessages();
        String openingMessage = messages.get(language, "shop.opening", player.getName());
        player.sendMessage(ColorHelper.parseComponent(openingMessage));
        
        // Open shop GUI
        ShopGUI shopGUI = new ShopGUI(plugin);
        shopGUI.open(player);
        
        return true;
    }
}

// Shop GUI
public class ShopGUI {
    
    private final ShopPlugin plugin;
    
    public ShopGUI(ShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void open(Player player) {
        String playerId = player.getUniqueId().toString();
        String language = plugin.getLanguageManager().getPlayerLanguageOrDefault(playerId);
        
        // Create inventory
        Messages messages = plugin.getShopMessages();
        String title = messages.get(language, "gui.shop.title");
        Inventory inventory = Bukkit.createInventory(null, 54, title);
        
        // Add category items
        ShopConfig config = plugin.getShopConfig();
        int slot = 10;
        
        for (Map.Entry<String, ShopConfig.ShopCategory> entry : config.getCategories().entrySet()) {
            String categoryId = entry.getKey();
            ShopConfig.ShopCategory category = entry.getValue();
            
            createCategoryItem(inventory, slot, categoryId, category, language);
            slot += 2;
        }
        
        // Add navigation and info items
        createInfoItem(inventory, 49, language, player);
        createCloseItem(inventory, 45, language);
        
        player.openInventory(inventory);
    }
    
    private void createCategoryItem(Inventory inventory, int slot, String categoryId, 
                                  ShopConfig.ShopCategory category, String language) {
        Material iconMaterial = Material.valueOf(category.getIconMaterial());
        ItemStack item = new ItemStack(iconMaterial);
        ItemMeta meta = item.getItemMeta();
        
        // Set display name
        Messages messages = plugin.getShopMessages();
        String displayName = messages.get(language, "category." + categoryId + ".name", category.getDisplayName());
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        // Set lore
        List<String> lore = new ArrayList<>();
        lore.add(messages.get(language, "category." + categoryId + ".description"));
        lore.add("");
        lore.add(messages.get(language, "gui.shop.items_count", category.getItems().size()));
        lore.add(messages.get(language, "gui.shop.click_to_browse"));
        
        meta.setLore(lore.stream()
            .map(ColorHelper::parseString)
            .collect(Collectors.toList()));
        
        // Add custom data for click handling
        PersistentDataContainer dataContainer = meta.getPersistentDataContainer();
        dataContainer.set(new NamespacedKey(plugin, "category"), PersistentDataType.STRING, categoryId);
        
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    private void createInfoItem(Inventory inventory, int slot, String language, Player player) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        
        Messages messages = plugin.getShopMessages();
        String displayName = messages.get(language, "gui.shop.info.name");
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        // Get player balance (integration with economy plugin)
        double balance = getPlayerBalance(player);
        
        List<String> lore = new ArrayList<>();
        lore.add(messages.get(language, "gui.shop.info.welcome", player.getName()));
        lore.add(messages.get(language, "gui.shop.info.balance", balance));
        
        ShopConfig config = plugin.getShopConfig();
        if (config.isEnableVipDiscount() && player.hasPermission("shop.vip")) {
            double discountPercent = config.getVipDiscountRate() * 100;
            lore.add(messages.get(language, "gui.shop.info.vip_discount", discountPercent));
        }
        
        lore.add("");
        lore.add(messages.get(language, "gui.shop.info.instructions"));
        
        meta.setLore(lore.stream()
            .map(ColorHelper::parseString)
            .collect(Collectors.toList()));
        
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    private void createCloseItem(Inventory inventory, int slot, String language) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        
        Messages messages = plugin.getShopMessages();
        String displayName = messages.get(language, "gui.shop.close");
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    private double getPlayerBalance(Player player) {
        // Integration with economy plugin (Vault, etc.)
        return 1000.0; // Placeholder
    }
}

// Shop GUI Listener
public class ShopGUIListener implements Listener {
    
    private final ShopPlugin plugin;
    
    public ShopGUIListener(ShopPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().getTitle();
        Messages messages = plugin.getShopMessages();
        String language = plugin.getLanguageManager().getPlayerLanguageOrDefault(player.getUniqueId().toString());
        String shopTitle = messages.get(language, "gui.shop.title");
        
        if (!title.equals(shopTitle)) return;
        
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        
        // Handle category click
        if (clickedItem.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "category"))) {
            String categoryId = clickedItem.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "category"), PersistentDataType.STRING);
            
            openCategoryGUI(player, categoryId);
        }
        
        // Handle close click
        if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            String closeMessage = messages.get(language, "gui.shop.closed");
            player.sendMessage(ColorHelper.parseComponent(closeMessage));
        }
    }
    
    private void openCategoryGUI(Player player, String categoryId) {
        CategoryGUI categoryGUI = new CategoryGUI(plugin, categoryId);
        categoryGUI.open(player);
    }
}
```

---

## 🏰 Economy Plugin Integration

### Player Economy System

```java
// Player Data Configuration
@ConfigsCollection("player-economy-data")
public class PlayerEconomyData {
    
    private String playerId;
    private String playerName;
    private double balance;
    private Map<String, Double> currencyBalances = new HashMap<>();
    private List<Transaction> transactionHistory = new ArrayList<>();
    private PlayerSettings settings = new PlayerSettings();
    private long lastLogin;
    private long lastTransaction;
    
    public PlayerEconomyData() {}
    
    public PlayerEconomyData(String playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.balance = 1000.0; // Starting balance
        this.lastLogin = System.currentTimeMillis();
        
        // Initialize default currencies
        currencyBalances.put("coins", 1000.0);
        currencyBalances.put("gems", 0.0);
        currencyBalances.put("tokens", 0.0);
    }
    
    public static class Transaction {
        private String transactionId;
        private TransactionType type;
        private double amount;
        private String currency;
        private String description;
        private long timestamp;
        private String relatedPlayerId;
        
        // Constructors, getters, setters...
    }
    
    public static class PlayerSettings {
        private boolean receivePaymentNotifications = true;
        private boolean showBalanceOnLogin = true;
        private String preferredCurrency = "coins";
        private boolean allowTransferRequests = true;
        
        // Getters, setters...
    }
    
    public enum TransactionType {
        DEPOSIT, WITHDRAWAL, TRANSFER_SEND, TRANSFER_RECEIVE, 
        SHOP_PURCHASE, SHOP_SELL, QUEST_REWARD, LOTTERY_WIN
    }
    
    // Getters and setters...
}

// Economy Manager
public class EconomyManager {
    
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final Messages economyMessages;
    
    public EconomyManager() {
        this.configManager = MongoConfigsAPI.getConfigManager();
        this.languageManager = MongoConfigsAPI.getLanguageManager();
        this.economyMessages = configManager.messagesOf(EconomyMessages.class);
    }
    
    public PlayerEconomyData getOrCreatePlayerData(Player player) {
        String playerId = player.getUniqueId().toString();
        
        return configManager.getConfigOrGenerate(
            PlayerEconomyData.class,
            () -> new PlayerEconomyData(playerId, player.getName())
        );
    }
    
    public boolean transferMoney(Player sender, Player recipient, double amount, String currency) {
        PlayerEconomyData senderData = getOrCreatePlayerData(sender);
        PlayerEconomyData recipientData = getOrCreatePlayerData(recipient);
        
        // Check if sender has enough money
        double senderBalance = senderData.getCurrencyBalances().getOrDefault(currency, 0.0);
        if (senderBalance < amount) {
            sendEconomyMessage(sender, "transfer.insufficient_funds", amount, currency);
            return false;
        }
        
        // Check if recipient allows transfers
        if (!recipientData.getSettings().isAllowTransferRequests()) {
            sendEconomyMessage(sender, "transfer.recipient_disabled");
            return false;
        }
        
        // Perform transfer
        senderData.getCurrencyBalances().put(currency, senderBalance - amount);
        double recipientBalance = recipientData.getCurrencyBalances().getOrDefault(currency, 0.0);
        recipientData.getCurrencyBalances().put(currency, recipientBalance + amount);
        
        // Record transactions
        String transactionId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        
        PlayerEconomyData.Transaction senderTransaction = new PlayerEconomyData.Transaction();
        senderTransaction.setTransactionId(transactionId);
        senderTransaction.setType(PlayerEconomyData.TransactionType.TRANSFER_SEND);
        senderTransaction.setAmount(-amount);
        senderTransaction.setCurrency(currency);
        senderTransaction.setDescription("Transfer to " + recipient.getName());
        senderTransaction.setTimestamp(timestamp);
        senderTransaction.setRelatedPlayerId(recipient.getUniqueId().toString());
        
        PlayerEconomyData.Transaction recipientTransaction = new PlayerEconomyData.Transaction();
        recipientTransaction.setTransactionId(transactionId);
        recipientTransaction.setType(PlayerEconomyData.TransactionType.TRANSFER_RECEIVE);
        recipientTransaction.setAmount(amount);
        recipientTransaction.setCurrency(currency);
        recipientTransaction.setDescription("Transfer from " + sender.getName());
        recipientTransaction.setTimestamp(timestamp);
        recipientTransaction.setRelatedPlayerId(sender.getUniqueId().toString());
        
        senderData.getTransactionHistory().add(senderTransaction);
        recipientData.getTransactionHistory().add(recipientTransaction);
        
        // Save data asynchronously
        CompletableFuture.allOf(
            configManager.setObject(senderData),
            configManager.setObject(recipientData)
        ).thenRun(() -> {
            // Send success messages
            sendEconomyMessage(sender, "transfer.sent", amount, currency, recipient.getName());
            
            if (recipientData.getSettings().isReceivePaymentNotifications()) {
                sendEconomyMessage(recipient, "transfer.received", amount, currency, sender.getName());
            }
        });
        
        return true;
    }
    
    public void addMoney(Player player, double amount, String currency, String reason) {
        PlayerEconomyData playerData = getOrCreatePlayerData(player);
        
        double currentBalance = playerData.getCurrencyBalances().getOrDefault(currency, 0.0);
        playerData.getCurrencyBalances().put(currency, currentBalance + amount);
        
        // Record transaction
        PlayerEconomyData.Transaction transaction = new PlayerEconomyData.Transaction();
        transaction.setTransactionId(UUID.randomUUID().toString());
        transaction.setType(PlayerEconomyData.TransactionType.DEPOSIT);
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        transaction.setDescription(reason);
        transaction.setTimestamp(System.currentTimeMillis());
        
        playerData.getTransactionHistory().add(transaction);
        
        // Save data
        configManager.setObject(playerData).thenRun(() -> {
            sendEconomyMessage(player, "money.added", amount, currency, reason);
        });
    }
    
    public boolean removeMoney(Player player, double amount, String currency, String reason) {
        PlayerEconomyData playerData = getOrCreatePlayerData(player);
        
        double currentBalance = playerData.getCurrencyBalances().getOrDefault(currency, 0.0);
        if (currentBalance < amount) {
            sendEconomyMessage(player, "money.insufficient", amount, currency);
            return false;
        }
        
        playerData.getCurrencyBalances().put(currency, currentBalance - amount);
        
        // Record transaction
        PlayerEconomyData.Transaction transaction = new PlayerEconomyData.Transaction();
        transaction.setTransactionId(UUID.randomUUID().toString());
        transaction.setType(PlayerEconomyData.TransactionType.WITHDRAWAL);
        transaction.setAmount(-amount);
        transaction.setCurrency(currency);
        transaction.setDescription(reason);
        transaction.setTimestamp(System.currentTimeMillis());
        
        playerData.getTransactionHistory().add(transaction);
        
        // Save data
        configManager.setObject(playerData).thenRun(() -> {
            sendEconomyMessage(player, "money.removed", amount, currency, reason);
        });
        
        return true;
    }
    
    public void showBalance(Player player) {
        PlayerEconomyData playerData = getOrCreatePlayerData(player);
        String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        
        player.sendMessage(ColorHelper.parseComponent(economyMessages.get(language, "balance.header", player.getName())));
        
        playerData.getCurrencyBalances().forEach((currency, balance) -> {
            String currencyDisplay = economyMessages.get(language, "currency." + currency + ".name", currency);
            String balanceMessage = economyMessages.get(language, "balance.entry", currencyDisplay, balance);
            player.sendMessage(ColorHelper.parseComponent(balanceMessage));
        });
        
        player.sendMessage(ColorHelper.parseComponent(economyMessages.get(language, "balance.footer")));
    }
    
    private void sendEconomyMessage(Player player, String key, Object... args) {
        String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        String message = economyMessages.get(language, key, args);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
}
```

---

## 🎮 Mini-Game Configuration

### Parkour Plugin System

```java
// Parkour Configuration
@ConfigsCollection("parkour-courses")
@ConfigsFileProperties(fileName = "parkour-config.yml", resource = true)
public class ParkourCourse {
    
    private String courseId;
    private String displayName;
    private String description;
    private ParkourDifficulty difficulty;
    private Location startLocation;
    private Location endLocation;
    private List<Checkpoint> checkpoints = new ArrayList<>();
    private Map<String, Object> rewards = new HashMap<>();
    private List<String> completionCommands = new ArrayList<>();
    private ParkourSettings settings = new ParkourSettings();
    private Map<String, ParkourRecord> records = new HashMap<>();
    private boolean enabled = true;
    
    public static class Checkpoint {
        private String checkpointId;
        private Location location;
        private String message;
        private boolean playSound = true;
        private String soundType = "ENTITY_EXPERIENCE_ORB_PICKUP";
        
        // Constructors, getters, setters...
    }
    
    public static class ParkourSettings {
        private boolean allowFlight = false;
        private boolean resetOnDamage = true;
        private boolean resetOnQuit = true;
        private int timeLimit = 300; // 5 minutes in seconds
        private boolean showActionBarProgress = true;
        private boolean allowSpectators = true;
        
        // Getters, setters...
    }
    
    public static class ParkourRecord {
        private String playerId;
        private String playerName;
        private long completionTime; // milliseconds
        private long timestamp;
        private int attempts;
        
        // Constructors, getters, setters...
    }
    
    public enum ParkourDifficulty {
        EASY, MEDIUM, HARD, EXPERT, INSANE
    }
    
    // Constructors, getters, setters...
}

// Player Parkour Data
@ConfigsCollection("player-parkour-data")
public class PlayerParkourData {
    
    private String playerId;
    private String playerName;
    private Map<String, CourseProgress> courseProgress = new HashMap<>();
    private Map<String, CourseStats> courseStats = new HashMap<>();
    private PlayerParkourSettings settings = new PlayerParkourSettings();
    
    public static class CourseProgress {
        private String courseId;
        private boolean isActive = false;
        private long startTime;
        private int currentCheckpoint = 0;
        private List<Long> checkpointTimes = new ArrayList<>();
        private int attempts = 0;
        
        // Getters, setters...
    }
    
    public static class CourseStats {
        private String courseId;
        private long bestTime = Long.MAX_VALUE;
        private int totalAttempts = 0;
        private int completions = 0;
        private long totalTimeSpent = 0;
        private long lastPlayed = 0;
        
        // Getters, setters...
    }
    
    public static class PlayerParkourSettings {
        private boolean showActionBar = true;
        private boolean playCheckpointSounds = true;
        private boolean autoStartCourse = false;
        private boolean showOtherPlayers = true;
        
        // Getters, setters...
    }
    
    // Constructors, getters, setters...
}

// Parkour Manager
public class ParkourManager {
    
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final Messages parkourMessages;
    private final Map<String, ParkourCourse> loadedCourses = new ConcurrentHashMap<>();
    
    public ParkourManager() {
        this.configManager = MongoConfigsAPI.getConfigManager();
        this.languageManager = MongoConfigsAPI.getLanguageManager();
        this.parkourMessages = configManager.messagesOf(ParkourMessages.class);
        
        loadAllCourses();
    }
    
    private void loadAllCourses() {
        // Load all parkour courses from database
        // This would require a method to query multiple documents
        // For now, we'll use a simple approach
        
        try {
            // Example: Load known courses
            List<String> courseIds = Arrays.asList("beginner_course", "expert_tower", "speed_run");
            
            for (String courseId : courseIds) {
                try {
                    ParkourCourse course = loadCourse(courseId);
                    if (course != null) {
                        loadedCourses.put(courseId, course);
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to load parkour course: " + courseId);
                }
            }
            
            getLogger().info("Loaded " + loadedCourses.size() + " parkour courses");
            
        } catch (Exception e) {
            getLogger().severe("Failed to load parkour courses: " + e.getMessage());
        }
    }
    
    private ParkourCourse loadCourse(String courseId) {
        // Load specific course - would need custom loading logic
        return configManager.get("parkour.course." + courseId, ParkourCourse.class);
    }
    
    public void startCourse(Player player, String courseId) {
        ParkourCourse course = loadedCourses.get(courseId);
        if (course == null || !course.isEnabled()) {
            sendParkourMessage(player, "course.not_found", courseId);
            return;
        }
        
        PlayerParkourData playerData = getOrCreatePlayerData(player);
        
        // Check if player is already in a course
        if (isPlayerInAnyCourse(playerData)) {
            sendParkourMessage(player, "course.already_active");
            return;
        }
        
        // Initialize course progress
        PlayerParkourData.CourseProgress progress = new PlayerParkourData.CourseProgress();
        progress.setCourseId(courseId);
        progress.setActive(true);
        progress.setStartTime(System.currentTimeMillis());
        progress.setCurrentCheckpoint(0);
        progress.setAttempts(progress.getAttempts() + 1);
        
        playerData.getCourseProgress().put(courseId, progress);
        
        // Update course stats
        PlayerParkourData.CourseStats stats = playerData.getCourseStats().computeIfAbsent(courseId, 
            k -> new PlayerParkourData.CourseStats());
        stats.setCourseId(courseId);
        stats.setTotalAttempts(stats.getTotalAttempts() + 1);
        stats.setLastPlayed(System.currentTimeMillis());
        
        // Teleport player to start
        player.teleport(course.getStartLocation());
        
        // Apply course settings
        if (!course.getSettings().isAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
        
        // Send start message
        sendParkourMessage(player, "course.started", course.getDisplayName());
        
        // Save player data
        configManager.setObject(playerData);
    }
    
    public void reachCheckpoint(Player player, String courseId, int checkpointIndex) {
        PlayerParkourData playerData = getOrCreatePlayerData(player);
        PlayerParkourData.CourseProgress progress = playerData.getCourseProgress().get(courseId);
        
        if (progress == null || !progress.isActive()) {
            return;
        }
        
        ParkourCourse course = loadedCourses.get(courseId);
        if (course == null || checkpointIndex >= course.getCheckpoints().size()) {
            return;
        }
        
        // Update progress
        progress.setCurrentCheckpoint(checkpointIndex);
        long checkpointTime = System.currentTimeMillis() - progress.getStartTime();
        progress.getCheckpointTimes().add(checkpointTime);
        
        ParkourCourse.Checkpoint checkpoint = course.getCheckpoints().get(checkpointIndex);
        
        // Send checkpoint message
        if (checkpoint.getMessage() != null && !checkpoint.getMessage().isEmpty()) {
            String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
            String message = parkourMessages.get(language, checkpoint.getMessage());
            player.sendMessage(ColorHelper.parseComponent(message));
        }
        
        // Play checkpoint sound
        if (checkpoint.isPlaySound() && playerData.getSettings().isPlayCheckpointSounds()) {
            try {
                Sound sound = Sound.valueOf(checkpoint.getSoundType());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                // Invalid sound type, use default
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
        
        // Show progress
        if (course.getSettings().isShowActionBarProgress() && playerData.getSettings().isShowActionBar()) {
            int totalCheckpoints = course.getCheckpoints().size();
            sendParkourMessage(player, "course.checkpoint_progress", checkpointIndex + 1, totalCheckpoints);
        }
        
        // Save progress
        configManager.setObject(playerData);
    }
    
    public void completeCourse(Player player, String courseId) {
        PlayerParkourData playerData = getOrCreatePlayerData(player);
        PlayerParkourData.CourseProgress progress = playerData.getCourseProgress().get(courseId);
        
        if (progress == null || !progress.isActive()) {
            return;
        }
        
        ParkourCourse course = loadedCourses.get(courseId);
        if (course == null) {
            return;
        }
        
        // Calculate completion time
        long completionTime = System.currentTimeMillis() - progress.getStartTime();
        
        // Update stats
        PlayerParkourData.CourseStats stats = playerData.getCourseStats().get(courseId);
        stats.setCompletions(stats.getCompletions() + 1);
        stats.setTotalTimeSpent(stats.getTotalTimeSpent() + completionTime);
        
        boolean newRecord = false;
        if (completionTime < stats.getBestTime()) {
            stats.setBestTime(completionTime);
            newRecord = true;
        }
        
        // Update course records
        ParkourCourse.ParkourRecord record = new ParkourCourse.ParkourRecord();
        record.setPlayerId(player.getUniqueId().toString());
        record.setPlayerName(player.getName());
        record.setCompletionTime(completionTime);
        record.setTimestamp(System.currentTimeMillis());
        record.setAttempts(progress.getAttempts());
        
        course.getRecords().put(player.getUniqueId().toString(), record);
        
        // Clear active progress
        progress.setActive(false);
        
        // Teleport to end location
        player.teleport(course.getEndLocation());
        
        // Send completion message
        String timeFormatted = formatTime(completionTime);
        if (newRecord) {
            sendParkourMessage(player, "course.completed_new_record", course.getDisplayName(), timeFormatted);
        } else {
            String bestTimeFormatted = formatTime(stats.getBestTime());
            sendParkourMessage(player, "course.completed", course.getDisplayName(), timeFormatted, bestTimeFormatted);
        }
        
        // Give rewards
        giveRewards(player, course.getRewards());
        
        // Execute completion commands
        for (String command : course.getCompletionCommands()) {
            String formattedCommand = command
                .replace("{player}", player.getName())
                .replace("{time}", timeFormatted)
                .replace("{course}", course.getDisplayName());
            
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCommand);
        }
        
        // Save data
        CompletableFuture.allOf(
            configManager.setObject(playerData),
            configManager.setObject(course)
        );
    }
    
    private PlayerParkourData getOrCreatePlayerData(Player player) {
        String playerId = player.getUniqueId().toString();
        
        return configManager.getConfigOrGenerate(
            PlayerParkourData.class,
            () -> {
                PlayerParkourData data = new PlayerParkourData();
                data.setPlayerId(playerId);
                data.setPlayerName(player.getName());
                return data;
            }
        );
    }
    
    private boolean isPlayerInAnyCourse(PlayerParkourData playerData) {
        return playerData.getCourseProgress().values().stream()
            .anyMatch(PlayerParkourData.CourseProgress::isActive);
    }
    
    private void sendParkourMessage(Player player, String key, Object... args) {
        String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        String message = parkourMessages.get(language, key, args);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        long millis = milliseconds % 1000;
        
        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }
    
    private void giveRewards(Player player, Map<String, Object> rewards) {
        // Implementation depends on your reward system
        // Could include money, items, experience, etc.
    }
}
```

---

## 🏠 Home & Teleportation System

### Player Homes Configuration

```java
// Player Homes Data
@ConfigsCollection("player-homes")
public class PlayerHomesData {
    
    private String playerId;
    private String playerName;
    private Map<String, Home> homes = new HashMap<>();
    private HomeSettings settings = new HomeSettings();
    private int maxHomes = 3;
    
    public static class Home {
        private String name;
        private Location location;
        private String description;
        private Material iconMaterial = Material.RED_BED;
        private boolean isPublic = false;
        private Set<String> allowedPlayers = new HashSet<>();
        private long createdTime;
        private long lastVisited;
        private int visitCount = 0;
        
        // Constructors, getters, setters...
    }
    
    public static class HomeSettings {
        private boolean allowPublicHomes = true;
        private boolean showTeleportEffects = true;
        private int teleportDelay = 3; // seconds
        private boolean cancelOnMove = true;
        private boolean cancelOnDamage = true;
        
        // Getters, setters...
    }
    
    // Constructors, getters, setters...
}

// Home Manager
public class HomeManager {
    
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final Messages homeMessages;
    private final Map<UUID, BukkitTask> teleportTasks = new ConcurrentHashMap<>();
    
    public HomeManager() {
        this.configManager = MongoConfigsAPI.getConfigManager();
        this.languageManager = MongoConfigsAPI.getLanguageManager();
        this.homeMessages = configManager.messagesOf(HomeMessages.class);
    }
    
    public boolean createHome(Player player, String homeName, String description) {
        PlayerHomesData playerData = getOrCreatePlayerData(player);
        
        // Check home limit
        if (playerData.getHomes().size() >= playerData.getMaxHomes()) {
            sendHomeMessage(player, "home.limit_reached", playerData.getMaxHomes());
            return false;
        }
        
        // Check if home already exists
        if (playerData.getHomes().containsKey(homeName.toLowerCase())) {
            sendHomeMessage(player, "home.already_exists", homeName);
            return false;
        }
        
        // Create home
        PlayerHomesData.Home home = new PlayerHomesData.Home();
        home.setName(homeName);
        home.setLocation(player.getLocation());
        home.setDescription(description);
        home.setCreatedTime(System.currentTimeMillis());
        
        playerData.getHomes().put(homeName.toLowerCase(), home);
        
        // Save data
        configManager.setObject(playerData).thenRun(() -> {
            sendHomeMessage(player, "home.created", homeName);
        });
        
        return true;
    }
    
    public void teleportToHome(Player player, String homeName) {
        PlayerHomesData playerData = getOrCreatePlayerData(player);
        PlayerHomesData.Home home = playerData.getHomes().get(homeName.toLowerCase());
        
        if (home == null) {
            sendHomeMessage(player, "home.not_found", homeName);
            return;
        }
        
        // Check if teleportation is already in progress
        if (teleportTasks.containsKey(player.getUniqueId())) {
            sendHomeMessage(player, "home.teleport_in_progress");
            return;
        }
        
        int delay = playerData.getSettings().getTeleportDelay();
        
        if (delay <= 0 || player.hasPermission("homes.instant")) {
            // Instant teleport
            performTeleport(player, home);
        } else {
            // Delayed teleport
            sendHomeMessage(player, "home.teleport_starting", homeName, delay);
            
            Location startLocation = player.getLocation();
            
            BukkitTask task = Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
                teleportTasks.remove(player.getUniqueId());
                
                // Check if player moved (if setting enabled)
                if (playerData.getSettings().isCancelOnMove() && 
                    startLocation.distance(player.getLocation()) > 1.0) {
                    sendHomeMessage(player, "home.teleport_cancelled_moved");
                    return;
                }
                
                performTeleport(player, home);
                
            }, delay * 20L); // Convert seconds to ticks
            
            teleportTasks.put(player.getUniqueId(), task);
        }
    }
    
    public void teleportToPlayerHome(Player player, String targetPlayerName, String homeName) {
        // Find target player's home data
        String targetPlayerId = getPlayerIdByName(targetPlayerName);
        if (targetPlayerId == null) {
            sendHomeMessage(player, "player.not_found", targetPlayerName);
            return;
        }
        
        PlayerHomesData targetData = configManager.get("player.homes." + targetPlayerId, PlayerHomesData.class);
        if (targetData == null) {
            sendHomeMessage(player, "home.player_no_homes", targetPlayerName);
            return;
        }
        
        PlayerHomesData.Home home = targetData.getHomes().get(homeName.toLowerCase());
        if (home == null) {
            sendHomeMessage(player, "home.not_found_player", homeName, targetPlayerName);
            return;
        }
        
        // Check permissions
        String playerId = player.getUniqueId().toString();
        if (!home.isPublic() && !home.getAllowedPlayers().contains(playerId)) {
            sendHomeMessage(player, "home.no_access", homeName, targetPlayerName);
            return;
        }
        
        // Perform teleport
        performTeleport(player, home);
        sendHomeMessage(player, "home.teleported_to_player", homeName, targetPlayerName);
    }
    
    private void performTeleport(Player player, PlayerHomesData.Home home) {
        Location location = home.getLocation();
        
        // Ensure location is safe
        if (!isLocationSafe(location)) {
            location = findSafeLocation(location);
        }
        
        player.teleport(location);
        
        // Update home statistics
        home.setLastVisited(System.currentTimeMillis());
        home.setVisitCount(home.getVisitCount() + 1);
        
        // Show teleport effects
        PlayerHomesData playerData = getOrCreatePlayerData(player);
        if (playerData.getSettings().isShowTeleportEffects()) {
            player.spawnParticle(Particle.PORTAL, location, 20, 0.5, 0.5, 0.5, 0.1);
            player.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }
        
        sendHomeMessage(player, "home.teleported", home.getName());
        
        // Save updated data
        configManager.setObject(playerData);
    }
    
    public void deleteHome(Player player, String homeName) {
        PlayerHomesData playerData = getOrCreatePlayerData(player);
        
        if (!playerData.getHomes().containsKey(homeName.toLowerCase())) {
            sendHomeMessage(player, "home.not_found", homeName);
            return;
        }
        
        playerData.getHomes().remove(homeName.toLowerCase());
        
        configManager.setObject(playerData).thenRun(() -> {
            sendHomeMessage(player, "home.deleted", homeName);
        });
    }
    
    public void listHomes(Player player, String targetPlayerName) {
        String targetPlayerId;
        PlayerHomesData targetData;
        
        if (targetPlayerName == null) {
            // List own homes
            targetData = getOrCreatePlayerData(player);
            targetPlayerName = player.getName();
        } else {
            // List another player's homes
            targetPlayerId = getPlayerIdByName(targetPlayerName);
            if (targetPlayerId == null) {
                sendHomeMessage(player, "player.not_found", targetPlayerName);
                return;
            }
            
            targetData = configManager.get("player.homes." + targetPlayerId, PlayerHomesData.class);
            if (targetData == null) {
                sendHomeMessage(player, "home.player_no_homes", targetPlayerName);
                return;
            }
        }
        
        Map<String, PlayerHomesData.Home> homes = targetData.getHomes();
        
        if (homes.isEmpty()) {
            sendHomeMessage(player, "home.no_homes", targetPlayerName);
            return;
        }
        
        String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        
        // Send header
        String header = homeMessages.get(language, "home.list.header", targetPlayerName, homes.size());
        player.sendMessage(ColorHelper.parseComponent(header));
        
        // List homes
        homes.values().forEach(home -> {
            String homeInfo;
            if (home.isPublic()) {
                homeInfo = homeMessages.get(language, "home.list.entry_public", 
                    home.getName(), home.getDescription());
            } else {
                homeInfo = homeMessages.get(language, "home.list.entry_private", 
                    home.getName(), home.getDescription());
            }
            
            // Create clickable message for teleportation
            Component message = Component.text(ColorHelper.parseString(homeInfo))
                .clickEvent(ClickEvent.runCommand("/home " + home.getName()))
                .hoverEvent(HoverEvent.showText(Component.text(
                    ColorHelper.parseString(homeMessages.get(language, "home.list.click_hint")))));
            
            player.sendMessage(message);
        });
        
        // Send footer
        String footer = homeMessages.get(language, "home.list.footer");
        player.sendMessage(ColorHelper.parseComponent(footer));
    }
    
    private PlayerHomesData getOrCreatePlayerData(Player player) {
        String playerId = player.getUniqueId().toString();
        
        return configManager.getConfigOrGenerate(
            PlayerHomesData.class,
            () -> {
                PlayerHomesData data = new PlayerHomesData();
                data.setPlayerId(playerId);
                data.setPlayerName(player.getName());
                
                // Set max homes based on permissions
                if (player.hasPermission("homes.vip")) {
                    data.setMaxHomes(10);
                } else if (player.hasPermission("homes.premium")) {
                    data.setMaxHomes(5);
                } else {
                    data.setMaxHomes(3);
                }
                
                return data;
            }
        );
    }
    
    private void sendHomeMessage(Player player, String key, Object... args) {
        String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        String message = homeMessages.get(language, key, args);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    private boolean isLocationSafe(Location location) {
        // Check if location is safe for teleportation
        return location.getBlock().getType().isAir() && 
               location.clone().add(0, 1, 0).getBlock().getType().isAir() &&
               location.clone().add(0, -1, 0).getBlock().getType().isSolid();
    }
    
    private Location findSafeLocation(Location location) {
        // Find the nearest safe location
        // Implementation would scan nearby blocks
        return location;
    }
    
    private String getPlayerIdByName(String playerName) {
        // Implementation to get player UUID by name
        // Could use a player cache or Mojang API
        Player player = Bukkit.getPlayer(playerName);
        return player != null ? player.getUniqueId().toString() : null;
    }
    
    private JavaPlugin getPlugin() {
        // Return your plugin instance
        return null; // Placeholder
    }
}
```

---

## 🎯 Best Practices Summary

### 1. Configuration Organization

```java
// ✅ Good - Separate concerns
@ConfigsCollection("server-settings")     // Server configuration
@ConfigsCollection("player-data")         // Player data
@ConfigsCollection("economy-config")      // Economy settings
@ConfigsCollection("gui-messages")        // GUI messages

// ✅ Good - Use descriptive class names
public class ServerConfiguration { }
public class PlayerEconomyData { }
public class ParkourCourseConfig { }
```

### 2. Error Handling

```java
// ✅ Good - Always handle errors gracefully
try {
    PlayerData data = configManager.loadObject(PlayerData.class);
    // Use data...
} catch (Exception e) {
    getLogger().severe("Failed to load player data: " + e.getMessage());
    // Use default or fallback data
}
```

### 3. Async Operations

```java
// ✅ Good - Use async for non-critical operations
configManager.setObject(playerData).thenRun(() -> {
    player.sendMessage("Data saved successfully!");
}).exceptionally(error -> {
    player.sendMessage("Failed to save data!");
    return null;
});
```

---

*Next: Learn about [[GUI Development]] for creating multilingual user interfaces.*