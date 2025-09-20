package xyz.wtje.mongoconfigs.paper.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
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
        getSenderLanguageAsync(sender)
            .whenComplete((senderLanguage, error) -> {
                if (error != null) {
                    sender.sendMessage("§c[ERROR] Failed to get language: " + error.getMessage());
                    return;
                }
                
                handleCommandWithLanguage(sender, args, senderLanguage);
            });

        return true;
    }
    
    private void handleCommandWithLanguage(CommandSender sender, String[] args, String senderLanguage) {

        if (!sender.hasPermission("mongoconfigs.admin")) {
            String noPermissionMessage = languageConfig.getMessage("commands.no-permission", senderLanguage);
            sender.sendMessage(ColorHelper.parseComponent(noPermissionMessage));
            return;
        }

        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "reload" -> handleReload(sender, args, senderLanguage);
            case "reloadall" -> handleReloadAll(sender, senderLanguage);
            case "collections" -> handleCollections(sender);
            case "testcollections" -> handleTestCollections(sender);
            case "changestreams" -> handleChangeStreams(sender);
            case "fixchangestreams" -> handleFixChangeStreams(sender);
            case "help" -> showHelp(sender);
            default -> {
                String unknownSubcommandMessage = languageConfig.getMessage("commands.admin.unknown-subcommand", senderLanguage)
                    .replace("{subcommand}", subcommand);
                sender.sendMessage(ColorHelper.parseComponent(unknownSubcommandMessage));
                showHelp(sender);
            }
        }

        return;
    }

    private String translateColors(String text) {
        return ColorHelper.colorize(text);
    }

    private CompletableFuture<String> getSenderLanguageAsync(CommandSender sender) {
        if (sender instanceof Player player) {
            return languageManager.getPlayerLanguage(player.getUniqueId().toString());
        }
        return CompletableFuture.completedFuture("en");
    }

    private void handleReload(CommandSender sender, String[] args, String senderLanguage) {
        String reloadingMessage = languageConfig.getMessage("commands.admin.reloading", senderLanguage);
        sender.sendMessage(ColorHelper.parseComponent(reloadingMessage));

        if (args.length > 1) {
            String collection = args[1];
            sender.sendMessage(ColorHelper.parseComponent("&e🔄 Reloading collection: &f" + collection));

            configManager.reloadCollection(collection)
                .thenRun(() -> {
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

    private void handleReloadAll(CommandSender sender, String senderLanguage) {
        sender.sendMessage(ColorHelper.parseComponent("&e🔄 Reloading ALL collections from MongoDB..."));

        configManager.reloadAll()
            .thenCompose(ignored -> configManager.getCollections())
            .whenComplete((collections, throwable) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (throwable != null) {
                    String reloadErrorMessage = languageConfig.getMessage("commands.admin.reload-error", senderLanguage)
                        .replace("{error}", throwable.getMessage());
                    sender.sendMessage(ColorHelper.parseComponent(reloadErrorMessage));
                    sender.sendMessage(ColorHelper.parseComponent("&c❌ Error reloading collections: " + throwable.getMessage()));
                    return;
                }

                sender.sendMessage(ColorHelper.parseComponent("&a✅ All collections reloaded successfully from MongoDB!"));

                // Refresh GUI messages cache after successful reload
                try {
                    plugin.refreshGUIMessages();
                    sender.sendMessage(ColorHelper.parseComponent("&a✅ GUI messages cache refreshed!"));
                } catch (Exception e) {
                    sender.sendMessage(ColorHelper.parseComponent("&e⚠ Warning: GUI cache refresh failed: " + e.getMessage()));
                }

                if (collections != null) {
                    sender.sendMessage(ColorHelper.parseComponent("&7📋 Reloaded collections: &f" + collections.size()));
                    for (String collection : collections) {
                        sender.sendMessage(ColorHelper.parseComponent("&7  - &a" + collection));
                    }
                } else {
                    sender.sendMessage(ColorHelper.parseComponent("&7Could not list collections: result unavailable."));
                }
            }));
    }

    private void handleCollections(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&e🔍 Loading collections from MongoDB..."));

        configManager.getCollections()
            .thenAccept(collections -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Component.text("§6=== Available Collections ===").color(NamedTextColor.GOLD));
                sender.sendMessage(ColorHelper.parseComponent("&7📋 Collections: &f" + collections.size()));
                
                
                List<CompletableFuture<Void>> collectionFutures = collections.stream()
                    .map(collection -> {
                        return configManager.getSupportedLanguages(collection)
                            .thenCombine(configManager.collectionExists(collection), 
                                (languages, exists) -> {
                                    String status = exists ? "§a✅" : "§c❌";
                                    return String.format("%s §f%s §7- Languages: §e%s",
                                        status, collection, String.join(", ", languages));
                                })
                            .thenAccept(message -> {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    sender.sendMessage(Component.text(message));
                                });
                            })
                            .exceptionally(throwable -> {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    sender.sendMessage(Component.text(String.format("§c❌ §f%s §7- Error: %s",
                                        collection, throwable.getMessage())));
                                });
                                return null;
                            });
                    })
                    .collect(Collectors.toList());
                
                
                CompletableFuture.allOf(collectionFutures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(ColorHelper.parseComponent("&a✅ Collections listing completed!"));
                        });
                    });
            }))
            .exceptionally(throwable -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(Component.text("§c❌ Error getting collections: " + throwable.getMessage())
                            .color(NamedTextColor.RED));
                });
                return null;
            });
    }

    private void handleTestCollections(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&e🔬 Testing MongoDB collections detection..."));

        try {
            var mongoManager = configManager.getMongoManager();
            var mongoCollections = mongoManager.getMongoCollections();

            sender.sendMessage(ColorHelper.parseComponent("&7📋 Direct MongoDB collections: " + mongoCollections.size()));
            for (String collection : mongoCollections) {
                sender.sendMessage(ColorHelper.parseComponent("&7  - &a" + collection));
            }

        } catch (Exception e) {
            sender.sendMessage(ColorHelper.parseComponent("&c❌ Error accessing MongoDB directly: " + e.getMessage()));
        }

        configManager.getCollections()
            .thenAccept(collections -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ColorHelper.parseComponent("&7📋 ConfigManager collections: " + collections.size()));
                    for (String collection : collections) {
                        sender.sendMessage(ColorHelper.parseComponent("&7  - &b" + collection));
                    }

                    sender.sendMessage(ColorHelper.parseComponent("&e🔄 Testing reload for each collection..."));
                    for (String collection : collections) {
                        try {
                            configManager.reloadCollection(collection)
                                .thenRun(() -> {
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        sender.sendMessage(ColorHelper.parseComponent("&a✅ Reloaded: " + collection));
                                    });
                                })
                                .exceptionally(throwable -> {
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        sender.sendMessage(ColorHelper.parseComponent("&c❌ Error reloading " + collection + ": " + throwable.getMessage()));
                                    });
                                    return null;
                                });
                        } catch (Exception e) {
                            sender.sendMessage(ColorHelper.parseComponent("&c❌ Error queuing reload for " + collection + ": " + e.getMessage()));
                        }
                    }
                });
            })
            .exceptionally(throwable -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ColorHelper.parseComponent("&c❌ Error getting collections from ConfigManager: " + throwable.getMessage()));
                });
                return null;
            });
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("§6=== MongoDB Configs Commands ===")
                .color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("§f/mongoconfigs reload [collection] §7- Reload specific collection"));
        sender.sendMessage(Component.text("§f/mongoconfigs reloadall §7- Reload ALL collections from MongoDB"));
        sender.sendMessage(Component.text("§f/mongoconfigs collections §7- List all collections"));
        sender.sendMessage(Component.text("§f/mongoconfigs testcollections §7- Test MongoDB collections detection"));
        sender.sendMessage(Component.text("§f/mongoconfigs changestreams §7- Check Change Streams status"));
        sender.sendMessage(Component.text("§f/mongoconfigs fixchangestreams §7- Force setup Change Streams"));
        sender.sendMessage(Component.text("§f/mongoconfigs help §7- Show this help"));
    }

    private void handleChangeStreams(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&e📡 Change Detection Status..."));
        sender.sendMessage(ColorHelper.parseComponent("&a✅ Auto-Updates: ENABLED"));
        sender.sendMessage(ColorHelper.parseComponent("&7🔍 Method: Change Streams + Polling Fallback"));

        try {
            // Check active watchers
            int watcherCount = 0;
            try {
                java.lang.reflect.Field field = configManager.getClass().getDeclaredField("changeStreamWatchers");
                field.setAccessible(true);
                java.util.Map<?, ?> watchers = (java.util.Map<?, ?>) field.get(configManager);
                watcherCount = watchers.size();
                
                sender.sendMessage(ColorHelper.parseComponent("&7📊 Active Watchers: &f" + watcherCount));
                
                for (Object collection : watchers.keySet()) {
                    sender.sendMessage(ColorHelper.parseComponent("&7  - &a" + collection));
                }
            } catch (Exception e) {
                sender.sendMessage(ColorHelper.parseComponent("&c❌ Error checking watchers: " + e.getMessage()));
            }
            
            sender.sendMessage(ColorHelper.parseComponent("&e🧪 To test: Update a document in MongoDB (auto-detected)"));
            
        } catch (Exception e) {
            sender.sendMessage(ColorHelper.parseComponent("&c❌ Error checking change detection: " + e.getMessage()));
        }
    }

    private void handleFixChangeStreams(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&e🔧 Force-setting up change detection for all collections..."));

        configManager.getCollections().thenAccept(collections -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (String collection : collections) {
                    try {
                        // Force setup change detection for each collection
                        configManager.enableChangeStreamForCollection(collection);
                        sender.sendMessage(ColorHelper.parseComponent("&a✅ Setup change detection for: " + collection));
                    } catch (Exception e) {
                        sender.sendMessage(ColorHelper.parseComponent("&c❌ Failed for " + collection + ": " + e.getMessage()));
                    }
                }
                sender.sendMessage(ColorHelper.parseComponent("&e✨ Change detection setup completed!"));
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mongoconfigs.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return List.of("reload", "reloadall", "collections", "changestreams", "fixchangestreams", "testcollections", "help")
                    .stream()
                    .filter(sub -> sub.startsWith(partial))
                    .toList();
        }

        if (args.length == 2 && "reload".equals(args[0])) {
            
            return List.of();
        }


        return List.of();
    }
}

