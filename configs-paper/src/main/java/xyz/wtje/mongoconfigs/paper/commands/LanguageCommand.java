package xyz.wtje.mongoconfigs.paper.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import xyz.wtje.mongoconfigs.paper.config.LanguageConfiguration;
import xyz.wtje.mongoconfigs.paper.gui.LanguageSelectionGUI;
import xyz.wtje.mongoconfigs.paper.impl.LanguageManagerImpl;
import xyz.wtje.mongoconfigs.paper.util.ColorHelper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LanguageCommand implements CommandExecutor, TabCompleter {

    private final LanguageManagerImpl languageManager;
    private final LanguageConfiguration config;

    public LanguageCommand(LanguageManagerImpl languageManager, LanguageConfiguration config) {
        this.languageManager = languageManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            String playersOnlyMessage = config.getMessage("commands.players-only", "en");
            sender.sendMessage(ColorHelper.parseComponent(playersOnlyMessage));
            return true;
        }

        String playerLanguage = languageManager.getPlayerLanguage(player.getUniqueId().toString());

        if (args.length == 0) {
            openLanguageGUI(player);
            return true;
        }

        String requestedLanguage = args[0].toLowerCase();

        if (!languageManager.isLanguageSupported(requestedLanguage)) {
            String unsupportedMessage = config.getMessage("commands.language.unsupported", playerLanguage)
                .replace("{language}", requestedLanguage);
            player.sendMessage(ColorHelper.parseComponent(unsupportedMessage));
            showAvailableLanguages(player);
            return true;
        }

        languageManager.setPlayerLanguage(player.getUniqueId(), requestedLanguage)
            .whenComplete((result, error) -> {
                if (error != null) {
                    String errorMessage = config.getMessage("commands.language.error", playerLanguage);
                    player.sendMessage(ColorHelper.parseComponent(errorMessage));
                } else {
                    String displayName = languageManager.getLanguageDisplayName(requestedLanguage);
                    String successMessage = config.getMessage("commands.language.success", requestedLanguage)
                        .replace("{language}", displayName);
                    player.sendMessage(ColorHelper.parseComponent(successMessage));
                }
            });

        return true;
    }

    private String translateColors(String text) {
        return ColorHelper.colorize(text);
    }

    private void showLanguageInfo(Player player, String playerId) {
        String playerLanguage = languageManager.getPlayerLanguage(player.getUniqueId().toString());
        String currentLanguage = languageManager.getPlayerLanguage(playerId);
        String displayName = languageManager.getLanguageDisplayName(currentLanguage);

        String currentMessage = config.getMessage("commands.language.current", playerLanguage)
            .replace("{language}", displayName);
    player.sendMessage(ColorHelper.parseComponent(currentMessage));

        showAvailableLanguages(player);
    }

    private void showAvailableLanguages(Player player) {
        String playerLanguage = languageManager.getPlayerLanguage(player.getUniqueId().toString());
        String availableHeader = config.getMessage("commands.language.available-header", playerLanguage);
    player.sendMessage(ColorHelper.parseComponent(availableHeader));

        StringBuilder languages = new StringBuilder();
        String[] supportedLanguages = languageManager.getSupportedLanguages();

        for (int i = 0; i < supportedLanguages.length; i++) {
            String lang = supportedLanguages[i];
            String displayName = languageManager.getLanguageDisplayName(lang);

            String languageFormat = config.getMessage("commands.language.available-format", playerLanguage)
                .replace("{code}", lang)
                .replace("{name}", displayName);

            languages.append(languageFormat);

            if (i < supportedLanguages.length - 1) {
                languages.append(config.getMessage("commands.language.separator", playerLanguage));
            }
        }

    player.sendMessage(ColorHelper.parseComponent(languages.toString()));

        String usageMessage = config.getMessage("commands.language.usage", playerLanguage);
    player.sendMessage(ColorHelper.parseComponent(usageMessage));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Arrays.stream(languageManager.getSupportedLanguages())
                    .filter(lang -> lang.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    private void openLanguageGUI(Player player) {
        LanguageSelectionGUI gui = new LanguageSelectionGUI(player, languageManager, config);
        gui.open();
    }
}
