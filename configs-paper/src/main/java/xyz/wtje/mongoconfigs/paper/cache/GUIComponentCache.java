package xyz.wtje.mongoconfigs.paper.cache;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.wtje.mongoconfigs.paper.util.ColorHelper;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Cache for pre-built GUI components to minimize main thread work
 */
public class GUIComponentCache {
    private static final ConcurrentHashMap<String, ItemStack> ITEM_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Component> COMPONENT_CACHE = new ConcurrentHashMap<>();
    private static final Executor ASYNC_EXECUTOR = ForkJoinPool.commonPool();
    
    /**
     * Get or create cached ItemStack asynchronously
     */
    public static CompletableFuture<ItemStack> getCachedItemAsync(String key, ItemStackBuilder builder) {
        ItemStack cached = ITEM_CACHE.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached.clone());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            ItemStack item = builder.build();
            ITEM_CACHE.put(key, item.clone());
            return item;
        }, ASYNC_EXECUTOR);
    }
    
    /**
     * Get or create cached Component asynchronously
     */
    public static CompletableFuture<Component> getCachedComponentAsync(String key, ComponentBuilder builder) {
        Component cached = COMPONENT_CACHE.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            Component component = builder.build();
            COMPONENT_CACHE.put(key, component);
            return component;
        }, ASYNC_EXECUTOR);
    }
    
    /**
     * Pre-warm cache with common items
     */
    public static void preWarmCache() {
        CompletableFuture.runAsync(() -> {
            // Pre-build common materials
            for (Material material : Material.values()) {
                if (material.name().contains("WOOL") || material == Material.PLAYER_HEAD || material == Material.BARRIER) {
                    String key = "basic_item_" + material.name();
                    if (!ITEM_CACHE.containsKey(key)) {
                        ItemStack item = new ItemStack(material);
                        ITEM_CACHE.put(key, item);
                    }
                }
            }
            
            // Pre-build common components
            String[] commonColors = {"§c", "§a", "§e", "§b", "§f", "§7", "§6"};
            String[] commonTexts = {"Close", "Select", "Back", "Next", "Confirm", "Cancel"};
            
            for (String color : commonColors) {
                for (String text : commonTexts) {
                    String key = "component_" + color + "_" + text;
                    if (!COMPONENT_CACHE.containsKey(key)) {
                        Component component = ColorHelper.parseComponent(color + text);
                        COMPONENT_CACHE.put(key, component);
                    }
                }
            }
        }, ASYNC_EXECUTOR);
    }
    
    /**
     * Invalidate specific cache entry
     */
    public static void invalidate(String key) {
        ITEM_CACHE.remove(key);
        COMPONENT_CACHE.remove(key);
    }
    
    /**
     * Clear all caches
     */
    public static void clearAll() {
        ITEM_CACHE.clear();
        COMPONENT_CACHE.clear();
    }
    
    /**
     * Get cache statistics
     */
    public static String getCacheStats() {
        return "GUI Cache - Items: " + ITEM_CACHE.size() + ", Components: " + COMPONENT_CACHE.size();
    }
    
    @FunctionalInterface
    public interface ItemStackBuilder {
        ItemStack build();
    }
    
    @FunctionalInterface
    public interface ComponentBuilder {
        Component build();
    }
    
    /**
     * Builder for quick ItemStack creation
     */
    public static class QuickItemBuilder {
        private Material material;
        private String displayName;
        private List<String> lore;
        
        public QuickItemBuilder material(Material material) {
            this.material = material;
            return this;
        }
        
        public QuickItemBuilder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }
        
        public QuickItemBuilder lore(List<String> lore) {
            this.lore = lore;
            return this;
        }
        
        public ItemStack build() {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            
            if (displayName != null) {
                meta.displayName(ColorHelper.parseComponent(displayName));
            }
            
            if (lore != null) {
                List<Component> componentLore = lore.stream()
                    .map(ColorHelper::parseComponent)
                    .toList();
                meta.lore(componentLore);
            }
            
            item.setItemMeta(meta);
            return item;
        }
    }
}