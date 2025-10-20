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
import java.util.concurrent.CompletableFuture;
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
        getPlugin().getLogger().info("[DEBUG] Language command executed by " + sender.getName());
        
        if (!(sender instanceof Player player)) {
            String playersOnlyMessage = config.getMessage("commands.players-only", "en");
            sender.sendMessage(ColorHelper.parseComponent(playersOnlyMessage));
            return true;
        }

        if (languageManager == null) {
            player.sendMessage("§c[ERROR] LanguageManager is not initialized!");
            getPlugin().getLogger().severe("[DEBUG] LanguageManager is null for player " + player.getName());
            return true;
        }

        
        if (args.length == 0) {
            getPlugin().getLogger().info("[DEBUG] Opening GUI for " + player.getName());
            openLanguageGUI(player);
            return true;
        }
        
        
        String requestedLanguage = args[0].toLowerCase();
        handleLanguageSetting(player, requestedLanguage);
        return true;
    }
        
    private void openLanguageGUI(Player player) {
        getPlugin().getLogger().info("[DEBUG] Creating and opening GUI for " + player.getName());
        
        
        LanguageSelectionGUI.forceCleanupForPlayer(player);
        
        
        LanguageSelectionGUI gui = new LanguageSelectionGUI(player, languageManager, config);
        gui.open();
    }
    
    private void handleLanguageSetting(Player player, String requestedLanguage) {
        getPlugin().getLogger().info("[DEBUG] Handling language setting for " + player.getName() + ": " + requestedLanguage);
        
        
        languageManager.getSupportedLanguages().thenAccept(supportedLanguages -> {
            boolean isSupported = java.util.Arrays.asList(supportedLanguages).contains(requestedLanguage);
            
            org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
                if (!isSupported) {
                    String unsupportedMessage = config.getMessage("commands.language.unsupported", "en")
                        .replace("{language}", requestedLanguage);
                    player.sendMessage(ColorHelper.parseComponent(unsupportedMessage));
                    showAvailableLanguages(player);
                    return;
                }
                
                
                languageManager.setPlayerLanguage(player.getUniqueId(), requestedLanguage)
                    .thenAccept(result -> {
                        org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
                            String displayName = config.getMessage("language.names." + requestedLanguage, requestedLanguage);
                            String successMessage = config.getMessage("commands.language.success", requestedLanguage)
                                .replace("{language}", displayName);
                            player.sendMessage(ColorHelper.parseComponent(successMessage));
                        });
                    })
                    .exceptionally(throwable -> {
                        org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
                            String errorMessage = config.getMessage("commands.language.error", "en");
                            player.sendMessage(ColorHelper.parseComponent(errorMessage));
                        });
                        return null;
                    });
            });
        });
    }
    
    private void showAvailableLanguages(Player player) {
        String availableHeader = config.getMessage("commands.language.available-header", "en");
        player.sendMessage(ColorHelper.parseComponent(availableHeader));
        
        languageManager.getSupportedLanguages().thenAccept(supportedLanguages -> {
            org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
                StringBuilder languages = new StringBuilder();
                for (int i = 0; i < supportedLanguages.length; i++) {
                    String lang = supportedLanguages[i];
                    String displayName = config.getMessage("language.names." + lang, lang);
                    
                    String languageFormat = config.getMessage("commands.language.available-format", "en")
                        .replace("{code}", lang)
                        .replace("{name}", displayName);
                    
                    languages.append(languageFormat);
                    
                    if (i < supportedLanguages.length - 1) {
                        languages.append(config.getMessage("commands.language.separator", "en"));
                    }
                }
                
                player.sendMessage(ColorHelper.parseComponent(languages.toString()));
                String usageMessage = config.getMessage("commands.language.usage", "en");
                player.sendMessage(ColorHelper.parseComponent(usageMessage));
            });
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


    
    private org.bukkit.plugin.Plugin getPlugin() {
        return org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(LanguageCommand.class);
    }
}

