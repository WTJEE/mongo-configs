# Creating GUI Components

Complete guide to creating interactive GUIs with MongoDB Configs API, including inventory management, pagination, and real-time updates.

## 📋 Overview

This tutorial covers creating sophisticated GUI components that integrate seamlessly with MongoDB Configs API for persistent data storage and real-time synchronization.

## 🏗️ Basic GUI Structure

### Base GUI Class

```java
public abstract class BaseGUI {
    
    protected final JavaPlugin plugin;
    protected final ConfigManager configManager;
    protected final Player player;
    protected final String language;
    protected Inventory inventory;
    
    public BaseGUI(JavaPlugin plugin, Player player) {
        this.plugin = plugin;
        this.configManager = MongoConfigsAPI.getConfigManager();
        this.player = player;
        this.language = MongoConfigsAPI.getLanguageManager()
            .getPlayerLanguageOrDefault(player.getUniqueId().toString());
    }
    
    public abstract void open();
    public abstract void handleClick(InventoryClickEvent event);
    public abstract String getTitle();
    
    protected void initializeInventory(int size, String title) {
        this.inventory = Bukkit.createInventory(null, size, ColorHelper.parseString(title));
        setupItems();
        player.openInventory(inventory);
    }
    
    protected abstract void setupItems();
    
    protected void addItem(int slot, ItemStack item) {
        if (inventory != null && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }
    
    protected void fillEmptySlots(ItemStack filler) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }
    
    protected ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ColorHelper.parseString(name));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream()
                    .map(ColorHelper::parseString)
                    .collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    protected ItemStack createPlayerHead(String playerName) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
            skull.setItemMeta(meta);
        }
        
        return skull;
    }
}
```

## 📄 Pagination System

### Paginated GUI

```java
public abstract class PaginatedGUI extends BaseGUI {
    
    protected int currentPage = 0;
    protected int itemsPerPage = 45;
    protected List<ItemStack> allItems;
    
    public PaginatedGUI(JavaPlugin plugin, Player player) {
        super(plugin, player);
        this.allItems = loadAllItems();
    }
    
    protected abstract List<ItemStack> loadAllItems();
    protected abstract String getItemName();
    
    @Override
    protected void setupItems() {
        // Add page items
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allItems.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            if (slot < 45) { // Leave room for navigation
                addItem(slot, allItems.get(i));
            }
        }
        
        // Add navigation items
        addNavigationItems();
        
        // Add page info
        addPageInfo();
    }
    
    private void addNavigationItems() {
        // Previous page button
        if (currentPage > 0) {
            ItemStack prevButton = createItem(Material.ARROW, 
                "&aPrevious Page", 
                Arrays.asList("&7Click to go to page " + currentPage));
            addItem(45, prevButton);
        }
        
        // Next page button
        if ((currentPage + 1) * itemsPerPage < allItems.size()) {
            ItemStack nextButton = createItem(Material.ARROW, 
                "&aNext Page", 
                Arrays.asList("&7Click to go to page " + (currentPage + 2)));
            addItem(53, nextButton);
        }
        
        // Close button
        ItemStack closeButton = createItem(Material.BARRIER, 
            "&cClose", 
            Arrays.asList("&7Click to close this menu"));
        addItem(49, closeButton);
    }
    
    private void addPageInfo() {
        int totalPages = (int) Math.ceil((double) allItems.size() / itemsPerPage);
        ItemStack infoItem = createItem(Material.PAPER, 
            "&ePage " + (currentPage + 1) + "/" + Math.max(1, totalPages),
            Arrays.asList(
                "&7Total items: &f" + allItems.size(),
                "&7Items per page: &f" + itemsPerPage
            ));
        addItem(48, infoItem);
    }
    
    public void nextPage() {
        if ((currentPage + 1) * itemsPerPage < allItems.size()) {
            currentPage++;
            refreshInventory();
        }
    }
    
    public void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            refreshInventory();
        }
    }
    
    protected void refreshInventory() {
        inventory.clear();
        setupItems();
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        
        int slot = event.getSlot();
        
        // Handle navigation
        if (slot == 45 && currentPage > 0) {
            previousPage();
        } else if (slot == 53 && (currentPage + 1) * itemsPerPage < allItems.size()) {
            nextPage();
        } else if (slot == 49 && clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
        } else if (slot < 45) {
            // Handle item click
            int itemIndex = currentPage * itemsPerPage + slot;
            if (itemIndex < allItems.size()) {
                handleItemClick(event, itemIndex);
            }
        }
    }
    
    protected abstract void handleItemClick(InventoryClickEvent event, int itemIndex);
}
```

## 🔄 Real-time GUI Updates

### Change Stream Integration

```java
public class RealtimeGUI extends BaseGUI {
    
    private final Map<String, ItemStack> itemCache = new ConcurrentHashMap<>();
    private ChangeStreamSubscription subscription;
    
    public RealtimeGUI(JavaPlugin plugin, Player player) {
        super(plugin, player);
        setupChangeStreams();
    }
    
    private void setupChangeStreams() {
        // Watch for changes to relevant data
        subscription = configManager.watchCollection(YourDataClass.class, changeEvent -> {
            YourDataClass data = changeEvent.getDocument();
            if (data != null) {
                // Update cache
                updateItemCache(data);
                
                // Refresh GUI if player is viewing it
                if (player.getOpenInventory().getTopInventory() == inventory) {
                    plugin.getServer().getScheduler().runTask(plugin, this::refreshInventory);
                }
            }
        });
    }
    
    private void updateItemCache(YourDataClass data) {
        // Update the cached item representation
        ItemStack item = createItemFromData(data);
        itemCache.put(data.getId(), item);
    }
    
    @Override
    protected void setupItems() {
        // Load items from cache
        int slot = 0;
        for (ItemStack item : itemCache.values()) {
            if (slot >= 45) break; // Leave room for controls
            addItem(slot++, item);
        }
        
        // Add control items
        addControlItems();
    }
    
    private void addControlItems() {
        // Refresh button
        ItemStack refreshButton = createItem(Material.CLOCK, 
            "&aRefresh", 
            Arrays.asList("&7Click to refresh data"));
        addItem(49, refreshButton);
        
        // Close button
        ItemStack closeButton = createItem(Material.BARRIER, 
            "&cClose", 
            Arrays.asList("&7Click to close"));
        addItem(53, closeButton);
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;
        
        if (clickedItem.getType() == Material.CLOCK) {
            refreshInventory();
        } else if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
        } else {
            // Handle data item click
            handleDataItemClick(event, clickedItem);
        }
    }
    
    protected abstract void handleDataItemClick(InventoryClickEvent event, ItemStack item);
    protected abstract ItemStack createItemFromData(YourDataClass data);
    
    public void cleanup() {
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }
}
```

## 🎨 Advanced GUI Components

### Confirmation Dialog

```java
public class ConfirmationGUI extends BaseGUI {
    
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private final String confirmMessage;
    private final String cancelMessage;
    
    public ConfirmationGUI(JavaPlugin plugin, Player player, 
                         String title, String message,
                         Runnable onConfirm, Runnable onCancel) {
        super(plugin, player);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.confirmMessage = message;
        
        // Default cancel message
        this.cancelMessage = "Action cancelled.";
    }
    
    @Override
    public void open() {
        initializeInventory(27, getTitle());
    }
    
    @Override
    public String getTitle() {
        return "&cConfirm Action";
    }
    
    @Override
    protected void setupItems() {
        // Add confirmation message
        ItemStack messageItem = createItem(Material.PAPER, 
            "&e" + confirmMessage,
            Arrays.asList(
                "&7Are you sure you want to",
                "&7perform this action?"
            ));
        addItem(13, messageItem);
        
        // Confirm button
        ItemStack confirmButton = createItem(Material.GREEN_WOOL, 
            "&aConfirm", 
            Arrays.asList("&7Click to confirm"));
        addItem(11, confirmButton);
        
        // Cancel button
        ItemStack cancelButton = createItem(Material.RED_WOOL, 
            "&cCancel", 
            Arrays.asList("&7Click to cancel"));
        addItem(15, cancelButton);
        
        // Fill empty slots
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, 
            "&7", Collections.emptyList());
        fillEmptySlots(filler);
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;
        
        if (clickedItem.getType() == Material.GREEN_WOOL) {
            // Confirm action
            player.closeInventory();
            if (onConfirm != null) {
                onConfirm.run();
            }
        } else if (clickedItem.getType() == Material.RED_WOOL) {
            // Cancel action
            player.closeInventory();
            if (onCancel != null) {
                onCancel.run();
            }
            player.sendMessage(ColorHelper.parseComponent("&c" + cancelMessage));
        }
    }
}
```

### Input GUI

```java
public class InputGUI extends BaseGUI {
    
    private final Consumer<String> onInput;
    private final String prompt;
    private final String defaultValue;
    private String currentInput = "";
    
    public InputGUI(JavaPlugin plugin, Player player, 
                   String title, String prompt, String defaultValue,
                   Consumer<String> onInput) {
        super(plugin, player);
        this.prompt = prompt;
        this.defaultValue = defaultValue != null ? defaultValue : "";
        this.onInput = onInput;
        this.currentInput = this.defaultValue;
    }
    
    @Override
    public void open() {
        initializeInventory(27, getTitle());
    }
    
    @Override
    public String getTitle() {
        return "&6" + prompt;
    }
    
    @Override
    protected void setupItems() {
        // Current input display
        ItemStack inputDisplay = createItem(Material.PAPER, 
            "&eCurrent Input:", 
            Arrays.asList(
                "&f" + (currentInput.isEmpty() ? "&7(none)" : currentInput),
                "",
                "&7Click the buttons below",
                "&7to modify your input"
            ));
        addItem(13, inputDisplay);
        
        // Letter buttons (A-Z)
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < 26 && i < 9; i++) {
            ItemStack letterButton = createItem(Material.BOOK, 
                "&a" + letters.charAt(i), 
                Arrays.asList("&7Click to add '" + letters.charAt(i) + "'"));
            addItem(i, letterButton);
        }
        
        // Control buttons
        ItemStack backspaceButton = createItem(Material.REDSTONE, 
            "&cBackspace", 
            Arrays.asList("&7Remove last character"));
        addItem(17, backspaceButton);
        
        ItemStack clearButton = createItem(Material.BARRIER, 
            "&cClear", 
            Arrays.asList("&7Clear all input"));
        addItem(18, clearButton);
        
        ItemStack confirmButton = createItem(Material.GREEN_WOOL, 
            "&aConfirm", 
            Arrays.asList("&7Confirm input"));
        addItem(26, confirmButton);
        
        // Space button
        ItemStack spaceButton = createItem(Material.GRAY_WOOL, 
            "&7Space", 
            Arrays.asList("&7Add space"));
        addItem(9, spaceButton);
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;
        
        Material type = clickedItem.getType();
        
        if (type == Material.BOOK) {
            // Letter button
            String letter = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
            currentInput += letter;
            refreshInventory();
            
        } else if (type == Material.REDSTONE) {
            // Backspace
            if (!currentInput.isEmpty()) {
                currentInput = currentInput.substring(0, currentInput.length() - 1);
                refreshInventory();
            }
            
        } else if (type == Material.BARRIER) {
            // Clear
            currentInput = "";
            refreshInventory();
            
        } else if (type == Material.GRAY_WOOL) {
            // Space
            currentInput += " ";
            refreshInventory();
            
        } else if (type == Material.GREEN_WOOL) {
            // Confirm
            player.closeInventory();
            if (onInput != null) {
                onInput.accept(currentInput);
            }
        }
    }
}
```

## 🔧 GUI Manager

### Centralized GUI Management

```java
public class GUIManager {
    
    private final JavaPlugin plugin;
    private final Map<UUID, BaseGUI> openGUIs = new ConcurrentHashMap<>();
    private final Map<String, Class<? extends BaseGUI>> guiRegistry = new HashMap<>();
    
    public GUIManager(JavaPlugin plugin) {
        this.plugin = plugin;
        registerGUIs();
        setupEventListener();
    }
    
    private void registerGUIs() {
        // Register GUI classes
        guiRegistry.put("shop", ShopGUI.class);
        guiRegistry.put("bank", BankGUI.class);
        guiRegistry.put("settings", SettingsGUI.class);
        // Add more as needed
    }
    
    private void setupEventListener() {
        plugin.getServer().getPluginManager().registerEvents(new InventoryClickListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new InventoryCloseListener(), plugin);
    }
    
    public void openGUI(Player player, String guiType, Object... args) {
        try {
            Class<? extends BaseGUI> guiClass = guiRegistry.get(guiType);
            if (guiClass == null) {
                player.sendMessage("§cUnknown GUI type: " + guiType);
                return;
            }
            
            // Create GUI instance with reflection
            Constructor<? extends BaseGUI> constructor = guiClass.getConstructor(JavaPlugin.class, Player.class);
            BaseGUI gui = constructor.newInstance(plugin, player);
            
            // Store reference
            openGUIs.put(player.getUniqueId(), gui);
            
            // Open GUI
            gui.open();
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to open GUI " + guiType + ": " + e.getMessage());
            player.sendMessage("§cFailed to open GUI. Please try again.");
        }
    }
    
    public void closeGUI(Player player) {
        BaseGUI gui = openGUIs.remove(player.getUniqueId());
        if (gui != null) {
            // Cleanup if needed
            if (gui instanceof RealtimeGUI) {
                ((RealtimeGUI) gui).cleanup();
            }
        }
    }
    
    private class InventoryClickListener implements Listener {
        @EventHandler
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player)) return;
            
            Player player = (Player) event.getWhoClicked();
            BaseGUI gui = openGUIs.get(player.getUniqueId());
            
            if (gui != null) {
                gui.handleClick(event);
            }
        }
    }
    
    private class InventoryCloseListener implements Listener {
        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player)) return;
            
            Player player = (Player) event.getPlayer();
            closeGUI(player);
        }
    }
}
```

## 🎯 Advanced Features

### Animated GUI Elements

```java
public class AnimatedGUI extends BaseGUI {
    
    private final List<ItemStack> animationFrames;
    private int currentFrame = 0;
    private int animationTaskId = -1;
    
    public AnimatedGUI(JavaPlugin plugin, Player player) {
        super(plugin, player);
        this.animationFrames = createAnimationFrames();
        startAnimation();
    }
    
    private List<ItemStack> createAnimationFrames() {
        List<ItemStack> frames = new ArrayList<>();
        
        // Create different frames for animation
        for (int i = 0; i < 4; i++) {
            ItemStack frame = createItem(Material.DIAMOND, 
                "&aLoading" + ".".repeat(i + 1), 
                Arrays.asList("&7Please wait..."));
            frames.add(frame);
        }
        
        return frames;
    }
    
    private void startAnimation() {
        animationTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (inventory != null && player.isOnline() && 
                player.getOpenInventory().getTopInventory() == inventory) {
                
                // Update animation frame
                currentFrame = (currentFrame + 1) % animationFrames.size();
                inventory.setItem(13, animationFrames.get(currentFrame));
            }
        }, 0L, 10L).getTaskId(); // Update every 10 ticks (0.5 seconds)
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        // Handle clicks while animating
        event.setCancelled(true);
        
        if (event.getCurrentItem() != null && 
            event.getCurrentItem().getType() == Material.BARRIER) {
            player.closeInventory();
        }
    }
    
    public void stopAnimation() {
        if (animationTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(animationTaskId);
            animationTaskId = -1;
        }
    }
    
    @Override
    public void open() {
        initializeInventory(27, "&6Loading...");
        
        // Add cancel button
        ItemStack cancelButton = createItem(Material.BARRIER, 
            "&cCancel", 
            Arrays.asList("&7Click to cancel"));
        addItem(22, cancelButton);
    }
}
```

### Searchable GUI

```java
public class SearchableGUI extends PaginatedGUI {
    
    private String searchQuery = "";
    private List<ItemStack> filteredItems;
    
    public SearchableGUI(JavaPlugin plugin, Player player) {
        super(plugin, player);
        this.filteredItems = new ArrayList<>(allItems);
    }
    
    @Override
    protected void setupItems() {
        // Add search input
        addSearchInput();
        
        // Add filtered items
        super.setupItems();
    }
    
    private void addSearchInput() {
        ItemStack searchItem = createItem(Material.COMPASS, 
            "&eSearch: " + (searchQuery.isEmpty() ? "&7(none)" : "&f" + searchQuery),
            Arrays.asList(
                "&7Click to change search",
                "&7Current: &f" + (searchQuery.isEmpty() ? "showing all" : searchQuery)
            ));
        addItem(45, searchItem);
    }
    
    public void setSearchQuery(String query) {
        this.searchQuery = query.toLowerCase();
        filterItems();
        currentPage = 0;
        refreshInventory();
    }
    
    private void filterItems() {
        if (searchQuery.isEmpty()) {
            filteredItems = new ArrayList<>(allItems);
        } else {
            filteredItems = allItems.stream()
                .filter(this::matchesSearch)
                .collect(Collectors.toList());
        }
    }
    
    private boolean matchesSearch(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        String displayName = meta.getDisplayName();
        List<String> lore = meta.getLore();
        
        // Check display name
        if (displayName != null && 
            ChatColor.stripColor(displayName).toLowerCase().contains(searchQuery)) {
            return true;
        }
        
        // Check lore
        if (lore != null) {
            for (String line : lore) {
                if (ChatColor.stripColor(line).toLowerCase().contains(searchQuery)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    @Override
    protected List<ItemStack> loadAllItems() {
        return filteredItems;
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem != null && clickedItem.getType() == Material.COMPASS) {
            // Open search input GUI
            openSearchInput();
        } else {
            super.handleClick(event);
        }
    }
    
    private void openSearchInput() {
        new InputGUI(plugin, player, "Search", "Enter search query:", searchQuery, query -> {
            setSearchQuery(query);
            // Re-open this GUI
            open();
        }).open();
    }
}
```

## 🎨 GUI Themes

### Theme System

```java
public class GUITheme {
    
    private final String primaryColor;
    private final String secondaryColor;
    private final String accentColor;
    private final Material backgroundMaterial;
    private final Material buttonMaterial;
    private final Material accentMaterial;
    
    public GUITheme(String primary, String secondary, String accent, 
                   Material background, Material button, Material accentMat) {
        this.primaryColor = primary;
        this.secondaryColor = secondary;
        this.accentColor = accent;
        this.backgroundMaterial = background;
        this.buttonMaterial = button;
        this.accentMaterial = accentMat;
    }
    
    // Predefined themes
    public static final GUITheme DARK = new GUITheme(
        "&8", "&7", "&e", 
        Material.BLACK_STAINED_GLASS_PANE, 
        Material.GRAY_WOOL, 
        Material.YELLOW_WOOL
    );
    
    public static final GUITheme LIGHT = new GUITheme(
        "&f", "&7", "&0", 
        Material.WHITE_STAINED_GLASS_PANE, 
        Material.LIGHT_GRAY_WOOL, 
        Material.BLACK_WOOL
    );
    
    public static final GUITheme NATURE = new GUITheme(
        "&2", "&a", "&e", 
        Material.GREEN_STAINED_GLASS_PANE, 
        Material.LIME_WOOL, 
        Material.YELLOW_WOOL
    );
    
    // Getters...
}
```

## 📊 Performance Considerations

### GUI Performance Tips

1. **Item Caching**: Cache frequently used ItemStacks
2. **Lazy Loading**: Load GUI content only when needed
3. **Async Updates**: Update GUI content asynchronously
4. **Memory Management**: Clean up unused GUI references
5. **Event Optimization**: Use specific event handlers when possible

```java
public class GUIPerformanceManager {
    
    private final Map<String, ItemStack> itemCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGUIOpen = new ConcurrentHashMap<>();
    
    public ItemStack getCachedItem(String key, Supplier<ItemStack> creator) {
        return itemCache.computeIfAbsent(key, k -> creator.get());
    }
    
    public boolean canOpenGUI(Player player, long cooldownMs) {
        Long lastOpen = lastGUIOpen.get(player.getUniqueId());
        if (lastOpen == null) return true;
        
        return System.currentTimeMillis() - lastOpen > cooldownMs;
    }
    
    public void recordGUIOpen(Player player) {
        lastGUIOpen.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    public void cleanupOldEntries(long maxAgeMs) {
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        lastGUIOpen.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}
```

---

*Next: Learn about [[Multilingual Support]] for internationalizing your GUIs.*