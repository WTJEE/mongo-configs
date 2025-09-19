package xyz.wtje.example.help.command;

import org.bukkit.entity.Player;
import xyz.wtje.example.help.gui.HelpMenuService;
import xyz.wtje.mongoconfigs.api.Messages;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class HelpMenuCommand {

    private final HelpMenuService menuService;
    private final CompletableFuture<Messages> messagesFuture;

    public HelpMenuCommand(HelpMenuService menuService, CompletableFuture<Messages> messagesFuture) {
        this.menuService = menuService;
        this.messagesFuture = messagesFuture;
    }

    public void execute(Player player, Consumer<Throwable> errorHandler) {
        messagesFuture
            .thenCompose(messages -> menuService.sendLoadingMessage(player, messages)
                .thenCompose(unused -> menuService.openFor(player, messages)))
            .exceptionally(throwable -> {
                errorHandler.accept(throwable);
                player.sendMessage(menuService.colorize("&cFailed to open the help menu. Check console for details."));
                return null;
            });
    }
}

