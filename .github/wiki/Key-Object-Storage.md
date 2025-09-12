# Key-Object Storage

Complete guide to using key-object storage approach in MongoDB Configs API.

## 🎯 Overview

Key-object storage provides a simple key-value approach for storing configuration data. This approach is ideal for:

- **Simple Data** - Basic configuration values
- **Dynamic Keys** - Keys determined at runtime
- **Migration** - Easy migration from existing systems
- **Performance** - Fast access for frequently changing data

## 📝 Basic Key-Object Operations

### Storing Values

```java
// Get ConfigManager instance
ConfigManager cm = MongoConfigsAPI.getConfigManager();

// Store primitive values
cm.set("server.name", "My Server");
cm.set("server.max_players", 100);
cm.set("server.pvp_enabled", true);
cm.set("server.difficulty", "NORMAL");

// Store complex objects
List<String> bannedItems = Arrays.asList("bedrock", "barrier", "tnt");
cm.set("server.banned_items", bannedItems);

Map<String, Double> prices = Map.of(
    "diamond_sword", 100.0,
    "diamond_pickaxe", 150.0,
    "diamond_armor", 500.0
);
cm.set("shop.prices", prices);

// Store custom objects
PlayerStats stats = new PlayerStats("Steve", 1000, 50);
cm.set("player." + playerId + ".stats", stats);
```

### Retrieving Values

```java
// Retrieve primitive values
String serverName = cm.get("server.name", String.class);
Integer maxPlayers = cm.get("server.max_players", Integer.class);
Boolean pvpEnabled = cm.get("server.pvp_enabled", Boolean.class);

// Retrieve with default values
String serverName = cm.getOrDefault("server.name", String.class, "Default Server");
Integer maxPlayers = cm.getOrDefault("server.max_players", Integer.class, 100);

// Retrieve complex objects
@SuppressWarnings("unchecked")
List<String> bannedItems = cm.get("server.banned_items", List.class);

@SuppressWarnings("unchecked")
Map<String, Double> prices = cm.get("shop.prices", Map.class);

// Retrieve custom objects
PlayerStats stats = cm.get("player." + playerId + ".stats", PlayerStats.class);
```

### Async Operations

```java
// Async storage
CompletableFuture<Void> storeFuture = cm.setAsync("player." + playerId + ".last_login", System.currentTimeMillis());
storeFuture.thenRun(() -> {
    getLogger().info("Player login time saved!");
});

// Async retrieval
CompletableFuture<PlayerStats> statsFuture = cm.getAsync("player." + playerId + ".stats", PlayerStats.class);
statsFuture.thenAccept(stats -> {
    if (stats != null) {
        getLogger().info("Player level: " + stats.getLevel());
    }
});
```

## 🔧 Advanced Key-Object Patterns

### Hierarchical Keys

```java
public class HierarchicalConfigManager {

    private final ConfigManager cm;
    private final String baseKey;

    public HierarchicalConfigManager(String baseKey) {
        this.cm = MongoConfigsAPI.getConfigManager();
        this.baseKey = baseKey;
    }

    // Store values with hierarchical keys
    public void setServerSetting(String setting, Object value) {
        cm.set(baseKey + ".server." + setting, value);
    }

    public void setPlayerSetting(String playerId, String setting, Object value) {
        cm.set(baseKey + ".players." + playerId + "." + setting, value);
    }

    public void setWorldSetting(String worldName, String setting, Object value) {
        cm.set(baseKey + ".worlds." + worldName + "." + setting, value);
    }

    // Retrieve values with hierarchical keys
    public <T> T getServerSetting(String setting, Class<T> type) {
        return cm.get(baseKey + ".server." + setting, type);
    }

    public <T> T getPlayerSetting(String playerId, String setting, Class<T> type) {
        return cm.get(baseKey + ".players." + playerId + "." + setting, type);
    }

    public <T> T getWorldSetting(String worldName, String setting, Class<T> type) {
        return cm.get(baseKey + ".worlds." + worldName + "." + setting, type);
    }
}

// Usage
HierarchicalConfigManager config = new HierarchicalConfigManager("myplugin");

// Store hierarchical data
config.setServerSetting("max_players", 100);
config.setPlayerSetting(playerId, "balance", 1000.0);
config.setWorldSetting("world", "pvp_enabled", true);

// Retrieve hierarchical data
Integer maxPlayers = config.getServerSetting("max_players", Integer.class);
Double balance = config.getPlayerSetting(playerId, "balance", Double.class);
Boolean pvpEnabled = config.getWorldSetting("world", "pvp_enabled", Boolean.class);
```

### Dynamic Key Generation

```java
public class DynamicKeyManager {

    private final ConfigManager cm;

    public DynamicKeyManager() {
        this.cm = MongoConfigsAPI.getConfigManager();
    }

    // Generate keys based on time
    public void storeDailyStats(String playerId, int kills, int deaths) {
        String today = LocalDate.now().toString();
        String key = "stats." + playerId + "." + today;

        Map<String, Integer> dailyStats = Map.of(
            "kills", kills,
            "deaths", deaths,
            "kd_ratio", deaths > 0 ? kills / deaths : kills
        );

        cm.set(key, dailyStats);
    }

    // Generate keys based on location
    public void storeLocationData(String playerId, Location location) {
        String world = location.getWorld().getName();
        String key = "locations." + playerId + "." + world;

        Map<String, Object> locationData = Map.of(
            "x", location.getX(),
            "y", location.getY(),
            "z", location.getZ(),
            "yaw", location.getYaw(),
            "pitch", location.getPitch(),
            "timestamp", System.currentTimeMillis()
        );

        cm.set(key, locationData);
    }

    // Generate keys based on session
    public void startPlayerSession(String playerId) {
        String sessionId = UUID.randomUUID().toString();
        String key = "sessions." + playerId + ".active";

        Map<String, Object> sessionData = Map.of(
            "session_id", sessionId,
            "start_time", System.currentTimeMillis(),
            "world", Bukkit.getPlayer(UUID.fromString(playerId)).getWorld().getName()
        );

        cm.set(key, sessionData);
    }

    public void endPlayerSession(String playerId) {
        String activeKey = "sessions." + playerId + ".active";

        @SuppressWarnings("unchecked")
        Map<String, Object> sessionData = cm.get(activeKey, Map.class);

        if (sessionData != null) {
            // Move to completed sessions
            String sessionId = (String) sessionData.get("session_id");
            String completedKey = "sessions." + playerId + ".completed." + sessionId;

            sessionData.put("end_time", System.currentTimeMillis());
            sessionData.put("duration", System.currentTimeMillis() - (Long) sessionData.get("start_time"));

            cm.set(completedKey, sessionData);
            cm.set(activeKey, null); // Remove active session
        }
    }
}
```

### Batch Operations

```java
public class BatchConfigManager {

    private final ConfigManager cm;

    public BatchConfigManager() {
        this.cm = MongoConfigsAPI.getConfigManager();
    }

    // Store multiple values in batch
    public void storePlayerDataBatch(String playerId, Map<String, Object> data) {
        Map<String, CompletableFuture<Void>> futures = new HashMap<>();

        data.forEach((key, value) -> {
            String fullKey = "players." + playerId + "." + key;
            futures.put(key, cm.setAsync(fullKey, value));
        });

        // Wait for all operations to complete
        CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                getLogger().info("Batch data stored for player: " + playerId);
            });
    }

    // Retrieve multiple values in batch
    public Map<String, Object> getPlayerDataBatch(String playerId, Set<String> keys) {
        Map<String, Object> results = new HashMap<>();
        Map<String, CompletableFuture<Object>> futures = new HashMap<>();

        keys.forEach(key -> {
            String fullKey = "players." + playerId + "." + key;
            futures.put(key, cm.getAsync(fullKey, Object.class));
        });

        // Wait for all retrievals to complete
        try {
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).get();

            futures.forEach((key, future) -> {
                try {
                    Object value = future.get();
                    if (value != null) {
                        results.put(key, value);
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to retrieve " + key + ": " + e.getMessage());
                }
            });

        } catch (Exception e) {
            getLogger().severe("Batch retrieval failed: " + e.getMessage());
        }

        return results;
    }

    // Bulk operations for multiple players
    public void updateAllPlayerBalances(double multiplier) {
        // This would require querying all player keys
        // Implementation depends on your key structure

        getLogger().info("Updating all player balances with multiplier: " + multiplier);
        // Implementation would iterate through all player balance keys
    }
}
```

## 📊 Use Cases & Patterns

### Player Data Management

```java
public class PlayerDataManager {

    private final ConfigManager cm;

    public PlayerDataManager() {
        this.cm = MongoConfigsAPI.getConfigManager();
    }

    // Store comprehensive player data
    public void savePlayerData(Player player) {
        String playerId = player.getUniqueId().toString();

        // Basic info
        cm.set("players." + playerId + ".name", player.getName());
        cm.set("players." + playerId + ".uuid", playerId);
        cm.set("players." + playerId + ".first_join", player.getFirstPlayed());
        cm.set("players." + playerId + ".last_seen", System.currentTimeMillis());

        // Location data
        Location loc = player.getLocation();
        Map<String, Object> location = Map.of(
            "world", loc.getWorld().getName(),
            "x", loc.getX(),
            "y", loc.getY(),
            "z", loc.getZ(),
            "yaw", loc.getYaw(),
            "pitch", loc.getPitch()
        );
        cm.set("players." + playerId + ".location", location);

        // Game stats
        cm.set("players." + playerId + ".health", player.getHealth());
        cm.set("players." + playerId + ".food", player.getFoodLevel());
        cm.set("players." + playerId + ".level", player.getLevel());
        cm.set("players." + playerId + ".exp", player.getExp());
    }

    // Load player data
    public void loadPlayerData(Player player) {
        String playerId = player.getUniqueId().toString();

        // Load location
        @SuppressWarnings("unchecked")
        Map<String, Object> location = cm.get("players." + playerId + ".location", Map.class);
        if (location != null) {
            World world = Bukkit.getWorld((String) location.get("world"));
            if (world != null) {
                Location loc = new Location(
                    world,
                    (Double) location.get("x"),
                    (Double) location.get("y"),
                    (Double) location.get("z"),
                    ((Double) location.get("yaw")).floatValue(),
                    ((Double) location.get("pitch")).floatValue()
                );
                player.teleport(loc);
            }
        }

        // Load game stats
        Double health = cm.get("players." + playerId + ".health", Double.class);
        if (health != null) {
            player.setHealth(health);
        }

        Integer food = cm.get("players." + playerId + ".food", Integer.class);
        if (food != null) {
            player.setFoodLevel(food);
        }

        Integer level = cm.get("players." + playerId + ".level", Integer.class);
        if (level != null) {
            player.setLevel(level);
        }

        Float exp = cm.get("players." + playerId + ".exp", Float.class);
        if (exp != null) {
            player.setExp(exp);
        }
    }

    // Update specific stats
    public void updatePlayerStat(String playerId, String stat, Object value) {
        cm.set("players." + playerId + ".stats." + stat, value);
    }

    public <T> T getPlayerStat(String playerId, String stat, Class<T> type) {
        return cm.get("players." + playerId + ".stats." + stat, type);
    }
}
```

### Economy System

```java
public class EconomyManager {

    private final ConfigManager cm;

    public EconomyManager() {
        this.cm = MongoConfigsAPI.getConfigManager();
    }

    // Account management
    public void createAccount(String playerId, String playerName) {
        cm.set("economy." + playerId + ".name", playerName);
        cm.set("economy." + playerId + ".balance", 1000.0);
        cm.set("economy." + playerId + ".created", System.currentTimeMillis());
        cm.set("economy." + playerId + ".transactions", new ArrayList<Map<String, Object>>());
    }

    public double getBalance(String playerId) {
        Double balance = cm.get("economy." + playerId + ".balance", Double.class);
        return balance != null ? balance : 0.0;
    }

    // Transaction handling
    public boolean transfer(String fromId, String toId, double amount, String reason) {
        double fromBalance = getBalance(fromId);
        double toBalance = getBalance(toId);

        if (fromBalance < amount) {
            return false; // Insufficient funds
        }

        // Update balances
        cm.set("economy." + fromId + ".balance", fromBalance - amount);
        cm.set("economy." + toId + ".balance", toBalance + amount);

        // Record transactions
        recordTransaction(fromId, "transfer_out", -amount, "Transfer to " + toId + ": " + reason);
        recordTransaction(toId, "transfer_in", amount, "Transfer from " + fromId + ": " + reason);

        return true;
    }

    private void recordTransaction(String playerId, String type, double amount, String description) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> transactions = cm.get("economy." + playerId + ".transactions", List.class);
        if (transactions == null) {
            transactions = new ArrayList<>();
        }

        Map<String, Object> transaction = Map.of(
            "type", type,
            "amount", amount,
            "description", description,
            "timestamp", System.currentTimeMillis()
        );

        transactions.add(transaction);

        // Keep only last 100 transactions
        if (transactions.size() > 100) {
            transactions = transactions.subList(transactions.size() - 100, transactions.size());
        }

        cm.set("economy." + playerId + ".transactions", transactions);
    }

    // Shop integration
    public boolean purchaseItem(String playerId, String itemId, double price) {
        double balance = getBalance(playerId);

        if (balance < price) {
            return false;
        }

        cm.set("economy." + playerId + ".balance", balance - price);
        recordTransaction(playerId, "purchase", -price, "Purchased " + itemId);

        return true;
    }

    // Bank operations
    public void deposit(String playerId, double amount) {
        double balance = getBalance(playerId);
        cm.set("economy." + playerId + ".balance", balance + amount);
        recordTransaction(playerId, "deposit", amount, "Bank deposit");
    }

    public boolean withdraw(String playerId, double amount) {
        double balance = getBalance(playerId);

        if (balance < amount) {
            return false;
        }

        cm.set("economy." + playerId + ".balance", balance - amount);
        recordTransaction(playerId, "withdraw", -amount, "Bank withdrawal");

        return true;
    }
}
```

### World Management

```java
public class WorldManager {

    private final ConfigManager cm;

    public WorldManager() {
        this.cm = MongoConfigsAPI.getConfigManager();
    }

    // World settings
    public void saveWorldSettings(String worldName, Map<String, Object> settings) {
        cm.set("worlds." + worldName + ".settings", settings);
    }

    public Map<String, Object> getWorldSettings(String worldName) {
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = cm.get("worlds." + worldName + ".settings", Map.class);
        return settings != null ? settings : new HashMap<>();
    }

    // Player world data
    public void savePlayerWorldData(String playerId, String worldName, Map<String, Object> data) {
        cm.set("players." + playerId + ".worlds." + worldName, data);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getPlayerWorldData(String playerId, String worldName) {
        Map<String, Object> data = cm.get("players." + playerId + ".worlds." + worldName, Map.class);
        return data != null ? data : new HashMap<>();
    }

    // World-specific player stats
    public void updatePlayerWorldStat(String playerId, String worldName, String stat, Object value) {
        cm.set("players." + playerId + ".worlds." + worldName + ".stats." + stat, value);
    }

    public <T> T getPlayerWorldStat(String playerId, String worldName, String stat, Class<T> type) {
        return cm.get("players." + playerId + ".worlds." + worldName + ".stats." + stat, type);
    }

    // Cross-world data
    public void migratePlayerData(String playerId, String fromWorld, String toWorld) {
        Map<String, Object> fromData = getPlayerWorldData(playerId, fromWorld);
        if (!fromData.isEmpty()) {
            savePlayerWorldData(playerId, toWorld, fromData);
            // Optionally clear old data
            cm.set("players." + playerId + ".worlds." + fromWorld, null);
        }
    }

    // World statistics
    public void recordWorldEvent(String worldName, String eventType, Map<String, Object> eventData) {
        String key = "worlds." + worldName + ".events." + System.currentTimeMillis();
        cm.set(key, eventData);
    }

    public List<Map<String, Object>> getWorldEvents(String worldName, long sinceTimestamp) {
        // This would require a more complex query
        // Implementation depends on your key structure
        return new ArrayList<>();
    }
}
```

## 🔍 Querying and Searching

### Key Pattern Matching

```java
public class KeyPatternManager {

    private final ConfigManager cm;

    public KeyPatternManager() {
        this.cm = MongoConfigsAPI.getConfigManager();
    }

    // Find all keys matching a pattern
    public Set<String> findKeys(String pattern) {
        // This would require additional API methods for pattern matching
        // For now, we'll use a simulated approach
        Set<String> matchingKeys = new HashSet<>();

        // Example: Find all player keys
        if (pattern.equals("players.*")) {
            // Would need to implement key listing functionality
            matchingKeys.add("players.player1");
            matchingKeys.add("players.player2");
        }

        return matchingKeys;
    }

    // Search by value
    public List<String> searchByValue(String searchValue) {
        // This would require full-text search capabilities
        // Implementation depends on MongoDB search features
        return new ArrayList<>();
    }

    // Range queries
    public List<String> findInRange(String keyPrefix, Object minValue, Object maxValue) {
        // Implementation for range queries
        return new ArrayList<>();
    }
}
```

## 🎯 Best Practices

### 1. Key Naming Conventions

```java
// ✅ Good - Consistent naming
cm.set("players." + playerId + ".balance", 1000.0);
cm.set("players." + playerId + ".stats.kills", 50);
cm.set("players." + playerId + ".settings.language", "en");

// ✅ Good - Hierarchical structure
cm.set("server.settings.max_players", 100);
cm.set("server.settings.difficulty", "NORMAL");
cm.set("server.worlds.world.settings.pvp", true);

// ❌ Avoid - Inconsistent naming
cm.set("player_" + playerId + "_balance", 1000.0);
cm.set("playerStatsKills", 50);
cm.set("lang", "en");
```

### 2. Data Types

```java
// ✅ Good - Use appropriate types
cm.set("player.level", 50);                    // Integer
cm.set("player.balance", 1000.0);              // Double
cm.set("player.name", "Steve");                // String
cm.set("player.is_online", true);              // Boolean
cm.set("player.inventory", itemList);          // List
cm.set("player.stats", statsMap);              // Map

// ❌ Avoid - Type confusion
cm.set("player.level", "50");                  // String instead of Integer
cm.set("player.balance", 1000);                // Integer instead of Double
```

### 3. Key Length and Complexity

```java
// ✅ Good - Reasonable key lengths
cm.set("players." + playerId + ".balance", balance);
cm.set("server.settings.max_players", maxPlayers);

// ❌ Avoid - Very long keys
cm.set("this.is.a.very.long.key.that.makes.the.database.hard.to.manage.and.slow.to.query.players." + playerId + ".balance", balance);

// ❌ Avoid - Complex nested structures in keys
cm.set("players." + playerId + ".worlds." + worldName + ".regions." + regionId + ".chunks." + chunkX + "." + chunkZ + ".data", data);
```

### 4. Performance Considerations

```java
// ✅ Good - Batch operations for multiple updates
Map<String, Object> updates = new HashMap<>();
updates.put("balance", newBalance);
updates.put("last_transaction", System.currentTimeMillis());
updates.put("transaction_count", transactionCount + 1);

storePlayerDataBatch(playerId, updates);

// ✅ Good - Cache frequently accessed data
private final Map<String, Double> balanceCache = new ConcurrentHashMap<>();

public double getCachedBalance(String playerId) {
    return balanceCache.computeIfAbsent(playerId, this::getBalance);
}

public void updateBalance(String playerId, double newBalance) {
    balanceCache.put(playerId, newBalance);
    cm.set("players." + playerId + ".balance", newBalance);
}
```

---

*Next: Learn about [[Class-Based Configuration]] for type-safe configuration management.*