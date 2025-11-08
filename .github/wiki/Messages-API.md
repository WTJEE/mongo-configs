# Messages API

## Overview
The Messages API provides a powerful, async-first system for managing localized messages from MongoDB. All operations are fully asynchronous with instant cache updates and real-time synchronization via Change Streams.

This page shows how to model your messages as plain Java objects (POJOs) so they can be synchronized with MongoConfigs. Following the pattern gives you type-safe defaults, lets writers work directly in MongoDB, and keeps lookups non-blocking. If you are unfamiliar with typed configs, skim [Creating Configs](Creating-Configs) first.

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
public final class HelpPlugin implements JavaPlugin {
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

## Reading values in handlers

```java
messages.get("gui.title").thenAccept(title -> gui.setTitle(color(title)));
messages.getList("commands.helpList").thenAccept(list -> list.forEach(player::sendMessage));
```

You can also fetch language-specific variants:

```java
messages.get("general.playerJoined", "pl", "player", player.getName());
```

Keep chaining futures when you interact with Bukkit/Velocity objects. If you really need synchronous access, create a `Messages.View` after the initial load. **All** `View` methods call `CompletableFuture#join`, so use them only after the data is cached and never on a busy game thread.

```java
Messages.View msg = messages.view(playerLanguage);
String joined = msg.format("general.playerJoined", player.getName());
List<String> motd = msg.list("general.motd");
player.sendMessage(color(joined));
motd.forEach(line -> player.sendMessage(color(line)));
```

`Messages.View` simply blocks until the cached value is available, which is handy for background tasks or precomputed menus. Prefer the asynchronous variants in player-facing code so you never stall the main loop.


If you must touch Bukkit API objects, use `Bukkit.getScheduler().runTask` inside the continuation to hop back to the main thread.

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

- Reuse the same `PluginMessages` instance when calling `getOrCreateFromObject` so schema discovery happens once.
- Avoid `join()` or `get()` on the main thread; chain futures or schedule sync callbacks to the game thread.
- Call `configManager.reloadCollection("help-messages")` or `reloadAll()` asynchronously if you need live updates; caches are warmed automatically.
- Prefer short, flat lists over deeply nested documents when you can; each nested class adds a small amount of reflection work only during initial sync.
- If you use custom colour processing, the Paper module wires in `BukkitColorProcessor`. Replace it once at startup via `configManager.setColorProcessor` if you need different formatting.

Continue with [Best Practices](Best-Practices) for production advice or jump ahead to the [Example Plugin](Example-Plugin).


