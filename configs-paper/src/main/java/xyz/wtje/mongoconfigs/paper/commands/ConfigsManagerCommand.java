package xyz.wtje.mongoconfigs.paper.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.ConfigCollectionsOps;
import xyz.wtje.mongoconfigs.api.MongoConfigsAPI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Set;

public class ConfigsManagerCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;
    private final ConfigCollectionsOps collectionsOps;

    public ConfigsManagerCommand() {
    this.configManager = MongoConfigsAPI.getConfigManager();
    this.collectionsOps = (ConfigCollectionsOps) this.configManager; // plugin initialization guarantees implementation
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
            case "collections" -> handleCollections(sender);
            case "info" -> handleInfo(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleReload(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("Reloading from MongoDB...", NamedTextColor.YELLOW));

        if (args.length == 1) {
            configManager.reloadAll().thenRun(() -> {
                sender.sendMessage(Component.text("✓ All collections reloaded from MongoDB!", NamedTextColor.GREEN));
            }).exceptionally(ex -> {
                sender.sendMessage(Component.text("✗ Error reloading: " + ex.getMessage(), NamedTextColor.RED));
                return null;
            });
        } else {
            String collection = args[1];
            if (collectionsOps.collectionExists(collection)) {
                collectionsOps.reloadCollection(collection).thenRun(() -> {
                    sender.sendMessage(Component.text("✓ Collection '" + collection + "' reloaded from MongoDB!", NamedTextColor.GREEN));
                }).exceptionally(ex -> {
                    sender.sendMessage(Component.text("✗ Error reloading: " + ex.getMessage(), NamedTextColor.RED));
                    return null;
                });
            } else {
                sender.sendMessage(Component.text("✗ Collection '" + collection + "' not found!", NamedTextColor.RED));
            }
        }
    }

    private void handleReloadAll(CommandSender sender) {
        sender.sendMessage(Component.text("Reloading all collections from MongoDB...", NamedTextColor.YELLOW));

        configManager.reloadAll().thenRun(() -> {
            sender.sendMessage(Component.text("✓ All collections reloaded successfully from MongoDB!", NamedTextColor.GREEN));
        }).exceptionally(ex -> {
            sender.sendMessage(Component.text("✗ Error reloading all collections: " + ex.getMessage(), NamedTextColor.RED));
            return null;
        });
    }

    private void handleCollections(CommandSender sender) {
    collectionsOps.getCollections().thenAccept(collections -> {
            if (collections.isEmpty()) {
                sender.sendMessage(Component.text("No collections found.", NamedTextColor.YELLOW));
                return;
            }

            sender.sendMessage(Component.text("=== Available Collections ===", NamedTextColor.GOLD));
            for (String collection : collections) {
                Set<String> languages = collectionsOps.getSupportedLanguages(collection);
                sender.sendMessage(Component.text("• " + collection + " (Languages: " + String.join(", ", languages) + ")", NamedTextColor.AQUA));
            }
        }).exceptionally(throwable -> {
            sender.sendMessage(Component.text("✗ Error loading collections: " + throwable.getMessage(), NamedTextColor.RED));
            return null;
        });
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /configsmanager info <collection>", NamedTextColor.RED));
            return;
        }

        String collection = args[1];
    if (!collectionsOps.collectionExists(collection)) {
            sender.sendMessage(Component.text("Collection '" + collection + "' not found!", NamedTextColor.RED));
            return;
        }

    Set<String> languages = collectionsOps.getSupportedLanguages(collection);

        sender.sendMessage(Component.text("=== Collection Info: " + collection + " ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Supported Languages: " + String.join(", ", languages), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Language Count: " + languages.size(), NamedTextColor.AQUA));

    configManager.findById(collection)
        .get("en", "version")
        .thenAccept(sampleMessage -> {
            String text = sampleMessage == null ? "Not set" : sampleMessage;
            sender.sendMessage(Component.text("Sample message (version): " + text, NamedTextColor.GRAY));
        })
        .exceptionally(ex -> {
            sender.sendMessage(Component.text("Sample message (version): error - " + ex.getMessage(), NamedTextColor.RED));
            return null;
        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== ConfigsManager Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/configsmanager reload [collection] - Reload specific collection", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/configsmanager reloadall - Reload ALL collections from MongoDB", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/configsmanager collections - List collections", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/configsmanager info <collection> - Show collection info", NamedTextColor.AQUA));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("mongoconfigs.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
                return Arrays.asList("reload", "reloadall", "collections", "info");
        }

        if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            if ("reload".equals(subcommand) || "info".equals(subcommand)) {
                    // Avoid blocking for tab completion; return empty or consider a cached snapshot
                    return new ArrayList<String>();
            }
        }

        return new ArrayList<>();
    }
}
