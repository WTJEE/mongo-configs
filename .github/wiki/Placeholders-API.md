# Placeholders API Guide

## Overview
MongoConfigs provides a powerful placeholder system that supports both simple variable substitution and complex Map-based placeholders. This guide shows you how to use the enhanced API for maximum flexibility.

## API Methods

### Basic Message Retrieval
```java
// Simple message without placeholders
CompletableFuture<String> future = messages.get("welcome.basic");
String message = future.join(); // "Welcome to our server!"

// Message with language
CompletableFuture<String> future = messages.get("welcome.basic", "pl");
String message = future.join(); // "Witamy na naszym serwerze!"
```

### Using Variable Arguments (Old Style)
```java
// Using Object... varargs
CompletableFuture<String> future = messages.get("welcome.player", "en", 
    "PlayerName", "World1", 100);
String message = future.join(); // Replaces {0}, {1}, {2}...
```

### Using Map-Based Placeholders (New API)
```java
// Create placeholder map
Map<String, Object> placeholders = Map.of(
    "player", "Steve",
    "lang", "en",
    "price", 150.50,
    "world", "Survival"
);

// Get message with placeholders
CompletableFuture<String> future = messages.get("shop.purchase", "en", placeholders);
String message = future.join(); 
// "Steve purchased item for $150.50 in world Survival"
```

## Complete API Reference

### Messages Interface
```java
public interface Messages {
    // Basic methods
    CompletableFuture<String> get(String path);
    CompletableFuture<String> get(String path, String language);
    
    // Variable arguments (legacy)
    CompletableFuture<String> get(String path, Object... placeholders);
    CompletableFuture<String> get(String path, String language, Object... placeholders);
    
    // Map-based placeholders (NEW)
    CompletableFuture<String> get(String path, Map<String, Object> placeholders);
    CompletableFuture<String> get(String path, String language, Map<String, Object> placeholders);
    
    // List methods
    CompletableFuture<List<String>> getList(String path);
    CompletableFuture<List<String>> getList(String path, String language);
}
```

### ConfigManager Interface
```java
public interface ConfigManager {
    // Basic message retrieval
    CompletableFuture<String> getMessageAsync(String collection, String language, String key);
    CompletableFuture<String> getMessageAsync(String collection, String language, String key, String defaultValue);
    
    // With variable arguments
    CompletableFuture<String> getMessageAsync(String collection, String language, String key, Object... placeholders);
    
    // With Map placeholders (NEW)
    CompletableFuture<String> getMessageAsync(String collection, String language, String key, Map<String, Object> placeholders);
    CompletableFuture<String> getMessageAsync(String collection, String language, String key, String defaultValue, Map<String, Object> placeholders);
}
```

## Practical Examples

### E-commerce Plugin
```java
public class ShopPlugin extends JavaPlugin {
    private Messages messages;
    private ConfigManager configManager;
    
    @Override
    public void onEnable() {
        this.configManager = MongoConfigsAPI.getConfigManager();
        // Load messages...
    }
    
    public void handlePurchase(Player player, ItemStack item, double price) {
        // Create dynamic placeholders
        Map<String, Object> placeholders = Map.of(
            "player", player.getName(),
            "item", getItemDisplayName(item),
            "price", price,
            "balance", getPlayerBalance(player),
            "world", player.getWorld().getName(),
            "amount", item.getAmount()
        );
        
        // Send purchase confirmation
        messages.get("shop.purchase.success", getPlayerLanguage(player), placeholders)
            .thenAccept(msg -> player.sendMessage(msg));
        
        // Broadcast to other players
        Map<String, Object> broadcastPlaceholders = Map.of(
            "player", player.getName(),
            "item", getItemDisplayName(item),
            "price", price
        );
        
        messages.get("shop.purchase.broadcast", "en", broadcastPlaceholders)
            .thenAccept(msg -> Bukkit.broadcastMessage(msg));
    }
}
```

### PvP Plugin
```java
public class PvPPlugin extends JavaPlugin {
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        if (killer != null) {
            // Calculate additional data
            double distance = killer.getLocation().distance(victim.getLocation());
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            
            Map<String, Object> placeholders = Map.of(
                "killer", killer.getName(),
                "victim", victim.getName(),
                "weapon", weapon.getType().name().toLowerCase().replace("_", " "),
                "distance", Math.round(distance * 10.0) / 10.0, // Round to 1 decimal
                "killer_health", Math.round(killer.getHealth()),
                "world", killer.getWorld().getName(),
                "time", new SimpleDateFormat("HH:mm").format(new Date())
            );
            
            // Send death message to all players
            messages.get("pvp.kill.broadcast", "en", placeholders)
                .thenAccept(Bukkit::broadcastMessage);
            
            // Send detailed message to killer
            messages.get("pvp.kill.killer_message", getPlayerLanguage(killer), placeholders)
                .thenAccept(killer::sendMessage);
            
            // Send message to victim
            messages.get("pvp.death.victim_message", getPlayerLanguage(victim), placeholders)
                .thenAccept(victim::sendMessage);
        }
    }
}
```

### Admin Tools Plugin
```java
public class AdminPlugin extends JavaPlugin {
    
    public void teleportPlayer(CommandSender sender, Player target, Location location) {
        target.teleport(location);
        
        Map<String, Object> placeholders = Map.of(
            "admin", sender.getName(),
            "player", target.getName(),
            "x", location.getBlockX(),
            "y", location.getBlockY(),
            "z", location.getBlockZ(),
            "world", location.getWorld().getName(),
            "timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
        );
        
        // Notify admin
        messages.get("admin.teleport.success", "en", placeholders)
            .thenAccept(sender::sendMessage);
        
        // Notify player
        messages.get("admin.teleport.notification", getPlayerLanguage(target), placeholders)
            .thenAccept(target::sendMessage);
        
        // Log to console
        messages.get("admin.teleport.log", "en", placeholders)
            .thenAccept(msg -> getLogger().info(msg));
    }
}
```

### Economy Integration
```java
public class EconomyPlugin extends JavaPlugin {
    
    public void transferMoney(Player from, Player to, double amount) {
        if (!hasEnoughMoney(from, amount)) {
            Map<String, Object> errorPlaceholders = Map.of(
                "amount", amount,
                "balance", getBalance(from),
                "needed", amount - getBalance(from)
            );
            
            messages.get("economy.transfer.insufficient_funds", getPlayerLanguage(from), errorPlaceholders)
                .thenAccept(from::sendMessage);
            return;
        }
        
        // Process transfer
        removeMoney(from, amount);
        addMoney(to, amount);
        
        Map<String, Object> placeholders = Map.of(
            "sender", from.getName(),
            "receiver", to.getName(),
            "amount", amount,
            "sender_balance", getBalance(from),
            "receiver_balance", getBalance(to),
            "fee", calculateFee(amount),
            "timestamp", System.currentTimeMillis()
        );
        
        // Notify sender
        messages.get("economy.transfer.sent", getPlayerLanguage(from), placeholders)
            .thenAccept(from::sendMessage);
        
        // Notify receiver
        messages.get("economy.transfer.received", getPlayerLanguage(to), placeholders)
            .thenAccept(to::sendMessage);
    }
}
```

## MongoDB Message Examples

### Shop Messages
```json
{
  "_id": "shop_en",
  "lang": "en",
  "purchase": {
    "success": "§a✓ You bought {amount}x {item} for ${price}. Balance: ${balance}",
    "broadcast": "§e{player} §7just bought §e{item} §7for §6${price}",
    "insufficient_funds": "§cYou need ${needed} more to buy {item}!"
  }
}
```

### PvP Messages
```json
{
  "_id": "pvp_en",
  "lang": "en",
  "kill": {
    "broadcast": "§c{killer} §7killed §c{victim} §7with {weapon} from {distance}m",
    "killer_message": "§aYou killed {victim}! Distance: {distance}m, Health left: {killer_health}❤",
    "headshot": "§6HEADSHOT! §c{killer} §7headshot §c{victim} §7with {weapon}"
  },
  "death": {
    "victim_message": "§cYou were killed by {killer} using {weapon} at {time}"
  }
}
```

### Admin Messages
```json
{
  "_id": "admin_en",
  "lang": "en",
  "teleport": {
    "success": "§aTeleported {player} to {x}, {y}, {z} in {world}",
    "notification": "§7You were teleported by {admin} to {x}, {y}, {z}",
    "log": "[{timestamp}] {admin} teleported {player} to {world} ({x}, {y}, {z})"
  }
}
```

### Economy Messages
```json
{
  "_id": "economy_en",
  "lang": "en",
  "transfer": {
    "sent": "§aSent ${amount} to {receiver}. Your balance: ${sender_balance}",
    "received": "§aReceived ${amount} from {sender}. Your balance: ${receiver_balance}",
    "insufficient_funds": "§cInsufficient funds! You have ${balance} but need ${amount} (missing ${needed})"
  }
}
```

## Helper Utilities

### Placeholder Builder Pattern
```java
public class PlaceholderBuilder {
    private final Map<String, Object> placeholders = new HashMap<>();
    
    public static PlaceholderBuilder create() {
        return new PlaceholderBuilder();
    }
    
    public PlaceholderBuilder player(Player player) {
        placeholders.put("player", player.getName());
        placeholders.put("uuid", player.getUniqueId().toString());
        placeholders.put("world", player.getWorld().getName());
        placeholders.put("x", player.getLocation().getBlockX());
        placeholders.put("y", player.getLocation().getBlockY());
        placeholders.put("z", player.getLocation().getBlockZ());
        placeholders.put("health", Math.round(player.getHealth()));
        placeholders.put("level", player.getLevel());
        return this;
    }
    
    public PlaceholderBuilder add(String key, Object value) {
        placeholders.put(key, value);
        return this;
    }
    
    public PlaceholderBuilder location(Location loc) {
        placeholders.put("x", loc.getBlockX());
        placeholders.put("y", loc.getBlockY());
        placeholders.put("z", loc.getBlockZ());
        placeholders.put("world", loc.getWorld().getName());
        return this;
    }
    
    public PlaceholderBuilder time() {
        placeholders.put("time", new SimpleDateFormat("HH:mm").format(new Date()));
        placeholders.put("date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        placeholders.put("timestamp", System.currentTimeMillis());
        return this;
    }
    
    public Map<String, Object> build() {
        return new HashMap<>(placeholders);
    }
}

// Usage:
Map<String, Object> placeholders = PlaceholderBuilder.create()
    .player(player)
    .add("price", 100.0)
    .add("item", "Diamond Sword")
    .time()
    .build();
```

### Async Message Utility
```java
public class MessageUtil {
    private final Messages messages;
    
    public MessageUtil(Messages messages) {
        this.messages = messages;
    }
    
    public void sendMessage(Player player, String key, Map<String, Object> placeholders) {
        String language = getPlayerLanguage(player);
        messages.get(key, language, placeholders)
            .thenAccept(player::sendMessage)
            .exceptionally(error -> {
                player.sendMessage("§cError loading message: " + key);
                return null;
            });
    }
    
    public void broadcast(String key, Map<String, Object> placeholders) {
        messages.get(key, "en", placeholders)
            .thenAccept(Bukkit::broadcastMessage)
            .exceptionally(error -> {
                Bukkit.getLogger().warning("Failed to broadcast message: " + key);
                return null;
            });
    }
    
    public CompletableFuture<List<String>> getFormattedList(String key, String language, Map<String, Object> placeholders) {
        return messages.getList(key, language)
            .thenApply(list -> list.stream()
                .map(line -> formatPlaceholders(line, placeholders))
                .collect(Collectors.toList()));
    }
    
    private String formatPlaceholders(String text, Map<String, Object> placeholders) {
        String result = text;
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
```

## Best Practices

1. **Use Map placeholders for complex data** - More readable and maintainable than positional arguments
2. **Create reusable placeholder maps** - Cache common placeholders like player data
3. **Handle async properly** - Always use `.thenAccept()` or schedule back to main thread
4. **Provide fallback messages** - Use `.exceptionally()` to handle errors gracefully
5. **Use builder pattern** - Makes placeholder creation cleaner and more chainable
6. **Type your values appropriately** - Numbers, booleans, dates should be proper types
7. **Cache Messages instance** - Don't recreate it every time you need to send messages

This API provides maximum flexibility while maintaining backwards compatibility with existing code.