# ConfigManager API

Async-first configuration and messages access for Mongo-backed apps. Sync helpers exist, but avoid them on the main thread (Paper/Bukkit) to keep the server responsive.

## Getting an instance

```java
ConfigManager cm = MongoConfigsAPI.getConfigManager();
```

## Key–value storage (async)

- set(String id, T value): CompletableFuture<Void>
- get(String id, Class<T> type): CompletableFuture<T>

Example:

```java
cm.set("server.max_players", 150)
  .thenRun(() -> getLogger().info("saved"))
  .exceptionally(ex -> { getLogger().severe(ex.getMessage()); return null; });

cm.get("server.max_players", Integer.class)
  .thenAccept(max -> Bukkit.getScheduler().runTask(plugin, () -> server.setMaxPlayers(max)));
```

Helpers (blocking, avoid on main thread):

- save(String id, T value)
- load(String id, Class<T> type)

## POJO configs (async)

- getObject(Class<T> type): CompletableFuture<T>
- setObject(T pojo): CompletableFuture<Void>
- getConfigOrGenerate(Class<T> type, Supplier<T> generator): CompletableFuture<T>

Example:

```java
cm.getConfigOrGenerate(ServerConfig.class, () -> new ServerConfig().withDefaults())
  .thenAccept(cfg -> Bukkit.getScheduler().runTask(plugin, () -> apply(cfg)));

cm.getObject(ServerConfig.class)
  .thenApply(cfg -> { cfg.setMaintenanceMode(true); return cfg; })
  .thenCompose(cm::setObject);
```

Helpers (blocking, avoid on main thread):

- loadObject(Class<T> type)
- saveObject(T pojo)

## Messages

- findById(String id): Messages
- messagesOf(Class<?> messageClass): Messages (uses annotations to resolve id)
- getOrCreateFromObject(T messageObject): Messages
- createFromObject(T messageObject): void
- getOrCreateFromObjectAsync(T): CompletableFuture<Messages>

Usage:

```java
Messages msgs = cm.messagesOf(GuiMessages.class); // or cm.findById("gui-messages")
String lang = MongoConfigsAPI.getLanguageManager().getPlayerLanguage(player.getUniqueId().toString());
player.sendMessage(msgs.get(lang, "welcome.title", Map.of("name", player.getName())));
```

Auto-merge behavior (object-driven messages):

- When you call getOrCreateFromObject(...) or its async variant with a POJO, keys are extracted from the object:
  - fields: literal name
  - getters: camelCase becomes dotted.lowercase (e.g., getMainMenuTitle -> main.menu.title)
  - nested classes/objects and Map<String,?> are flattened into dotted keys
- Missing keys are automatically added to every supported language document. Existing translations are never overwritten.
- The merge runs asynchronously; the returned Messages handle is usable immediately.

See also: Messages-API for key mapping and placeholders.

## Reload

- reloadAll(): CompletableFuture<Void>

```java
cm.reloadAll().thenRun(() -> getLogger().info("configs reloaded"));
```

## Notes and best practices

- Prefer async methods; only hop back to the main thread for API calls that require it.
- Avoid join or blocking helpers on the server thread.
- Reuse Messages handles; fetch player language once per interaction when possible.
- Handle errors with exceptionally/handle to avoid silent failures.