package xyz.wtje.mongoconfigs.paper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitTask;
import xyz.wtje.mongoconfigs.paper.config.LanguageConfiguration;
import xyz.wtje.mongoconfigs.paper.impl.LanguageManagerImpl;
import xyz.wtje.mongoconfigs.paper.util.ColorHelper;
import me.clip.placeholderapi.PlaceholderAPI;

import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class LanguageSelectionGUI implements InventoryHolder {
    private static final Logger LOGGER = Logger.getLogger(LanguageSelectionGUI.class.getName());
    
    // Track open GUIs per player
    private static final Map<UUID, LanguageSelectionGUI> OPEN_GUIS = new ConcurrentHashMap<>();

    private final LanguageManagerImpl languageManager;
    private final LanguageConfiguration config;
    private volatile Inventory inventory; // volatile for thread-safe lazy init
    private final Player player;
    private volatile boolean isOpen = false;
    
    /**
     * Check if this GUI is currently open
     * @return true if this GUI is open, false otherwise
     */
    public boolean isOpen() {
        return isOpen;
    }

    private static final Map<String, ItemStack> CACHED_HEADS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<ItemStack>> LOADING_HEADS = new ConcurrentHashMap<>();
    private static final Map<String, String> TEXTURE_URL_CACHE = new ConcurrentHashMap<>(); // Cache decoded URLs
    private static volatile boolean isPreloading = false;

    // Auto-refresh
    private BukkitTask refreshTask;
    private volatile int countdownSeconds = -1;

    public LanguageSelectionGUI(Player player, LanguageManagerImpl languageManager, LanguageConfiguration config) {
        this.player = player;
        this.languageManager = languageManager;
        this.config = config;

        // Create inventory only when needed (on main thread)
        this.inventory = null;
    }
    
    // Get existing GUI for player or null
    public static LanguageSelectionGUI getOpenGUI(Player player) {
        if (player == null) {
            return null;
        }
        
        LanguageSelectionGUI gui = OPEN_GUIS.get(player.getUniqueId());
        if (gui == null) {
            return null;
        }

        try {
            InventoryView view = player.getOpenInventory();
            if (view == null) {
                OPEN_GUIS.remove(player.getUniqueId(), gui);
                gui.isOpen = false;
                return null;
            }

            Inventory top = view.getTopInventory();
            if (top == null || top.getHolder() != gui) {
                OPEN_GUIS.remove(player.getUniqueId(), gui);
                gui.isOpen = false;
                return null;
            }
        } catch (Throwable ignored) {
            OPEN_GUIS.remove(player.getUniqueId(), gui);
            gui.isOpen = false;
            return null;
        }

        return gui;
    }

    // --- Safety helpers -----------------------------------------------------
    private int safeSize(int requested) {
        int size = requested <= 0 ? 9 : requested;
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        // round up to nearest multiple of 9
        if (size % 9 != 0) size = ((size / 9) + 1) * 9;
        return size;
    }

    private int clampSlot(int slot, int invSize) {
        if (slot < 0) return 0;
        if (slot >= invSize) return invSize - 1;
        return slot;
    }

    // Resolve internal and PlaceholderAPI placeholders
    private String resolvePlaceholders(String text, String currentLanguage) {
        if (text == null) return null;
        String out = text
            .replace("{player}", player.getName())
            .replace("{displayname}", player.getDisplayName())
            .replace("{uuid}", player.getUniqueId().toString())
            .replace("{lang}", currentLanguage == null ? "" : currentLanguage)
            .replace("{countdown}", countdownSeconds >= 0 ? String.valueOf(countdownSeconds) : "");
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                out = PlaceholderAPI.setPlaceholders(player, out);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private boolean containsDynamicTokens(String text) {
        if (text == null) return false;
        return text.contains("{countdown}") || text.contains("%");
    }

    public CompletableFuture<Void> openAsync() {
        // Check if already opening
        if (isOpen && inventory != null) {
            // Just refresh existing GUI
            return refreshAsync();
        }
        
        // Start building items immediately in parallel
        CompletableFuture<Map<Integer, ItemStack>> itemsFuture = buildInventoryAsync();
        
        // If heads not cached, preload them in parallel (don't wait)
        if (CACHED_HEADS.isEmpty()) {
            CompletableFuture.runAsync(() -> preloadHeadsForCurrentLanguages());
        }
        
        return itemsFuture.thenAccept(itemsToSet -> {
            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                try {
                    // Fast inventory creation and opening on main thread
                    if (inventory == null) {
                        String titleResolved = resolvePlaceholders(config.getGuiTitle(), null);
                        int size = safeSize(config.getGuiSize());
                        inventory = Bukkit.createInventory(this, size,
                            ColorHelper.parseComponent(titleResolved));
                    } else {
                        inventory.clear(); // Clear existing items for refresh
                    }
                    
                    // Batch set items for performance
                    int invSize = inventory.getSize();
                    itemsToSet.forEach((pos, stack) -> inventory.setItem(clampSlot(pos, invSize), stack));
                    
                    // Open immediately
                    player.openInventory(inventory);
                    isOpen = true;
                    OPEN_GUIS.put(player.getUniqueId(), this);
                    startAutoRefreshIfNeeded();
                } catch (Exception e) {
                    player.sendMessage("§c[ERROR] Failed to open inventory: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                player.sendMessage("§c[ERROR] Failed to build GUI: " + throwable.getMessage());
                throwable.printStackTrace();
            });
            return null;
        });
    }
    
    public void open() {
        openAsync().exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                player.sendMessage("§6[INFO] Using simplified GUI mode");
                openSimpleAsync();
            });
            return null;
        });
    }

    private CompletableFuture<Map<Integer, ItemStack>> buildInventoryAsync() {
        // Build the item set asynchronously; create/open the Inventory on the main thread later
        CompletableFuture<Map<Integer, ItemStack>> base = CompletableFuture.supplyAsync(() -> {
            Map<Integer, ItemStack> items = new HashMap<>();
            ItemStack closeButton = createCloseButton();
            items.put(config.getCloseButtonSlot(), closeButton);
            return items;
        });

        return base.thenCombine(
            languageManager.getSupportedLanguages().thenCombine(
                languageManager.getPlayerLanguage(player.getUniqueId().toString()),
                (supportedLanguages, currentLanguage) -> {
                    // Build language items async
                    Map<Integer, ItemStack> langItems = new HashMap<>();
                    int slot = config.getGuiStartSlot();

                    for (String language : supportedLanguages) {
                        ItemStack item = buildLanguageItemCompleteAsync(language, language.equals(currentLanguage), currentLanguage);
                        langItems.put(slot, item);
                        slot++;

                        if (slot % 9 == 8) {
                            slot += 2;
                        }
                    }
                    return langItems;
                }
            ),
            (baseItems, langItems) -> {
                baseItems.putAll(langItems);
                return baseItems;
            }
        );
    }

    private ItemStack createCloseButton() {
        Material material;
        try {
            material = Material.valueOf(config.getCloseButtonMaterial());
        } catch (IllegalArgumentException e) {
            material = Material.BARRIER;
        }

        ItemStack closeButton = new ItemStack(material);
        ItemMeta closeMeta = closeButton.getItemMeta();
        String resolvedName = resolvePlaceholders(config.getCloseButtonName(), null);
        closeMeta.displayName(ColorHelper.parseComponent(resolvedName));

        List<Component> lore = config.getCloseButtonLore().stream()
            .map(line -> ColorHelper.parseComponent(resolvePlaceholders(line, null)))
            .toList();
        closeMeta.lore(lore);

        closeButton.setItemMeta(closeMeta);
        return closeButton;
    }

    private String translateColors(String text) {
        return ColorHelper.colorize(text);
    }

    private ItemStack buildLanguageItemCompleteAsync(String language, boolean isSelected, String playerLanguage) {
        ItemStack cachedHead = CACHED_HEADS.get(language);
        ItemStack baseItem;

        if (cachedHead != null) {
            baseItem = cachedHead.clone();
        } else {
            // Create head async and cache it
            baseItem = createHeadWithTextureAsync(language);
            CACHED_HEADS.put(language, baseItem.clone());
        }

        return updateItemDisplayAsync(baseItem, language, isSelected, playerLanguage);
    }
    
    private ItemStack buildLanguageItemComplete(String language, boolean isSelected, String playerLanguage) {
        return buildLanguageItemCompleteAsync(language, isSelected, playerLanguage);
    }

    private ItemStack createHeadWithTextureAsync(String language) {
        String texture = config.getLanguageHeadTextures().get(language);
        if (texture == null) {
            Material fallbackMaterial = config.getFallbackMaterials().get(language);
            if (fallbackMaterial != null) {
                return new ItemStack(fallbackMaterial);
            }
            return new ItemStack(getLanguageMaterial(language));
        }

        try {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();

            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();

            // Use cached URL if available to avoid Base64 decoding
            String url = TEXTURE_URL_CACHE.computeIfAbsent(texture, t -> {
                try {
                    String decoded = new String(Base64.getDecoder().decode(t));
                    return decoded.split("\"url\":\"")[1].split("\"")[0];
                } catch (Exception e) {
                    LOGGER.warning("Failed to decode texture for " + language + ": " + e.getMessage());
                    return null;
                }
            });
            
            if (url == null) {
                throw new IllegalStateException("Failed to decode texture URL");
            }

            textures.setSkin(new URL(url));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
            item.setItemMeta(meta);

            return item;
        } catch (Exception e) {
            Material fallbackMaterial = config.getFallbackMaterials().get(language);
            if (fallbackMaterial != null) {
                return new ItemStack(fallbackMaterial);
            }
            return new ItemStack(getLanguageMaterial(language));
        }
    }
    
    private ItemStack createHeadWithTexture(String language) {
        return createHeadWithTextureAsync(language);
    }

    private ItemStack updateItemDisplayAsync(ItemStack item, String language, boolean isSelected, String playerLanguage) {
        ItemMeta meta = item.getItemMeta();

        // Use cached display name if available, otherwise get async
        String displayName;
        try {
            displayName = languageManager.getLanguageDisplayName(language).getNow(language);
        } catch (Exception e) {
            displayName = language; // fallback
        }
        
        String resolvedName = resolvePlaceholders(displayName, playerLanguage);
        Component nameComponent = ColorHelper.parseComponent(resolvedName);
        meta.displayName(nameComponent);

        List<Component> lore = buildLanguageItemLoreAsync(language, isSelected, playerLanguage);
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack updateItemDisplay(ItemStack item, String language, boolean isSelected, String playerLanguage) {
        return updateItemDisplayAsync(item, language, isSelected, playerLanguage);
    }

    private List<Component> buildLanguageItemLoreAsync(String language, boolean isSelected, String playerLanguage) {
        List<String> configLore = config.getLanguageItemLore(language);
        String selectedMessage = isSelected ?
            config.getSelectedMessage(playerLanguage) :
            config.getNotSelectedMessage(playerLanguage);

        return configLore.stream()
            .map(line -> {
                String processedLine = line
                    .replace("{selection_status}", selectedMessage);
                processedLine = resolvePlaceholders(processedLine, playerLanguage);
                return ColorHelper.parseComponent(processedLine);
            })
            .toList();
    }
    
    private List<Component> buildLanguageItemLore(String language, boolean isSelected, String playerLanguage) {
        return buildLanguageItemLoreAsync(language, isSelected, playerLanguage);
    }

    private org.bukkit.plugin.Plugin getPlugin() {
        return org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(LanguageSelectionGUI.class);
    }

    private Material getLanguageMaterial(String language) {
        return switch (language) {
            case "en" -> Material.WHITE_WOOL;
            case "pl" -> Material.RED_WOOL;
            case "de" -> Material.YELLOW_WOOL;
            case "fr" -> Material.BLUE_WOOL;
            case "es" -> Material.ORANGE_WOOL;
            default -> Material.GRAY_WOOL;
        };
    }

    private boolean isLanguageItem(ItemStack item) {
        Material type = item.getType();
        return type == Material.PLAYER_HEAD ||
               type == Material.WHITE_WOOL ||
               type == Material.RED_WOOL ||
               type == Material.YELLOW_WOOL ||
               type == Material.BLUE_WOOL ||
               type == Material.ORANGE_WOOL ||
               type == Material.GRAY_WOOL;
    }

    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player clickingPlayer)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Handle close button by slot for robustness
        if (event.getSlot() == config.getCloseButtonSlot()) {
            clickingPlayer.closeInventory();
            
            // Send close message async
            CompletableFuture.runAsync(() -> {
                languageManager.getPlayerLanguage(clickingPlayer.getUniqueId().toString())
                    .thenAccept(currentPlayerLanguage -> {
                        String closeMessage = config.getMessage("gui.closed", currentPlayerLanguage);
                        if (closeMessage != null && !closeMessage.isEmpty()) {
                            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                                clickingPlayer.sendMessage(ColorHelper.parseComponent(closeMessage));
                            });
                        }
                    });
            });
            return;
        }

        // Handle language selection async
        if (isLanguageItem(clickedItem)) {
            handleLanguageSelectionAsync(clickingPlayer, event.getSlot());
        }
    }
    
    private void handleLanguageSelectionAsync(Player clickingPlayer, int slot) {
        CompletableFuture.runAsync(() -> {
            languageManager.getSupportedLanguages().thenCombine(
                languageManager.getPlayerLanguage(clickingPlayer.getUniqueId().toString()),
                (supportedLanguages, currentPlayerLanguage) -> {
                    String selectedLanguage = getLanguageFromSlot(slot, supportedLanguages);

                    if (selectedLanguage != null) {
                        // Check if already selected
                        if (selectedLanguage.equals(currentPlayerLanguage)) {
                            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                                String alreadySelectedMessage = config.getMessage("commands.language.already_selected", currentPlayerLanguage);
                                if (alreadySelectedMessage == null || alreadySelectedMessage.isEmpty()) {
                                    alreadySelectedMessage = "§eYou already have this language selected!";
                                }
                                clickingPlayer.sendMessage(ColorHelper.parseComponent(alreadySelectedMessage));
                            });
                            return null;
                        }
                        
                        languageManager.setPlayerLanguage(clickingPlayer.getUniqueId(), selectedLanguage)
                            .whenCompleteAsync((result, error) -> {
                                Bukkit.getScheduler().runTask(getPlugin(), () -> {
                                    if (error != null) {
                                        getPlugin().getLogger().warning("Failed to update language for " +
                                            clickingPlayer.getName() + ": " + error.getMessage());
                                        String errorMessage = config.getMessage("commands.language.error", currentPlayerLanguage);
                                        clickingPlayer.sendMessage(ColorHelper.parseComponent(errorMessage));
                                    } else {
                                        try {
                                            String displayName = languageManager.getLanguageDisplayName(selectedLanguage).getNow(selectedLanguage);
                                            String successMessage = config.getMessage("commands.language.success", selectedLanguage)
                                                .replace("{language}", displayName);
                                            clickingPlayer.sendMessage(ColorHelper.parseComponent(successMessage));
                                            
                                            // Force refresh the GUI immediately
                                            refreshAsync();
                                        } catch (Exception e) {
                                            getPlugin().getLogger().warning("Error updating GUI after language change: " + e.getMessage());
                                        }
                                    }
                                });
                            });
                    }
                    return null;
                });
        });
    }

    private void refreshInventoryAsync(Player player) {
        CompletableFuture.runAsync(() -> {
            languageManager.getSupportedLanguages().thenCombine(
                languageManager.getPlayerLanguage(player.getUniqueId().toString()),
                (supportedLanguages, currentLanguage) -> {
                    // Build all items async first
                    Map<Integer, ItemStack> itemsToUpdate = new HashMap<>();
                    int slot = config.getGuiStartSlot();

                    for (String language : supportedLanguages) {
                        ItemStack item = buildLanguageItemCompleteAsync(language, language.equals(currentLanguage), currentLanguage);
                        itemsToUpdate.put(slot, item);
                        slot++;

                        if (slot % 9 == 8) {
                            slot += 2;
                        }
                    }
                    
                    // Update inventory on main thread
                    Bukkit.getScheduler().runTask(getPlugin(), () -> {
                        if (inventory != null && player.getOpenInventory().getTopInventory().getHolder() == this) {
                            itemsToUpdate.forEach(inventory::setItem);
                        }
                    });
                    
                    return null;
                });
        });
    }
    
    private void refreshInventory(Player player) {
        refreshInventoryAsync(player);
    }
    
    // New method for easier refresh
    public CompletableFuture<Void> refreshAsync() {
        return buildInventoryAsync().thenAccept(itemsToSet -> {
            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                if (inventory != null && isOpen) {
                    inventory.clear();
                    int invSize = inventory.getSize();
                    itemsToSet.forEach((pos, stack) -> inventory.setItem(clampSlot(pos, invSize), stack));
                }
            });
        });
    }

    private void startAutoRefreshIfNeeded() {
        boolean dynamic = containsDynamicTokens(config.getGuiTitle()) ||
                config.getCloseButtonLore().stream().anyMatch(this::containsDynamicTokens) ||
                containsDynamicTokens(config.getCloseButtonName());
        if (!dynamic) {
            try {
                String current = languageManager.getPlayerLanguage(player.getUniqueId().toString()).getNow("");
                List<String> lore = config.getLanguageItemLore(current);
                dynamic = lore != null && lore.stream().anyMatch(this::containsDynamicTokens);
            } catch (Throwable ignored) {}
        }
        if (!dynamic) return;

        if (config.getGuiTitle().contains("{countdown}")) {
            countdownSeconds = 60;
        }
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            if (countdownSeconds >= 0) countdownSeconds = Math.max(0, countdownSeconds - 1);
            if (player.getOpenInventory().getTopInventory().getHolder() != this) {
                if (refreshTask != null) refreshTask.cancel();
                refreshTask = null;
                countdownSeconds = -1;
                return;
            }
            refreshInventoryAsync(player);
        }, 20L, 20L);
    }

    public void onClose(InventoryCloseEvent event) {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        countdownSeconds = -1;
        isOpen = false;
        // Clear the GUI reference to allow reopening
        OPEN_GUIS.remove(player.getUniqueId());
        // Reset inventory reference to allow fresh creation next time
        inventory = null;
    }

    private String getLanguageFromSlot(int slot, String[] languages) {
        // Calculate based on actual GUI layout
        int startSlot = config.getGuiStartSlot();
        int langIndex = 0;
        int currentSlot = startSlot;
        
        for (String lang : languages) {
            if (currentSlot == slot) {
                return lang;
            }
            currentSlot++;
            // Handle row wrapping
            if (currentSlot % 9 == 8) {
                currentSlot += 2;
            }
            langIndex++;
        }
        return null;
    }

    @Override
    public Inventory getInventory() {
        // Create inventory on-demand if needed (should only happen on main thread)
        if (inventory == null) {
            int size = safeSize(config.getGuiSize());
            inventory = Bukkit.createInventory(this, size,
                ColorHelper.parseComponent(config.getGuiTitle()));
        }
        return inventory;
    }

    public static void preloadCache(LanguageManagerImpl languageManager, LanguageConfiguration config) {
        if (isPreloading || !CACHED_HEADS.isEmpty()) {
            return;
        }

        isPreloading = true;

        languageManager.getSupportedLanguages().thenAccept(languages -> {
            CompletableFuture.runAsync(() -> {
                for (String language : languages) {
                    if (!CACHED_HEADS.containsKey(language)) {
                        String texture = config.getLanguageHeadTextures().get(language);
                        if (texture != null) {
                            try {
                                ItemStack item = new ItemStack(Material.PLAYER_HEAD);
                                SkullMeta meta = (SkullMeta) item.getItemMeta();

                                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                                PlayerTextures textures = profile.getTextures();

                                // Use cached URL to avoid repeated decoding
                                String url = TEXTURE_URL_CACHE.computeIfAbsent(texture, t -> {
                                    try {
                                        String decoded = new String(Base64.getDecoder().decode(t));
                                        return decoded.split("\"url\":\"")[1].split("\"")[0];
                                    } catch (Exception ex) {
                                        return null;
                                    }
                                });
                                
                                if (url == null) {
                                    throw new IllegalStateException("Failed to decode URL");
                                }

                                textures.setSkin(new URL(url));
                                profile.setTextures(textures);
                                meta.setOwnerProfile(profile);
                                item.setItemMeta(meta);

                                CACHED_HEADS.put(language, item);

                                Thread.sleep(50);
                            } catch (Exception e) {
                                Material fallbackMaterial = config.getFallbackMaterials().get(language);
                                if (fallbackMaterial == null) {
                                    fallbackMaterial = getLanguageMaterialStatic(language);
                                }
                                ItemStack fallback = new ItemStack(fallbackMaterial);
                                CACHED_HEADS.put(language, fallback);
                            }
                        } else {
                            Material fallbackMaterial = config.getFallbackMaterials().get(language);
                            if (fallbackMaterial == null) {
                                fallbackMaterial = getLanguageMaterialStatic(language);
                            }
                            ItemStack fallback = new ItemStack(fallbackMaterial);
                            CACHED_HEADS.put(language, fallback);
                        }
                    }
                }

                isPreloading = false;

                Bukkit.getScheduler().runTask(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(LanguageSelectionGUI.class),
                    () -> Bukkit.getLogger().info("Language GUI heads preloaded successfully"));
            });
        });
    }

    private static Material getLanguageMaterialStatic(String language) {
        return switch (language) {
            case "en" -> Material.WHITE_WOOL;
            case "pl" -> Material.RED_WOOL;
            case "de" -> Material.YELLOW_WOOL;
            case "fr" -> Material.BLUE_WOOL;
            case "es" -> Material.ORANGE_WOOL;
            default -> Material.GRAY_WOOL;
        };
    }

    public CompletableFuture<Void> openSimpleAsync() {
        return CompletableFuture.runAsync(() -> {
            languageManager.getSupportedLanguages().thenCombine(
                languageManager.getPlayerLanguage(player.getUniqueId().toString()),
                (supportedLanguages, currentLanguage) -> {
                    // Build items async
                    Map<Integer, ItemStack> itemsToSet = new HashMap<>();
                    int slot = config.getGuiStartSlot();

                    for (String language : supportedLanguages) {
                        ItemStack item = buildLanguageItemCompleteAsync(language, language.equals(currentLanguage), currentLanguage);
                        itemsToSet.put(slot, item);
                        slot++;

                        if (slot % 9 == 8) {
                            slot += 2;
                        }
                    }

                    ItemStack closeButton = createCloseButton();
                    itemsToSet.put(config.getCloseButtonSlot(), closeButton);

                    // Apply to inventory and open on main thread
                    Bukkit.getScheduler().runTask(getPlugin(), () -> {
                        // Create inventory on main thread if not exists
                        if (inventory == null) {
                            String titleResolved = resolvePlaceholders(config.getGuiTitle(), null);
                            int size = safeSize(config.getGuiSize());
                            inventory = Bukkit.createInventory(LanguageSelectionGUI.this, size,
                                ColorHelper.parseComponent(titleResolved));
                        }
                        int invSize = inventory.getSize();
                        itemsToSet.forEach((pos, stack) -> inventory.setItem(clampSlot(pos, invSize), stack));
                        player.openInventory(inventory);
                        isOpen = true;
                        OPEN_GUIS.put(player.getUniqueId(), this);
                        startAutoRefreshIfNeeded();
                    });
                    return null;
                });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                player.sendMessage("§c[ERROR] Failed to open simple GUI: " + throwable.getMessage());
            });
            return null;
        });
    }
    
    public void openSimple() {
        openSimpleAsync();
    }

    public static void clearCache() {
        CACHED_HEADS.clear();
        LOADING_HEADS.clear();
        isPreloading = false;
    }
    
    private void preloadHeadsForCurrentLanguages() {
        try {
            String[] languages = languageManager.getSupportedLanguages().get(1, TimeUnit.SECONDS);
            for (String language : languages) {
                if (!CACHED_HEADS.containsKey(language)) {
                    ItemStack head = createHeadWithTextureAsync(language);
                    CACHED_HEADS.put(language, head);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to preload heads: " + e.getMessage());
        }
    }
}

