# Language Commands

Comprehensive command system for managing player languages, translations, and language settings.

## 🎯 Commands Overview

The Language Commands system provides a complete set of commands for players and administrators to manage language preferences, view translations, and configure language settings.

## 📋 Core Implementation

### LanguageCommandManager

```java
public class LanguageCommandManager {
    
    private final JavaPlugin plugin;
    private final LanguageManager languageManager;
    private final MessageTranslationService translationService;
    private final PlayerLanguageStorage languageStorage;
    private final TranslationManager translationManager;
    
    public LanguageCommandManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.translationService = plugin.getTranslationService();
        this.languageStorage = plugin.getLanguageStorage();
        this.translationManager = plugin.getTranslationManager();
    }
    
    public void registerCommands() {
        PluginCommand langCmd = plugin.getCommand("lang");
        if (langCmd != null) {
            langCmd.setExecutor(new LanguageCommand(this));
            langCmd.setTabCompleter(new LanguageTabCompleter(this));
        }
        
        PluginCommand translateCmd = plugin.getCommand("translate");
        if (translateCmd != null) {
            translateCmd.setExecutor(new TranslationCommand(this));
            translateCmd.setTabCompleter(new TranslationTabCompleter(this));
        }
        
        PluginCommand langAdminCmd = plugin.getCommand("langadmin");
        if (langAdminCmd != null) {
            langAdminCmd.setExecutor(new LanguageAdminCommand(this));
            langAdminCmd.setTabCompleter(new LanguageAdminTabCompleter(this));
        }
    }
    
    // Getters for subcommands
    public LanguageManager getLanguageManager() { return languageManager; }
    public MessageTranslationService getTranslationService() { return translationService; }
    public PlayerLanguageStorage getLanguageStorage() { return languageStorage; }
    public TranslationManager getTranslationManager() { return translationManager; }
    public JavaPlugin getPlugin() { return plugin; }
}
```

### Main Language Command

```java
public class LanguageCommand implements CommandExecutor {
    
    private final LanguageCommandManager commandManager;
    private final LanguageManager languageManager;
    private final MessageTranslationService translationService;
    private final PlayerLanguageStorage languageStorage;
    
    public LanguageCommand(LanguageCommandManager commandManager) {
        this.commandManager = commandManager;
        this.languageManager = commandManager.getLanguageManager();
        this.translationService = commandManager.getTranslationService();
        this.languageStorage = commandManager.getLanguageStorage();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        String playerId = player.getUniqueId().toString();
        String language = languageStorage.getPlayerEffectiveLanguage(playerId);
        
        if (args.length == 0) {
            // Open language selection GUI
            new LanguageSelectionGUI(commandManager.getPlugin(), player).open();
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "set":
                return handleSetLanguage(player, args, language);
            case "current":
                return handleCurrentLanguage(player, language);
            case "list":
                return handleListLanguages(player, language);
            case "detect":
                return handleDetectLanguage(player, language);
            case "auto":
                return handleAutoDetectToggle(player, language);
            case "help":
                return handleHelp(player, language);
            default:
                sendUsage(player, language);
                return true;
        }
    }
    
    private boolean handleSetLanguage(Player player, String[] args, String language) {
        if (args.length < 2) {
            String message = translationService.translate(language, "command.lang.set.usage");
            player.sendMessage(ColorHelper.parseComponent(message));
            return true;
        }
        
        String targetLang = args[1].toLowerCase();
        
        if (!languageManager.isLanguageSupported(targetLang)) {
            String message = translationService.translate(language, "command.lang.set.invalid_language", targetLang);
            player.sendMessage(ColorHelper.parseComponent(message));
            return true;
        }
        
        languageStorage.setPlayerSelectedLanguage(player.getUniqueId().toString(), targetLang, "player");
        
        String langName = languageManager.getLanguageName(targetLang);
        String message = translationService.translate(language, "command.lang.set.success", langName);
        player.sendMessage(ColorHelper.parseComponent(message));
        
        return true;
    }
    
    private boolean handleCurrentLanguage(Player player, String language) {
        String currentLang = languageStorage.getPlayerEffectiveLanguage(player.getUniqueId().toString());
        String langName = languageManager.getLanguageName(currentLang);
        
        String message = translationService.translate(language, "command.lang.current.info", 
            langName, currentLang.toUpperCase());
        player.sendMessage(ColorHelper.parseComponent(message));
        
        return true;
    }
    
    private boolean handleListLanguages(Player player, String language) {
        List<String> supportedLanguages = languageManager.getSupportedLanguages();
        
        String header = translationService.translate(language, "command.lang.list.header");
        player.sendMessage(ColorHelper.parseComponent(header));
        
        String currentLang = languageStorage.getPlayerEffectiveLanguage(player.getUniqueId().toString());
        
        for (String langCode : supportedLanguages) {
            String langName = languageManager.getLanguageName(langCode);
            String indicator = langCode.equals(currentLang) ? " &a(✓)" : "";
            String line = "&f- " + langName + " &7(" + langCode.toUpperCase() + ")" + indicator;
            player.sendMessage(ColorHelper.parseComponent(line));
        }
        
        return true;
    }
    
    private boolean handleDetectLanguage(Player player, String language) {
        String detected = detectPlayerLanguage(player);
        if (detected != null) {
            languageStorage.setPlayerDetectedLanguage(player.getUniqueId().toString(), detected);
            
            String langName = languageManager.getLanguageName(detected);
            String message = translationService.translate(language, "command.lang.detect.success", langName);
            player.sendMessage(ColorHelper.parseComponent(message));
        } else {
            String message = translationService.translate(language, "command.lang.detect.failed");
            player.sendMessage(ColorHelper.parseComponent(message));
        }
        
        return true;
    }
    
    private boolean handleAutoDetectToggle(Player player, String language) {
        PlayerLanguage playerLang = languageStorage.getPlayerLanguage(player.getUniqueId().toString());
        boolean newState = !playerLang.isAutoDetectEnabled();
        
        languageStorage.updatePlayerLanguage(player.getUniqueId().toString(), 
            pl -> pl.setAutoDetectEnabled(newState));
        
        String message = translationService.translate(language, 
            newState ? "command.lang.auto.enabled" : "command.lang.auto.disabled");
        player.sendMessage(ColorHelper.parseComponent(message));
        
        return true;
    }
    
    private boolean handleHelp(Player player, String language) {
        List<String> helpLines = Arrays.asList(
            translationService.translate(language, "command.lang.help.header"),
            "&f/lang &7- " + translationService.translate(language, "command.lang.help.main"),
            "&f/lang set <language> &7- " + translationService.translate(language, "command.lang.help.set"),
            "&f/lang current &7- " + translationService.translate(language, "command.lang.help.current"),
            "&f/lang list &7- " + translationService.translate(language, "command.lang.help.list"),
            "&f/lang detect &7- " + translationService.translate(language, "command.lang.help.detect"),
            "&f/lang auto &7- " + translationService.translate(language, "command.lang.help.auto"),
            "&f/lang help &7- " + translationService.translate(language, "command.lang.help.help")
        );
        
        for (String line : helpLines) {
            player.sendMessage(ColorHelper.parseComponent(line));
        }
        
        return true;
    }
    
    private void sendUsage(Player player, String language) {
        String message = translationService.translate(language, "command.lang.usage");
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    private String detectPlayerLanguage(Player player) {
        String locale = player.getLocale();
        if (locale != null && !locale.isEmpty()) {
            return languageManager.detectFromLocale(locale);
        }
        return null;
    }
}
```

### Translation Command

```java
public class TranslationCommand implements CommandExecutor {
    
    private final LanguageCommandManager commandManager;
    private final MessageTranslationService translationService;
    private final TranslationManager translationManager;
    private final PlayerLanguageStorage languageStorage;
    
    public TranslationCommand(LanguageCommandManager commandManager) {
        this.commandManager = commandManager;
        this.translationService = commandManager.getTranslationService();
        this.translationManager = commandManager.getTranslationManager();
        this.languageStorage = commandManager.getLanguageStorage();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        String playerLang = "en"; // Default for console
        if (sender instanceof Player) {
            playerLang = languageStorage.getPlayerEffectiveLanguage(((Player) sender).getUniqueId().toString());
        }
        
        switch (args[0].toLowerCase()) {
            case "test":
                return handleTestTranslation(sender, args, playerLang);
            case "add":
                return handleAddTranslation(sender, args, playerLang);
            case "update":
                return handleUpdateTranslation(sender, args, playerLang);
            case "remove":
                return handleRemoveTranslation(sender, args, playerLang);
            case "list":
                return handleListTranslations(sender, args, playerLang);
            case "search":
                return handleSearchTranslations(sender, args, playerLang);
            default:
                sendUsage(sender);
                return true;
        }
    }
    
    private boolean handleTestTranslation(CommandSender sender, String[] args, String language) {
        if (args.length < 2) {
            String message = translationService.translate(language, "command.translate.test.usage");
            sender.sendMessage(ColorHelper.parseComponent(message));
            return true;
        }
        
        String key = args[1];
        Object[] translationArgs = new Object[args.length - 2];
        for (int i = 2; i < args.length; i++) {
            translationArgs[i - 2] = args[i];
        }
        
        String result = translationService.translate(language, key, translationArgs);
        String message = translationService.translate(language, "command.translate.test.result", key, result);
        sender.sendMessage(ColorHelper.parseComponent(message));
        
        return true;
    }
    
    private boolean handleAddTranslation(CommandSender sender, String[] args, String language) {
        if (args.length < 4) {
            String message = translationService.translate(language, "command.translate.add.usage");
            sender.sendMessage(ColorHelper.parseComponent(message));
            return true;
        }
        
        String key = args[1];
        String targetLang = args[2];
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        
        String updater = sender instanceof Player ? ((Player) sender).getName() : "console";
        translationManager.addMessage(key, targetLang, value, "custom");
        
        String message = translationService.translate(language, "command.translate.add.success", key, targetLang);
        sender.sendMessage(ColorHelper.parseComponent(message));
        
        return true;
    }
    
    private boolean handleUpdateTranslation(CommandSender sender, String[] args, String language) {
        if (args.length < 4) {
            String message = translationService.translate(language, "command.translate.update.usage");
            sender.sendMessage(ColorHelper.parseComponent(message));
            return true;
        }
        
        String key = args[1];
        String targetLang = args[2];
        String newValue = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        
        String updater = sender instanceof Player ? ((Player) sender).getName() : "console";
        translationManager.updateMessage(key, targetLang, newValue, updater);
        
        String message = translationService.translate(language, "command.translate.update.success", key, targetLang);
        sender.sendMessage(ColorHelper.parseComponent(message));
        
        return true;
    }
    
    private boolean handleRemoveTranslation(CommandSender sender, String[] args, String language) {
        if (args.length < 3) {
            String message = translationService.translate(language, "command.translate.remove.usage");
            sender.sendMessage(ColorHelper.parseComponent(message));
            return true;
        }
        
        String key = args[1];
        String targetLang = args[2];
        
        translationManager.deleteMessage(key, targetLang);
        
        String message = translationService.translate(language, "command.translate.remove.success", key, targetLang);
        sender.sendMessage(ColorHelper.parseComponent(message));
        
        return true;
    }
    
    private boolean handleListTranslations(CommandSender sender, String[] args, String language) {
        String targetLang = args.length > 1 ? args[1] : language;
        
        List<TranslatedMessage> messages = translationManager.getMessagesByLanguage(targetLang);
        
        String header = translationService.translate(language, "command.translate.list.header", targetLang);
        sender.sendMessage(ColorHelper.parseComponent(header));
        
        for (TranslatedMessage message : messages) {
            String line = "&f" + message.getKey() + ": &7" + message.getValue();
            sender.sendMessage(ColorHelper.parseComponent(line));
        }
        
        return true;
    }
    
    private boolean handleSearchTranslations(CommandSender sender, String[] args, String language) {
        if (args.length < 2) {
            String message = translationService.translate(language, "command.translate.search.usage");
            sender.sendMessage(ColorHelper.parseComponent(message));
            return true;
        }
        
        String searchTerm = args[1].toLowerCase();
        
        // This would require a search implementation in TranslationManager
        List<TranslatedMessage> results = searchTranslations(searchTerm);
        
        String header = translationService.translate(language, "command.translate.search.results", 
            searchTerm, results.size());
        sender.sendMessage(ColorHelper.parseComponent(header));
        
        for (TranslatedMessage message : results) {
            String line = "&f" + message.getKey() + " (" + message.getLanguage() + "): &7" + message.getValue();
            sender.sendMessage(ColorHelper.parseComponent(line));
        }
        
        return true;
    }
    
    private List<TranslatedMessage> searchTranslations(String searchTerm) {
        // Implementation would depend on your search system
        return new ArrayList<>();
    }
    
    private void sendUsage(CommandSender sender) {
        if (sender instanceof Player) {
            String language = languageStorage.getPlayerEffectiveLanguage(((Player) sender).getUniqueId().toString());
            List<String> helpLines = Arrays.asList(
                translationService.translate(language, "command.translate.help.header"),
                "&f/translate test <key> [args...] &7- " + translationService.translate(language, "command.translate.help.test"),
                "&f/translate add <key> <lang> <value> &7- " + translationService.translate(language, "command.translate.help.add"),
                "&f/translate update <key> <lang> <value> &7- " + translationService.translate(language, "command.translate.help.update"),
                "&f/translate remove <key> <lang> &7- " + translationService.translate(language, "command.translate.help.remove"),
                "&f/translate list [language] &7- " + translationService.translate(language, "command.translate.help.list"),
                "&f/translate search <term> &7- " + translationService.translate(language, "command.translate.help.search")
            );
            
            for (String line : helpLines) {
                sender.sendMessage(ColorHelper.parseComponent(line));
            }
        } else {
            sender.sendMessage("Translation Commands:");
            sender.sendMessage("  /translate test <key> [args...] - Test a translation");
            sender.sendMessage("  /translate add <key> <lang> <value> - Add a translation");
            sender.sendMessage("  /translate update <key> <lang> <value> - Update a translation");
            sender.sendMessage("  /translate remove <key> <lang> - Remove a translation");
            sender.sendMessage("  /translate list [language] - List translations");
            sender.sendMessage("  /translate search <term> - Search translations");
        }
    }
}
```

### Admin Command

```java
public class LanguageAdminCommand implements CommandExecutor {
    
    private final LanguageCommandManager commandManager;
    private final LanguageManager languageManager;
    private final PlayerLanguageStorage languageStorage;
    private final TranslationManager translationManager;
    
    public LanguageAdminCommand(LanguageCommandManager commandManager) {
        this.commandManager = commandManager;
        this.languageManager = commandManager.getLanguageManager();
        this.languageStorage = commandManager.getLanguageStorage();
        this.translationManager = commandManager.getTranslationManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("language.admin")) {
            sender.sendMessage(ColorHelper.parseComponent("&cYou don't have permission to use this command!"));
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "stats":
                return handleStats(sender, args);
            case "setplayer":
                return handleSetPlayerLanguage(sender, args);
            case "resetplayer":
                return handleResetPlayerLanguage(sender, args);
            case "broadcast":
                return handleBroadcast(sender, args);
            case "reload":
                return handleReload(sender, args);
            case "export":
                return handleExport(sender, args);
            case "import":
                return handleImport(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }
    
    private boolean handleStats(CommandSender sender, String[] args) {
        Map<String, Integer> languageStats = languageStorage.getLanguageUsageStatistics();
        List<PlayerLanguage> allPlayers = languageStorage.getAllPlayerLanguages();
        
        sender.sendMessage(ColorHelper.parseComponent("&6=== Language Statistics ==="));
        sender.sendMessage(ColorHelper.parseComponent("&fTotal players: &e" + allPlayers.size()));
        
        languageStats.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                String langName = languageManager.getLanguageName(entry.getKey());
                double percentage = (double) entry.getValue() / allPlayers.size() * 100;
                sender.sendMessage(ColorHelper.parseComponent(
                    "&f" + langName + " (" + entry.getKey() + "): &e" + entry.getValue() + 
                    " &7(" + String.format("%.1f", percentage) + "%)"));
            });
        
        return true;
    }
    
    private boolean handleSetPlayerLanguage(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorHelper.parseComponent("&cUsage: /langadmin setplayer <player> <language>"));
            return true;
        }
        
        String playerName = args[1];
        String language = args[2].toLowerCase();
        
        Player target = commandManager.getPlugin().getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ColorHelper.parseComponent("&cPlayer not found: " + playerName));
            return true;
        }
        
        if (!languageManager.isLanguageSupported(language)) {
            sender.sendMessage(ColorHelper.parseComponent("&cUnsupported language: " + language));
            return true;
        }
        
        languageStorage.setPlayerSelectedLanguage(target.getUniqueId().toString(), language, "admin");
        
        String langName = languageManager.getLanguageName(language);
        sender.sendMessage(ColorHelper.parseComponent("&aSet " + playerName + "'s language to " + langName));
        target.sendMessage(ColorHelper.parseComponent("&aYour language has been set to " + langName + " by an admin"));
        
        return true;
    }
    
    private boolean handleResetPlayerLanguage(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorHelper.parseComponent("&cUsage: /langadmin resetplayer <player>"));
            return true;
        }
        
        String playerName = args[1];
        Player target = commandManager.getPlugin().getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ColorHelper.parseComponent("&cPlayer not found: " + playerName));
            return true;
        }
        
        languageStorage.updatePlayerLanguage(target.getUniqueId().toString(), pl -> {
            pl.setSelectedLanguage(null, "admin");
            pl.setAutoDetectEnabled(true);
        });
        
        sender.sendMessage(ColorHelper.parseComponent("&aReset " + playerName + "'s language preferences"));
        target.sendMessage(ColorHelper.parseComponent("&aYour language preferences have been reset"));
        
        return true;
    }
    
    private boolean handleBroadcast(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorHelper.parseComponent("&cUsage: /langadmin broadcast <message_key> [args...]"));
            return true;
        }
        
        String messageKey = args[1];
        Object[] broadcastArgs = new Object[args.length - 2];
        for (int i = 2; i < args.length; i++) {
            broadcastArgs[i - 2] = args[i];
        }
        
        // Broadcast to all online players in their preferred language
        for (Player player : commandManager.getPlugin().getServer().getOnlinePlayers()) {
            String language = languageStorage.getPlayerEffectiveLanguage(player.getUniqueId().toString());
            String message = commandManager.getTranslationService().translate(language, messageKey, broadcastArgs);
            player.sendMessage(ColorHelper.parseComponent(message));
        }
        
        sender.sendMessage(ColorHelper.parseComponent("&aBroadcast sent to all players"));
        
        return true;
    }
    
    private boolean handleReload(CommandSender sender, String[] args) {
        // Reload language configurations
        commandManager.getTranslationService().invalidateAllCache();
        
        sender.sendMessage(ColorHelper.parseComponent("&aLanguage system reloaded"));
        
        return true;
    }
    
    private boolean handleExport(CommandSender sender, String[] args) {
        // Export translations to file
        String language = args.length > 1 ? args[1] : "all";
        
        // Implementation would depend on your export system
        sender.sendMessage(ColorHelper.parseComponent("&aExporting translations for language: " + language));
        
        return true;
    }
    
    private boolean handleImport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorHelper.parseComponent("&cUsage: /langadmin import <file>"));
            return true;
        }
        
        String fileName = args[1];
        
        // Implementation would depend on your import system
        sender.sendMessage(ColorHelper.parseComponent("&aImporting translations from: " + fileName));
        
        return true;
    }
    
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&6Language Admin Commands:"));
        sender.sendMessage(ColorHelper.parseComponent("&f/langadmin stats &7- Show language usage statistics"));
        sender.sendMessage(ColorHelper.parseComponent("&f/langadmin setplayer <player> <lang> &7- Set player's language"));
        sender.sendMessage(ColorHelper.parseComponent("&f/langadmin resetplayer <player> &7- Reset player's language"));
        sender.sendMessage(ColorHelper.parseComponent("&f/langadmin broadcast <key> [args...] &7- Broadcast message to all"));
        sender.sendMessage(ColorHelper.parseComponent("&f/langadmin reload &7- Reload language system"));
        sender.sendMessage(ColorHelper.parseComponent("&f/langadmin export [lang] &7- Export translations"));
        sender.sendMessage(ColorHelper.parseComponent("&f/langadmin import <file> &7- Import translations"));
    }
}
```

## 🎯 Tab Completion

### LanguageTabCompleter

```java
public class LanguageTabCompleter implements TabCompleter {
    
    private final LanguageCommandManager commandManager;
    private final LanguageManager languageManager;
    
    public LanguageTabCompleter(LanguageCommandManager commandManager) {
        this.commandManager = commandManager;
        this.languageManager = commandManager.getLanguageManager();
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // First argument completion
            List<String> commands = Arrays.asList("set", "current", "list", "detect", "auto", "help");
            completions.addAll(commands);
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "set":
                    // Complete with supported languages
                    completions.addAll(languageManager.getSupportedLanguages());
                    break;
            }
        }
        
        // Filter completions based on what the user has typed
        String currentArg = args[args.length - 1].toLowerCase();
        completions.removeIf(completion -> !completion.toLowerCase().startsWith(currentArg));
        
        return completions;
    }
}
```

### TranslationTabCompleter

```java
public class TranslationTabCompleter implements TabCompleter {
    
    private final LanguageCommandManager commandManager;
    private final TranslationManager translationManager;
    
    public TranslationTabCompleter(LanguageCommandManager commandManager) {
        this.commandManager = commandManager;
        this.translationManager = commandManager.getTranslationManager();
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // First argument completion
            List<String> commands = Arrays.asList("test", "add", "update", "remove", "list", "search");
            completions.addAll(commands);
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "test":
                case "update":
                case "remove":
                    // Complete with existing message keys
                    completions.addAll(getExistingKeys());
                    break;
                case "list":
                    // Complete with supported languages
                    completions.addAll(commandManager.getLanguageManager().getSupportedLanguages());
                    break;
                case "add":
                    // No specific completion for new keys
                    break;
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "add":
                case "update":
                case "remove":
                    // Complete with supported languages
                    completions.addAll(commandManager.getLanguageManager().getSupportedLanguages());
                    break;
            }
        }
        
        // Filter completions
        if (args.length > 0) {
            String currentArg = args[args.length - 1].toLowerCase();
            completions.removeIf(completion -> !completion.toLowerCase().startsWith(currentArg));
        }
        
        return completions;
    }
    
    private List<String> getExistingKeys() {
        // This would need to be implemented to get all existing message keys
        // For now, return some common ones
        return Arrays.asList("welcome", "goodbye", "error", "success");
    }
}
```

## 🔧 Integration Examples

### Plugin Integration

```java
public class MultilingualPlugin extends JavaPlugin {
    
    private LanguageCommandManager commandManager;
    
    @Override
    public void onEnable() {
        // Initialize other systems...
        
        // Initialize command manager
        commandManager = new LanguageCommandManager(this);
        commandManager.registerCommands();
        
        getLogger().info("Language commands registered!");
    }
    
    // Getters...
    public LanguageCommandManager getCommandManager() { return commandManager; }
}
```

### Permission Setup

```yaml
# plugin.yml
commands:
  lang:
    description: "Manage your language preferences"
    usage: "/lang [set|current|list|detect|auto|help]"
    permission: "language.use"
  translate:
    description: "Manage translations"
    usage: "/translate [test|add|update|remove|list|search]"
    permission: "language.translate"
  langadmin:
    description: "Admin language management"
    usage: "/langadmin [stats|setplayer|resetplayer|broadcast|reload|export|import]"
    permission: "language.admin"

permissions:
  language.use:
    description: "Allows players to manage their language preferences"
    default: true
  language.translate:
    description: "Allows managing translations"
    default: op
  language.admin:
    description: "Allows admin language management"
    default: op
```

---

*Next: Learn about [[Plugin Integration]] for seamless integration with Minecraft plugins.*