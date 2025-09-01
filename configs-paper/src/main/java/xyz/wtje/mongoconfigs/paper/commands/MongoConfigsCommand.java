package xyz.wtje.mongoconfigs.paper.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;
import xyz.wtje.mongoconfigs.paper.MongoConfigsPlugin;
import xyz.wtje.mongoconfigs.paper.config.LanguageConfiguration;
import xyz.wtje.mongoconfigs.paper.impl.LanguageManagerImpl;
import xyz.wtje.mongoconfigs.paper.util.ColorHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class MongoConfigsCommand implements CommandExecutor, TabCompleter {
    
    private final ConfigManagerImpl configManager;
    private final LanguageManagerImpl languageManager;
    private final MongoConfigsPlugin plugin;
    private final LanguageConfiguration languageConfig;
    
    public MongoConfigsCommand(ConfigManagerImpl configManager, LanguageManagerImpl languageManager, 
                              MongoConfigsPlugin plugin, LanguageConfiguration languageConfig) {
        this.configManager = configManager;
        this.languageManager = languageManager;
        this.plugin = plugin;
        this.languageConfig = languageConfig;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String senderLanguage = getSenderLanguage(sender);
        
        if (!sender.hasPermission("mongoconfigs.admin")) {
            String noPermissionMessage = languageConfig.getMessage("commands.no-permission", senderLanguage);
            sender.sendMessage(ColorHelper.parseComponent(noPermissionMessage));
            return true;
        }
        
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        switch (subcommand) {
            case "reload" -> handleReload(sender, args);
            case "reloadall" -> handleReloadAll(sender);
            case "stats" -> handleStats(sender);
            case "collections" -> handleCollections(sender);
            case "create" -> handleCreate(sender, args);
            case "copy" -> handleCopy(sender, args);
            case "help" -> showHelp(sender);
            default -> {
                String unknownSubcommandMessage = languageConfig.getMessage("commands.admin.unknown-subcommand", senderLanguage)
                    .replace("{subcommand}", subcommand);
                sender.sendMessage(ColorHelper.parseComponent(unknownSubcommandMessage));
                showHelp(sender);
            }
        }
        
        return true;
    }
    
    private String translateColors(String text) {
        return ColorHelper.colorize(text);
    }
    
    private String getSenderLanguage(CommandSender sender) {
        if (sender instanceof Player player) {
            return languageManager.getPlayerLanguage(player.getUniqueId().toString());
        }
        return "en";
    }
    
    private void handleReload(CommandSender sender, String[] args) {
        String senderLanguage = getSenderLanguage(sender);
        String reloadingMessage = languageConfig.getMessage("commands.admin.reloading", senderLanguage);
        sender.sendMessage(ColorHelper.parseComponent(reloadingMessage));
        
        if (args.length > 1) {
            String collection = args[1];
            sender.sendMessage(ColorHelper.parseComponent("&e🔄 Reloading collection: &f" + collection));
            
            configManager.reloadCollection(collection)
                .thenRun(() -> {
                    // Wykonaj w głównym wątku
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        String reloadedCollectionMessage = languageConfig.getMessage("commands.admin.reloaded-collection", senderLanguage)
                            .replace("{collection}", collection);
                        sender.sendMessage(ColorHelper.parseComponent(reloadedCollectionMessage));
                        sender.sendMessage(ColorHelper.parseComponent("&a✅ Collection '" + collection + "' reloaded successfully!"));
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        String reloadErrorMessage = languageConfig.getMessage("commands.admin.reload-error", senderLanguage)
                            .replace("{error}", throwable.getMessage());
                        sender.sendMessage(ColorHelper.parseComponent(reloadErrorMessage));
                        sender.sendMessage(ColorHelper.parseComponent("&c❌ Error reloading collection '" + collection + "': " + throwable.getMessage()));
                    });
                    return null;
                });
        } else {
            sender.sendMessage(ColorHelper.parseComponent("&e🔄 Reloading plugin configuration..."));
            
            CompletableFuture.runAsync(() -> {
                try {
                    plugin.reloadPlugin();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        String reloadSuccessMessage = languageConfig.getMessage("commands.admin.reload-success", senderLanguage);
                        sender.sendMessage(ColorHelper.parseComponent(reloadSuccessMessage));
                        sender.sendMessage(ColorHelper.parseComponent("&a✅ Plugin configuration reloaded!"));
                    });
                } catch (Exception e) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        String reloadErrorMessage = languageConfig.getMessage("commands.admin.reload-error", senderLanguage)
                            .replace("{error}", e.getMessage());
                        sender.sendMessage(ColorHelper.parseComponent(reloadErrorMessage));
                        sender.sendMessage(ColorHelper.parseComponent("&c❌ Error reloading plugin: " + e.getMessage()));
                    });
                }
            });
        }
    }
    
    private void handleReloadAll(CommandSender sender) {
        String senderLanguage = getSenderLanguage(sender);
        sender.sendMessage(ColorHelper.parseComponent("&e🔄 Reloading ALL collections from MongoDB..."));
        
        configManager.reloadAll()
            .thenRun(() -> {
                // Wykonaj w głównym wątku serwera
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ColorHelper.parseComponent("&a✅ All collections reloaded successfully from MongoDB!"));
                    
                    // Pokaż dodatkowe informacje
                    try {
                        var collections = configManager.getCollections().join();
                        sender.sendMessage(ColorHelper.parseComponent("&7📋 Reloaded collections: &f" + collections.size()));
                        for (String collection : collections) {
                            sender.sendMessage(ColorHelper.parseComponent("&7  - &a" + collection));
                        }
                    } catch (Exception e) {
                        sender.sendMessage(ColorHelper.parseComponent("&7Could not list collections: " + e.getMessage()));
                    }
                });
            })
            .exceptionally(throwable -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String reloadErrorMessage = languageConfig.getMessage("commands.admin.reload-error", senderLanguage)
                        .replace("{error}", throwable.getMessage());
                    sender.sendMessage(ColorHelper.parseComponent(reloadErrorMessage));
                    sender.sendMessage(ColorHelper.parseComponent("&c❌ Error reloading collections: " + throwable.getMessage()));
                });
                return null;
            });
    }
    
    private void handleStats(CommandSender sender) {
        try {
            var cacheStats = configManager.getCacheStats();
            var metrics = configManager.getMetrics();
            
            sender.sendMessage(Component.text("§6=== MongoDB Configs Statistics ===")
                    .color(NamedTextColor.GOLD));
            sender.sendMessage(Component.text(String.format("§7Cache Hit Rate: §f%.2f%%", 
                    cacheStats.getHitRate() * 100)));
            sender.sendMessage(Component.text(String.format("§7Cache Size: §f%d entries", 
                    cacheStats.getSize())));
            sender.sendMessage(Component.text(String.format("§7Cache Requests: §f%d (Hits: %d, Misses: %d)", 
                    cacheStats.getRequestCount(), cacheStats.getHitCount(), cacheStats.getMissCount())));
            sender.sendMessage(Component.text(String.format("§7MongoDB Operations: §f%d", 
                    metrics.getMongoOperationsCount())));
            sender.sendMessage(Component.text(String.format("§7Connections: §f%d/%d", 
                    metrics.getActiveConnections(), metrics.getConnectionPoolSize())));
            sender.sendMessage(Component.text(String.format("§7Change Streams: §f%s", 
                    metrics.isChangeStreamsActive() ? "§aActive" : "§cInactive")));
            sender.sendMessage(Component.text(String.format("§7Monitored Collections: §f%d", 
                    metrics.getMonitoredCollections())));
            
        } catch (Exception e) {
            sender.sendMessage(Component.text("§cError getting statistics: " + e.getMessage())
                    .color(NamedTextColor.RED));
        }
    }
    
    private void handleCollections(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&e🔍 Loading collections from MongoDB..."));
        
        configManager.getCollections()
            .thenAccept(collections -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(Component.text("§6=== Available Collections ===")
                            .color(NamedTextColor.GOLD));
                    
                    if (collections.isEmpty()) {
                        sender.sendMessage(Component.text("§c❌ No collections found in MongoDB!")
                                .color(NamedTextColor.RED));
                        sender.sendMessage(Component.text("§7Create collections using: /mongoconfigs create <name> <languages...>")
                                .color(NamedTextColor.GRAY));
                    } else {
                        sender.sendMessage(Component.text("§7Found " + collections.size() + " collections:")
                                .color(NamedTextColor.GRAY));
                        
                        for (String collection : collections) {
                            try {
                                Set<String> languages = configManager.getSupportedLanguages(collection);
                                boolean exists = configManager.collectionExists(collection);
                                String status = exists ? "§a✅" : "§c❌";
                                sender.sendMessage(Component.text(String.format("%s §f%s §7- Languages: §e%s", 
                                        status, collection, String.join(", ", languages))));
                            } catch (Exception e) {
                                sender.sendMessage(Component.text(String.format("§c❌ §f%s §7- Error: %s", 
                                        collection, e.getMessage())));
                            }
                        }
                    }
                });
            })
            .exceptionally(throwable -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(Component.text("§c❌ Error getting collections: " + throwable.getMessage())
                            .color(NamedTextColor.RED));
                });
                return null;
            });
    }
    
    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("§cUsage: /mongoconfigs create <collection> <languages...>")
                    .color(NamedTextColor.RED));
            sender.sendMessage(Component.text("§7Example: /mongoconfigs create MyPlugin_Config en pl de")
                    .color(NamedTextColor.GRAY));
            return;
        }
        
        String collection = args[1];
        Set<String> languages = Set.of(Arrays.copyOfRange(args, 2, args.length));
        
        sender.sendMessage(Component.text("§7Creating collection: " + collection)
                .color(NamedTextColor.GRAY));
        
        CompletableFuture.runAsync(() -> {
            try {
                configManager.createCollection(collection, languages).join();
                sender.sendMessage(Component.text("§aCreated collection: " + collection + " with languages: " + 
                        String.join(", ", languages)).color(NamedTextColor.GREEN));
            } catch (Exception e) {
                sender.sendMessage(Component.text("§cError creating collection: " + e.getMessage())
                        .color(NamedTextColor.RED));
            }
        });
    }
    
    private void handleCopy(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(Component.text("§cUsage: /mongoconfigs copy <collection> <source-language> <target-language>")
                    .color(NamedTextColor.RED));
            sender.sendMessage(Component.text("§7Example: /mongoconfigs copy MyPlugin_Config en pl")
                    .color(NamedTextColor.GRAY));
            return;
        }
        
        String collection = args[1];
        String sourceLanguage = args[2];
        String targetLanguage = args[3];
        
        sender.sendMessage(Component.text("§7Copying language data...")
                .color(NamedTextColor.GRAY));
        
        CompletableFuture.runAsync(() -> {
            try {
                configManager.copyLanguage(collection, sourceLanguage, targetLanguage).join();
                sender.sendMessage(Component.text("§aCopied language " + sourceLanguage + " to " + 
                        targetLanguage + " in collection " + collection).color(NamedTextColor.GREEN));
            } catch (Exception e) {
                sender.sendMessage(Component.text("§cError copying language: " + e.getMessage())
                        .color(NamedTextColor.RED));
            }
        });
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("§6=== MongoDB Configs Commands ===")
                .color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("§f/mongoconfigs reload [collection] §7- Reload specific collection"));
        sender.sendMessage(Component.text("§f/mongoconfigs reloadall §7- Reload ALL collections from MongoDB"));
        sender.sendMessage(Component.text("§f/mongoconfigs stats §7- Show cache and performance statistics"));
        sender.sendMessage(Component.text("§f/mongoconfigs collections §7- List all collections"));
        sender.sendMessage(Component.text("§f/mongoconfigs create <collection> <languages...> §7- Create new collection"));
        sender.sendMessage(Component.text("§f/mongoconfigs copy <collection> <source> <target> §7- Copy language data"));
        sender.sendMessage(Component.text("§f/mongoconfigs help §7- Show this help"));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mongoconfigs.admin")) {
            return List.of();
        }
        
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return List.of("reload", "reloadall", "stats", "collections", "create", "copy", "help")
                    .stream()
                    .filter(sub -> sub.startsWith(partial))
                    .toList();
        }
        
        if (args.length == 2 && "reload".equals(args[0])) {
            try {
                return configManager.getCollections().join().stream().toList();
            } catch (Exception e) {
                return List.of();
            }
        }
        
        if (args.length >= 3 && "create".equals(args[0])) {
            return Arrays.asList(languageManager.getSupportedLanguages());
        }
        
        return List.of();
    }
}