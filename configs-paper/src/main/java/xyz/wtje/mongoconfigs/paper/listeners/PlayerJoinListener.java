package xyz.wtje.mongoconfigs.paper.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import xyz.wtje.mongoconfigs.paper.impl.LanguageManagerImpl;

import java.util.concurrent.CompletableFuture;


public class PlayerJoinListener implements Listener {
    
    private final LanguageManagerImpl languageManager;
    
    public PlayerJoinListener(LanguageManagerImpl languageManager) {
        this.languageManager = languageManager;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        String playerId = event.getPlayer().getUniqueId().toString();
        

        CompletableFuture.runAsync(() -> {
            try {
                languageManager.getPlayerLanguage(playerId);
            } catch (Exception e) {
            }
        });
    }
}