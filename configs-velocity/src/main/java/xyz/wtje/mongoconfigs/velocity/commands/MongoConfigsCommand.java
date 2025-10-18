package xyz.wtje.mongoconfigs.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;
import xyz.wtje.mongoconfigs.velocity.impl.LanguageManagerImpl;
import xyz.wtje.mongoconfigs.velocity.util.ColorHelper;

public class MongoConfigsCommand implements SimpleCommand {

    private final ConfigManagerImpl configManager;
    private final LanguageManagerImpl languageManager;

    public MongoConfigsCommand(ConfigManagerImpl configManager, LanguageManagerImpl languageManager) {
        this.configManager = configManager;
        this.languageManager = languageManager;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        // Velocity doesn't have per-command perms by default; allow all sources
        return true;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            source.sendMessage(Component.text("Usage: /mongoconfigs reload"));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            source.sendMessage(Component.text("Reloading MongoConfigs..."));
            configManager.reloadAll().thenRun(() -> {
                languageManager.reload();
                source.sendMessage(Component.text("Reload complete."));
            }).exceptionally(ex -> {
                source.sendMessage(Component.text("Reload failed: " + ex.getMessage()));
                return null;
            });
            return;
        }

        source.sendMessage(ColorHelper.parseComponent("&cUnknown subcommand."));
    }
}

