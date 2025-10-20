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

import java.net.URL;
import java.util.Arrays;
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
    
    
    private static final Map<UUID, LanguageSelectionGUI> OPEN_GUIS = new ConcurrentHashMap<>();
    private static final int[] CORNER_SLOTS = {0, 8, 36, 44};
    private static final int[] EDGE_SLOTS = {1, 7, 9, 17, 27, 35, 37, 43};
    private static final int[] INNER_SLOTS = {2, 3, 5, 6, 38, 39, 41, 42};
    private static final int[] LANGUAGE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int PREVIOUS_PAGE_SLOT = 18;
    private static final int NEXT_PAGE_SLOT = 26;
    private static volatile boolean GLOBAL_DEBUG_LOGGING = false;
    private static volatile boolean GLOBAL_VERBOSE_LOGGING = false;

    private final LanguageManagerImpl languageManager;
    private final LanguageConfiguration config;
    private volatile Inventory inventory; 
    private final Player player;
    private volatile boolean isOpen = false;
    private final boolean debugLogging;
    private final boolean verboseLogging;
    private int currentPage = 0;
    private java.util.List<String> availableLanguages = java.util.List.of();
    
    
    private static final Map<String, Map<String, ItemStack>> LANGUAGE_ITEMS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> CACHED_HEADS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<ItemStack>> LOADING_HEADS = new ConcurrentHashMap<>();
    private static final Map<String, String> TEXTURE_URL_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean isPreloading = false;
    
    
    private static volatile ItemStack CACHED_CLOSE_BUTTON = null;

    
    
    public boolean isOpen() {
        return isOpen;
    }

    
    private BukkitTask refreshTask;
    private volatile int countdownSeconds = -1;

    public LanguageSelectionGUI(Player player, LanguageManagerImpl languageManager, LanguageConfiguration config) {
        this.player = player;
        this.languageManager = languageManager;
        this.config = config;
        this.debugLogging = languageManager.isDebugLoggingEnabled();
        this.verboseLogging = languageManager.isVerboseLoggingEnabled();
        this.currentPage = 0;
        GLOBAL_DEBUG_LOGGING = this.debugLogging;
        GLOBAL_VERBOSE_LOGGING = this.verboseLogging;

        this.inventory = null;
    }
    
    
    public static LanguageSelectionGUI getOpenGUI(Player player) {
        if (player == null) {
            debugLogStatic("getOpenGUI called with null player");
            return null;
        }
        
        debugLogStatic("getOpenGUI checking for player: " + player.getName());
        LanguageSelectionGUI gui = OPEN_GUIS.get(player.getUniqueId());
        if (gui == null) {
            debugLogStatic("No GUI found in OPEN_GUIS map for player: " + player.getName());
            return null;
        }

        debugLogStatic("Found GUI in map for player: " + player.getName() + ", gui.isOpen=" + gui.isOpen);
        try {
            InventoryView view = player.getOpenInventory();
            if (view == null) {
                debugLogStatic("Player's open inventory view is null for: " + player.getName());
                OPEN_GUIS.remove(player.getUniqueId(), gui);
                gui.isOpen = false;
                return null;
            }

            Inventory top = view.getTopInventory();
            if (top == null) {
                debugLogStatic("Top inventory is null for player: " + player.getName());
                OPEN_GUIS.remove(player.getUniqueId(), gui);
                gui.isOpen = false;
                return null;
            }
            
            debugLogStatic("Top inventory holder check: current=" + top.getHolder() + ", expected=" + gui + ", matches=" + (top.getHolder() == gui));
            if (top.getHolder() != gui) {
                debugLogStatic("Top inventory holder mismatch for player: " + player.getName());
                OPEN_GUIS.remove(player.getUniqueId(), gui);
                gui.isOpen = false;
                return null;
            }
        } catch (Throwable t) {
            debugLogStatic("Exception checking inventory: " + t.getMessage());
            OPEN_GUIS.remove(player.getUniqueId(), gui);
            gui.isOpen = false;
            return null;
        }

        debugLogStatic("Returning valid GUI for player: " + player.getName());
        return gui;
    }

    
    private int safeSize(int requested) {
        int size = requested <= 0 ? 9 : requested;
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        
        if (size % 9 != 0) size = ((size / 9) + 1) * 9;
        return size;
    }

    private int clampSlot(int slot, int invSize) {
        if (slot < 0) return 0;
        if (slot >= invSize) return invSize - 1;
        return slot;
    }

    
    private String resolvePlaceholders(String text, String currentLanguage) {
        if (text == null) return null;
        String out = text
            .replace("{player}", player.getName())
            .replace("{displayname}", player.getDisplayName())
            .replace("{uuid}", player.getUniqueId().toString())
            .replace("{lang}", currentLanguage == null ? "" : currentLanguage)
            .replace("{countdown}", countdownSeconds >= 0 ? String.valueOf(countdownSeconds) : "");
        return out;
    }

    private boolean containsDynamicTokens(String text) {
        if (text == null) return false;
        return text.contains("{countdown}");
    }

    
    public void open() {
        debugLog("Opening GUI synchronously for player: " + player.getName());
        
        try {
            
            String title = resolvePlaceholders(config.getGuiTitle(), null);
            int size = safeSize(config.getGuiSize());
            inventory = Bukkit.createInventory(this, size, ColorHelper.parseComponent(title));
            debugLog("Created inventory with title: " + title + ", size: " + size);
            
            
            ItemStack closeButton = createCloseButton();
            inventory.setItem(config.getCloseButtonSlot(), closeButton);
            debugLog("Added close button at slot: " + config.getCloseButtonSlot());
            
            
            addLanguageItems();
            debugLog("Added language items");
            
            
            player.openInventory(inventory);
            isOpen = true;
            OPEN_GUIS.put(player.getUniqueId(), this);
            
            debugLog("GUI opened successfully for player: " + player.getName());
            
        } catch (Exception e) {
            getPlugin().getLogger().severe("Failed to open GUI for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§c[ERROR] Failed to open language GUI: " + e.getMessage());
        }
    }
    
    private void addLanguageItems() {
        refreshAsync();
    }

    
    private ItemStack createCloseButton() {
        
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
        
        
        CACHED_CLOSE_BUTTON = closeButton.clone();
        
        return closeButton;
    }

    private ItemStack createLanguageItem(String language, boolean isSelected) {
        ItemStack item;
        
        
        String texture = config.getLanguageHeadTextures().get(language);
        if (texture != null && CACHED_HEADS.containsKey(language)) {
            item = CACHED_HEADS.get(language).clone();
        } else if (texture != null) {
            item = createHeadWithTexture(language);
            CACHED_HEADS.put(language, item.clone());
        } else {
            
            Material fallbackMaterial = config.getFallbackMaterials().get(language);
            if (fallbackMaterial == null) {
                fallbackMaterial = getLanguageMaterial(language);
            }
            item = new ItemStack(fallbackMaterial);
        }
        
        
        ItemMeta meta = item.getItemMeta();
        
        
        String displayName = config.getDisplayName(language);
        meta.displayName(ColorHelper.parseComponent(displayName));
        
        
        List<Component> lore = buildLanguageItemLore(language, isSelected, "en");
        meta.lore(lore);
        
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLanguageItemLore(String language, boolean isSelected, String playerLanguage) {
        List<String> configLore = config.getLanguageItemLore(language);
        
        
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






    private ItemStack buildLanguageItemCompleteAsync(String language, boolean isSelected, String playerLanguage) {
        return createLanguageItem(language, isSelected);
    }

    private CompletableFuture<Map<Integer, ItemStack>> buildInventoryAsync() {
        return languageManager.getSupportedLanguages().thenCombine(
            languageManager.getPlayerLanguage(player.getUniqueId().toString()),
            (supportedLanguages, currentLanguage) -> {
                Map<Integer, ItemStack> itemsToSet = new HashMap<>();
                java.util.List<String> languages = java.util.Arrays.asList(supportedLanguages);
                availableLanguages = languages;

                int totalPages = Math.max(1, (int) Math.ceil((double) languages.size() / LANGUAGE_SLOTS.length));
                if (currentPage >= totalPages) {
                    currentPage = totalPages - 1;
                }

                addFillerPanes(itemsToSet);
                addLanguageItems(itemsToSet, languages, currentLanguage);
                addNavigationButtons(itemsToSet, totalPages);
                itemsToSet.put(config.getCloseButtonSlot(), createCloseButton());
                return itemsToSet;
            });
    }

    private void addFillerPanes(Map<Integer, ItemStack> itemsToSet) {
        Material cornerMaterial = config.getCornerPaneMaterial();
        Material edgeMaterial = config.getEdgePaneMaterial();
        Material innerMaterial = config.getInnerPaneMaterial();

        for (int slot : CORNER_SLOTS) {
            itemsToSet.put(slot, createPaneItem(cornerMaterial));
        }
        for (int slot : EDGE_SLOTS) {
            itemsToSet.put(slot, createPaneItem(edgeMaterial));
        }
        for (int slot : INNER_SLOTS) {
            itemsToSet.put(slot, createPaneItem(innerMaterial));
        }
    }

    private void addLanguageItems(Map<Integer, ItemStack> itemsToSet, java.util.List<String> languages, String currentLanguage) {
        int startIndex = currentPage * LANGUAGE_SLOTS.length;
        for (int i = 0; i < LANGUAGE_SLOTS.length; i++) {
            int slot = LANGUAGE_SLOTS[i];
            int languageIndex = startIndex + i;
            if (languageIndex < languages.size()) {
                String language = languages.get(languageIndex);
                itemsToSet.put(slot, createLanguageItem(language, language.equals(currentLanguage)));
            } else {
                itemsToSet.put(slot, new ItemStack(Material.AIR));
            }
        }
    }

    private void addNavigationButtons(Map<Integer, ItemStack> itemsToSet, int totalPages) {
        if (currentPage > 0) {
            itemsToSet.put(PREVIOUS_PAGE_SLOT, createNavigationButton(false));
        } else {
            itemsToSet.put(PREVIOUS_PAGE_SLOT, createPaneItem(config.getInnerPaneMaterial()));
        }

        if (currentPage < totalPages - 1) {
            itemsToSet.put(NEXT_PAGE_SLOT, createNavigationButton(true));
        } else {
            itemsToSet.put(NEXT_PAGE_SLOT, createPaneItem(config.getInnerPaneMaterial()));
        }
    }

    private ItemStack createPaneItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        meta.setLore(java.util.List.of());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavigationButton(boolean next) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorHelper.parseComponent(next ? "&aNext Page" : "&aPrevious Page"));
        meta.lore(java.util.List.of(ColorHelper.parseComponent("&7Click to view " + (next ? "next" : "previous") + " languages")));
        item.setItemMeta(meta);
        return item;
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

    private boolean isLanguageSlot(int slot) {
        for (int languageSlot : LANGUAGE_SLOTS) {
            if (languageSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNextPage() {
        return (currentPage + 1) * LANGUAGE_SLOTS.length < availableLanguages.size();
    }

    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) {
            return;
        }

        event.setCancelled(true);
        debugLog("Inventory click detected for player: " + player.getName());

        if (!(event.getWhoClicked() instanceof Player clickingPlayer)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        
        int slot = event.getSlot();

        if (slot == config.getCloseButtonSlot()) {
            debugLog("Close button clicked by: " + clickingPlayer.getName());
            clickingPlayer.closeInventory();
            
            
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

        if (slot == PREVIOUS_PAGE_SLOT) {
            if (currentPage > 0) {
                currentPage--;
                debugLog("Navigated to previous page: " + currentPage);
                refreshAsync();
            }
            return;
        }

        if (slot == NEXT_PAGE_SLOT) {
            if (hasNextPage()) {
                currentPage++;
                debugLog("Navigated to next page: " + currentPage);
                refreshAsync();
            }
            return;
        }

        if (isLanguageSlot(slot)) {
            debugLog("Language item clicked at slot: " + slot);
            handleLanguageSelection(clickingPlayer, slot);
        }
    }
    
    private void handleLanguageSelection(Player clickingPlayer, int slot) {
        try {
            java.util.List<String> languages = availableLanguages;
            if (languages.isEmpty()) {
                languages = java.util.Arrays.asList(
                    languageManager.getSupportedLanguages().get(1, java.util.concurrent.TimeUnit.SECONDS)
                );
            }
            String currentPlayerLanguage = languageManager.getPlayerLanguage(clickingPlayer.getUniqueId().toString()).get(1, java.util.concurrent.TimeUnit.SECONDS);

            String selectedLanguage = getLanguageFromSlot(slot, languages);
            debugLog("Selected language: " + selectedLanguage + " for player: " + clickingPlayer.getName());

            if (selectedLanguage != null) {
                if (selectedLanguage.equals(currentPlayerLanguage)) {
                    String alreadySelectedMessage = config.getMessage("commands.language.already_selected", currentPlayerLanguage);
                    if (alreadySelectedMessage == null || alreadySelectedMessage.isEmpty()) {
                        alreadySelectedMessage = "??eYou already have this language selected!";
                    }
                    clickingPlayer.sendMessage(ColorHelper.parseComponent(alreadySelectedMessage));
                    return;
                }

                LANGUAGE_ITEMS_CACHE.entrySet().removeIf(entry -> entry.getKey().startsWith(currentPlayerLanguage + "_"));

                languageManager.setPlayerLanguage(clickingPlayer.getUniqueId(), selectedLanguage)
                    .thenAccept(result -> Bukkit.getScheduler().runTask(getPlugin(), () -> {
                        try {
                            String displayName = config.getDisplayName(selectedLanguage);
                            String successMessage = config.getMessage("commands.language.success", selectedLanguage)
                                .replace("{language}", displayName);
                            clickingPlayer.sendMessage(ColorHelper.parseComponent(successMessage));
                            refreshAsync();
                        } catch (Exception e) {
                            getPlugin().getLogger().warning("Error updating GUI after language change: " + e.getMessage());
                        }
                    }))
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
            LOGGER.warning("Error in handleLanguageSelection: " + e.getMessage());
            clickingPlayer.sendMessage("??cError selecting language. Please try again.");
        }
    }


    private void refreshInventoryAsync(Player player) {
        CompletableFuture.runAsync(() -> {
            languageManager.getSupportedLanguages().thenCombine(
                languageManager.getPlayerLanguage(player.getUniqueId().toString()),
                (supportedLanguages, currentLanguage) -> {
                    
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
        debugLog("onClose event triggered for player: " + player.getName());
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
            debugLog("Cancelled refresh task for player: " + player.getName());
        }
        countdownSeconds = -1;
        isOpen = false;
        debugLog("Set isOpen=false for player: " + player.getName());
        
        
        OPEN_GUIS.remove(player.getUniqueId());
        debugLog("Removed from OPEN_GUIS map for player: " + player.getName());
        
        
        inventory = null;
        debugLog("Reset inventory to null for player: " + player.getName());
        
        
        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            debugLog("Running delayed cleanup for player: " + player.getName());
            if (OPEN_GUIS.containsKey(player.getUniqueId())) {
                OPEN_GUIS.remove(player.getUniqueId());
                debugLog("Removed lingering reference from OPEN_GUIS map for player: " + player.getName());
            }
        }, 5L); 
    }

    private String getLanguageFromSlot(int slot, java.util.List<String> languages) {
        for (int i = 0; i < LANGUAGE_SLOTS.length; i++) {
            if (LANGUAGE_SLOTS[i] == slot) {
                int index = currentPage * LANGUAGE_SLOTS.length + i;
                if (index >= 0 && index < languages.size()) {
                    return languages.get(index);
                }
            }
        }
        return null;
    }


    @Override
    public Inventory getInventory() {
        
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

                    
                    Bukkit.getScheduler().runTask(getPlugin(), () -> {
                        
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

    
    public static void forceCleanupForPlayer(Player player) {
        if (player == null) {
            return;
        }
        
        debugLogStatic("Force cleaning any existing GUI for player: " + player.getName());
        LanguageSelectionGUI existing = OPEN_GUIS.remove(player.getUniqueId());
        if (existing != null) {
            existing.isOpen = false;
            existing.inventory = null;
            if (existing.refreshTask != null) {
                existing.refreshTask.cancel();
                existing.refreshTask = null;
            }
            debugLogStatic("Forcibly cleaned existing GUI for player: " + player.getName());
        }
    }
    
    
    public static void clearCache() {
        CACHED_HEADS.clear();
        LOADING_HEADS.clear();
        TEXTURE_URL_CACHE.clear();
        LANGUAGE_ITEMS_CACHE.clear();
        OPEN_GUIS.clear();
        CACHED_CLOSE_BUTTON = null;
        isPreloading = false;
        GLOBAL_DEBUG_LOGGING = false;
        GLOBAL_VERBOSE_LOGGING = false;
        debugLogStatic("Cleared all GUI caches including language items cache");
    }

    
    
    public static void preloadGUIElements(LanguageManagerImpl languageManager, LanguageConfiguration config) {
        if (isPreloading) {
            return;
        }
        
        isPreloading = true;
        
        CompletableFuture.runAsync(() -> {
            try {
                
                Map<String, String> headTextures = config.getLanguageHeadTextures();
                for (Map.Entry<String, String> entry : headTextures.entrySet()) {
                    String language = entry.getKey();
                    String texture = entry.getValue();
                    
                    if (!CACHED_HEADS.containsKey(language) && texture != null) {
                        try {
                            ItemStack head = createHeadWithTextureStatic(language, texture, config);
                            CACHED_HEADS.put(language, head);
                            Thread.sleep(10); 
                        } catch (Exception e) {
                            
                            Material fallback = config.getFallbackMaterials().get(language);
                            if (fallback != null) {
                                CACHED_HEADS.put(language, new ItemStack(fallback));
                            }
                        }
                    }
                }
                
                debugLogStatic("Preloaded " + CACHED_HEADS.size() + " language heads");
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

    private void debugLog(String message) {
        if (debugLogging) {
            LOGGER.info(message);
        }
    }

    private void verboseLog(String message) {
        if (debugLogging || verboseLogging) {
            LOGGER.info(message);
        }
    }

    private static void debugLogStatic(String message) {
        if (GLOBAL_DEBUG_LOGGING) {
            LOGGER.info(message);
        }
    }

    private static void verboseLogStatic(String message) {
        if (GLOBAL_VERBOSE_LOGGING || GLOBAL_DEBUG_LOGGING) {
            LOGGER.info(message);
        }
    }
}














