package xyz.wtje.mongoconfigs.paper.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import xyz.wtje.mongoconfigs.paper.gui.LanguageSelectionGUI;

public class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof LanguageSelectionGUI gui) {
            gui.onInventoryClick(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof LanguageSelectionGUI gui) {
            gui.onClose(event);
        }
    }
}

