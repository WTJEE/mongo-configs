# MongoDB Configs Library

[![](https://jitpack.io/v/aiikk/mongo-configs.svg)](https://jitpack.io/#aiikk/mongo-configs)

**Zaawansowana biblioteka MongoDB do zarządzania konfiguracjami i tłumaczeniami dla wtyczek Minecraft Paper/Bukkit z wysokowydajnymi operacjami batch.**

---

## 🚀 **Kluczowe możliwości**

### ⚡ **Wydajność na dużą skalę**
- **100+ wiadomości jednocześnie** - batch operations bez limitów
- **30+ dokumentów w 10 kolekcjach** - kontrola współbieżności
- **Operacje batch**## 🆘 **Wsparcie**

- **GitHub Issues**: [Zgłoś błędy lub poproś o nowe funkcje](https://github.com/WTJEE/mongo-configs/issues)

---

## 📊 **Podsumowanie możliwości**

✅ **100+ wiadomości jednocześnie** - batch operations bez limitów  
✅ **30+ dokumentów w 10 kolekcjach** - kontrolowana współbieżność  
✅ **Wszystkie formaty kolorów** - Legacy, Hex, RGB, MiniMessage gradienty  
✅ **Cache 10k wpisów** - <0.001ms dostęp do kolorów  
✅ **MongoDB Reactive Streams** - connection pooling i auto-resume  
✅ **Multi-język z GUI** - automatyczne preferencje graczy  
✅ **Zagnieżdżone klucze** - `gui.buttons.close` support  
✅ **Lore support** - separacja przecinkami  
✅ **Async/Sync API** - elastyczne operacje  
✅ **Hot-reload** - Change Streams monitoring  
✅ **Error resilience** - timeout handling  

**MongoDB Configs Library - wydajne zarządzanie konfiguracjami na dużą skalę! 🚀**dna operacja MongoDB zamiast setek
- **Smart Cache** - buforowanie kolorów i danych z <0.001ms dostępem
- **Connection pooling** - kontrolowane wykorzystanie zasobów

### 🗄️ **Integracja MongoDB**
- MongoDB Reactive Streams z connection pooling
- Change Streams do hot-reload
- Auto-resume przy błędach połączenia
- Konfigurowalne timeouty i pool settings

### 🌍 **Wsparcie wielojęzyczne**
- Komenda `/language` z konfigurowalnymi nazwami
- Automatyczny zapis preferencji do MongoDB
- Wsparcie zagnieżdżonych kluczy (`warrior.openTitle`)
- **Wsparcie lore** z separacją przecinkami
- Fallback do domyślnego języka

### 🎨 **Zaawansowany system kolorów**
- **Wszystkie formaty**: Legacy (`&6`), Hex (`&#54DAF4`), MiniMessage
- **Gradienty MiniMessage**: `<gradient:#54daf4:#545eb6>tekst</gradient>`
- **Format Bukkit RGB**: `&x&5&4&D&A&F&4`
- **Własny format RGB**: `&{54,218,244}`
- **Cache 10k wpisów** z <0.001ms dostępem
- **Automatyczne przetwarzanie** wszystkich wiadomości

## 📦 **Instalacja**

**Maven:**
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
	<dependency>
	    <groupId>com.github.WTJEE.mongo-configs</groupId>
	    <artifactId>configs-api</artifactId>
	    <version>{WersjaRelease}</version>
	</dependency>
</dependencies>
```

**Gradle:**
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.WTJEE.mongo-configs:configs-api:{WersjaRelease}'
}
```

**Na serwer:**
1. Pobierz plugin z releases
2. Umieść w folderze `plugins/`
3. Skonfiguruj połączenie MongoDB w `config.yml`
4. Restart serwera

## 📚 **Jak używać API**

### Pobieranie instancji API
```java
ConfigManager config = MongoConfigsAPI.getConfigManager();
LanguageManager lang = MongoConfigsAPI.getLanguageManager();
```

### Zarządzanie konfiguracją
```java
// Pobieranie wartości konfiguracji z domyślnymi
String dbName = config.getConfig("MojaWtyczka_Config", "database", "default");
boolean enabled = config.getConfig("MojaWtyczka_Config", "enabled", true);
int maxPlayers = config.getConfig("MojaWtyczka_Config", "maxPlayers", 100);

// Ustawianie wartości (async)
config.setConfig("MojaWtyczka_Config", "maintenance", false);
config.setConfig("MojaWtyczka_Config", "spawn.world", "world");

// ⚡ BATCH OPERATIONS - Wiele konfigów na raz (DUŻO SZYBCIEJ!)
Map<String, Object> configValues = new HashMap<>();
configValues.put("maintenance", false);
configValues.put("maxPlayers", 200);
configValues.put("spawn.world", "world");
configValues.put("economy.enabled", true);
// Dodaj nawet 100+ wartości - bez problemu!
for (int i = 1; i <= 100; i++) {
    configValues.put("setting_" + i, "value_" + i);
}
config.setConfigBatch("MojaWtyczka_Config", configValues); // Jedna operacja MongoDB!
```

### Zarządzanie wiadomościami/tłumaczeniami

#### **Pobieranie wiadomości - podstawy**
```java
// Podstawowe pobieranie wiadomości (automatyczne kolorowanie!)
String msg = config.getMessage("MojaWtyczka_Config", "pl", "welcome", "Domyślna wiadomość");
player.sendMessage(msg); // Wyśle wiadomość z kolorami!

// Z placeholderami - {klucz} zostanie zastąpione wartością
String personalMsg = config.getMessage("MojaWtyczka_Config", "pl", "welcome", 
    "player", player.getName(),           // {player} → nazwa gracza
    "server", "SkyPvP",                  // {server} → nazwa serwera  
    "level", String.valueOf(playerLevel) // {level} → poziom gracza
);
// Wynik: "&#54DAF4Witaj Gracz123 &ana serwerze SkyPvP! &eJesteś na poziomie 15"
```

#### **Różne sposoby pobierania wiadomości**
```java
// 1. getMessage() - z automatycznymi kolorami
String coloredMsg = config.getMessage("Wtyczka", "pl", "welcome", 
    "player", player.getName());
// Wynik: kolorowa wiadomość gotowa do wyświetlenia

// 2. getPlainMessage() - bez kolorów (do logów/konsoli)
String plainMsg = config.getPlainMessage("Wtyczka", "pl", "welcome", 
    "player", player.getName());
// Wynik: czysty tekst bez kodów kolorów

// 3. getMessageLore() - lista wiadomości (dla itemów)
List<String> lore = config.getMessageLore("Wtyczka", "pl", "item.sword.lore");
// Automatycznie dzieli po przecinkach i koloruje każdą linię
```

#### **Języki graczy - automatyczne pobieranie**
```java
// Pobieranie języka gracza
LanguageManager langManager = MongoConfigsAPI.getLanguageManager();
String playerLang = langManager.getPlayerLanguage(player.getUniqueId().toString());

// Wiadomość w języku gracza
String welcomeMsg = config.getMessage("MojaWtyczka", playerLang, "welcome",
    "player", player.getName(),
    "time", LocalTime.now().toString()
);
player.sendMessage(welcomeMsg);

// Ustawianie języka gracza (zapisuje do MongoDB)
langManager.setPlayerLanguage(player.getUniqueId(), "pl")
    .thenRun(() -> {
        player.sendMessage("Język zmieniony na polski!");
    });
```

#### **Zagnieżdżone klucze wiadomości**
```java
// Struktura w MongoDB:
// {
//   "gui": {
//     "buttons": {
//       "close": "&#FF0000&lZamknij",
//       "next": "&a&lDalej"
//     },
//     "title": "<gradient:#FFD700:#FF8C00>Menu Główne</gradient>"
//   }
// }

// Pobieranie zagnieżdżonych kluczy
String closeButton = config.getMessage("Wtyczka", "pl", "gui.buttons.close", "Zamknij");
String guiTitle = config.getMessage("Wtyczka", "pl", "gui.title", "Menu");
String nextButton = config.getMessage("Wtyczka", "pl", "gui.buttons.next", "Dalej");

// Używanie w GUI
Inventory inv = Bukkit.createInventory(null, 27, ColorHelper.parseText(guiTitle));
ItemStack closeItem = new ItemStack(Material.BARRIER);
ItemMeta meta = closeItem.getItemMeta();
meta.setDisplayName(ColorHelper.parseText(closeButton));
closeItem.setItemMeta(meta);
```

#### **Lore dla itemów**
```java
// W MongoDB zapisz jako string z przecinkami:
// "sword.lore": "&7Potężna broń,&#54DAF4+15 Obrażeń,<gradient:#FF0000:#8B0000>Zaklęcie Ognia III</gradient>,&eKliknij aby użyć"

// Pobieranie jako lista (automatyczny split i kolorowanie)
List<String> swordLore = config.getMessageLore("Wtyczka", "pl", "sword.lore");
// Rezultat:
// [0] = "§7Potężna broń"
// [1] = "§x§5§4§D§A§F§4+15 Obrażeń"  
// [2] = "§x§F§F§0§0§0§0Zaklęcie Ognia III" (gradient)
// [3] = "§eKliknij aby użyć"

// Używanie w itemie
ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
ItemMeta swordMeta = sword.getItemMeta();
swordMeta.setLore(swordLore); // Automatycznie kolorowe!
sword.setItemMeta(swordMeta);
```

#### **Ustawianie wiadomości**
#### **Ustawianie wiadomości**
```java
// Pojedyncza wiadomość
config.setMessage("MojaWtyczka", "pl", "goodbye", 
    "&#FF0000Do widzenia {player}!");

// Z wszystkimi formatami kolorów jednocześnie
config.setMessage("MojaWtyczka", "pl", "level_up", 
    "&l&6AWANS! &r<gradient:#54daf4:#545eb6>Gratulacje</gradient> &#FFD700{player}! " +
    "&aJesteś teraz na poziomie &{255,215,0}{level}");
```

### **Formaty kolorów - wszystkie obsługiwane**
```java
// 1. LEGACY COLORS (klasyczne Bukkit)
"&6Złoty &cCzerwony &aBold &ltext"
"&0Czarny &1Ciemny Niebieski &2Ciemny Zielony &3Ciemny Błękitny"
"&4Ciemny Czerwony &5Ciemny Fioletowy &6Złoty &7Szary"
"&8Ciemny Szary &9Niebieski &aZielony &bBłękitny"
"&cCzerwony &dJasny Fioletowy &eŻółty &fBiały"
"&lPogrubienie &mPrzekreślenie &nPodkreślenie &oKursywa &rReset"

// 2. HEX COLORS (nowoczesne, czyste)
"&#54DAF4Piękny błękitny &#FF0000jasny czerwony"
"&#FFD700Złoty &#32CD32Limonka &#FF69B4Różowy"

// 3. BUKKIT RGB FORMAT (obsługiwany przez większość pluginów)
"&x&5&4&D&A&F&4Własny &x&F&F&0&0&0&0kolor"
"&x&F&F&D&7&0&0Złoty &x&3&2&C&D&3&2Limonka"

// 4. WŁASNY RGB FORMAT (łatwy w użyciu)
"&{54,218,244}RGB niebieski &{255,0,0}RGB czerwony"
"&{255,215,0}Złoty &{50,205,50}Limonka &{255,105,180}Różowy"

// 5. MINIMESSAGE GRADIENTY (piękne przejścia)
"<gradient:#54daf4:#545eb6>Niesamowity gradient</gradient>"
"<gradient:#FF0000:#FFFF00:#00FF00>Tęczowe przejście</gradient>"
"<gradient:#FFD700:#FF8C00>Złote zanikanie</gradient>"

// 6. MIESZANE FORMATY (wszystko razem!)
"&6Złoty &#54DAF4hex <gradient:#FF0000:#00FF00>gradient</gradient> &{255,255,0}rgb"
"&l&6SERWER &r&8» <gradient:#54daf4:#545eb6>Witaj</gradient> &#FF0000{player}!"

// Przykłady z życia wzięte:
"&l&6AWANS POZIOMU! &r&#54DAF4Osiągnąłeś poziom &{255,215,0}{level}"
"<gradient:#FF6B6B:#4ECDC4>Dzięki za grę!</gradient> &aOdwiedź nas ponownie!"
"&8[&6VIP&8] &#54DAF4{player} &7dołączył na serwer"
```

### **Praktyczne przykłady użycia**

#### **System powiadomień graczy**
```java
public class PlayerNotificationSystem {
    private final ConfigManager config = MongoConfigsAPI.getConfigManager();
    private final LanguageManager lang = MongoConfigsAPI.getLanguageManager();
    
    public void sendLevelUpMessage(Player player, int newLevel) {
        String playerLang = lang.getPlayerLanguage(player.getUniqueId().toString());
        
        // Wiadomość z automatycznymi kolorami
        String message = config.getMessage("LevelSystem", playerLang, "level_up",
            "player", player.getName(),
            "level", String.valueOf(newLevel),
            "xp_needed", String.valueOf(getXpForLevel(newLevel + 1))
        );
        
        player.sendMessage(message);
        
        // Opcjonalnie tytuł
        String title = config.getMessage("LevelSystem", playerLang, "level_up_title",
            "level", String.valueOf(newLevel)
        );
        player.sendTitle(ColorHelper.parseText(title), "", 10, 70, 20);
    }
    
    public void sendDeathMessage(Player player, Player killer) {
        String playerLang = lang.getPlayerLanguage(player.getUniqueId().toString());
        String killerLang = lang.getPlayerLanguage(killer.getUniqueId().toString());
        
        // Wiadomość dla zabitego
        String deathMsg = config.getMessage("PvP", playerLang, "death.killed_by",
            "killer", killer.getName(),
            "weapon", getWeaponName(killer.getInventory().getItemInMainHand())
        );
        player.sendMessage(deathMsg);
        
        // Wiadomość dla zabójcy
        String killMsg = config.getMessage("PvP", killerLang, "kill.player",
            "victim", player.getName(),
            "streak", String.valueOf(getKillStreak(killer))
        );
        killer.sendMessage(killMsg);
    }
}
```

#### **System GUI z wiadomościami**
```java
public class ShopGUI {
    private final ConfigManager config = MongoConfigsAPI.getConfigManager();
    private final LanguageManager lang = MongoConfigsAPI.getLanguageManager();
    
    public void openShop(Player player) {
        String playerLang = lang.getPlayerLanguage(player.getUniqueId().toString());
        
        // Tytuł GUI z gradientem
        String title = config.getMessage("Shop", playerLang, "gui.title", "Sklep");
        Inventory inv = Bukkit.createInventory(null, 54, ColorHelper.parseText(title));
        
        // Item z lore
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        
        // Nazwa itemu
        String itemName = config.getMessage("Shop", playerLang, "items.diamond_sword.name",
            "price", "1000"
        );
        swordMeta.setDisplayName(ColorHelper.parseText(itemName));
        
        // Lore itemu (automatyczne dzielenie po przecinkach)
        List<String> lore = config.getMessageLore("Shop", playerLang, "items.diamond_sword.lore");
        swordMeta.setLore(lore);
        
        sword.setItemMeta(swordMeta);
        inv.setItem(10, sword);
        
        player.openInventory(inv);
    }
}
```

#### **System ogłoszeń serwera**
```java
public class AnnouncementSystem {
    private final ConfigManager config = MongoConfigsAPI.getConfigManager();
    private final LanguageManager lang = MongoConfigsAPI.getLanguageManager();
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerLang = lang.getPlayerLanguage(player.getUniqueId().toString());
        
        // Wiadomość powitalna
        String welcomeMsg = config.getMessage("Server", playerLang, "join.welcome",
            "player", player.getName(),
            "online", String.valueOf(Bukkit.getOnlinePlayers().size()),
            "max", String.valueOf(Bukkit.getMaxPlayers())
        );
        
        // Wyślij po 2 sekundach (żeby GUI się załadowało)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage(welcomeMsg);
            
            // Jeśli pierwszy raz
            if (!player.hasPlayedBefore()) {
                String firstJoinMsg = config.getMessage("Server", playerLang, "join.first_time",
                    "player", player.getName()
                );
                player.sendMessage(firstJoinMsg);
            }
        }, 40L);
    }
    
    // Globalny broadcast w różnych językach
    public void broadcastMessage(String key, String... placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerLang = lang.getPlayerLanguage(player.getUniqueId().toString());
            String message = config.getMessage("Broadcast", playerLang, key, placeholders);
            player.sendMessage(message);
        }
    }
}
Map<String, String> messages = new HashMap<>();
messages.put("welcome", "<gradient:#54daf4:#545eb6>Witaj {player}!</gradient>");
messages.put("goodbye", "&#FF0000Do widzenia {player}!");
messages.put("level_up", "&l&6AWANS! &r&#54DAF4Jesteś na poziomie &{255,215,0}{level}");

// Dodaj nawet 100+ wiadomości jednocześnie
for (int i = 1; i <= 100; i++) {
    messages.put("message_" + i, "&#54DAF4Wiadomość numer " + i + " dla {player}!");
}
config.setMessageBatch("MojaWtyczka_Config", "pl", messages); // Jedna operacja!

// ⚡ MULTI-LANGUAGE BATCH - Wszystkie języki na raz!
Map<String, Map<String, String>> allLanguages = new HashMap<>();
allLanguages.put("en", getEnglishMessages());   // 100+ wiadomości EN
allLanguages.put("pl", getPolishMessages());    // 100+ wiadomości PL  
allLanguages.put("de", getGermanMessages());    // 100+ wiadomości DE
}

### **Zarządzanie kolekcjami - duża skala**
```

### **Batch operations - masowe operacje**
```java
// ⚡ BATCH MESSAGES - 100+ wiadomości na raz (SUPER SZYBKO!)
```java
// 🚀 BATCH COLLECTION CREATION - 10+ kolekcji z 30+ dokumentami każda
Set<CollectionSetupData> collections = new HashSet<>();

for (int gameId = 1; gameId <= 10; gameId++) {
    Map<String, Object> gameConfigs = new HashMap<>();
    // 30+ konfigów na grę
    for (int i = 1; i <= 30; i++) {
        gameConfigs.put("config_" + i, "value_" + i);
    }
    
    Map<String, Map<String, String>> gameMessages = new HashMap<>();
    Map<String, String> plMessages = new HashMap<>();
    Map<String, String> enMessages = new HashMap<>();
    
    // 50+ wiadomości per język
    for (int i = 1; i <= 50; i++) {
        plMessages.put("msg_" + i, "Wiadomość " + i + " dla gry " + gameId);
        enMessages.put("msg_" + i, "Message " + i + " for game " + gameId);
    }
    
    gameMessages.put("pl", plMessages);
    gameMessages.put("en", enMessages);
    
    CollectionSetupData gameData = new CollectionSetupData.Builder("game_" + gameId)
            .addLanguage("pl")
            .addLanguage("en")
            .configValues(gameConfigs)
            .languageMessages(gameMessages)
            .build();
    
    collections.add(gameData);
}

// Tworzenie WSZYSTKICH kolekcji z kontrolą współbieżności (max 3 na raz)
config.createCollectionsBatch(collections, 3)
      .thenRun(() -> {
          System.out.println("Utworzono " + collections.size() + " kolekcji z setkami dokumentów!");
      });

// 🚀 BATCH RELOAD - Przeładowanie wielu kolekcji na raz
Set<String> collectionsToReload = Set.of("game_1", "game_2", "game_3", "game_4", "game_5", "game_6", "game_7", "game_8", "game_9", "game_10");
config.reloadCollectionsBatch(collectionsToReload, 4) // Max 4 na raz
      .thenRun(() -> {
          System.out.println("Przeładowano wszystkie 10 kolekcji z setkami dokumentów!");
      });
```

### Language Management
```java
// Get player's language
String playerLang = lang.getPlayerLanguage(player.getUniqueId().toString());

// Set player's language (sync operation)
lang.setPlayerLanguage(player.getUniqueId().toString(), "pl");

// Set player's language (async with UUID)
lang.setPlayerLanguage(player.getUniqueId(), "pl")
    .thenRun(() -> {
        // Language saved to database
    });

// Set player's language (async with String)
lang.setPlayerLanguageAsync(player.getUniqueId().toString(), "pl")
    .thenRun(() -> {
        // Language saved to database
    });

// Get default language
String defaultLang = lang.getDefaultLanguage();

// Get all supported languages
String[] supportedLangs = lang.getSupportedLanguages();

// Check supported languages
if (lang.isLanguageSupported("de")) {
    // German is supported
}

// Get display name (supports base64)
String displayName = lang.getLanguageDisplayName("pl"); // "Polski"
```

### Performance Monitoring
```java
// Get cache statistics
CacheStats stats = config.getCacheStats();
double hitRate = stats.getHitRate();
long cacheSize = stats.getSize();
long hitCount = stats.getHitCount();
long missCount = stats.getMissCount();

// Get color cache statistics
var colorStats = config.getColorCacheStats();
System.out.println("Color cache hit rate: " + colorStats.hitRate());

// Get performance metrics
PerformanceMetrics metrics = config.getMetrics();
boolean changeStreamsActive = metrics.isChangeStreamsActive();
int activeConnections = metrics.getActiveConnections();
Duration avgMongoTime = metrics.getAverageMongoTime();
long mongoOpsCount = metrics.getMongoOperationsCount();
```

## � **Troubleshooting i najlepsze praktyki**

### **Często spotykane problemy**

#### **Wiadomość nie ma kolorów**
```java
// ❌ ŹLE - używasz ColorHelper ręcznie
player.sendMessage(ColorHelper.parseText(message));

// ✅ DOBRZE - getMessage() automatycznie koloruje
String message = config.getMessage("Wtyczka", "pl", "welcome");
player.sendMessage(message); // Już kolorowe!
```

#### **Placeholdery się nie zastępują**
```java
// ❌ ŹLE - nieparzysty liczba argumentów
String msg = config.getMessage("Wtyczka", "pl", "welcome", "player"); // Brak wartości!

// ✅ DOBRZE - para klucz-wartość
String msg = config.getMessage("Wtyczka", "pl", "welcome", 
    "player", player.getName()); // Klucz i wartość
```

#### **Kolekcja nie istnieje**
```java
// Zawsze sprawdź przed użyciem
if (!config.collectionExists("MojaWtyczka")) {
    // Utwórz kolekcję
    config.createCollection("MojaWtyczka", Set.of("pl", "en"))
          .thenRun(() -> {
              // Dodaj domyślne wiadomości
              setupDefaultMessages();
          });
}
```

### **Najlepsze praktyki**

#### **1. Zawsze używaj domyślnych wartości**
```java
// ✅ DOBRZE - zawsze podaj fallback
String message = config.getMessage("Wtyczka", lang, "welcome", "Witaj na serwerze!");

// ❌ ŹLE - może zwrócić null
String message = config.getMessage("Wtyczka", lang, "welcome", null);
```

#### **2. Cache języków graczy lokalnie**
```java
public class PlayerLanguageCache {
    private final Map<UUID, String> languageCache = new ConcurrentHashMap<>();
    private final LanguageManager langManager = MongoConfigsAPI.getLanguageManager();
    
    public String getPlayerLanguage(Player player) {
        return languageCache.computeIfAbsent(player.getUniqueId(), uuid -> {
            return langManager.getPlayerLanguage(uuid.toString());
        });
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        languageCache.remove(event.getPlayer().getUniqueId());
    }
}
```

#### **3. Używaj batch operations dla lepszej wydajności**
```java
// ✅ DOBRZE - jedna operacja
Map<String, String> allMessages = prepareAllMessages();
config.setMessageBatch("Wtyczka", "pl", allMessages);

// ❌ ŹLE - 100 operacji MongoDB
for (Entry<String, String> entry : allMessages.entrySet()) {
    config.setMessage("Wtyczka", "pl", entry.getKey(), entry.getValue());
}
```

#### **4. Obsługuj błędy async operations**
```java
config.createCollection("NewCollection", Set.of("pl", "en"))
      .thenRun(() -> {
          getLogger().info("Kolekcja utworzona!");
      })
      .exceptionally(throwable -> {
          getLogger().severe("Błąd tworzenia kolekcji: " + throwable.getMessage());
          return null;
      });
```

## 🗄️ **Struktura dokumentów MongoDB**

### **Dokument konfiguracji**
```json
{
  "_id": "config",
  "name": "config", 
  "type": "config",
  "data": {
    "database": "skyPvP",
    "maxPlayers": 100,
    "maintenance": false,
    "spawn": {
      "world": "world",
      "x": 0,
      "y": 64,
      "z": 0
    },
    "economy": {
      "enabled": true,
      "startingMoney": 1000
    }
  },
  "updatedAt": {"$date": "2025-08-31T10:00:00Z"}
}
```

### **Dokument językowy (z kolorami!)**
```json
{
  "_id": "ObjectId(...)",
  "lang": "pl",
  "type": "language", 
  "data": {
    "welcome": "<gradient:#54daf4:#545eb6>Witaj {player}</gradient> &ana serwerze!",
    "goodbye": "&#FF0000Do widzenia {player}!",
    "levelup": "&l&6AWANS! &r&#54DAF4Jesteś teraz na poziomie &{255,215,0}{level}",
    "gui": {
      "title": "<gradient:#FFD700:#FF8C00>Menu Główne</gradient>",
      "buttons": {
        "close": "&#FF0000&lZamknij",
        "next": "&a&lNext Page",
        "back": "&c&lWstecz"
      }
    },
    "item": {
      "sword": {
        "name": "<gradient:#FFD700:#FF8C00>Magiczny Miecz</gradient>",
        "lore": "&7Potężna broń,&#54DAF4+10 Obrażeń,<gradient:#FF0000:#8B0000>Fire Aspect III</gradient>,&eKliknij PPM aby użyć"
      }
    },
    "pvp": {
      "death": {
        "killed_by": "&#FF0000☠ &7Zostałeś zabity przez &#54DAF4{killer} &7używając &e{weapon}"
      },
      "kill": {
        "player": "&#54DAF4⚔ &7Zabiłeś gracza &#FFD700{victim} &7(Seria: &a{streak}&7)"
      }
    }
  },
  "updatedAt": {"$date": "2025-08-31T10:00:00Z"}
}
```

### **Jak dane są przechowywane**
- **Jedna kolekcja MongoDB** = jedno ustawienie wtyczki (np. "MojaWtyczka_Config")
- **Dokument config** = wszystkie ustawienia konfiguracyjne
- **Dokumenty language** = po jednym dla każdego języka (pl, en, de, itp.)
- **Zagnieżdżone obiekty** = klucze z kropkami (`gui.buttons.close`)
- **Automatyczne timestampy** = `updatedAt` przy każdej zmianie

## 📋 **Komendy**

### Komendy dla graczy
- `/language [język]` - Wybierz język lub otwórz GUI (aliasy: `/lang`, `/jezyk`)

### Komendy administracyjne (mongoconfigs)
- `/mongoconfigs reload [kolekcja]` - Przeładuj konfiguracje
- `/mongoconfigs reloadbatch <kolekcje...>` - Przeładuj wiele kolekcji naraz
- `/mongoconfigs stats` - Pokaż statystyki cache i wydajności
- `/mongoconfigs collections` - Lista wszystkich kolekcji
- `/mongoconfigs create <kolekcja> <języki...>` - Utwórz nową kolekcję
- `/mongoconfigs help` - Pomoc
- Aliasy: `/mconfig`, `/mc`

### Komendy zarządzania (configsmanager)
- `/configsmanager reload [kolekcja]` - Przeładuj kolekcje
- `/configsmanager reloadbatch <kolekcje...>` - Batch reload z kontrolą współbieżności
- `/configsmanager stats` - Szczegółowe statystyki cache
- `/configsmanager collections` - Lista kolekcji z językami
- `/configsmanager create <kolekcja> <języki...>` - Utwórz kolekcję
- `/configsmanager info [kolekcja]` - Informacje o kolekcji
- Aliasy: `/cfgmgr`, `/cm`

## 🏗️ Example Plugin Integration

```java
public class MyPlugin extends JavaPlugin {
    
    private ConfigManager configManager;
    
    @Override
    public void onEnable() {
        // Wait for MongoDB Configs to load
        if (!MongoConfigsAPI.isInitialized()) {
            getLogger().severe("MongoDB Configs not found! Install the plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        configManager = MongoConfigsAPI.getConfigManager();
        
        // 🚀 OPTIMIZED: Create collection with all data in batch operations
        setupPluginDataOptimized();
    }
    
    private void setupPluginDataOptimized() {
        // Prepare all config values
        Map<String, Object> configValues = new HashMap<>();
        configValues.put("enabled", true);
        configValues.put("maxLevel", 100);
        configValues.put("economy.enabled", true);
        configValues.put("spawn.world", "world");
        configValues.put("spawn.x", 0);
        configValues.put("spawn.y", 64);
        configValues.put("spawn.z", 0);
        
        // Prepare messages for all languages
        Map<String, Map<String, String>> allMessages = new HashMap<>();
        
        // English messages
        Map<String, String> enMessages = new HashMap<>();
        enMessages.put("levelUp", "<gradient:#54daf4:#545eb6>Level up!</gradient> &aYou are now level &#FFD700{level}!");
        enMessages.put("welcome", "&l&6SERVER &r&8» <gradient:#54daf4:#545eb6>Welcome {player}</gradient> &ato our amazing server!");
        enMessages.put("sword.lore", "&7A powerful weapon,&#54DAF4+15 Attack Damage,<gradient:#FF0000:#8B0000>Fire Aspect III</gradient>");
        enMessages.put("gui.title", "<gradient:#FFD700:#FF8C00>Main Menu</gradient>");
        enMessages.put("gui.close", "&#FF0000&lClose");
        allMessages.put("en", enMessages);
        
        // Polish messages
        Map<String, String> plMessages = new HashMap<>();
        plMessages.put("levelUp", "<gradient:#54daf4:#545eb6>Awans!</gradient> &aJesteś teraz na poziomie &#FFD700{level}!");
        plMessages.put("welcome", "&l&6SERWER &r&8» <gradient:#54daf4:#545eb6>Witaj {player}</gradient> &ana naszym niesamowitym serwerze!");
        plMessages.put("sword.lore", "&7Potężna broń,&#54DAF4+15 Obrażeń,<gradient:#FF0000:#8B0000>Zaklęcie Ognia III</gradient>");
        plMessages.put("gui.title", "<gradient:#FFD700:#FF8C00>Menu Główne</gradient>");
        plMessages.put("gui.close", "&#FF0000&lZamknij");
        allMessages.put("pl", plMessages);
        
        // Create collection with all data in batch operations
        configManager.createCollection("MyPlugin_Config", Set.of("en", "pl"))
                .thenCompose(v -> {
                    getLogger().info("Collection created, setting up config values...");
                    return configManager.setConfigBatch("MyPlugin_Config", configValues);
                })
                .thenCompose(v -> {
                    getLogger().info("Config values set, setting up messages...");
                    return configManager.setMessageBatchMultiLang("MyPlugin_Config", allMessages);
                })
                .thenRun(() -> {
                    getLogger().info("Plugin data setup completed! All configs and messages ready.");
                })
                .exceptionally(throwable -> {
                    getLogger().severe("Failed to setup plugin data: " + throwable.getMessage());
                    return null;
                });
    }
    
    // 🚀 ADVANCED: Setup multiple game systems at once
    private void setupMultipleGameSystems() {
        Map<String, CollectionSetupData> gameSystems = new HashMap<>();
        
        // Level system
        gameSystems.put("levels_system", CollectionSetupData.builder()
            .languages(Set.of("en", "pl"))
            .configValues(Map.of("max_level", 100, "exp_per_level", 1000))
            .languageMessages(Map.of(
                "en", Map.of("level_up", "&6Level up to {level}!", "max_level", "&cMax level reached!"),
                "pl", Map.of("level_up", "&6Awans na poziom {level}!", "max_level", "&cMaksymalny poziom osiągnięty!")
            ))
            .build());
        
        // Bedwars stats
        gameSystems.put("bedwars_stats", CollectionSetupData.builder()
            .languages(Set.of("en", "pl"))
            .configValues(Map.of("track_kills", true, "track_wins", true, "leaderboard_size", 10))
            .languageMessages(Map.of(
                "en", Map.of("kills", "Kills: {kills}", "wins", "Wins: {wins}"),
                "pl", Map.of("kills", "Zabójstwa: {kills}", "wins", "Wygrane: {wins}")
            ))
            .build());
        
        // Prison ranks
        gameSystems.put("prison_ranks", CollectionSetupData.builder()
            .languages(Set.of("en", "pl"))
            .configValues(Map.of("max_rank", 50, "auto_rankup", false, "cost_multiplier", 1.5))
            .languageMessages(Map.of(
                "en", Map.of("rankup", "Ranked up to {rank}!", "insufficient_money", "Need ${cost} to rank up!"),
                "pl", Map.of("rankup", "Awansowano na {rank}!", "insufficient_money", "Potrzebujesz ${cost} do awansu!")
            ))
            .build());
        
        // Create all systems with controlled concurrency (max 2 at once)
        configManager.createCollectionsBatch(gameSystems, 2)
            .thenRun(() -> {
                getLogger().info("All " + gameSystems.size() + " game systems created successfully!");
            })
            .exceptionally(throwable -> {
                getLogger().severe("Failed to create game systems: " + throwable.getMessage());
                return null;
            });
    }
    
    public void sendLevelUpMessage(Player player, int level) {
        LanguageManager langManager = MongoConfigsAPI.getLanguageManager();
        String playerLang = langManager.getPlayerLanguage(player.getUniqueId().toString());
        
        // Automatically colored message!
        String message = configManager.getMessage("MyPlugin_Config", playerLang, "levelUp",
                "player", player.getName(),
                "level", level);
                
        player.sendMessage(message);
        
        // For console/logs - get plain text version
        String plainMessage = configManager.getPlainMessage("MyPlugin_Config", playerLang, "levelUp",
                "player", player.getName(),
                "level", level);
        getLogger().info(plainMessage);
    }
}
```

## ⚡ **Wydajność na dużą skalę**

### **Rzeczywiste możliwości:**
```java
// ✅ 100+ WIADOMOŚCI jednocześnie
Map<String, String> bigMessages = new HashMap<>();
for (int i = 1; i <= 100; i++) {
    bigMessages.put("message_" + i, "&#54DAF4Wiadomość " + i + " z kolorami!");
}
config.setMessageBatch("MojaWtyczka", "pl", bigMessages); // 1 operacja MongoDB

// ✅ 30+ DOKUMENTÓW w 10 KOLEKCJACH  
Set<CollectionSetupData> collections = new HashSet<>();
for (int gameId = 1; gameId <= 10; gameId++) {
    // Każda kolekcja = config + 3 języki = 4 dokumenty
    // 10 kolekcji × 4 dokumenty = 40 dokumentów total
    CollectionSetupData gameData = new CollectionSetupData.Builder("game_" + gameId)
            .addLanguage("pl").addLanguage("en").addLanguage("de")
            .configValues(getGameConfigs(30)) // 30+ konfigów
            .languageMessages(getGameMessages(50)) // 50+ wiadomości per język
            .build();
    collections.add(gameData);
}
config.createCollectionsBatch(collections, 3); // Max 3 kolekcje jednocześnie

// ✅ MASOWE PRZEŁADOWANIE
Set<String> manyCollections = Set.of("game_1", "game_2", "game_3", "game_4", "game_5", "game_6", "game_7", "game_8", "game_9", "game_10");
config.reloadCollectionsBatch(manyCollections, 4); // Max 4 na raz
```

### **Porównanie wydajności:**

**❌ PRZED (wolno):**
```java
// 100 osobnych operacji MongoDB
for (String key : messageKeys) {
    config.setMessage("Wtyczka", "pl", key, messages.get(key)); // 100× operacji
}
// Czas: 30-60 sekund, ryzyko timeout
```

**✅ PO (szybko):**
```java
// 1 operacja MongoDB  
config.setMessageBatch("Wtyczka", "pl", allMessages); // 100+ wiadomości
// Czas: 1-2 sekundy, niezawodne
```

### **Kontrola współbieżności:**
```java
// ✅ BEZPIECZNE dla MongoDB
config.createCollectionsBatch(collections, 3); // Max 3 jednocześnie
config.reloadCollectionsBatch(collections, 4);  // Max 4 jednocześnie
// Nie przeciąża MongoDB, stabilne połączenie
```
```

## �🆘 Support

- **GitHub Issues**: [Report bugs or request features](https://github.com/WTJEE/mongo-configs/issues)

---
