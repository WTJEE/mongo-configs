package xyz.wtje.mongoconfigs.paper.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import xyz.wtje.mongoconfigs.core.TypedConfigManager;
import xyz.wtje.mongoconfigs.paper.MongoConfigsPlugin;
import xyz.wtje.mongoconfigs.paper.gui.LanguageSelectionGUI;
import xyz.wtje.mongoconfigs.paper.impl.LanguageManagerImpl;

import java.util.concurrent.CompletableFuture;

public class HotReloadCommand implements CommandExecutor {

    private final MongoConfigsPlugin plugin;
    private final TypedConfigManager typedConfigManager;
    private final LanguageManagerImpl languageManager;

    public HotReloadCommand(MongoConfigsPlugin plugin, TypedConfigManager typedConfigManager, LanguageManagerImpl languageManager) {
        this.plugin = plugin;
        this.typedConfigManager = typedConfigManager;
        this.languageManager = languageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mongoconfigs.hotreload")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                sender.sendMessage("§7Hot-reload system is active");
                sender.sendMessage("§7Use Change Streams for real-time updates");
                break;
                
            case "gui":
                reloadGUI(sender);
                break;
                
            case "cache":
                reloadCache(sender);
                break;
                
            case "all":
                reloadAll(sender);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6Hot-Reload Commands:");
        sender.sendMessage("§7/hotreload gui §f- Reload GUI and clear caches");
        sender.sendMessage("§7/hotreload cache §f- Clear all caches");
        sender.sendMessage("§7/hotreload all §f- Reload everything");
        sender.sendMessage("§7/hotreload status §f- Show system status");
    }
    
    private void reloadGUI(CommandSender sender) {
        sender.sendMessage("§e⟳ Reloading GUI caches...");
        
        CompletableFuture.runAsync(() -> {
            // Clear GUI cache
            LanguageSelectionGUI.clearCache();
            
            // Clear language manager cache
            if (languageManager != null) {
                languageManager.clearCache();
            }
            
            // Notify on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§a✔ GUI caches cleared and reloaded!");
                
                if (sender instanceof Player player) {
                    sender.sendMessage("§7Tip: Use /language to open the refreshed GUI");
                }
            });
        });
    }
    
    private void reloadCache(CommandSender sender) {
        sender.sendMessage("§e⟳ Clearing all caches...");
        
        CompletableFuture.runAsync(() -> {
            // Clear config manager cache
            plugin.getConfigManager().invalidateCache();
            
            // Clear language manager cache
            if (languageManager != null) {
                languageManager.clearCache();
            }
            
            // Clear GUI cache
            LanguageSelectionGUI.clearCache();
            
            // Notify on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§a✔ All caches cleared!");
            });
        });
    }
    
    private void reloadAll(CommandSender sender) {
        sender.sendMessage("§e⟳ Reloading everything...");
        
        CompletableFuture.runAsync(() -> {
            // Reload language manager
            if (languageManager != null) {
                languageManager.reload();
            }
            
            // Clear config cache
            plugin.getConfigManager().invalidateCache();
            
            // Clear GUI cache
            LanguageSelectionGUI.clearCache();
            
            // Preload GUI cache again
            if (languageManager != null) {
                LanguageSelectionGUI.preloadCache(languageManager, plugin.getLanguageConfiguration());
            }
            
            // Notify on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§a✔ Full reload complete!");
                sender.sendMessage("§7• Language Manager reloaded");
                sender.sendMessage("§7• All caches cleared");
                sender.sendMessage("§7• GUI preloaded");
            });
        });
    }
}

