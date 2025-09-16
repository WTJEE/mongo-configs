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

        if (languageManager == null) {
            player.sendMessage("§c[ERROR] LanguageManager is not initialized!");
            return true;
        }

        
        languageManager.getPlayerLanguage(player.getUniqueId().toString())
            .whenComplete((playerLanguage, error) -> {
                if (error != null) {
                    player.sendMessage("§c[ERROR] Failed to get player language: " + error.getMessage());
                    handleCommand(player, args, "en"); 
                } else {
                    handleCommand(player, args, playerLanguage);
                }
            });

        return true;
    }

    private void handleCommand(Player player, String[] args, String playerLanguage) {

        if (args.length == 0) {
            try {
                openLanguageGUI(player, playerLanguage);
            } catch (Exception e) {
                player.sendMessage("§c[ERROR] Failed to open GUI: " + e.getMessage());
                showLanguageInfo(player, player.getUniqueId().toString(), playerLanguage);
            }
            return;
        }

        String requestedLanguage = args[0].toLowerCase();

        languageManager.getSupportedLanguages()
            .whenComplete((supportedLanguages, error) -> {
                if (error != null) {
                    player.sendMessage("§c[ERROR] Failed to get supported languages: " + error.getMessage());
                    return;
                }
                
                boolean isSupported = Arrays.asList(supportedLanguages).contains(requestedLanguage);
                if (!isSupported) {
                    String unsupportedMessage = config.getMessage("commands.language.unsupported", playerLanguage)
                        .replace("{language}", requestedLanguage);
                    player.sendMessage(ColorHelper.parseComponent(unsupportedMessage));
                    showAvailableLanguages(player, playerLanguage);
                    return;
                }
                
                
                setPlayerLanguage(player, requestedLanguage, playerLanguage);
            });
    }
    
    private void setPlayerLanguage(Player player, String requestedLanguage, String playerLanguage) {

        languageManager.setPlayerLanguage(player.getUniqueId(), requestedLanguage)
            .whenComplete((result, error) -> {
                if (error != null) {
                    String errorMessage = config.getMessage("commands.language.error", playerLanguage);
                    player.sendMessage(ColorHelper.parseComponent(errorMessage));
                } else {
                    String displayName = config.getMessage("language.names." + requestedLanguage, requestedLanguage);
                    String successMessage = config.getMessage("commands.language.success", playerLanguage)
                        .replace("{language}", displayName);
                    player.sendMessage(ColorHelper.parseComponent(successMessage));
                }
            });
    }

    private String translateColors(String text) {
        return ColorHelper.colorize(text);
    }

    private void showLanguageInfo(Player player, String playerId, String playerLanguage) {
        languageManager.getPlayerLanguage(playerId)
            .whenComplete((currentLanguage, error) -> {
                if (error != null) {
                    player.sendMessage("§c[ERROR] Failed to get language info: " + error.getMessage());
                    return;
                }
                
                String displayName = config.getMessage("language.names." + currentLanguage, currentLanguage);
                String currentMessage = config.getMessage("commands.language.current", playerLanguage)
                    .replace("{language}", displayName);
                player.sendMessage(ColorHelper.parseComponent(currentMessage));

                showAvailableLanguages(player, playerLanguage);
            });
    }

    private void showAvailableLanguages(Player player, String playerLanguage) {
        String availableHeader = config.getMessage("commands.language.available-header", playerLanguage);
        player.sendMessage(ColorHelper.parseComponent(availableHeader));

        languageManager.getSupportedLanguages()
            .whenComplete((supportedLanguages, error) -> {
                if (error != null) {
                    player.sendMessage("§c[ERROR] Failed to get supported languages: " + error.getMessage());
                    return;
                }
                
                StringBuilder languages = new StringBuilder();
                for (int i = 0; i < supportedLanguages.length; i++) {
                    String lang = supportedLanguages[i];
                    String displayName = config.getMessage("language.names." + lang, lang);

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
            });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            
            
            return Arrays.asList("en", "pl", "es", "fr", "de")
                .stream()
                .filter(lang -> lang.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }

        return List.of();
    }

    private void openLanguageGUI(Player player, String playerLanguage) {
        try {
            LanguageSelectionGUI gui = new LanguageSelectionGUI(player, languageManager, config);

            try {
                gui.open();
            } catch (Exception asyncError) {
                player.sendMessage("§6[INFO] Using simplified GUI mode");
                gui.openSimple();
            }
        } catch (Exception e) {
            player.sendMessage("§c[ERROR] Exception in openLanguageGUI: " + e.getMessage());
            showLanguageInfo(player, player.getUniqueId().toString(), playerLanguage);
        }
    }
}

