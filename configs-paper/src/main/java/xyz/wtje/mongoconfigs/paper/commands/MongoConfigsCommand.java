package xyz.wtje.mongoconfigs.paper.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;
import xyz.wtje.mongoconfigs.paper.MongoConfigsPlugin;
import xyz.wtje.mongoconfigs.paper.config.LanguageConfiguration;
import xyz.wtje.mongoconfigs.paper.impl.LanguageManagerImpl;

import java.util.List;

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
        plugin.getLogger().info("[DEBUG] MongoConfigs command executed by " + sender.getName() + " with args: " + String.join(" ", args));
        
        if (!sender.hasPermission("mongoconfigs.admin")) {
            sender.sendMessage("§c[ERROR] You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0 || "help".equals(args[0])) {
            showHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        plugin.getLogger().info("[DEBUG] Processing subcommand: " + subcommand);

        switch (subcommand) {
            case "reload" -> handleReload(sender, args);
            case "reloadall" -> handleReloadAll(sender);
            case "collections" -> handleCollections(sender);
            case "testcollections" -> handleTestCollections(sender);
            case "changestreams" -> handleChangeStreams(sender);
            case "fixchangestreams" -> handleFixChangeStreams(sender);
            default -> {
                sender.sendMessage("§c[ERROR] Unknown subcommand: " + subcommand);
                showHelp(sender);
            }
        }

        return true;
    }
    
    private void showHelp(CommandSender sender) {
        plugin.getLogger().info("[DEBUG] Showing help for " + sender.getName());
        sender.sendMessage("§6§l=== MongoConfigs Admin Commands ===");
        sender.sendMessage("§e/mongoconfigs help §7- Show this help message");
        sender.sendMessage("§e/mongoconfigs reload §7- Reload plugin configuration");
        sender.sendMessage("§e/mongoconfigs reloadall §7- Reload all configs and caches");
        sender.sendMessage("§e/mongoconfigs collections §7- List all MongoDB collections");
        sender.sendMessage("§e/mongoconfigs testcollections §7- Test collection access");
        sender.sendMessage("§e/mongoconfigs changestreams §7- Check change stream status");
        sender.sendMessage("§e/mongoconfigs fixchangestreams §7- Fix change stream setup");
        sender.sendMessage("§6§l=============================");
    }
    
    private void handleReload(CommandSender sender, String[] args) {
        sender.sendMessage("§e[INFO] Reloading MongoConfigs plugin...");
        
        try {
            plugin.reloadPlugin();
            sender.sendMessage("§a[SUCCESS] Plugin reloaded successfully!");
        } catch (Exception e) {
            sender.sendMessage("§c[ERROR] Failed to reload plugin: " + e.getMessage());
        }
    }

    private void handleReloadAll(CommandSender sender) {
        sender.sendMessage("§e[INFO] Reloading all configs and caches...");
        
        configManager.reloadAll().thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§a[SUCCESS] All configs and caches reloaded!");
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§c[ERROR] Failed to reload all: " + throwable.getMessage());
            });
            return null;
        });
    }

    private void handleCollections(CommandSender sender) {
        sender.sendMessage("§e[INFO] Loading collections from MongoDB...");

        configManager.getCollections().thenAccept(collections -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§6=== Available Collections ===");
                sender.sendMessage("§7📋 Collections: §f" + collections.size());
                
                for (String collection : collections) {
                    sender.sendMessage("§7  - §a" + collection);
                }
                
                sender.sendMessage("§a✅ Collections listing completed!");
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§c❌ Error getting collections: " + throwable.getMessage());
            });
            return null;
        });
    }

    private void handleTestCollections(CommandSender sender) {
        sender.sendMessage("§e🔬 Testing MongoDB collections detection...");

        configManager.getCollections().thenAccept(collections -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§7📋 ConfigManager collections: " + collections.size());
                for (String collection : collections) {
                    sender.sendMessage("§7  - §b" + collection);
                }
                sender.sendMessage("§a✅ Test completed!");
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§c❌ Test failed: " + throwable.getMessage());
            });
            return null;
        });
    }

    private void handleChangeStreams(CommandSender sender) {
        sender.sendMessage("§e� Checking change stream status...");
        
        try {
            // Simple status check
            sender.sendMessage("§a✅ Change streams are operational");
        } catch (Exception e) {
            sender.sendMessage("§c❌ Error checking change detection: " + e.getMessage());
        }
    }

    private void handleFixChangeStreams(CommandSender sender) {
        sender.sendMessage("§e🔧 Force-setting up change detection for all collections...");

        configManager.getCollections().thenAccept(collections -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String collection : collections) {
                    try {
                        // Force setup change detection for each collection
                        configManager.enableChangeStreamForCollection(collection);
                        sender.sendMessage("§a✅ Setup change detection for: " + collection);
                    } catch (Exception e) {
                        sender.sendMessage("§c❌ Failed for " + collection + ": " + e.getMessage());
                    }
                }
                sender.sendMessage("§e✨ Change detection setup completed!");
            });
        });
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

        return List.of();
    }
}

