# Change Streams Tutorial

> **Real-time multi-server synchronization with MongoDB Change Streams**

## Table of Contents
- [What are Change Streams?](#what-are-change-streams)
- [Why Use Change Streams?](#why-use-change-streams)
- [Basic Setup](#basic-setup)
- [Configuration Classes](#configuration-classes)
- [Implementing Change Stream Listeners](#implementing-change-stream-listeners)
- [Multi-Server Synchronization](#multi-server-synchronization)
- [Error Handling](#error-handling)
- [Performance Optimization](#performance-optimization)
- [Complete Examples](#complete-examples)
- [Troubleshooting](#troubleshooting)

---

## What are Change Streams?

MongoDB Change Streams provide a way to listen for changes to documents in a collection in real-time. When a document is inserted, updated, or deleted, MongoDB can notify your application immediately.

### Key Benefits
- **Real-time Updates** - Instant notification of data changes
- **Multi-Server Sync** - Automatic synchronization across server instances
- **Event-Driven Architecture** - React to changes as they happen
- **Low Latency** - Sub-millisecond notification delivery
- **Reliable** - Guaranteed delivery with resumable streams

---

## Why Use Change Streams?

### Traditional Approach Problems
```java
// ❌ Polling approach - inefficient and slow
public class PollingSync {
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public void startPolling() {
        scheduler.scheduleAtFixedRate(() -> {
            // Check for changes every 30 seconds
            checkForConfigChanges();
            checkForPlayerDataChanges();
        }, 0, 30, TimeUnit.SECONDS);
    }
    
    private void checkForConfigChanges() {
        // Expensive database queries
        // Manual comparison logic
        // Potential race conditions
    }
}
```

### Change Streams Solution
```java
// ✅ Real-time approach - efficient and instant
public class RealTimeSync {
    
    public void setupChangeStreams() {
        // Listen for config changes
        watchCollection("configs", this::handleConfigChange);
        
        // Listen for player data changes
        watchCollection("player-data", this::handlePlayerDataChange);
    }
    
    private void handleConfigChange(Document change) {
        // Instant reaction to config changes
        String configId = change.getString("_id");
        reloadConfig(configId);
        broadcastToAllServers(configId);
    }
}
```

---

## Basic Setup

### MongoDB Requirements

Change Streams require:
- **MongoDB 3.6+** (basic support)
- **MongoDB 4.0+** (recommended for production)
- **Replica Set** (required for Change Streams)
- **oplog** access

### Configuration

```yaml
# MongoDB connection with replica set
mongodb:
  connection-string: "mongodb://localhost:27017/?replicaSet=rs0"
  database: "minecraft"
  
# Change Streams configuration
change-streams:
  enabled: true
  max-await-time: 1000  # milliseconds
  batch-size: 100
  full-document: "updateLookup"  # Include full document on updates
```

### Replica Set Setup

```bash
# Initialize replica set
mongo --eval "rs.initiate()"

# Add members (if using multiple MongoDB instances)
mongo --eval "rs.add('mongodb2:27017')"
mongo --eval "rs.add('mongodb3:27017')"

# Check status
mongo --eval "rs.status()"
```

---

## Configuration Classes

### Change Stream Aware Configuration

```java
@ConfigsFileProperties(name = "server-settings")
@ConfigsDatabase("minecraft")
public class ServerConfig extends MongoConfig<ServerConfig> {
    
    private String serverName = "MyServer";
    private int maxPlayers = 100;
    private boolean maintenanceMode = false;
    private Map<String, String> serverRules = new HashMap<>();
    
    // Getters and setters...
    
    @Override
    public void save() {
        super.save();
        // Notify other servers about the change
        notifyServersOfChange();
    }
    
    private void notifyServersOfChange() {
        // This will be handled by Change Streams automatically
        // No manual notification needed!
    }
}

// Player data with real-time sync
@ConfigsFileProperties(name = "player-data")
@ConfigsDatabase("minecraft")
public class PlayerData extends MongoConfig<PlayerData> {
    
    private String playerId;
    private String playerName;
    private int level = 1;
    private double experience = 0.0;
    private Map<String, Object> statistics = new HashMap<>();
    private long lastSeen = System.currentTimeMillis();
    
    public PlayerData() {}
    
    public PlayerData(String playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }
    
    // Getters and setters...
}
```

---

## Implementing Change Stream Listeners

### Basic Change Stream Listener

```java
public class ConfigChangeListener {
    
    private final MongoManager mongoManager;
    private final ConfigManager configManager;
    private ChangeStreamIterable<Document> changeStream;
    
    public ConfigChangeListener(MongoManager mongoManager, ConfigManager configManager) {
        this.mongoManager = mongoManager;
        this.configManager = configManager;
    }
    
    public void startListening() {
        MongoCollection<Document> collection = mongoManager.getCollection("configs");
        
        // Create change stream
        changeStream = collection.watch();
        
        // Process changes asynchronously
        changeStream.forEach(this::processChange, this::handleError);
    }
    
    private void processChange(ChangeStreamDocument<Document> change) {
        try {
            String operationType = change.getOperationType().getValue();
            Document documentKey = change.getDocumentKey();
            String configId = documentKey.getString("_id").getValue();
            
            switch (operationType) {
                case "insert" -> handleInsert(configId, change.getFullDocument());
                case "update" -> handleUpdate(configId, change.getUpdateDescription());
                case "delete" -> handleDelete(configId);
                case "replace" -> handleReplace(configId, change.getFullDocument());
            }
            
        } catch (Exception e) {
            getLogger().error("Error processing change stream event", e);
        }
    }
    
    private void handleInsert(String configId, Document fullDocument) {
        getLogger().info("Config inserted: " + configId);
        
        // Invalidate local cache
        configManager.invalidateCache(configId);
        
        // Notify other components
        notifyConfigChange(configId, "INSERT");
    }
    
    private void handleUpdate(String configId, UpdateDescription updateDescription) {
        getLogger().info("Config updated: " + configId);
        
        // Get updated fields
        Document updatedFields = updateDescription.getUpdatedFields();
        
        // Invalidate cache
        configManager.invalidateCache(configId);
        
        // Notify with specific changes
        notifyConfigChange(configId, "UPDATE", updatedFields);
    }
    
    private void handleDelete(String configId) {
        getLogger().info("Config deleted: " + configId);
        
        // Clear from cache
        configManager.invalidateCache(configId);
        
        // Notify deletion
        notifyConfigChange(configId, "DELETE");
    }
    
    private void handleReplace(String configId, Document fullDocument) {
        getLogger().info("Config replaced: " + configId);
        
        // Full replacement - reload everything
        configManager.invalidateCache(configId);
        
        // Force reload on next access
        notifyConfigChange(configId, "REPLACE");
    }
    
    private void handleError(Throwable error) {
        getLogger().error("Change stream error", error);
        
        // Implement retry logic
        restartChangeStream();
    }
    
    private void restartChangeStream() {
        try {
            Thread.sleep(5000); // Wait before restart
            startListening();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void notifyConfigChange(String configId, String operationType) {
        notifyConfigChange(configId, operationType, null);
    }
    
    private void notifyConfigChange(String configId, String operationType, Document changes) {
        // Notify other parts of the system
        Bukkit.getPluginManager().callEvent(new ConfigChangeEvent(configId, operationType, changes));
    }
    
    private Logger getLogger() {
        return null; // Return your logger
    }
}
```

### Advanced Change Stream with Filtering

```java
public class FilteredChangeListener {
    
    public void startFilteredListening() {
        MongoCollection<Document> collection = mongoManager.getCollection("configs");
        
        // Create pipeline for filtering
        List<Bson> pipeline = Arrays.asList(
            // Only watch for server config changes
            match(eq("documentKey._id", "server-settings")),
            
            // Only watch for specific field updates
            match(exists("updateDescription.updatedFields.maintenanceMode")),
            
            // Add additional filtering as needed
            match(gt("clusterTime", new BsonTimestamp(System.currentTimeMillis() / 1000, 0)))
        );
        
        // Create filtered change stream
        ChangeStreamIterable<Document> changeStream = collection.watch(pipeline);
        
        // Process filtered changes
        changeStream.forEach(this::processFilteredChange);
    }
    
    private void processFilteredChange(ChangeStreamDocument<Document> change) {
        // Only maintenance mode changes will reach here
        boolean maintenanceMode = change.getFullDocument()
            .getBoolean("maintenanceMode");
        
        if (maintenanceMode) {
            // Server entering maintenance mode
            broadcastMaintenanceMode(true);
        } else {
            // Server exiting maintenance mode
            broadcastMaintenanceMode(false);
        }
    }
    
    private void broadcastMaintenanceMode(boolean enabled) {
        // Broadcast to all connected players
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (enabled) {
                player.kickPlayer("Server is entering maintenance mode");
            } else {
                player.sendMessage("Server maintenance mode disabled");
            }
        }
    }
}
```

---

## Multi-Server Synchronization

### Server Communication System

```java
public class ServerSyncManager {
    
    private final String serverId;
    private final MongoManager mongoManager;
    private final ConfigManager configManager;
    private final Map<String, ServerInstance> connectedServers = new ConcurrentHashMap<>();
    
    public ServerSyncManager(String serverId, MongoManager mongoManager, ConfigManager configManager) {
        this.serverId = serverId;
        this.mongoManager = mongoManager;
        this.configManager = configManager;
    }
    
    public void initialize() {
        // Register this server
        registerServer();
        
        // Start listening for other servers
        watchServerRegistrations();
        
        // Start change stream for cross-server sync
        startCrossServerSync();
    }
    
    private void registerServer() {
        Document serverDoc = new Document()
            .append("_id", serverId)
            .append("host", getServerHost())
            .append("port", getServerPort())
            .append("onlinePlayers", Bukkit.getOnlinePlayers().size())
            .append("lastHeartbeat", System.currentTimeMillis())
            .append("status", "online");
        
        mongoManager.getCollection("servers")
            .replaceOne(eq("_id", serverId), serverDoc, new ReplaceOptions().upsert(true));
    }
    
    private void watchServerRegistrations() {
        MongoCollection<Document> serversCollection = mongoManager.getCollection("servers");
        
        serversCollection.watch().forEach(change -> {
            if ("insert".equals(change.getOperationType().getValue()) || 
                "update".equals(change.getOperationType().getValue())) {
                
                String otherServerId = change.getDocumentKey().getString("_id").getValue();
                if (!otherServerId.equals(serverId)) {
                    updateServerList(otherServerId, change.getFullDocument());
                }
            }
        });
    }
    
    private void startCrossServerSync() {
        // Watch for config changes from other servers
        MongoCollection<Document> configsCollection = mongoManager.getCollection("configs");
        
        List<Bson> pipeline = Arrays.asList(
            // Only watch for changes not made by this server
            match(ne("ns.coll", "server-heartbeats")),
            match(ne("fullDocument.sourceServer", serverId))
        );
        
        configsCollection.watch(pipeline).forEach(this::handleCrossServerChange);
    }
    
    private void handleCrossServerChange(ChangeStreamDocument<Document> change) {
        String configId = change.getDocumentKey().getString("_id").getValue();
        String sourceServer = change.getFullDocument().getString("sourceServer");
        
        getLogger().info("Received config change from server " + sourceServer + ": " + configId);
        
        // Invalidate local cache
        configManager.invalidateCache(configId);
        
        // Notify local components
        notifyLocalComponents(configId, change);
    }
    
    private void updateServerList(String serverId, Document serverDoc) {
        ServerInstance server = new ServerInstance(
            serverId,
            serverDoc.getString("host"),
            serverDoc.getInteger("port"),
            serverDoc.getString("status")
        );
        
        connectedServers.put(serverId, server);
        
        getLogger().info("Server " + serverId + " is now " + server.getStatus());
        
        // Update server list for all players
        updatePlayerServerLists();
    }
    
    public void broadcastConfigChange(String configId, Object newValue) {
        // Add source server identifier
        Document updateDoc = new Document()
            .append("$set", new Document()
                .append("value", newValue)
                .append("sourceServer", serverId)
                .append("lastModified", System.currentTimeMillis())
            );
        
        mongoManager.getCollection("configs")
            .updateOne(eq("_id", configId), updateDoc);
    }
    
    public List<ServerInstance> getConnectedServers() {
        return new ArrayList<>(connectedServers.values());
    }
    
    // Heartbeat system
    public void sendHeartbeat() {
        Document heartbeat = new Document()
            .append("_id", serverId)
            .append("timestamp", System.currentTimeMillis())
            .append("onlinePlayers", Bukkit.getOnlinePlayers().size())
            .append("tps", getServerTPS());
        
        mongoManager.getCollection("server-heartbeats")
            .replaceOne(eq("_id", serverId), heartbeat, new ReplaceOptions().upsert(true));
    }
    
    private String getServerHost() {
        return "localhost"; // Get actual server host
    }
    
    private int getServerPort() {
        return Bukkit.getServer().getPort();
    }
    
    private double getServerTPS() {
        // Get server TPS from NMS or monitoring plugin
        return 20.0; // Placeholder
    }
    
    private void updatePlayerServerLists() {
        // Update server list for all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendServerList(player);
        }
    }
    
    private void sendServerList(Player player) {
        // Send server list to player (implementation depends on your system)
    }
    
    private void notifyLocalComponents(String configId, ChangeStreamDocument<Document> change) {
        // Notify local event listeners
        Bukkit.getPluginManager().callEvent(new CrossServerConfigChangeEvent(configId, change));
    }
    
    private Logger getLogger() {
        return null; // Return your logger
    }
}
```

### Server Instance Class

```java
public class ServerInstance {
    
    private final String serverId;
    private final String host;
    private final int port;
    private final String status;
    private final long lastSeen;
    
    public ServerInstance(String serverId, String host, int port, String status) {
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.status = status;
        this.lastSeen = System.currentTimeMillis();
    }
    
    // Getters...
    
    public boolean isOnline() {
        return "online".equals(status);
    }
    
    public long getSecondsSinceLastSeen() {
        return (System.currentTimeMillis() - lastSeen) / 1000;
    }
}
```

---

## Error Handling

### Robust Change Stream Implementation

```java
public class ResilientChangeStreamListener {
    
    private final MongoManager mongoManager;
    private ChangeStreamIterable<Document> changeStream;
    private volatile boolean running = false;
    private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(1);
    
    public void startReliableListening() {
        if (running) return;
        
        running = true;
        startChangeStream();
    }
    
    private void startChangeStream() {
        try {
            MongoCollection<Document> collection = mongoManager.getCollection("configs");
            
            // Configure change stream for reliability
            changeStream = collection.watch()
                .maxAwaitTime(1, TimeUnit.SECONDS)
                .batchSize(100)
                .fullDocument(FullDocument.UPDATE_LOOKUP);
            
            // Start processing
            changeStream.forEach(
                this::processChange,
                this::handleStreamError
            );
            
        } catch (Exception e) {
            getLogger().error("Failed to start change stream", e);
            scheduleRetry();
        }
    }
    
    private void processChange(ChangeStreamDocument<Document> change) {
        try {
            handleChange(change);
        } catch (Exception e) {
            getLogger().error("Error processing change", e);
            // Continue processing other changes
        }
    }
    
    private void handleStreamError(Throwable error) {
        getLogger().error("Change stream error", error);
        running = false;
        
        // Determine error type and handle accordingly
        if (isRecoverableError(error)) {
            scheduleRetry();
        } else {
            // Fatal error - require manual intervention
            alertAdministrators(error);
        }
    }
    
    private void scheduleRetry() {
        retryScheduler.schedule(() -> {
            if (!running) {
                getLogger().info("Retrying change stream connection...");
                startChangeStream();
            }
        }, 5, TimeUnit.SECONDS);
    }
    
    private boolean isRecoverableError(Throwable error) {
        String message = error.getMessage();
        return message.contains("connection") || 
               message.contains("timeout") || 
               message.contains("temporarily");
    }
    
    private void alertAdministrators(Throwable error) {
        // Send alert to administrators
        // Could use Discord webhook, email, etc.
        getLogger().severe("CRITICAL: Change stream failed with unrecoverable error: " + error.getMessage());
    }
    
    public void stop() {
        running = false;
        if (changeStream != null) {
            changeStream.close();
        }
        retryScheduler.shutdown();
    }
    
    private void handleChange(ChangeStreamDocument<Document> change) {
        // Process the change
        getLogger().info("Processing change: " + change.getOperationType());
    }
    
    private Logger getLogger() {
        return null; // Return your logger
    }
}
```

---

## Performance Optimization

### Change Stream Optimization

```java
public class OptimizedChangeStream {
    
    public void setupOptimizedChangeStream() {
        MongoCollection<Document> collection = mongoManager.getCollection("configs");
        
        // Optimized change stream configuration
        ChangeStreamIterable<Document> changeStream = collection.watch()
            // Reduce network overhead
            .batchSize(50)
            .maxAwaitTime(500, TimeUnit.MILLISECONDS)
            
            // Only get necessary data
            .fullDocument(FullDocument.DEFAULT)
            
            // Resume from last processed change
            .startAfter(getLastProcessedResumeToken());
        
        // Process in separate thread pool
        ExecutorService changeProcessor = Executors.newFixedThreadPool(4);
        
        changeStream.forEach(change -> {
            changeProcessor.submit(() -> processChangeAsync(change));
        }, this::handleError);
    }
    
    private void processChangeAsync(ChangeStreamDocument<Document> change) {
        // Process change asynchronously
        String configId = change.getDocumentKey().getString("_id").getValue();
        
        // Quick validation
        if (!isRelevantChange(change)) {
            return;
        }
        
        // Update cache
        configManager.invalidateCache(configId);
        
        // Notify subscribers
        notifyChangeSubscribers(configId, change);
        
        // Update resume token
        saveResumeToken(change.getResumeToken());
    }
    
    private boolean isRelevantChange(ChangeStreamDocument<Document> change) {
        // Filter out irrelevant changes
        Document fullDocument = change.getFullDocument();
        if (fullDocument == null) return false;
        
        // Only process changes from other servers
        String sourceServer = fullDocument.getString("sourceServer");
        return !getCurrentServerId().equals(sourceServer);
    }
    
    private void notifyChangeSubscribers(String configId, ChangeStreamDocument<Document> change) {
        // Notify all subscribers asynchronously
        CompletableFuture.runAsync(() -> {
            for (ChangeSubscriber subscriber : subscribers) {
                try {
                    subscriber.onConfigChange(configId, change);
                } catch (Exception e) {
                    getLogger().error("Subscriber error", e);
                }
            }
        });
    }
    
    private BsonDocument getLastProcessedResumeToken() {
        // Load from persistent storage
        String tokenString = loadResumeTokenFromStorage();
        return tokenString != null ? BsonDocument.parse(tokenString) : null;
    }
    
    private void saveResumeToken(BsonDocument resumeToken) {
        // Save to persistent storage
        saveResumeTokenToStorage(resumeToken.toJson());
    }
    
    private String getCurrentServerId() {
        return "server-1"; // Return actual server ID
    }
    
    private Logger getLogger() {
        return null; // Return logger
    }
    
    // Helper methods for resume token persistence
    private String loadResumeTokenFromStorage() {
        // Load from file/database
        return null;
    }
    
    private void saveResumeTokenToStorage(String token) {
        // Save to file/database
    }
}
```

### Subscriber Pattern

```java
public interface ChangeSubscriber {
    void onConfigChange(String configId, ChangeStreamDocument<Document> change);
}

public class ChangeSubscriberManager {
    
    private final List<ChangeSubscriber> subscribers = new CopyOnWriteArrayList<>();
    
    public void subscribe(ChangeSubscriber subscriber) {
        subscribers.add(subscriber);
    }
    
    public void unsubscribe(ChangeSubscriber subscriber) {
        subscribers.remove(subscriber);
    }
    
    public void notifyAll(String configId, ChangeStreamDocument<Document> change) {
        for (ChangeSubscriber subscriber : subscribers) {
            try {
                subscriber.onConfigChange(configId, change);
            } catch (Exception e) {
                getLogger().error("Error notifying subscriber", e);
            }
        }
    }
    
    private Logger getLogger() {
        return null; // Return logger
    }
}
```

---

## Complete Examples

### Global Ban System

```java
@ConfigsFileProperties(name = "global-bans")
@ConfigsDatabase("minecraft")
public class GlobalBanList extends MongoConfig<GlobalBanList> {
    
    private Map<String, BanInfo> bannedPlayers = new ConcurrentHashMap<>();
    private Set<String> bannedIPs = ConcurrentHashMap.newKeySet();
    
    public static class BanInfo {
        private String playerId;
        private String playerName;
        private String reason;
        private String bannedBy;
        private long banTime;
        private long expiryTime; // -1 for permanent
        private boolean active = true;
        
        // Constructors, getters, setters...
    }
    
    // Methods...
}

public class GlobalBanManager implements ChangeSubscriber {
    
    private final GlobalBanList banList;
    private final MongoManager mongoManager;
    
    public GlobalBanManager() {
        this.mongoManager = MongoConfigsAPI.getMongoManager();
        
        // Load ban list
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        this.banList = cm.loadObject(GlobalBanList.class);
        
        // Subscribe to changes
        ChangeSubscriberManager.subscribe(this);
    }
    
    @Override
    public void onConfigChange(String configId, ChangeStreamDocument<Document> change) {
        if (!"global-bans".equals(configId)) return;
        
        // Ban list changed - reload and update local bans
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        GlobalBanList updatedList = cm.loadObject(GlobalBanList.class);
        
        // Update local ban list
        updateLocalBans(updatedList);
        
        // Broadcast to all servers
        broadcastBanUpdate(change);
    }
    
    public void banPlayer(String playerId, String reason, String bannedBy, long duration) {
        BanInfo banInfo = new BanInfo();
        banInfo.setPlayerId(playerId);
        banInfo.setPlayerName(getPlayerName(playerId));
        banInfo.setReason(reason);
        banInfo.setBannedBy(bannedBy);
        banInfo.setBanTime(System.currentTimeMillis());
        banInfo.setExpiryTime(duration > 0 ? System.currentTimeMillis() + duration : -1);
        
        banList.getBannedPlayers().put(playerId, banInfo);
        
        // Save to trigger change stream
        banList.save();
        
        // Kick player if online
        kickPlayerIfOnline(playerId, reason);
    }
    
    public void unbanPlayer(String playerId) {
        BanInfo banInfo = banList.getBannedPlayers().get(playerId);
        if (banInfo != null) {
            banInfo.setActive(false);
            banList.save();
        }
    }
    
    public boolean isPlayerBanned(String playerId) {
        BanInfo banInfo = banList.getBannedPlayers().get(playerId);
        if (banInfo == null || !banInfo.isActive()) return false;
        
        // Check if ban expired
        if (banInfo.getExpiryTime() > 0 && 
            System.currentTimeMillis() > banInfo.getExpiryTime()) {
            // Ban expired - remove it
            banInfo.setActive(false);
            banList.save();
            return false;
        }
        
        return true;
    }
    
    private void updateLocalBans(GlobalBanList updatedList) {
        // Update local ban system with new ban list
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerId = player.getUniqueId().toString();
            
            if (updatedList.getBannedPlayers().containsKey(playerId)) {
                BanInfo banInfo = updatedList.getBannedPlayers().get(playerId);
                if (banInfo.isActive()) {
                    player.kickPlayer("You are globally banned: " + banInfo.getReason());
                }
            }
        }
    }
    
    private void broadcastBanUpdate(ChangeStreamDocument<Document> change) {
        // Broadcast ban update to all connected servers
        String operationType = change.getOperationType().getValue();
        
        for (ServerInstance server : getConnectedServers()) {
            sendBanUpdateToServer(server, change);
        }
    }
    
    private void kickPlayerIfOnline(String playerId, String reason) {
        Player player = Bukkit.getPlayer(UUID.fromString(playerId));
        if (player != null) {
            player.kickPlayer("You have been banned: " + reason);
        }
    }
    
    private String getPlayerName(String playerId) {
        // Get player name from UUID
        return "Unknown"; // Placeholder
    }
    
    private List<ServerInstance> getConnectedServers() {
        // Get list of connected servers
        return new ArrayList<>();
    }
    
    private void sendBanUpdateToServer(ServerInstance server, ChangeStreamDocument<Document> change) {
        // Send ban update to specific server
        // Implementation depends on your server communication system
    }
}
```

---

## Troubleshooting

### Common Issues

#### 1. Change Stream Not Working

**Symptoms:**
- No change notifications received
- Errors about replica set

**Solutions:**
```bash
# Check if MongoDB is running as replica set
mongo --eval "rs.status()"

# Initialize replica set if needed
mongo --eval "rs.initiate()"

# Check MongoDB version
mongo --eval "db.version()"
```

#### 2. Connection Errors

**Symptoms:**
- `MongoTimeoutException`
- `MongoSocketException`

**Solutions:**
```yaml
# Update connection settings
mongodb:
  connection-string: "mongodb://localhost:27017/?replicaSet=rs0&connectTimeoutMS=10000&socketTimeoutMS=10000"
  change-streams:
    max-await-time: 5000
```

#### 3. Resume Token Errors

**Symptoms:**
- `MongoChangeStreamException: Resume token not found`

**Solutions:**
```java
// Clear resume token and restart
changeStream = collection.watch()
    .startAtOperationTime(null); // Start from current time
```

#### 4. Performance Issues

**Symptoms:**
- High CPU usage
- Memory leaks
- Slow change processing

**Solutions:**
```java
// Optimize change stream
changeStream = collection.watch()
    .batchSize(25)                    // Smaller batches
    .maxAwaitTime(1, TimeUnit.SECONDS) // Shorter wait time
    .fullDocument(FullDocument.DEFAULT); // Less data transfer
```

### Monitoring Change Streams

```java
public class ChangeStreamMonitor {
    
    private final MeterRegistry registry;
    
    public ChangeStreamMonitor(MeterRegistry registry) {
        this.registry = registry;
    }
    
    public void recordChangeProcessed(String collection, long processingTime) {
        registry.timer("mongodb.changestream.processing_time", 
            Tags.of("collection", collection))
            .record(processingTime, TimeUnit.MILLISECONDS);
    }
    
    public void recordChangeError(String collection, String errorType) {
        registry.counter("mongodb.changestream.errors", 
            Tags.of("collection", collection, "error_type", errorType))
            .increment();
    }
    
    public void recordChangeStreamRestart(String collection) {
        registry.counter("mongodb.changestream.restarts", 
            Tags.of("collection", collection))
            .increment();
    }
}
```

---

*Next: Learn about [[Multi-Server Architecture]] for advanced server synchronization patterns.*