package xyz.wtje.mongoconfigs.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import xyz.wtje.mongoconfigs.velocity.impl.LanguageManagerImpl;
import xyz.wtje.mongoconfigs.velocity.util.ColorHelper;

import java.util.concurrent.CompletableFuture;

public class LanguageCommand implements SimpleCommand {

    private final LanguageManagerImpl languageManager;

    public LanguageCommand(LanguageManagerImpl languageManager) { this.languageManager = languageManager; }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!(source instanceof Player player)) {
            source.sendMessage(ColorHelper.parseComponent("&cThis command can only be used by players."));
            return;
        }

        if (args.length == 0) {
            // No GUI on proxy: show usage and available languages
            sendAvailableLanguages(player);
            return;
        }

        String requested = args[0].toLowerCase();
        languageManager.getSupportedLanguages().thenAccept(supported -> {
            boolean ok = java.util.Arrays.asList(supported).contains(requested);
            if (!ok) {
                player.sendMessage(ColorHelper.parseComponent("&cUnsupported language: &f{language}".replace("{language}", requested)));
                sendAvailableLanguages(player);
                return;
            }
            languageManager.setPlayerLanguage(player.getUniqueId(), requested)
                .thenCompose(v -> languageManager.getLanguageDisplayName(requested))
                .thenAccept(display -> {
                    String msg = "&aLanguage changed to: &f{language}&a!".replace("{language}", display);
                    player.sendMessage(ColorHelper.parseComponent(msg));
                })
                .exceptionally(ex -> {
                    player.sendMessage(ColorHelper.parseComponent("&cError changing language. Please try again."));
                    return null;
                });
        });
    }

    private void sendAvailableLanguages(Player player) {
        player.sendMessage(ColorHelper.parseComponent("&7Available languages:"));
        CompletableFuture.supplyAsync(() -> java.util.Arrays.asList(languageManager.getSupportedLanguages().join()))
            .thenAccept(list -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    String lang = list.get(i);
                    String display = languageManager.getLanguageDisplayName(lang).join();
                    String fmt = "&a{code} &7({name})".replace("{code}", lang).replace("{name}", display);
                    sb.append(fmt);
                    if (i < list.size() - 1) sb.append("&7, ");
                }
                player.sendMessage(ColorHelper.parseComponent(sb.toString()));
                player.sendMessage(ColorHelper.parseComponent("&7Usage: &f/language [language]"));
            });
    }
}
