package xyz.wtje.mongoconfigs.paper.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;
import xyz.wtje.mongoconfigs.paper.impl.LanguageManagerImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for sending messages with placeholders
 * Supports placeholders like {player}, {lang}, {amount}, etc.
 */
public class MessageHelper {
    
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private final ConfigManagerImpl configManager;
    private final LanguageManagerImpl languageManager;
    
    public MessageHelper(ConfigManagerImpl configManager, LanguageManagerImpl languageManager) {
        this.configManager = configManager;
        this.languageManager = languageManager;
    }
    
    /**
     * Send message to player with placeholders
     * Example: sendMessage(player, "messages", "welcome", "{player}", player.getName(), "{world}", player.getWorld().getName())
     */
    public CompletableFuture<Void> sendMessage(Player player, String collection, String key, Object... placeholders) {
        return languageManager.getPlayerLanguage(player.getUniqueId().toString())
            .thenCompose(lang -> configManager.getMessageAsync(collection, lang, key))
            .thenAccept(message -> {
                if (message != null) {
                    String formatted = replacePlaceholders(message, placeholders);
                    player.sendMessage(ColorHelper.parseComponent(formatted));
                }
            });
    }
    
    /**
     * Send message with a Map of placeholders
     * Example: Map<String, Object> placeholders = Map.of("player", player.getName(), "amount", 100);
     */
    public CompletableFuture<Void> sendMessage(Player player, String collection, String key, Map<String, Object> placeholders) {
        return languageManager.getPlayerLanguage(player.getUniqueId().toString())
            .thenCompose(lang -> {
                // Add automatic placeholders
                Map<String, Object> allPlaceholders = new HashMap<>(placeholders);
                allPlaceholders.put("player", player.getName());
                allPlaceholders.put("displayname", player.getDisplayName());
                allPlaceholders.put("lang", lang);
                allPlaceholders.put("world", player.getWorld().getName());
                allPlaceholders.put("x", player.getLocation().getBlockX());
                allPlaceholders.put("y", player.getLocation().getBlockY());
                allPlaceholders.put("z", player.getLocation().getBlockZ());
                
                return configManager.getMessageAsync(collection, lang, key)
                    .thenAccept(message -> {
                        if (message != null) {
                            String formatted = replacePlaceholders(message, allPlaceholders);
                            player.sendMessage(ColorHelper.parseComponent(formatted));
                        }
                    });
            });
    }
    
    /**
     * Get formatted message with placeholders
     */
    public CompletableFuture<String> getMessage(String lang, String collection, String key, Object... placeholders) {
        return configManager.getMessageAsync(collection, lang, key)
            .thenApply(message -> {
                if (message == null) return "";
                return replacePlaceholders(message, placeholders);
            });
    }
    
    /**
     * Get formatted message for player with automatic placeholders
     */
    public CompletableFuture<String> getPlayerMessage(Player player, String collection, String key, Map<String, Object> extraPlaceholders) {
        return languageManager.getPlayerLanguage(player.getUniqueId().toString())
            .thenCompose(lang -> {
                Map<String, Object> placeholders = new HashMap<>();
                // Add automatic player placeholders
                placeholders.put("player", player.getName());
                placeholders.put("displayname", player.getDisplayName());
                placeholders.put("lang", lang);
                placeholders.put("world", player.getWorld().getName());
                placeholders.put("x", player.getLocation().getBlockX());
                placeholders.put("y", player.getLocation().getBlockY());
                placeholders.put("z", player.getLocation().getBlockZ());
                placeholders.put("health", (int) player.getHealth());
                placeholders.put("level", player.getLevel());
                placeholders.put("exp", player.getTotalExperience());
                
                // Add extra placeholders
                if (extraPlaceholders != null) {
                    placeholders.putAll(extraPlaceholders);
                }
                
                return configManager.getMessageAsync(collection, lang, key)
                    .thenApply(message -> {
                        if (message == null) return "";
                        return replacePlaceholders(message, placeholders);
                    });
            });
    }
    
    /**
     * Broadcast message to all players with placeholders
     */
    public CompletableFuture<Void> broadcast(String collection, String key, Map<String, Object> placeholders) {
        return CompletableFuture.runAsync(() -> {
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                sendMessage(player, collection, key, placeholders);
            }
        });
    }
    
    /**
     * Send message to CommandSender (console or player)
     */
    public CompletableFuture<Void> sendMessage(CommandSender sender, String collection, String key, Object... placeholders) {
        if (sender instanceof Player) {
            return sendMessage((Player) sender, collection, key, placeholders);
        } else {
            // For console, use default language
            return languageManager.getDefaultLanguage()
                .thenCompose(lang -> configManager.getMessageAsync(collection, lang, key))
                .thenAccept(message -> {
                    if (message != null) {
                        String formatted = replacePlaceholders(message, placeholders);
                        sender.sendMessage(ColorHelper.parseComponent(formatted));
                    }
                });
        }
    }
    
    /**
     * Replace placeholders in message using varargs (key, value, key, value...)
     */
    private String replacePlaceholders(String message, Object... placeholders) {
        if (placeholders.length == 0) return message;
        
        String result = message;
        
        // Handle key-value pairs
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String key = String.valueOf(placeholders[i]);
            Object value = placeholders[i + 1];
            
            // Support both {key} and %key% formats
            result = result.replace("{" + key + "}", String.valueOf(value));
            result = result.replace("%" + key + "%", String.valueOf(value));
        }
        
        return result;
    }
    
    /**
     * Replace placeholders in message using a map
     */
    private String replacePlaceholders(String message, Map<String, Object> placeholders) {
        if (placeholders.isEmpty()) return message;
        
        String result = message;
        
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Support both {key} and %key% formats
            result = result.replace("{" + key + "}", String.valueOf(value));
            result = result.replace("%" + key + "%", String.valueOf(value));
        }
        
        return result;
    }
    
    /**
     * Create a placeholder map builder for easy construction
     */
    public static PlaceholderBuilder placeholders() {
        return new PlaceholderBuilder();
    }
    
    /**
     * Builder for creating placeholder maps
     */
    public static class PlaceholderBuilder {
        private final Map<String, Object> placeholders = new HashMap<>();
        
        public PlaceholderBuilder add(String key, Object value) {
            placeholders.put(key, value);
            return this;
        }
        
        public PlaceholderBuilder player(Player player) {
            placeholders.put("player", player.getName());
            placeholders.put("displayname", player.getDisplayName());
            placeholders.put("uuid", player.getUniqueId().toString());
            placeholders.put("world", player.getWorld().getName());
            placeholders.put("x", player.getLocation().getBlockX());
            placeholders.put("y", player.getLocation().getBlockY());
            placeholders.put("z", player.getLocation().getBlockZ());
            return this;
        }
        
        public PlaceholderBuilder location(org.bukkit.Location loc) {
            placeholders.put("world", loc.getWorld().getName());
            placeholders.put("x", loc.getBlockX());
            placeholders.put("y", loc.getBlockY());
            placeholders.put("z", loc.getBlockZ());
            return this;
        }
        
        public PlaceholderBuilder amount(Number amount) {
            placeholders.put("amount", amount);
            return this;
        }
        
        public PlaceholderBuilder time(long seconds) {
            placeholders.put("time", formatTime(seconds));
            placeholders.put("seconds", seconds);
            placeholders.put("minutes", seconds / 60);
            placeholders.put("hours", seconds / 3600);
            return this;
        }
        
        public Map<String, Object> build() {
            return new HashMap<>(placeholders);
        }
        
        private String formatTime(long seconds) {
            if (seconds < 60) {
                return seconds + "s";
            } else if (seconds < 3600) {
                return (seconds / 60) + "m " + (seconds % 60) + "s";
            } else {
                return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
            }
        }
    }
}