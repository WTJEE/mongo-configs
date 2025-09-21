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
    
    // Performance optimization - cache language items per player language
    private static final Map<String, Map<String, ItemStack>> LANGUAGE_ITEMS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> CACHED_HEADS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<ItemStack>> LOADING_HEADS = new ConcurrentHashMap<>();
    private static final Map<String, String> TEXTURE_URL_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean isPreloading = false;
    
    // Cache for close button to avoid recreating
    private static volatile ItemStack CACHED_CLOSE_BUTTON = null;

    
    /**
     * Check if this GUI is currently open
     * @return true if this GUI is open, false otherwise
     */
    public boolean isOpen() {
        return isOpen;
    }

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
            LOGGER.info("[DEBUG-GUI] getOpenGUI called with null player");
            return null;
        }
        
        LOGGER.info("[DEBUG-GUI] getOpenGUI checking for player: " + player.getName());
        LanguageSelectionGUI gui = OPEN_GUIS.get(player.getUniqueId());
        if (gui == null) {
            LOGGER.info("[DEBUG-GUI] No GUI found in OPEN_GUIS map for player: " + player.getName());
            return null;
        }

        LOGGER.info("[DEBUG-GUI] Found GUI in map for player: " + player.getName() + ", gui.isOpen=" + gui.isOpen);
        try {
            InventoryView view = player.getOpenInventory();
            if (view == null) {
                LOGGER.info("[DEBUG-GUI] Player's open inventory view is null for: " + player.getName());
                OPEN_GUIS.remove(player.getUniqueId(), gui);
                gui.isOpen = false;
                return null;
            }

            Inventory top = view.getTopInventory();
            if (top == null) {
                LOGGER.info("[DEBUG-GUI] Top inventory is null for player: " + player.getName());
                OPEN_GUIS.remove(player.getUniqueId(), gui);
                gui.isOpen = false;
                return null;
            }
            
            LOGGER.info("[DEBUG-GUI] Top inventory holder check: current=" + top.getHolder() + ", expected=" + gui + ", matches=" + (top.getHolder() == gui));
            if (top.getHolder() != gui) {
                LOGGER.info("[DEBUG-GUI] Top inventory holder mismatch for player: " + player.getName());
                OPEN_GUIS.remove(player.getUniqueId(), gui);
                gui.isOpen = false;
                return null;
            }
        } catch (Throwable t) {
            LOGGER.info("[DEBUG-GUI] Exception checking inventory: " + t.getMessage());
            OPEN_GUIS.remove(player.getUniqueId(), gui);
            gui.isOpen = false;
            return null;
        }

        LOGGER.info("[DEBUG-GUI] Returning valid GUI for player: " + player.getName());
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

    /**
     * Simple synchronous method to open the GUI on main thread
     */
    public void open() {
        getPlugin().getLogger().info("[DEBUG-GUI] Opening GUI synchronously for player: " + player.getName());
        
        try {
            // Create inventory
            String title = resolvePlaceholders(config.getGuiTitle(), null);
            int size = safeSize(config.getGuiSize());
            inventory = Bukkit.createInventory(this, size, ColorHelper.parseComponent(title));
            getPlugin().getLogger().info("[DEBUG-GUI] Created inventory with title: " + title + ", size: " + size);
            
            // Add close button
            ItemStack closeButton = createCloseButton();
            inventory.setItem(config.getCloseButtonSlot(), closeButton);
            getPlugin().getLogger().info("[DEBUG-GUI] Added close button at slot: " + config.getCloseButtonSlot());
            
            // Add language items synchronously
            addLanguageItems();
            getPlugin().getLogger().info("[DEBUG-GUI] Added language items");
            
            // Open inventory
            player.openInventory(inventory);
            isOpen = true;
            OPEN_GUIS.put(player.getUniqueId(), this);
            
            getPlugin().getLogger().info("[DEBUG-GUI] GUI opened successfully for player: " + player.getName());
            
        } catch (Exception e) {
            getPlugin().getLogger().severe("[DEBUG-GUI] Failed to open GUI for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§c[ERROR] Failed to open language GUI: " + e.getMessage());
        }
    }
    
    private void addLanguageItems() {
        // Get supported languages synchronously (this might block briefly but it's necessary)
        try {
            String[] supportedLanguages = languageManager.getSupportedLanguages().get(1, java.util.concurrent.TimeUnit.SECONDS);
            String currentLanguage = languageManager.getPlayerLanguage(player.getUniqueId().toString()).get(1, java.util.concurrent.TimeUnit.SECONDS);
            
            // Create cache key for this language combination
            String cacheKey = currentLanguage + "_" + String.join(",", supportedLanguages);
            
            // Check if we have cached items for this combination
            Map<String, ItemStack> cachedItems = LANGUAGE_ITEMS_CACHE.get(cacheKey);
            
            int slot = config.getGuiStartSlot();
            for (String language : supportedLanguages) {
                ItemStack item;
                
                // Use cached item if available, otherwise create new
                if (cachedItems != null && cachedItems.containsKey(language)) {
                    item = cachedItems.get(language).clone();
                } else {
                    item = createLanguageItem(language, language.equals(currentLanguage));
                    
                    // Cache the item
                    if (cachedItems == null) {
                        cachedItems = new ConcurrentHashMap<>();
                        LANGUAGE_ITEMS_CACHE.put(cacheKey, cachedItems);
                    }
                    cachedItems.put(language, item.clone());
                }
                
                inventory.setItem(slot, item);
                slot++;
                
                // Handle row wrapping
                if (slot % 9 == 8) {
                    slot += 2;
                }
            }
        } catch (Exception e) {
            getPlugin().getLogger().warning("[DEBUG-GUI] Failed to load language data: " + e.getMessage());
            // Add a fallback message item
            ItemStack errorItem = new ItemStack(Material.BARRIER);
            ItemMeta meta = errorItem.getItemMeta();
            meta.displayName(Component.text("§cError loading languages"));
            errorItem.setItemMeta(meta);
            inventory.setItem(config.getGuiStartSlot(), errorItem);
        }
    }
    
    private ItemStack createLanguageItem(String language, boolean isSelected) {
        ItemStack item;
        
        // Try to create head with texture
        String texture = config.getLanguageHeadTextures().get(language);
        if (texture != null && CACHED_HEADS.containsKey(language)) {
            item = CACHED_HEADS.get(language).clone();
        } else if (texture != null) {
            item = createHeadWithTexture(language);
            CACHED_HEADS.put(language, item.clone());
        } else {
            // Use fallback material
            Material fallbackMaterial = config.getFallbackMaterials().get(language);
            if (fallbackMaterial == null) {
                fallbackMaterial = getLanguageMaterial(language);
            }
            item = new ItemStack(fallbackMaterial);
        }
        
        // Update item display
        ItemMeta meta = item.getItemMeta();
        
        // Set display name from config
        String displayName = config.getDisplayName(language);
        meta.displayName(ColorHelper.parseComponent(displayName));
        
        // Set lore from config
        List<Component> lore = buildLanguageItemLore(language, isSelected, "en");
        meta.lore(lore);
        
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        // Return cached close button if available
        if (CACHED_CLOSE_BUTTON != null) {
            return CACHED_CLOSE_BUTTON.clone();
        }
        
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
        
        // Cache the close button for future use
        CACHED_CLOSE_BUTTON = closeButton.clone();
        
        return closeButton;
    }

    private ItemStack createLanguageItem(String language, boolean isSelected) {
        ItemStack item;
        
        // Try to create head with texture
        String texture = config.getLanguageHeadTextures().get(language);
        if (texture != null && CACHED_HEADS.containsKey(language)) {
            item = CACHED_HEADS.get(language).clone();
        } else if (texture != null) {
            item = createHeadWithTexture(language);
            CACHED_HEADS.put(language, item.clone());
        } else {
            // Use fallback material
            Material fallbackMaterial = config.getFallbackMaterials().get(language);
            if (fallbackMaterial == null) {
                fallbackMaterial = getLanguageMaterial(language);
            }
            item = new ItemStack(fallbackMaterial);
        }
        
        // Update item display
        ItemMeta meta = item.getItemMeta();
        
        // Set display name from config
        String displayName = config.getDisplayName(language);
        meta.displayName(ColorHelper.parseComponent(displayName));
        
        // Set lore from config
        List<Component> lore = buildLanguageItemLore(language, isSelected, "en");
        meta.lore(lore);
        
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLanguageItemLore(String language, boolean isSelected, String playerLanguage) {
        List<String> configLore = config.getLanguageItemLore(language);
        
        // If no config lore found, create default lore
        if (configLore == null || configLore.isEmpty()) {
            configLore = List.of(
                "&7Language: &e" + language.toUpperCase(),
                "",
                "{selection_status}",
                "",
                "&7Click to select this language"
            );
        }
        
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

    private String translateColors(String text) {
        return ColorHelper.colorize(text);
    }

    private ItemStack createHeadWithTexture(String language) {
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
        LOGGER.info("[DEBUG-GUI] Inventory click detected for player: " + player.getName());

        if (!(event.getWhoClicked() instanceof Player clickingPlayer)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Handle close button by slot for robustness
        if (event.getSlot() == config.getCloseButtonSlot()) {
            LOGGER.info("[DEBUG-GUI] Close button clicked by: " + clickingPlayer.getName());
            clickingPlayer.closeInventory();
            
            // Send close message
            try {
                String currentPlayerLanguage = languageManager.getPlayerLanguage(clickingPlayer.getUniqueId().toString()).get(2, java.util.concurrent.TimeUnit.SECONDS);
                String closeMessage = config.getMessage("gui.closed", currentPlayerLanguage);
                if (closeMessage != null && !closeMessage.isEmpty()) {
                    clickingPlayer.sendMessage(ColorHelper.parseComponent(closeMessage));
                }
            } catch (Exception e) {
                clickingPlayer.sendMessage("§7Language selection closed.");
            }
            return;
        }

        // Handle language selection
        if (isLanguageItem(clickedItem)) {
            LOGGER.info("[DEBUG-GUI] Language item clicked at slot: " + event.getSlot());
            handleLanguageSelection(clickingPlayer, event.getSlot());
        }
    }
    
    private void handleLanguageSelection(Player clickingPlayer, int slot) {
        try {
            // Get languages synchronously with shorter timeout for better UX
            String[] supportedLanguages = languageManager.getSupportedLanguages().get(1, java.util.concurrent.TimeUnit.SECONDS);
            String currentPlayerLanguage = languageManager.getPlayerLanguage(clickingPlayer.getUniqueId().toString()).get(1, java.util.concurrent.TimeUnit.SECONDS);
            
            String selectedLanguage = getLanguageFromSlot(slot, supportedLanguages);
            LOGGER.info("[DEBUG-GUI] Selected language: " + selectedLanguage + " for player: " + clickingPlayer.getName());

            if (selectedLanguage != null) {
                // Check if already selected
                if (selectedLanguage.equals(currentPlayerLanguage)) {
                    String alreadySelectedMessage = config.getMessage("commands.language.already_selected", currentPlayerLanguage);
                    if (alreadySelectedMessage == null || alreadySelectedMessage.isEmpty()) {
                        alreadySelectedMessage = "§eYou already have this language selected!";
                    }
                    clickingPlayer.sendMessage(ColorHelper.parseComponent(alreadySelectedMessage));
                    return;
                }
                
                // Clear cache for this player's language combination
                LANGUAGE_ITEMS_CACHE.entrySet().removeIf(entry -> entry.getKey().startsWith(currentPlayerLanguage + "_"));
                
                // Set language async but handle result on main thread
                languageManager.setPlayerLanguage(clickingPlayer.getUniqueId(), selectedLanguage)
                    .thenAccept(result -> {
                        Bukkit.getScheduler().runTask(getPlugin(), () -> {
                            try {
                                String displayName = config.getDisplayName(selectedLanguage);
                                String successMessage = config.getMessage("commands.language.success", selectedLanguage)
                                    .replace("{language}", displayName);
                                clickingPlayer.sendMessage(ColorHelper.parseComponent(successMessage));
                                
                                // Refresh the GUI efficiently
                                addLanguageItems();
                            } catch (Exception e) {
                                getPlugin().getLogger().warning("Error updating GUI after language change: " + e.getMessage());
                            }
                        });
                    })
                    .exceptionally(throwable -> {
                        Bukkit.getScheduler().runTask(getPlugin(), () -> {
                            getPlugin().getLogger().warning("Failed to update language for " +
                                clickingPlayer.getName() + ": " + throwable.getMessage());
                            String errorMessage = config.getMessage("commands.language.error", currentPlayerLanguage);
                            clickingPlayer.sendMessage(ColorHelper.parseComponent(errorMessage));
                        });
                        return null;
                    });
            }
        } catch (Exception e) {
            LOGGER.warning("[DEBUG-GUI] Error in handleLanguageSelection: " + e.getMessage());
            clickingPlayer.sendMessage("§cError selecting language. Please try again.");
        }
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
        LOGGER.info("[DEBUG-GUI] onClose event triggered for player: " + player.getName());
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
            LOGGER.info("[DEBUG-GUI] Cancelled refresh task for player: " + player.getName());
        }
        countdownSeconds = -1;
        isOpen = false;
        LOGGER.info("[DEBUG-GUI] Set isOpen=false for player: " + player.getName());
        
        // Clear the GUI reference to allow reopening
        OPEN_GUIS.remove(player.getUniqueId());
        LOGGER.info("[DEBUG-GUI] Removed from OPEN_GUIS map for player: " + player.getName());
        
        // Reset inventory reference to allow fresh creation next time
        inventory = null;
        LOGGER.info("[DEBUG-GUI] Reset inventory to null for player: " + player.getName());
        
        // Force a cleanup task to run later to ensure proper removal
        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            LOGGER.info("[DEBUG-GUI] Running delayed cleanup for player: " + player.getName());
            if (OPEN_GUIS.containsKey(player.getUniqueId())) {
                OPEN_GUIS.remove(player.getUniqueId());
                LOGGER.info("[DEBUG-GUI] Removed lingering reference from OPEN_GUIS map for player: " + player.getName());
            }
        }, 5L); // Run 5 ticks later (0.25 seconds)
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

    /**
     * Force clean any existing GUI for the given player
     * @param player Player to clean GUI for
     */
    public static void forceCleanupForPlayer(Player player) {
        if (player == null) {
            return;
        }
        
        LOGGER.info("[DEBUG-GUI] Force cleaning any existing GUI for player: " + player.getName());
        LanguageSelectionGUI existing = OPEN_GUIS.remove(player.getUniqueId());
        if (existing != null) {
            existing.isOpen = false;
            existing.inventory = null;
            if (existing.refreshTask != null) {
                existing.refreshTask.cancel();
                existing.refreshTask = null;
            }
            LOGGER.info("[DEBUG-GUI] Forcibly cleaned existing GUI for player: " + player.getName());
        }
    }
    
    /**
     * Clear all cached GUI data - use during reload
     */
    public static void clearCache() {
        CACHED_HEADS.clear();
        LOADING_HEADS.clear();
        TEXTURE_URL_CACHE.clear();
        LANGUAGE_ITEMS_CACHE.clear();
        OPEN_GUIS.clear();
        CACHED_CLOSE_BUTTON = null;
        isPreloading = false;
        LOGGER.info("[DEBUG-GUI] Cleared all GUI caches including language items cache");
    }
    
    /**
     * Preload GUI elements for better performance
     */
    public static void preloadGUIElements(LanguageManagerImpl languageManager, LanguageConfiguration config) {
        if (isPreloading) {
            return;
        }
        
        isPreloading = true;
        
        CompletableFuture.runAsync(() -> {
            try {
                // Preload head textures
                Map<String, String> headTextures = config.getLanguageHeadTextures();
                for (Map.Entry<String, String> entry : headTextures.entrySet()) {
                    String language = entry.getKey();
                    String texture = entry.getValue();
                    
                    if (!CACHED_HEADS.containsKey(language) && texture != null) {
                        try {
                            ItemStack head = createHeadWithTextureStatic(language, texture, config);
                            CACHED_HEADS.put(language, head);
                            Thread.sleep(10); // Small delay to avoid overwhelming
                        } catch (Exception e) {
                            // Fallback to material
                            Material fallback = config.getFallbackMaterials().get(language);
                            if (fallback != null) {
                                CACHED_HEADS.put(language, new ItemStack(fallback));
                            }
                        }
                    }
                }
                
                LOGGER.info("[DEBUG-GUI] Preloaded " + CACHED_HEADS.size() + " language heads");
            } finally {
                isPreloading = false;
            }
        });
    }
    
    private static ItemStack createHeadWithTextureStatic(String language, String texture, LanguageConfiguration config) {
        try {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();

            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();

            String url = TEXTURE_URL_CACHE.computeIfAbsent(texture, t -> {
                try {
                    String decoded = new String(Base64.getDecoder().decode(t));
                    return decoded.split("\"url\":\"")[1].split("\"")[0];
                } catch (Exception ex) {
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
            Material fallback = config.getFallbackMaterials().get(language);
            return new ItemStack(fallback != null ? fallback : Material.GRAY_WOOL);
        }
    }

    private void preloadHeadsForCurrentLanguages() {
        try {
            String[] languages = languageManager.getSupportedLanguages().get(1, TimeUnit.SECONDS);
            for (String language : languages) {
                if (!CACHED_HEADS.containsKey(language)) {
                    ItemStack head = createHeadWithTexture(language);
                    CACHED_HEADS.put(language, head);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to preload heads: " + e.getMessage());
        }
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

    private String getLanguageFromSlot(int slot, String[] languages) {
        // Calculate based on actual GUI layout
        int startSlot = config.getGuiStartSlot();
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
}

