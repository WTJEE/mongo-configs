# Help System Example

> **Complete multilingual help system with GUI and commands using MongoDB Configs API**

## 🎯 Overview

This example shows how to create a full help system with:
- **Class-based message configuration** stored in MongoDB
- **Multilingual support** with automatic language detection
- **GUI help interface** with player's preferred language
- **`/pomoc` command** that opens help in correct language
- **MongoDB documents** with `lang: <language>` structure

---

## 📁 1. Message Configuration Class

First, create the help messages configuration class:

```java
@ConfigsFileProperties(name = "help-messages")
@SupportedLanguages({"en", "pl", "de", "es"})
public class HelpMessages extends MongoMessages<HelpMessages> {
    // Empty class - MongoDB Configs API handles all message storage automatically!
    // Messages will be stored as separate documents with lang: "en", lang: "pl", etc.
}
```

---

## 🗄️ 2. MongoDB Document Structure

The API automatically creates documents in this format:

### English Help Messages (`lang: "en"`)
```json
{
  "_id": "help-messages",
  "lang": "en",
  "messages": {
    "gui.title": "§6Help Menu",
    "gui.commands.title": "§a§lCommands",
    "gui.commands.description": "§7Click to view available commands",
    "gui.gameplay.title": "§b§lGameplay",
    "gui.gameplay.description": "§7Learn how to play",
    "gui.rules.title": "§c§lServer Rules",
    "gui.rules.description": "§7Important server rules",
    "gui.contact.title": "§d§lContact Staff",
    "gui.contact.description": "§7Get help from moderators",
    "commands.teleport": "§7/tp <player> - §fTeleport to player",
    "commands.home": "§7/home - §fGo to your home",
    "commands.spawn": "§7/spawn - §fReturn to spawn",
    "gameplay.building": "§7You can build anywhere outside protected areas",
    "gameplay.economy": "§7Use /shop to buy and sell items",
    "rules.no_griefing": "§cNo griefing or destroying other players' builds",
    "rules.be_respectful": "§cBe respectful to all players",
    "contact.discord": "§7Join our Discord: §bdiscord.gg/yourserver",
    "contact.website": "§7Visit: §fwww.yourserver.com"
  }
}
```

### Polish Help Messages (`lang: "pl"`)
```json
{
  "_id": "help-messages",
  "lang": "pl",
  "messages": {
    "gui.title": "§6Menu Pomocy",
    "gui.commands.title": "§a§lKomendy",
    "gui.commands.description": "§7Kliknij aby zobaczyć dostępne komendy",
    "gui.gameplay.title": "§b§lRozgrywka",
    "gui.gameplay.description": "§7Dowiedz się jak grać",
    "gui.rules.title": "§c§lZasady Serwera",
    "gui.rules.description": "§7Ważne zasady serwera",
    "gui.contact.title": "§d§lKontakt z Administracją",
    "gui.contact.description": "§7Uzyskaj pomoc od moderatorów",
    "commands.teleport": "§7/tp <gracz> - §fTeleportuj się do gracza",
    "commands.home": "§7/home - §fIdź do swojego domu",
    "commands.spawn": "§7/spawn - §fWróć na spawn",
    "gameplay.building": "§7Możesz budować wszędzie poza chronionymi obszarami",
    "gameplay.economy": "§7Użyj /shop aby kupować i sprzedawać przedmioty",
    "rules.no_griefing": "§cZakaz griefingu i niszczenia budowli innych graczy",
    "rules.be_respectful": "§cBądź uprzejmy wobec wszystkich graczy",
    "contact.discord": "§7Dołącz na Discord: §bdiscord.gg/yourserver",
    "contact.website": "§7Odwiedź: §fwww.yourserver.com"
  }
}
```

### German Help Messages (`lang: "de"`)
```json
{
  "_id": "help-messages",
  "lang": "de",
  "messages": {
    "gui.title": "§6Hilfe-Menü",
    "gui.commands.title": "§a§lBefehle",
    "gui.commands.description": "§7Klicken Sie um verfügbare Befehle zu sehen",
    "gui.gameplay.title": "§b§lSpielablauf",
    "gui.gameplay.description": "§7Lernen Sie wie man spielt",
    "gui.rules.title": "§c§lServer-Regeln",
    "gui.rules.description": "§7Wichtige Server-Regeln",
    "gui.contact.title": "§d§lStaff Kontaktieren",
    "gui.contact.description": "§7Hilfe von Moderatoren erhalten",
    "commands.teleport": "§7/tp <spieler> - §fZu Spieler teleportieren",
    "commands.home": "§7/home - §fNach Hause gehen",
    "commands.spawn": "§7/spawn - §fZum Spawn zurückkehren",
    "gameplay.building": "§7Sie können überall außerhalb geschützter Bereiche bauen",
    "gameplay.economy": "§7Nutzen Sie /shop um Gegenstände zu kaufen und verkaufen",
    "rules.no_griefing": "§cKein Griefing oder Zerstören anderer Spieler-Builds",
    "rules.be_respectful": "§cSeien Sie respektvoll zu allen Spielern",
    "contact.discord": "§7Unserem Discord beitreten: §bdiscord.gg/yourserver",
    "contact.website": "§7Besuchen Sie: §fwww.yourserver.com"
  }
}
```

---

## 🎮 3. Help GUI Class

Create the help GUI that displays content in player's language:

```java
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class HelpGUI implements Listener {

    private final Messages helpMessages;
    private final LanguageManager languageManager;

    public HelpGUI() {
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        this.helpMessages = cm.messagesOf(HelpMessages.class);
        this.languageManager = MongoConfigsAPI.getLanguageManager();
    }

    public void openHelpMenu(Player player) {
        String language = getPlayerLanguage(player);
        
        // Get localized GUI title
        String title = helpMessages.get(language, "gui.title");
        Inventory gui = Bukkit.createInventory(null, 27, title);

        // Create help categories
        createHelpItem(gui, 10, Material.COMMAND_BLOCK, "gui.commands", language);
        createHelpItem(gui, 12, Material.DIAMOND_SWORD, "gui.gameplay", language);
        createHelpItem(gui, 14, Material.WRITTEN_BOOK, "gui.rules", language);
        createHelpItem(gui, 16, Material.BELL, "gui.contact", language);

        player.openInventory(gui);
    }

    private void createHelpItem(Inventory gui, int slot, Material material, String key, String language) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Set localized name and description
        String title = helpMessages.get(language, key + ".title");
        String description = helpMessages.get(language, key + ".description");

        meta.setDisplayName(title);
        meta.setLore(Arrays.asList(description, "", "§e▶ Click to view"));
        item.setItemMeta(meta);

        gui.setItem(slot, item);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().getTitle();
        String language = getPlayerLanguage(player);
        String expectedTitle = helpMessages.get(language, "gui.title");
        
        if (!title.equals(expectedTitle)) return;
        
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Handle different help categories
        Material material = clickedItem.getType();
        switch (material) {
            case COMMAND_BLOCK -> showCommands(player, language);
            case DIAMOND_SWORD -> showGameplay(player, language);
            case WRITTEN_BOOK -> showRules(player, language);
            case BELL -> showContact(player, language);
        }
    }

    private void showCommands(Player player, String language) {
        player.closeInventory();
        player.sendMessage("§6§l=== " + helpMessages.get(language, "gui.commands.title") + " §6§l===");
        player.sendMessage(helpMessages.get(language, "commands.teleport"));
        player.sendMessage(helpMessages.get(language, "commands.home"));
        player.sendMessage(helpMessages.get(language, "commands.spawn"));
        player.sendMessage("§6§l========================");
    }

    private void showGameplay(Player player, String language) {
        player.closeInventory();
        player.sendMessage("§6§l=== " + helpMessages.get(language, "gui.gameplay.title") + " §6§l===");
        player.sendMessage(helpMessages.get(language, "gameplay.building"));
        player.sendMessage(helpMessages.get(language, "gameplay.economy"));
        player.sendMessage("§6§l========================");
    }

    private void showRules(Player player, String language) {
        player.closeInventory();
        player.sendMessage("§6§l=== " + helpMessages.get(language, "gui.rules.title") + " §6§l===");
        player.sendMessage(helpMessages.get(language, "rules.no_griefing"));
        player.sendMessage(helpMessages.get(language, "rules.be_respectful"));
        player.sendMessage("§6§l========================");
    }

    private void showContact(Player player, String language) {
        player.closeInventory();
        player.sendMessage("§6§l=== " + helpMessages.get(language, "gui.contact.title") + " §6§l===");
        player.sendMessage(helpMessages.get(language, "contact.discord"));
        player.sendMessage(helpMessages.get(language, "contact.website"));
        player.sendMessage("§6§l========================");
    }

    private String getPlayerLanguage(Player player) {
        String playerId = player.getUniqueId().toString();
        String language = languageManager.getPlayerLanguage(playerId);
        return language != null ? language : languageManager.getDefaultLanguage();
    }
}
```

---

## ⚡ 4. Help Command (`/pomoc`)

Create the command that opens the help GUI:

```java
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HelpCommand implements CommandExecutor {

    private final HelpGUI helpGUI;

    public HelpCommand(HelpGUI helpGUI) {
        this.helpGUI = helpGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        // Open help menu in player's language
        helpGUI.openHelpMenu(player);
        return true;
    }
}
```

---

## 🚀 5. Plugin Main Class Setup

Register everything in your main plugin class:

```java
public class YourPlugin extends JavaPlugin {

    private HelpGUI helpGUI;

    @Override
    public void onEnable() {
        // Initialize MongoDB Configs API (automatically done)
        
        // Setup help system
        this.helpGUI = new HelpGUI();
        
        // Register command
        getCommand("pomoc").setExecutor(new HelpCommand(helpGUI));
        
        // Register GUI listener
        getServer().getPluginManager().registerEvents(helpGUI, this);
        
        getLogger().info("Help system loaded with multilingual support!");
    }
}
```

---

## 📄 6. plugin.yml Configuration

Add the command to your `plugin.yml`:

```yaml
name: YourPlugin
version: 1.0.0
main: your.package.YourPlugin
api-version: 1.19

commands:
  pomoc:
    description: "Opens help menu in player's language"
    usage: "/pomoc"
    aliases: ["help", "aide", "hilfe", "ayuda"]
```

---

## 🌐 7. Language Management

### Setting Player Language
```java
// Players can change their language
LanguageManager lm = MongoConfigsAPI.getLanguageManager();

// Set player language
lm.setPlayerLanguage(player.getUniqueId().toString(), "pl"); // Polish
lm.setPlayerLanguage(player.getUniqueId().toString(), "en"); // English
lm.setPlayerLanguage(player.getUniqueId().toString(), "de"); // German

// Get supported languages
String[] supportedLanguages = lm.getSupportedLanguages();
// Returns: ["en", "pl", "de", "es"]
```

### Auto-detect Player Language
```java
// You can auto-detect based on player's client locale
public void setPlayerLanguageFromLocale(Player player) {
    String locale = player.getLocale(); // e.g., "en_US", "pl_PL", "de_DE"
    String language = locale.substring(0, 2); // Extract "en", "pl", "de"
    
    LanguageManager lm = MongoConfigsAPI.getLanguageManager();
    
    if (lm.isLanguageSupported(language)) {
        lm.setPlayerLanguage(player.getUniqueId().toString(), language);
        player.sendMessage("§aLanguage set to: " + language.toUpperCase());
    } else {
        // Fall back to default
        String defaultLang = lm.getDefaultLanguage();
        lm.setPlayerLanguage(player.getUniqueId().toString(), defaultLang);
        player.sendMessage("§eLanguage set to default: " + defaultLang.toUpperCase());
    }
}
```

---

## 🎯 8. How It Works

### Message Loading Process:
1. **Player runs `/pomoc`**
2. **System gets player's language** using `languageManager.getPlayerLanguage()`
3. **GUI loads messages** using `helpMessages.get(language, "key")`
4. **MongoDB automatically returns** messages from document with `lang: "pl"` (or player's language)
5. **GUI displays** in correct language

### MongoDB Storage:
- **Class-based approach**: `HelpMessages` class maps to `help-messages` documents
- **Language separation**: Each language gets its own document with `lang: "en"`, `lang: "pl"`, etc.
- **Automatic management**: MongoDB Configs API handles document creation and retrieval
- **Real-time sync**: Changes in MongoDB instantly appear in-game

### Example Flow:
```
Player (Polish): /pomoc
→ languageManager.getPlayerLanguage() → "pl"
→ helpMessages.get("pl", "gui.title") → "§6Menu Pomocy"
→ GUI opens with Polish text

Player (English): /pomoc  
→ languageManager.getPlayerLanguage() → "en"
→ helpMessages.get("en", "gui.title") → "§6Help Menu"
→ GUI opens with English text
```

---

## ✨ 9. Benefits

✅ **Class-based configuration** - Type-safe and organized
✅ **Automatic MongoDB structure** - API handles document format
✅ **Real-time language switching** - No server restart needed
✅ **Scalable** - Easy to add new languages and messages
✅ **Performance** - Messages cached automatically
✅ **Cross-server sync** - Works across multiple servers with same MongoDB

This complete example shows how to create a professional multilingual help system using MongoDB Configs API!