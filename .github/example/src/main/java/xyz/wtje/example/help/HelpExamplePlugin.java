package xyz.wtje.example.help;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.wtje.example.help.command.HelpMenuCommand;
import xyz.wtje.example.help.config.HelpMessages;
import xyz.wtje.example.help.gui.HelpMenuListener;
import xyz.wtje.example.help.gui.HelpMenuService;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.LanguageManager;
import xyz.wtje.mongoconfigs.api.Messages;
import xyz.wtje.mongoconfigs.api.MongoConfigsAPI;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class HelpExamplePlugin extends JavaPlugin {

    private ConfigManager configManager;
    private LanguageManager languageManager;
    private CompletableFuture<Messages> messagesFuture;
    private HelpMenuService menuService;

    @Override
    public void onEnable() {
        try {
            this.configManager = MongoConfigsAPI.getConfigManager();
            this.languageManager = MongoConfigsAPI.getLanguageManager();
        } catch (IllegalStateException missing) {
            getLogger().log(Level.SEVERE, "MongoConfigs is not ready. Did you declare a dependency in plugin.yml?", missing);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.messagesFuture = configManager.getOrCreateFromObject(new HelpMessages())
            .whenComplete((messages, throwable) -> {
                if (throwable != null) {
                    getLogger().log(Level.SEVERE, "Failed to load help messages", throwable);
                } else {
                    getLogger().info("Help GUI messages initialised");
                }
            });

        this.menuService = new HelpMenuService(this, languageManager, messagesFuture);
        getServer().getPluginManager().registerEvents(new HelpMenuListener(menuService, this::logAsyncError), this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"helpui".equalsIgnoreCase(command.getName())) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the help UI");
            return true;
        }

        if (menuService == null) {
            sender.sendMessage("Help menu is still loading. Try again in a moment.");
            return true;
        }

        new HelpMenuCommand(menuService, messagesFuture).execute(player, this::logAsyncError);
        return true;
    }

    private void logAsyncError(Throwable throwable) {
        getLogger().log(Level.SEVERE, "Unexpected error in help GUI", throwable);
    }
}
