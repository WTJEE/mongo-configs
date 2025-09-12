# Change Streams

> **Real-time data synchronization with MongoDB Change Streams**

## What are Change Streams?

Change Streams are MongoDB's mechanism for real-time data synchronization. They allow applications to:

- **Monitor data changes** in real-time
- **React to updates** instantly across multiple servers
- **Synchronize configurations** automatically
- **Enable live features** like global bans, announcements
- **Maintain data consistency** across distributed systems

## Basic Change Stream Setup

### Enable Change Streams

```java
public class ChangeStreamManager {

    private final MongoManager mongoManager;
    private final Map<String, ChangeStreamIterable<Document>> activeStreams = new ConcurrentHashMap<>();

    public ChangeStreamManager(MongoManager mongoManager) {
        this.mongoManager = mongoManager;
    }

    public void startWatchingCollection(String collectionName, ChangeStreamHandler handler) {
        MongoCollection<Document> collection = mongoManager.getCollection(collectionName);

        ChangeStreamIterable<Document> changeStream = collection.watch();

        // Process changes asynchronously
        changeStream.forEach(change -> {
            try {
                handler.handleChange(change);
            } catch (Exception e) {
                getLogger().error("Error processing change: " + e.getMessage(), e);
            }
        });

        activeStreams.put(collectionName, changeStream);
    }

    public void stopWatchingCollection(String collectionName) {
        ChangeStreamIterable<Document> stream = activeStreams.remove(collectionName);
        if (stream != null) {
            // Note: MongoDB Java driver doesn't provide a direct way to close streams
            // They will be closed when the MongoClient is closed
        }
    }

    public interface ChangeStreamHandler {
        void handleChange(ChangeStreamDocument<Document> change);
    }

    private Logger getLogger() {
        return Logger.getLogger("ChangeStreamManager");
    }
}
```

### Basic Change Handler

```java
public class ConfigChangeHandler implements ChangeStreamManager.ChangeStreamHandler {

    private final ConfigManager configManager;

    public ConfigChangeHandler(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void handleChange(ChangeStreamDocument<Document> change) {
        String operationType = change.getOperationType().getValue();

        switch (operationType) {
            case "insert":
                handleInsert(change);
                break;
            case "update":
                handleUpdate(change);
                break;
            case "delete":
                handleDelete(change);
                break;
            case "replace":
                handleReplace(change);
                break;
        }
    }

    private void handleInsert(ChangeStreamDocument<Document> change) {
        Document document = change.getFullDocument();
        String collectionName = change.getNamespace().getCollectionName();

        getLogger().info("New document inserted in " + collectionName + ": " + document.get("_id"));
    }

    private void handleUpdate(ChangeStreamDocument<Document> change) {
        Document document = change.getFullDocument();
        String collectionName = change.getNamespace().getCollectionName();

        getLogger().info("Document updated in " + collectionName + ": " + document.get("_id"));
    }

    private void handleDelete(ChangeStreamDocument<Document> change) {
        Document documentKey = change.getDocumentKey();
        String collectionName = change.getNamespace().getCollectionName();

        getLogger().info("Document deleted from " + collectionName + ": " + documentKey.get("_id"));
    }

    private void handleReplace(ChangeStreamDocument<Document> change) {
        Document document = change.getFullDocument();
        String collectionName = change.getNamespace().getCollectionName();

        getLogger().info("Document replaced in " + collectionName + ": " + document.get("_id"));
    }

    private Logger getLogger() {
        return Logger.getLogger("ConfigChangeHandler");
    }
}
```

## Advanced Change Stream Features

### Filtered Change Streams

```java
public class FilteredChangeStream {

    public void watchSpecificOperations() {
        MongoCollection<Document> collection = mongoManager.getCollection("configs");

        // Only watch for updates and inserts
        List<Bson> pipeline = Arrays.asList(
            match(in("operationType", Arrays.asList("insert", "update", "replace")))
        );

        ChangeStreamIterable<Document> changeStream = collection.watch(pipeline);

        changeStream.forEach(change -> {
            // Only process inserts, updates, and replaces
            handleConfigChange(change);
        });
    }

    public void watchSpecificFields() {
        MongoCollection<Document> collection = mongoManager.getCollection("configs");

        // Only watch changes to specific fields
        List<Bson> pipeline = Arrays.asList(
            match(exists("updateDescription.updatedFields.serverName")),
            match(exists("updateDescription.updatedFields.maxPlayers"))
        );

        ChangeStreamIterable<Document> changeStream = collection.watch(pipeline);

        changeStream.forEach(change -> {
            // Only process changes to serverName or maxPlayers
            handleFieldChange(change);
        });
    }

    public void watchCollectionChanges() {
        MongoCollection<Document> collection = mongoManager.getCollection("configs");

        // Watch for changes in a specific collection
        List<Bson> pipeline = Arrays.asList(
            match(eq("ns.coll", "configs"))
        );

        ChangeStreamIterable<Document> changeStream = collection.watch(pipeline);

        changeStream.forEach(change -> {
            handleCollectionChange(change);
        });
    }

    private void handleConfigChange(ChangeStreamDocument<Document> change) {
        // Handle configuration changes
    }

    private void handleFieldChange(ChangeStreamDocument<Document> change) {
        // Handle specific field changes
    }

    private void handleCollectionChange(ChangeStreamDocument<Document> change) {
        // Handle collection-specific changes
    }
}
```

### Resume Token Management

```java
public class ResumableChangeStream {

    private BsonDocument resumeToken;
    private final String RESUME_TOKEN_KEY = "changeStreamResumeToken";

    public void startResumableStream() {
        MongoCollection<Document> collection = mongoManager.getCollection("configs");

        ChangeStreamIterable<Document> changeStream;

        // Try to resume from saved token
        if (resumeToken != null) {
            changeStream = collection.watch().resumeAfter(resumeToken);
            getLogger().info("Resumed change stream from token");
        } else {
            changeStream = collection.watch();
            getLogger().info("Started new change stream");
        }

        // Process changes and save resume token
        changeStream.forEach(change -> {
            try {
                handleChange(change);

                // Save resume token for recovery
                resumeToken = change.getResumeToken();
                saveResumeToken(resumeToken);

            } catch (Exception e) {
                getLogger().error("Error processing change: " + e.getMessage(), e);
            }
        });
    }

    public void resumeFromTimestamp(Instant timestamp) {
        MongoCollection<Document> collection = mongoManager.getCollection("configs");

        // Resume from specific timestamp
        BsonDocument timestampDoc = new BsonDocument("ts", new BsonTimestamp(timestamp.getEpochSecond(), 0));
        ChangeStreamIterable<Document> changeStream = collection.watch().startAtOperationTime(timestampDoc);

        changeStream.forEach(this::handleChange);
    }

    private void saveResumeToken(BsonDocument token) {
        // Save to persistent storage
        try {
            Document tokenDoc = new Document("_id", RESUME_TOKEN_KEY)
                .append("token", token)
                .append("timestamp", System.currentTimeMillis());

            mongoManager.getCollection("system").replaceOne(
                eq("_id", RESUME_TOKEN_KEY),
                tokenDoc,
                new ReplaceOptions().upsert(true)
            );

        } catch (Exception e) {
            getLogger().error("Failed to save resume token", e);
        }
    }

    private BsonDocument loadResumeToken() {
        try {
            Document tokenDoc = mongoManager.getCollection("system")
                .find(eq("_id", RESUME_TOKEN_KEY))
                .first();

            if (tokenDoc != null) {
                return tokenDoc.get("token", BsonDocument.class);
            }

        } catch (Exception e) {
            getLogger().error("Failed to load resume token", e);
        }

        return null;
    }

    private void handleChange(ChangeStreamDocument<Document> change) {
        // Process the change
        getLogger().info("Change detected: " + change.getOperationType());
    }

    private Logger getLogger() {
        return Logger.getLogger("ResumableChangeStream");
    }
}
```

## Real-World Examples

### Global Ban System

```java
@ConfigsFileProperties(name = "global-bans")
@ConfigsDatabase("minecraft")
public class GlobalBanManager extends MongoConfig<GlobalBanManager> {

    private Map<String, BanInfo> bannedPlayers = new ConcurrentHashMap<>();

    public static class BanInfo {
        private String playerId;
        private String playerName;
        private String reason;
        private String bannedBy;
        private long banTime;
        private long expiryTime; // -1 for permanent

        // Getters and setters...
    }

    public void banPlayer(String playerId, String reason, String bannedBy) {
        BanInfo banInfo = new BanInfo();
        banInfo.setPlayerId(playerId);
        banInfo.setReason(reason);
        banInfo.setBannedBy(bannedBy);
        banInfo.setBanTime(System.currentTimeMillis());
        banInfo.setExpiryTime(-1); // Permanent ban

        bannedPlayers.put(playerId, banInfo);
        save();

        // Broadcast ban to all servers
        broadcastBan(playerId, banInfo);
    }

    public void unbanPlayer(String playerId) {
        bannedPlayers.remove(playerId);
        save();

        // Broadcast unban to all servers
        broadcastUnban(playerId);
    }

    public boolean isPlayerBanned(String playerId) {
        BanInfo banInfo = bannedPlayers.get(playerId);
        if (banInfo == null) return false;

        // Check if ban has expired
        if (banInfo.getExpiryTime() != -1 &&
            System.currentTimeMillis() > banInfo.getExpiryTime()) {
            bannedPlayers.remove(playerId);
            save();
            return false;
        }

        return true;
    }

    private void broadcastBan(String playerId, BanInfo banInfo) {
        // This will be picked up by Change Streams on other servers
        getLogger().info("Player " + playerId + " banned globally: " + banInfo.getReason());
    }

    private void broadcastUnban(String playerId) {
        // This will be picked up by Change Streams on other servers
        getLogger().info("Player " + playerId + " unbanned globally");
    }

    private Logger getLogger() {
        return Logger.getLogger("GlobalBanManager");
    }

    // Getters and setters...
}
```

### Change Stream Listener for Bans

```java
public class GlobalBanChangeListener implements ChangeStreamManager.ChangeStreamHandler {

    private final GlobalBanManager banManager;
    private final Plugin plugin;

    public GlobalBanChangeListener(GlobalBanManager banManager, Plugin plugin) {
        this.banManager = banManager;
        this.plugin = plugin;
    }

    @Override
    public void handleChange(ChangeStreamDocument<Document> change) {
        String operationType = change.getOperationType().getValue();
        Document document = change.getFullDocument();

        if ("update".equals(operationType) || "replace".equals(operationType)) {
            // Ban list was updated
            updateLocalBanCache(document);

        } else if ("insert".equals(operationType)) {
            // New ban added
            handleNewBan(document);

        } else if ("delete".equals(operationType)) {
            // Ban removed
            handleBanRemoval(change.getDocumentKey());
        }
    }

    private void updateLocalBanCache(Document document) {
        // Reload the entire ban list
        banManager.reload();

        // Kick any banned players currently online
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (banManager.isPlayerBanned(player.getUniqueId().toString())) {
                player.kickPlayer("You have been banned globally!");
            }
        }
    }

    private void handleNewBan(Document document) {
        String playerId = document.getString("playerId");
        String reason = document.getString("reason");

        // Find and kick the player if online
        Player player = plugin.getServer().getPlayer(UUID.fromString(playerId));
        if (player != null) {
            player.kickPlayer("Banned: " + reason);
            plugin.getServer().broadcastMessage(ChatColor.RED + player.getName() + " was banned globally!");
        }
    }

    private void handleBanRemoval(Document documentKey) {
        String playerId = documentKey.getString("_id");
        plugin.getServer().broadcastMessage(ChatColor.GREEN + "Player unbanned globally!");
    }
}
```

### Global Announcement System

```java
@ConfigsFileProperties(name = "global-announcements")
@ConfigsDatabase("minecraft")
public class GlobalAnnouncementManager extends MongoConfig<GlobalAnnouncementManager> {

    private List<Announcement> announcements = new ArrayList<>();

    public static class Announcement {
        private String id;
        private String message;
        private String sender;
        private long timestamp;
        private boolean active = true;

        // Getters and setters...
    }

    public void sendGlobalAnnouncement(String message, String sender) {
        Announcement announcement = new Announcement();
        announcement.setId(UUID.randomUUID().toString());
        announcement.setMessage(message);
        announcement.setSender(sender);
        announcement.setTimestamp(System.currentTimeMillis());
        announcement.setActive(true);

        announcements.add(announcement);
        save();

        // This will trigger Change Streams on all servers
    }

    public void deactivateAnnouncement(String announcementId) {
        announcements.stream()
            .filter(a -> announcementId.equals(a.getId()))
            .findFirst()
            .ifPresent(a -> {
                a.setActive(false);
                save();
            });
    }

    public List<Announcement> getActiveAnnouncements() {
        return announcements.stream()
            .filter(Announcement::isActive)
            .collect(Collectors.toList());
    }

    // Getters and setters...
}
```

### Announcement Change Listener

```java
public class AnnouncementChangeListener implements ChangeStreamManager.ChangeStreamHandler {

    private final GlobalAnnouncementManager announcementManager;
    private final Plugin plugin;

    public AnnouncementChangeListener(GlobalAnnouncementManager announcementManager, Plugin plugin) {
        this.announcementManager = announcementManager;
        this.plugin = plugin;
    }

    @Override
    public void handleChange(ChangeStreamDocument<Document> change) {
        String operationType = change.getOperationType().getValue();

        if ("insert".equals(operationType)) {
            handleNewAnnouncement(change.getFullDocument());

        } else if ("update".equals(operationType) || "replace".equals(operationType)) {
            handleAnnouncementUpdate(change.getFullDocument());
        }
    }

    private void handleNewAnnouncement(Document document) {
        String message = document.getString("message");
        String sender = document.getString("sender");

        // Broadcast announcement to all players
        String announcement = ChatColor.GOLD + "[GLOBAL] " + ChatColor.WHITE + message;
        plugin.getServer().broadcastMessage(announcement);

        // Log the announcement
        getLogger().info("Global announcement from " + sender + ": " + message);
    }

    private void handleAnnouncementUpdate(Document document) {
        // Handle announcement updates (like deactivation)
        announcementManager.reload();
    }

    private Logger getLogger() {
        return Logger.getLogger("AnnouncementChangeListener");
    }
}
```

## Performance Optimization

### Batch Processing

```java
public class BatchedChangeProcessor {

    private final List<ChangeStreamDocument<Document>> changeBatch = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final int batchSize;
    private final long batchTimeout;

    public BatchedChangeProcessor(int batchSize, long batchTimeout) {
        this.batchSize = batchSize;
        this.batchTimeout = batchTimeout;
    }

    public void startBatchProcessing(Consumer<List<ChangeStreamDocument<Document>>> batchHandler) {
        scheduler.scheduleAtFixedRate(() -> {
            processBatch(batchHandler);
        }, batchTimeout, batchTimeout, TimeUnit.MILLISECONDS);
    }

    public synchronized void addChange(ChangeStreamDocument<Document> change) {
        changeBatch.add(change);

        if (changeBatch.size() >= batchSize) {
            processBatchImmediately();
        }
    }

    private synchronized void processBatchImmediately() {
        if (!changeBatch.isEmpty()) {
            List<ChangeStreamDocument<Document>> batch = new ArrayList<>(changeBatch);
            changeBatch.clear();

            // Process batch asynchronously
            CompletableFuture.runAsync(() -> processBatch(batch));
        }
    }

    private synchronized void processBatch(Consumer<List<ChangeStreamDocument<Document>>> batchHandler) {
        if (!changeBatch.isEmpty()) {
            List<ChangeStreamDocument<Document>> batch = new ArrayList<>(changeBatch);
            changeBatch.clear();
            batchHandler.accept(batch);
        }
    }

    private void processBatch(List<ChangeStreamDocument<Document>> batch) {
        try {
            // Group changes by type
            Map<String, List<ChangeStreamDocument<Document>>> groupedChanges = batch.stream()
                .collect(Collectors.groupingBy(change -> change.getOperationType().getValue()));

            // Process each group
            for (Map.Entry<String, List<ChangeStreamDocument<Document>>> entry : groupedChanges.entrySet()) {
                processChangeGroup(entry.getKey(), entry.getValue());
            }

        } catch (Exception e) {
            getLogger().error("Error processing change batch", e);
        }
    }

    private void processChangeGroup(String operationType, List<ChangeStreamDocument<Document>> changes) {
        switch (operationType) {
            case "insert":
                processInserts(changes);
                break;
            case "update":
                processUpdates(changes);
                break;
            case "delete":
                processDeletes(changes);
                break;
        }
    }

    private void processInserts(List<ChangeStreamDocument<Document>> changes) {
        // Bulk insert processing
        getLogger().info("Processing " + changes.size() + " inserts");
    }

    private void processUpdates(List<ChangeStreamDocument<Document>> changes) {
        // Bulk update processing
        getLogger().info("Processing " + changes.size() + " updates");
    }

    private void processDeletes(List<ChangeStreamDocument<Document>> changes) {
        // Bulk delete processing
        getLogger().info("Processing " + changes.size() + " deletes");
    }

    private Logger getLogger() {
        return Logger.getLogger("BatchedChangeProcessor");
    }

    public void shutdown() {
        scheduler.shutdown();
        processBatchImmediately(); // Process remaining changes
    }
}
```

### Error Handling and Recovery

```java
public class ResilientChangeStream {

    private final MongoManager mongoManager;
    private final ChangeStreamManager.ChangeStreamHandler handler;
    private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(1);

    private volatile boolean running = true;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 10;
    private static final long BASE_RETRY_DELAY = 1000; // 1 second

    public ResilientChangeStream(MongoManager mongoManager, ChangeStreamManager.ChangeStreamHandler handler) {
        this.mongoManager = mongoManager;
        this.handler = handler;
    }

    public void start() {
        startChangeStream();
    }

    private void startChangeStream() {
        try {
            MongoCollection<Document> collection = mongoManager.getCollection("configs");

            ChangeStreamIterable<Document> changeStream = collection.watch()
                .maxAwaitTime(1000, TimeUnit.MILLISECONDS);

            changeStream.forEach(change -> {
                if (!running) return;

                try {
                    handler.handleChange(change);
                    retryCount = 0; // Reset retry count on success

                } catch (Exception e) {
                    getLogger().error("Error processing change: " + e.getMessage(), e);

                    // Continue processing other changes
                    // Don't break the stream for individual errors
                }
            });

        } catch (Exception e) {
            getLogger().error("Change stream failed: " + e.getMessage(), e);
            scheduleRetry();
        }
    }

    private void scheduleRetry() {
        if (!running || retryCount >= MAX_RETRIES) {
            getLogger().error("Max retries exceeded, stopping change stream");
            return;
        }

        retryCount++;
        long delay = BASE_RETRY_DELAY * (1L << Math.min(retryCount, 6)); // Exponential backoff

        getLogger().info("Retrying change stream in " + delay + "ms (attempt " + retryCount + ")");

        retryScheduler.schedule(this::startChangeStream, delay, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        retryScheduler.shutdown();
    }

    private Logger getLogger() {
        return Logger.getLogger("ResilientChangeStream");
    }
}
```

## Monitoring and Metrics

### Change Stream Metrics

```java
public class ChangeStreamMetrics {

    private final Map<String, AtomicLong> operationCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);

    public void recordOperation(String operationType) {
        operationCounts.computeIfAbsent(operationType, k -> new AtomicLong(0)).incrementAndGet();
        totalProcessed.incrementAndGet();
    }

    public void recordError(String operationType) {
        errorCounts.computeIfAbsent(operationType, k -> new AtomicLong(0)).incrementAndGet();
        totalErrors.incrementAndGet();
    }

    public void printMetrics() {
        getLogger().info("=== Change Stream Metrics ===");
        getLogger().info("Total processed: " + totalProcessed.get());
        getLogger().info("Total errors: " + totalErrors.get());

        operationCounts.forEach((op, count) ->
            getLogger().info("Operation " + op + ": " + count.get()));

        errorCounts.forEach((op, count) ->
            getLogger().info("Errors for " + op + ": " + count.get()));
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalProcessed", totalProcessed.get());
        metrics.put("totalErrors", totalErrors.get());
        metrics.put("operationCounts", new HashMap<>(operationCounts));
        metrics.put("errorCounts", new HashMap<>(errorCounts));
        return metrics;
    }

    private Logger getLogger() {
        return Logger.getLogger("ChangeStreamMetrics");
    }
}
```

---

*Change Streams configured? Check out [[Multi-Server Architecture]] for complete multi-server setup.*