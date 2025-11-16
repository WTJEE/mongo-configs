package xyz.wtje.mongoconfigs.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;
import xyz.wtje.mongoconfigs.velocity.impl.LanguageManagerImpl;

public class MongoConfigsProxyCommand implements SimpleCommand {

    private static final String PERM_RELOAD = "mongoconfigsproxy.reload";

    private final ConfigManagerImpl configManager;
    private final LanguageManagerImpl languageManager;

    public MongoConfigsProxyCommand(ConfigManagerImpl configManager, LanguageManagerImpl languageManager) {
        this.configManager = configManager;
        this.languageManager = languageManager;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERM_RELOAD);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0 || (!args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("reloadall"))) {
            source.sendMessage(Component.text("Usage: /mongoconfigsproxy <reload|reloadall>"));
            return;
        }

        if (!source.hasPermission(PERM_RELOAD)) {
            source.sendMessage(Component.text("You don't have permission (" + PERM_RELOAD + ")"));
            return;
        }

        String action = args[0].equalsIgnoreCase("reloadall") ? "ALL collections" : "MongoConfigs";
        source.sendMessage(Component.text("Reloading " + action + " on proxy..."));
        configManager.reloadAll().thenRun(() -> {
            languageManager.reload();
            source.sendMessage(Component.text("✅ Reload complete."));
        }).exceptionally(ex -> {
            source.sendMessage(Component.text("❌ Reload failed: " + ex.getMessage()));
            return null;
        });
    }
}

