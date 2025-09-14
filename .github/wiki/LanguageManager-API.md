# LanguageManager API

Complete reference for the LanguageManager - the interface for managing multilingual player preferences and language data.

## 🎯 Getting LanguageManager Instance

```java
// Get the global LanguageManager instance
LanguageManager lm = MongoConfigsAPI.getLanguageManager();
```

---

## 👤 Player Language Management

### Setting Player Language
# LanguageManager API

Manage player language preferences and server-supported languages. Prefer async writes; reads are cheap and return quickly with a default when not set.

## Getting an instance

```java
LanguageManager lm = MongoConfigsAPI.getLanguageManager();
```

## Player language

- getPlayerLanguage(String playerId): String
  - Returns the stored code or null; when using it, fall back to default.
- getPlayerLanguageAsync(String playerId): CompletableFuture<String>
- setPlayerLanguage(String playerId, String language): void
- setPlayerLanguageAsync(String playerId, String language): CompletableFuture<Void>
- setPlayerLanguage(UUID playerId, String language): CompletableFuture<Void>

Examples:

```java
String playerId = player.getUniqueId().toString();
String lang = lm.getPlayerLanguage(playerId);
if (lang == null) lang = lm.getDefaultLanguage();

lm.setPlayerLanguageAsync(playerId, "pl")
  .thenRun(() -> player.sendMessage("Language set to PL"));

lm.setPlayerLanguage(player.getUniqueId(), "en")
  .thenRun(() -> player.sendMessage("Language set to EN"));
```

Validate before setting:

```java
String desired = args[0].toLowerCase();
if (!lm.isLanguageSupported(desired)) {
  sender.sendMessage("Unsupported language");
  return true;
}
lm.setPlayerLanguageAsync(player.getUniqueId().toString(), desired);
```

## Server languages

- getDefaultLanguage(): String
- getSupportedLanguages(): String[]
- isLanguageSupported(String language): boolean
- getLanguageDisplayName(String language): String

Example:

```java
String def = lm.getDefaultLanguage();
String[] all = lm.getSupportedLanguages();
sender.sendMessage("Default: " + def + ", supported: " + String.join(", ", all));
```

## With Messages

```java
Messages msgs = MongoConfigsAPI.getConfigManager().messagesOf(GuiMessages.class);
String lang = lm.getPlayerLanguage(player.getUniqueId().toString());
if (lang == null) lang = lm.getDefaultLanguage();
player.sendMessage(msgs.get(lang, "welcome.title", Map.of("name", player.getName())));
```

Notes:

- Use String playerId in most places; a UUID overload exists for convenience.
- Reads are non-blocking and typically served from cache; writes are async to storage.
- Keep it simple—no client locale detection is built-in.