## Class-based messages — TL;DR

Minimalny i pewny przepis na wiadomości oparte o klasę (to co naprawdę działa).

### 1) Zdefiniuj klasę
```java
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

@ConfigsFileProperties(name = "teleport-messages")
@SupportedLanguages({"en", "pl"})
public class TeleportMessages {
    // Pola → klucze takie jak nazwa pola (bez konwersji)
    public String playerNotFound = "Player {name} not found!";

    // Gettery → camelCase do dotted.lowercase
    public String getSuccessTeleportedTo() { return "Teleported to {target}!"; }

    // Zagnieżdżone obiekty/klasy są spłaszczane kropkami
    public static class Gui {
        public String title = "Teleport GUI";               // gui.title
        public String getOpenButton() { return "Open"; }    // gui.open.button
    }
    public Gui gui = new Gui();
}
```

Klucze:
- `playerNotFound` → "playerNotFound"
- `getSuccessTeleportedTo()` → "success.teleported.to"
- `gui.title` i `gui.open.button` z obiektu zagnieżdżonego

## Messages API (concise)

Class-based messages with named placeholders and automatic key propagation across languages.

### 1) Define a class
```java
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

@ConfigsFileProperties(name = "teleport-messages")
@SupportedLanguages({"en", "pl"})
public class TeleportMessages {
  // Fields → keys are literal (no conversion)
  public String playerNotFound = "Player {name} not found!"; // key: playerNotFound

  // Getters → camelCase → dotted.lowercase
  public String getSuccessTeleportedTo() { return "Teleported to {target}!"; } // success.teleported.to

  // Nested objects/classes are flattened with dots
  public static class Gui {
    public String title = "Teleport GUI";            // gui.title
    public String getOpenButton() { return "Open"; } // gui.open.button
  }
  public Gui gui = new Gui();
}
```

Key mapping summary:
- fields: exact field name (playerNotFound)
- getters: camelCase → dotted.lowercase (getMainMenuTitle → main.menu.title)
- nested objects/maps: flattened (prefix.key)

### 2) Initialize and auto-merge
```java
ConfigManager cm = MongoConfigsAPI.getConfigManager();
Messages tp = cm.getOrCreateFromObject(new TeleportMessages());
```
Behavior:
- getOrCreateFromObject (and async variant) extracts keys from the object and ensures they exist in every supported language document.
- Missing keys are added automatically. Existing translations are never overwritten.
- The merge runs asynchronously; the returned Messages handle is usable immediately.

### 3) Use with named placeholders
```java
String playerId = player.getUniqueId().toString();
LanguageManager lm = MongoConfigsAPI.getLanguageManager();
String lang = lm.getPlayerLanguage(playerId);
if (lang == null) lang = lm.getDefaultLanguage();

// Provide name→value pairs
player.sendMessage(tp.get(lang, "playerNotFound", "name", targetName));

// Or use a Map
player.sendMessage(tp.get(lang, "success.teleported.to", java.util.Map.of("target", targetName)));

// Plain keys without placeholders
player.sendMessage(tp.get(lang, "gui.title"));
```

Async retrieval is also available for bulk/IO-heavy flows:
```java
tp.getAsync(lang, "playerNotFound", java.util.Map.of("name", targetName))
  .thenAccept(msg -> Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(msg)));
```

### 4) Alternative access

- cm.findById("teleport-messages") → Messages
- cm.messagesOf(TeleportMessages.class) → Messages (uses annotations to resolve id)

Tips:
- Cache the Messages handle; do not recreate per call.
- Fetch language once per interaction; default when null.
- Prefer named placeholders over positional. The API supports pairs (key, value, ... ) or a Map.

See also: [[ConfigManager-API]] and [[LanguageManager-API]].