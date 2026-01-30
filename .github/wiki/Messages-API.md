# Messages API

> **🤖 For AI Assistants**: This page explains the concepts. For complete coding patterns and rules, see **[AGENTS.md](../../AGENTS.md)**.

## Overview

The Messages API provides a powerful, async-first system for managing localized messages from MongoDB. All operations are fully asynchronous with instant cache updates and real-time synchronization via Change Streams.

**⚠️ CRITICAL**: All operations are **non-blocking** using Java 21+ Virtual Threads. Never use `.join()` or `.get()` on the main thread!

## Define the POJO

Create a class that mirrors the structure you want to expose through the API. Annotate it so MongoConfigs knows how to store the document and which languages you maintain.

```java
package xyz.wtje.nauka.messages;

import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

import java.util.List;

@ConfigsFileProperties(name = "help-messages")
@SupportedLanguages({"en", "pl"})
public class PluginMessages {

    public GUI gui = new GUI();
    public Commands commands = new Commands();
    public General general = new General();

    public static class GUI {
        public String title = "&aHelp Menu";
        public Items items = new Items();

        public static class Items {
            public HelpItem help = new HelpItem();
            public SettingsItem settings = new SettingsItem();

            public static class HelpItem {
                public String name = "&eHelp";
                public List<String> lore = List.of(
                        "&7Click to see commands",
                        "&7Welcome, {player}!"
                );
            }

            public static class SettingsItem {
                public String name = "&bSettings";
                public List<String> lore = List.of(
                        "&7Change your preferences",
                        "&7Current lang: {lang}"
                );
            }
        }
    }

    public static class Commands {
        public String helpUsage = "&7Use: /help [page]";
        public String noPermission = "&cYou don't have permission!";
        public List<String> helpList = List.of(
                "&e/spawn &7- Teleport to spawn",
                "&e/home &7- Go to your home",
                "&e/sethome &7- Set your home"
        );
    }

    public static class General {
        public String prefix = "&8[&aServer&8] ";
        public String playerJoined = "&a{player} joined the game!";
        public String playerLeft = "&c{player} left the game!";
        public List<String> motd = List.of(
                "&6Welcome to {server}!",
                "&7Enjoy your stay."
        );
    }
}
```

### Mapping rules

- Public fields become keys using dot notation (`gui.items.help.name`).
- Nested classes let you keep related strings grouped without writing extra configuration files.
- Use `List.of` for lore or multi-line text so defaults stay immutable.
- Stick to simple types (`String` and `List<String>`) to minimise serialization overhead.

## Bootstrapping the messages

When you depend on the Paper plugin module, the `ConfigManager` is already configured from `config.yml`. Read it from `MongoConfigsAPI` and register your defaults.

```java
public final class HelpPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private Messages messages;

    @Override
    public void onEnable() {
        this.configManager = MongoConfigsAPI.getConfigManager();

        PluginMessages defaults = new PluginMessages();
        configManager.getOrCreateFromObject(defaults)
            .thenAccept(msgs -> {
                this.messages = msgs;
                getLogger().info("Help messages ready");
            })
            .exceptionally(throwable -> {
                getLogger().severe("Could not load help messages: " + throwable.getMessage());
                return null;
            });
    }
}
```

Embedding the library outside Paper works the same way: instantiate `ConfigManagerImpl` with your `MongoConfig`, call `getOrCreateFromObject`, and handle the `CompletableFuture` asynchronously just like the server modules do.

## Reading values (NON-BLOCKING!)

### Method 1: Consumer-based (RECOMMENDED)

```java
// Simple message
messages.use("gui.title", title -> gui.setTitle(color(title)));

// With language
messages.use("general.playerJoined", "pl", msg -> player.sendMessage(msg));

// With placeholders (varargs: key, value, key, value...)
messages.useFormat("general.playerJoined", "pl", 
    msg -> player.sendMessage(msg),
    "player", player.getName(),
    "online", Bukkit.getOnlinePlayers().size());

// List of messages
messages.useList("commands.helpList", list -> 
    list.forEach(player::sendMessage));
```

### Method 2: CompletableFuture

```java
messages.get("gui.title").thenAccept(title -> gui.setTitle(color(title)));
messages.getList("commands.helpList").thenAccept(list -> list.forEach(player::sendMessage));

// With language
messages.get("general.playerJoined", "pl", "player", player.getName())
    .thenAccept(msg -> player.sendMessage(msg));

// With Map placeholders
messages.get("general.playerJoined", "pl", 
        Map.of("player", player.getName(), "server", "Lobby"))
    .thenAccept(msg -> player.sendMessage(msg));
```

### Method 3: View API (for multiple messages)

```java
// Create view for a specific language
Messages.View view = messages.view(playerLanguage);

// Use multiple times - reuses the language
view.use("motd.line1", player::sendMessage);
view.use("motd.line2", player::sendMessage);
view.use("motd.line3", player::sendMessage);

// Async futures
view.future("welcome.message").thenAccept(msg -> player.sendMessage(msg));

// Lists
view.listFuture("rules").thenAccept(rules -> rules.forEach(player::sendMessage));
```

**⚠️ WARNING**: `Messages.View#get/format/list` call `CompletableFuture#join()` internally. Only use them from background threads or after you know the data is cached. **Never use on the main thread during player interactions!**

```java
// ❌ BAD - Can block main thread!
String msg = messages.view("pl").get("welcome.message");

// ✅ GOOD - Async callback
messages.view("pl").use("welcome.message", msg -> player.sendMessage(msg));
```

## Full Example: PlayerJoinEvent

```java
@EventHandler
public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    
    // Get language and send messages - all async!
    langManager.usePlayerLanguage(player.getUniqueId(), lang -> {
        Messages.View view = messages.view(lang);
        
        view.use("join.welcome", msg -> player.sendMessage(msg));
        view.use("join.motd", msg -> player.sendMessage(msg));
        
        // List of rules
        view.useList("rules", rules -> 
            rules.forEach(player::sendMessage));
    });
}
```

## Fetching Typed Language Classes

When you need the full message object for a particular locale, call the asynchronous helpers on `ConfigManager`:

```java
configManager.getLanguageClass(PluginMessages.class, "pl")
    .thenAccept(polish -> {
        String title = polish.gui.title;
        // use the translated values
    });
```

`getLanguageClass` resolves a single language, merging stored data with the defaults defined in your POJO. To process every language at once, use `getLanguageClasses` which returns a map keyed by language code:

```java
configManager.getLanguageClasses(PluginMessages.class)
    .thenAccept(classes -> classes.forEach((code, bundle) -> {
        getLogger().info("Loaded " + code + " title -> " + bundle.gui.title);
    }));
```

Both methods complete on worker threads and never touch the main server thread. They fall back to the default language when a translation is missing, so your command handlers always receive a fully populated object.

## Keeping it fast

- **Use `messages.use()`** instead of `messages.get().thenAccept()` for cleaner code.
- **Reuse Views** when sending multiple messages to the same player.
- **Preload language** in PlayerJoinEvent to avoid lookups during gameplay.
- **Use MessageService wrapper** - see [AGENTS.md](../../AGENTS.md) for the pattern.
- Reuse the same `PluginMessages` instance when calling `getOrCreateFromObject` so schema discovery happens once.
- Avoid `join()` or `get()` on the main thread; chain futures or schedule sync callbacks to the game thread.
- Call `configManager.reloadCollection("help-messages")` or `reloadAll()` asynchronously if you need live updates; caches are warmed automatically.
- Prefer short, flat lists over deeply nested documents when you can; each nested class adds a small amount of reflection work only during initial sync.
- If you use custom colour processing, the Paper module wires in `BukkitColorProcessor`. Replace it once at startup via `configManager.setColorProcessor` if you need different formatting.

Continue with [Best Practices](Best-Practices) for production advice or jump ahead to the [Example Plugin](Example-Plugin).
