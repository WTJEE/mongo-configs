# Example Plugin

> **🤖 For AI Assistants**: This is a basic example. For complete patterns including MessageService wrapper, see **[AGENTS.md](../../AGENTS.md)**.

This example shows how a Paper plugin consumes MongoConfigs when the `mongo-configs-paper` module is present. The Paper module reads `config.yml` and provides ready-to-use managers through `MongoConfigsAPI`, so you never touch the builder yourself.

## ⚠️ Important Note

**Never use `.join()` or `.get()` on the main thread!** All MongoConfigs operations are async. Use `thenAccept()` or consumer-based methods (`use()`).

## plugin.yml

```yaml
name: ExamplePlugin
version: 1.0.0
main: xyz.wtje.example.ExamplePlugin
api-version: '1.20'
depend: [MongoConfigs]
```

Declaring the dependency guarantees the managers are initialised before your plugin enables.

## Source layout

```
src/main/java/
 └─ xyz/wtje/example/
    ├─ ExamplePlugin.java
    ├─ config/
    │   └─ PluginSettings.java
    ├─ messages/
    │   └─ PluginMessages.java
    └─ service/
        └─ MessageService.java    # Recommended: wrapper service
```

## PluginSettings.java

```java
package xyz.wtje.example.config;

import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;

@ConfigsFileProperties(name = "plugin-settings")
public class PluginSettings {
    public boolean maintenanceMode = false;
    public int broadcastIntervalSeconds = 180;
}
```

## PluginMessages.java

```java
package xyz.wtje.example.messages;

import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

import java.util.List;

@ConfigsFileProperties(name = "plugin-messages")
@SupportedLanguages({"en", "pl"})
public class PluginMessages {
    public General general = new General();

    public static class General {
        public String prefix = "&8[&aExample&8] ";
        public String toggleOn = "&aMaintenance enabled";
        public String toggleOff = "&cMaintenance disabled";
        public List<String> status = List.of(
            "&7Maintenance: {state}",
            "&7Players online: {online}"
        );
    }
}
```

## MessageService.java (Recommended Pattern)

```java
package xyz.wtje.example.service;

import org.bukkit.entity.Player;
import xyz.wtje.mongoconfigs.api.LanguageManager;
import xyz.wtje.mongoconfigs.api.Messages;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class MessageService {
    private final Messages messages;
    private final LanguageManager langManager;
    private final String defaultLanguage;
    private final Map<UUID, String> languageCache = new ConcurrentHashMap<>();

    public MessageService(Messages messages, LanguageManager langManager, String defaultLanguage) {
        this.messages = messages;
        this.langManager = langManager;
        this.defaultLanguage = defaultLanguage;
    }

    public void send(Player player, String path, Consumer<String> action) {
        messages.use(path, getLanguage(player), action);
    }

    public void send(Player player, String path, Map<String, Object> placeholders, Consumer<String> action) {
        messages.get(path, getLanguage(player), placeholders).thenAccept(action);
    }

    public void sendList(Player player, String path, Consumer<List<String>> action) {
        messages.getList(path, getLanguage(player)).thenAccept(action);
    }

    public void preloadLanguage(UUID playerId) {
        langManager.usePlayerLanguage(playerId, lang -> 
            languageCache.put(playerId, lang != null ? lang : defaultLanguage));
    }

    public void removePlayer(UUID playerId) {
        languageCache.remove(playerId);
    }

    private String getLanguage(Player player) {
        return languageCache.getOrDefault(player.getUniqueId(), defaultLanguage);
    }
}
```

## ExamplePlugin.java

```java
package xyz.wtje.example;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.LanguageManager;
import xyz.wtje.mongoconfigs.api.Messages;
import xyz.wtje.mongoconfigs.api.MongoConfigsAPI;
import xyz.wtje.example.config.PluginSettings;
import xyz.wtje.example.messages.PluginMessages;
import xyz.wtje.example.service.MessageService;

public final class ExamplePlugin extends JavaPlugin {
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private PluginSettings settings;
    private MessageService messageService;

    @Override
    public void onEnable() {
        this.configManager = MongoConfigsAPI.getConfigManager();
        this.languageManager = MongoConfigsAPI.getLanguageManager();

        // Load settings
        configManager.getConfigOrGenerate(PluginSettings.class, PluginSettings::new)
            .thenAccept(loaded -> {
                this.settings = loaded;
                getLogger().info("Loaded maintenance toggle=" + loaded.maintenanceMode);
            })
            .exceptionally(throwable -> {
                getLogger().severe("Failed to load settings: " + throwable.getMessage());
                return null;
            });

        // Load messages and create service
        configManager.getOrCreateFromObject(new PluginMessages())
            .thenAccept(msgs -> {
                this.messageService = new MessageService(msgs, languageManager, "en");
                getLogger().info("Messages loaded!");
                registerListeners();
            })
            .exceptionally(throwable -> {
                getLogger().severe("Failed to load messages: " + throwable.getMessage());
                return null;
            });
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
            new PlayerListener(messageService), this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"maintenance".equalsIgnoreCase(command.getName())) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can toggle maintenance");
            return true;
        }

        // Toggle maintenance
        settings.maintenanceMode = !settings.maintenanceMode;
        configManager.setObject(settings); // async, no blocking

        // Send message using service
        String key = settings.maintenanceMode ? "general.toggleOn" : "general.toggleOff";
        messageService.send(player, key, player::sendMessage);

        return true;
    }
}
```

## PlayerListener.java

```java
package xyz.wtje.example;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import xyz.wtje.example.service.MessageService;

import java.util.Map;

public class PlayerListener implements Listener {
    private final MessageService msg;

    public PlayerListener(MessageService msg) {
        this.msg = msg;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        
        // Preload language (CRITICAL!)
        msg.preloadLanguage(player.getUniqueId());
        
        // Send welcome message with placeholders
        msg.send(player, "general.playerJoined",
            Map.of("player", player.getName()),
            player::sendMessage);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Cleanup cache
        msg.removePlayer(event.getPlayer().getUniqueId());
    }
}
```

## Flow

1. `MongoConfigs` loads first, reads `config.yml`, and initialises the managers on worker pools defined in the YAML.
2. Your plugin fetches the managers through `MongoConfigsAPI` and schedules async loads with `getConfigOrGenerate` / `getOrCreateFromObject`.
3. Command handlers use `MessageService` to send messages asynchronously.
4. PlayerListener preloads language on join and cleans up on quit.

## Key Points

- **All MongoConfigs operations are async** - never block the main thread
- **Use MessageService wrapper** - don't use Messages directly everywhere
- **Preload language on join** - prevents lag during gameplay
- **Cleanup on quit** - prevents memory leaks
- **Use consumer-based methods** (`use()`) for cleaner code than `thenAccept()`

For more details and complete patterns, see **[AGENTS.md](../../AGENTS.md)**.
