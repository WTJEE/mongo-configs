# 🔥 MongoDB Configs API# 🔥 MongoDB Configs API



**Najlepsza biblioteka do konfiguracji Minecraft serwerów. Jedna linia = cała konfiguracja!****Najlepsza biblioteka do konfiguracji Minecraft serwerów. Jedna linia = cała konfiguracja!**



```java```java

// To wszystko w jednej linii! 🎯// To wszystko w jednej linii! 🎯

ServerConfig config = cm.loadObject(ServerConfig.class);  // ⚡ GOTOWE!ServerConfig config = cm.loadObject(ServerConfig.class);  // ⚡ GOTOWE!

``````



## 🚀 Dlaczego to najlepsze?## 🚀 Dlaczego to najlepsze?



| 🏆 Mongo Configs | 💀 YAML/JSON || 🏆 Mongo Configs | 💀 YAML/JSON |

|------------------|---------------||------------------|---------------|

| **1 linia** kodu | 20-50 linii boilerplate || **1 linia** kodu | 20-50 linii boilerplate |

| ✅ Type Safety | ❌ Runtime errors || ✅ Type Safety | ❌ Runtime errors |

| ✅ Auto-sync serwerów | ❌ Manual reload || ✅ Auto-sync serwerów | ❌ Manual reload |

| ✅ Complex objects | ❌ Limited support || ✅ Complex objects | ❌ Limited support |

| ✅ Smart caching | ❌ Slow file I/O || ✅ Smart caching | ❌ Slow file I/O |



## 📚 Separated Documentation## 📦 Instalacja



This library supports two powerful approaches for configuration management:```xml

<dependency>

### 🎯 Class-Based Configuration    <groupId>xyz.wtje</groupId>

For structured, type-safe configurations with Java classes and annotations.    <artifactId>mongo-configs-api</artifactId>

    <version>1.0.0</version>

📖 **[Complete Guide → Wiki: Class-Based Configuration](https://github.com/WTJEE/mongo-configs/wiki/Class-Based-Configuration)**</dependency>

- Basic usage with annotations```

- Advanced features (validation, nested objects)

- Multi-database & collection support## ⚙️ Config (plugin.yml)

- Hot reload capabilities

```yaml

### 🔑 Key-Object Storagemongo:

For flexible, dynamic configurations using MongoDB documents directly.  connection-string: "mongodb://localhost:27017"

  database: "my-server-configs"

📖 **[Complete Guide → Wiki: Key-Object Storage](https://github.com/WTJEE/mongo-configs/wiki/Key-Object-Storage)**  change-streams-enabled: true  # Auto-sync między serwerami

- Hierarchical key-value operations

- Batch operations and patternslanguages:

- Player data management  default: "en"

- Economy systems integration  supported: ["en", "pl", "de"]

```

## 📦 Instalacja

## 🎯 Podstawy - 3 kroki

```xml

<dependency>### 1. Stwórz klasę config (zwykła Java klasa!)

    <groupId>xyz.wtje</groupId>```java

    <artifactId>mongo-configs-api</artifactId>// ✅ Zwykła klasa Java - NIE MUSISZ dziedziczyć niczego!

    <version>1.0.0</version>@ConfigsFileProperties(name = "server-settings")

</dependency>public class ServerConfig {

```    private int maxPlayers = 100;

    private String serverName = "My Server";

## ⚙️ Config (plugin.yml)    private boolean pvpEnabled = true;

    private List<String> bannedItems = new ArrayList<>();

```yaml    

mongo:    // gettery/settery (lub użyj Lombok @Data)

  connection-string: "mongodb://localhost:27017"}

  database: "my-server-configs"

  change-streams-enabled: true  # Auto-sync między serwerami// 🎪 OPCJONALNIE - extend MongoConfig dla convenience methods:

public class AdvancedConfig extends MongoConfig<AdvancedConfig> {

languages:    private String setting = "default";

  default: "en"    

  supported: ["en", "pl", "de"]    // Teraz masz: save(), load(), saveAsync(), loadAsync()

```}

```

## 🎯 Podstawy - 3 kroki

### 2. Load & Save - jedna linia!

### 1. Stwórz klasę config (zwykła Java klasa!)```java

```javaConfigManager cm = MongoConfigsAPI.getConfigManager();

// ✅ Zwykła klasa Java - NIE MUSISZ dziedziczyć niczego!

@ConfigsFileProperties(name = "server-settings")// 🔥 LOAD - jedna linia (działa z każdą klasą!)

public class ServerConfig {ServerConfig config = cm.loadObject(ServerConfig.class);

    private int maxPlayers = 100;

    private String serverName = "My Server";// Zmień coś

    private boolean pvpEnabled = true;config.setMaxPlayers(200);

    private List<String> bannedItems = new ArrayList<>();config.getBannedItems().add("bedrock");



    // gettery/settery (lub użyj Lombok @Data)// 🔥 SAVE - jedna linia  

}cm.saveObject(config);

// ⚡ Wszystkie serwery automatycznie dostaną update!

// 🎪 OPCJONALNIE - extend MongoConfig dla convenience methods:```

public class AdvancedConfig extends MongoConfig<AdvancedConfig> {

    private String setting = "default";### 3. Messages wielojęzyczne (też zwykłe klasy!)

```java

    // Teraz masz: save(), load(), saveAsync(), loadAsync()// ✅ Zwykła klasa - NIE MUSISZ dziedziczyć MongoMessages!

}@ConfigsFileProperties(name = "my-messages")

```public class MyMessages {

    // Pusta klasa - system automatycznie obsługuje wszystkie języki

### 2. Load & Save - jedna linia!}

```java

ConfigManager cm = MongoConfigsAPI.getConfigManager();Messages messages = cm.messagesOf(MyMessages.class);

String msg = messages.get("en", "welcome.player", playerName);

// 🔥 LOAD - jedna linia (działa z każdą klasą!)

ServerConfig config = cm.loadObject(ServerConfig.class);// 🎪 OPCJONALNIE - extend MongoMessages dla dodatkowych metod:

public class AdvancedMessages extends MongoMessages<AdvancedMessages> {

// Zmień coś    // Implementuj abstract methods jeśli chcesz custom logic

config.setMaxPlayers(200);}

config.getBannedItems().add("bedrock");```



// 🔥 SAVE - jedna linia## 🌍 System językowy - Kompletny przewodnik

cm.saveObject(config);

// ⚡ Wszystkie serwery automatycznie dostaną update!### 🎮 Komendy dla graczy

```

```bash

### 3. Messages wielojęzyczne (też zwykłe klasy!)# Otwórz GUI wyboru języka

```java/language

// ✅ Zwykła klasa - NIE MUSISZ dziedziczyć MongoMessages!/lang                # Alias

@ConfigsFileProperties(name = "my-messages")/jezyk               # Polski alias

public class MyMessages {

    // Pusta klasa - system automatycznie obsługuje wszystkie języki# Ustaw język bezpośrednio

}/language en         # English

/language pl         # Polski 

Messages messages = cm.messagesOf(MyMessages.class);/language de         # Deutsch

String msg = messages.get("en", "welcome.player", playerName);/language fr         # Français

/language es         # Español

// 🎪 OPCJONALNIE - extend MongoMessages dla dodatkowych metod:

public class AdvancedMessages extends MongoMessages<AdvancedMessages> {# Pokaż informacje o językach

    // Implementuj abstract methods jeśli chcesz custom logic/language help       # Lista dostępnych języków

}```

```

### 📋 Komendy administracyjne

## 🌍 System językowy - Kompletny przewodnik

```bash

### 🎮 Komendy dla graczy# Zarządzanie konfiguracją

/mongoconfigs reload server-settings    # Reload konkretnej konfiguracji

```bash/mongoconfigs reloadall                 # Reload wszystkich konfiguracji

# Otwórz GUI wyboru języka/mongoconfigs collections               # Lista dostępnych kolekcji

/language/mongoconfigs info                      # Informacje o systemie

/lang

/jezyk# Hot reload dla deweloperów

/hotreload test                         # Test hot reload

# Ustaw język bezpośrednio/hotreload clear                        # Wyczyść cache

/language en         # English/hotreload status                       # Status systemu

/language pl         # Polski

/language de         # Deutsch# Zarządzanie cache

/language fr         # Français/configsmanager stats                   # Statystyki cache

/language es         # Español/configsmanager reload                  # Reload cache

/configsmanager clear                   # Wyczyść cache

# Pokaż informacje o językach```

/language help       # Lista dostępnych języków

```## 🎨 Tworzenie wielojęzycznych plików



### 📋 Komendy administracyjne### 📁 Struktura plików językowych



```bash```java

# Zarządzanie konfiguracją// 1. Stwórz klasę dla wiadomości

/mongoconfigs reload server-settings    # Reload konkretnej konfiguracji@ConfigsFileProperties(name = "gui-messages")

/mongoconfigs reloadall                 # Reload wszystkich konfiguracjipublic class GuiMessages {

/mongoconfigs collections               # Lista dostępnych kolekcji    // Pusta klasa - system automatycznie obsługuje języki z MongoDB

/mongoconfigs info                      # Informacje o systemie}



# Hot reload dla deweloperów// 2. W MongoDB kolekcji "gui-messages" stwórz dokumenty:

/hotreload test                         # Test hot reload```

/hotreload clear                        # Wyczyść cache

/hotreload status                       # Status systemu### 🗄️ Format dokumentów w MongoDB



# Zarządzanie cache```javascript

/configsmanager stats                   # Statystyki cache// Dokument dla języka polskiego

/configsmanager reload                  # Reload cache{

/configsmanager clear                   # Wyczyść cache  "_id": "pl",

```  "welcome": {

    "title": "Witaj na serwerze!",

## 🎯 **DLACZEGO TO JEST ZAJEBISTE:**    "subtitle": "Miłej gry, {player}!",

    "message": "§aWitaj {player}! §eAktualny poziom: §6{level}"

### ⚡ **Każda klasa = Jedna linia:**  },

```java  "gui": {

// Zamiast 50 linii boilerplate:    "buttons": {

ServerConfig config = cm.loadObject(ServerConfig.class);     // ⚡ GOTOWE!      "confirm": "§aPotwierź",

EconomyConfig economy = cm.loadObject(EconomyConfig.class);  // ⚡ GOTOWE!      "cancel": "§cAnuluj",

Messages msgs = cm.messagesOf(GuiMessages.class);           // ⚡ GOTOWE!      "close": "§7Zamknij"

```    },

    "titles": {

### 🔥 **Modyfikacja = Jedna linia:**      "main_menu": "§b§lMenu Główne",

```java      "settings": "§6§lUstawienia",

// Zmień cokolwiek w obiekcie...      "shop": "§e§lSklep"

config.setMaxPlayers(500);    }

economy.getItemPrices().put("diamond", 999.0);  },

config.getFactions().get("dragons").setPower(150.0);  "shop": {

    "buy_success": "§aZakupiono {item} za {price} monet!",

// I zapisz jedną linią:    "insufficient_funds": "§cNie masz wystarczająco monet!",

cm.saveObject(config);   // 💥 CHANGE STREAMS = SYNC WSZĘDZIE!    "item_not_found": "§cPrzedmiot nie został znaleziony!"

```  }

}

### 💻 **Zero konfiguracji JSON/YAML:**

```java// Dokument dla języka angielskiego  

// ❌ Nie ma tego:{

// - Ręczne parsowanie YAML  "_id": "en",

// - Sprawdzanie null values  "welcome": {

// - Konwersje typów    "title": "Welcome to the server!",

// - File I/O operations    "subtitle": "Have fun, {player}!",

// - Synchronizacja między serwerami    "message": "§aWelcome {player}! §eCurrent level: §6{level}"

  },

// ✅ Jest to:  "gui": {

ConfigClass data = cm.loadObject(ConfigClass.class);  // GOTOWE! 🎯    "buttons": {

```      "confirm": "§aConfirm",

      "cancel": "§cCancel", 

## 🏆 **MongoDB Configs API zapewnia:**      "close": "§7Close"

    },

- ✅ **🎯 Type Safety** - Kompiler pilnuje typów, zero runtime errors    "titles": {

- ✅ **🚀 Zero Boilerplate** - Jedna linia zamiast 20-50 linii kodu      "main_menu": "§b§lMain Menu",

- ✅ **⚡ Multi-server Sync** - Change Streams = automatyczna synchronizacja wszędzie      "settings": "§6§lSettings",

- ✅ **🔥 Hot Reload** - Zmiany bez restartu serwera      "shop": "§e§lShop"

- ✅ **🎪 Flexible** - Adnotacje do kontroli baz danych i kolekcji    }

- ✅ **🏎️ Performance** - Smart caching + lazy loading + MongoDB  },

- ✅ **🛡️ Reliable** - Fallbacks, error handling, connection pooling  "shop": {

- ✅ **🌍 Multi-language** - Automatyczne wsparcie wielojęzyczności    "buy_success": "§aPurchased {item} for {price} coins!",

- ✅ **🔧 IDE Support** - Pełne wsparcie IntelliJ/Eclipse    "insufficient_funds": "§cYou don't have enough coins!",

- ✅ **📊 Complex Objects** - Lists, Maps, nested objects, wszystko native    "item_not_found": "§cItem not found!"

  }

### 🎮 **PERFECT DLA MINECRAFT:**}

```

```java

// MMO Serwer:### 🔨 Automatyczne buildowanie językowych plików

CharacterConfig chars = cm.loadObject(CharacterConfig.class);     // ⚡

GuildConfig guilds = cm.loadObject(GuildConfig.class);           // ⚡```java

EconomyConfig economy = cm.loadObject(EconomyConfig.class);      // ⚡/**

Messages guildMsgs = cm.messagesOf(GuildMessages.class);         // ⚡ * Klasa builder dla automatycznego tworzenia dokumentów językowych

 */

// PvP Serwer:public class LanguageFileBuilder {

ArenaConfig arenas = cm.loadObject(ArenaConfig.class);           // ⚡    

TournamentConfig tournaments = cm.loadObject(TournamentConfig.class); // ⚡    private final ConfigManager cm;

    private final String collectionName;

// Skyblock Serwer:    

IslandConfig islands = cm.loadObject(IslandConfig.class);        // ⚡    public LanguageFileBuilder(String messagesClassName) {

ShopConfig shop = cm.loadObject(ShopConfig.class);              // ⚡        this.cm = MongoConfigsAPI.getConfigManager();

        this.collectionName = getCollectionName(messagesClassName);

// Network Serwerów:    }

// Wszystkie serwery automatycznie zsynchronizowane! 🌐    

```    // 🔥 Automatyczne tworzenie struktury językowej

    public void buildLanguageStructure() {

### 🔥 **JAK TO DZIAŁA W PRAKTYCE:**        String[] languages = {"en", "pl", "de", "fr", "es"};

        

1. **🎯 Piszesz klasę** - Zwykła Java class z getterami/setterami        for (String lang : languages) {

2. **⚡ Jedna linia load** - `MyConfig config = cm.loadObject(MyConfig.class);`            Document langDoc = new Document("_id", lang);

3. **🚀 Używasz** - `config.getMaxPlayers()`, `config.setServerName("New name")`            

4. **💾 Jedna linia save** - `cm.saveObject(config);`            // Dodaj podstawowe sekcje

5. **🌐 Auto-sync** - Wszystkie serwery natychmiast widzą zmiany!            langDoc.put("welcome", createWelcomeSection(lang));

            langDoc.put("gui", createGuiSection(lang));

## 🔄 Hot Reload Examples            langDoc.put("shop", createShopSection(lang));

            langDoc.put("commands", createCommandsSection(lang));

```bash            

# Reload ekonomii na wszystkich serwerach            // Zapisz w MongoDB

/mongoconfigs reload economy            cm.getMongoManager()

              .getCollection("minecraft", collectionName)

# Reload wszystkich wiadomości              .replaceOne(

/mongoconfigs reload spawn-messages                  com.mongodb.client.model.Filters.eq("_id", lang),

                  langDoc,

# Reload wszystkiego (ostrożnie)                  new com.mongodb.client.model.ReplaceOptions().upsert(true)

/mongoconfigs reloadall              );

        }

# Sprawdź dostępne kolekcje    }

/mongoconfigs collections    

```    private Document createWelcomeSection(String lang) {

        return switch (lang) {

## 🏆 Best Practices            case "pl" -> new Document()

                .append("title", "Witaj na serwerze!")

### 1. **Używaj Load-or-Generate**                .append("subtitle", "Miłej gry, {player}!")

```java                .append("message", "§aWitaj {player}! §ePoziomlevel: §6{level}");

// ✅ Dobre - zawsze masz poprawny obiekt            case "en" -> new Document()

ServerConfig config = cm.getConfigOrGenerate(ServerConfig.class,                .append("title", "Welcome to the server!")

    () -> new ServerConfig());                .append("subtitle", "Have fun, {player}!")

                .append("message", "§aWelcome {player}! §eCurrent level: §6{level}");

// ❌ Złe - może być null            // ... inne języki

ServerConfig config = cm.getObject(ServerConfig.class);            default -> new Document()

if (config == null) { /* boilerplate... */ }                .append("title", "Welcome!")

```                .append("subtitle", "Hello {player}!")

                .append("message", "§aHello {player}!");

### 2. **Sync convenience methods**        };

```java    }

// Dla prostych operacji    

cm.saveObject(config);          // sync setObject()    // Użycie:

ServerConfig c = cm.loadObject(ServerConfig.class); // sync getObject()    public static void main(String[] args) {

        LanguageFileBuilder builder = new LanguageFileBuilder("GuiMessages");

// Dla performance-critical - używaj async        builder.buildLanguageStructure();

cm.setObject(config).thenRun(() -> {        System.out.println("✅ Struktura językowa została utworzona!");

    // callback after save    }

});}

``````



### 3. **Struktura wiadomości**## 🎨 Tworzenie wielojęzycznych GUI

```yaml

# W MongoDB dla języka "pl":### 📋 Kompletny przykład GUI z tłumaczeniami

welcome:

  message: "Witaj {0}!"```java

  subtitle: "Poziom: {1}"@ConfigsFileProperties(name = "shop-messages")

gui:public class ShopMessages {

  buttons:    // System automatycznie obsłuży wszystkie języki

    confirm: "Potwierdź"}

    cancel: "Anuluj"

shop:public class ShopGUI implements InventoryHolder {

  prices:    

    diamond: "Diament: {price} monet"    private final Messages messages;

```    private final LanguageManager languageManager;

    private final Player player;

## 🚨 Troubleshooting    private final Inventory inventory;

    

### Problemy z połączeniem    public ShopGUI(Player player) {

```bash        this.player = player;

# Sprawdź połączenie        this.languageManager = MongoConfigsAPI.getLanguageManager();

/mongoconfigs collections        this.messages = MongoConfigsAPI.getConfigManager().messagesOf(ShopMessages.class);

        

# Test reloadu        // 🌍 Pobierz język gracza

/mongoconfigs reload server-settings        String playerLang = languageManager.getPlayerLanguage(player.getUniqueId().toString());

        

# Debug informacje        // 🎨 Stwórz GUI z tłumaczonym tytułem

/mongoconfigs testcollections        String title = messages.get(playerLang, "gui.titles.shop");

```        this.inventory = Bukkit.createInventory(this, 27, 

            ColorHelper.parseComponent(title));

### Cache issues    }

```java    

// Wyczyść cache jeśli coś się zepsuło    public void open() {

        String playerLang = languageManager.getPlayerLanguage(player.getUniqueId().toString());

        

// Wymuś reload konkretnej kolekcji        // 🛒 Przedmioty sklepu z tłumaczeniami

cm.reloadCollection("problematic-collection");        addShopItem(10, Material.DIAMOND, "shop.items.diamond", playerLang, 100);

```        addShopItem(11, Material.EMERALD, "shop.items.emerald", playerLang, 50);

        addShopItem(12, Material.GOLD_INGOT, "shop.items.gold", playerLang, 25);

## 📈 Performance Tips        

        // 🎮 Przyciski nawigacji

### 1. **Batch reload** przy starcie        addNavigationButtons(playerLang);

```java        

Set<String> collections = Set.of("server-settings", "gui-messages", "economy");        player.openInventory(inventory);

cm.reloadCollectionsBatch(collections, 3); // max 3 równocześnie    }

```    

    private void addShopItem(int slot, Material material, String messageKey, 

### 2. **Cache warm-up**                           String playerLang, double price) {

```java        ItemStack item = new ItemStack(material);

// Pre-load często używanych configów        ItemMeta meta = item.getItemMeta();

cm.getObject(ServerConfig.class);        

cm.getObject(EconomyConfig.class);        // 🏷️ Nazwa przedmiotu w języku gracza

messages.get("en", "common.messages"); // pre-cache messages        String name = messages.get(playerLang, messageKey + ".name");

```        meta.displayName(ColorHelper.parseComponent(name));

        

### 3. **Change Streams monitoring**        // 📝 Opis w języku gracza  

- Włącz `change-streams-enabled: true` dla multi-server        List<Component> lore = Arrays.asList(

- System automatycznie synchronizuje zmiany            ColorHelper.parseComponent(messages.get(playerLang, messageKey + ".description")),

- Zero manual work - wszystko dzieje się w tle            Component.empty(),

            ColorHelper.parseComponent(messages.get(playerLang, "shop.price", price)),

## 🏁 **PODSUMOWANIE - DLACZEGO TO NAJLEPSZE API NA ŚWIECIE**            ColorHelper.parseComponent(messages.get(playerLang, "shop.click_to_buy"))

        );

### 🎯 **JEDNA LINIA = MAGIC**        meta.lore(lore);

```java        

// To wszystko to jedna linia:        item.setItemMeta(meta);

MyComplexConfig config = cm.loadObject(MyComplexConfig.class);  // 🔥        inventory.setItem(slot, item);

// - Automatyczny load z MongoDB    }

// - Full type safety    

// - Smart caching    private void addNavigationButtons(String playerLang) {

// - Error handling        // ✅ Przycisk potwierdzenia

// - Change streams sync        ItemStack confirmBtn = new ItemStack(Material.GREEN_WOOL);

// - Jackson serialization        ItemMeta confirmMeta = confirmBtn.getItemMeta();

// - WSZYSTKO W JEDNEJ LINII! 🤯        confirmMeta.displayName(ColorHelper.parseComponent(

```            messages.get(playerLang, "gui.buttons.confirm")));

        confirmBtn.setItemMeta(confirmMeta);

### 🚀 **ROZWÓJ BŁYSKAWICZNY**        inventory.setItem(22, confirmBtn);

```java        

// ❌ TRADYCYJNE BIBLIOTEKI: 50+ linii boilerplate na każdą klasę        // ❌ Przycisk anulowania

// ✅ MONGO CONFIGS: 1 linia na klasę        ItemStack cancelBtn = new ItemStack(Material.RED_WOOL);

        ItemMeta cancelMeta = cancelBtn.getItemMeta();

// Ile czasu zaoszczędzisz:        cancelMeta.displayName(ColorHelper.parseComponent(

// 10 klas config = 500 linii boilerplate → 10 linii            messages.get(playerLang, "gui.buttons.cancel")));

// 20 klas config = 1000 linii boilerplate → 20 linii        cancelBtn.setItemMeta(cancelMeta);

// 50 klas config = 2500 linii boilerplate → 50 linii        inventory.setItem(18, cancelBtn);

    }

// = TYSIĄCE GODZIN ZAOSZCZONE! ⏰💰    

```    @EventHandler

    public void onInventoryClick(InventoryClickEvent event) {

### 🏆 **NAJLEPSZA BIBLIOTEKA CONFIG NA MINECRAFT!**        if (event.getInventory().getHolder() != this) return;

        event.setCancelled(true);

**Przyszłość zarządzania konfiguracją jest tutaj!** 🚀🔥        

        Player clicker = (Player) event.getWhoClicked();

**MONGO CONFIGS = GAME CHANGER!** 🎮⚡🏆        String playerLang = languageManager.getPlayerLanguage(clicker.getUniqueId().toString());

        

---        ItemStack clicked = event.getCurrentItem();

        if (clicked == null) return;

## 📞 Support        

        // 🛒 Obsługa zakupu przedmiotu

- 📖 **[Full Documentation](https://github.com/WTJEE/mongo-configs/wiki)**        if (event.getSlot() >= 10 && event.getSlot() <= 12) {

- 🐛 **[Issues](https://github.com/WTJEE/mongo-configs/issues)**            handleItemPurchase(clicker, clicked, playerLang);

- 💬 **Discord Support Server**        }

        

## 📄 License        // 🎮 Obsługa przycisków nawigacji

        switch (event.getSlot()) {

MIT License - Zobacz [LICENSE](LICENSE) dla szczegółów.            case 22 -> handleConfirm(clicker, playerLang);
            case 18 -> handleCancel(clicker, playerLang);
        }
    }
    
    private void handleItemPurchase(Player player, ItemStack item, String playerLang) {
        // Logika zakupu...
        String successMsg = messages.get(playerLang, "shop.buy_success", 
            item.getType().name(), "100");
        player.sendMessage(ColorHelper.parseComponent(successMsg));
    }
}
```

### 🎭 Dynamiczne GUI z live language switching

```java
public class LanguageSwitchableGUI implements InventoryHolder {
    
    private final Player player;
    private final Messages messages;
    private final LanguageManager languageManager;
    private Inventory inventory;
    private String currentLanguage;
    
    public LanguageSwitchableGUI(Player player) {
        this.player = player;
        this.languageManager = MongoConfigsAPI.getLanguageManager();
        this.messages = MongoConfigsAPI.getConfigManager().messagesOf(GuiMessages.class);
        this.currentLanguage = languageManager.getPlayerLanguage(player.getUniqueId().toString());
        
        createInventory();
    }
    
    private void createInventory() {
        String title = messages.get(currentLanguage, "gui.dynamic.title");
        this.inventory = Bukkit.createInventory(this, 36, 
            ColorHelper.parseComponent(title));
        populateInventory();
    }
    
    private void populateInventory() {
        // 🌍 Flagi językowe jako przyciski
        addLanguageFlags();
        
        // 📋 Zawartość GUI w aktualnym języku
        addContentItems();
        
        // 🔄 Przycisk odświeżania
        addRefreshButton();
    }
    
    private void addLanguageFlags() {
        String[] languages = {"en", "pl", "de", "fr", "es"};
        Material[] flags = {Material.WHITE_WOOL, Material.RED_WOOL, 
                           Material.YELLOW_WOOL, Material.BLUE_WOOL, Material.ORANGE_WOOL};
        
        for (int i = 0; i < languages.length; i++) {
            String lang = languages[i];
            ItemStack flag = new ItemStack(flags[i]);
            ItemMeta meta = flag.getItemMeta();
            
            String name = messages.get(currentLanguage, "gui.language_selector.name", lang);
            meta.displayName(ColorHelper.parseComponent(name));
            
            if (lang.equals(currentLanguage)) {
                // ✨ Highlight aktualny język  
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            
            flag.setItemMeta(meta);
            inventory.setItem(i, flag);
        }
    }
    
    // 🔄 Przełączanie języka i live update GUI
    public void switchLanguage(String newLanguage) {
        this.currentLanguage = newLanguage;
        
        // Zapisz nowy język gracza
        languageManager.setPlayerLanguage(player.getUniqueId(), newLanguage);
        
        // 🎨 Natychmiastowe odświeżenie GUI
        inventory.clear();
        populateInventory();
        
        // 📢 Powiadom gracza
        String msg = messages.get(newLanguage, "gui.language_changed", newLanguage);
        player.sendMessage(ColorHelper.parseComponent(msg));
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;
        event.setCancelled(true);
        
        int slot = event.getSlot();
        
        // 🌍 Przełączanie języków (sloty 0-4)
        if (slot >= 0 && slot <= 4) {
            String[] languages = {"en", "pl", "de", "fr", "es"};
            String selectedLang = languages[slot];
            
            if (!selectedLang.equals(currentLanguage)) {
                switchLanguage(selectedLang);
            }
        }
    }
}
```

## 🔧 Zaawansowane metody ładowania konfiguracji

### 🚀 Podstawowe metody ładowania

```java
public class ConfigLoadingExamples {
    
    private final ConfigManager cm = MongoConfigsAPI.getConfigManager();
    
    // 🔥 PODSTAWOWE ŁADOWANIE
    public void basicLoading() {
        // Jedna linia = cała konfiguracja
        ServerConfig config = cm.loadObject(ServerConfig.class);
        
        // Z fallbackiem jeśli nie istnieje
        ServerConfig configWithFallback = cm.getConfigOrGenerate(
            ServerConfig.class, 
            () -> new ServerConfig() // Domyślne wartości
        );
        
        // Asynchroniczne ładowanie
        CompletableFuture<ServerConfig> asyncConfig = cm.getObject(ServerConfig.class);
        asyncConfig.thenAccept(config -> {
            // Użyj config gdy się załaduje
            System.out.println("Max players: " + config.getMaxPlayers());
        });
    }
    
    // 💾 ZAAWANSOWANE ZAPISYWANIE
    public void advancedSaving() {
        ServerConfig config = cm.loadObject(ServerConfig.class);
        
        // Zwykłe zapisywanie
        cm.saveObject(config);
        
        // Asynchroniczne zapisywanie z callbackiem
        cm.setObject(config).thenRun(() -> {
            System.out.println("✅ Konfiguracja zapisana!");
        }).exceptionally(error -> {
            System.err.println("❌ Błąd zapisu: " + error.getMessage());
            return null;
        });
        
        // Batch saving (kilka obiektów naraz)
        List<Object> configs = Arrays.asList(
            cm.loadObject(ServerConfig.class),
            cm.loadObject(EconomyConfig.class),
            cm.loadObject(GuiConfig.class)
        );
        
        // Zapisz wszystkie asynchronicznie
        CompletableFuture.allOf(
            configs.stream()
                   .map(cm::setObject)
                   .toArray(CompletableFuture[]::new)
        ).thenRun(() -> {
            System.out.println("✅ Wszystkie konfiguracje zapisane!");
        });
    }
    
    // 🔄 CACHE MANAGEMENT
    public void cacheManagement() {
        // Wymuś przeładowanie konkretnej kolekcji
        cm.reloadCollection("server-settings");
        
        // Przeładuj kilka kolekcji naraz
        Set<String> collections = Set.of("server-settings", "gui-config", "economy");
        cm.reloadCollectionsBatch(collections, 3); // max 3 równocześnie
        
        // Wyczyść cache
        cm.invalidateCache();
        
        // Sprawdź czy obiekt jest w cache
        boolean inCache = cm.isCached(ServerConfig.class);
        
        // Usuń konkretny obiekt z cache
        cache.remove(ServerConfig.class);
    }
    
    // 🎯 WARUNKOWE ŁADOWANIE
    public void conditionalLoading() {
        // Załaduj tylko jeśli istnieje
        Optional<ServerConfig> maybeConfig = cm.getObjectIfExists(ServerConfig.class);
        if (maybeConfig.isPresent()) {
            ServerConfig config = maybeConfig.get();
            // Użyj config...
        }
        
        // Załaduj z timeoutem
        try {
            ServerConfig config = cm.getObject(ServerConfig.class)
                                   .orTimeout(5, TimeUnit.SECONDS)
                                   .join();
        } catch (CompletionException e) {
            System.err.println("Timeout loading config!");
        }
        
        // Załaduj z retry mechanizmem
        ServerConfig config = loadWithRetry(ServerConfig.class, 3);
    }
    
    private <T> T loadWithRetry(Class<T> configClass, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return cm.loadObject(configClass);
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    throw new RuntimeException("Failed to load after " + maxRetries + " attempts", e);
                }
                System.err.println("Attempt " + attempt + " failed, retrying...");
                try {
                    Thread.sleep(1000 * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", ie);
                }
            }
        }
        throw new RuntimeException("Should never reach here");
    }
}
```

### 🎨 Utility klasy dla łatwiejszego zarządzania

```java
/**
 * 🛠️ Utility klasa dla łatwiejszego zarządzania konfiguracjami
 */
public class ConfigUtils {
    
    private static final ConfigManager cm = MongoConfigsAPI.getConfigManager();
    private static final LanguageManager lm = MongoConfigsAPI.getLanguageManager();
    
    // 🔥 Quick access methods
    public static <T> T config(Class<T> configClass) {
        return cm.loadObject(configClass);
    }
    
    public static <T> T configOrDefault(Class<T> configClass, Supplier<T> defaultSupplier) {
        return cm.getConfigOrGenerate(configClass, defaultSupplier);
    }
    
    public static Messages messages(Class<?> messagesClass) {
        return cm.messagesOf(messagesClass);
    }
    
    public static String playerMessage(Player player, String messageKey, Object... args) {
        String lang = lm.getPlayerLanguage(player.getUniqueId().toString());
        return messages(GuiMessages.class).get(lang, messageKey, args);
    }
    
    // 🎮 Player-specific helpers
    public static void sendPlayerMessage(Player player, String messageKey, Object... args) {
        String message = playerMessage(player, messageKey, args);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    public static void setPlayerLanguage(Player player, String language) {
        lm.setPlayerLanguage(player.getUniqueId(), language).thenRun(() -> {
            sendPlayerMessage(player, "language.changed", language);
        });
    }
    
    // 🔄 Bulk operations
    public static void reloadAllConfigs() {
        cm.reloadAll().thenRun(() -> {
            System.out.println("✅ All configs reloaded!");
        });
    }
    
    public static void saveAllConfigs(Object... configs) {
        CompletableFuture.allOf(
            Arrays.stream(configs)
                  .map(cm::setObject)
                  .toArray(CompletableFuture[]::new)
        ).thenRun(() -> {
            System.out.println("✅ All configs saved!");
        });
    }
    
    // 📊 Monitoring helpers
    public static void logConfigState(Class<?> configClass) {
        boolean cached = cm.isCached(configClass);
        System.out.println("Config " + configClass.getSimpleName() + 
                          " - Cached: " + cached);
    }
    
    public static void printCacheStats() {
        // Implementation for cache statistics
        System.out.println("=== CACHE STATISTICS ===");
        // Print cache hit/miss ratios, memory usage, etc.
    }
}

// 🎯 Przykład użycia utility klasy
public class ExampleUsage {
    
    public void quickExample() {
        // 🔥 Super szybkie ładowanie
        ServerConfig server = ConfigUtils.config(ServerConfig.class);
        EconomyConfig economy = ConfigUtils.config(EconomyConfig.class);
        
        // 📧 Szybkie wiadomości
        ConfigUtils.sendPlayerMessage(player, "welcome.message", player.getName());
        
        // 💾 Szybkie zapisywanie
        server.setMaxPlayers(500);
        economy.setStartingMoney(1000.0);
        ConfigUtils.saveAllConfigs(server, economy);
    }
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

## 🛠️ Troubleshooting - Debugging komendy i GUI

### 🔍 Diagnostyka problemów z komendami

```bash
# Sprawdź czy plugin jest załadowany
/plugins

# Sprawdź uprawnienia gracza
/lp user <player> permission check mongoconfigs.language

# Test komend językowych z debugiem
/language          # Powinno otworzyć GUI
/lang              # Alias - powinno działać tak samo  
/jezyk             # Polski alias

# Sprawdź konfigurację
/mongoconfigs info
/mongoconfigs collections
```

### 🔧 Debug mode dla komend

Jeśli komendy nie działają, plugin automatycznie dodaje debug informacje:

```
§a[DEBUG] LanguageCommand executed with label: lang, args: []
§a[DEBUG] Creating LanguageSelectionGUI...
§a[DEBUG] LanguageSelectionGUI constructor - config title: Language Selection
§a[DEBUG] GUI size: 27
§a[DEBUG] Starting GUI build process...
§a[DEBUG] GUI built successfully, opening inventory...
§a[DEBUG] About to open inventory for player...
§a[DEBUG] Inventory opened!
```

Jeśli widzisz błędy, sprawdź:

1. **Plugin initialization**: Czy MongoDB jest dostępne?
2. **Permissions**: Czy gracz ma `mongoconfigs.language`?
3. **Language config**: Czy `languages.yml` jest poprawnie załadowany?
4. **GUI config**: Czy GUI ma poprawne ustawienia rozmiaru i slotów?

### 🐛 Najczęstsze problemy i rozwiązania

```java
// Problem: GUI się nie otwiera
// Rozwiązanie: Użyj prostej metody jako fallback
public void openLanguageGUI(Player player) {
    try {
        LanguageSelectionGUI gui = new LanguageSelectionGUI(player, languageManager, config);
        gui.open(); // Async method
    } catch (Exception e) {
        // Fallback do prostej metody
        gui.openSimple(); // Sync method
    }
}

// Problem: Brak odpowiedzi na komendę
// Rozwiązanie: Sprawdź czy languageManager jest zainicjalizowany
if (languageManager == null) {
    player.sendMessage("§c[ERROR] LanguageManager not initialized!");
    return true;
}

// Problem: Błędne języki
// Rozwiązanie: Sprawdź supported languages w config
String[] supported = languageManager.getSupportedLanguages();
if (supported.length == 0) {
    player.sendMessage("§c[ERROR] No supported languages configured!");
    return true;
}
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
