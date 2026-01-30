# MongoConfigs - AI Agent Documentation

> **WERSJA**: 1.0.0  
> **JAVA**: 21+ (wymagane dla Virtual Threads)  
> **ARCHITEKTURA**: Modularna (API → Core → Platform)
> **PRIORYTET**: ZERO BLOCKING MAIN THREAD

## 🎯 INSTRUKCJA DLA AI

Jesteś AI agentem generującym kod dla MongoConfigs. **NIGDY nie generuj kodu blokującego main thread**. Zawsze używaj async/consumer pattern.

---

## 📁 Struktura Projektów

```
mongo-configs/
├── configs-api/          # Publiczne API - interfejsy i annotacje
├── configs-core/         # Implementacja core (cache, mongo, async)
├── configs-paper/        # Implementacja Paper/Spigot
└── configs-velocity/     # Implementacja Velocity Proxy
```

**Zasada**: Pluginy używają tylko `configs-api` jako `provided` dependency.

---

## 1. DEKLARACJA ZALEŻNOŚCI (pom.xml)

### Dla pluginów używających API:

```xml
<dependencies>
    <!-- MongoConfigs API - REQUIRED -->
    <dependency>
        <groupId>xyz.wtje</groupId>
        <artifactId>configs-api</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- Paper API -->
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.20.4-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<repositories>
    <repository>
        <id>papermc</id>
        <url>https://repo.papermc.io/repository/maven-public/</url>
    </repository>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

### Kluczowe biblioteki w projekcie:

| Biblioteka | Wersja | Cel |
|------------|--------|-----|
| `mongodb-driver-reactivestreams` | 5.5.0 | Async MongoDB driver |
| `caffeine` | 3.1.8 | In-memory cache |
| `jackson-databind` | 2.15.2 | JSON/Object mapping |
| `adventure-api` | 4.19.0 | Text/Component API |
| `adventure-text-minimessage` | 4.24.0 | MiniMessage format |

---

## 2. IMPORTY - KOMPLETNA LISTA

### 2.1 Publiczne API (ZAWSZE używaj tych)

```java
// Główny entry point
import xyz.wtje.mongoconfigs.api.MongoConfigsAPI;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.LanguageManager;
import xyz.wtje.mongoconfigs.api.Messages;

// Annotacje dla POJO
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsCollection;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsDatabase;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

// Eventy
import xyz.wtje.mongoconfigs.api.event.LanguageUpdateListener;

// Core helper (opcjonalnie)
import xyz.wtje.mongoconfigs.api.core.Annotations;
```

### 2.2 Java Standard (async)

```java
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
```

### 2.3 Bukkit/Paper

```java
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
```

---

## 3. ZASADY GENEROWANIA KODU DLA AI

### 🚨 ZASADA #1: NIGDY nie blokuj main thread

```java
// ❌ ŹLE - BLOKUJE MAIN THREAD!
String msg = messages.get("key").join();  // .join() blokuje!
String msg = messages.view("pl").get("key");  // .get() używa .join()!

// ✅ DOBRZE - Async callback
messages.use("key", msg -> player.sendMessage(msg));
messages.get("key").thenAccept(msg -> player.sendMessage(msg));
```

### 🚨 ZASADA #2: Zawsze używaj consumer-based API dla prostych operacji

```java
// ❌ ŹLE - zbędne thenAccept
messages.get("welcome", lang).thenAccept(msg -> player.sendMessage(msg));

// ✅ DOBRZE - prostsze, czytelniejsze
messages.use("welcome", lang, player::sendMessage);
```

### 🚨 ZASADA #3: Preloaduj język przy joinie

```java
@EventHandler
public void onJoin(PlayerJoinEvent event) {
    // ✅ ZAWSZE preloaduj język
    messageService.preloadLanguage(event.getPlayer().getUniqueId());
}
```

### 🚨 ZASADA #4: Używaj View API dla wielu wiadomości

```java
// ❌ ŹLE - osobne lookup dla każdej wiadomości
messages.use("motd.line1", lang, player::sendMessage);
messages.use("motd.line2", lang, player::sendMessage);
messages.use("motd.line3", lang, player::sendMessage);

// ✅ DOBRZE - jeden view, wielokrotne użycie
Messages.View view = messages.view(lang);
view.use("motd.line1", player::sendMessage);
view.use("motd.line2", player::sendMessage);
view.use("motd.line3", player::sendMessage);
```

### 🚨 ZASADA #5: Używaj MessageService wrappera

NIGDY nie używaj bezpośrednio Messages w wielu miejscach. Stwórz MessageService.

---

## 4. INTERFEJSY API - SZCZEGÓŁOWA DOKUMENTACJA

### 4.1 `MongoConfigsAPI`

```java
public class MongoConfigsAPI {
    // Pobierz instancje (inicjalizowane przez plugin platformowy)
    public static ConfigManager getConfigManager();
    public static LanguageManager getLanguageManager();
    
    // Cleanup (wywoływane w onDisable)
    public static void reset();
}
```

**Użycie:**
```java
ConfigManager configManager = MongoConfigsAPI.getConfigManager();
LanguageManager langManager = MongoConfigsAPI.getLanguageManager();
```

### 4.2 `ConfigManager`

```java
public interface ConfigManager {
    
    // ========== KOLEKCJE ==========
    CompletableFuture<Void> reloadAll();
    CompletableFuture<Void> reloadCollection(String collection);
    CompletableFuture<Set<String>> getCollections();
    CompletableFuture<Boolean> collectionExists(String collection);
    
    // ========== CONFIG VALUES ==========
    <T> CompletableFuture<Void> set(String id, T value);
    <T> CompletableFuture<T> get(String id, Class<T> type);
    
    // ========== POJO CONFIGS ==========
    <T> CompletableFuture<Void> setObject(T pojo);
    <T> CompletableFuture<T> getObject(Class<T> type);
    <T> CompletableFuture<T> getConfigOrGenerate(Class<T> type, Supplier<T> generator);
    
    // Z ID:
    default <T> CompletableFuture<Void> setObject(String id, T pojo);
    default <T> CompletableFuture<T> getObject(String id, Class<T> type);
    default <T> CompletableFuture<T> getConfigOrGenerate(String id, Class<T> type, Supplier<T> generator);
    
    // ========== MESSAGES ==========
    Messages findById(String id);
    
    // ========== JĘZYKI ==========
    CompletableFuture<Set<String>> getSupportedLanguages(String collection);
    <T> CompletableFuture<T> getLanguageClass(Class<T> type, String language);
    <T> CompletableFuture<Map<String, T>> getLanguageClasses(Class<T> type);
    
    // ========== ADVANCED ==========
    <T> CompletableFuture<Void> createFromObject(T messageObject);
    <T> CompletableFuture<Messages> getOrCreateFromObject(T messageObject);
    
    // ========== LISTENERS ==========
    void addReloadListener(String collection, Consumer<String> listener);
}
```

### 4.3 `Messages` - NON-BLOCKING!

```java
public interface Messages {
    
    // ========== ASYNC GETTERS ==========
    CompletableFuture<String> get(String path);
    CompletableFuture<String> get(String path, String language);
    CompletableFuture<String> get(String path, Object... placeholders);
    CompletableFuture<String> get(String path, String language, Object... placeholders);
    CompletableFuture<String> get(String path, Map<String, Object> placeholders);
    CompletableFuture<String> get(String path, String language, Map<String, Object> placeholders);
    
    CompletableFuture<List<String>> getList(String path);
    CompletableFuture<List<String>> getList(String path, String language);
    
    // ========== CONSUMER-BASED (ZALECANE) ==========
    default void use(String path, Consumer<String> consumer);
    default void use(String path, String language, Consumer<String> consumer);
    default void useFormat(String path, Consumer<String> consumer, Object... placeholders);
    default void useFormat(String path, String language, Consumer<String> consumer, Object... placeholders);
    
    // ========== VIEW API ==========
    default View view();           // Domyślny język
    default View view(String language);
    
    // ========== VIEW CLASS ==========
    final class View {
        // Async
        public CompletableFuture<String> future(String path);
        public CompletableFuture<String> future(String path, Object... placeholders);
        public CompletableFuture<String> future(String path, Map<String, Object> placeholders);
        public CompletableFuture<List<String>> listFuture(String path);
        
        // SYNC - Uwaga: używa .join() - MOŻE BLOKOWAĆ!
        public String get(String path);
        public String format(String path, Object... placeholders);
        public String format(String path, Map<String, Object> placeholders);
        public List<String> list(String path);
        
        // Consumer-based
        public void use(String path, Consumer<String> consumer);
        public void useList(String path, Consumer<List<String>> consumer);
        
        public String language();
    }
}
```

### 4.4 `LanguageManager`

```java
public interface LanguageManager {
    
    // ========== POBIERANIE/USTAWIANIE ==========
    CompletableFuture<String> getPlayerLanguage(String playerId);
    default CompletableFuture<String> getPlayerLanguage(UUID playerId);
    CompletableFuture<Void> setPlayerLanguage(String playerId, String language);
    default CompletableFuture<Void> setPlayerLanguage(UUID playerId, String language);
    
    // ========== INFO O JĘZYKACH ==========
    CompletableFuture<String> getDefaultLanguage();
    CompletableFuture<String[]> getSupportedLanguages();
    CompletableFuture<Boolean> isLanguageSupported(String language);
    CompletableFuture<String> getLanguageDisplayName(String language);
    
    // ========== CONSUMER-BASED ==========
    default void usePlayerLanguage(String playerId, Consumer<String> consumer);
    default void usePlayerLanguage(UUID playerId, Consumer<String> consumer);
    default void useDefaultLanguage(Consumer<String> consumer);
    default void useSupportedLanguages(Consumer<String[]> consumer);
    
    // ========== LISTENERS ==========
    void registerListener(LanguageUpdateListener listener);
    void unregisterListener(LanguageUpdateListener listener);
}
```

### 4.5 `LanguageUpdateListener`

```java
@FunctionalInterface
public interface LanguageUpdateListener {
    void onLanguageUpdate(String playerId, String oldLanguage, String newLanguage);
}
```

---

## 5. ANOTACJE

### `@ConfigsFileProperties` - Główna annotacja

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigsFileProperties {
    String name();  // ID kolekcji w MongoDB
}
```

**Użycie:**
```java
@ConfigsFileProperties(name = "my-plugin-messages")
@SupportedLanguages({"en", "pl", "de"})
public class MyMessages {
    public String welcome = "Welcome!";
}
```

### `@SupportedLanguages`

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SupportedLanguages {
    String[] value();
}
```

### `@ConfigsCollection` - Niestandardowa kolekcja

```java
@ConfigsFileProperties(name = "my-config")
@ConfigsCollection("custom_configs")  // inna niż domyślna
public class MyConfig { }
```

### `@ConfigsDatabase` - Niestandardowa baza

```java
@ConfigsFileProperties(name = "my-config")
@ConfigsDatabase("other_database")
public class MyConfig { }
```

---

## 6. WZORCE KODU DLA AI

### Wzorzec 1: MessageService (ZAWSZE używaj tego!)

```java
package com.example.plugin.service;

import org.bukkit.entity.Player;
import xyz.wtje.mongoconfigs.api.LanguageManager;
import xyz.wtje.mongoconfigs.api.Messages;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Serwis do wysyłania wiadomości - 100% async, zero lagów.
 */
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

    // ========== GŁÓWNE METODY ==========

    public void send(Player player, String path, Consumer<String> action) {
        messages.use(path, getLanguage(player), action);
    }

    public void send(Player player, String path, Map<String, Object> placeholders, Consumer<String> action) {
        messages.get(path, getLanguage(player), placeholders).thenAccept(action);
    }

    public void send(Player player, String path, Consumer<String> action, Object... placeholders) {
        messages.get(path, getLanguage(player), placeholders).thenAccept(action);
    }

    public void sendList(Player player, String path, Consumer<List<String>> action) {
        messages.getList(path, getLanguage(player)).thenAccept(action);
    }

    public Messages.View view(Player player) {
        return messages.view(getLanguage(player));
    }

    // ========== CACHE JĘZYKÓW ==========

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

    public void setLanguage(UUID playerId, String language) {
        languageCache.put(playerId, language);
        langManager.setPlayerLanguage(playerId, language);
    }
}
```

### Wzorzec 2: Main Plugin Class

```java
package com.example.plugin;

import com.example.plugin.config.MyConfig;
import com.example.plugin.message.MyMessages;
import com.example.plugin.service.MessageService;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.LanguageManager;
import xyz.wtje.mongoconfigs.api.MongoConfigsAPI;

public class MyPlugin extends JavaPlugin {

    private static MyPlugin instance;
    private MessageService messageService;
    private MyConfig config;

    @Override
    public void onEnable() {
        instance = this;
        
        ConfigManager configManager = MongoConfigsAPI.getConfigManager();
        LanguageManager langManager = MongoConfigsAPI.getLanguageManager();

        // Załaduj config
        configManager.getConfigOrGenerate(MyConfig.class, MyConfig::new)
            .thenAccept(loadedConfig -> {
                this.config = loadedConfig;
                getLogger().info("Config loaded!");
            });

        // Załaduj messages i stwórz MessageService
        configManager.getOrCreateFromObject(new MyMessages())
            .thenAccept(messages -> {
                this.messageService = new MessageService(messages, langManager, "en");
                getLogger().info("Messages loaded!");
                
                // Rejestruj listenery PO załadowaniu
                registerListeners();
            });
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
            new PlayerListener(messageService), this);
    }

    @Override
    public void onDisable() {
        // Cleanup jeśli potrzebne
    }

    public static MyPlugin getInstance() { return instance; }
    public MessageService getMessageService() { return messageService; }
    public MyConfig getConfig() { return config; }
}
```

### Wzorzec 3: PlayerListener

```java
package com.example.plugin.listener;

import com.example.plugin.service.MessageService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

public class PlayerListener implements Listener {

    private final MessageService msg;

    public PlayerListener(MessageService msg) {
        this.msg = msg;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        
        // ✅ Preload języka
        msg.preloadLanguage(player.getUniqueId());
        
        // ✅ Wyślij powitanie z placeholderami
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

### Wzorzec 4: Komendy

```java
package com.example.plugin.command;

import com.example.plugin.service.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class MyCommand implements CommandExecutor {

    private final MessageService msg;

    public MyCommand(MessageService msg) {
        this.msg = msg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players!");
            return true;
        }

        if (args.length == 0) {
            // ✅ Wysyłanie listy
            msg.sendList(player, "commands.helpLines", lines -> 
                lines.forEach(player::sendMessage)
            );
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            // ✅ Prosta wiadomość
            msg.send(player, "general.reloaded", player::sendMessage);
            return true;
        }

        if ("greet".equalsIgnoreCase(args[0]) && args.length > 1) {
            // ✅ Z placeholderami
            msg.send(player, "general.playerGreeted", 
                Map.of("player", args[1]),
                player::sendMessage);
            return true;
        }

        return false;
    }
}
```

### Wzorzec 5: POJO Classes

```java
package com.example.plugin.config;

import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;

@ConfigsFileProperties(name = "my-plugin-config")
public class MyConfig {
    
    public Database database = new Database();
    public Features features = new Features();

    public static class Database {
        public String host = "localhost";
        public int port = 27017;
        public String name = "minecraft";
    }

    public static class Features {
        public boolean featureA = true;
        public boolean featureB = false;
        public int cooldownSeconds = 5;
    }
}
```

```java
package com.example.plugin.message;

import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

import java.util.List;

@ConfigsFileProperties(name = "my-plugin-messages")
@SupportedLanguages({"en", "pl"})
public class MyMessages {

    public General general = new General();
    public Commands commands = new Commands();

    public static class General {
        public String prefix = "&8[&aMyPlugin&8] ";
        public String noPermission = "&cYou don't have permission!";
        public String playerNotFound = "&cPlayer not found: &e{player}";
        public String reloaded = "&aConfiguration reloaded!";
    }

    public static class Commands {
        public String helpHeader = "&6=== MyPlugin Help ===";
        public List<String> helpLines = List.of(
            "&e/mycommand help &7- Show this help",
            "&e/mycommand reload &7- Reload config"
        );
        public String usage = "&cUsage: /mycommand <args>";
    }
}
```

---

## 7. ŚCIEŻKI DO WIADOMOŚCI

Ścieżki odpowiadają strukturze klasy POJO:

| Pole w klasie | Ścieżka | Przykład użycia |
|--------------|---------|-----------------|
| `MyMessages.general.prefix` | `"general.prefix"` | `msg.send(player, "general.prefix", ...)` |
| `MyMessages.general.noPermission` | `"general.noPermission"` | `msg.send(player, "general.noPermission", ...)` |
| `MyMessages.commands.helpLines` | `"commands.helpLines"` | `msg.sendList(player, "commands.helpLines", ...)` |
| `MyMessages.commands.usage` | `"commands.usage"` | `msg.send(player, "commands.usage", ...)` |

---

## 8. PLACEHOLDERS

### Format: `{placeholder}`

```java
// W definicji
public String welcome = "Welcome {player}! You have {coins} coins.";

// Użycie - varargs (key, value, key, value...)
messages.get("welcome", "pl", "player", "Steve", "coins", 100)
    .thenAccept(msg -> ...);  // "Welcome Steve! You have 100 coins."

// Użycie - Map
messages.get("welcome", "pl", Map.of(
    "player", player.getName(),
    "coins", player.getCoins()
)).thenAccept(msg -> ...);

// Użycie w MessageService
msg.send(player, "general.welcome", 
    Map.of("player", player.getName()),
    player::sendMessage);
```

---

## 9. CHAINING COMPLETABLEFUTURE

### Dla zaawansowanych operacji

```java
// Pobierz język, potem wiadomość, potem kolejną
langManager.getPlayerLanguage(player.getUniqueId())
    .thenCompose(lang -> messages.get("welcome", lang))
    .thenCompose(msg -> {
        player.sendMessage(msg);
        return langManager.getPlayerLanguage(player.getUniqueId());
    })
    .thenCompose(lang -> messages.get("rules", lang))
    .thenAccept(rules -> player.sendMessage(rules));

// Obsługa błędów
messages.get("welcome", "pl")
    .thenAccept(msg -> player.sendMessage(msg))
    .exceptionally(throwable -> {
        player.sendMessage("Error loading message!");
        return null;
    });
```

---

## 10. LISTENERS I CALLBACKI

### Reload listener

```java
configManager.addReloadListener("my-plugin-messages", collection -> {
    getLogger().info("Messages reloaded!");
    // Odśwież cache jeśli masz
});
```

### Language update listener

```java
langManager.registerListener((playerId, oldLang, newLang) -> {
    UUID uuid = UUID.fromString(playerId);
    messageService.updateCachedLanguage(uuid, newLang);
    
    Player player = Bukkit.getPlayer(uuid);
    if (player != null) {
        player.sendMessage("Language changed to: " + newLang);
    }
});
```

---

## 11. ARCHITEKTURA VIRTUAL THREADS

```
┌─────────────────────────────────────────────────────────────┐
│                     MAIN SERVER THREAD                       │
│              (NIGDY nie blokowany przez Mongo!)              │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ messages.use("key", consumer)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│            VIRTUAL THREAD EXECUTOR (Java 21+)               │
│         Executors.newVirtualThreadPerTaskExecutor()         │
│                                                              │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │ Virtual     │ │ Virtual     │ │ Virtual     │  ...       │
│  │ Thread #1   │ │ Thread #2   │ │ Thread #3   │           │
│  │ (~1KB RAM)  │ │ (~1KB RAM)  │ │ (~1KB RAM)  │           │
│  └─────────────┘ └─────────────┘ └─────────────┘           │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ cache lookup / MongoDB query
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    CAFFEINE CACHE                            │
│           (ReadWriteLock dla atomic operations)             │
│                                                              │
│  ┌───────────────────────┐  ┌───────────────────────┐       │
│  │ Read Lock (shared)    │  │ Write Lock (exclusive)│       │
│  │ getMessage()          │  │ replaceLanguageData() │       │
│  │ getConfig()           │  │ invalidate()          │       │
│  └───────────────────────┘  └───────────────────────┘       │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ callback do main thread
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     MAIN SERVER THREAD                       │
│              consumer.accept(message) - INSTANT!             │
└─────────────────────────────────────────────────────────────┘
```

**Dlaczego to działa:**
1. **Virtual Threads**: ~1KB vs ~1MB dla platform threads
2. **Non-blocking I/O**: MongoDB reactive streams
3. **ReadWriteLock**: Wiele wątków czyta równocześnie
4. **Caffeine Cache**: O(1) lookup

---

## 12. CHECKLIST DLA AI

Przed wygenerowaniem kodu sprawdź:

- [ ] Czy używam `messages.use()` zamiast `messages.get().join()`?
- [ ] Czy MessageService ma metodę `preloadLanguage()`?
- [ ] Czy PlayerListener wywołuje `preloadLanguage()` w `onJoin()`?
- [ ] Czy PlayerListener wywołuje `removePlayer()` w `onQuit()`?
- [ ] Czy używam `Map.of()` dla placeholderów?
- [ ] Czy POJO ma `@ConfigsFileProperties`?
- [ ] Czy Messages POJO ma `@SupportedLanguages`?
- [ ] Czy wszystkie ścieżki wiadomości odpowiadają strukturze klasy?
- [ ] Czy używam `view()` dla wielu wiadomości?
- [ ] Czy importuję tylko z `xyz.wtje.mongoconfigs.api.*`?

---

## 13. FAQ DLA AI

### Q: Czy mogę użyć `messages.view("pl").get("key")`?
**A: NIE!** `.get()` używa `.join()` wewnętrznie i blokuje main thread. Użyj `messages.use()` lub `messages.get().thenAccept()`.

### Q: Jak wysłać wiadomość do wielu graczy?
**A:**
```java
for (Player player : Bukkit.getOnlinePlayers()) {
    msg.send(player, "broadcast.message", player::sendMessage);
}
```

### Q: Jak obsłużyć błędy?
**A:**
```java
messages.get("welcome", "pl")
    .thenAccept(msg -> player.sendMessage(msg))
    .exceptionally(throwable -> {
        player.sendMessage("Error!");
        return null;
    });
```

### Q: Czy mogę cache'ować Messages.View?
**A:** Tak, ale tylko na czas jednej operacji. Nie przechowuj długoterminowo.

### Q: Jak sprawdzić czy wiadomość istnieje?
**A:** Użyj domyślnej wartości lub sprawdź w cache:
```java
messages.get("key", "pl").thenAccept(msg -> {
    if (msg != null && !msg.isEmpty()) {
        player.sendMessage(msg);
    }
});
```

---

**END OF DOCUMENTATION**

> Ostatnia aktualizacja: 2026-01-30  
> Wersja projektu: 1.0.0  
> Java: 21+ (Virtual Threads wymagane)  
> Priorytet: ZERO BLOCKING MAIN THREAD
