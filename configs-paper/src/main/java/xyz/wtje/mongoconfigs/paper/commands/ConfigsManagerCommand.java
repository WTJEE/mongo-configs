package xyz.wtje.mongoconfigs.paper.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.wtje.mongoconfigs.api.CacheStats;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.MongoConfigsAPI;
import xyz.wtje.mongoconfigs.api.PerformanceMetrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Set;


public class ConfigsManagerCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;

    public ConfigsManagerCommand() {
        this.configManager = MongoConfigsAPI.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mongoconfigs.admin")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender, args);
            case "reloadall" -> handleReloadAll(sender);
            case "stats" -> handleStats(sender);
            case "collections" -> handleCollections(sender);
            case "create" -> handleCreate(sender, args);
            case "info" -> handleInfo(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleReload(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("Reloading from MongoDB...", NamedTextColor.YELLOW));
        
        CompletableFuture.runAsync(() -> {
            try {
                if (args.length == 1) {
                    configManager.reloadAll().join();
                    sender.sendMessage(Component.text("✓ All collections reloaded from MongoDB!", NamedTextColor.GREEN));
                } else {
                    String collection = args[1];
                    if (configManager.collectionExists(collection)) {
                        configManager.reloadCollection(collection).join();
                        sender.sendMessage(Component.text("✓ Collection '" + collection + "' reloaded from MongoDB!", NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("✗ Collection '" + collection + "' not found!", NamedTextColor.RED));
                    }
                }
            } catch (Exception e) {
                sender.sendMessage(Component.text("✗ Error reloading: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private void handleReloadAll(CommandSender sender) {
        sender.sendMessage(Component.text("Reloading all collections from MongoDB...", NamedTextColor.YELLOW));
        
        CompletableFuture.runAsync(() -> {
            try {
                configManager.reloadAll().join();
                sender.sendMessage(Component.text("✓ All collections reloaded successfully from MongoDB!", NamedTextColor.GREEN));
            } catch (Exception e) {
                sender.sendMessage(Component.text("✗ Error reloading all collections: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private void handleStats(CommandSender sender) {
        CacheStats stats = configManager.getCacheStats();
        PerformanceMetrics metrics = configManager.getMetrics();

        sender.sendMessage(Component.text("=== MongoDB Configs Statistics ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Cache Hit Rate: " + String.format("%.2f%%", stats.getHitRate() * 100), NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Cache Size: " + stats.getSize() + " entries", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Total Requests: " + stats.getRequestCount(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Cache Hits: " + stats.getHitCount(), NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Cache Misses: " + stats.getMissCount(), NamedTextColor.RED));
        sender.sendMessage(Component.text("Evictions: " + stats.getEvictionCount(), NamedTextColor.YELLOW));
        
        sender.sendMessage(Component.text("--- Performance Metrics ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("MongoDB Operations: " + metrics.getMongoOperationsCount(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Average MongoDB Time: " + formatDuration(metrics.getAverageMongoTime()), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Cache Operations: " + metrics.getCacheOperationsCount(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Average Cache Time: " + formatDuration(metrics.getAverageCacheTime()), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Active Connections: " + metrics.getActiveConnections(), NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Change Streams Active: " + (metrics.isChangeStreamsActive() ? "Yes" : "No"), 
            metrics.isChangeStreamsActive() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void handleCollections(CommandSender sender) {
        configManager.getCollections().thenAccept(collections -> {
            if (collections.isEmpty()) {
                sender.sendMessage(Component.text("No collections found.", NamedTextColor.YELLOW));
                return;
            }

            sender.sendMessage(Component.text("=== Available Collections ===", NamedTextColor.GOLD));
            for (String collection : collections) {
                Set<String> languages = configManager.getSupportedLanguages(collection);
                sender.sendMessage(Component.text("• " + collection + " (Languages: " + String.join(", ", languages) + ")", NamedTextColor.AQUA));
            }
        }).exceptionally(throwable -> {
            sender.sendMessage(Component.text("✗ Error loading collections: " + throwable.getMessage(), NamedTextColor.RED));
            return null;
        });
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /configsmanager create <collection> <language1> [language2] ...", NamedTextColor.RED));
            return;
        }

        String collection = args[1];
        Set<String> languages = Set.of(Arrays.copyOfRange(args, 2, args.length));

        if (configManager.collectionExists(collection)) {
            sender.sendMessage(Component.text("Collection '" + collection + "' already exists!", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Creating collection '" + collection + "' with languages: " + String.join(", ", languages), NamedTextColor.YELLOW));
        
        configManager.createCollection(collection, languages)
            .thenRun(() -> {
                sender.sendMessage(Component.text("✓ Collection '" + collection + "' created successfully!", NamedTextColor.GREEN));
            })
            .exceptionally(throwable -> {
                sender.sendMessage(Component.text("✗ Failed to create collection: " + throwable.getMessage(), NamedTextColor.RED));
                return null;
            });
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /configsmanager info <collection>", NamedTextColor.RED));
            return;
        }

        String collection = args[1];
        if (!configManager.collectionExists(collection)) {
            sender.sendMessage(Component.text("Collection '" + collection + "' not found!", NamedTextColor.RED));
            return;
        }

        Set<String> languages = configManager.getSupportedLanguages(collection);
        
        sender.sendMessage(Component.text("=== Collection Info: " + collection + " ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Supported Languages: " + String.join(", ", languages), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Language Count: " + languages.size(), NamedTextColor.AQUA));

        try {
            String sampleConfig = configManager.getConfig(collection, "version", "Not set");
            sender.sendMessage(Component.text("Sample config (version): " + sampleConfig, NamedTextColor.GRAY));
        } catch (Exception e) {
            sender.sendMessage(Component.text("No config data found.", NamedTextColor.GRAY));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== ConfigsManager Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/configsmanager reload [collection] - Reload specific collection", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/configsmanager reloadall - Reload ALL collections from MongoDB", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/configsmanager stats - Show statistics", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/configsmanager collections - List collections", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/configsmanager create <collection> <langs...> - Create collection", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/configsmanager info <collection> - Show collection info", NamedTextColor.AQUA));
    }

    private String formatDuration(Duration duration) {
        long nanos = duration.toNanos();
        if (nanos < 1000) {
            return nanos + "ns";
        } else if (nanos < 1000000) {
            return String.format("%.1fμs", nanos / 1000.0);
        } else if (nanos < 1000000000) {
            return String.format("%.1fms", nanos / 1000000.0);
        } else {
            return String.format("%.1fs", nanos / 1000000000.0);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("mongoconfigs.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("reload", "reloadall", "stats", "collections", "create", "info");
        }

        if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            if ("reload".equals(subcommand) || "info".equals(subcommand)) {
                try {
                    return new ArrayList<String>(configManager.getCollections().join());
                } catch (Exception e) {
                    return new ArrayList<String>();
                }
            }
            if ("create".equals(subcommand)) {
                return Arrays.asList("<collection_name>");
            }
        }

        if (args.length >= 3 && "create".equals(args[0].toLowerCase())) {
            return Arrays.asList("en", "pl", "de", "fr", "es", "ru");
        }

        return new ArrayList<>();
    }
}