# Vibecoders Quick Reference

Snapshot of how MongoConfigs behaves so AI/code copilots can reason about the project without re-reading the entire wiki. Everything below reflects the current API inside `configs-api`, `configs-core`, `configs-paper`, and `configs-velocity`.

## Config fundamentals

```java
ConfigManager configManager = MongoConfigsAPI.getConfigManager();
```

- Every method is async (`CompletableFuture`). Chain `.thenAccept/.thenCompose`; do not `join()` on the main server thread.
- **Annotation-driven configs** (single document):
  ```java
  @ConfigsFileProperties(name = "practice-config")
  public class PracticeConfig { public boolean enabled = true; }

  configManager.getConfigOrGenerate(PracticeConfig.class, PracticeConfig::new)
      .thenAccept(config -> {
          PracticeConfig updated = new PracticeConfig();
          updated.enabled = false;
          configManager.setObject(updated);
      });
  ```
- **Per-document configs** (one doc per arena/player/economy row):
  ```java
  record Arena(String id, String displayName, int minPlayers) {}

  String arenaId = "arena-1";
  configManager.getConfigOrGenerate(arenaId, Arena.class,
          () -> new Arena(arenaId, "Default Arena", 2))
      .thenAccept(arena -> {
          Arena tweaked = new Arena(arena.id(), "Nether", 4);
          configManager.setObject(arena.id(), tweaked);
      });
  ```
- Need raw objects? `configManager.set("state", new MaintenanceState(...))` / `get("state", MaintenanceState.class)`.
- Caches warm asynchronously; use `reloadCollection("collection")` or `reloadAll()` after manual DB edits. Change streams keep caches hot when enabled in `MongoConfig`.

## Messages & localization

```java
Messages messages;
configManager.getOrCreateFromObject(new PluginMessages())
    .thenAccept(view -> messages = view);
```

- Builders supply default `_id` via `@ConfigsFileProperties`.
- Async usage:
  ```java
  messages.get("general.playerJoined", lang, "player", player.getName())
      .thenAccept(msg -> runSync(() -> player.sendMessage(color(msg))));
  ```
- `Messages.View` wraps `join()`. Only use it off-thread or after values are cached.
- Standalone apps: create `MongoConfig`, pass into `ConfigManagerImpl`, then call the same API.

## Language manager

```java
LanguageManager languageManager = MongoConfigsAPI.getLanguageManager();

languageManager.getPlayerLanguage(player.getUniqueId())
    .thenCompose(lang -> messages.get("general.welcome", lang))
    .thenAccept(msg -> runSync(() -> player.sendMessage(color(msg))));

languageManager.setPlayerLanguage(player.getUniqueId(), "pl");
languageManager.getSupportedLanguages().thenAccept(list -> ...);
languageManager.getLanguageDisplayName("pl").thenAccept(name -> ...);
```

- Entirely async; stores data in the Mongo collection defined by `player-languages` settings.
- Paper & Velocity share the same Mongo configuration; `player_languages` is ignored by change streams automatically.

## Placeholders & helpers

- Raw API: pass placeholders directly.
  ```java
  Map<String, Object> placeholders = Map.of("player", player.getName(), "kills", 12);
  configManager.getMessageAsync("messages", lang, "stats.kills", placeholders)
      .thenAccept(msg -> runSync(() -> player.sendMessage(color(msg))));
  ```
- On Paper, `MessageHelper` handles language lookup + core placeholders:
  ```java
  MessageHelper helper = new MessageHelper(configManagerImpl, languageManagerImpl);
  helper.sendMessage(player, "messages", "arena.joined",
      MessageHelper.placeholders().player(player).add("arena", arenaName).build());
  ```
- `{placeholder}` and `%placeholder%` tokens are both supported; PlaceholderAPI is *not* invoked automatically.

## Workflow checklist

1. (Paper/Velocity) let the module load `config.yml` / `languages.yml`; grab managers from `MongoConfigsAPI` inside `onEnable`.
2. Pre-seed configs with `getConfigOrGenerate(...)` (class-based or per-ID) before accessing them.
3. For player commands, chain futures and bounce back to the main thread only for API calls.
4. Use `LanguageManager` to resolve player language before hitting `Messages`.
5. For AI tooling, always mention whether you need annotation-based or per-ID storage, and include placeholder maps when formatting strings.

Use this page as the canonical answer when assistants ask “how do configs/languages/messages/placeholders work here?”—link to the detailed pages for more context or edge cases.
