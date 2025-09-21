package xyz.wtje.mongoconfigs.paper.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.wtje.mongoconfigs.paper.gui.LanguageSelectionGUI;

public class GUIListener implements Listener {
    
    private final JavaPlugin plugin;
    
    public GUIListener(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("[DEBUG] GUIListener constructed with plugin: " + plugin.getName());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof LanguageSelectionGUI gui) {
            plugin.getLogger().info("[DEBUG-GUI-LISTENER] InventoryClickEvent for player: " + 
                (event.getWhoClicked() instanceof Player ? ((Player)event.getWhoClicked()).getName() : "unknown"));
            gui.onInventoryClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        plugin.getLogger().info("[DEBUG-GUI-LISTENER] InventoryCloseEvent triggered for player: " + 
            (event.getPlayer() instanceof Player ? ((Player)event.getPlayer()).getName() : "unknown"));
            
        if (event.getInventory().getHolder() instanceof LanguageSelectionGUI gui) {
            plugin.getLogger().info("[DEBUG-GUI-LISTENER] InventoryCloseEvent for LanguageSelectionGUI, calling onClose");
            gui.onClose(event);
        } else {
            plugin.getLogger().info("[DEBUG-GUI-LISTENER] InventoryCloseEvent for non-GUI inventory: " + 
                event.getInventory().getType() + ", holder: " + 
                (event.getInventory().getHolder() != null ? event.getInventory().getHolder().getClass().getName() : "null"));
        }
    }
}

