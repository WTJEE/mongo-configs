# Help & FAQ

Comprehensive help and frequently asked questions for the MongoDB Configs API, including common issues, best practices, and troubleshooting tips.

## ❓ Frequently Asked Questions

### Getting Started

#### Q: What is the MongoDB Configs API?

**A:** The MongoDB Configs API is a powerful Java library for type-safe configuration management in Minecraft plugins. It provides:

- **Type-safe configuration classes** with annotations
- **MongoDB backend** for persistent storage
- **Real-time synchronization** via Change Streams
- **Multilingual support** with automatic message loading
- **Advanced GUI components** for configuration management
- **Caching strategies** for optimal performance
- **Async operations** for non-blocking configuration access

#### Q: How do I add the MongoDB Configs API to my project?

**A:** Add the following to your `pom.xml`:

```xml
<dependency>
    <groupId>xyz.wtje.mongoconfigs</groupId>
    <artifactId>mongo-configs-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

Or for Gradle:

```gradle
dependencies {
    implementation 'xyz.wtje.mongoconfigs:mongo-configs-api:1.0.0'
}
```

#### Q: What are the system requirements?

**A:**
- **Java:** 17 or higher
- **MongoDB:** 5.5 or higher (for Change Streams)
- **Minecraft:** Paper/Spigot 1.16.5 or higher
- **Memory:** Minimum 2GB RAM recommended
- **Storage:** Depends on configuration size

### Configuration Basics

#### Q: How do I create my first configuration class?

**A:** Here's a simple example:

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "myplugin")
@ConfigsCollection(collection = "config")
public class MyPluginConfig {
    
    // Basic fields
    public String serverName = "MyServer";
    public boolean debugMode = false;
    public int maxPlayers = 100;
    
    // Lists and maps
    public List<String> worlds = Arrays.asList("world", "world_nether");
    public Map<String, Integer> itemLimits = new HashMap<>();
    
    // Custom objects
    public DatabaseConfig database = new DatabaseConfig();
    
    public static class DatabaseConfig {
        public String host = "localhost";
        public int port = 27017;
        public String database = "myplugin";
    }
}
```

#### Q: How do I save and load configurations?

**A:**
```java
// Initialize
ConfigManager configManager = MongoConfigsPlugin.getInstance().getConfigManager();

// Save configuration
MyPluginConfig config = new MyPluginConfig();
config.serverName = "MyAwesomeServer";
configManager.save(config);

// Load configuration
MyPluginConfig loadedConfig = configManager.get(MyPluginConfig.class, "my_config");
if (loadedConfig == null) {
    // Handle missing configuration
    loadedConfig = new MyPluginConfig();
    configManager.save(loadedConfig);
}
```

#### Q: What annotations are available?

**A:**
- `@ConfigsFileProperties` - Marks a class as a configuration class
- `@ConfigsDatabase` - Specifies the MongoDB database
- `@ConfigsCollection` - Specifies the MongoDB collection
- `@SupportedLanguages` - Defines supported languages for translation

### Multilingual Support

#### Q: How do I add multilingual support?

**A:** First, create a Messages interface:

```java
public interface PluginMessages extends Messages {
    // Your message methods here
}
```

Then implement it:

```java
public class MyPlugin extends JavaPlugin {
    
    private Messages messages;
    
    @Override
    public void onEnable() {
        // Initialize messages
        this.messages = new MongoMessages(this);
        
        // Register messages (this would be in your message files)
        // The API will automatically load from MongoDB
    }
    
    public Messages getMessages() {
        return messages;
    }
}
```

#### Q: How do I create translation files?

**A:** Translations are stored in MongoDB. You can create them programmatically:

```java
public void createTranslations() {
    ConfigManager configManager = getConfigManager();
    
    // English translations
    TranslatedMessage welcomeEn = new TranslatedMessage();
    welcomeEn.language = "en";
    welcomeEn.key = "welcome";
    welcomeEn.message = "Welcome to the server, {0}!";
    configManager.save(welcomeEn);
    
    // Spanish translations
    TranslatedMessage welcomeEs = new TranslatedMessage();
    welcomeEs.language = "es";
    welcomeEs.key = "welcome";
    welcomeEs.message = "¡Bienvenido al servidor, {0}!";
    configManager.save(welcomeEs);
}
```

#### Q: How do players change their language?

**A:** Use the built-in language commands:

```java
// Players can use:
/lang list          // List available languages
/lang set <lang>    // Set their language
/lang current       // Check current language
```

Or programmatically:

```java
public void setPlayerLanguage(Player player, String language) {
    LanguageManager langManager = getLanguageManager();
    langManager.setPlayerLanguage(player, language);
}
```

### GUI Development

#### Q: How do I create a basic GUI?

**A:**
```java
public class MyGUI extends BaseGUI {
    
    public MyGUI(Player player) {
        super(player, "My GUI", 3); // 3 rows = 27 slots
    }
    
    @Override
    protected void initializeItems() {
        // Add items to the GUI
        setItem(11, createItem(Material.DIAMOND, "&bDiamond Item", 
            "&7This is a diamond item"));
        
        setItem(15, createItem(Material.GOLD_INGOT, "&eGold Item",
            "&7This is a gold item"));
    }
    
    @Override
    protected void onItemClick(int slot, ClickType clickType) {
        if (slot == 11) {
            player.sendMessage("You clicked the diamond!");
        } else if (slot == 15) {
            player.sendMessage("You clicked the gold!");
        }
    }
}
```

#### Q: How do I create a paginated GUI?

**A:**
```java
public class ShopGUI extends PaginatedGUI {
    
    private final List<ShopItem> shopItems;
    
    public ShopGUI(Player player, List<ShopItem> shopItems) {
        super(player, "Shop", 6); // 6 rows
        this.shopItems = shopItems;
    }
    
    @Override
    protected List<ItemStack> getItemsForPage(int page) {
        int startIndex = page * getItemsPerPage();
        int endIndex = Math.min(startIndex + getItemsPerPage(), shopItems.size());
        
        return shopItems.subList(startIndex, endIndex).stream()
            .map(this::createShopItem)
            .collect(Collectors.toList());
    }
    
    @Override
    protected int getTotalItems() {
        return shopItems.size();
    }
    
    @Override
    protected void onItemClick(int slot, ClickType clickType) {
        int itemIndex = getItemIndexForSlot(slot);
        if (itemIndex >= 0 && itemIndex < shopItems.size()) {
            ShopItem item = shopItems.get(itemIndex);
            // Handle purchase logic
        }
    }
}
```

#### Q: How do I create a real-time GUI?

**A:**
```java
public class LiveStatsGUI extends RealtimeGUI {
    
    public LiveStatsGUI(Player player) {
        super(player, "Live Stats", 3);
        // Update every 5 seconds
        setUpdateInterval(100);
    }
    
    @Override
    protected void updateItems() {
        // Update player count
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        setItem(11, createItem(Material.PLAYER_HEAD, "&aOnline Players", 
            "&7" + onlinePlayers + " players online"));
        
        // Update server TPS
        double tps = getServerTPS();
        Material tpsMaterial = tps > 18 ? Material.GREEN_WOOL : 
                              tps > 15 ? Material.YELLOW_WOOL : Material.RED_WOOL;
        setItem(13, createItem(tpsMaterial, "&eServer TPS", 
            "&7Current TPS: " + String.format("%.2f", tps)));
        
        // Update memory usage
        long usedMemory = getUsedMemory();
        long maxMemory = getMaxMemory();
        double memoryPercent = (double) usedMemory / maxMemory * 100;
        setItem(15, createItem(Material.BUCKET, "&bMemory Usage", 
            "&7" + String.format("%.1f%%", memoryPercent)));
    }
    
    @Override
    protected void onItemClick(int slot, ClickType clickType) {
        // Handle clicks on live stats
    }
}
```

### Performance Optimization

#### Q: How do I optimize configuration loading?

**A:**
1. **Use caching effectively:**
   ```java
   // Enable L2 caching
   @ConfigsFileProperties(cache = true)
   public class CachedConfig {
       // Fields...
   }
   ```

2. **Batch operations:**
   ```java
   // Instead of multiple saves
   for (PlayerConfig config : configs) {
       configManager.save(config);
   }
   
   // Use batch save
   configManager.saveBatch(configs);
   ```

3. **Async operations:**
   ```java
   // For non-blocking operations
   configManager.saveAsync(config)
       .thenAccept(saved -> {
           // Handle success
       })
       .exceptionally(throwable -> {
           // Handle error
           return null;
       });
   ```

#### Q: How do I monitor performance?

**A:**
```java
public class PerformanceMonitor {
    
    public void logPerformanceMetrics() {
        ConfigManager configManager = getConfigManager();
        
        // Get cache statistics
        CacheStats cacheStats = configManager.getCacheStats();
        getLogger().info("Cache hit rate: " + String.format("%.2f%%", 
            cacheStats.getHitRate() * 100));
        
        // Get operation metrics
        Map<String, OperationMetrics> metrics = configManager.getOperationMetrics();
        for (Map.Entry<String, OperationMetrics> entry : metrics.entrySet()) {
            getLogger().info("Operation '" + entry.getKey() + "': " + 
                entry.getValue().getAverageResponseTime() + "ms avg");
        }
    }
}
```

### Troubleshooting

#### Q: Why are my configurations not saving?

**A:** Common causes:

1. **Missing annotations:**
   ```java
   // Wrong - missing annotations
   public class MyConfig {
       public String value;
   }
   
   // Correct
   @ConfigsFileProperties
   @ConfigsDatabase(database = "mydb")
   @ConfigsCollection(collection = "configs")
   public class MyConfig {
       public String value;
   }
   ```

2. **MongoDB connection issues:**
   - Check connection string
   - Verify MongoDB is running
   - Check network connectivity

3. **Permission issues:**
   - Ensure write permissions to database
   - Check MongoDB user roles

#### Q: Why are translations not working?

**A:** Check these common issues:

1. **Missing translation data:**
   ```java
   // Ensure translations exist
   TranslatedMessage msg = new TranslatedMessage();
   msg.language = "en";
   msg.key = "welcome";
   msg.message = "Welcome!";
   configManager.save(msg);
   ```

2. **Language not set:**
   ```java
   // Set player language
   languageManager.setPlayerLanguage(player, "en");
   ```

3. **Cache issues:**
   ```java
   // Clear translation cache
   translationService.invalidateCache();
   ```

#### Q: Why is my GUI not updating?

**A:** For real-time GUIs:

1. **Check update interval:**
   ```java
   // Set appropriate update interval
   setUpdateInterval(20); // 1 second (20 ticks)
   ```

2. **Verify data sources:**
   ```java
   // Ensure data sources are accessible
   @Override
   protected void updateItems() {
       // Make sure data is available
       if (getData() != null) {
           // Update items
       }
   }
   ```

3. **Check player permissions:**
   ```java
   // Ensure player can see the GUI
   if (!player.hasPermission("myplugin.gui")) {
       return;
   }
   ```

### Advanced Features

#### Q: How do I use Change Streams?

**A:**
```java
public class ChangeStreamHandler {
    
    private final MongoClient mongoClient;
    private ChangeStreamIterable<Document> changeStream;
    
    public void startListening() {
        MongoDatabase database = mongoClient.getDatabase("myplugin");
        MongoCollection<Document> collection = database.getCollection("configs");
        
        changeStream = collection.watch();
        
        changeStream.forEach(change -> {
            switch (change.getOperationType()) {
                case INSERT:
                    handleInsert(change.getFullDocument());
                    break;
                case UPDATE:
                    handleUpdate(change.getDocumentKey(), change.getUpdateDescription());
                    break;
                case DELETE:
                    handleDelete(change.getDocumentKey());
                    break;
            }
        });
    }
    
    private void handleInsert(Document document) {
        // Handle new configuration
        getLogger().info("New config created: " + document.get("_id"));
    }
    
    private void handleUpdate(BsonDocument documentKey, UpdateDescription updateDesc) {
        // Handle configuration update
        String configId = documentKey.get("_id").asString().getValue();
        getLogger().info("Config updated: " + configId);
        
        // Notify listeners
        notifyConfigUpdate(configId);
    }
    
    private void handleDelete(BsonDocument documentKey) {
        // Handle configuration deletion
        String configId = documentKey.get("_id").asString().getValue();
        getLogger().info("Config deleted: " + configId);
    }
    
    public void stopListening() {
        if (changeStream != null) {
            changeStream.cursor().close();
        }
    }
}
```

#### Q: How do I implement custom caching?

**A:**
```java
public class CustomCacheManager {
    
    private final Cache<String, Object> customCache;
    
    public CustomCacheManager() {
        customCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .removalListener((String key, Object value, RemovalCause cause) -> {
                // Custom cleanup logic
                onCacheEviction(key, value, cause);
            })
            .build();
    }
    
    public <T> T get(String key, Class<T> type) {
        Object cached = customCache.getIfPresent(key);
        if (cached != null) {
            return type.cast(cached);
        }
        
        // Load from source
        T value = loadFromSource(key, type);
        if (value != null) {
            customCache.put(key, value);
        }
        
        return value;
    }
    
    public void put(String key, Object value) {
        customCache.put(key, value);
        
        // Optionally persist to database
        persistToDatabase(key, value);
    }
    
    public void invalidate(String key) {
        customCache.invalidate(key);
    }
    
    public void invalidateAll() {
        customCache.invalidateAll();
    }
    
    private <T> T loadFromSource(String key, Class<T> type) {
        // Implement your loading logic
        return null;
    }
    
    private void persistToDatabase(String key, Object value) {
        // Implement persistence logic
    }
    
    private void onCacheEviction(String key, Object value, RemovalCause cause) {
        // Handle cache eviction
        getLogger().info("Cache entry evicted: " + key + " (" + cause + ")");
    }
}
```

### Security

#### Q: How do I secure my configurations?

**A:**
1. **Use authentication:**
   ```yaml
   mongodb:
     uri: "mongodb://username:password@host:port/database"
   ```

2. **Implement access control:**
   ```java
   public boolean hasConfigAccess(Player player, String configKey) {
       // Check permissions
       if (!player.hasPermission("myplugin.config.access")) {
           return false;
       }
       
       // Check ownership
       if (configKey.startsWith("player_" + player.getUniqueId())) {
           return true;
       }
       
       // Check admin access
       return player.hasPermission("myplugin.admin");
   }
   ```

3. **Validate input:**
   ```java
   public void saveSecureConfig(Object config) {
       // Validate config
       ValidationUtils.validate(config);
       
       // Sanitize data
       DataSanitizer.sanitize(config);
       
       // Save
       configManager.save(config);
   }
   ```

#### Q: How do I handle sensitive data?

**A:**
```java
@ConfigsFileProperties
public class SecureConfig {
    
    // Regular fields
    public String serverName;
    
    // Encrypted fields
    @Encrypted
    public String apiKey;
    
    @Encrypted
    public DatabaseCredentials databaseCreds;
    
    public static class DatabaseCredentials {
        public String username;
        public String password;
        public String host;
    }
}
```

### Migration

#### Q: How do I migrate from YAML files?

**A:** Use the migration utilities:

```java
public class YamlMigration {
    
    public void migrateFromYaml() {
        File yamlFile = new File("config.yml");
        YamlToMongoMigration migration = new YamlToMongoMigration(configManager);
        
        // Migrate single file
        migration.migrateYamlFile(yamlFile, "main_config");
        
        // Or migrate entire directory
        File configDir = new File("configs");
        migration.migrateYamlDirectory(configDir);
    }
}
```

#### Q: How do I migrate from a SQL database?

**A:**
```java
public class SqlMigration {
    
    public void migrateFromSql() {
        // Set up SQL data source
        HikariDataSource sqlDataSource = new HikariDataSource();
        sqlDataSource.setJdbcUrl("jdbc:mysql://localhost:3306/myplugin");
        sqlDataSource.setUsername("user");
        sqlDataSource.setPassword("password");
        
        SqlToMongoMigration migration = new SqlToMongoMigration(configManager, sqlDataSource);
        
        // Migrate table
        migration.migrateSqlTable("plugin_configs", "migrated_config");
        
        sqlDataSource.close();
    }
}
```

### Common Errors

#### Q: "Class not found" errors

**A:** Ensure all dependencies are included:

```xml
<dependencies>
    <dependency>
        <groupId>xyz.wtje.mongoconfigs</groupId>
        <artifactId>mongo-configs-api</artifactId>
        <version>1.0.0</version>
    </dependency>
    <dependency>
        <groupId>org.mongodb</groupId>
        <artifactId>mongodb-driver-sync</artifactId>
        <version>4.8.0</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.14.0</version>
    </dependency>
</dependencies>
```

#### Q: "Connection refused" errors

**A:** Check MongoDB connection:

```java
public void diagnoseConnection() {
    try {
        MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
        MongoDatabase database = mongoClient.getDatabase("test");
        
        // Test connection
        database.runCommand(new Document("ping", 1));
        
        getLogger().info("MongoDB connection successful");
        mongoClient.close();
        
    } catch (Exception e) {
        getLogger().severe("MongoDB connection failed: " + e.getMessage());
    }
}
```

#### Q: "Codec not found" errors

**A:** Register custom codecs:

```java
public class CustomCodecProvider implements CodecProvider {
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        if (clazz == MyCustomClass.class) {
            return (Codec<T>) new MyCustomCodec(registry);
        }
        return null;
    }
}

// Register in MongoDB settings
CodecRegistry codecRegistry = CodecRegistries.fromRegistries(
    CodecRegistries.fromProviders(new CustomCodecProvider()),
    MongoClientSettings.getDefaultCodecRegistry()
);
```

### Best Practices

#### Q: What are the recommended patterns?

**A:**
1. **Use dependency injection:**
   ```java
   public class MyPlugin extends JavaPlugin {
       
       private ConfigManager configManager;
       private Messages messages;
       
       @Override
       public void onEnable() {
           // Initialize services
           this.configManager = MongoConfigsPlugin.getInstance().getConfigManager();
           this.messages = new MongoMessages(this);
           
           // Register commands
           getCommand("mycommand").setExecutor(new MyCommand(this));
       }
       
       public ConfigManager getConfigManager() {
           return configManager;
       }
       
       public Messages getMessages() {
           return messages;
       }
   }
   ```

2. **Handle errors gracefully:**
   ```java
   public <T> T getConfigSafe(Class<T> configClass, String key) {
       try {
           T config = configManager.get(configClass, key);
           return config != null ? config : createDefaultConfig(configClass);
       } catch (Exception e) {
           getLogger().warning("Failed to load config '" + key + "': " + e.getMessage());
           return createDefaultConfig(configClass);
       }
   }
   ```

3. **Use async operations for performance:**
   ```java
   public CompletableFuture<Void> saveConfigAsync(Object config) {
       return configManager.saveAsync(config)
           .thenAccept(saved -> {
               getLogger().info("Config saved successfully");
           })
           .exceptionally(throwable -> {
               getLogger().severe("Failed to save config: " + throwable.getMessage());
               return null;
           });
   }
   ```

### Support

#### Q: Where can I get help?

**A:**
- **Documentation:** Check the complete wiki documentation
- **GitHub Issues:** Report bugs and request features
- **Discord:** Join our community Discord server
- **Javadocs:** Comprehensive API documentation

#### Q: How do I report a bug?

**A:** When reporting bugs, please include:

1. **Plugin version** and **MongoDB Configs API version**
2. **Minecraft version** and **server software** (Paper/Spigot)
3. **Java version** (`java -version`)
4. **Full error message** and **stack trace**
5. **Steps to reproduce** the issue
6. **Expected behavior** vs **actual behavior**
7. **Configuration files** (with sensitive data removed)

#### Q: How do I request a feature?

**A:** Feature requests should include:

1. **Use case:** What problem are you trying to solve?
2. **Proposed solution:** How should it work?
3. **Alternatives:** Have you considered other approaches?
4. **Impact:** How will this affect existing users?

---

*This concludes the Help & FAQ section. For more detailed information, please refer to the specific documentation sections linked throughout this guide.*