# Best Practices

Follow these recommendations to keep MongoConfigs responsive and maintainable in production environments.

> **🤖 For AI Assistants**: For complete coding patterns and detailed examples, see **[AGENTS.md](../../AGENTS.md)**.

## Threading and Async (CRITICAL!)

- **NEVER block the main thread!** Treat every API call as asynchronous. Chain continuations with `thenAccept`/`thenCompose` or use consumer-based methods (`use()`).
- **Never block on `CompletableFuture#get` or `join`** in the server thread; it can freeze the tick loop.
- **`Messages.View#get/format/list` call `CompletableFuture#join`**. Only use the view from worker threads or after you have preloaded data. **Never on player interactions!**
- **Use consumer-based methods**: Prefer `messages.use("key", consumer)` over `messages.get("key").thenAccept(consumer)`.
- **Preload language on join**: Always call `messageService.preloadLanguage(player.getUniqueId())` in `PlayerJoinEvent`.
- **Cleanup on quit**: Call `messageService.removePlayer(player.getUniqueId())` in `PlayerQuitEvent` to prevent memory leaks.
- **Use MessageService wrapper**: Don't use Messages directly in multiple places. Create a service class.

### ❌ BAD - Blocks main thread
```java
String msg = messages.get("welcome").join();  // NEVER!
String msg = messages.view("pl").get("welcome");  // uses .join() internally!
```

### ✅ GOOD - Non-blocking
```java
messages.use("welcome", msg -> player.sendMessage(msg));

// Or with CompletableFuture
messages.get("welcome").thenAccept(msg -> player.sendMessage(msg));
```

## Schema management

- Keep POJOs small and focused. Split unrelated concerns into separate classes and collections.
- Prefer the ID-based helpers (`getConfigOrGenerate(id, ...)`) for high-cardinality data such as players or arenas so each record stays isolated.
- Use `@SupportedLanguages` only for languages you actually serve—extra entries trigger unnecessary lookups.
- Version large structural changes by renaming the `@ConfigsFileProperties` value so you can migrate old data gradually.

## Performance tuning

- Configure cache TTL and size in `MongoConfig` to match your workload. Set `cacheRecordStats` to true temporarily when diagnosing hit rates.
- Call `setColorProcessor` once on start-up if you perform colour translation (e.g. MiniMessage). The message formatter reuses it for every lookup.
- Monitor the MongoDB server for index and throughput metrics; add indexes on `_id` plus custom keys if you use `TypedConfigManager#set`/`get` heavily.
- Prefer batching reloads with `reloadAll()` during scheduled maintenance instead of hammering the database per-request.

## Recommended Architecture

```
MyPlugin
├── config/
│   └── MyConfig.java          # @ConfigsFileProperties
├── messages/
│   └── MyMessages.java        # @ConfigsFileProperties + @SupportedLanguages
├── service/
│   └── MessageService.java    # Wrapper around Messages
└── listener/
    └── PlayerListener.java    # Preloads language on join
```

### MessageService Pattern

Always create a MessageService wrapper:

```java
public final class MessageService {
    private final Messages messages;
    private final LanguageManager langManager;
    private final Map<UUID, String> languageCache = new ConcurrentHashMap<>();
    
    public void send(Player player, String path, Consumer<String> action) {
        messages.use(path, getLanguage(player), action);
    }
    
    public void preloadLanguage(UUID playerId) {
        langManager.usePlayerLanguage(playerId, lang -> 
            languageCache.put(playerId, lang != null ? lang : defaultLanguage));
    }
    
    public void removePlayer(UUID playerId) {
        languageCache.remove(playerId);
    }
}
```

## Operations and tooling

- Run `reloadCollection("name")` after editing documents directly in MongoDB to invalidate caches hot.
- Couple configuration changes with automated tests. Store representative POJOs in your test sources and assert round-trip correctness using the API module.
- Document placeholders (e.g. `{player}`, `{lang}`) near the fields so translators know which tokens are available.

## Complete Checklist

- [ ] Created POJO with `@ConfigsFileProperties`
- [ ] Added `@SupportedLanguages` for message POJOs
- [ ] Created MessageService wrapper
- [ ] Preloading language in PlayerJoinEvent
- [ ] Cleaning up in PlayerQuitEvent
- [ ] Using `messages.use()` instead of `.get().thenAccept()`
- [ ] Never using `.join()` or `.get()` on main thread
- [ ] Using View API for multiple messages
- [ ] Using Map.of() for placeholders
- [ ] Added `depend: [MongoConfigs]` in plugin.yml

Continue to the [Example Plugin](Example-Plugin) for an end-to-end scenario or see **[AGENTS.md](../../AGENTS.md)** for complete AI documentation.
