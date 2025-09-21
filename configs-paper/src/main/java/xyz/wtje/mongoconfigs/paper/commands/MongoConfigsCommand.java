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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class MongoConfigsCommand implements CommandExecutor, TabCompleter {

    private final ConfigManagerImpl configManager;
    private final LanguageManagerImpl languageManager;
    private final MongoConfigsPlugin plugin;
    private final LanguageConfiguration languageConfig;

    private final ConcurrentMap<CommandSender, CompletableFuture<Void>> messageQueue = new ConcurrentHashMap<>();

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
            sendMessagesAsync(sender, noPermissionMessage);
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
                sendMessagesAsync(sender, unknownSubcommandMessage);
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

    private void sendMessagesAsync(CommandSender sender, String... messages) {
        List<String> filtered = Arrays.stream(messages)
            .filter(message -> message != null && !message.isEmpty())
            .toList();
        if (filtered.isEmpty()) {
            return;
        }

        // Simplify - just send messages directly on main thread
        if (Bukkit.isPrimaryThread()) {
            // Already on main thread, send directly
            for (String msg : filtered) {
                sender.sendMessage(ColorHelper.parseComponent(msg));
            }
        } else {
            // Schedule on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (String msg : filtered) {
                    sender.sendMessage(ColorHelper.parseComponent(msg));
                }
            });
        }
    }

    private void handleReload(CommandSender sender, String[] args, String senderLanguage) {
        String reloadingMessage = languageConfig.getMessage("commands.admin.reloading", senderLanguage);
        sendMessagesAsync(sender, reloadingMessage);

        if (args.length > 1) {
            String collection = args[1];
            sendMessagesAsync(sender, "&e🔄 Reloading collection: &f" + collection);

            configManager.reloadCollection(collection)
                .thenRun(() -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        String reloadedCollectionMessage = languageConfig.getMessage("commands.admin.reloaded-collection", senderLanguage)
                            .replace("{collection}", collection);
                        sendMessagesAsync(sender, reloadedCollectionMessage);
                        sendMessagesAsync(sender, "&a✅ Collection '" + collection + "' reloaded successfully!");
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        String reloadErrorMessage = languageConfig.getMessage("commands.admin.reload-error", senderLanguage)
                            .replace("{error}", throwable.getMessage());
                        sendMessagesAsync(sender, reloadErrorMessage);
                        sendMessagesAsync(sender, "&c❌ Error reloading collection '" + collection + "': " + throwable.getMessage());
                    });
                    return null;
                });
        } else {
            sendMessagesAsync(sender, "&e🔄 Reloading plugin configuration...");

            CompletableFuture.runAsync(() -> {
                try {
                    plugin.reloadPlugin();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        String reloadSuccessMessage = languageConfig.getMessage("commands.admin.reload-success", senderLanguage);
                        sendMessagesAsync(sender, reloadSuccessMessage);
                        sendMessagesAsync(sender, "&a✅ Plugin configuration reloaded!");
                    });
                } catch (Exception e) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        String reloadErrorMessage = languageConfig.getMessage("commands.admin.reload-error", senderLanguage)
                            .replace("{error}", e.getMessage());
                        sendMessagesAsync(sender, reloadErrorMessage);
                        sendMessagesAsync(sender, "&c❌ Error reloading plugin: " + e.getMessage());
                    });
                }
            });
        }
    }

    private void handleReloadAll(CommandSender sender, String senderLanguage) {
        sendMessagesAsync(sender, "&e🔄 Reloading ALL collections from MongoDB...");

        configManager.reloadAll()
            .thenCompose(ignored -> configManager.getCollections())
            .whenComplete((collections, throwable) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (throwable != null) {
                    String reloadErrorMessage = languageConfig.getMessage("commands.admin.reload-error", senderLanguage)
                        .replace("{error}", throwable.getMessage());
                    sendMessagesAsync(sender, reloadErrorMessage);
                    sendMessagesAsync(sender, "&c❌ Error reloading collections: " + throwable.getMessage());
                    return;
                }

                sendMessagesAsync(sender, "&a✅ All collections reloaded successfully from MongoDB!");

                // Refresh GUI messages cache after successful reload
                try {
                    plugin.refreshGUIMessages();
                    sendMessagesAsync(sender, "&a✅ GUI messages cache refreshed!");
                } catch (Exception e) {
                    sendMessagesAsync(sender, "&e⚠ Warning: GUI cache refresh failed: " + e.getMessage());
                }

                if (collections != null) {
                    sendMessagesAsync(sender, "&7📋 Reloaded collections: &f" + collections.size());
                    for (String collection : collections) {
                        sendMessagesAsync(sender, "&7  - &a" + collection);
                    }
                } else {
                    sendMessagesAsync(sender, "&7Could not list collections: result unavailable.");
                }
            }));
    }

    private void handleCollections(CommandSender sender) {
        sendMessagesAsync(sender, "&e🔍 Loading collections from MongoDB...");

        configManager.getCollections()
            .thenAccept(collections -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Component.text("§6=== Available Collections ===").color(NamedTextColor.GOLD));
                sendMessagesAsync(sender, "&7📋 Collections: &f" + collections.size());
                
                
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
                            sendMessagesAsync(sender, "&a✅ Collections listing completed!");
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
        sendMessagesAsync(sender, "&e🔬 Testing MongoDB collections detection...");

        try {
            var mongoManager = configManager.getMongoManager();
            var mongoCollections = mongoManager.getMongoCollections();

            sendMessagesAsync(sender, "&7📋 Direct MongoDB collections: " + mongoCollections.size());
            for (String collection : mongoCollections) {
                sendMessagesAsync(sender, "&7  - &a" + collection);
            }

        } catch (Exception e) {
            sendMessagesAsync(sender, "&c❌ Error accessing MongoDB directly: " + e.getMessage());
        }

        configManager.getCollections()
            .thenAccept(collections -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sendMessagesAsync(sender, "&7📋 ConfigManager collections: " + collections.size());
                    for (String collection : collections) {
                        sendMessagesAsync(sender, "&7  - &b" + collection);
                    }

                    sendMessagesAsync(sender, "&e🔄 Testing reload for each collection...");
                    for (String collection : collections) {
                        try {
                            configManager.reloadCollection(collection)
                                .thenRun(() -> {
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        sendMessagesAsync(sender, "&a✅ Reloaded: " + collection);
                                    });
                                })
                                .exceptionally(throwable -> {
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        sendMessagesAsync(sender, "&c❌ Error reloading " + collection + ": " + throwable.getMessage());
                                    });
                                    return null;
                                });
                        } catch (Exception e) {
                            sendMessagesAsync(sender, "&c❌ Error queuing reload for " + collection + ": " + e.getMessage());
                        }
                    }
                });
            })
            .exceptionally(throwable -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sendMessagesAsync(sender, "&c❌ Error getting collections from ConfigManager: " + throwable.getMessage());
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
        sendMessagesAsync(sender, "&e📡 Change Detection Status...");
        sendMessagesAsync(sender, "&a✅ Auto-Updates: ENABLED");
        sendMessagesAsync(sender, "&7🔍 Method: Change Streams + Polling Fallback");

        try {
            // Check active watchers
            int watcherCount = 0;
            try {
                java.lang.reflect.Field field = configManager.getClass().getDeclaredField("changeStreamWatchers");
                field.setAccessible(true);
                java.util.Map<?, ?> watchers = (java.util.Map<?, ?>) field.get(configManager);
                watcherCount = watchers.size();
                
                sendMessagesAsync(sender, "&7📊 Active Watchers: &f" + watcherCount);
                
                for (Object collection : watchers.keySet()) {
                    sendMessagesAsync(sender, "&7  - &a" + collection);
                }
            } catch (Exception e) {
                sendMessagesAsync(sender, "&c❌ Error checking watchers: " + e.getMessage());
            }
            
            sendMessagesAsync(sender, "&e🧪 To test: Update a document in MongoDB (auto-detected)");
            
        } catch (Exception e) {
            sendMessagesAsync(sender, "&c❌ Error checking change detection: " + e.getMessage());
        }
    }

    private void handleFixChangeStreams(CommandSender sender) {
        sendMessagesAsync(sender, "&e🔧 Force-setting up change detection for all collections...");

        configManager.getCollections().thenAccept(collections -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (String collection : collections) {
                    try {
                        // Force setup change detection for each collection
                        configManager.enableChangeStreamForCollection(collection);
                        sendMessagesAsync(sender, "&a✅ Setup change detection for: " + collection);
                    } catch (Exception e) {
                        sendMessagesAsync(sender, "&c❌ Failed for " + collection + ": " + e.getMessage());
                    }
                }
                sendMessagesAsync(sender, "&e✨ Change detection setup completed!");
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

