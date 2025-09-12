# Message Translation

Dynamic message translation system with placeholder support, pluralization, and real-time updates.

## 💬 Translation Overview

The Message Translation system provides comprehensive support for multilingual messages with advanced features like placeholder replacement, pluralization, and real-time translation updates.

## 📋 Core Implementation

### MessageTranslationService

```java
public class MessageTranslationService {
    
    private final ConfigManager configManager;
    private final Map<String, Map<String, String>> messageCache = new ConcurrentHashMap<>();
    private final Map<String, MessageFormatter> formatters = new ConcurrentHashMap<>();
    private final ExecutorService translationLoader;
    
    public MessageTranslationService(ConfigManager configManager) {
        this.configManager = configManager;
        this.translationLoader = Executors.newCachedThreadPool();
        
        // Register default formatters
        registerFormatter("default", new DefaultMessageFormatter());
        registerFormatter("plural", new PluralMessageFormatter());
        registerFormatter("datetime", new DateTimeMessageFormatter());
        registerFormatter("number", new NumberMessageFormatter());
        
        // Setup change streams for real-time updates
        setupChangeStreams();
    }
    
    private void setupChangeStreams() {
        configManager.watchCollection(TranslatedMessage.class, changeEvent -> {
            TranslatedMessage message = changeEvent.getDocument();
            if (message != null) {
                invalidateCache(message.getLanguage(), message.getKey());
            }
        });
    }
    
    public String translate(String language, String key, Object... args) {
        return translate(language, key, Map.of(), args);
    }
    
    public String translate(String language, String key, Map<String, Object> context, Object... args) {
        // Get message template
        String template = getMessageTemplate(language, key);
        if (template == null) {
            return key; // Fallback to key
        }
        
        // Apply formatters
        return applyFormatters(template, language, context, args);
    }
    
    private String getMessageTemplate(String language, String key) {
        // Try cache first
        Map<String, String> langCache = messageCache.get(language);
        if (langCache != null) {
            String cached = langCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        
        // Load from database
        try {
            TranslatedMessage message = configManager.findFirst(TranslatedMessage.class,
                Filters.and(
                    Filters.eq("language", language),
                    Filters.eq("key", key)
                ));
            
            if (message != null) {
                // Cache the result
                messageCache.computeIfAbsent(language, k -> new ConcurrentHashMap<>())
                           .put(key, message.getValue());
                return message.getValue();
            }
        } catch (Exception e) {
            // Log error
        }
        
        // Try fallback language
        if (!language.equals("en")) {
            return getMessageTemplate("en", key);
        }
        
        return null;
    }
    
    private String applyFormatters(String template, String language, Map<String, Object> context, Object[] args) {
        String result = template;
        
        // Replace simple placeholders {0}, {1}, etc.
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        
        // Apply named formatters
        Pattern formatterPattern = Pattern.compile("\\{(\\w+):([^}]+)\\}");
        Matcher matcher = formatterPattern.matcher(result);
        
        while (matcher.find()) {
            String formatterName = matcher.group(1);
            String value = matcher.group(2);
            
            MessageFormatter formatter = formatters.get(formatterName);
            if (formatter != null) {
                String formatted = formatter.format(value, language, context);
                result = result.replace(matcher.group(0), formatted);
            }
        }
        
        return result;
    }
    
    public void registerFormatter(String name, MessageFormatter formatter) {
        formatters.put(name, formatter);
    }
    
    public void invalidateCache(String language, String key) {
        Map<String, String> langCache = messageCache.get(language);
        if (langCache != null) {
            langCache.remove(key);
        }
    }
    
    public void invalidateCache(String language) {
        messageCache.remove(language);
    }
    
    public void invalidateAllCache() {
        messageCache.clear();
    }
    
    public CompletableFuture<String> translateAsync(String language, String key, Object... args) {
        return CompletableFuture.supplyAsync(() -> translate(language, key, args), translationLoader);
    }
    
    public void shutdown() {
        translationLoader.shutdown();
        try {
            if (!translationLoader.awaitTermination(5, TimeUnit.SECONDS)) {
                translationLoader.shutdownNow();
            }
        } catch (InterruptedException e) {
            translationLoader.shutdownNow();
        }
    }
    
    // Formatter interface
    public interface MessageFormatter {
        String format(String value, String language, Map<String, Object> context);
    }
}
```

### TranslatedMessage Entity

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "translation_system")
@ConfigsCollection(collection = "translated_messages")
public class TranslatedMessage {
    
    @ConfigsField
    private String id;
    
    @ConfigsField
    private String key;
    
    @ConfigsField
    private String language;
    
    @ConfigsField
    private String value;
    
    @ConfigsField
    private String category;
    
    @ConfigsField
    private String description;
    
    @ConfigsField
    private long lastModified;
    
    @ConfigsField
    private String lastModifiedBy;
    
    @ConfigsField
    private boolean approved = false;
    
    @ConfigsField
    private List<String> tags = new ArrayList<>();
    
    @ConfigsField
    private Map<String, Object> metadata = new HashMap<>();
    
    @ConfigsField
    private List<String> placeholders = new ArrayList<>();
    
    // Constructors
    public TranslatedMessage() {
        this.lastModified = System.currentTimeMillis();
    }
    
    public TranslatedMessage(String key, String language, String value) {
        this();
        this.key = key;
        this.language = language;
        this.value = value;
        this.id = key + "_" + language;
    }
    
    // Validation
    public boolean isValid() {
        return key != null && !key.isEmpty() &&
               language != null && !language.isEmpty() &&
               value != null && !value.isEmpty();
    }
    
    public void extractPlaceholders() {
        Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(value);
        
        placeholders.clear();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!placeholders.contains(placeholder)) {
                placeholders.add(placeholder);
            }
        }
    }
    
    public boolean hasPlaceholder(String placeholder) {
        return placeholders.contains(placeholder);
    }
    
    public void addTag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
    }
    
    public void removeTag(String tag) {
        tags.remove(tag);
    }
    
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    // Getters and setters...
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    
    public String getLastModifiedBy() { return lastModifiedBy; }
    public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }
    
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    
    public List<String> getTags() { return new ArrayList<>(tags); }
    public void setTags(List<String> tags) { this.tags = tags; }
    
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public List<String> getPlaceholders() { return new ArrayList<>(placeholders); }
    public void setPlaceholders(List<String> placeholders) { this.placeholders = placeholders; }
}
```

## 🎯 Advanced Formatters

### PluralMessageFormatter

```java
public class PluralMessageFormatter implements MessageTranslationService.MessageFormatter {
    
    private final Map<String, PluralRules> pluralRules = new HashMap<>();
    
    public PluralMessageFormatter() {
        // Initialize plural rules for different languages
        pluralRules.put("en", new EnglishPluralRules());
        pluralRules.put("pl", new PolishPluralRules());
        pluralRules.put("es", new SpanishPluralRules());
        pluralRules.put("de", new GermanPluralRules());
        pluralRules.put("fr", new FrenchPluralRules());
    }
    
    @Override
    public String format(String value, String language, Map<String, Object> context) {
        // Parse value as "count|singular|plural" or "count|zero|one|other"
        String[] parts = value.split("\\|");
        if (parts.length < 2) {
            return value;
        }
        
        try {
            int count = Integer.parseInt(parts[0]);
            PluralRules rules = pluralRules.getOrDefault(language, pluralRules.get("en"));
            String form = rules.getPluralForm(count);
            
            // Find the appropriate form
            for (int i = 1; i < parts.length; i++) {
                String[] formParts = parts[i].split(":");
                if (formParts.length == 2 && formParts[0].equals(form)) {
                    return formParts[1].replace("{count}", String.valueOf(count));
                }
            }
            
            // Fallback to last part
            String fallback = parts[parts.length - 1];
            return fallback.replace("{count}", String.valueOf(count));
            
        } catch (NumberFormatException e) {
            return value;
        }
    }
    
    public interface PluralRules {
        String getPluralForm(int count);
    }
    
    public static class EnglishPluralRules implements PluralRules {
        @Override
        public String getPluralForm(int count) {
            return count == 1 ? "one" : "other";
        }
    }
    
    public static class PolishPluralRules implements PluralRules {
        @Override
        public String getPluralForm(int count) {
            if (count == 1) return "one";
            int lastDigit = count % 10;
            int lastTwoDigits = count % 100;
            
            if (lastTwoDigits >= 12 && lastTwoDigits <= 14) return "other";
            if (lastDigit >= 2 && lastDigit <= 4) return "few";
            return "other";
        }
    }
    
    public static class SpanishPluralRules implements PluralRules {
        @Override
        public String getPluralForm(int count) {
            return count == 1 ? "one" : "other";
        }
    }
    
    public static class GermanPluralRules implements PluralRules {
        @Override
        public String getPluralForm(int count) {
            return count == 1 ? "one" : "other";
        }
    }
    
    public static class FrenchPluralRules implements PluralRules {
        @Override
        public String getPluralForm(int count) {
            return count <= 1 ? "one" : "other";
        }
    }
}
```

### DateTimeMessageFormatter

```java
public class DateTimeMessageFormatter implements MessageTranslationService.MessageFormatter {
    
    private final Map<String, String> datePatterns = new HashMap<>();
    private final Map<String, String> timePatterns = new HashMap<>();
    
    public DateTimeMessageFormatter() {
        // Initialize patterns for different languages
        datePatterns.put("en", "MM/dd/yyyy");
        datePatterns.put("pl", "dd.MM.yyyy");
        datePatterns.put("es", "dd/MM/yyyy");
        datePatterns.put("de", "dd.MM.yyyy");
        datePatterns.put("fr", "dd/MM/yyyy");
        
        timePatterns.put("en", "hh:mm a");
        timePatterns.put("pl", "HH:mm");
        timePatterns.put("es", "HH:mm");
        timePatterns.put("de", "HH:mm");
        timePatterns.put("fr", "HH:mm");
    }
    
    @Override
    public String format(String value, String language, Map<String, Object> context) {
        // Parse value as "timestamp|format" where format is "date", "time", or "datetime"
        String[] parts = value.split("\\|");
        if (parts.length != 2) {
            return value;
        }
        
        try {
            long timestamp = Long.parseLong(parts[0]);
            String format = parts[1].toLowerCase();
            
            SimpleDateFormat sdf;
            switch (format) {
                case "date":
                    sdf = new SimpleDateFormat(datePatterns.getOrDefault(language, "yyyy-MM-dd"));
                    break;
                case "time":
                    sdf = new SimpleDateFormat(timePatterns.getOrDefault(language, "HH:mm"));
                    break;
                case "datetime":
                    String datePattern = datePatterns.getOrDefault(language, "yyyy-MM-dd");
                    String timePattern = timePatterns.getOrDefault(language, "HH:mm");
                    sdf = new SimpleDateFormat(datePattern + " " + timePattern);
                    break;
                case "relative":
                    return formatRelativeTime(timestamp, language);
                default:
                    return value;
            }
            
            return sdf.format(new Date(timestamp));
            
        } catch (NumberFormatException e) {
            return value;
        }
    }
    
    private String formatRelativeTime(long timestamp, String language) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return formatPlural(language, days, "day", "days");
        } else if (hours > 0) {
            return formatPlural(language, hours, "hour", "hours");
        } else if (minutes > 0) {
            return formatPlural(language, minutes, "minute", "minutes");
        } else {
            return formatPlural(language, seconds, "second", "seconds");
        }
    }
    
    private String formatPlural(String language, long count, String singular, String plural) {
        if (count == 1) {
            return count + " " + singular;
        } else {
            return count + " " + plural;
        }
    }
}
```

### NumberMessageFormatter

```java
public class NumberMessageFormatter implements MessageTranslationService.MessageFormatter {
    
    private final Map<String, NumberFormat> numberFormats = new HashMap<>();
    private final Map<String, DecimalFormat> currencyFormats = new HashMap<>();
    
    public NumberMessageFormatter() {
        // Initialize number formats for different languages
        for (String lang : Arrays.asList("en", "pl", "es", "de", "fr")) {
            Locale locale = Locale.forLanguageTag(lang);
            numberFormats.put(lang, NumberFormat.getNumberInstance(locale));
            currencyFormats.put(lang, (DecimalFormat) NumberFormat.getCurrencyInstance(locale));
        }
    }
    
    @Override
    public String format(String value, String language, Map<String, Object> context) {
        // Parse value as "number|format" where format is "number", "currency", "percent"
        String[] parts = value.split("\\|");
        if (parts.length != 2) {
            return value;
        }
        
        try {
            double number = Double.parseDouble(parts[0]);
            String format = parts[1].toLowerCase();
            
            NumberFormat formatter;
            switch (format) {
                case "number":
                    formatter = numberFormats.getOrDefault(language, NumberFormat.getNumberInstance());
                    break;
                case "currency":
                    formatter = currencyFormats.getOrDefault(language, 
                        (DecimalFormat) NumberFormat.getCurrencyInstance());
                    break;
                case "percent":
                    formatter = NumberFormat.getPercentInstance(Locale.forLanguageTag(language));
                    break;
                default:
                    return value;
            }
            
            return formatter.format(number);
            
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
```

## 🔄 Real-time Translation Updates

### TranslationManager

```java
public class TranslationManager {
    
    private final ConfigManager configManager;
    private final MessageTranslationService translationService;
    private final Map<String, TranslationUpdateListener> listeners = new ConcurrentHashMap<>();
    
    public TranslationManager(ConfigManager configManager, MessageTranslationService translationService) {
        this.configManager = configManager;
        this.translationService = translationService;
        
        setupChangeStreams();
    }
    
    private void setupChangeStreams() {
        configManager.watchCollection(TranslatedMessage.class, changeEvent -> {
            TranslatedMessage message = changeEvent.getDocument();
            if (message != null) {
                notifyTranslationUpdate(message);
            }
        });
    }
    
    public void addMessage(String key, String language, String value, String category) {
        TranslatedMessage message = new TranslatedMessage(key, language, value);
        message.setCategory(category);
        message.setLastModifiedBy("system");
        message.extractPlaceholders();
        
        configManager.save(message);
    }
    
    public void updateMessage(String key, String language, String newValue, String updatedBy) {
        try {
            TranslatedMessage message = configManager.findFirst(TranslatedMessage.class,
                Filters.and(
                    Filters.eq("key", key),
                    Filters.eq("language", language)
                ));
            
            if (message != null) {
                message.setValue(newValue);
                message.setLastModified(System.currentTimeMillis());
                message.setLastModifiedBy(updatedBy);
                message.extractPlaceholders();
                
                configManager.save(message);
            }
        } catch (Exception e) {
            // Log error
        }
    }
    
    public void deleteMessage(String key, String language) {
        try {
            TranslatedMessage message = configManager.findFirst(TranslatedMessage.class,
                Filters.and(
                    Filters.eq("key", key),
                    Filters.eq("language", language)
                ));
            
            if (message != null) {
                configManager.delete(TranslatedMessage.class, message.getId());
            }
        } catch (Exception e) {
            // Log error
        }
    }
    
    public List<TranslatedMessage> getMessagesByCategory(String category) {
        try {
            return configManager.find(TranslatedMessage.class, Filters.eq("category", category));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    public List<TranslatedMessage> getMessagesByLanguage(String language) {
        try {
            return configManager.find(TranslatedMessage.class, Filters.eq("language", language));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    public Map<String, String> getAllMessagesForLanguage(String language) {
        Map<String, String> messages = new HashMap<>();
        
        try {
            List<TranslatedMessage> translatedMessages = configManager.find(
                TranslatedMessage.class, Filters.eq("language", language));
            
            for (TranslatedMessage message : translatedMessages) {
                messages.put(message.getKey(), message.getValue());
            }
        } catch (Exception e) {
            // Log error
        }
        
        return messages;
    }
    
    public void addTranslationUpdateListener(String key, TranslationUpdateListener listener) {
        listeners.put(key, listener);
    }
    
    public void removeTranslationUpdateListener(String key) {
        listeners.remove(key);
    }
    
    private void notifyTranslationUpdate(TranslatedMessage message) {
        for (TranslationUpdateListener listener : listeners.values()) {
            try {
                listener.onTranslationUpdated(message);
            } catch (Exception e) {
                // Log error
            }
        }
    }
    
    public interface TranslationUpdateListener {
        void onTranslationUpdated(TranslatedMessage message);
    }
}
```

## 🎨 Integration Examples

### Plugin Integration

```java
public class MultilingualPlugin extends JavaPlugin {
    
    private ConfigManager configManager;
    private MessageTranslationService translationService;
    private TranslationManager translationManager;
    
    @Override
    public void onEnable() {
        // Initialize MongoDB Configs API
        configManager = MongoConfigsAPI.createConfigManager(
            getConfig().getString("mongodb.uri"),
            getConfig().getString("mongodb.database")
        );
        
        // Initialize translation system
        translationService = new MessageTranslationService(configManager);
        translationManager = new TranslationManager(configManager, translationService);
        
        // Load default messages
        loadDefaultMessages();
        
        // Register commands
        getCommand("translate").setExecutor(new TranslationCommand(this));
        
        getLogger().info("Multilingual Plugin with translation system enabled!");
    }
    
    private void loadDefaultMessages() {
        // Load English messages
        translationManager.addMessage("welcome", "en", "Welcome to the server, {0}!", "general");
        translationManager.addMessage("goodbye", "en", "Goodbye, {0}!", "general");
        translationManager.addMessage("items_found", "en", "{plural:{0}|one:Found {0} item|other:Found {0} items}", "general");
        translationManager.addMessage("last_seen", "en", "Last seen: {datetime:{0}|datetime}", "general");
        
        // Load Polish messages
        translationManager.addMessage("welcome", "pl", "Witaj na serwerze, {0}!", "general");
        translationManager.addMessage("goodbye", "pl", "Do widzenia, {0}!", "general");
        translationManager.addMessage("items_found", "pl", "{plural:{0}|one:Znalaziono {0} przedmiot|few:Znalaziono {0} przedmioty|other:Znalaziono {0} przedmiotów}", "general");
        translationManager.addMessage("last_seen", "pl", "Ostatnio widziany: {datetime:{0}|datetime}", "general");
    }
    
    public String translate(Player player, String key, Object... args) {
        String language = getPlayerLanguage(player);
        return translationService.translate(language, key, args);
    }
    
    public String translate(String playerId, String key, Object... args) {
        String language = getPlayerLanguage(playerId);
        return translationService.translate(language, key, args);
    }
    
    private String getPlayerLanguage(Player player) {
        // Implementation depends on your language storage system
        return "en"; // Placeholder
    }
    
    private String getPlayerLanguage(String playerId) {
        // Implementation depends on your language storage system
        return "en"; // Placeholder
    }
    
    // Getters...
    public MessageTranslationService getTranslationService() { return translationService; }
    public TranslationManager getTranslationManager() { return translationManager; }
}
```

### TranslationCommand

```java
public class TranslationCommand implements CommandExecutor {
    
    private final MultilingualPlugin plugin;
    private final MessageTranslationService translationService;
    private final TranslationManager translationManager;
    
    public TranslationCommand(MultilingualPlugin plugin) {
        this.plugin = plugin;
        this.translationService = translationService;
        this.translationManager = translationManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "test":
                return handleTestTranslation(sender, args);
            case "add":
                return handleAddTranslation(sender, args);
            case "update":
                return handleUpdateTranslation(sender, args);
            case "list":
                return handleListTranslations(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    private boolean handleTestTranslation(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /translate test <key> [args...]");
            return true;
        }
        
        String key = args[1];
        Object[] translationArgs = new Object[args.length - 2];
        for (int i = 2; i < args.length; i++) {
            translationArgs[i - 2] = args[i];
        }
        
        String language = "en"; // Default for console
        if (sender instanceof Player) {
            language = plugin.getPlayerLanguage((Player) sender);
        }
        
        String result = translationService.translate(language, key, translationArgs);
        sender.sendMessage("Translation: " + result);
        
        return true;
    }
    
    private boolean handleAddTranslation(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("Usage: /translate add <key> <language> <value>");
            return true;
        }
        
        String key = args[1];
        String language = args[2];
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        
        translationManager.addMessage(key, language, value, "custom");
        sender.sendMessage("Translation added successfully!");
        
        return true;
    }
    
    private boolean handleUpdateTranslation(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("Usage: /translate update <key> <language> <new_value>");
            return true;
        }
        
        String key = args[1];
        String language = args[2];
        String newValue = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        
        String updater = sender instanceof Player ? ((Player) sender).getName() : "console";
        translationManager.updateMessage(key, language, newValue, updater);
        sender.sendMessage("Translation updated successfully!");
        
        return true;
    }
    
    private boolean handleListTranslations(CommandSender sender, String[] args) {
        String language = args.length > 1 ? args[1] : "en";
        
        List<TranslatedMessage> messages = translationManager.getMessagesByLanguage(language);
        
        sender.sendMessage("Translations for " + language.toUpperCase() + ":");
        for (TranslatedMessage message : messages) {
            sender.sendMessage("  " + message.getKey() + ": " + message.getValue());
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("Translation Commands:");
        sender.sendMessage("  /translate test <key> [args...] - Test a translation");
        sender.sendMessage("  /translate add <key> <lang> <value> - Add a translation");
        sender.sendMessage("  /translate update <key> <lang> <value> - Update a translation");
        sender.sendMessage("  /translate list [language] - List translations");
    }
}
```

---

*Next: Learn about [[Language Commands]] for player language management.*