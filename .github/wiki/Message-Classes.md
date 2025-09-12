# Message Classes

Complete guide to creating multilingual message systems with MongoDB Configs API.

## 🌍 Basic Message Class

### Simple Message Class

```java
@ConfigsFileProperties(name = "gui-messages")
public class GuiMessages {
    // Empty class - system automatically handles all languages!
}
```

**Usage**:
```java
ConfigManager cm = MongoConfigsAPI.getConfigManager();
LanguageManager lm = MongoConfigsAPI.getLanguageManager();

// Get messages instance
Messages messages = cm.messagesOf(GuiMessages.class);

// Get player's language
String playerLang = lm.getPlayerLanguage(player.getUniqueId().toString());

// Get localized message
String welcome = messages.get(playerLang, "welcome.message", player.getName());
String button = messages.get(playerLang, "gui.buttons.confirm");
```

---

## 🗄️ MongoDB Structure for Messages

### Document Structure

Each language is stored as a separate document in the collection:

```javascript
// Collection: gui-messages

// English document
{
  "_id": "en",
  "welcome": {
    "title": "Welcome to the server!",
    "message": "Hello {0}! Your level is {1}",
    "subtitle": "Have a great time!"
  },
  "gui": {
    "buttons": {
      "confirm": "Confirm",
      "cancel": "Cancel",
      "close": "Close",
      "next": "Next Page",
      "previous": "Previous Page"
    },
    "titles": {
      "main_menu": "Main Menu",
      "settings": "Settings",
      "shop": "Shop"
    }
  },
  "shop": {
    "buy_success": "Successfully purchased {0} for {1} coins!",
    "insufficient_funds": "You don't have enough coins!",
    "item_not_found": "Item not found in shop!"
  }
}

// Polish document  
{
  "_id": "pl",
  "welcome": {
    "title": "Witaj na serwerze!",
    "message": "Cześć {0}! Twój poziom to {1}",
    "subtitle": "Miłej gry!"
  },
  "gui": {
    "buttons": {
      "confirm": "Potwierdź",
      "cancel": "Anuluj", 
      "close": "Zamknij",
      "next": "Następna strona",
      "previous": "Poprzednia strona"
    },
    "titles": {
      "main_menu": "Menu Główne",
      "settings": "Ustawienia", 
      "shop": "Sklep"
    }
  },
  "shop": {
    "buy_success": "Pomyślnie kupiono {0} za {1} monet!",
    "insufficient_funds": "Nie masz wystarczająco monet!",
    "item_not_found": "Przedmiot nie został znaleziony w sklepie!"
  }
}
```

---

## 🏗️ Creating Message Documents

### Automated Builder

```java
/**
 * Utility class for building language documents
 */
public class MessageBuilder {
    
    private final ConfigManager cm;
    private final String collectionName;
    
    public MessageBuilder(Class<?> messageClass) {
        this.cm = MongoConfigsAPI.getConfigManager();
        this.collectionName = getCollectionName(messageClass);
    }
    
    public void createLanguageStructure() {
        String[] languages = {"en", "pl", "de", "fr", "es"};
        
        for (String lang : languages) {
            Document langDoc = new Document("_id", lang);
            
            // Add sections
            langDoc.put("welcome", createWelcomeSection(lang));
            langDoc.put("gui", createGuiSection(lang));
            langDoc.put("shop", createShopSection(lang));
            langDoc.put("commands", createCommandsSection(lang));
            langDoc.put("errors", createErrorsSection(lang));
            
            // Save to MongoDB
            saveLanguageDocument(lang, langDoc);
        }
    }
    
    private Document createWelcomeSection(String lang) {
        return switch (lang) {
            case "en" -> new Document()
                .append("title", "Welcome to the server!")
                .append("message", "Hello {0}! Your level is {1}")
                .append("subtitle", "Have a great time!")
                .append("first_join", "Welcome to our server for the first time!");
                
            case "pl" -> new Document()
                .append("title", "Witaj na serwerze!")
                .append("message", "Cześć {0}! Twój poziom to {1}")
                .append("subtitle", "Miłej gry!")
                .append("first_join", "Witaj na naszym serwerze po raz pierwszy!");
                
            case "de" -> new Document()
                .append("title", "Willkommen auf dem Server!")
                .append("message", "Hallo {0}! Dein Level ist {1}")
                .append("subtitle", "Viel Spaß!")
                .append("first_join", "Willkommen zum ersten Mal auf unserem Server!");
                
            // ... other languages
            default -> createWelcomeSection("en"); // Fallback
        };
    }
    
    private Document createGuiSection(String lang) {
        Document buttons = new Document();
        Document titles = new Document();
        
        switch (lang) {
            case "en" -> {
                buttons.put("confirm", "Confirm");
                buttons.put("cancel", "Cancel");
                buttons.put("close", "Close");
                buttons.put("next", "Next Page");
                buttons.put("previous", "Previous Page");
                
                titles.put("main_menu", "Main Menu");
                titles.put("settings", "Settings");
                titles.put("shop", "Shop");
                titles.put("inventory", "Inventory");
            }
            case "pl" -> {
                buttons.put("confirm", "Potwierdź");
                buttons.put("cancel", "Anuluj");
                buttons.put("close", "Zamknij");
                buttons.put("next", "Następna strona");
                buttons.put("previous", "Poprzednia strona");
                
                titles.put("main_menu", "Menu Główne");
                titles.put("settings", "Ustawienia");
                titles.put("shop", "Sklep");
                titles.put("inventory", "Ekwipunek");
            }
            // ... other languages
        }
        
        return new Document()
            .append("buttons", buttons)
            .append("titles", titles);
    }
    
    // Usage:
    public static void main(String[] args) {
        MessageBuilder builder = new MessageBuilder(GuiMessages.class);
        builder.createLanguageStructure();
        System.out.println("✅ Language structure created!");
    }
}
```

---

## 🎨 Advanced Message Usage

### Complex Message Formatting

```java
public class ShopGUI {
    
    private final Messages messages;
    private final LanguageManager languageManager;
    
    public ShopGUI() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        this.messages = cm.messagesOf(ShopMessages.class);
        this.languageManager = MongoConfigsAPI.getLanguageManager();
    }
    
    public void showShopItem(Player player, String item, double price, int stock) {
        String lang = languageManager.getPlayerLanguage(player.getUniqueId().toString());
        
        // Simple message
        String title = messages.get(lang, "shop.item.title");
        
        // Message with single parameter
        String itemName = messages.get(lang, "shop.item.name", item);
        
        // Message with multiple parameters
        String priceText = messages.get(lang, "shop.item.price", price, "coins");
        
        // Complex formatting
        String description = messages.get(lang, "shop.item.description", 
            item,           // {0} - item name
            price,          // {1} - price
            stock,          // {2} - stock amount
            calculateDiscount(price), // {3} - discount
            player.getName() // {4} - player name
        );
        
        // Color formatting (supports all Minecraft color formats)
        String coloredMessage = messages.get(lang, "shop.item.colored",
            "§6" + item,    // Gold item name
            "§a$" + price,  // Green price
            "§c" + stock    // Red stock
        );
        
        player.sendMessage(ColorHelper.parseComponent(coloredMessage));
    }
}
```

### MongoDB Message Document with Color Formatting

```javascript
{
  "_id": "en",
  "shop": {
    "item": {
      "title": "Shop Item",
      "name": "{0}",
      "price": "Price: {0} {1}",
      "description": "Item: {0} | Price: ${1} | Stock: {2} | Discount: {3}% | For: {4}",
      "colored": "&#FF6B35Item: {0} &#00D9FF| Price: {1} &#FF006E| Stock: {2}",
      "gradient": "gradient:#FF6B35:#00D9FFItem: {0} &#FFFFFFPrice: {1}"
    },
    "purchase": {
      "success": "&#00FF00✅ Successfully purchased {0}!",
      "failure": "&#FF0000❌ Failed to purchase {0}: {1}",
      "insufficient_funds": "&#FF6B35⚠️ You need {0} more coins!"
    }
  }
}
```

---

## 🔧 Message System Integration

### Plugin Integration Example

```java
public class MyPlugin extends JavaPlugin {
    
    private Messages shopMessages;
    private Messages guiMessages;
    private Messages commandMessages;
    private LanguageManager languageManager;
    
    @Override
    public void onEnable() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        this.languageManager = MongoConfigsAPI.getLanguageManager();
        
        // Load all message systems
        this.shopMessages = cm.messagesOf(ShopMessages.class);
        this.guiMessages = cm.messagesOf(GuiMessages.class);
        this.commandMessages = cm.messagesOf(CommandMessages.class);
    }
    
    // Utility method for sending player messages
    public void sendMessage(Player player, Messages messageSystem, String key, Object... args) {
        String lang = languageManager.getPlayerLanguage(player.getUniqueId().toString());
        String message = messageSystem.get(lang, key, args);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    // Convenience methods
    public void sendShopMessage(Player player, String key, Object... args) {
        sendMessage(player, shopMessages, key, args);
    }
    
    public void sendGuiMessage(Player player, String key, Object... args) {
        sendMessage(player, guiMessages, key, args);
    }
    
    public void sendCommandMessage(Player player, String key, Object... args) {
        sendMessage(player, commandMessages, key, args);
    }
}
```

### Custom Message Utility Class

```java
public class MessageUtils {
    
    private static final Map<Class<?>, Messages> MESSAGE_CACHE = new HashMap<>();
    private static final LanguageManager LANGUAGE_MANAGER = MongoConfigsAPI.getLanguageManager();
    
    public static Messages getMessages(Class<?> messageClass) {
        return MESSAGE_CACHE.computeIfAbsent(messageClass, 
            k -> MongoConfigsAPI.getConfigManager().messagesOf(k));
    }
    
    public static String getMessage(Player player, Class<?> messageClass, String key, Object... args) {
        String lang = LANGUAGE_MANAGER.getPlayerLanguage(player.getUniqueId().toString());
        return getMessages(messageClass).get(lang, key, args);
    }
    
    public static void sendMessage(Player player, Class<?> messageClass, String key, Object... args) {
        String message = getMessage(player, messageClass, key, args);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    public static void broadcast(Class<?> messageClass, String key, Object... args) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendMessage(player, messageClass, key, args);
        }
    }
    
    public static String getServerMessage(String language, Class<?> messageClass, String key, Object... args) {
        return getMessages(messageClass).get(language, key, args);
    }
}

// Usage:
MessageUtils.sendMessage(player, ShopMessages.class, "purchase.success", "Diamond Sword");
MessageUtils.broadcast(ServerMessages.class, "server.restart", "5 minutes");
```

---

## 🎯 Message Organization Patterns

### 1. Feature-Based Messages

```java
// Separate message classes for each feature
@ConfigsFileProperties(name = "shop-messages")
public class ShopMessages { }

@ConfigsFileProperties(name = "guild-messages") 
public class GuildMessages { }

@ConfigsFileProperties(name = "pvp-messages")
public class PvPMessages { }

@ConfigsFileProperties(name = "command-messages")
public class CommandMessages { }
```

### 2. Context-Based Messages

```java
// Different contexts of communication
@ConfigsFileProperties(name = "gui-messages")
public class GuiMessages { }        // All GUI text

@ConfigsFileProperties(name = "chat-messages")
public class ChatMessages { }       // Chat/command responses

@ConfigsFileProperties(name = "game-messages")
public class GameMessages { }       // Gameplay notifications

@ConfigsFileProperties(name = "error-messages")
public class ErrorMessages { }      // Error handling
```

### 3. Hierarchical Organization

```javascript
// Complex nested structure
{
  "_id": "en",
  "game": {
    "pvp": {
      "arena": {
        "join": "You joined the arena!",
        "leave": "You left the arena!",
        "death": "You died in the arena!"
      },
      "tournament": {
        "start": "Tournament started!",
        "end": "Tournament ended!",
        "winner": "{0} won the tournament!"
      }
    },
    "economy": {
      "shop": {
        "purchase": {
          "success": "Purchase successful!",
          "failed": "Purchase failed: {0}"
        }
      }
    }
  }
}
```

---

## 📊 Best Practices

### 1. Consistent Key Naming

```javascript
// ✅ Good - consistent naming pattern
{
  "shop": {
    "purchase_success": "Purchase successful!",
    "purchase_failed": "Purchase failed!",
    "item_not_found": "Item not found!"
  },
  "guild": {
    "create_success": "Guild created!",
    "create_failed": "Guild creation failed!",
    "member_not_found": "Member not found!"
  }
}

// ❌ Bad - inconsistent naming
{
  "shop": {
    "buyOK": "Purchase successful!",
    "purchase-fail": "Purchase failed!",
    "ItemNotFound": "Item not found!"
  }
}
```

### 2. Placeholder Consistency

```javascript
// ✅ Good - numbered placeholders
{
  "welcome": "Hello {0}! You have {1} coins and {2} experience.",
  "guild_info": "Guild {0} has {1} members and {2} total power."
}

// ✅ Also good - named placeholders (if your system supports it)
{
  "welcome": "Hello {player}! You have {coins} coins and {exp} experience."
}
```

### 3. Fallback Messages

```java
public class SafeMessageUtils {
    
    public static String getMessage(Player player, Class<?> messageClass, String key, Object... args) {
        try {
            String lang = LANGUAGE_MANAGER.getPlayerLanguage(player.getUniqueId().toString());
            Messages messages = getMessages(messageClass);
            
            // Try player's language first
            String message = messages.get(lang, key, args);
            if (message != null && !message.startsWith("Missing message:")) {
                return message;
            }
            
            // Fallback to English
            message = messages.get("en", key, args);
            if (message != null && !message.startsWith("Missing message:")) {
                return message;
            }
            
            // Final fallback
            return "Message not found: " + key;
            
        } catch (Exception e) {
            return "Error loading message: " + key;
        }
    }
}
```

---

*Next: Learn about [[Class Based vs Key Object]] approaches for different use cases.*