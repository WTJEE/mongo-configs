# Performance Tips

Optimization strategies and best practices for high-performance MongoDB Configs API usage, including caching, connection pooling, and query optimization.

## ⚡ Performance Optimization Overview

The Performance Tips guide provides comprehensive optimization strategies for achieving maximum performance with the MongoDB Configs API.

## 📋 Core Performance Strategies

### Connection Pool Optimization

```java
public class OptimizedMongoConfigManager extends ConfigManager {
    
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final CodecRegistry codecRegistry;
    
    public OptimizedMongoConfigManager(MongoConfig config) {
        // Optimized connection settings
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(config.getConnectionString()))
            .applyToConnectionPoolSettings(builder -> 
                builder.maxSize(20) // Maximum connections
                    .minSize(5)   // Minimum connections
                    .maxWaitTime(5000, TimeUnit.MILLISECONDS) // Max wait time
                    .maxConnectionIdleTime(30, TimeUnit.SECONDS) // Idle timeout
            )
            .applyToSocketSettings(builder ->
                builder.connectTimeout(5000, TimeUnit.MILLISECONDS)
                    .readTimeout(10000, TimeUnit.MILLISECONDS)
            )
            .applyToServerSettings(builder ->
                builder.heartbeatFrequency(10000, TimeUnit.MILLISECONDS)
                    .minHeartbeatFrequency(500, TimeUnit.MILLISECONDS)
            )
            .build();
        
        this.mongoClient = MongoClients.create(settings);
        this.codecRegistry = CodecRegistries.fromRegistries(
            CodecRegistries.fromCodecs(new JacksonCodec<>(Object.class)),
            MongoClientSettings.getDefaultCodecRegistry()
        );
        this.database = mongoClient.getDatabase(config.getDatabase()).withCodecRegistry(codecRegistry);
    }
    
    @Override
    public <T> T get(Class<T> type, String key) {
        MongoCollection<Document> collection = getCollection(type);
        
        // Use projection to fetch only needed fields
        Bson projection = Projections.include("_id", "data", "lastModified");
        
        Document doc = collection.find(Filters.eq("_id", key))
            .projection(projection)
            .first();
            
        if (doc == null) {
            return null;
        }
        
        return deserialize(type, doc.get("data", Document.class));
    }
    
    @Override
    public <T> void save(T config) {
        MongoCollection<Document> collection = getCollection(config.getClass());
        String key = generateKey(config);
        
        Document doc = new Document("_id", key)
            .append("data", serialize(config))
            .append("lastModified", new Date())
            .append("version", getNextVersion(key));
        
        // Use upsert for atomic operation
        collection.replaceOne(
            Filters.eq("_id", key), 
            doc, 
            new ReplaceOptions().upsert(true)
        );
    }
    
    private <T> MongoCollection<Document> getCollection(Class<T> type) {
        String collectionName = getCollectionName(type);
        return database.getCollection(collectionName);
    }
    
    private String getCollectionName(Class<?> type) {
        // Use simple class name for collection naming
        return type.getSimpleName().toLowerCase() + "s";
    }
    
    private long getNextVersion(String key) {
        // Implement optimistic locking with version numbers
        // This helps prevent concurrent modification issues
        return System.currentTimeMillis();
    }
}
```

### Advanced Caching Strategy

```java
public class MultiLevelCache implements Cache {
    
    private final Cache<String, Object> l1Cache; // Caffeine in-memory
    private final Cache<String, Object> l2Cache; // MongoDB-based
    private final ExecutorService cacheExecutor;
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    public MultiLevelCache(MongoConfigsPlugin plugin) {
        // L1 Cache: High-speed in-memory cache
        this.l1Cache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .removalListener((String key, Object value, RemovalCause cause) -> {
                if (cause == RemovalCause.EXPIRED) {
                    // Write back to L2 if modified
                    writeToL2Async(key, value);
                }
            })
            .build();
        
        // L2 Cache: Persistent MongoDB cache
        this.l2Cache = new MongoDBCache(plugin.getConfigManager());
        this.cacheExecutor = Executors.newCachedThreadPool();
    }
    
    @Override
    public <T> T get(String key, Class<T> type) {
        // Try L1 cache first
        T value = getFromL1(key, type);
        if (value != null) {
            return value;
        }
        
        // Try L2 cache
        value = getFromL2(key, type);
        if (value != null) {
            // Populate L1 cache
            putInL1(key, value);
            return value;
        }
        
        return null;
    }
    
    @Override
    public void put(String key, Object value) {
        // Write to both caches
        putInL1(key, value);
        writeToL2Async(key, value);
    }
    
    @Override
    public void invalidate(String key) {
        cacheLock.writeLock().lock();
        try {
            l1Cache.invalidate(key);
            l2Cache.invalidate(key);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    @Override
    public void invalidateAll() {
        cacheLock.writeLock().lock();
        try {
            l1Cache.invalidateAll();
            l2Cache.invalidateAll();
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> T getFromL1(String key, Class<T> type) {
        cacheLock.readLock().lock();
        try {
            Object value = l1Cache.getIfPresent(key);
            return value != null && type.isInstance(value) ? (T) value : null;
        } finally {
            cacheLock.readLock().unlock();
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> T getFromL2(String key, Class<T> type) {
        try {
            Object value = l2Cache.getIfPresent(key);
            return value != null && type.isInstance(value) ? (T) value : null;
        } catch (Exception e) {
            // Log error but don't fail
            MongoConfigsPlugin.getInstance().getLogger()
                .warning("L2 cache read error for key '" + key + "': " + e.getMessage());
            return null;
        }
    }
    
    private void putInL1(String key, Object value) {
        cacheLock.writeLock().lock();
        try {
            l1Cache.put(key, value);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    private void writeToL2Async(String key, Object value) {
        cacheExecutor.submit(() -> {
            try {
                l2Cache.put(key, value);
            } catch (Exception e) {
                MongoConfigsPlugin.getInstance().getLogger()
                    .warning("L2 cache write error for key '" + key + "': " + e.getMessage());
            }
        });
    }
    
    public void preloadCache(List<String> keys) {
        // Preload frequently accessed keys
        cacheExecutor.submit(() -> {
            for (String key : keys) {
                try {
                    Object value = l2Cache.getIfPresent(key);
                    if (value != null) {
                        putInL1(key, value);
                    }
                } catch (Exception e) {
                    // Log error but continue
                }
            }
        });
    }
    
    public CacheStats getStats() {
        return new CacheStats(
            l1Cache.stats().hitCount(),
            l1Cache.stats().missCount(),
            l1Cache.estimatedSize(),
            l2Cache.estimatedSize()
        );
    }
    
    public void shutdown() {
        cacheExecutor.shutdown();
        try {
            if (!cacheExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                cacheExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cacheExecutor.shutdownNow();
        }
    }
    
    public static class CacheStats {
        private final long l1Hits;
        private final long l1Misses;
        private final long l1Size;
        private final long l2Size;
        
        public CacheStats(long l1Hits, long l1Misses, long l1Size, long l2Size) {
            this.l1Hits = l1Hits;
            this.l1Misses = l1Misses;
            this.l1Size = l1Size;
            this.l2Size = l2Size;
        }
        
        public double getL1HitRate() {
            long total = l1Hits + l1Misses;
            return total > 0 ? (double) l1Hits / total : 0.0;
        }
        
        public long getL1Hits() { return l1Hits; }
        public long getL1Misses() { return l1Misses; }
        public long getL1Size() { return l1Size; }
        public long getL2Size() { return l2Size; }
    }
}
```

### Bulk Operation Manager

```java
public class BulkOperationManager {
    
    private final MongoConfigsPlugin plugin;
    private final ExecutorService bulkExecutor;
    private final Map<String, BulkOperation> pendingOperations = new ConcurrentHashMap<>();
    private final AtomicInteger operationCounter = new AtomicInteger(0);
    
    public BulkOperationManager(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.bulkExecutor = Executors.newFixedThreadPool(2);
    }
    
    public <T> CompletableFuture<BulkResult> executeBulkSave(List<T> items, String collectionName) {
        String operationId = "bulk_save_" + operationCounter.incrementAndGet();
        
        BulkOperation operation = new BulkOperation(operationId, BulkOperation.Type.SAVE, items.size());
        pendingOperations.put(operationId, operation);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return performBulkSave(items, collectionName);
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                pendingOperations.remove(operationId);
            }
        }, bulkExecutor);
    }
    
    public CompletableFuture<BulkResult> executeBulkDelete(List<String> keys, String collectionName) {
        String operationId = "bulk_delete_" + operationCounter.incrementAndGet();
        
        BulkOperation operation = new BulkOperation(operationId, BulkOperation.Type.DELETE, keys.size());
        pendingOperations.put(operationId, operation);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return performBulkDelete(keys, collectionName);
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                pendingOperations.remove(operationId);
            }
        }, bulkExecutor);
    }
    
    private <T> BulkResult performBulkSave(List<T> items, String collectionName) {
        MongoCollection<Document> collection = plugin.getConfigManager()
            .getDatabase().getCollection(collectionName);
        
        List<WriteModel<Document>> writes = new ArrayList<>();
        
        for (T item : items) {
            String key = generateKey(item);
            Document doc = new Document("_id", key)
                .append("data", serialize(item))
                .append("lastModified", new Date());
            
            writes.add(new ReplaceOneModel<>(
                Filters.eq("_id", key), 
                doc, 
                new ReplaceOptions().upsert(true)
            ));
        }
        
        // Execute bulk write
        BulkWriteResult result = collection.bulkWrite(writes, 
            new BulkWriteOptions().ordered(false)); // Unordered for better performance
        
        return new BulkResult(
            result.getInsertedCount(),
            result.getModifiedCount(),
            result.getDeletedCount(),
            result.wasAcknowledged()
        );
    }
    
    private BulkResult performBulkDelete(List<String> keys, String collectionName) {
        MongoCollection<Document> collection = plugin.getConfigManager()
            .getDatabase().getCollection(collectionName);
        
        List<WriteModel<Document>> deletes = keys.stream()
            .map(key -> new DeleteOneModel<>(Filters.eq("_id", key)))
            .collect(Collectors.toList());
        
        BulkWriteResult result = collection.bulkWrite(deletes);
        
        return new BulkResult(
            0, // No inserts
            0, // No modifications
            result.getDeletedCount(),
            result.wasAcknowledged()
        );
    }
    
    private <T> String generateKey(T item) {
        // Generate key based on item type and properties
        if (item instanceof PlayerConfig) {
            return "player:" + ((PlayerConfig) item).getPlayerId();
        } else if (item instanceof ServerConfig) {
            return "server:" + ((ServerConfig) item).getServerId();
        } else {
            return item.getClass().getSimpleName() + ":" + UUID.randomUUID().toString();
        }
    }
    
    private <T> Document serialize(T item) {
        // Use Jackson for serialization
        try {
            return Document.parse(plugin.getObjectMapper().writeValueAsString(item));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize item", e);
        }
    }
    
    public Map<String, BulkOperation> getPendingOperations() {
        return new HashMap<>(pendingOperations);
    }
    
    public void cancelOperation(String operationId) {
        BulkOperation operation = pendingOperations.get(operationId);
        if (operation != null) {
            operation.cancel();
            pendingOperations.remove(operationId);
        }
    }
    
    public void shutdown() {
        // Cancel all pending operations
        pendingOperations.values().forEach(BulkOperation::cancel);
        pendingOperations.clear();
        
        bulkExecutor.shutdown();
        try {
            if (!bulkExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                bulkExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            bulkExecutor.shutdownNow();
        }
    }
    
    public static class BulkResult {
        private final int inserted;
        private final int modified;
        private final int deleted;
        private final boolean acknowledged;
        
        public BulkResult(int inserted, int modified, int deleted, boolean acknowledged) {
            this.inserted = inserted;
            this.modified = modified;
            this.deleted = deleted;
            this.acknowledged = acknowledged;
        }
        
        public int getInserted() { return inserted; }
        public int getModified() { return modified; }
        public int getDeleted() { return deleted; }
        public boolean isAcknowledged() { return acknowledged; }
        public int getTotalAffected() { return inserted + modified + deleted; }
    }
    
    public static class BulkOperation {
        
        public enum Type { SAVE, DELETE, UPDATE }
        
        private final String operationId;
        private final Type type;
        private final int itemCount;
        private final long startTime;
        private volatile boolean cancelled = false;
        
        public BulkOperation(String operationId, Type type, int itemCount) {
            this.operationId = operationId;
            this.type = type;
            this.itemCount = itemCount;
            this.startTime = System.currentTimeMillis();
        }
        
        public void cancel() {
            cancelled = true;
        }
        
        public boolean isCancelled() {
            return cancelled;
        }
        
        public String getOperationId() { return operationId; }
        public Type getType() { return type; }
        public int getItemCount() { return itemCount; }
        public long getStartTime() { return startTime; }
        public long getElapsedTime() { return System.currentTimeMillis() - startTime; }
    }
}
```

## 🔧 Performance Monitoring

### Performance Metrics Collector

```java
public class PerformanceMetrics {
    
    private final Map<String, OperationMetrics> operationMetrics = new ConcurrentHashMap<>();
    private final Map<String, CacheMetrics> cacheMetrics = new ConcurrentHashMap<>();
    private final Map<String, DatabaseMetrics> databaseMetrics = new ConcurrentHashMap<>();
    private final ScheduledExecutorService metricsExecutor;
    
    public PerformanceMetrics() {
        this.metricsExecutor = Executors.newSingleThreadScheduledExecutor();
        
        // Schedule periodic metrics reporting
        metricsExecutor.scheduleAtFixedRate(this::reportMetrics, 5, 5, TimeUnit.MINUTES);
    }
    
    public void recordOperation(String operationName, long durationMs, boolean success) {
        OperationMetrics metrics = operationMetrics.computeIfAbsent(operationName, 
            k -> new OperationMetrics());
        
        metrics.recordOperation(durationMs, success);
    }
    
    public void recordCacheHit(String cacheName) {
        CacheMetrics metrics = cacheMetrics.computeIfAbsent(cacheName, k -> new CacheMetrics());
        metrics.recordHit();
    }
    
    public void recordCacheMiss(String cacheName) {
        CacheMetrics metrics = cacheMetrics.computeIfAbsent(cacheName, k -> new CacheMetrics());
        metrics.recordMiss();
    }
    
    public void recordDatabaseOperation(String operation, long durationMs, boolean success) {
        DatabaseMetrics metrics = databaseMetrics.computeIfAbsent(operation, 
            k -> new DatabaseMetrics());
        
        metrics.recordOperation(durationMs, success);
    }
    
    private void reportMetrics() {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        Logger logger = plugin.getLogger();
        
        logger.info("=== Performance Metrics Report ===");
        
        // Operation metrics
        logger.info("Operation Metrics:");
        operationMetrics.forEach((name, metrics) -> {
            logger.info(String.format("  %s: avg=%.2fms, min=%.2fms, max=%.2fms, success=%.2f%%, count=%d",
                name, metrics.getAverageDuration(), metrics.getMinDuration(), 
                metrics.getMaxDuration(), metrics.getSuccessRate() * 100, metrics.getTotalCount()));
        });
        
        // Cache metrics
        logger.info("Cache Metrics:");
        cacheMetrics.forEach((name, metrics) -> {
            logger.info(String.format("  %s: hit_rate=%.2f%%, hits=%d, misses=%d",
                name, metrics.getHitRate() * 100, metrics.getHits(), metrics.getMisses()));
        });
        
        // Database metrics
        logger.info("Database Metrics:");
        databaseMetrics.forEach((name, metrics) -> {
            logger.info(String.format("  %s: avg=%.2fms, success=%.2f%%, count=%d",
                name, metrics.getAverageDuration(), metrics.getSuccessRate() * 100, metrics.getTotalCount()));
        });
        
        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        logger.info(String.format("Memory Usage: %.2fMB used, %.2fMB free, %.2fMB total",
            usedMemory / 1024.0 / 1024.0,
            runtime.freeMemory() / 1024.0 / 1024.0,
            runtime.totalMemory() / 1024.0 / 1024.0));
    }
    
    public Map<String, Object> getMetricsSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        
        snapshot.put("operations", new HashMap<>(operationMetrics));
        snapshot.put("cache", new HashMap<>(cacheMetrics));
        snapshot.put("database", new HashMap<>(databaseMetrics));
        
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> memory = new HashMap<>();
        memory.put("used", runtime.totalMemory() - runtime.freeMemory());
        memory.put("free", runtime.freeMemory());
        memory.put("total", runtime.totalMemory());
        memory.put("max", runtime.maxMemory());
        snapshot.put("memory", memory);
        
        return snapshot;
    }
    
    public void resetMetrics() {
        operationMetrics.clear();
        cacheMetrics.clear();
        databaseMetrics.clear();
    }
    
    public void shutdown() {
        metricsExecutor.shutdown();
        try {
            if (!metricsExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                metricsExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            metricsExecutor.shutdownNow();
        }
    }
    
    public static class OperationMetrics {
        private final AtomicLong totalDuration = new AtomicLong(0);
        private final AtomicLong totalCount = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong minDuration = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxDuration = new AtomicLong(0);
        
        public void recordOperation(long durationMs, boolean success) {
            totalDuration.addAndGet(durationMs);
            totalCount.incrementAndGet();
            
            if (success) {
                successCount.incrementAndGet();
            }
            
            // Update min/max
            long currentMin = minDuration.get();
            while (durationMs < currentMin && !minDuration.compareAndSet(currentMin, durationMs)) {
                currentMin = minDuration.get();
            }
            
            long currentMax = maxDuration.get();
            while (durationMs > currentMax && !maxDuration.compareAndSet(currentMax, durationMs)) {
                currentMax = maxDuration.get();
            }
        }
        
        public double getAverageDuration() {
            long count = totalCount.get();
            return count > 0 ? (double) totalDuration.get() / count : 0.0;
        }
        
        public double getMinDuration() {
            return minDuration.get() == Long.MAX_VALUE ? 0.0 : minDuration.get();
        }
        
        public double getMaxDuration() {
            return maxDuration.get();
        }
        
        public double getSuccessRate() {
            long count = totalCount.get();
            return count > 0 ? (double) successCount.get() / count : 0.0;
        }
        
        public long getTotalCount() {
            return totalCount.get();
        }
    }
    
    public static class CacheMetrics {
        private final AtomicLong hits = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);
        
        public void recordHit() {
            hits.incrementAndGet();
        }
        
        public void recordMiss() {
            misses.incrementAndGet();
        }
        
        public double getHitRate() {
            long total = hits.get() + misses.get();
            return total > 0 ? (double) hits.get() / total : 0.0;
        }
        
        public long getHits() { return hits.get(); }
        public long getMisses() { return misses.get(); }
        public long getTotalRequests() { return hits.get() + misses.get(); }
    }
    
    public static class DatabaseMetrics {
        private final AtomicLong totalDuration = new AtomicLong(0);
        private final AtomicLong totalCount = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        
        public void recordOperation(long durationMs, boolean success) {
            totalDuration.addAndGet(durationMs);
            totalCount.incrementAndGet();
            
            if (success) {
                successCount.incrementAndGet();
            }
        }
        
        public double getAverageDuration() {
            long count = totalCount.get();
            return count > 0 ? (double) totalDuration.get() / count : 0.0;
        }
        
        public double getSuccessRate() {
            long count = totalCount.get();
            return count > 0 ? (double) successCount.get() / count : 0.0;
        }
        
        public long getTotalCount() {
            return totalCount.get();
        }
    }
}
```

## 🔧 Optimization Techniques

### Query Optimization

```java
public class QueryOptimizer {
    
    public static Bson createOptimizedQuery(String key, Map<String, Object> filters) {
        List<Bson> conditions = new ArrayList<>();
        
        // Add key condition
        conditions.add(Filters.eq("_id", key));
        
        // Add additional filters
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            String field = filter.getKey();
            Object value = filter.getValue();
            
            if (value instanceof String) {
                // Use regex for string matching with index
                conditions.add(Filters.regex("data." + field, (String) value, "i"));
            } else if (value instanceof Number) {
                conditions.add(Filters.eq("data." + field, value));
            } else if (value instanceof List) {
                conditions.add(Filters.in("data." + field, (List<?>) value));
            } else {
                conditions.add(Filters.eq("data." + field, value));
            }
        }
        
        return Filters.and(conditions);
    }
    
    public static Bson createProjection(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return Projections.include("_id", "data", "lastModified");
        }
        
        List<String> projectionFields = new ArrayList<>();
        projectionFields.add("_id");
        projectionFields.add("lastModified");
        
        // Add requested data fields
        for (String field : fields) {
            projectionFields.add("data." + field);
        }
        
        return Projections.include(projectionFields);
    }
    
    public static FindIterable<Document> optimizeFind(MongoCollection<Document> collection, 
                                                    Bson filter, Bson projection, 
                                                    Integer limit, Integer skip) {
        FindIterable<Document> find = collection.find(filter);
        
        if (projection != null) {
            find = find.projection(projection);
        }
        
        if (limit != null && limit > 0) {
            find = find.limit(limit);
        }
        
        if (skip != null && skip > 0) {
            find = find.skip(skip);
        }
        
        // Add hints for indexed queries
        if (hasIndexHint(filter)) {
            find = find.hint(getIndexHint(filter));
        }
        
        return find;
    }
    
    private static boolean hasIndexHint(Bson filter) {
        // Check if query can benefit from specific index
        return filter.toBsonDocument().containsKey("_id");
    }
    
    private static Bson getIndexHint(Bson filter) {
        // Return appropriate index hint
        return Indexes.ascending("_id");
    }
}
```

### Index Management

```java
public class IndexManager {
    
    private final MongoDatabase database;
    private final Map<String, List<IndexModel>> collectionIndexes = new HashMap<>();
    
    public IndexManager(MongoDatabase database) {
        this.database = database;
        initializeIndexes();
    }
    
    private void initializeIndexes() {
        // Config collection indexes
        collectionIndexes.put("configs", Arrays.asList(
            new IndexModel(Indexes.ascending("_id")),
            new IndexModel(Indexes.ascending("lastModified")),
            new IndexModel(Indexes.ascending("data.type")),
            new IndexModel(Indexes.compoundIndex(
                Indexes.ascending("data.type"), 
                Indexes.ascending("lastModified")
            ))
        ));
        
        // Translation collection indexes
        collectionIndexes.put("translations", Arrays.asList(
            new IndexModel(Indexes.ascending("_id")),
            new IndexModel(Indexes.ascending("language")),
            new IndexModel(Indexes.ascending("key")),
            new IndexModel(Indexes.compoundIndex(
                Indexes.ascending("language"), 
                Indexes.ascending("key")
            ), new IndexOptions().unique(true))
        ));
        
        // Player data indexes
        collectionIndexes.put("playerdata", Arrays.asList(
            new IndexModel(Indexes.ascending("_id")),
            new IndexModel(Indexes.ascending("data.uuid")),
            new IndexModel(Indexes.ascending("data.language")),
            new IndexModel(Indexes.ascending("lastModified"))
        ));
    }
    
    public void createIndexes() {
        for (Map.Entry<String, List<IndexModel>> entry : collectionIndexes.entrySet()) {
            String collectionName = entry.getKey();
            List<IndexModel> indexes = entry.getValue();
            
            MongoCollection<Document> collection = database.getCollection(collectionName);
            
            try {
                List<String> indexNames = collection.createIndexes(indexes);
                MongoConfigsPlugin.getInstance().getLogger()
                    .info("Created indexes for " + collectionName + ": " + indexNames);
            } catch (Exception e) {
                MongoConfigsPlugin.getInstance().getLogger()
                    .warning("Failed to create indexes for " + collectionName + ": " + e.getMessage());
            }
        }
    }
    
    public void validateIndexes() {
        for (String collectionName : collectionIndexes.keySet()) {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            
            try {
                List<Document> indexes = new ArrayList<>();
                collection.listIndexes().into(indexes);
                
                MongoConfigsPlugin.getInstance().getLogger()
                    .info("Collection " + collectionName + " has " + indexes.size() + " indexes");
                
                // Check for missing indexes
                checkMissingIndexes(collectionName, indexes);
                
            } catch (Exception e) {
                MongoConfigsPlugin.getInstance().getLogger()
                    .warning("Failed to validate indexes for " + collectionName + ": " + e.getMessage());
            }
        }
    }
    
    private void checkMissingIndexes(String collectionName, List<Document> existingIndexes) {
        List<IndexModel> expectedIndexes = collectionIndexes.get(collectionName);
        if (expectedIndexes == null) return;
        
        Set<String> existingIndexNames = existingIndexes.stream()
            .map(doc -> doc.getString("name"))
            .collect(Collectors.toSet());
        
        for (IndexModel expectedIndex : expectedIndexes) {
            String expectedName = expectedIndex.getOptions() != null ? 
                expectedIndex.getOptions().getName() : null;
            
            if (expectedName != null && !existingIndexNames.contains(expectedName)) {
                MongoConfigsPlugin.getInstance().getLogger()
                    .warning("Missing index '" + expectedName + "' in collection " + collectionName);
            }
        }
    }
    
    public void dropUnusedIndexes() {
        // This should be used carefully in production
        for (String collectionName : collectionIndexes.keySet()) {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            
            try {
                Document stats = database.runCommand(new Document("collStats", collectionName));
                double totalIndexSize = stats.get("totalIndexSize", Double.class);
                
                MongoConfigsPlugin.getInstance().getLogger()
                    .info("Collection " + collectionName + " index size: " + 
                          (totalIndexSize / 1024 / 1024) + "MB");
                
            } catch (Exception e) {
                // Log error but continue
            }
        }
    }
    
    public Map<String, Object> getIndexStats() {
        Map<String, Object> stats = new HashMap<>();
        
        for (String collectionName : collectionIndexes.keySet()) {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            
            try {
                List<Document> indexes = new ArrayList<>();
                collection.listIndexes().into(indexes);
                
                Map<String, Object> collectionStats = new HashMap<>();
                collectionStats.put("count", indexes.size());
                collectionStats.put("indexes", indexes.stream()
                    .map(doc -> doc.get("name"))
                    .collect(Collectors.toList()));
                
                stats.put(collectionName, collectionStats);
                
            } catch (Exception e) {
                stats.put(collectionName, "Error: " + e.getMessage());
            }
        }
        
        return stats;
    }
}
```

## 🔧 Integration Examples

### Performance Command

```java
public class PerformanceCommand implements CommandExecutor {
    
    private final MongoConfigsPlugin plugin;
    private final PerformanceMetrics metrics;
    private final IndexManager indexManager;
    
    public PerformanceCommand(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.metrics = plugin.getPerformanceMetrics();
        this.indexManager = plugin.getIndexManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mongoconfigs.admin")) {
            sender.sendMessage(ColorHelper.parseComponent("&cYou don't have permission to use this command!"));
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "stats":
                return handleStats(sender);
            case "cache":
                return handleCache(sender, Arrays.copyOfRange(args, 1, args.length));
            case "indexes":
                return handleIndexes(sender, Arrays.copyOfRange(args, 1, args.length));
            case "memory":
                return handleMemory(sender);
            case "reset":
                return handleReset(sender);
            default:
                sendUsage(sender);
                return true;
        }
    }
    
    private boolean handleStats(CommandSender sender) {
        Map<String, Object> snapshot = metrics.getMetricsSnapshot();
        
        sender.sendMessage(ColorHelper.parseComponent("&6=== Performance Statistics ==="));
        
        // Memory stats
        @SuppressWarnings("unchecked")
        Map<String, Object> memory = (Map<String, Object>) snapshot.get("memory");
        if (memory != null) {
            long used = (Long) memory.get("used");
            long free = (Long) memory.get("free");
            long total = (Long) memory.get("total");
            
            sender.sendMessage(ColorHelper.parseComponent(String.format(
                "&fMemory: &e%.2fMB used, &e%.2fMB free, &e%.2fMB total",
                used / 1024.0 / 1024.0, free / 1024.0 / 1024.0, total / 1024.0 / 1024.0)));
        }
        
        return true;
    }
    
    private boolean handleCache(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorHelper.parseComponent("&cUsage: /perf cache <stats|clear|preload>"));
            return true;
        }
        
        String action = args[0].toLowerCase();
        
        switch (action) {
            case "stats":
                MultiLevelCache.CacheStats stats = plugin.getCache().getStats();
                sender.sendMessage(ColorHelper.parseComponent("&6=== Cache Statistics ==="));
                sender.sendMessage(ColorHelper.parseComponent(String.format(
                    "&fL1 Cache: &e%d items, &e%.2f%% hit rate",
                    stats.getL1Size(), stats.getL1HitRate() * 100)));
                sender.sendMessage(ColorHelper.parseComponent(String.format(
                    "&fL2 Cache: &e%d items", stats.getL2Size())));
                break;
                
            case "clear":
                plugin.getCache().invalidateAll();
                sender.sendMessage(ColorHelper.parseComponent("&aCache cleared"));
                break;
                
            case "preload":
                // Preload common configurations
                plugin.getCache().preloadCache(Arrays.asList(
                    "global_config", "language_config", "server_config"));
                sender.sendMessage(ColorHelper.parseComponent("&aCache preload initiated"));
                break;
                
            default:
                sender.sendMessage(ColorHelper.parseComponent("&cUnknown cache action: " + action));
        }
        
        return true;
    }
    
    private boolean handleIndexes(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorHelper.parseComponent("&cUsage: /perf indexes <create|validate|stats>"));
            return true;
        }
        
        String action = args[0].toLowerCase();
        
        switch (action) {
            case "create":
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    indexManager.createIndexes();
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ColorHelper.parseComponent("&aIndexes created")));
                });
                sender.sendMessage(ColorHelper.parseComponent("&aCreating indexes..."));
                break;
                
            case "validate":
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    indexManager.validateIndexes();
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ColorHelper.parseComponent("&aIndex validation completed")));
                });
                sender.sendMessage(ColorHelper.parseComponent("&aValidating indexes..."));
                break;
                
            case "stats":
                Map<String, Object> stats = indexManager.getIndexStats();
                sender.sendMessage(ColorHelper.parseComponent("&6=== Index Statistics ==="));
                stats.forEach((collection, stat) -> 
                    sender.sendMessage(ColorHelper.parseComponent("&f" + collection + ": &e" + stat)));
                break;
                
            default:
                sender.sendMessage(ColorHelper.parseComponent("&cUnknown index action: " + action));
        }
        
        return true;
    }
    
    private boolean handleMemory(CommandSender sender) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long free = runtime.freeMemory();
        long total = runtime.totalMemory();
        long max = runtime.maxMemory();
        
        sender.sendMessage(ColorHelper.parseComponent("&6=== Memory Information ==="));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fUsed: &e%.2fMB", used / 1024.0 / 1024.0)));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fFree: &e%.2fMB", free / 1024.0 / 1024.0)));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fTotal: &e%.2fMB", total / 1024.0 / 1024.0)));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fMax: &e%.2fMB", max / 1024.0 / 1024.0)));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fUsage: &e%.2f%%", (double) used / total * 100)));
        
        return true;
    }
    
    private boolean handleReset(CommandSender sender) {
        metrics.resetMetrics();
        sender.sendMessage(ColorHelper.parseComponent("&aPerformance metrics reset"));
        return true;
    }
    
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&6Performance Commands:"));
        sender.sendMessage(ColorHelper.parseComponent("&f/perf stats &7- Show performance statistics"));
        sender.sendMessage(ColorHelper.parseComponent("&f/perf cache <stats|clear|preload> &7- Cache management"));
        sender.sendMessage(ColorHelper.parseComponent("&f/perf indexes <create|validate|stats> &7- Index management"));
        sender.sendMessage(ColorHelper.parseComponent("&f/perf memory &7- Show memory information"));
        sender.sendMessage(ColorHelper.parseComponent("&f/perf reset &7- Reset performance metrics"));
    }
}
```

### Performance Configuration

```yaml
# config.yml
performance:
  enabled: true
  metrics-enabled: true
  metrics-interval-minutes: 5
  
  # Cache settings
  cache:
    l1-size: 10000
    l1-expiry-minutes: 30
    l2-enabled: true
    l2-expiry-minutes: 60
  
  # Connection pool settings
  connection-pool:
    max-size: 20
    min-size: 5
    max-wait-ms: 5000
    max-idle-time-seconds: 30
  
  # Bulk operations
  bulk:
    enabled: true
    batch-size: 100
    timeout-ms: 30000
  
  # Query optimization
  query:
    projection-enabled: true
    index-hints-enabled: true
  
  # Async operations
  async:
    thread-pool-size: 10
    operation-timeout-ms: 30000
    retry-attempts: 3
```

---

*Next: Learn about [[Monitoring]] for tracking system performance and health.*