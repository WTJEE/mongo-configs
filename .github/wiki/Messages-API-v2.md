# Messages API Reference

This page mirrors the public interfaces shipped in `configs-api`. Everything listed below is implemented in `ConfigManagerImpl`/`Messages` at runtime, so you can rely on it from any module (Paper, Velocity, or an embedded app).

## ConfigManager entry points

`ConfigManager` exposes the following methods for working with message bundles:

| Method | Description |
|--------|-------------|
| `getOrCreateFromObject(T defaults)` | Ensures the MongoDB document exists (seeded from your POJO) and returns a `Messages` facade backed by that data. |
| `createFromObject(T defaults)` | Seeds the POJO without returning a `Messages` facade. Useful for preloading defaults at startup. |
| `getMessageAsync(collection, language, key)` | Reads a single message string. |
| `getMessageAsync(..., String defaultValue)` | Same as above but falls back to a literal default if the key is missing. |
| `getMessageAsync(..., Object... placeholders)` | Fetches the message and performs positional placeholder replacement. |
| `getMessageAsync(..., Map<String, Object> placeholders)` | Fetches the message and replaces named `{placeholder}` tokens. |

Every method returns a `CompletableFuture`. Never call `join()` on the server thread—chain continuations or hop back to the main thread with your scheduler once the future completes.

## Messages facade

`Messages` is what `getOrCreateFromObject` returns. It mirrors the async surface of `ConfigManager` but scopes calls to a single document:

```java
CompletableFuture<String> get(String path);
CompletableFuture<String> get(String path, String language);
CompletableFuture<String> get(String path, Object... placeholders);
CompletableFuture<String> get(String path, String language, Object... placeholders);
CompletableFuture<String> get(String path, Map<String, Object> placeholders);
CompletableFuture<String> get(String path, String language, Map<String, Object> placeholders);
CompletableFuture<List<String>> getList(String path);
CompletableFuture<List<String>> getList(String path, String language);
```

It also ships with `Messages.View`. That helper caches a language selection and exposes synchronous `get/format/list` methods that internally call `join()`. Only use it from background threads or after you know the futures are fulfilled—`View` is a convenience wrapper, not a cache by itself.

## Language manager integration

`LanguageManager` (available through `MongoConfigsAPI.getLanguageManager()` inside the Paper and Velocity modules) lets you look up the player’s preferred language:

```java
languageManager.getPlayerLanguage(player.getUniqueId())
    .thenCompose(lang -> messages.get("general.playerJoined", lang))
    .thenAcceptAsync(msg -> player.sendMessage(ColorHelper.parseComponent(msg)), task -> Bukkit.getScheduler().runTask(this, () -> task.run()));
```

You can set a preference with `setPlayerLanguage(UUID,String)` and inspect the supported set with `getSupportedLanguages()`. The implementation is entirely asynchronous and backed by MongoDB.

## Putting it together

```java
public void sendJoinMessage(Player player) {
    ConfigManager configManager = MongoConfigsAPI.getConfigManager();
    LanguageManager languageManager = MongoConfigsAPI.getLanguageManager();

    configManager.getOrCreateFromObject(new PluginMessages())
        .thenCompose(messages -> languageManager.getPlayerLanguage(player.getUniqueId())
            .thenCompose(lang -> messages.get("general.playerJoined", lang,
                "player", player.getName(),
                "online", Bukkit.getOnlinePlayers().size())))
        .thenAccept(msg -> Bukkit.getScheduler().runTask(this,
            () -> player.sendMessage(ColorHelper.parseComponent(msg))));
}
```

All placeholder handling shown above happens in your code (e.g., simple `String#replace` or a helper utility). The API does not integrate with PlaceholderAPI automatically—if you need that, pass the resolved text through PlaceholderAPI yourself after the future completes.

## MongoDB structure

Documents stored by `Messages` are plain JSON with `_id` equal to the value of `@ConfigsFileProperties(name = "...")` and optional `lang` metadata if you maintain separate copies per language. Nested classes in your POJO become nested objects in the document.

```json
{
  "_id": "plugin-messages",
  "lang": "en",
  "general": {
    "playerJoined": "&a{player} joined!",
    "playerLeft": "&c{player} left."
  },
  "gui": {
    "title": "&aHelp Menu",
    "helpItem": {
      "name": "&eHelp",
      "lore": [
        "&7Click to see commands",
        "&7Players online: {online}"
      ]
    }
  }
}
```

## Best practices

- Keep everything async—switch to the main thread only when you touch Bukkit/Velocity objects.
- Reuse the same default POJO when calling `getOrCreateFromObject` so schema inference stays consistent.
- Prefer named placeholders (maps) over positional ones for maintainability.
- If you need synchronous access, precompute it off the main thread with `Messages.View` and store the result in your own cache.
- Call `configManager.reloadCollection("plugin-messages")` or `reloadAll()` when you edit documents directly in MongoDB to invalidate caches.
