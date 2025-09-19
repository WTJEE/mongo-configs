package xyz.wtje.example.help.gui;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.wtje.mongoconfigs.api.LanguageManager;
import xyz.wtje.mongoconfigs.api.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class HelpMenuService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final LanguageManager languageManager;
    private final CompletableFuture<Messages> messagesFuture;
    private final NamespacedKey languageKey;
    private final NamespacedKey helpKey;

    public HelpMenuService(JavaPlugin plugin, LanguageManager languageManager, CompletableFuture<Messages> messagesFuture) {
        this.plugin = plugin;
        this.languageManager = languageManager;
        this.messagesFuture = messagesFuture;
        this.languageKey = new NamespacedKey(plugin, "help-language");
        this.helpKey = new NamespacedKey(plugin, "help-command");
    }

    public CompletableFuture<Void> sendLoadingMessage(Player player, Messages messages) {
        CompletableFuture<String> languageFuture = languageManager.getPlayerLanguage(player.getUniqueId())
            .exceptionally(throwable -> null);
        CompletableFuture<String> defaultLanguageFuture = languageManager.getDefaultLanguage();

        return languageFuture.thenCombine(defaultLanguageFuture, (playerLanguage, defaultLanguage) -> playerLanguage != null ? playerLanguage : defaultLanguage)
            .thenCompose(language -> messages.get("chat.loading", language))
            .thenAccept(message -> Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(colorize(message))));
    }
    public CompletableFuture<Void> openFor(Player player, Messages messages) {
        CompletableFuture<String> languageFuture = languageManager.getPlayerLanguage(player.getUniqueId())
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.WARNING, "Falling back to default language for {0}: {1}", new Object[]{player.getName(), throwable.getMessage()});
                return null;
            });

        CompletableFuture<String> defaultLanguageFuture = languageManager.getDefaultLanguage();

        return languageFuture.thenCombine(defaultLanguageFuture, (playerLanguage, defaultLanguage) -> playerLanguage != null ? playerLanguage : defaultLanguage)
            .thenCompose(language -> languageManager.getLanguageDisplayName(language)
                .exceptionally(throwable -> language)
                .thenCompose(displayName -> buildInventory(player, messages, language, displayName)))
            .thenAccept(inventory -> Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inventory)));
    public void handleClick(Player player, Inventory inventory, ItemStack clicked, Consumer<Throwable> errorHandler) {

        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(languageKey, PersistentDataType.STRING)) {
            String targetLanguage = container.get(languageKey, PersistentDataType.STRING);
            if (targetLanguage == null) {
                return;
            }

            messagesFuture.thenCompose(messages -> languageManager.getLanguageDisplayName(targetLanguage)
                    .exceptionally(throwable -> targetLanguage)
                    .thenCompose(displayName -> languageManager.setPlayerLanguage(player.getUniqueId(), targetLanguage)
                        .thenCompose(v -> messages.get("chat.languageChanged", targetLanguage,
                                "language_display", displayName))
                        .thenAccept(message -> Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(colorize(message));
                            openFor(player, messages);
                        }))))
                .exceptionally(throwable -> {
                    errorHandler.accept(throwable);
                    player.sendMessage(colorize("&cFailed to change language."));
                    return null;
                });
            return;
        }

        if (container.has(helpKey, PersistentDataType.STRING)) {
            messagesFuture.thenCompose(messages -> languageManager.getPlayerLanguage(player.getUniqueId())
                    .thenCompose(language -> {
                        if (language == null) {
                            return languageManager.getDefaultLanguage();
                        }
                        return CompletableFuture.completedFuture(language);
                    })
                    .thenCompose(language -> messages.get("chat.commandsHeader", language)
                        .thenCombine(messages.getList("commands.entries", language), (header, entries) -> {
                            List<String> lines = new ArrayList<>();
                            lines.add(header);
                            lines.addAll(entries);
                            return lines;
                        }))
                    .thenAccept(lines -> Bukkit.getScheduler().runTask(plugin, () -> {
                        lines.stream().map(this::colorize).forEach(player::sendMessage);
                    })))
                .exceptionally(throwable -> {
                    errorHandler.accept(throwable);
                    player.sendMessage(colorize("&cFailed to print commands."));
                    return null;
                });
        }
    }

    public boolean isHelpInventory(Inventory inventory) {
        return inventory.getHolder() instanceof HelpMenuHolder;
    }

    public String colorize(String input) {
        return LEGACY.serialize(LEGACY.deserialize(input));
    }

    private CompletableFuture<Inventory> buildInventory(Player player, Messages messages, String language, String languageDisplayName) {
        CompletableFuture<String> titleFuture = messages.get("gui.title", language, "player", player.getName());
        CompletableFuture<String> helpNameFuture = messages.get("gui.items.help.name", language,
            "language_display", languageDisplayName);
        CompletableFuture<List<String>> helpLoreFuture = messages.getList("gui.items.help.lore", language,
            "language_display", languageDisplayName);
        CompletableFuture<List<String>> commandListFuture = messages.getList("commands.entries", language);
        CompletableFuture<List<LanguageButton>> languageButtonsFuture = buildLanguageButtons(messages, language);

        return CompletableFuture.allOf(titleFuture, helpNameFuture, helpLoreFuture, commandListFuture, languageButtonsFuture)
            .thenApply(unused -> {
                String title = titleFuture.join();
                String helpName = helpNameFuture.join();
                List<String> helpLore = new ArrayList<>(helpLoreFuture.join());
                helpLore.add(" ");
                helpLore.addAll(commandListFuture.join());

                HelpMenuHolder holder = new HelpMenuHolder();
                Inventory inventory = Bukkit.createInventory(holder, 27, LEGACY.deserialize(title));
                holder.setInventory(inventory);

                ItemStack helpItem = new ItemStack(Material.BOOK);
                ItemMeta helpMeta = Objects.requireNonNull(helpItem.getItemMeta());
                helpMeta.displayName(LEGACY.deserialize(helpName));
                helpMeta.lore(helpLore.stream().map(LEGACY::deserialize).toList());
                helpMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                helpMeta.getPersistentDataContainer().set(helpKey, PersistentDataType.STRING, "commands");
                helpItem.setItemMeta(helpMeta);
                inventory.setItem(11, helpItem);

                List<LanguageButton> buttons = languageButtonsFuture.join();
                int slot = 13;
                for (LanguageButton button : buttons) {
                    ItemStack paper = new ItemStack(Material.PAPER);
                    ItemMeta meta = Objects.requireNonNull(paper.getItemMeta());
                    meta.displayName(LEGACY.deserialize(button.displayName()));
                    meta.lore(button.lore().stream().map(LEGACY::deserialize).toList());
                    meta.getPersistentDataContainer().set(languageKey, PersistentDataType.STRING, button.code());
                    paper.setItemMeta(meta);
                    inventory.setItem(slot++, paper);
                }

                return inventory;
            });
    }

    private CompletableFuture<List<LanguageButton>> buildLanguageButtons(Messages messages, String viewerLanguage) {
        return languageManager.getSupportedLanguages().thenCompose(langs -> {
            if (langs.length == 0) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            List<CompletableFuture<LanguageButton>> tasks = new ArrayList<>();
            for (String target : langs) {
                CompletableFuture<String> displayFuture = languageManager.getLanguageDisplayName(target)
                    .exceptionally(throwable -> target);

                CompletableFuture<LanguageButton> buttonFuture = displayFuture.thenCompose(display -> {
                    String selectedMarker = viewerLanguage.equalsIgnoreCase(target) ? "Yes" : "No";
                    CompletableFuture<String> nameFuture = messages.get("gui.items.language.name", viewerLanguage,
                        "language_display", display,
                        "language_code", target,
                        "selected", selectedMarker);
                    CompletableFuture<List<String>> loreFuture = messages.getList("gui.items.language.lore", viewerLanguage,
                        "language_display", display,
                        "language_code", target,
                        "selected", selectedMarker);
                    return nameFuture.thenCombine(loreFuture, (name, lore) -> new LanguageButton(target, name, lore));
                });

                tasks.add(buttonFuture);
            }

            return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                .thenApply(unused -> tasks.stream().map(CompletableFuture::join).toList());
        });
    }



    private static final class LanguageButton {
        private final String code;
        private final String displayName;
        private final List<String> lore;

        private LanguageButton(String code, String displayName, List<String> lore) {
            this.code = code;
            this.displayName = displayName;
            this.lore = lore;
        }

        public String code() {
            return code;
        }

        public String displayName() {
            return displayName;
        }

        public List<String> lore() {
            return lore;
        }
    }

    private static final class HelpMenuHolder implements InventoryHolder {
        private Inventory inventory;

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}













