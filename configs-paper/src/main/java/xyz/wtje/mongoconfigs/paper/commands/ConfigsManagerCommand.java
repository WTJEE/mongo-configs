package xyz.wtje.mongoconfigs.paper.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.MongoConfigsAPI;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;
import xyz.wtje.mongoconfigs.paper.MongoConfigsPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ConfigsManagerCommand implements CommandExecutor, TabCompleter {

    private final MongoConfigsPlugin plugin;
    private final ConfigManager configManager;
    private final ConfigManagerImpl configManagerImpl;

    public ConfigsManagerCommand(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.configManager = MongoConfigsAPI.getConfigManager();
        this.configManagerImpl = (ConfigManagerImpl) this.configManager;
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
            configManager.reloadAll()
                .whenComplete((ignored, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        sender.sendMessage(Component.text("[ERROR] Error reloading: " + throwable.getMessage(), NamedTextColor.RED));
                        return;
                    }

                    sender.sendMessage(Component.text("[OK] All collections reloaded from MongoDB!", NamedTextColor.GREEN));
                }));
            return;
        }

        String collection = args[1];

        configManagerImpl.collectionExists(collection)
            .thenCompose(exists -> {
                if (!exists) {
                    return CompletableFuture.completedFuture(Boolean.FALSE);
                }
                return configManagerImpl.reloadCollection(collection)
                    .thenApply(ignored -> Boolean.TRUE);
            })
            .whenComplete((reloaded, throwable) -> runSync(() -> {
                if (throwable != null) {
                    sender.sendMessage(Component.text("[ERROR] Error reloading: " + throwable.getMessage(), NamedTextColor.RED));
                    return;
                }

                if (Boolean.TRUE.equals(reloaded)) {
                    sender.sendMessage(Component.text("[OK] Collection '" + collection + "' reloaded from MongoDB!", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("[ERROR] Collection '" + collection + "' not found!", NamedTextColor.RED));
                }
            }));
    }

    private void handleReloadAll(CommandSender sender) {
        sender.sendMessage(Component.text("Reloading all collections from MongoDB...", NamedTextColor.YELLOW));

        configManager.reloadAll()
            .whenComplete((ignored, throwable) -> runSync(() -> {
                if (throwable != null) {
                    sender.sendMessage(Component.text("[ERROR] Error reloading all collections: " + throwable.getMessage(), NamedTextColor.RED));
                    return;
                }

                sender.sendMessage(Component.text("[OK] All collections reloaded successfully from MongoDB!", NamedTextColor.GREEN));
            }));
    }

    private void handleCollections(CommandSender sender) {
        configManagerImpl.getCollections()
            .<List<CollectionInfo>>thenCompose(collections -> {
                List<String> collectionList = new ArrayList<>(collections);
                if (collectionList.isEmpty()) {
                    return CompletableFuture.completedFuture(Collections.<CollectionInfo>emptyList());
                }

                List<CompletableFuture<CollectionInfo>> futures = new ArrayList<>();
                for (String collection : collectionList) {
                    futures.add(configManagerImpl.getSupportedLanguages(collection)
                        .thenApply(languages -> new CollectionInfo(collection, languages)));
                }

                CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return allDone.thenApply(ignored ->
            futures.stream()
                .map((CompletableFuture<CollectionInfo> cf) -> cf.getNow(new CollectionInfo("", Set.of())))
                .filter(info -> !info.name.isEmpty())
                .collect(Collectors.<CollectionInfo>toList()));
            })
            .whenComplete((infos, throwable) -> runSync(() -> {
                if (throwable != null) {
                    sender.sendMessage(Component.text("[ERROR] Error loading collections: " + throwable.getMessage(), NamedTextColor.RED));
                    return;
                }

                if (infos == null || infos.isEmpty()) {
                    sender.sendMessage(Component.text("No collections found.", NamedTextColor.YELLOW));
                    return;
                }

                sender.sendMessage(Component.text("=== Available Collections ===", NamedTextColor.GOLD));
                for (CollectionInfo info : infos) {
                    sender.sendMessage(Component.text("- " + info.name + " (Languages: " + String.join(", ", info.languages) + ")", NamedTextColor.AQUA));
                }
            }));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /configsmanager info <collection>", NamedTextColor.RED));
            return;
        }

        String collection = args[1];

        configManagerImpl.collectionExists(collection)
            .thenCompose(exists -> {
                if (!exists) {
                    return CompletableFuture.completedFuture(new InfoResult(false, Set.of()));
                }
                return configManagerImpl.getSupportedLanguages(collection)
                    .thenApply(languages -> new InfoResult(true, languages));
            })
            .whenComplete((result, throwable) -> runSync(() -> {
                if (throwable != null) {
                    sender.sendMessage(Component.text("Error checking collection: " + throwable.getMessage(), NamedTextColor.RED));
                    return;
                }

                if (result == null || !result.exists) {
                    sender.sendMessage(Component.text("Collection '" + collection + "' not found!", NamedTextColor.RED));
                    return;
                }

                sender.sendMessage(Component.text("=== Collection Info: " + collection + " ===", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Supported Languages: " + String.join(", ", result.languages), NamedTextColor.AQUA));
                sender.sendMessage(Component.text("Language Count: " + result.languages.size(), NamedTextColor.AQUA));

                configManagerImpl.findById(collection)
                    .get("version")
                    .whenComplete((sampleMessage, ex) -> runSync(() -> {
                        if (ex != null) {
                            sender.sendMessage(Component.text("Sample message (version): error - " + ex.getMessage(), NamedTextColor.RED));
                            return;
                        }

                        String text = sampleMessage == null ? "Not set" : sampleMessage;
                        sender.sendMessage(Component.text("Sample message (version): " + text, NamedTextColor.GRAY));
                    }));
            }));
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
                return new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }

    private void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private static final class CollectionInfo {
        private final String name;
        private final Set<String> languages;

        private CollectionInfo(String name, Set<String> languages) {
            this.name = name;
            this.languages = languages;
        }
    }

    private static final class InfoResult {
        private final boolean exists;
        private final Set<String> languages;

        private InfoResult(boolean exists, Set<String> languages) {
            this.exists = exists;
            this.languages = languages;
        }
    }
}






