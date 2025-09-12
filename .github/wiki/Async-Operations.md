# Async Operations

Non-blocking configuration management with CompletableFuture for high-performance async operations, including batch processing and concurrent execution.

## ⚡ Async Operations Overview

The Async Operations system provides non-blocking configuration management using Java's CompletableFuture, enabling high-performance concurrent operations and batch processing.

## 📋 Core Async Implementation

### AsyncConfigManager

```java
public class AsyncConfigManager {
    
    private final ConfigManager syncConfigManager;
    private final ExecutorService asyncExecutor;
    private final ExecutorService batchExecutor;
    private final Map<String, CompletableFuture<?>> pendingOperations = new ConcurrentHashMap<>();
    private final AtomicLong operationCounter = new AtomicLong(0);
    
    public AsyncConfigManager(ConfigManager syncConfigManager, int threadPoolSize) {
        this.syncConfigManager = syncConfigManager;
        this.asyncExecutor = Executors.newFixedThreadPool(threadPoolSize);
        this.batchExecutor = Executors.newSingleThreadExecutor();
    }
    
    // Async Get Operations
    public <T> CompletableFuture<T> getAsync(Class<T> type, String key) {
        String operationId = generateOperationId("get", key);
        
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return syncConfigManager.get(type, key);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
        
        pendingOperations.put(operationId, future);
        future.whenComplete((result, error) -> pendingOperations.remove(operationId));
        
        return future;
    }
    
    public <T> CompletableFuture<T> getAsync(Class<T> type, String key, T defaultValue) {
        return getAsync(type, key).exceptionally(error -> {
            MongoConfigsPlugin.getInstance().getLogger()
                .warning("Using default value for key '" + key + "': " + error.getMessage());
            return defaultValue;
        });
    }
    
    public <T> CompletableFuture<List<T>> getListAsync(Class<T> type, String key) {
        String operationId = generateOperationId("getList", key);
        
        CompletableFuture<List<T>> future = CompletableFuture.supplyAsync(() -> {
            try {
                return syncConfigManager.getList(type, key);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
        
        pendingOperations.put(operationId, future);
        future.whenComplete((result, error) -> pendingOperations.remove(operationId));
        
        return future;
    }
    
    // Async Save Operations
    public <T> CompletableFuture<Void> saveAsync(T config) {
        String operationId = generateOperationId("save", config.getClass().getSimpleName());
        
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                syncConfigManager.save(config);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
        
        pendingOperations.put(operationId, future);
        future.whenComplete((result, error) -> pendingOperations.remove(operationId));
        
        return future;
    }
    
    public <T> CompletableFuture<Void> saveAsync(T config, String collection) {
        String operationId = generateOperationId("save", collection);
        
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                syncConfigManager.save(config, collection);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
        
        pendingOperations.put(operationId, future);
        future.whenComplete((result, error) -> pendingOperations.remove(operationId));
        
        return future;
    }
    
    // Async Delete Operations
    public CompletableFuture<Boolean> deleteAsync(String key) {
        String operationId = generateOperationId("delete", key);
        
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                return syncConfigManager.delete(key);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
        
        pendingOperations.put(operationId, future);
        future.whenComplete((result, error) -> pendingOperations.remove(operationId));
        
        return future;
    }
    
    public CompletableFuture<Boolean> deleteAsync(Class<?> type, String key) {
        String operationId = generateOperationId("delete", type.getSimpleName() + ":" + key);
        
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                return syncConfigManager.delete(type, key);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
        
        pendingOperations.put(operationId, future);
        future.whenComplete((result, error) -> pendingOperations.remove(operationId));
        
        return future;
    }
    
    // Async Exists Operations
    public CompletableFuture<Boolean> existsAsync(String key) {
        String operationId = generateOperationId("exists", key);
        
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                return syncConfigManager.exists(key);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
        
        pendingOperations.put(operationId, future);
        future.whenComplete((result, error) -> pendingOperations.remove(operationId));
        
        return future;
    }
    
    public CompletableFuture<Boolean> existsAsync(Class<?> type, String key) {
        String operationId = generateOperationId("exists", type.getSimpleName() + ":" + key);
        
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                return syncConfigManager.exists(type, key);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
        
        pendingOperations.put(operationId, future);
        future.whenComplete((result, error) -> pendingOperations.remove(operationId));
        
        return future;
    }
    
    // Batch Operations
    public <T> CompletableFuture<List<T>> getBatchAsync(List<String> keys, Class<T> type) {
        String operationId = generateOperationId("batchGet", keys.size() + " keys");
        
        CompletableFuture<List<T>> future = CompletableFuture.supplyAsync(() -> {
            try {
                List<CompletableFuture<T>> futures = keys.stream()
                    .map(key -> getAsync(type, key))
                    .collect(Collectors.toList());
                
                return futures.stream()
                    .map(f -> f.join()) // Wait for all to complete
                    .collect(Collectors.toList());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, batchExecutor);
        
        pendingOperations.put(operationId, future);
        future.whenComplete((result, error) -> pendingOperations.remove(operationId));
        
        return future;
    }
    
    public <T> CompletableFuture<Void> saveBatchAsync(List<T> configs) {
        String operationId = generateOperationId("batchSave", configs.size() + " items");
        
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                // Use MongoDB bulk operations for efficiency
                syncConfigManager.saveBatch(configs);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, batchExecutor);
        
        pendingOperations.put(operationId, future);
        future.whenComplete((result, error) -> pendingOperations.remove(operationId));
        
        return future;
    }
    
    // Utility Methods
    private String generateOperationId(String operation, String key) {
        return operation + "_" + key + "_" + operationCounter.incrementAndGet();
    }
    
    public Map<String, CompletableFuture<?>> getPendingOperations() {
        return new HashMap<>(pendingOperations);
    }
    
    public int getPendingOperationCount() {
        return pendingOperations.size();
    }
    
    public boolean hasPendingOperations() {
        return !pendingOperations.isEmpty();
    }
    
    public CompletableFuture<Void> waitForAllPendingOperations() {
        if (pendingOperations.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        List<CompletableFuture<?>> futures = new ArrayList<>(pendingOperations.values());
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    public void cancelAllPendingOperations() {
        pendingOperations.values().forEach(future -> future.cancel(true));
        pendingOperations.clear();
    }
    
    public void shutdown() {
        cancelAllPendingOperations();
        
        asyncExecutor.shutdown();
        batchExecutor.shutdown();
        
        try {
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
            if (!batchExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            batchExecutor.shutdownNow();
        }
    }
}
```

### Async Translation Service

```java
public class AsyncTranslationService {
    
    private final TranslationService syncTranslationService;
    private final ExecutorService translationExecutor;
    private final Cache<String, CompletableFuture<String>> translationCache;
    private final Map<String, CompletableFuture<Map<String, String>>> batchTranslationCache;
    
    public AsyncTranslationService(TranslationService syncTranslationService, int threadPoolSize) {
        this.syncTranslationService = syncTranslationService;
        this.translationExecutor = Executors.newFixedThreadPool(threadPoolSize);
        this.translationCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();
        this.batchTranslationCache = new ConcurrentHashMap<>();
    }
    
    public CompletableFuture<String> translateAsync(String language, String key) {
        String cacheKey = language + ":" + key;
        
        return translationCache.get(cacheKey, k -> 
            CompletableFuture.supplyAsync(() -> {
                try {
                    return syncTranslationService.translate(language, key);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, translationExecutor)
        );
    }
    
    public CompletableFuture<String> translateAsync(String language, String key, Object... placeholders) {
        return translateAsync(language, key).thenApply(template -> {
            if (template == null) {
                return key; // Fallback to key if translation not found
            }
            
            try {
                return MessageFormat.format(template, placeholders);
            } catch (Exception e) {
                MongoConfigsPlugin.getInstance().getLogger()
                    .warning("Failed to format translation for key '" + key + "': " + e.getMessage());
                return template;
            }
        });
    }
    
    public CompletableFuture<String> translateAsync(String language, String key, Map<String, Object> placeholders) {
        return translateAsync(language, key).thenApply(template -> {
            if (template == null) {
                return key;
            }
            
            String result = template;
            for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                result = result.replace(placeholder, String.valueOf(entry.getValue()));
            }
            
            return result;
        });
    }
    
    public CompletableFuture<Map<String, String>> getTranslationsAsync(String language, List<String> keys) {
        String cacheKey = language + ":batch:" + keys.hashCode();
        
        return batchTranslationCache.computeIfAbsent(cacheKey, k -> 
            CompletableFuture.supplyAsync(() -> {
                try {
                    Map<String, String> translations = new HashMap<>();
                    for (String key : keys) {
                        String translation = syncTranslationService.translate(language, key);
                        if (translation != null) {
                            translations.put(key, translation);
                        }
                    }
                    return translations;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, translationExecutor)
                .whenComplete((result, error) -> batchTranslationCache.remove(cacheKey))
        );
    }
    
    public CompletableFuture<List<String>> translateBatchAsync(String language, List<String> keys) {
        return getTranslationsAsync(language, keys).thenApply(translations -> 
            keys.stream()
                .map(key -> translations.getOrDefault(key, key))
                .collect(Collectors.toList())
        );
    }
    
    public CompletableFuture<String> getPlayerLanguageAsync(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return syncTranslationService.getPlayerLanguage(playerId);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, translationExecutor);
    }
    
    public CompletableFuture<Void> setPlayerLanguageAsync(UUID playerId, String language) {
        return CompletableFuture.runAsync(() -> {
            try {
                syncTranslationService.setPlayerLanguage(playerId, language);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, translationExecutor);
    }
    
    public CompletableFuture<String> translateForPlayerAsync(UUID playerId, String key, Object... placeholders) {
        return getPlayerLanguageAsync(playerId).thenCompose(language -> 
            translateAsync(language, key, placeholders)
        );
    }
    
    public CompletableFuture<String> translateForPlayerAsync(UUID playerId, String key, Map<String, Object> placeholders) {
        return getPlayerLanguageAsync(playerId).thenCompose(language -> 
            translateAsync(language, key, placeholders)
        );
    }
    
    public void invalidateCache() {
        translationCache.invalidateAll();
        batchTranslationCache.clear();
    }
    
    public void invalidateCache(String language) {
        // Remove all entries for the specified language
        translationCache.asMap().keySet().removeIf(key -> key.startsWith(language + ":"));
        batchTranslationCache.keySet().removeIf(key -> key.startsWith(language + ":"));
    }
    
    public void invalidateCache(String language, String key) {
        String cacheKey = language + ":" + key;
        translationCache.invalidate(cacheKey);
    }
    
    public long getCacheSize() {
        return translationCache.estimatedSize();
    }
    
    public void shutdown() {
        translationExecutor.shutdown();
        try {
            if (!translationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                translationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            translationExecutor.shutdownNow();
        }
    }
}
```

## 🔄 Advanced Async Patterns

### Async Chain Operations

```java
public class AsyncChainOperations {
    
    private final AsyncConfigManager asyncConfigManager;
    private final AsyncTranslationService asyncTranslationService;
    
    public AsyncChainOperations(AsyncConfigManager asyncConfigManager, 
                              AsyncTranslationService asyncTranslationService) {
        this.asyncConfigManager = asyncConfigManager;
        this.asyncTranslationService = asyncTranslationService;
    }
    
    // Chain: Get config -> Translate message -> Send to player
    public CompletableFuture<Void> sendTranslatedConfigMessage(UUID playerId, String configKey, String messageKey) {
        return asyncConfigManager.getAsync(String.class, configKey)
            .thenCompose(configValue -> 
                asyncTranslationService.translateForPlayerAsync(playerId, messageKey, configValue)
            )
            .thenAccept(translatedMessage -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage(ColorHelper.parseComponent(translatedMessage));
                }
            })
            .exceptionally(error -> {
                MongoConfigsPlugin.getInstance().getLogger()
                    .warning("Failed to send translated config message: " + error.getMessage());
                return null;
            });
    }
    
    // Chain: Load user preferences -> Get language -> Translate UI elements
    public CompletableFuture<Map<String, String>> loadTranslatedUI(UUID playerId, List<String> uiKeys) {
        return asyncTranslationService.getPlayerLanguageAsync(playerId)
            .thenCompose(language -> 
                asyncTranslationService.getTranslationsAsync(language, uiKeys)
            )
            .thenApply(translations -> {
                // Fill in missing translations with defaults
                Map<String, String> result = new HashMap<>();
                for (String key : uiKeys) {
                    result.put(key, translations.getOrDefault(key, key));
                }
                return result;
            });
    }
    
    // Chain: Validate config -> Save if valid -> Update cache -> Notify subscribers
    public CompletableFuture<Void> saveAndNotify(String configKey, Object config, List<UUID> subscribers) {
        return asyncConfigManager.existsAsync(configKey)
            .thenCompose(exists -> {
                if (exists) {
                    return asyncConfigManager.saveAsync(config);
                } else {
                    throw new CompletionException(new IllegalArgumentException("Config key does not exist"));
                }
            })
            .thenRun(() -> {
                // Invalidate relevant caches
                asyncTranslationService.invalidateCache();
            })
            .thenRun(() -> {
                // Notify subscribers asynchronously
                notifySubscribersAsync(subscribers, configKey);
            })
            .exceptionally(error -> {
                MongoConfigsPlugin.getInstance().getLogger()
                    .severe("Failed to save and notify for key '" + configKey + "': " + error.getMessage());
                return null;
            });
    }
    
    // Chain: Batch load configs -> Process in parallel -> Aggregate results
    public CompletableFuture<Map<String, Object>> loadAndProcessBatch(List<String> configKeys, 
                                                                     Function<Object, Object> processor) {
        List<CompletableFuture<Map.Entry<String, Object>>> futures = configKeys.stream()
            .map(key -> 
                asyncConfigManager.getAsync(Object.class, key)
                    .thenApply(value -> new AbstractMap.SimpleEntry<>(key, value))
                    .exceptionally(error -> {
                        MongoConfigsPlugin.getInstance().getLogger()
                            .warning("Failed to load config '" + key + "': " + error.getMessage());
                        return new AbstractMap.SimpleEntry<>(key, null);
                    })
            )
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> 
                futures.stream()
                    .map(CompletableFuture::join)
                    .filter(entry -> entry.getValue() != null)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> processor.apply(entry.getValue())
                    ))
            );
    }
    
    // Chain: Get user data -> Check permissions -> Perform action -> Log result
    public CompletableFuture<Void> performSecuredAction(UUID playerId, String action, Runnable securedAction) {
        return asyncConfigManager.getAsync(PlayerPermissions.class, "permissions:" + playerId)
            .thenCompose(permissions -> {
                if (permissions == null) {
                    throw new CompletionException(new SecurityException("No permissions found for player"));
                }
                
                if (!permissions.hasPermission(action)) {
                    throw new CompletionException(new SecurityException("Player lacks permission: " + action));
                }
                
                return CompletableFuture.runAsync(securedAction);
            })
            .thenRun(() -> {
                // Log successful action
                MongoConfigsPlugin.getInstance().getLogger()
                    .info("Player " + playerId + " performed action: " + action);
            })
            .exceptionally(error -> {
                // Log failed action
                MongoConfigsPlugin.getInstance().getLogger()
                    .warning("Player " + playerId + " failed to perform action '" + action + "': " + error.getMessage());
                return null;
            });
    }
    
    private void notifySubscribersAsync(List<UUID> subscribers, String configKey) {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        for (UUID subscriberId : subscribers) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player subscriber = plugin.getServer().getPlayer(subscriberId);
                if (subscriber != null && subscriber.isOnline()) {
                    String message = plugin.getMessage(subscriber, "config.updated", configKey);
                    subscriber.sendMessage(ColorHelper.parseComponent(message));
                }
            });
        }
    }
}
```

### Async Batch Processor

```java
public class AsyncBatchProcessor {
    
    private final ExecutorService batchExecutor;
    private final int batchSize;
    private final long batchTimeoutMs;
    private final Map<String, BatchOperation<?>> pendingBatches = new ConcurrentHashMap<>();
    
    public AsyncBatchProcessor(int threadPoolSize, int batchSize, long batchTimeoutMs) {
        this.batchExecutor = Executors.newFixedThreadPool(threadPoolSize);
        this.batchSize = batchSize;
        this.batchTimeoutMs = batchTimeoutMs;
    }
    
    public <T, R> CompletableFuture<List<R>> processBatchAsync(List<T> items, 
                                                             Function<List<T>, List<R>> processor) {
        if (items.size() <= batchSize) {
            // Process directly if within batch size
            return CompletableFuture.supplyAsync(() -> processor.apply(items), batchExecutor);
        }
        
        // Split into batches
        List<List<T>> batches = splitIntoBatches(items, batchSize);
        
        List<CompletableFuture<List<R>>> batchFutures = batches.stream()
            .map(batch -> CompletableFuture.supplyAsync(() -> processor.apply(batch), batchExecutor))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> 
                batchFutures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .collect(Collectors.toList())
            );
    }
    
    public <T> CompletableFuture<Void> processBatchAsync(List<T> items, Consumer<List<T>> processor) {
        return processBatchAsync(items, batch -> {
            processor.accept(batch);
            return Collections.singletonList((Void) null);
        }).thenAccept(v -> {});
    }
    
    public <T> BatchOperation<T> createBatchOperation(String operationId) {
        BatchOperation<T> batchOp = new BatchOperation<>(operationId, batchSize, batchTimeoutMs);
        pendingBatches.put(operationId, batchOp);
        return batchOp;
    }
    
    public <T> CompletableFuture<List<T>> submitBatchOperation(String operationId, 
                                                             Function<List<T>, List<T>> processor) {
        @SuppressWarnings("unchecked")
        BatchOperation<T> batchOp = (BatchOperation<T>) pendingBatches.get(operationId);
        
        if (batchOp == null) {
            throw new IllegalArgumentException("Batch operation not found: " + operationId);
        }
        
        return batchOp.processAsync(processor)
            .whenComplete((result, error) -> pendingBatches.remove(operationId));
    }
    
    private <T> List<List<T>> splitIntoBatches(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, items.size());
            batches.add(items.subList(i, endIndex));
        }
        return batches;
    }
    
    public void shutdown() {
        // Cancel all pending batches
        pendingBatches.values().forEach(BatchOperation::cancel);
        pendingBatches.clear();
        
        batchExecutor.shutdown();
        try {
            if (!batchExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchExecutor.shutdownNow();
        }
    }
    
    public static class BatchOperation<T> {
        
        private final String operationId;
        private final int maxBatchSize;
        private final long timeoutMs;
        private final List<T> items = Collections.synchronizedList(new ArrayList<>());
        private final AtomicBoolean processing = new AtomicBoolean(false);
        private volatile long lastAddTime = System.currentTimeMillis();
        
        public BatchOperation(String operationId, int maxBatchSize, long timeoutMs) {
            this.operationId = operationId;
            this.maxBatchSize = maxBatchSize;
            this.timeoutMs = timeoutMs;
        }
        
        public boolean addItem(T item) {
            if (processing.get()) {
                return false; // Can't add while processing
            }
            
            items.add(item);
            lastAddTime = System.currentTimeMillis();
            
            return items.size() >= maxBatchSize;
        }
        
        public <R> CompletableFuture<List<R>> processAsync(Function<List<T>, List<R>> processor) {
            if (!processing.compareAndSet(false, true)) {
                throw new IllegalStateException("Batch operation already processing");
            }
            
            return CompletableFuture.supplyAsync(() -> {
                try {
                    List<T> itemsToProcess = new ArrayList<>(items);
                    items.clear();
                    
                    MongoConfigsPlugin.getInstance().getLogger()
                        .info("Processing batch operation '" + operationId + "' with " + 
                              itemsToProcess.size() + " items");
                    
                    return processor.apply(itemsToProcess);
                } finally {
                    processing.set(false);
                }
            });
        }
        
        public boolean isReady() {
            return items.size() >= maxBatchSize || 
                   (items.size() > 0 && System.currentTimeMillis() - lastAddTime > timeoutMs);
        }
        
        public int getCurrentSize() {
            return items.size();
        }
        
        public void cancel() {
            processing.set(true); // Prevent further processing
            items.clear();
        }
        
        public String getOperationId() {
            return operationId;
        }
    }
}
```

## 🔧 Integration Examples

### Async Command Handler

```java
public class AsyncCommandHandler implements CommandExecutor {
    
    private final AsyncConfigManager asyncConfigManager;
    private final AsyncTranslationService asyncTranslationService;
    private final AsyncChainOperations chainOperations;
    
    public AsyncCommandHandler(MongoConfigsPlugin plugin) {
        this.asyncConfigManager = plugin.getAsyncConfigManager();
        this.asyncTranslationService = plugin.getAsyncTranslationService();
        this.chainOperations = plugin.getAsyncChainOperations();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "get":
                return handleGetAsync(player, Arrays.copyOfRange(args, 1, args.length));
            case "set":
                return handleSetAsync(player, Arrays.copyOfRange(args, 1, args.length));
            case "translate":
                return handleTranslateAsync(player, Arrays.copyOfRange(args, 1, args.length));
            case "batch":
                return handleBatchAsync(player, Arrays.copyOfRange(args, 1, args.length));
            default:
                sendUsage(player);
                return true;
        }
    }
    
    private boolean handleGetAsync(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ColorHelper.parseComponent("&cUsage: /async get <key>"));
            return true;
        }
        
        String key = args[0];
        
        asyncConfigManager.getAsync(String.class, key)
            .thenAccept(value -> {
                if (value != null) {
                    String message = MongoConfigsPlugin.getInstance()
                        .getMessage(player, "config.value", key, value);
                    player.sendMessage(ColorHelper.parseComponent(message));
                } else {
                    String message = MongoConfigsPlugin.getInstance()
                        .getMessage(player, "config.not_found", key);
                    player.sendMessage(ColorHelper.parseComponent(message));
                }
            })
            .exceptionally(error -> {
                String message = MongoConfigsPlugin.getInstance()
                    .getMessage(player, "config.error", error.getMessage());
                player.sendMessage(ColorHelper.parseComponent(message));
                return null;
            });
        
        return true;
    }
    
    private boolean handleSetAsync(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorHelper.parseComponent("&cUsage: /async set <key> <value>"));
            return true;
        }
        
        String key = args[0];
        String value = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        asyncConfigManager.saveAsync(value)
            .thenRun(() -> {
                String message = MongoConfigsPlugin.getInstance()
                    .getMessage(player, "config.saved", key);
                player.sendMessage(ColorHelper.parseComponent(message));
            })
            .exceptionally(error -> {
                String message = MongoConfigsPlugin.getInstance()
                    .getMessage(player, "config.save_error", error.getMessage());
                player.sendMessage(ColorHelper.parseComponent(message));
                return null;
            });
        
        return true;
    }
    
    private boolean handleTranslateAsync(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ColorHelper.parseComponent("&cUsage: /async translate <key> [placeholders...]"));
            return true;
        }
        
        String key = args[0];
        Object[] placeholders = Arrays.copyOfRange(args, 1, args.length);
        
        asyncTranslationService.translateForPlayerAsync(player.getUniqueId(), key, placeholders)
            .thenAccept(translation -> {
                player.sendMessage(ColorHelper.parseComponent("&aTranslation: &f" + translation));
            })
            .exceptionally(error -> {
                player.sendMessage(ColorHelper.parseComponent("&cTranslation error: " + error.getMessage()));
                return null;
            });
        
        return true;
    }
    
    private boolean handleBatchAsync(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorHelper.parseComponent("&cUsage: /async batch <operation> <keys...>"));
            return true;
        }
        
        String operation = args[0];
        List<String> keys = Arrays.asList(Arrays.copyOfRange(args, 1, args.length));
        
        switch (operation.toLowerCase()) {
            case "get":
                handleBatchGetAsync(player, keys);
                break;
            case "translate":
                handleBatchTranslateAsync(player, keys);
                break;
            default:
                player.sendMessage(ColorHelper.parseComponent("&cUnknown batch operation: " + operation));
        }
        
        return true;
    }
    
    private void handleBatchGetAsync(Player player, List<String> keys) {
        asyncConfigManager.getBatchAsync(keys, String.class)
            .thenAccept(values -> {
                StringBuilder result = new StringBuilder("&aBatch Results:\n");
                for (int i = 0; i < keys.size(); i++) {
                    String key = keys.get(i);
                    String value = i < values.size() ? values.get(i) : "null";
                    result.append("&f").append(key).append(": &e").append(value).append("\n");
                }
                player.sendMessage(ColorHelper.parseComponent(result.toString()));
            })
            .exceptionally(error -> {
                player.sendMessage(ColorHelper.parseComponent("&cBatch get error: " + error.getMessage()));
                return null;
            });
    }
    
    private void handleBatchTranslateAsync(Player player, List<String> keys) {
        asyncTranslationService.translateBatchAsync(player.getUniqueId().toString(), keys)
            .thenAccept(translations -> {
                StringBuilder result = new StringBuilder("&aBatch Translations:\n");
                for (int i = 0; i < keys.size(); i++) {
                    String key = keys.get(i);
                    String translation = i < translations.size() ? translations.get(i) : key;
                    result.append("&f").append(key).append(": &e").append(translation).append("\n");
                }
                player.sendMessage(ColorHelper.parseComponent(result.toString()));
            })
            .exceptionally(error -> {
                player.sendMessage(ColorHelper.parseComponent("&cBatch translate error: " + error.getMessage()));
                return null;
            });
    }
    
    private void sendUsage(Player player) {
        player.sendMessage(ColorHelper.parseComponent("&6Async Operations Commands:"));
        player.sendMessage(ColorHelper.parseComponent("&f/async get <key> &7- Get configuration value asynchronously"));
        player.sendMessage(ColorHelper.parseComponent("&f/async set <key> <value> &7- Set configuration value asynchronously"));
        player.sendMessage(ColorHelper.parseComponent("&f/async translate <key> [args...] &7- Translate message asynchronously"));
        player.sendMessage(ColorHelper.parseComponent("&f/async batch <operation> <keys...> &7- Perform batch operations"));
    }
}
```

### Async Configuration

```yaml
# config.yml
async:
  enabled: true
  thread-pool-size: 10
  batch-thread-pool-size: 2
  batch-size: 100
  batch-timeout-ms: 5000
  operation-timeout-ms: 30000
  retry-attempts: 3
  retry-delay-ms: 1000
  cache-enabled: true
  cache-size: 10000
  cache-expiry-minutes: 30
```

---

*Next: Learn about [[Performance Tips]] for optimizing your MongoDB Configs implementation.*