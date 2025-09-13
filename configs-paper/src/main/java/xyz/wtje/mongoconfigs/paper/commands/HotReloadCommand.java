package xyz.wtje.mongoconfigs.paper.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import xyz.wtje.mongoconfigs.core.TypedConfigManager;
import xyz.wtje.mongoconfigs.paper.MongoConfigsPlugin;

public class HotReloadCommand implements CommandExecutor {

    private final MongoConfigsPlugin plugin;
    private final TypedConfigManager typedConfigManager;

    public HotReloadCommand(MongoConfigsPlugin plugin, TypedConfigManager typedConfigManager) {
        this.plugin = plugin;
        this.typedConfigManager = typedConfigManager;
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

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6Hot-Reload Commands:");
    sender.sendMessage("§7/hotreload test §f- (Not implemented in this build)");
    sender.sendMessage("§7/hotreload clear §f- (No-op)");
        sender.sendMessage("§7/hotreload status §f- Show system status");
    }
}
