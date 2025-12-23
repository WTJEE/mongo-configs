package xyz.wtje.mongoconfigs.paper.events;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PlayerLanguageUpdateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String playerId;
    private final String oldLanguage;
    private final String newLanguage;

    public PlayerLanguageUpdateEvent(String playerId, String oldLanguage, String newLanguage) {
        super(!Bukkit.isPrimaryThread());
        this.playerId = playerId;
        this.oldLanguage = oldLanguage;
        this.newLanguage = newLanguage;
    }

    public @NotNull String getPlayerId() {
        return playerId;
    }

    public @Nullable Player getPlayer() {
        try {
            return Bukkit.getPlayer(UUID.fromString(playerId));
        } catch (Exception e) {
            return null;
        }
    }

    public String getOldLanguage() {
        return oldLanguage;
    }

    public String getNewLanguage() {
        return newLanguage;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
