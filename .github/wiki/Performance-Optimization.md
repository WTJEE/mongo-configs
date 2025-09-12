# Performance Optimization

Comprehensive guide to optimizing MongoDB Configs API performance with caching strategies, connection pooling, and monitoring.

## 📊 Performance Overview

This tutorial covers advanced performance optimization techniques for high-throughput applications using MongoDB Configs API.

## 🚀 Connection Optimization

### Connection Pool Configuration

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "performance_config")
@ConfigsCollection(collection = "connection_settings")
public class ConnectionConfig {
    
    @ConfigsField
    private int minPoolSize = 5;
    
    @ConfigsField
    private int maxPoolSize = 50;
    
    @ConfigsField
    private int maxIdleTimeMS = 30000;
    
    @ConfigsField
    private int maxLifeTimeMS = 300000;
    
    @ConfigsField
    private int connectionTimeoutMS = 10000;
    
    @ConfigsField
    private int serverSelectionTimeoutMS = 5000;
    
    @ConfigsField
    private int socketTimeoutMS = 20000;
    
    @ConfigsField
    private boolean retryWrites = true;
    
    @ConfigsField
    private boolean retryReads = true;
    
    @ConfigsField
    private ReadPreference readPreference = ReadPreference.primaryPreferred();
    
    @ConfigsField
    private WriteConcern writeConcern = WriteConcern.W1;
    
    // Getters and setters...
}
```

### Optimized ConfigManager Setup

```java
public class OptimizedConfigManager {
    
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final CodecRegistry codecRegistry;
    
    public OptimizedConfigManager(String connectionString, String databaseName) {
        // Configure connection pool
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(connectionString))
            .applyToConnectionPoolSettings(builder -> 
                builder.minSize(5)
                    .maxSize(50)
                    .maxIdleTime(30, TimeUnit.SECONDS)
                    .maxLifeTime(5, TimeUnit.MINUTES)
            )
            .applyToSocketSettings(builder ->
                builder.connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
            )
            .applyToServerSettings(builder ->
                builder.heartbeatFrequency(10, TimeUnit.SECONDS)
            )
            .retryWrites(true)
            .retryReads(true)
            .readPreference(ReadPreference.primaryPreferred())
            .writeConcern(WriteConcern.W1)
            .build();
        
        mongoClient = MongoClients.create(settings);
        database = mongoClient.getDatabase(databaseName);
        
        // Configure codec registry for optimal serialization
        codecRegistry = CodecRegistries.fromRegistries(
            CodecRegistries.fromCodecs(new ConfigDocumentCodec()),
            MongoClientSettings.getDefaultCodecRegistry()
        );
    }
    
    public MongoCollection<Document> getCollection(String collectionName) {
        return database.getCollection(collectionName).withCodecRegistry(codecRegistry);
    }
}
```

## 💾 Advanced Caching Strategies

### Multi-Level Cache System

```java
public class MultiLevelCache<K, V> {
    
    private final Cache<K, V> l1Cache; // Fast in-memory cache
    private final Cache<K, V> l2Cache; // Distributed cache (Redis/MongoDB)
    private final CacheLoader<K, V> loader;
    private final ExecutorService asyncLoader;
    
    public MultiLevelCache(CacheLoader<K, V> loader) {
        this.loader = loader;
        this.asyncLoader = Executors.newCachedThreadPool();
        
        // L1 Cache - Caffeine for high-performance in-memory caching
        l1Cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .removalListener(this::onL1Removal)
            .build();
        
        // L2 Cache - MongoDB-based distributed cache
        l2Cache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();
    }
    
    public V get(K key) {
        // Try L1 cache first
        V value = l1Cache.getIfPresent(key);
        if (value != null) {
            return value;
        }
        
        // Try L2 cache
        value = l2Cache.getIfPresent(key);
        if (value != null) {
            // Promote to L1
            l1Cache.put(key, value);
            return value;
        }
        
        // Load from source
        try {
            value = loader.load(key);
            if (value != null) {
                put(key, value);
            }
            return value;
        } catch (Exception e) {
            // Log error
            return null;
        }
    }
    
    public void put(K key, V value) {
        l1Cache.put(key, value);
        l2Cache.put(key, value);
        
        // Async persistence to L2 storage
        asyncLoader.submit(() -> persistToL2Storage(key, value));
    }
    
    public void invalidate(K key) {
        l1Cache.invalidate(key);
        l2Cache.invalidate(key);
        asyncLoader.submit(() -> removeFromL2Storage(key));
    }
    
    private void onL1Removal(K key, V value, RemovalCause cause) {
        // Handle L1 cache eviction
        if (cause == RemovalCause.EXPIRED) {
            // Keep in L2 if still valid
        }
    }
    
    private void persistToL2Storage(K key, V value) {
        // Persist to MongoDB or Redis
        try {
            // Implementation depends on your L2 storage
        } catch (Exception e) {
            // Log error
        }
    }
    
    private void removeFromL2Storage(K key) {
        // Remove from L2 storage
    }
    
    public void shutdown() {
        asyncLoader.shutdown();
        try {
            if (!asyncLoader.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncLoader.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncLoader.shutdownNow();
        }
    }
}
```

### Cache Key Strategies

```java
public class CacheKey {
    
    public static String create(Class<?> clazz, String id) {
        return clazz.getSimpleName() + ":" + id;
    }
    
    public static String create(Class<?> clazz, String field, Object value) {
        return clazz.getSimpleName() + ":" + field + ":" + value;
    }
    
    public static String createQuery(Class<?> clazz, Map<String, Object> query) {
        StringBuilder key = new StringBuilder(clazz.getSimpleName() + ":query:");
        query.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> key.append(entry.getKey()).append("=").append(entry.getValue()).append(";"));
        return key.toString();
    }
    
    public static String createCollection(Class<?> clazz) {
        return clazz.getSimpleName() + ":collection";
    }
    
    public static String createIndex(Class<?> clazz, String indexName) {
        return clazz.getSimpleName() + ":index:" + indexName;
    }
}
```

## 🔄 Change Stream Optimization

### Optimized Change Stream Handler

```java
public class OptimizedChangeStreamHandler {
    
    private final MongoClient mongoClient;
    private final String databaseName;
    private final Map<String, ChangeStreamIterable<Document>> activeStreams = new ConcurrentHashMap<>();
    private final ExecutorService streamProcessor;
    private final Map<String, Consumer<ChangeStreamDocument<Document>>> listeners = new ConcurrentHashMap<>();
    
    public OptimizedChangeStreamHandler(MongoClient mongoClient, String databaseName) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
        this.streamProcessor = Executors.newFixedThreadPool(4);
        
        // Configure thread pool for change stream processing
        ThreadPoolExecutor executor = (ThreadPoolExecutor) streamProcessor;
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    }
    
    public void watchCollection(String collectionName, Consumer<ChangeStreamDocument<Document>> listener) {
        watchCollection(collectionName, listener, null);
    }
    
    public void watchCollection(String collectionName, Consumer<ChangeStreamDocument<Document>> listener, 
                              Bson filter) {
        String key = collectionName + (filter != null ? ":" + filter.toString() : "");
        listeners.put(key, listener);
        
        if (activeStreams.containsKey(key)) {
            return; // Already watching
        }
        
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        MongoCollection<Document> collection = database.getCollection(collectionName);
        
        ChangeStreamIterable<Document> changeStream = collection.watch();
        
        if (filter != null) {
            changeStream = changeStream.filter(filter);
        }
        
        // Configure change stream for optimal performance
        changeStream = changeStream
            .fullDocument(FullDocument.UPDATE_LOOKUP)
            .maxAwaitTime(1, TimeUnit.SECONDS)
            .batchSize(100);
        
        activeStreams.put(key, changeStream);
        
        // Start processing in background
        streamProcessor.submit(() -> processChangeStream(key, changeStream));
    }
    
    private void processChangeStream(String key, ChangeStreamIterable<Document> changeStream) {
        try (MongoCursor<ChangeStreamDocument<Document>> cursor = changeStream.iterator()) {
            while (cursor.hasNext()) {
                ChangeStreamDocument<Document> change = cursor.next();
                
                Consumer<ChangeStreamDocument<Document>> listener = listeners.get(key);
                if (listener != null) {
                    // Process change asynchronously to avoid blocking
                    streamProcessor.submit(() -> {
                        try {
                            listener.accept(change);
                        } catch (Exception e) {
                            // Log error and continue processing
                        }
                    });
                }
            }
        } catch (Exception e) {
            // Log error and attempt to restart stream
            restartChangeStream(key);
        }
    }
    
    private void restartChangeStream(String key) {
        activeStreams.remove(key);
        
        // Wait before restarting to avoid tight loops
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        // Extract collection name from key
        String collectionName = key.split(":")[0];
        Consumer<ChangeStreamDocument<Document>> listener = listeners.get(key);
        
        if (listener != null) {
            // Restart the stream
            watchCollection(collectionName, listener);
        }
    }
    
    public void stopWatching(String collectionName) {
        String key = collectionName;
        activeStreams.remove(key);
        listeners.remove(key);
    }
    
    public void shutdown() {
        activeStreams.clear();
        listeners.clear();
        streamProcessor.shutdown();
        
        try {
            if (!streamProcessor.awaitTermination(10, TimeUnit.SECONDS)) {
                streamProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            streamProcessor.shutdownNow();
        }
    }
}
```

## 📈 Bulk Operations

### Bulk Write Optimization

```java
public class BulkOperationManager {
    
    private final MongoCollection<Document> collection;
    private final int batchSize;
    private final Map<String, List<WriteModel<Document>>> pendingOperations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService batchScheduler;
    
    public BulkOperationManager(MongoCollection<Document> collection, int batchSize) {
        this.collection = collection;
        this.batchSize = batchSize;
        this.batchScheduler = Executors.newScheduledThreadPool(2);
        
        // Schedule periodic batch processing
        batchScheduler.scheduleAtFixedRate(this::processPendingBatches, 1, 1, TimeUnit.SECONDS);
    }
    
    public void addOperation(String batchKey, WriteModel<Document> operation) {
        List<WriteModel<Document>> operations = pendingOperations.computeIfAbsent(batchKey, 
            k -> Collections.synchronizedList(new ArrayList<>()));
        
        operations.add(operation);
        
        // Process immediately if batch is full
        if (operations.size() >= batchSize) {
            processBatch(batchKey, operations);
        }
    }
    
    public void addInsert(String batchKey, Document document) {
        addOperation(batchKey, new InsertOneModel<>(document));
    }
    
    public void addUpdate(String batchKey, Bson filter, Bson update) {
        addOperation(batchKey, new UpdateOneModel<>(filter, update));
    }
    
    public void addDelete(String batchKey, Bson filter) {
        addOperation(batchKey, new DeleteOneModel<>(filter));
    }
    
    private void processPendingBatches() {
        for (Map.Entry<String, List<WriteModel<Document>>> entry : pendingOperations.entrySet()) {
            String batchKey = entry.getKey();
            List<WriteModel<Document>> operations = entry.getValue();
            
            if (!operations.isEmpty()) {
                processBatch(batchKey, operations);
            }
        }
    }
    
    private void processBatch(String batchKey, List<WriteModel<Document>> operations) {
        synchronized (operations) {
            if (operations.isEmpty()) return;
            
            List<WriteModel<Document>> batch = new ArrayList<>(operations);
            operations.clear();
            
            try {
                BulkWriteResult result = collection.bulkWrite(batch, 
                    new BulkWriteOptions().ordered(false));
                
                // Log results
                System.out.println("Processed batch " + batchKey + ": " + 
                    result.getInsertedCount() + " inserted, " + 
                    result.getModifiedCount() + " modified, " + 
                    result.getDeletedCount() + " deleted");
                
            } catch (Exception e) {
                // Log error and handle retry logic
                System.err.println("Failed to process batch " + batchKey + ": " + e.getMessage());
                
                // Re-queue failed operations
                operations.addAll(batch);
            }
        }
    }
    
    public void flushBatch(String batchKey) {
        List<WriteModel<Document>> operations = pendingOperations.get(batchKey);
        if (operations != null) {
            processBatch(batchKey, operations);
        }
    }
    
    public void shutdown() {
        // Process all remaining batches
        processPendingBatches();
        
        batchScheduler.shutdown();
        try {
            if (!batchScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                batchScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchScheduler.shutdownNow();
        }
    }
}
```

## 📊 Performance Monitoring

### Metrics Collector

```java
public class PerformanceMetrics {
    
    private final Map<String, AtomicLong> operationCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> operationTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService metricsReporter;
    
    public PerformanceMetrics() {
        this.metricsReporter = Executors.newScheduledThreadPool(1);
        
        // Report metrics every minute
        metricsReporter.scheduleAtFixedRate(this::reportMetrics, 1, 1, TimeUnit.MINUTES);
    }
    
    public <T> T measure(String operationName, Supplier<T> operation) {
        long startTime = System.nanoTime();
        
        try {
            T result = operation.get();
            
            long duration = System.nanoTime() - startTime;
            recordOperation(operationName, duration);
            
            return result;
            
        } catch (Exception e) {
            recordError(operationName);
            throw e;
        }
    }
    
    public void measure(String operationName, Runnable operation) {
        measure(operationName, () -> {
            operation.run();
            return null;
        });
    }
    
    private void recordOperation(String operationName, long durationNs) {
        operationCounts.computeIfAbsent(operationName, k -> new AtomicLong()).incrementAndGet();
        operationTimes.computeIfAbsent(operationName, k -> new AtomicLong()).addAndGet(durationNs);
    }
    
    private void recordError(String operationName) {
        errorCounts.computeIfAbsent(operationName, k -> new AtomicLong()).incrementAndGet();
    }
    
    private void reportMetrics() {
        System.out.println("=== Performance Metrics Report ===");
        
        for (String operation : operationCounts.keySet()) {
            long count = operationCounts.get(operation).get();
            long totalTime = operationTimes.get(operation).get();
            long errors = errorCounts.getOrDefault(operation, new AtomicLong(0)).get();
            
            double avgTimeMs = (double) totalTime / count / 1_000_000;
            double errorRate = (double) errors / count * 100;
            
            System.out.printf("%s: %d ops, %.2f ms avg, %.2f%% errors%n", 
                operation, count, avgTimeMs, errorRate);
        }
        
        // Reset counters
        operationCounts.clear();
        operationTimes.clear();
        errorCounts.clear();
    }
    
    public Map<String, Double> getAverageResponseTimes() {
        Map<String, Double> averages = new HashMap<>();
        
        for (String operation : operationCounts.keySet()) {
            long count = operationCounts.get(operation).get();
            long totalTime = operationTimes.get(operation).get();
            
            if (count > 0) {
                double avgTimeMs = (double) totalTime / count / 1_000_000;
                averages.put(operation, avgTimeMs);
            }
        }
        
        return averages;
    }
    
    public void shutdown() {
        metricsReporter.shutdown();
        try {
            if (!metricsReporter.awaitTermination(5, TimeUnit.SECONDS)) {
                metricsReporter.shutdownNow();
            }
        } catch (InterruptedException e) {
            metricsReporter.shutdownNow();
        }
    }
}
```

### Database Profiler Integration

```java
public class DatabaseProfiler {
    
    private final MongoDatabase database;
    private final PerformanceMetrics metrics;
    
    public DatabaseProfiler(MongoDatabase database, PerformanceMetrics metrics) {
        this.database = database;
        this.metrics = metrics;
    }
    
    public void enableProfiling() {
        // Enable database profiling for slow operations
        Document profileCommand = new Document("profile", 2)
            .append("slowms", 100) // Log operations slower than 100ms
            .append("sampleRate", 0.1); // Sample 10% of operations
        
        database.runCommand(profileCommand);
    }
    
    public void analyzeSlowOperations() {
        MongoCollection<Document> systemProfile = database.getCollection("system.profile");
        
        // Find slow operations from the last hour
        long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
        
        FindIterable<Document> slowOps = systemProfile.find(
            Filters.gt("ts", new Date(oneHourAgo))
        ).sort(Sorts.descending("millis")).limit(10);
        
        System.out.println("=== Top 10 Slow Operations ===");
        for (Document op : slowOps) {
            System.out.printf("Operation: %s, Time: %d ms, Collection: %s%n",
                op.getString("op"),
                op.getInteger("millis"),
                op.getString("ns"));
        }
    }
    
    public void createPerformanceIndexes() {
        // Analyze common query patterns and create indexes
        MongoCollection<Document> configCollection = database.getCollection("configs");
        
        // Create compound index for common queries
        IndexModel compoundIndex = new IndexModel(
            Indexes.compoundIndex(
                Indexes.ascending("className"),
                Indexes.ascending("lastModified")
            ),
            new IndexOptions().name("class_modified_idx")
        );
        
        configCollection.createIndex(compoundIndex);
        
        // Create text index for search operations
        IndexModel textIndex = new IndexModel(
            Indexes.text("content"),
            new IndexOptions().name("content_text_idx")
        );
        
        configCollection.createIndex(textIndex);
    }
    
    public void monitorConnectionPool() {
        // Get connection pool statistics
        Document serverStatus = database.runCommand(new Document("serverStatus", 1));
        Document connections = (Document) serverStatus.get("connections");
        
        if (connections != null) {
            System.out.printf("Active connections: %d, Available: %d, Total created: %d%n",
                connections.getInteger("active", 0),
                connections.getInteger("available", 0),
                connections.getInteger("totalCreated", 0));
        }
    }
}
```

## 🔧 Query Optimization

### Index Management

```java
public class IndexManager {
    
    private final MongoDatabase database;
    private final Map<String, Set<String>> collectionIndexes = new ConcurrentHashMap<>();
    
    public IndexManager(MongoDatabase database) {
        this.database = database;
        loadExistingIndexes();
    }
    
    private void loadExistingIndexes() {
        for (String collectionName : database.listCollectionNames()) {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            Set<String> indexes = new HashSet<>();
            
            for (Document index : collection.listIndexes()) {
                String indexName = index.getString("name");
                if (indexName != null) {
                    indexes.add(indexName);
                }
            }
            
            collectionIndexes.put(collectionName, indexes);
        }
    }
    
    public void ensureIndex(String collectionName, IndexModel indexModel) {
        String indexName = indexModel.getOptions().getName();
        Set<String> indexes = collectionIndexes.computeIfAbsent(collectionName, k -> new HashSet<>());
        
        if (!indexes.contains(indexName)) {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            collection.createIndex(indexModel);
            indexes.add(indexName);
        }
    }
    
    public void createOptimizedIndexes(Class<?> configClass) {
        String collectionName = getCollectionName(configClass);
        
        // Create indexes based on common query patterns
        ensureIndex(collectionName, new IndexModel(
            Indexes.ascending("lastModified"),
            new IndexOptions().name("lastModified_idx")
        ));
        
        ensureIndex(collectionName, new IndexModel(
            Indexes.ascending("playerId"),
            new IndexOptions().name("playerId_idx")
        ));
        
        // Create compound indexes for complex queries
        ensureIndex(collectionName, new IndexModel(
            Indexes.compoundIndex(
                Indexes.ascending("type"),
                Indexes.ascending("enabled")
            ),
            new IndexOptions().name("type_enabled_idx")
        ));
    }
    
    public void analyzeQueryPerformance(String collectionName, Bson filter) {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        
        // Use explain to analyze query performance
        Document explainResult = collection.find(filter)
            .explain(Verbosity.EXECUTION_STATS);
        
        Document executionStats = (Document) explainResult.get("executionStats");
        
        if (executionStats != null) {
            int totalDocsExamined = executionStats.getInteger("totalDocsExamined", 0);
            int totalDocsReturned = executionStats.getInteger("totalDocsReturned", 0);
            long executionTimeMillis = executionStats.getLong("executionTimeMillis");
            
            System.out.printf("Query analysis for %s:%n", collectionName);
            System.out.printf("  Documents examined: %d%n", totalDocsExamined);
            System.out.printf("  Documents returned: %d%n", totalDocsReturned);
            System.out.printf("  Execution time: %d ms%n", executionTimeMillis);
            
            if (totalDocsExamined > totalDocsReturned * 10) {
                System.out.println("  WARNING: Inefficient query - consider adding indexes");
            }
        }
    }
    
    private String getCollectionName(Class<?> configClass) {
        // Extract collection name from annotations
        ConfigsCollection collectionAnnotation = configClass.getAnnotation(ConfigsCollection.class);
        if (collectionAnnotation != null) {
            return collectionAnnotation.collection();
        }
        return configClass.getSimpleName().toLowerCase();
    }
}
```

## 🚀 Async Operations

### Async ConfigManager

```java
public class AsyncConfigManager {
    
    private final ConfigManager syncManager;
    private final ExecutorService asyncExecutor;
    private final Map<String, CompletableFuture<?>> pendingOperations = new ConcurrentHashMap<>();
    
    public AsyncConfigManager(ConfigManager syncManager) {
        this.syncManager = syncManager;
        this.asyncExecutor = Executors.newCachedThreadPool();
    }
    
    public <T> CompletableFuture<T> getAsync(Class<T> clazz, String id) {
        String operationKey = "get:" + clazz.getSimpleName() + ":" + id;
        
        return pendingOperations.computeIfAbsent(operationKey, k -> 
            CompletableFuture.supplyAsync(() -> syncManager.get(clazz, id), asyncExecutor)
                .whenComplete((result, throwable) -> pendingOperations.remove(k))
        );
    }
    
    public <T> CompletableFuture<Void> saveAsync(T config) {
        String operationKey = "save:" + config.getClass().getSimpleName() + ":" + getId(config);
        
        return pendingOperations.computeIfAbsent(operationKey, k -> 
            CompletableFuture.runAsync(() -> syncManager.save(config), asyncExecutor)
                .whenComplete((result, throwable) -> pendingOperations.remove(k))
        );
    }
    
    public <T> CompletableFuture<List<T>> getAllAsync(Class<T> clazz) {
        String operationKey = "getAll:" + clazz.getSimpleName();
        
        return pendingOperations.computeIfAbsent(operationKey, k -> 
            CompletableFuture.supplyAsync(() -> syncManager.getAll(clazz), asyncExecutor)
                .whenComplete((result, throwable) -> pendingOperations.remove(k))
        );
    }
    
    public <T> CompletableFuture<Void> deleteAsync(Class<T> clazz, String id) {
        String operationKey = "delete:" + clazz.getSimpleName() + ":" + id;
        
        return pendingOperations.computeIfAbsent(operationKey, k -> 
            CompletableFuture.runAsync(() -> syncManager.delete(clazz, id), asyncExecutor)
                .whenComplete((result, throwable) -> pendingOperations.remove(k))
        );
    }
    
    public CompletableFuture<Void> flushAsync() {
        return CompletableFuture.runAsync(() -> {
            // Wait for all pending operations to complete
            CompletableFuture.allOf(pendingOperations.values().toArray(new CompletableFuture[0]))
                .join();
        }, asyncExecutor);
    }
    
    private String getId(Object config) {
        // Extract ID from the config object
        // This depends on your ID field naming convention
        try {
            java.lang.reflect.Field idField = config.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            return String.valueOf(idField.get(config));
        } catch (Exception e) {
            return config.hashCode() + "";
        }
    }
    
    public void shutdown() {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
        }
    }
}
```

## 📈 Performance Benchmarks

### Benchmark Runner

```java
public class PerformanceBenchmark {
    
    private final ConfigManager configManager;
    private final PerformanceMetrics metrics;
    private final int iterations;
    
    public PerformanceBenchmark(ConfigManager configManager, int iterations) {
        this.configManager = configManager;
        this.metrics = new PerformanceMetrics();
        this.iterations = iterations;
    }
    
    public void runBenchmarks() {
        System.out.println("Running performance benchmarks...");
        
        benchmarkSaveOperations();
        benchmarkGetOperations();
        benchmarkBulkOperations();
        benchmarkChangeStreams();
        
        System.out.println("Benchmarks completed.");
        metrics.reportMetrics();
    }
    
    private void benchmarkSaveOperations() {
        System.out.println("Benchmarking save operations...");
        
        for (int i = 0; i < iterations; i++) {
            TestConfig config = new TestConfig();
            config.setId("test_" + i);
            config.setName("Test Config " + i);
            config.setValue(i);
            
            metrics.measure("save_operation", () -> configManager.save(config));
        }
    }
    
    private void benchmarkGetOperations() {
        System.out.println("Benchmarking get operations...");
        
        for (int i = 0; i < iterations; i++) {
            String id = "test_" + (i % 100); // Reuse some configs for cache testing
            
            metrics.measure("get_operation", () -> configManager.get(TestConfig.class, id));
        }
    }
    
    private void benchmarkBulkOperations() {
        System.out.println("Benchmarking bulk operations...");
        
        List<TestConfig> configs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            TestConfig config = new TestConfig();
            config.setId("bulk_test_" + i);
            config.setName("Bulk Test Config " + i);
            config.setValue(i);
            configs.add(config);
        }
        
        metrics.measure("bulk_save_operation", () -> {
            for (TestConfig config : configs) {
                configManager.save(config);
            }
        });
    }
    
    private void benchmarkChangeStreams() {
        System.out.println("Benchmarking change streams...");
        
        AtomicInteger changeCount = new AtomicInteger(0);
        
        configManager.watchCollection(TestConfig.class, change -> {
            changeCount.incrementAndGet();
        });
        
        // Perform some operations to trigger changes
        for (int i = 0; i < iterations / 10; i++) {
            TestConfig config = new TestConfig();
            config.setId("stream_test_" + i);
            config.setName("Stream Test Config " + i);
            config.setValue(i);
            
            configManager.save(config);
        }
        
        // Wait a bit for change streams to process
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // Ignore
        }
        
        System.out.println("Change stream processed " + changeCount.get() + " changes");
    }
    
    @ConfigsFileProperties
    @ConfigsDatabase(database = "benchmark_db")
    @ConfigsCollection(collection = "test_configs")
    public static class TestConfig {
        
        @ConfigsField
        private String id;
        
        @ConfigsField
        private String name;
        
        @ConfigsField
        private int value;
        
        @ConfigsField
        private long timestamp = System.currentTimeMillis();
        
        // Getters and setters...
    }
}
```

---

*Next: Learn about [[Plugin Integration]] for seamless integration with Minecraft plugins.*