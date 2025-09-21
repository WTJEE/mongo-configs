# Placeholders Guide

## Overview
MongoDB Configs supports powerful placeholder system that lets you use variables like `{player}`, `{lang}`, `{amount}` etc. in your messages.

## How It Works

### In MongoDB Documents
Store messages with placeholders in MongoDB:
```json
{
  "_id": "messages_en",
  "lang": "en",
  "welcome": "Welcome {player}! You are in {world}.",
  "balance": "Your balance: ${amount}",
  "kill": "{killer} killed {victim} with {weapon}!",
  "teleport": "Teleported to {x}, {y}, {z} in {world}",
  "language_changed": "Language changed to {lang} ({langname})"
}
```

### In Your Code

#### Simple Usage
```java
// Basic placeholder replacement
configManager.getMessageAsync("messages", "en", "welcome")
    .thenAccept(msg -> {
        String formatted = msg.replace("{player}", player.getName())
                             .replace("{world}", player.getWorld().getName());
        player.sendMessage(formatted);
    });
```

#### Using MessageHelper (Recommended)
```java
MessageHelper messageHelper = new MessageHelper(configManager, languageManager);

// Simple placeholders
messageHelper.sendMessage(player, "messages", "welcome", 
    "player", player.getName(),
    "world", player.getWorld().getName()
);

// With map
Map<String, Object> placeholders = Map.of(
    "killer", killer.getName(),
    "victim", victim.getName(),
    "weapon", weapon.getType().name()
);
messageHelper.sendMessage(player, "messages", "kill", placeholders);

// Using builder
messageHelper.sendMessage(player, "messages", "teleport",
    MessageHelper.placeholders()
        .player(player)
        .location(targetLocation)
        .build()
);
```

## Automatic Placeholders

When using `MessageHelper`, these placeholders are automatically available:

| Placeholder | Description |
|------------|-------------|
| `{player}` | Player's name |
| `{displayname}` | Player's display name |
| `{uuid}` | Player's UUID |
| `{lang}` | Player's current language code |
| `{world}` | Player's current world |
| `{x}` | Player's X coordinate |
| `{y}` | Player's Y coordinate |
| `{z}` | Player's Z coordinate |
| `{health}` | Player's health |
| `{level}` | Player's level |
| `{exp}` | Player's total experience |

## Advanced Examples

### Economy Plugin
```java
@EventHandler
public void onPurchase(PurchaseEvent event) {
    Player player = event.getPlayer();
    
    messageHelper.sendMessage(player, "shop", "purchase_success",
        MessageHelper.placeholders()
            .player(player)
            .add("item", event.getItem().getName())
            .add("price", event.getPrice())
            .add("balance", economy.getBalance(player))
            .build()
    );
}
```

### Kill Messages
```java
@EventHandler
public void onPlayerDeath(PlayerDeathEvent event) {
    Player victim = event.getEntity();
    Player killer = victim.getKiller();
    
    if (killer != null) {
        // Broadcast kill message to all players
        messageHelper.broadcast("messages", "kill",
            Map.of(
                "killer", killer.getName(),
                "victim", victim.getName(),
                "weapon", killer.getInventory().getItemInMainHand().getType().name(),
                "distance", (int) killer.getLocation().distance(victim.getLocation())
            )
        );
    }
}
```

### Teleport Messages
```java
public void teleportPlayer(Player player, Location destination) {
    player.teleport(destination);
    
    messageHelper.sendMessage(player, "messages", "teleport_success",
        MessageHelper.placeholders()
            .player(player)
            .location(destination)
            .add("from_world", player.getWorld().getName())
            .build()
    );
}
```

### Time-based Placeholders
```java
public void showCooldown(Player player, long remainingSeconds) {
    messageHelper.sendMessage(player, "messages", "cooldown",
        MessageHelper.placeholders()
            .player(player)
            .time(remainingSeconds) // Adds {time}, {seconds}, {minutes}, {hours}
            .build()
    );
}
```

## MongoDB Message Examples

### Shop Messages
```json
{
  "_id": "shop_en",
  "lang": "en",
  "purchase_success": "§a✓ Purchased {item} for ${price}. Balance: ${balance}",
  "insufficient_funds": "§cYou need ${needed} more to buy {item}!",
  "shop_title": "§6{player}'s Shop - Page {page}/{total}"
}
```

### Combat Messages
```json
{
  "_id": "combat_en",
  "lang": "en",
  "kill": "§c{killer} §7killed §c{victim} §7with {weapon} from {distance}m",
  "assist": "§7You assisted in killing {victim} (+{points} points)",
  "combat_tag": "§cCombat tagged for {time}! Don't logout!"
}
```

### Teleport Messages
```json
{
  "_id": "teleport_en",
  "lang": "en",
  "teleport_request": "{player} wants to teleport to you. Type /tpaccept to accept.",
  "teleport_success": "§aTeleported to {x}, {y}, {z} in {world}",
  "teleport_cooldown": "§cPlease wait {time} before teleporting again."
}
```

## Format Support

Both formats are supported:
- `{placeholder}` - Curly braces (recommended)
- `%placeholder%` - Percent signs (legacy support)

## Performance Tips

1. **Cache MessageHelper Instance**
```java
public class MyPlugin extends JavaPlugin {
    private MessageHelper messageHelper;
    
    @Override
    public void onEnable() {
        messageHelper = new MessageHelper(configManager, languageManager);
    }
}
```

2. **Precompute Placeholders**
```java
// Good - compute once
Map<String, Object> placeholders = computePlaceholders(player);
for (String message : messages) {
    messageHelper.sendMessage(player, "messages", message, placeholders);
}

// Bad - recompute each time
for (String message : messages) {
    messageHelper.sendMessage(player, "messages", message,
        computePlaceholders(player) // Wasteful
    );
}
```

3. **Use Async Operations**
```java
// Process multiple messages in parallel
CompletableFuture.allOf(
    messageHelper.sendMessage(player, "messages", "line1", placeholders),
    messageHelper.sendMessage(player, "messages", "line2", placeholders),
    messageHelper.sendMessage(player, "messages", "line3", placeholders)
).thenRun(() -> {
    // All messages sent
});
```

## PlaceholderAPI Integration

Message sending (MessageHelper) and GUI both resolve PlaceholderAPI tokens when the PAPI plugin is present. That means any `%...%` used in your MongoDB messages or GUI texts is evaluated per-player asynchronously.

Examples:
```yaml
# Works in GUI titles/names/lores and in messages sent via MessageHelper
Welcome: "%player_name%, your balance is %vault_eco_balance%"
```

Under the hood:
- First we apply your own placeholders (e.g., {player}, {amount})
- Then we pass the result through PlaceholderAPI for dynamic tokens
- Everything runs async; only the final send to the player happens on the main thread

For external plugins using PlaceholderAPI:
- `%mongoconfigs_message_<collection>_<key>%` - Gets message with auto placeholders
- `%mongoconfigs_config_<collection>_<key>%` - Gets config value

These work in any plugin supporting PlaceholderAPI (scoreboards, TAB, etc.)