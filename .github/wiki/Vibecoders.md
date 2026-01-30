# Vibecoders Quick Reference

> **🤖 For AI Assistants**: This is a quick reference. For complete documentation with detailed patterns, see **[AGENTS.md](../../AGENTS.md)** in the repository root.

Snapshot of how MongoConfigs behaves so AI/code copilots can reason about the project without re-reading the entire wiki. Everything below reflects the current API inside `configs-api`, `configs-core`, `configs-paper`, and `configs-velocity`.

## ⚠️ CRITICAL RULES (Never Break These!)

```java
// ❌ NEVER DO THIS - Blocks main thread!
String msg = messages.get("key").join();
String msg = messages.view("pl").get("key");  // uses .join() internally!

// ✅ ALWAYS DO THIS - Async non-blocking
messages.use("key", msg -> player.sendMessage(msg));
messages.get("key").thenAccept(msg -> player.sendMessage(msg));
```

**Rule #1**: Never call `.join()` or `.get()` on `CompletableFuture` from the main thread  
**Rule #2**: Always use `MessageService` wrapper pattern  
**Rule #3**: Always preload language in `PlayerJoinEvent`  
**Rule #4**: Use `Messages.View` for multiple messages to the same player  
**Rule #5**: Use consumer-based methods (`use()`) instead of `thenAccept()` when possible

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
          // use config
      });
  ```
- **Per-document configs** (one doc per arena/player/economy row):
  ```java
  record Arena(String id, String displayName, int minPlayers) {}

  String arenaId = "arena-1";
  configManager.getConfigOrGenerate(arenaId, Arena.class,
          () -> new Arena(arenaId, "Default Arena", 2))
      .thenAccept(arena -> {
          // use arena
      });
  ```
- Need raw objects? `configManager.set("state", new MaintenanceState(...))` / `get("state", MaintenanceState.class)`.
- Caches warm asynchronously; use `reloadCollection("collection")` or `reloadAll()` after manual DB edits. Change streams keep caches hot when enabled in `MongoConfig`.

## Messages & localization

```java
Messages messages;
configManager.getOrCreateFromObject(new PluginMessages())
    .thenAccept(msgs -> messages = msgs);
```

- Builders supply default `_id` via `@ConfigsFileProperties`.
- **Async usage (CORRECT)**:
  ```java
  messages.get("general.playerJoined", lang, "player", player.getName())
      .thenAccept(msg -> runSync(() -> player.sendMessage(color(msg))));
  ```
- **Consumer-based (RECOMMENDED)**:
  ```java
  messages.use("general.playerJoined", lang, msg -> player.sendMessage(msg));
  ```
- `Messages.View` wraps `join()`. **Only use it off-thread or after values are cached.**
  ```java
  // ❌ BAD - blocks if not cached
  String msg = messages.view("pl").get("key");
  
  // ✅ GOOD - async callback
  messages.view("pl").use("key", msg -> player.sendMessage(msg));
  ```

## Language manager

```java
LanguageManager languageManager = MongoConfigsAPI.getLanguageManager();

// Get language and use it
languageManager.usePlayerLanguage(player.getUniqueId(), lang -> {
    messages.use("general.welcome", lang, msg -> player.sendMessage(msg));
});

// Or chain futures
languageManager.getPlayerLanguage(player.getUniqueId())
    .thenCompose(lang -> messages.get("general.welcome", lang))
    .thenAccept(msg -> player.sendMessage(msg));

languageManager.setPlayerLanguage(player.getUniqueId(), "pl");
languageManager.getSupportedLanguages().thenAccept(list -> ...);
```

## MessageService Pattern (USE THIS!)

```java
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

    public Messages.View view(Player player) {
        return messages.view(getLanguage(player));
    }

    public void preloadLanguage(UUID playerId) {
        langManager.usePlayerLanguage(playerId, lang -> 
            languageCache.put(playerId, lang != null ? lang : defaultLanguage)
        );
    }

    public void removePlayer(UUID playerId) {
        languageCache.remove(playerId);
    }

    public String getLanguage(Player player) {
        return languageCache.getOrDefault(player.getUniqueId(), defaultLanguage);
    }
}
```

## PlayerListener Pattern

```java
public class PlayerListener implements Listener {
    private final MessageService msg;

    public PlayerListener(MessageService msg) {
        this.msg = msg;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // ✅ ALWAYS preload language
        msg.preloadLanguage(player.getUniqueId());
        
        // ✅ Send message with placeholders
        msg.send(player, "general.welcome", 
            Map.of("player", player.getName()),
            player::sendMessage);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // ✅ Cleanup cache
        msg.removePlayer(event.getPlayer().getUniqueId());
    }
}
```

## Placeholders

- Raw API: pass placeholders directly.
  ```java
  Map<String, Object> placeholders = Map.of("player", player.getName(), "kills", 12);
  messages.get("stats.kills", lang, placeholders)
      .thenAccept(msg -> player.sendMessage(msg));
  ```
- Format: `{placeholder}` in message strings
- Named placeholders are preferred over positional ones.

## POJO Structure

```java
@ConfigsFileProperties(name = "my-plugin-messages")
@SupportedLanguages({"en", "pl"})
public class MyMessages {
    public General general = new General();
    
    public static class General {
        public String welcome = "&aWelcome {player}!";
        public String noPermission = "&cNo permission!";
        public List<String> rules = List.of("Rule 1", "Rule 2");
    }
}
```

**Path mapping**: `MyMessages.general.welcome` → `"general.welcome"`

## Workflow checklist

1. (Paper/Velocity) let the module load `config.yml` / `languages.yml`; grab managers from `MongoConfigsAPI` inside `onEnable`.
2. Pre-seed configs with `getConfigOrGenerate(...)` (class-based or per-ID) before accessing them.
3. **Create MessageService wrapper** - never use Messages directly in multiple places.
4. **Register PlayerListener** that preloads language on join and cleans up on quit.
5. Use consumer-based methods (`use()`) instead of chaining `thenAccept` when possible.
6. For AI tooling, always mention whether you need annotation-based or per-ID storage, and include placeholder maps when formatting strings.

## Quick Links

- **[AGENTS.md](../../AGENTS.md)** - Complete AI documentation with all patterns
- [Installation](Installation) - Setup guide
- [Messages API](Messages-API) - Detailed message documentation
- [Example Plugin](Example-Plugin) - Full working example

Use this page as the canonical answer when assistants ask "how do configs/languages/messages/placeholders work here?"—link to the detailed pages for more context or edge cases.
