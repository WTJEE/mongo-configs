package xyz.wtje.example.help.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Objects;
import java.util.function.Consumer;

public final class HelpMenuListener implements Listener {

    private final HelpMenuService menuService;
    private final Consumer<Throwable> errorHandler;

    public HelpMenuListener(HelpMenuService menuService, Consumer<Throwable> errorHandler) {
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!menuService.isHelpInventory(event.getInventory())) {
            return;
        }

        event.setCancelled(true);
        menuService.handleClick(player, event.getInventory(), event.getCurrentItem(), errorHandler);
    }
}
