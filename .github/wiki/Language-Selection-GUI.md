# Language Selection GUI

Interactive GUI component for players to select and manage their preferred language settings.

## 🎨 GUI Overview

The Language Selection GUI provides an intuitive interface for players to choose their preferred language, toggle auto-detection, and view current language settings.

## 📋 Core Implementation

### LanguageSelectionGUI Class

```java
public class LanguageSelectionGUI extends BaseGUI {
    
    private final LanguageManager languageManager;
    private final PluginMessages messages;
    private final Map<String, ItemStack> languageItems = new HashMap<>();
    
    public LanguageSelectionGUI(JavaPlugin plugin, Player player) {
        super(plugin, player);
        this.languageManager = MongoConfigsAPI.getLanguageManager();
        this.messages = plugin.getMessages();
        initializeLanguageItems();
    }
    
    private void initializeLanguageItems() {
        List<String> supportedLanguages = languageManager.getSupportedLanguages();
        
        for (String langCode : supportedLanguages) {
            languageItems.put(langCode, createLanguageItem(langCode));
        }
    }
    
    @Override
    public void open() {
        initializeInventory(54, getTitle());
        setupItems();
        player.openInventory(inventory);
    }
    
    @Override
    public String getTitle() {
        String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        return messages.getGuiTitle(language, "language_selection");
    }
    
    @Override
    protected void setupItems() {
        // Language selection area (top 4 rows)
        setupLanguageGrid();
        
        // Current language display
        setupCurrentLanguageDisplay();
        
        // Control buttons
        setupControlButtons();
        
        // Information area
        setupInfoArea();
        
        // Fill empty slots
        fillEmptySlots();
    }
    
    private void setupLanguageGrid() {
        List<String> supportedLanguages = languageManager.getSupportedLanguages();
        int slot = 0;
        
        for (String langCode : supportedLanguages) {
            if (slot >= 28) break; // Reserve bottom rows for controls
            
            ItemStack langItem = languageItems.get(langCode);
            if (langItem != null) {
                addItem(slot, langItem);
            }
            slot++;
        }
    }
    
    private ItemStack createLanguageItem(String langCode) {
        String langName = languageManager.getLanguageName(langCode);
        String flagEmoji = getFlagEmoji(langCode);
        
        Material icon = getLanguageIcon(langCode);
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        
        // Set display name with color coding
        String displayName = ColorHelper.parseString("&e" + langName + " " + flagEmoji);
        meta.setDisplayName(displayName);
        
        // Create detailed lore
        List<String> lore = new ArrayList<>();
        lore.add(ColorHelper.parseString("&7Language code: &f" + langCode.toUpperCase()));
        lore.add(""); // Empty line
        
        // Check if this is the player's current language
        String currentLang = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        if (langCode.equals(currentLang)) {
            lore.add(ColorHelper.parseString("&a✓ Currently selected"));
            lore.add(ColorHelper.parseString("&7Click to keep this language"));
        } else {
            lore.add(ColorHelper.parseString("&7Click to select this language"));
        }
        
        // Add language statistics if available
        int usageCount = getLanguageUsageCount(langCode);
        if (usageCount > 0) {
            lore.add(ColorHelper.parseString("&7Users: &f" + usageCount));
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    private void setupCurrentLanguageDisplay() {
        String currentLang = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        String langName = languageManager.getLanguageName(currentLang);
        
        ItemStack currentItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = currentItem.getItemMeta();
        
        meta.setDisplayName(ColorHelper.parseString("&aCurrent Language"));
        
        List<String> lore = new ArrayList<>();
        lore.add(ColorHelper.parseString("&f" + langName));
        lore.add(ColorHelper.parseString("&7(" + currentLang.toUpperCase() + ")"));
        lore.add("");
        lore.add(ColorHelper.parseString("&7Auto-detect: &f" + (isAutoDetectEnabled() ? "Enabled" : "Disabled")));
        
        meta.setLore(lore);
        currentItem.setItemMeta(meta);
        
        addItem(31, currentItem);
    }
    
    private void setupControlButtons() {
        // Auto-detect toggle button
        ItemStack autoDetectItem = new ItemStack(Material.COMPASS);
        ItemMeta autoDetectMeta = autoDetectItem.getItemMeta();
        autoDetectMeta.setDisplayName(ColorHelper.parseString("&bAuto-Detect Language"));
        
        List<String> autoDetectLore = new ArrayList<>();
        autoDetectLore.add(ColorHelper.parseString("&7Currently: &f" + (isAutoDetectEnabled() ? "Enabled" : "Disabled")));
        autoDetectLore.add("");
        if (isAutoDetectEnabled()) {
            autoDetectLore.add(ColorHelper.parseString("&7Click to &cdisable &7auto-detection"));
            autoDetectLore.add(ColorHelper.parseString("&7Your language will be manually set"));
        } else {
            autoDetectLore.add(ColorHelper.parseString("&7Click to &aenable &7auto-detection"));
            autoDetectLore.add(ColorHelper.parseString("&7Your language will be detected automatically"));
        }
        
        autoDetectMeta.setLore(autoDetectLore);
        autoDetectItem.setItemMeta(autoDetectMeta);
        addItem(32, autoDetectItem);
        
        // Detect now button
        ItemStack detectNowItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta detectNowMeta = detectNowItem.getItemMeta();
        detectNowMeta.setDisplayName(ColorHelper.parseString("&eDetect Language Now"));
        
        List<String> detectNowLore = new ArrayList<>();
        detectNowLore.add(ColorHelper.parseString("&7Force language detection"));
        detectNowLore.add(ColorHelper.parseString("&7Based on your client locale"));
        
        detectNowMeta.setLore(detectNowLore);
        detectNowItem.setItemMeta(detectNowMeta);
        addItem(33, detectNowItem);
        
        // Close button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ColorHelper.parseString("&cClose"));
        closeMeta.setLore(Arrays.asList(ColorHelper.parseString("&7Click to close this menu")));
        closeItem.setItemMeta(closeMeta);
        addItem(49, closeItem);
        
        // Help button
        ItemStack helpItem = new ItemStack(Material.BOOK);
        ItemMeta helpMeta = helpItem.getItemMeta();
        helpMeta.setDisplayName(ColorHelper.parseString("&6Help & Information"));
        helpMeta.setLore(Arrays.asList(
            ColorHelper.parseString("&7Click for language help"),
            ColorHelper.parseString("&7and usage information")
        ));
        helpItem.setItemMeta(helpMeta);
        addItem(50, helpItem);
    }
    
    private void setupInfoArea() {
        // Language statistics
        ItemStack statsItem = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(ColorHelper.parseString("&6Language Statistics"));
        
        List<String> statsLore = new ArrayList<>();
        statsLore.add(ColorHelper.parseString("&7Most popular languages:"));
        
        Map<String, Integer> usageStats = getLanguageUsageStats();
        usageStats.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .forEach(entry -> {
                String langName = languageManager.getLanguageName(entry.getKey());
                statsLore.add(ColorHelper.parseString("&f" + langName + ": &e" + entry.getValue()));
            });
        
        statsMeta.setLore(statsLore);
        statsItem.setItemMeta(statsMeta);
        addItem(48, statsItem);
    }
    
    private void fillEmptySlots() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        
        int slot = event.getSlot();
        
        // Language selection (slots 0-27)
        if (slot >= 0 && slot < 28) {
            handleLanguageSelection(slot);
        }
        // Current language display (slot 31)
        else if (slot == 31) {
            // Could show detailed language info
        }
        // Auto-detect toggle (slot 32)
        else if (slot == 32) {
            toggleAutoDetect();
        }
        // Detect now (slot 33)
        else if (slot == 33) {
            detectLanguageNow();
        }
        // Close (slot 49)
        else if (slot == 49) {
            player.closeInventory();
        }
        // Help (slot 50)
        else if (slot == 50) {
            showHelp();
        }
    }
    
    private void handleLanguageSelection(int slot) {
        List<String> supportedLanguages = languageManager.getSupportedLanguages();
        
        if (slot < supportedLanguages.size()) {
            String selectedLang = supportedLanguages.get(slot);
            selectLanguage(selectedLang);
        }
    }
    
    private void selectLanguage(String langCode) {
        languageManager.setPlayerLanguage(player.getUniqueId().toString(), langCode, true);
        
        String langName = languageManager.getLanguageName(langCode);
        String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        String message = messages.getLanguageChanged(language, langName);
        player.sendMessage(ColorHelper.parseComponent(message));
        
        // Refresh GUI to show new selection
        refreshInventory();
    }
    
    private void toggleAutoDetect() {
        // This would require extending the language manager to store auto-detect preference
        // Implementation depends on your PlayerLanguage structure
        boolean currentState = isAutoDetectEnabled();
        setAutoDetectEnabled(!currentState);
        
        String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        String message = messages.getAutoDetectToggled(language, !currentState);
        player.sendMessage(ColorHelper.parseComponent(message));
        
        refreshInventory();
    }
    
    private void detectLanguageNow() {
        String detected = detectPlayerLanguage();
        if (detected != null) {
            languageManager.setPlayerLanguage(player.getUniqueId().toString(), detected, false);
            
            String langName = languageManager.getLanguageName(detected);
            String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
            String message = messages.getLanguageDetected(language, langName);
            player.sendMessage(ColorHelper.parseComponent(message));
            
            refreshInventory();
        } else {
            String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
            String message = messages.getLanguageDetectionFailed(language);
            player.sendMessage(ColorHelper.parseComponent(message));
        }
    }
    
    private void showHelp() {
        String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        
        List<String> helpLines = Arrays.asList(
            messages.getLanguageHelpHeader(language),
            "&f• &7Click on a language to select it",
            "&f• &7Use auto-detect to automatically set your language",
            "&f• &7Your language affects all plugin messages",
            "&f• &7Language preference is saved permanently",
            "",
            "&6Available languages:"
        );
        
        for (String line : helpLines) {
            player.sendMessage(ColorHelper.parseComponent(line));
        }
        
        List<String> supportedLanguages = languageManager.getSupportedLanguages();
        for (String langCode : supportedLanguages) {
            String langName = languageManager.getLanguageName(langCode);
            player.sendMessage(ColorHelper.parseComponent("&f- " + langName + " &7(" + langCode.toUpperCase() + ")"));
        }
    }
    
    @Override
    public void handleClose(InventoryCloseEvent event) {
        // Cleanup if needed
    }
    
    // Helper methods (implementations depend on your system)
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
    
    private int getLanguageUsageCount(String langCode) {
        // Implementation depends on your analytics system
        return 0; // Placeholder
    }
    
    private Map<String, Integer> getLanguageUsageStats() {
        // Implementation depends on your analytics system
        return new HashMap<>(); // Placeholder
    }
    
    private boolean isAutoDetectEnabled() {
        // Implementation depends on your PlayerLanguage structure
        return true; // Placeholder
    }
    
    private void setAutoDetectEnabled(boolean enabled) {
        // Implementation depends on your PlayerLanguage structure
    }
    
    private String detectPlayerLanguage() {
        // Implementation depends on your language detection system
        return languageManager.detectFromLocale(player.getLocale());
    }
}
```

## 🎯 Advanced Features

### Language Preview

```java
public class LanguagePreviewGUI extends LanguageSelectionGUI {
    
    private String previewLanguage;
    
    public LanguagePreviewGUI(JavaPlugin plugin, Player player, String previewLanguage) {
        super(plugin, player);
        this.previewLanguage = previewLanguage;
    }
    
    @Override
    public String getTitle() {
        return messages.getGuiTitle(previewLanguage, "language_preview");
    }
    
    @Override
    protected void setupItems() {
        super.setupItems();
        
        // Add preview indicator
        ItemStack previewItem = new ItemStack(Material.EYE_OF_ENDER);
        ItemMeta meta = previewItem.getItemMeta();
        meta.setDisplayName(ColorHelper.parseString("&6Preview Mode"));
        meta.setLore(Arrays.asList(
            ColorHelper.parseString("&7You are previewing: &f" + languageManager.getLanguageName(previewLanguage)),
            ColorHelper.parseString("&7Changes are &cnot saved"),
            ColorHelper.parseString("&7Click to exit preview")
        ));
        previewItem.setItemMeta(meta);
        addItem(45, previewItem);
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getSlot() == 45) {
            // Exit preview
            new LanguageSelectionGUI(plugin, player).open();
            return;
        }
        
        // Handle other clicks in preview mode
        super.handleClick(event);
    }
}
```

### Language Settings GUI

```java
public class LanguageSettingsGUI extends BaseGUI {
    
    private final LanguageManager languageManager;
    private final PluginMessages messages;
    
    public LanguageSettingsGUI(JavaPlugin plugin, Player player) {
        super(plugin, player);
        this.languageManager = MongoConfigsAPI.getLanguageManager();
        this.messages = plugin.getMessages();
    }
    
    @Override
    public void open() {
        initializeInventory(27, getTitle());
        setupItems();
        player.openInventory(inventory);
    }
    
    @Override
    public String getTitle() {
        String language = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        return messages.getGuiTitle(language, "language_settings");
    }
    
    @Override
    protected void setupItems() {
        // Language selection button
        ItemStack languageItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta languageMeta = languageItem.getItemMeta();
        languageMeta.setDisplayName(ColorHelper.parseString("&aSelect Language"));
        languageMeta.setLore(Arrays.asList(
            ColorHelper.parseString("&7Click to open language selection"),
            ColorHelper.parseString("&7Current: &f" + getCurrentLanguageName())
        ));
        languageItem.setItemMeta(languageMeta);
        addItem(11, languageItem);
        
        // Auto-detect toggle
        ItemStack autoDetectItem = new ItemStack(Material.COMPASS);
        ItemMeta autoDetectMeta = autoDetectItem.getItemMeta();
        autoDetectMeta.setDisplayName(ColorHelper.parseString("&bAuto-Detect"));
        autoDetectMeta.setLore(Arrays.asList(
            ColorHelper.parseString("&7Status: &f" + (isAutoDetectEnabled() ? "Enabled" : "Disabled")),
            ColorHelper.parseString("&7Click to toggle")
        ));
        autoDetectItem.setItemMeta(autoDetectMeta);
        addItem(13, autoDetectItem);
        
        // Preview languages
        ItemStack previewItem = new ItemStack(Material.EYE_OF_ENDER);
        ItemMeta previewMeta = previewItem.getItemMeta();
        previewMeta.setDisplayName(ColorHelper.parseString("&6Preview Languages"));
        previewMeta.setLore(Arrays.asList(
            ColorHelper.parseString("&7Click to preview different languages"),
            ColorHelper.parseString("&7See how the plugin looks in other languages")
        ));
        previewItem.setItemMeta(previewMeta);
        addItem(15, previewItem);
        
        // Close button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ColorHelper.parseString("&cClose"));
        closeItem.setItemMeta(closeMeta);
        addItem(22, closeItem);
        
        fillEmptySlots();
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getSlot();
        
        switch (slot) {
            case 11:
                new LanguageSelectionGUI(plugin, player).open();
                break;
            case 13:
                toggleAutoDetect();
                refreshInventory();
                break;
            case 15:
                showLanguagePreview();
                break;
            case 22:
                player.closeInventory();
                break;
        }
    }
    
    private void showLanguagePreview() {
        // Open a preview selection GUI
        new LanguagePreviewSelectionGUI(plugin, player).open();
    }
    
    private String getCurrentLanguageName() {
        String currentLang = languageManager.getPlayerLanguageOrDefault(player.getUniqueId().toString());
        return languageManager.getLanguageName(currentLang);
    }
    
    private boolean isAutoDetectEnabled() {
        // Implementation depends on your system
        return true;
    }
    
    private void toggleAutoDetect() {
        // Implementation depends on your system
    }
}
```

## 🎨 Customization Options

### Theme Support

```java
public class LanguageGUITheme {
    
    private final Map<String, Object> themeSettings = new HashMap<>();
    
    public LanguageGUITheme() {
        // Default theme settings
        themeSettings.put("primary_color", "&a");
        themeSettings.put("secondary_color", "&7");
        themeSettings.put("accent_color", "&e");
        themeSettings.put("error_color", "&c");
        themeSettings.put("success_color", "&a");
        themeSettings.put("background_material", Material.GRAY_STAINED_GLASS_PANE);
        themeSettings.put("selected_indicator", "✓");
        themeSettings.put("unselected_indicator", "○");
    }
    
    public String getPrimaryColor() {
        return (String) themeSettings.get("primary_color");
    }
    
    public String getSecondaryColor() {
        return (String) themeSettings.get("secondary_color");
    }
    
    public Material getBackgroundMaterial() {
        return (Material) themeSettings.get("background_material");
    }
    
    public void setThemeSetting(String key, Object value) {
        themeSettings.put(key, value);
    }
    
    public Object getThemeSetting(String key) {
        return themeSettings.get(key);
    }
}
```

### Animation Effects

```java
public class LanguageGUIAnimations {
    
    public static void playSelectionAnimation(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null) return;
        
        // Simple glow effect
        ItemMeta meta = item.getItemMeta();
        String originalName = meta.getDisplayName();
        
        // Add glow effect temporarily
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + originalName);
        item.setItemMeta(meta);
        
        // Reset after a short delay
        new BukkitRunnable() {
            @Override
            public void run() {
                meta.setDisplayName(originalName);
                item.setItemMeta(meta);
            }
        }.runTaskLater(JavaPlugin.getProvidingPlugin(LanguageGUIAnimations.class), 20L);
    }
    
    public static void playTransitionAnimation(Player player, String fromLang, String toLang) {
        // Play sound effect
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        
        // Send transition message
        player.sendTitle(
            ChatColor.GREEN + "Language Changed!",
            ChatColor.YELLOW + languageManager.getLanguageName(toLang),
            10, 40, 10
        );
    }
}
```

## 🔧 Integration Examples

### Command Integration

```java
public class LanguageCommand implements CommandExecutor {
    
    private final JavaPlugin plugin;
    private final LanguageManager languageManager;
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            // Open main language GUI
            new LanguageSettingsGUI(plugin, player).open();
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "select":
                new LanguageSelectionGUI(plugin, player).open();
                return true;
            case "settings":
                new LanguageSettingsGUI(plugin, player).open();
                return true;
            default:
                sendUsage(player);
                return true;
        }
    }
    
    private void sendUsage(Player player) {
        player.sendMessage(ColorHelper.parseComponent("&aLanguage Commands:"));
        player.sendMessage(ColorHelper.parseComponent("&f/lang &7- Open language settings"));
        player.sendMessage(ColorHelper.parseComponent("&f/lang select &7- Open language selection"));
        player.sendMessage(ColorHelper.parseComponent("&f/lang settings &7- Open language settings"));
    }
}
```

### Event Integration

```java
public class LanguageGUIListener implements Listener {
    
    private final JavaPlugin plugin;
    private final LanguageManager languageManager;
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if player needs language setup
        String playerId = player.getUniqueId().toString();
        String currentLang = languageManager.getPlayerLanguageOrDefault(playerId);
        
        if (currentLang.equals(languageManager.getDefaultLanguage())) {
            // Offer language selection on first join
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.sendMessage(ColorHelper.parseComponent(
                            "&aWelcome! Please select your preferred language:"));
                        new LanguageSelectionGUI(plugin, player).open();
                    }
                }
            }.runTaskLater(plugin, 40L); // 2 seconds delay
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Cleanup any open GUIs for the player
        // Implementation depends on your GUI management system
    }
}
```

---

*Next: Learn about [[Player Language Storage]] for persistent language preferences.*