# 🔥 MongoDB Configs API

**Najlepsza biblioteka do konfiguracji Minecraft serwerów. Jedna linia = cała konfiguracja!**

```java
// To wszystko w jednej linii! 🎯
ServerConfig config = cm.loadObject(ServerConfig.class);  // ⚡ GOTOWE!
```

## 🚀 Dlaczego to najlepsze?

| 🏆 Mongo Configs | 💀 YAML/JSON |
|------------------|---------------|
| **1 linia** kodu | 20-50 linii boilerplate |
| ✅ Type Safety | ❌ Runtime errors |
| ✅ Auto-sync serwerów | ❌ Manual reload |
| ✅ Complex objects | ❌ Limited support |
| ✅ Smart caching | ❌ Slow file I/O |

## 📦 Instalacja

```xml
<dependency>
    <groupId>xyz.wtje</groupId>
    <artifactId>mongo-configs-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

## ⚙️ Config (plugin.yml)

```yaml
mongo:
  connection-string: "mongodb://localhost:27017"
  database: "my-server-configs"
  change-streams-enabled: true  # Auto-sync między serwerami

languages:
  default: "en"
  supported: ["en", "pl", "de"]
```

## 🎯 Podstawy - 3 kroki

### 1. Stwórz klasę config (zwykła Java klasa!)
```java
// ✅ Zwykła klasa Java - NIE MUSISZ dziedziczyć niczego!
@ConfigsFileProperties(name = "server-settings")
public class ServerConfig {
    private int maxPlayers = 100;
    private String serverName = "My Server";
    private boolean pvpEnabled = true;
    private List<String> bannedItems = new ArrayList<>();
    
    // gettery/settery (lub użyj Lombok @Data)
}

// 🎪 OPCJONALNIE - extend MongoConfig dla convenience methods:
public class AdvancedConfig extends MongoConfig<AdvancedConfig> {
    private String setting = "default";
    
    // Teraz masz: save(), load(), saveAsync(), loadAsync()
}
```

### 2. Load & Save - jedna linia!
```java
ConfigManager cm = MongoConfigsAPI.getConfigManager();

// 🔥 LOAD - jedna linia (działa z każdą klasą!)
ServerConfig config = cm.loadObject(ServerConfig.class);

// Zmień coś
config.setMaxPlayers(200);
config.getBannedItems().add("bedrock");

// 🔥 SAVE - jedna linia  
cm.saveObject(config);
// ⚡ Wszystkie serwery automatycznie dostaną update!
```

### 3. Messages wielojęzyczne (też zwykłe klasy!)
```java
// ✅ Zwykła klasa - NIE MUSISZ dziedziczyć MongoMessages!
@ConfigsFileProperties(name = "my-messages")
public class MyMessages {
    // Pusta klasa - system automatycznie obsługuje wszystkie języki
}

Messages messages = cm.messagesOf(MyMessages.class);
String msg = messages.get("en", "welcome.player", playerName);

// 🎪 OPCJONALNIE - extend MongoMessages dla dodatkowych metod:
public class AdvancedMessages extends MongoMessages<AdvancedMessages> {
    // Implementuj abstract methods jeśli chcesz custom logic
}
```

## 🚀 Zaawansowane features

### Zwykłe klasy vs Klasy bazowe
```java
// ✅ ZWYKŁA KLASA (preferowane - prostsze!)
@ConfigsFileProperties(name = "simple-config") 
public class SimpleConfig {
    private String value = "default";
    // gettery/settery
}

// 🎪 KLASA Z MongoConfig (opcjonalne convenience methods)
public class ConvenienceConfig extends MongoConfig<ConvenienceConfig> {
    private String value = "default";
    
    // Dodatkowe metody:
    // save() - zapisz siebie
    // load() - przeładuj siebie
    // saveAsync() - async save
    // loadAsync() - async load
}

// Obie działają identycznie:
SimpleConfig simple = cm.loadObject(SimpleConfig.class);        // ⚡
ConvenienceConfig conv = cm.loadObject(ConvenienceConfig.class); // ⚡

// Różnica: convenience config ma extra metody
conv.save();  // to samo co cm.saveObject(conv)
```

### Multi-database & Collections
```java
@ConfigsDatabase("economy-server")     // Inna baza
@ConfigsCollection("shop-data")        // Inna kolekcja  
@ConfigsFileProperties(name = "shop")
public class ShopConfig {
    private Map<String, Double> prices = new HashMap<>();
}
```

### Complex objects - wszystko działa!
```java
public class PlayerData {
    private UUID playerId;
    private LocalDateTime lastSeen;
    private Map<String, Integer> stats = new HashMap<>();
    private List<Achievement> achievements = new ArrayList<>();
    private PlayerBank bank = new PlayerBank();
    
    public static class PlayerBank {
        private double balance;
        private List<Transaction> history = new ArrayList<>();
    }
}

// 🔥 Jedna linia = full serialization!
PlayerData data = cm.loadObject(PlayerData.class);
```

## 🔄 Hot Reload

```bash
# In-game commands
/mongoconfigs reload server-settings    # Konkretna konfiguracja
/mongoconfigs reloadall                 # Wszystkie 
/mongoconfigs collections               # Lista kolekcji
```
## 💡 Praktyczne przykłady

### MMO Server
```java
// 🔥 Każda klasa = jedna linia!
CharacterConfig chars = cm.loadObject(CharacterConfig.class);    // ⚡
GuildConfig guilds = cm.loadObject(GuildConfig.class);           // ⚡  
EconomyConfig economy = cm.loadObject(EconomyConfig.class);      // ⚡
Messages guildMsgs = cm.messagesOf(GuildMessages.class);         // ⚡

// Use them instantly
int maxLevel = chars.getMaxLevel();
long guildCost = guilds.getCreationCost();
String joinMsg = guildMsgs.get(playerLang, "guild.joined", guildName);
```

### Dynamic Shop
```java
@ConfigsFileProperties(name = "dynamic-shop")
public class ShopConfig {
    private Map<String, ItemPrice> items = new HashMap<>();
    private double globalMultiplier = 1.0;
    
    public static class ItemPrice {
        private double basePrice;
        private double currentPrice;
        private int stock;
    }
}

// Business logic
ShopConfig shop = cm.loadObject(ShopConfig.class);              // ⚡
ItemPrice item = shop.getItems().get("diamond");
item.setCurrentPrice(item.getBasePrice() * 1.1);  // +10% price
cm.saveObject(shop);                                           // ⚡
// 💥 Wszystkie serwery natychmiast widzą nową cenę!
```

### Event System  
```java
@ConfigsDatabase("events-db")
@ConfigsFileProperties(name = "events")
public class EventConfig {
    private Map<String, Event> activeEvents = new HashMap<>();
    private Map<UUID, PlayerStats> playerStats = new HashMap<>();
}

EventConfig events = cm.loadObject(EventConfig.class);         // ⚡
events.getActiveEvents().put("pvp-tournament", newTournament);
cm.saveObject(events);                                         // ⚡
// 💥 Wszystkie serwery widzą nowy event!
```

## 🎯 Podsumowanie

**Jedna linia = cała konfiguracja!** 🔥
```java
MyConfig config = cm.loadObject(MyConfig.class);  // ⚡ GOTOWE!
```

**Najlepsza biblioteka config na Minecraft!** 🏆🚀
    
    private ConfigManager configManager;
    private ServerConfig serverConfig;
    
    @Override
    public void onEnable() {
        // API jest już zainicjalizowane przez mongo-configs plugin
        this.configManager = MongoConfigsAPI.getConfigManager();
        
        // Załaduj konfigurację
        this.serverConfig = configManager.getConfigOrGenerate(ServerConfig.class, 
            () -> new ServerConfig()).join();
        
        getLogger().info("Max players: " + serverConfig.getMaxPlayers());
    }
}
```

### Event handler z wiadomościami

```java
public class PlayerJoinListener implements Listener {
    
    private final Messages messages;
    
    public PlayerJoinListener() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        this.messages = cm.messagesOf(WelcomeMessages.class);
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerLang = getPlayerLanguage(player); // twoja logika
        
        String welcome = messages.get(playerLang, "welcome.message", 
            player.getName(), player.getLevel());
        
        player.sendMessage(welcome);
    }
}
```

### Komenda z konfiguracją

```java
public class ShopCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        
        // Załaduj aktualną konfigurację sklepu
        ShopConfig shop = cm.getObject(ShopConfig.class);
        
        if (!shop.isShopEnabled()) {
            sender.sendMessage("Sklep jest wyłączony!");
            return true;
        }
        
        // Użyj konfiguracji...
        double price = shop.getPrice("diamond");
        sender.sendMessage("Cena diamentu: " + price);
        
        return true;
    }
}
```

## 🔧 Zarządzanie językami graczy

```java
LanguageManager lm = MongoConfigsAPI.getLanguageManager();

// Ustaw język gracza
lm.setPlayerLanguage(player.getUniqueId().toString(), "pl");

// Pobierz język gracza  
String playerLang = lm.getPlayerLanguage(player.getUniqueId().toString());

// Sprawdź obsługiwane języki
String[] supported = lm.getSupportedLanguages(); // ["en", "pl", "de", ...]
```

## ⚡ Performance i Cache

### Strategia ładowania
- **Startup**: Ładowanie metadanych (szybkie)
- **Runtime**: Lazy loading dokumentów (na żądanie)
- **Fresh DB**: Mass creation wszystkich struktur (jednorazowo)

### Cache behavior  
```java
// Pierwsze wywołanie → MongoDB query
ServerConfig config1 = cm.getObject(ServerConfig.class);

// Drugie wywołanie → Cache hit ⚡
ServerConfig config2 = cm.getObject(ServerConfig.class);

// Change Streams automatycznie invalidują cache gdy ktoś zmieni w MongoDB
```

### Batch operations
```java
// Przeładuj kilka kolekcji naraz
Set<String> collections = Set.of("server-settings", "gui-messages", "shop-config");
cm.reloadCollectionsBatch(collections, 3); // max 3 równocześnie
```

## 🌐 Multi-server setup

### Serwer A zmienia konfigurację:
```java
ServerConfig config = cm.getObject(ServerConfig.class);
config.setMaxPlayers(300);
cm.setObject(config); // Zapisuje w MongoDB
```

### Serwery B, C, D automatycznie się aktualizują:
```
MongoDB Change Stream wykrywa zmianę
    ↓
Cache invalidation na wszystkich serwerach  
    ↓
Następne wywołanie pobiera nowe dane
```

## 🛠️ API Reference

### ConfigManager
- `setObject(T pojo)` - Zapisz obiekt z @ConfigsFileProperties
- `getObject(Class<T> type)` - Odczytaj obiekt
- `getConfigOrGenerate(Class<T> type, Supplier<T> generator)` - Load-or-create
- `set(String id, T value)` - Zapisz z explicit ID
- `get(String id, Class<T> type)` - Odczytaj z explicit ID
- `findById(String id)` - Dostęp do Messages
- `messagesOf(Class<?> type)` - Messages z adnotacji

### Messages
- `get(String lang, String key)` - Pobierz wiadomość
- `get(String lang, String key, Object... placeholders)` - Z placeholderami
- `get(String lang, String key, Class<T> type)` - Typed message

### LanguageManager
- `setPlayerLanguage(String playerId, String language)` - Ustaw język gracza
- `getPlayerLanguage(String playerId)` - Pobierz język gracza
- `getSupportedLanguages()` - Lista obsługiwanych języków

## 🎮 Praktyczne przykłady - Każda klasa = Jedna linia!

### 🏰 MMO Serwer - Kompletny system
```java
// === KONFIGURACJE ===
@ConfigsDatabase("mmo-main")
@ConfigsFileProperties(name = "character-settings")
public class CharacterConfig {
    private int maxLevel = 100;
    private double expMultiplier = 1.0;
    private Map<String, Integer> classBonuses = Map.of("warrior", 10, "mage", 15);
    private List<String> allowedClasses = Arrays.asList("warrior", "mage", "archer");
    private Map<Integer, List<String>> levelRewards = new HashMap<>();
}

@ConfigsDatabase("mmo-guilds")  
@ConfigsFileProperties(name = "guild-settings")
public class GuildConfig {
    private int maxMembers = 50;
    private long creationCost = 10000;
    private Map<String, GuildPerk> availablePerks = new HashMap<>();
    private List<GuildWar> activeWars = new ArrayList<>();
    
    public static class GuildPerk {
        private String name;
        private int cost;
        private List<String> benefits;
    }
}

// === UŻYCIE - JEDNA LINIA NA KLASĘ! ===
public class MMOPlugin extends JavaPlugin {
    
    @Override 
    public void onEnable() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        
        // 🔥 JEDNA LINIA = CAŁA KONFIGURACJA!
        CharacterConfig chars = cm.loadObject(CharacterConfig.class);    // ⚡
        GuildConfig guilds = cm.loadObject(GuildConfig.class);           // ⚡
        Messages guildMsgs = cm.messagesOf(GuildMessages.class);         // ⚡
        
        getLogger().info("Max level: " + chars.getMaxLevel());
        getLogger().info("Guild creation cost: " + guilds.getCreationCost());
        
        // Modyfikacja w locie
        chars.setMaxLevel(120);                    // Zmień
        cm.saveObject(chars);                      // Zapisz ⚡
        // Wszystkie serwery dostaną update przez Change Streams!
    }
}
```

### 🏕️ Survival Serwer - Ekonomia i sklepy
```java
@ConfigsFileProperties(name = "economy")
public class EconomyConfig {
    private double startingMoney = 1000.0;
    private Map<String, Double> itemPrices = new HashMap<>();
    private Map<String, ShopNPC> shopNPCs = new HashMap<>();
    private List<DailyDeal> dailyDeals = new ArrayList<>();
    private boolean economyEnabled = true;
    
    public static class ShopNPC {
        private String name;
        private Location location;
        private Map<String, Double> inventory;
        private List<String> buyableItems;
    }
    
    public static class DailyDeal {
        private String item;
        private double originalPrice;
        private double discountedPrice;
        private LocalDate validUntil;
    }
}

@ConfigsFileProperties(name = "spawn-messages")
public class SpawnMessages {
    // Automatyczne wsparcie dla wszystkich języków z config.yml
}

// === ELEGANCKIE UŻYCIE ===
public class EconomyManager {
    
    private final EconomyConfig economy;
    private final Messages messages;
    
    public EconomyManager() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        
        // 🔥 DWA OBIEKTY = DWA WYWOŁANIA!
        this.economy = cm.loadObject(EconomyConfig.class);        // ⚡
        this.messages = cm.messagesOf(SpawnMessages.class);       // ⚡
    }
    
    public void buyItem(Player player, String item) {
        Double price = economy.getItemPrices().get(item);
        if (price == null) {
            player.sendMessage("Item not found!");
            return;
        }
        
        // Użyj wielojęzycznych wiadomości
        String lang = getPlayerLanguage(player);
        String msg = messages.get(lang, "shop.purchase.success", 
            item, price);
        player.sendMessage(msg);
        
        // Aktualizuj ceny (dynamic pricing)
        economy.getItemPrices().put(item, price * 1.01); // +1% po każdym zakupie
        
        // 🔥 JEDNA LINIA = ZAPISANE!
        MongoConfigsAPI.getConfigManager().saveObject(economy);   // ⚡
    }
}
```

### 🎯 PvP Arena - Kompleksny system
```java
@ConfigsDatabase("pvp-server")
@ConfigsFileProperties(name = "arena-config")
public class ArenaConfig {
    private Map<String, Arena> arenas = new HashMap<>();
    private List<Tournament> activeTournaments = new ArrayList<>();
    private Map<UUID, PlayerPvPStats> playerStats = new HashMap<>();
    private ArenaSettings globalSettings = new ArenaSettings();
    
    public static class Arena {
        private String name;
        private Location spawnPoint1, spawnPoint2;
        private List<UUID> currentPlayers = new ArrayList<>();
        private Map<String, Object> gameSettings = new HashMap<>();
        private ArenaStatus status = ArenaStatus.WAITING;
    }
    
    public static class Tournament {
        private String name;
        private List<UUID> participants;
        private Map<UUID, Integer> scores = new HashMap<>();
        private LocalDateTime startTime;
        private TournamentBracket bracket;
    }
    
    public static class PlayerPvPStats {
        private int kills = 0;
        private int deaths = 0;
        private double rating = 1000.0;
        private List<Match> matchHistory = new ArrayList<>();
        private Map<String, Integer> weaponPreferences = new HashMap<>();
    }
}

@ConfigsFileProperties(name = "pvp-messages")
public class PvPMessages {
    // Auto-languages: kill notifications, tournament announcements, etc.
}

// === SUPER PROSTE UŻYCIE ===
public class PvPManager {
    
    public void startArenaMatch(Player p1, Player p2, String arenaName) {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        
        // 🔥 JEDNA LINIA = CAŁY CONFIG!
        ArenaConfig config = cm.loadObject(ArenaConfig.class);           // ⚡
        Messages pvpMsgs = cm.messagesOf(PvPMessages.class);            // ⚡
        
        Arena arena = config.getArenas().get(arenaName);
        if (arena == null) {
            p1.sendMessage("Arena not found!");
            return;
        }
        
        // Dodaj graczy do areny
        arena.getCurrentPlayers().addAll(Arrays.asList(p1.getUniqueId(), p2.getUniqueId()));
        arena.setStatus(ArenaStatus.IN_PROGRESS);
        
        // Aktualizuj statystyki
        PlayerPvPStats stats1 = config.getPlayerStats().computeIfAbsent(p1.getUniqueId(), 
            k -> new PlayerPvPStats());
        PlayerPvPStats stats2 = config.getPlayerStats().computeIfAbsent(p2.getUniqueId(), 
            k -> new PlayerPvPStats());
        
        // 🔥 JEDNA LINIA = WSZYSTKO ZAPISANE!
        cm.saveObject(config);                                          // ⚡
        
        // Powiadom wszystkich graczy  
        String announcement = pvpMsgs.get("en", "arena.match.started", 
            p1.getName(), p2.getName(), arenaName);
        Bukkit.broadcastMessage(announcement);
        
        // 💥 CHANGE STREAMS = WSZYSTKIE SERWERY WIEDZĄ O MECZU!
    }
}
```

### 🏪 Sklep z dynamicznymi cenami
```java
@ConfigsFileProperties(name = "dynamic-shop")
public class DynamicShopConfig {
    private Map<String, ItemData> items = new HashMap<>();
    private Map<String, List<Sale>> salesHistory = new HashMap<>();
    private ShopAnalytics analytics = new ShopAnalytics();
    
    public static class ItemData {
        private double basePrice;
        private double currentPrice;
        private int stock;
        private List<PriceChange> priceHistory = new ArrayList<>();
        private Map<String, Double> playerDemand = new HashMap<>(); // UUID -> demand factor
    }
    
    public static class Sale {
        private UUID buyer;
        private double pricePaid;
        private int quantity;
        private LocalDateTime timestamp;
    }
    
    public static class ShopAnalytics {
        private Map<String, Integer> itemPopularity = new HashMap<>();
        private double totalRevenue = 0.0;
        private Map<String, Double> dailyStats = new HashMap<>();
    }
}

// === BUSINESS LOGIC ===
public class DynamicShopManager {
    
    public void buyItem(Player player, String itemName, int quantity) {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        
        // 🔥 JEDNA LINIA = CAŁY SKLEP!
        DynamicShopConfig shop = cm.loadObject(DynamicShopConfig.class);  // ⚡
        
        ItemData item = shop.getItems().get(itemName);
        if (item == null || item.getStock() < quantity) {
            player.sendMessage("Not enough stock!");
            return;
        }
        
        // Logika biznesowa
        double totalPrice = item.getCurrentPrice() * quantity;
        item.setStock(item.getStock() - quantity);
        
        // Aktualizuj cenę na podstawie popytu (dynamic pricing!)
        double demandFactor = calculateDemand(item, quantity);
        item.setCurrentPrice(item.getBasePrice() * demandFactor);
        
        // Dodaj do historii
        shop.getSalesHistory().computeIfAbsent(itemName, k -> new ArrayList<>())
            .add(new Sale(player.getUniqueId(), totalPrice, quantity, LocalDateTime.now()));
        
        // Aktualizuj analytics
        shop.getAnalytics().getItemPopularity().merge(itemName, quantity, Integer::sum);
        shop.getAnalytics().setTotalRevenue(shop.getAnalytics().getTotalRevenue() + totalPrice);
        
        // 🔥 JEDNA LINIA = WSZYSTKO ZAPISANE + SYNC NA WSZYSTKICH SERWERACH!
        cm.saveObject(shop);                                              // ⚡
        
        player.sendMessage("Bought " + quantity + "x " + itemName + " for $" + totalPrice);
    }
}
```

### 🎪 Event System z rankingami
```java
@ConfigsDatabase("events-db")
@ConfigsFileProperties(name = "event-system")
public class EventSystemConfig {
    private Map<String, Event> activeEvents = new HashMap<>();
    private Map<UUID, PlayerEventStats> playerStats = new HashMap<>();
    private List<EventTemplate> eventTemplates = new ArrayList<>();
    private EventScheduler scheduler = new EventScheduler();
    private Map<String, List<Reward>> eventRewards = new HashMap<>();
    
    public static class Event {
        private String id, name, type;
        private LocalDateTime startTime, endTime;
        private Map<UUID, Integer> participants = new HashMap<>(); // player -> score
        private Map<String, Object> settings = new HashMap<>();
        private List<EventPhase> phases = new ArrayList<>();
        private EventStatus status = EventStatus.PLANNED;
    }
    
    public static class PlayerEventStats {
        private int eventsParticipated = 0;
        private int eventsWon = 0;
        private double totalScore = 0.0;
        private Map<String, Integer> eventTypeStats = new HashMap<>();
        private List<Achievement> achievements = new ArrayList<>();
    }
}

// === MEGA PROSTY EVENT MANAGER ===
public class EventManager {
    
    public void joinEvent(Player player, String eventId) {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        
        // 🔥 JEDNA LINIA = CAŁY SYSTEM EVENTÓW!
        EventSystemConfig events = cm.loadObject(EventSystemConfig.class);  // ⚡
        Messages eventMsgs = cm.messagesOf(EventMessages.class);            // ⚡
        
        Event event = events.getActiveEvents().get(eventId);
        if (event == null) {
            String msg = eventMsgs.get(getPlayerLang(player), "event.not.found", eventId);
            player.sendMessage(msg);
            return;
        }
        
        // Dodaj gracza
        event.getParticipants().put(player.getUniqueId(), 0);
        
        // Aktualizuj statystyki gracza
        PlayerEventStats stats = events.getPlayerStats()
            .computeIfAbsent(player.getUniqueId(), k -> new PlayerEventStats());
        stats.setEventsParticipated(stats.getEventsParticipated() + 1);
        
        // 🔥 JEDNA LINIA = ZAPISANE + SYNC WSZĘDZIE!
        cm.saveObject(events);                                              // ⚡
        
        // Powiadom gracza
        String joinMsg = eventMsgs.get(getPlayerLang(player), "event.joined", 
            event.getName(), event.getParticipants().size());
        player.sendMessage(joinMsg);
        
        // 💥 INNE SERWERY NATYCHMIAST WIDZĄ NOWEGO UCZESTNIKA!
    }
    
    public void endEvent(String eventId, UUID winnerId) {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        
        // 🔥 JEDNA LINIA = LOAD!
        EventSystemConfig events = cm.loadObject(EventSystemConfig.class);  // ⚡
        
        Event event = events.getActiveEvents().get(eventId);
        event.setStatus(EventStatus.FINISHED);
        
        // Aktualizuj statystyki zwycięzcy
        PlayerEventStats winnerStats = events.getPlayerStats().get(winnerId);
        winnerStats.setEventsWon(winnerStats.getEventsWon() + 1);
        
        // Przenieś do historii
        events.getActiveEvents().remove(eventId);
        
        // 🔥 JEDNA LINIA = SAVE!
        cm.saveObject(events);                                              // ⚡
        
        // 💥 WSZYSTKIE SERWERY NATYCHMIAST WIEDZĄ O KOŃCU EVENTU!
    }
}
```

### 🛡️ Factions War System
```java
@ConfigsDatabase("factions-war")
@ConfigsFileProperties(name = "war-system")
public class WarSystemConfig {
    private Map<String, Faction> factions = new HashMap<>();
    private List<War> activeWars = new ArrayList<>();
    private Map<String, Territory> territories = new HashMap<>();
    private WarSettings settings = new WarSettings();
    private Map<UUID, PlayerWarStats> playerWarStats = new HashMap<>();
    
    public static class Faction {
        private String name;
        private UUID leader;
        private List<UUID> members = new ArrayList<>();
        private Map<String, Integer> resources = new HashMap<>();
        private List<String> controlledTerritories = new ArrayList<>();
        private double power = 100.0;
        private FactionBank bank = new FactionBank();
    }
    
    public static class War {
        private String id;
        private String attackerFaction, defenderFaction;
        private LocalDateTime startTime;
        private Map<String, Integer> battleResults = new HashMap<>();
        private List<Battle> battles = new ArrayList<>();
        private WarStatus status = WarStatus.ONGOING;
    }
}

// === WOJNA W JEDNEJ LINII! ===
public class WarManager {
    
    public void declareWar(String attackerName, String defenderName) {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        
        // 🔥 JEDNA LINIA = CAŁY SYSTEM WOJEN!
        WarSystemConfig wars = cm.loadObject(WarSystemConfig.class);         // ⚡
        Messages warMsgs = cm.messagesOf(WarMessages.class);                // ⚡
        
        Faction attacker = wars.getFactions().get(attackerName);
        Faction defender = wars.getFactions().get(defenderName);
        
        // Stwórz nową wojnę
        War newWar = new War();
        newWar.setId(UUID.randomUUID().toString());
        newWar.setAttackerFaction(attackerName);
        newWar.setDefenderFaction(defenderName);
        newWar.setStartTime(LocalDateTime.now());
        
        wars.getActiveWars().add(newWar);
        
        // 🔥 JEDNA LINIA = WOJNA ZAPISANA!
        cm.saveObject(wars);                                                // ⚡
        
        // 💥 WSZYSTKIE SERWERY NATYCHMIAST WIEDZĄ O WOJNIE!
        
        // Powiadom wszystkich
        for (UUID memberId : attacker.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                String msg = warMsgs.get(getPlayerLang(member), "war.declared.attacker", defenderName);
                member.sendMessage(msg);
            }
        }
    }
}
```

### 🏆 Minigames Hub
```java
@ConfigsFileProperties(name = "minigames")
public class MinigameConfig {
    private Map<String, Minigame> availableGames = new HashMap<>();
    private Map<UUID, PlayerMinigameStats> playerStats = new HashMap<>();
    private List<Tournament> tournaments = new ArrayList<>();
    private MinigameQueue queue = new MinigameQueue();
    
    public static class Minigame {
        private String name, type;
        private int minPlayers, maxPlayers;
        private Map<String, Object> gameSettings = new HashMap<>();
        private List<GameMap> maps = new ArrayList<>();
        private boolean enabled = true;
    }
    
    public static class PlayerMinigameStats {
        private Map<String, Integer> gamesPlayed = new HashMap<>();
        private Map<String, Integer> gamesWon = new HashMap<>();
        private double totalScore = 0.0;
        private List<Achievement> unlockedAchievements = new ArrayList<>();
    }
}

// === MINIGAME MANAGER ===
public class MinigameManager {
    
    public void joinGame(Player player, String gameName) {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        
        // 🔥 JEDNA LINIA = CAŁY SYSTEM MINIGIER!
        MinigameConfig games = cm.loadObject(MinigameConfig.class);          // ⚡
        
        Minigame game = games.getAvailableGames().get(gameName);
        if (!game.isEnabled()) {
            player.sendMessage("Game disabled!");
            return;
        }
        
        // Dodaj do queue
        games.getQueue().addPlayer(player.getUniqueId(), gameName);
        
        // Aktualizuj statystyki
        PlayerMinigameStats stats = games.getPlayerStats()
            .computeIfAbsent(player.getUniqueId(), k -> new PlayerMinigameStats());
        stats.getGamesPlayed().merge(gameName, 1, Integer::sum);
        
        // 🔥 JEDNA LINIA = SAVE!
        cm.saveObject(games);                                               // ⚡
        
        player.sendMessage("Joined " + gameName + " queue!");
        
        // 💥 CHANGE STREAMS = INNE SERWERY WIDZĄ GRACZA W QUEUE!
    }
}
```

## 🎯 **DLACZEGO TO JEST ZAJEBISTE:**

### ⚡ **Każda klasa = Jedna linia:**
```java
// Zamiast 50 linii boilerplate:
ServerConfig config = cm.loadObject(ServerConfig.class);     // ⚡ GOTOWE!
EconomyConfig economy = cm.loadObject(EconomyConfig.class);  // ⚡ GOTOWE!
Messages msgs = cm.messagesOf(GuiMessages.class);           // ⚡ GOTOWE!
```

### 🔥 **Modyfikacja = Jedna linia:**
```java
// Zmień cokolwiek w obiekcie...
config.setMaxPlayers(500);
economy.getItemPrices().put("diamond", 999.0);
config.getFactions().get("dragons").setPower(150.0);

// I zapisz jedną linią:
cm.saveObject(config);   // 💥 CHANGE STREAMS = SYNC WSZĘDZIE!
```

### 💻 **Zero konfiguracji JSON/YAML:**
```java
// ❌ Nie ma tego:
// - Ręczne parsowanie YAML
// - Sprawdzanie null values  
// - Konwersje typów
// - File I/O operations
// - Synchronizacja między serwerami

// ✅ Jest to:
ConfigClass data = cm.loadObject(ConfigClass.class);  // GOTOWE! 🎯
```

**NAJLEPSZA BIBLIOTEKA DO MINECRAFT!** 🏆

## 🔄 Hot Reload Examples

```bash
# Reload ekonomii na wszystkich serwerach
/mongoconfigs reload economy

# Reload wszystkich wiadomości  
/mongoconfigs reload spawn-messages

# Reload wszystkiego (ostrożnie!)
/mongoconfigs reloadall

# Sprawdź dostępne kolekcje
/mongoconfigs collections
```

## 🏆 Best Practices

### 1. **Używaj Load-or-Generate**
```java
// ✅ Dobre - zawsze masz poprawny obiekt
ServerConfig config = cm.getConfigOrGenerate(ServerConfig.class, 
    () -> new ServerConfig());

// ❌ Złe - może być null
ServerConfig config = cm.getObject(ServerConfig.class);
if (config == null) { /* boilerplate... */ }
```

### 2. **Sync convenience methods**
```java
// Dla prostych operacji
cm.saveObject(config);          // sync setObject()
ServerConfig c = cm.loadObject(ServerConfig.class); // sync getObject()

// Dla performance-critical - używaj async
cm.setObject(config).thenRun(() -> {
    // callback after save
});
```

### 3. **Struktura wiadomości**
```yaml
# W MongoDB dla języka "pl":
welcome:
  message: "Witaj {0}!"
  subtitle: "Poziom: {1}"
gui:
  buttons:
    confirm: "Potwierdź"
    cancel: "Anuluj"
shop:
  prices:
    diamond: "Diament: {price} monet"
```

### 4. **Error handling**
```java
try {
    ServerConfig config = cm.getObject(ServerConfig.class);
    // użyj config...
} catch (Exception e) {
    // fallback lub log error
    getLogger().warning("Nie można załadować konfiguracji: " + e.getMessage());
}
```

## 🚨 Troubleshooting

### Problemy z połączeniem
```bash
# Sprawdź połączenie
/mongoconfigs collections

# Test reloadu
/mongoconfigs reload server-settings

# Debug informacje
/mongoconfigs testcollections
```

### Cache issues
```java
// Wyczyść cache jeśli coś się zepsuło
cm.invalidateCache();

// Wymuś reload konkretnej kolekcji
cm.reloadCollection("problematic-collection");
```

### Migration dokumentów
```java
// Aktualizacja wersji konfiguracji
OldConfig old = cm.getObject(OldConfig.class);
if (old != null) {
    NewConfig migrated = migrateFromOld(old);
    cm.setObject(migrated);
}
```

## 📈 Performance Tips

### 1. **Batch reload** przy starcie
```java
Set<String> collections = Set.of("server-settings", "gui-messages", "economy");
cm.reloadCollectionsBatch(collections, 3); // max 3 równocześnie
```

### 2. **Cache warm-up**
```java
// Pre-load często używanych configów
cm.getObject(ServerConfig.class);
cm.getObject(EconomyConfig.class);
messages.get("en", "common.messages"); // pre-cache messages
```

### 3. **Change Streams monitoring**
- Włącz `change-streams-enabled: true` dla multi-server
- System automatycznie synchronizuje zmiany
- Zero manual work - wszystko dzieje się w tle

## 🎯 Migration Guide

### Z tradycyjnych YAML files:

```java
// Zamiast:
FileConfiguration config = YamlConfiguration.loadConfiguration(file);
int maxPlayers = config.getInt("max-players", 100);

// Używaj:
@ConfigsFileProperties(name = "server-config")
public class ServerConfig {
    private int maxPlayers = 100;
}
ServerConfig config = cm.getObject(ServerConfig.class);
int maxPlayers = config.getMaxPlayers();
```

### Z locale files:

```java
// Zamiast wielu plików:
// messages_en.yml, messages_pl.yml, messages_de.yml...

// Używaj:
Messages messages = cm.messagesOf(MyMessages.class);
String msg = messages.get(playerLang, "welcome.message");
```

## 🏁 **PODSUMOWANIE - DLACZEGO TO NAJLEPSZE API NA ŚWIECIE**

### 🎯 **JEDNA LINIA = MAGIC**
```java
// To wszystko to jedna linia:
MyComplexConfig config = cm.loadObject(MyComplexConfig.class);  // 🔥
// - Automatyczny load z MongoDB
// - Full type safety
// - Smart caching  
// - Error handling
// - Change streams sync
// - Jackson serialization
// - WSZYSTKO W JEDNEJ LINII! 🤯
```

### 🚀 **ROZWÓJ BŁYSKAWICZNY**
```java
// ❌ TRADYCYJNE BIBLIOTEKI: 50+ linii boilerplate na każdą klasę
// ✅ MONGO CONFIGS: 1 linia na klasę

// Ile czasu zaoszczędzisz:
// 10 klas config = 500 linii boilerplate → 10 linii
// 20 klas config = 1000 linii boilerplate → 20 linii  
// 50 klas config = 2500 linii boilerplate → 50 linii

// = TYSIĄCE GODZIN ZAOSZCZĘDZONE! ⏰💰
```

### 🏆 **MongoDB Configs API zapewnia:**

- ✅ **🎯 Type Safety** - Kompiler pilnuje typów, zero runtime errors
- ✅ **🚀 Zero Boilerplate** - Jedna linia zamiast 20-50 linii kodu  
- ✅ **⚡ Multi-server Sync** - Change Streams = automatyczna synchronizacja wszędzie
- ✅ **🔥 Hot Reload** - Zmiany bez restartu serwera
- ✅ **🎪 Flexible** - Adnotacje do kontroli baz danych i kolekcji
- ✅ **🏎️ Performance** - Smart caching + lazy loading + MongoDB
- ✅ **🛡️ Reliable** - Fallbacks, error handling, connection pooling
- ✅ **🌍 Multi-language** - Automatyczne wsparcie wielojęzyczności
- ✅ **🔧 IDE Support** - Pełne wsparcie IntelliJ/Eclipse
- ✅ **📊 Complex Objects** - Lists, Maps, nested objects, wszystko native

### 🎮 **PERFECT DLA MINECRAFT:**

```java
// MMO Serwer:
CharacterConfig chars = cm.loadObject(CharacterConfig.class);     // ⚡
GuildConfig guilds = cm.loadObject(GuildConfig.class);           // ⚡
EconomyConfig economy = cm.loadObject(EconomyConfig.class);      // ⚡
Messages guildMsgs = cm.messagesOf(GuildMessages.class);         // ⚡

// PvP Serwer:
ArenaConfig arenas = cm.loadObject(ArenaConfig.class);           // ⚡
TournamentConfig tournaments = cm.loadObject(TournamentConfig.class); // ⚡

// Skyblock Serwer:
IslandConfig islands = cm.loadObject(IslandConfig.class);        // ⚡
ShopConfig shop = cm.loadObject(ShopConfig.class);              // ⚡

// Network Serwerów:
// Wszystkie serwery automatycznie zsynchronizowane! 🌐
```

### 🔥 **JAK TO DZIAŁA W PRAKTYCE:**

1. **🎯 Piszesz klasę** - Zwykła Java class z getterami/setterami
2. **⚡ Jedna linia load** - `MyConfig config = cm.loadObject(MyConfig.class);`
3. **🚀 Używasz** - `config.getMaxPlayers()`, `config.setServerName("New name")`
4. **💾 Jedna linia save** - `cm.saveObject(config);`
5. **🌐 Auto-sync** - Wszystkie serwery natychmiast widzą zmiany!

### 🏆 **NAJLEPSZA BIBLIOTEKA CONFIG NA MINECRAFT!**

**Przyszłość zarządzania konfiguracją jest tutaj!** 🚀🔥

**MONGO CONFIGS = GAME CHANGER!** 🎮⚡🏆

---

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/WTJEE/mongo-configs/issues)
- **Wiki**: [Dokumentacja](https://github.com/WTJEE/mongo-configs/wiki)
- **Discord**: [Support Server](#)

## 📄 License

MIT License - Zobacz [LICENSE](LICENSE) dla szczegółów.
