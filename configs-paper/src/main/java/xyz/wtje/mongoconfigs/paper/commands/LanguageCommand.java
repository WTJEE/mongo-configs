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
        if (!(sender instanceof Player player)) {
            String playersOnlyMessage = config.getMessage("commands.players-only", "en");
            sender.sendMessage(ColorHelper.parseComponent(playersOnlyMessage));
            return true;
        }

        if (languageManager == null) {
            player.sendMessage("§c[ERROR] LanguageManager is not initialized!");
            return true;
        }

        // Open GUI immediately if no args, to ensure fast UX. We'll resolve language async.
        if (args.length == 0) {
            // Check for existing GUI first
            LanguageSelectionGUI existingGui = LanguageSelectionGUI.getOpenGUI(player);
            if (existingGui != null) {
                // Refresh the existing GUI
                existingGui.refreshAsync();
                return true;
            }
            
            // Create new GUI
            LanguageSelectionGUI newGui = new LanguageSelectionGUI(player, languageManager, config);
            newGui.openAsync().exceptionally(throwable -> {
                player.sendMessage("§c[ERROR] Failed to open language GUI: " + throwable.getMessage());
                return null;
            });
            return true;
        }
        
        languageManager.getPlayerLanguage(player.getUniqueId().toString())
            .whenComplete((playerLanguage, error) -> {
                if (error != null) {
                    player.sendMessage("§c[ERROR] Failed to get player language: " + error.getMessage());
                    handleCommand(player, args, config.getDefaultLanguage()); 
                } else {
                    handleCommand(player, args, playerLanguage);
                }
            });

        return true;
    }

    private void handleCommand(Player player, String[] args, String playerLanguage) {
        // Immediately return from main thread and handle everything async
        CompletableFuture.runAsync(() -> {
            if (args.length == 0) {
                // GUI opening already handled above
                return;
            }

            String requestedLanguage = args[0].toLowerCase();
            
            // Handle language setting asynchronously
            handleLanguageSettingAsync(player, requestedLanguage, playerLanguage);
        }).exceptionally(throwable -> {
            // Send error message on main thread
            org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
                player.sendMessage("§c[ERROR] Command processing failed: " + throwable.getMessage());
            });
            return null;
        });
    }
    
    private void handleLanguageSettingAsync(Player player, String requestedLanguage, String playerLanguage) {
        languageManager.getSupportedLanguages()
            .whenCompleteAsync((supportedLanguages, error) -> {
                if (error != null) {
                    org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
                        player.sendMessage("§c[ERROR] Failed to get supported languages: " + error.getMessage());
                    });
                    return;
                }
                
                boolean isSupported = Arrays.asList(supportedLanguages).contains(requestedLanguage);
                if (!isSupported) {
                    org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
                        String unsupportedMessage = config.getMessage("commands.language.unsupported", playerLanguage)
                            .replace("{language}", requestedLanguage);
                        player.sendMessage(ColorHelper.parseComponent(unsupportedMessage));
                    });
                    showAvailableLanguagesAsync(player, playerLanguage);
                    return;
                }
                
                setPlayerLanguageAsync(player, requestedLanguage, playerLanguage);
            });
    }
    
    private void setPlayerLanguageAsync(Player player, String requestedLanguage, String playerLanguage) {
        languageManager.setPlayerLanguage(player.getUniqueId(), requestedLanguage)
            .whenCompleteAsync((result, error) -> {
                org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
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
            });
    }

    private String translateColors(String text) {
        return ColorHelper.colorize(text);
    }

    private void showLanguageInfoAsync(Player player, String playerId, String playerLanguage) {
        languageManager.getPlayerLanguage(playerId)
            .whenCompleteAsync((currentLanguage, error) -> {
                if (error != null) {
                    org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
                        player.sendMessage("§c[ERROR] Failed to get language info: " + error.getMessage());
                    });
                    return;
                }
                
                org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
                    String displayName = config.getMessage("language.names." + currentLanguage, currentLanguage);
                    String currentMessage = config.getMessage("commands.language.current", playerLanguage)
                        .replace("{language}", displayName);
                    player.sendMessage(ColorHelper.parseComponent(currentMessage));
                });

                showAvailableLanguagesAsync(player, playerLanguage);
            });
    }

    private void showAvailableLanguagesAsync(Player player, String playerLanguage) {
        org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
            String availableHeader = config.getMessage("commands.language.available-header", playerLanguage);
            player.sendMessage(ColorHelper.parseComponent(availableHeader));
        });

        languageManager.getSupportedLanguages()
            .whenCompleteAsync((supportedLanguages, error) -> {
                if (error != null) {
                    org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
                        player.sendMessage("§c[ERROR] Failed to get supported languages: " + error.getMessage());
                    });
                    return;
                }
                
                // Build message async, send on main thread
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

                org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
                    player.sendMessage(ColorHelper.parseComponent(languages.toString()));
                    String usageMessage = config.getMessage("commands.language.usage", playerLanguage);
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

    private void openLanguageGUIAsync(Player player, String playerLanguage) {
        // Check for existing GUI first
        LanguageSelectionGUI existingGui = LanguageSelectionGUI.getOpenGUI(player);
        if (existingGui != null) {
            // Refresh the existing GUI
            existingGui.refreshAsync();
            return;
        }
        
        // Create GUI object immediately (lightweight operation)
        LanguageSelectionGUI gui = new LanguageSelectionGUI(player, languageManager, config);
        
        // Open GUI instantly async
        gui.openAsync().exceptionally(throwable -> {
            // Log error but don't block
            getPlugin().getLogger().warning("GUI open failed: " + throwable.getMessage());
            
            // Try simple mode as fallback
            org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
                player.sendMessage("§6[INFO] Using fallback display mode");
            });
            
            // Show language info instead
            showLanguageInfoAsync(player, player.getUniqueId().toString(), playerLanguage);
            return null;
        });
    }
    
    private org.bukkit.plugin.Plugin getPlugin() {
        return org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(LanguageCommand.class);
    }
}

