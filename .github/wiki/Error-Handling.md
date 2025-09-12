# Error Handling

Comprehensive error management and recovery strategies for robust MongoDB Configs API integration, including graceful degradation and administrator notifications.

## 🚨 Error Handling Overview

The Error Handling system provides comprehensive error management, recovery strategies, and administrator notifications to ensure system reliability and maintain user experience during failures.

## 📋 Core Error Types

### Configuration Errors

```java
public enum ConfigErrorType {
    CONNECTION_FAILED("Failed to connect to MongoDB"),
    INVALID_CONFIGURATION("Configuration validation failed"),
    MISSING_REQUIRED_FIELD("Required configuration field is missing"),
    INVALID_FORMAT("Configuration field has invalid format"),
    PERMISSION_DENIED("Insufficient permissions for operation"),
    TIMEOUT("Operation timed out"),
    RESOURCE_EXHAUSTED("System resources exhausted"),
    CONCURRENT_MODIFICATION("Configuration modified by another process"),
    VALIDATION_FAILED("Configuration validation failed"),
    BACKUP_FAILED("Configuration backup failed");
    
    private final String description;
    
    ConfigErrorType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
```

### Translation Errors

```java
public enum TranslationErrorType {
    MISSING_TRANSLATION("Translation key not found"),
    INVALID_LANGUAGE("Unsupported language requested"),
    PLACEHOLDER_MISMATCH("Placeholder count mismatch"),
    FORMAT_ERROR("Translation format error"),
    ENCODING_ERROR("Text encoding error"),
    CACHE_CORRUPTION("Translation cache corrupted"),
    DATABASE_ERROR("Translation database error"),
    FALLBACK_FAILED("Fallback translation failed");
    
    private final String description;
    
    TranslationErrorType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
```

## 🛠️ Error Handler Implementation

### Central Error Handler

```java
public class ErrorHandler {
    
    private final MongoConfigsPlugin plugin;
    private final Map<Class<? extends Exception>, ErrorStrategy> errorStrategies = new HashMap<>();
    private final List<ErrorListener> errorListeners = new ArrayList<>();
    private final ExecutorService errorProcessor;
    private final Map<String, ErrorStatistics> errorStats = new ConcurrentHashMap<>();
    
    public ErrorHandler(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.errorProcessor = Executors.newCachedThreadPool();
        
        // Register default error strategies
        registerDefaultStrategies();
    }
    
    private void registerDefaultStrategies() {
        // MongoDB connection errors
        registerStrategy(MongoTimeoutException.class, new RetryWithBackoffStrategy(3, 1000));
        registerStrategy(MongoSocketException.class, new ReconnectStrategy());
        registerStrategy(MongoWriteException.class, new ValidationStrategy());
        
        // Configuration errors
        registerStrategy(IllegalArgumentException.class, new ValidationStrategy());
        registerStrategy(NullPointerException.class, new FallbackStrategy());
        
        // Translation errors
        registerStrategy(MissingResourceException.class, new FallbackTranslationStrategy());
        registerStrategy(UnsupportedEncodingException.class, new EncodingFallbackStrategy());
    }
    
    public void handleError(Throwable error, String context, Map<String, Object> metadata) {
        // Create error context
        ErrorContext errorContext = new ErrorContext(error, context, metadata, System.currentTimeMillis());
        
        // Update statistics
        updateErrorStatistics(errorContext);
        
        // Log the error
        logError(errorContext);
        
        // Find and execute appropriate strategy
        ErrorStrategy strategy = findStrategy(error);
        if (strategy != null) {
            errorProcessor.submit(() -> {
                try {
                    strategy.handle(errorContext);
                } catch (Exception strategyError) {
                    plugin.getLogger().severe("Error strategy failed: " + strategyError.getMessage());
                    // Fallback to basic error handling
                    handleFallback(errorContext);
                }
            });
        } else {
            // No specific strategy, use fallback
            handleFallback(errorContext);
        }
        
        // Notify listeners
        notifyErrorListeners(errorContext);
    }
    
    private ErrorStrategy findStrategy(Throwable error) {
        Class<?> errorClass = error.getClass();
        
        // Check for exact match first
        ErrorStrategy strategy = errorStrategies.get(errorClass);
        if (strategy != null) {
            return strategy;
        }
        
        // Check for superclass match
        for (Class<? extends Exception> registeredClass : errorStrategies.keySet()) {
            if (registeredClass.isAssignableFrom(errorClass)) {
                return errorStrategies.get(registeredClass);
            }
        }
        
        return null;
    }
    
    private void handleFallback(ErrorContext context) {
        // Basic fallback: log and notify administrators
        plugin.getLogger().severe("Unhandled error in " + context.getContext() + ": " + 
            context.getError().getMessage());
        
        // Notify administrators
        notifyAdministrators(context);
        
        // Attempt graceful degradation if possible
        attemptGracefulDegradation(context);
    }
    
    private void updateErrorStatistics(ErrorContext context) {
        String errorType = context.getError().getClass().getSimpleName();
        ErrorStatistics stats = errorStats.computeIfAbsent(errorType, k -> new ErrorStatistics());
        
        stats.incrementCount();
        stats.updateLastOccurrence(context.getTimestamp());
        
        // Check for error rate thresholds
        if (stats.getCountInLastHour() > getErrorThreshold()) {
            handleHighErrorRate(errorType, stats);
        }
    }
    
    private void logError(ErrorContext context) {
        Logger logger = plugin.getLogger();
        
        if (isSevereError(context.getError())) {
            logger.severe(createDetailedErrorMessage(context));
        } else {
            logger.warning(createDetailedErrorMessage(context));
        }
        
        // Log stack trace for debugging
        if (plugin.isDebugMode()) {
            StringWriter sw = new StringWriter();
            context.getError().printStackTrace(new PrintWriter(sw));
            logger.info("Stack trace: " + sw.toString());
        }
    }
    
    private String createDetailedErrorMessage(ErrorContext context) {
        StringBuilder message = new StringBuilder();
        message.append("Error in ").append(context.getContext()).append(": ");
        message.append(context.getError().getMessage());
        
        if (context.getMetadata() != null && !context.getMetadata().isEmpty()) {
            message.append(" [Metadata: ").append(context.getMetadata()).append("]");
        }
        
        return message.toString();
    }
    
    private boolean isSevereError(Throwable error) {
        return error instanceof MongoException || 
               error instanceof IllegalStateException ||
               error instanceof OutOfMemoryError;
    }
    
    private void notifyErrorListeners(ErrorContext context) {
        for (ErrorListener listener : errorListeners) {
            try {
                listener.onError(context);
            } catch (Exception e) {
                plugin.getLogger().warning("Error listener failed: " + e.getMessage());
            }
        }
    }
    
    private void notifyAdministrators(ErrorContext context) {
        if (!plugin.getConfig().getBoolean("error-handling.notify-admins", true)) {
            return;
        }
        
        String message = createAdminNotificationMessage(context);
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("mongoconfigs.admin")) {
                player.sendMessage(ColorHelper.parseComponent(message));
            }
        }
        
        // Also send to console
        plugin.getLogger().warning("Admin notification: " + message);
    }
    
    private String createAdminNotificationMessage(ErrorContext context) {
        return "&c[ERROR] &f" + context.getContext() + ": " + context.getError().getMessage() + 
               " &7(Check console for details)";
    }
    
    private void attemptGracefulDegradation(ErrorContext context) {
        // Implement graceful degradation based on error type
        String contextType = context.getContext();
        
        if (contextType.contains("translation")) {
            // Fallback to default language
            handleTranslationFallback(context);
        } else if (contextType.contains("config")) {
            // Use cached configuration
            handleConfigFallback(context);
        } else if (contextType.contains("database")) {
            // Switch to offline mode
            handleDatabaseFallback(context);
        }
    }
    
    private void handleTranslationFallback(ErrorContext context) {
        // Implementation for translation fallback
        plugin.getLogger().info("Attempting translation fallback for: " + context.getContext());
    }
    
    private void handleConfigFallback(ErrorContext context) {
        // Implementation for configuration fallback
        plugin.getLogger().info("Attempting configuration fallback for: " + context.getContext());
    }
    
    private void handleDatabaseFallback(ErrorContext context) {
        // Implementation for database fallback
        plugin.getLogger().info("Attempting database fallback for: " + context.getContext());
    }
    
    private void handleHighErrorRate(String errorType, ErrorStatistics stats) {
        plugin.getLogger().warning("High error rate detected for " + errorType + 
            ": " + stats.getCountInLastHour() + " errors in the last hour");
        
        // Implement circuit breaker or other protective measures
        if (stats.getCountInLastHour() > getCriticalErrorThreshold()) {
            activateCircuitBreaker(errorType);
        }
    }
    
    private void activateCircuitBreaker(String errorType) {
        plugin.getLogger().severe("Activating circuit breaker for " + errorType);
        
        // Disable problematic functionality temporarily
        // Implementation depends on the specific error type
    }
    
    public void registerStrategy(Class<? extends Exception> exceptionClass, ErrorStrategy strategy) {
        errorStrategies.put(exceptionClass, strategy);
    }
    
    public void addErrorListener(ErrorListener listener) {
        errorListeners.add(listener);
    }
    
    public void removeErrorListener(ErrorListener listener) {
        errorListeners.remove(listener);
    }
    
    public Map<String, ErrorStatistics> getErrorStatistics() {
        return new HashMap<>(errorStats);
    }
    
    public void clearErrorStatistics() {
        errorStats.clear();
    }
    
    private int getErrorThreshold() {
        return plugin.getConfig().getInt("error-handling.threshold-per-hour", 50);
    }
    
    private int getCriticalErrorThreshold() {
        return plugin.getConfig().getInt("error-handling.critical-threshold-per-hour", 200);
    }
    
    public void shutdown() {
        errorProcessor.shutdown();
        try {
            if (!errorProcessor.awaitTermination(10, TimeUnit.SECONDS)) {
                errorProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            errorProcessor.shutdownNow();
        }
    }
}
```

### Error Context

```java
public class ErrorContext {
    
    private final Throwable error;
    private final String context;
    private final Map<String, Object> metadata;
    private final long timestamp;
    private final String errorId;
    
    public ErrorContext(Throwable error, String context, Map<String, Object> metadata, long timestamp) {
        this.error = error;
        this.context = context;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.timestamp = timestamp;
        this.errorId = UUID.randomUUID().toString();
    }
    
    public Throwable getError() {
        return error;
    }
    
    public String getContext() {
        return context;
    }
    
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getErrorId() {
        return errorId;
    }
    
    public String getErrorType() {
        return error.getClass().getSimpleName();
    }
    
    public String getErrorMessage() {
        return error.getMessage() != null ? error.getMessage() : "No message";
    }
    
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    @Override
    public String toString() {
        return "ErrorContext{" +
                "errorId='" + errorId + '\'' +
                ", errorType='" + getErrorType() + '\'' +
                ", context='" + context + '\'' +
                ", timestamp=" + timestamp +
                ", metadata=" + metadata +
                '}';
    }
}
```

### Error Statistics

```java
public class ErrorStatistics {
    
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicLong lastOccurrence = new AtomicLong(0);
    private final Queue<Long> recentTimestamps = new ConcurrentLinkedQueue<>();
    
    public void incrementCount() {
        totalCount.incrementAndGet();
        long now = System.currentTimeMillis();
        lastOccurrence.set(now);
        recentTimestamps.add(now);
        
        // Clean old timestamps (keep only last hour)
        cleanOldTimestamps();
    }
    
    private void cleanOldTimestamps() {
        long oneHourAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
        while (!recentTimestamps.isEmpty() && recentTimestamps.peek() < oneHourAgo) {
            recentTimestamps.poll();
        }
    }
    
    public int getTotalCount() {
        return totalCount.get();
    }
    
    public long getLastOccurrence() {
        return lastOccurrence.get();
    }
    
    public int getCountInLastHour() {
        cleanOldTimestamps();
        return recentTimestamps.size();
    }
    
    public void updateLastOccurrence(long timestamp) {
        lastOccurrence.set(timestamp);
    }
    
    public double getErrorRatePerMinute() {
        int count = getCountInLastHour();
        return count / 60.0;
    }
    
    public double getErrorRatePerHour() {
        return getCountInLastHour();
    }
    
    @Override
    public String toString() {
        return "ErrorStatistics{" +
                "totalCount=" + totalCount +
                ", lastOccurrence=" + lastOccurrence +
                ", countInLastHour=" + getCountInLastHour() +
                '}';
    }
}
```

## 🎯 Error Recovery Strategies

### Retry with Backoff Strategy

```java
public class RetryWithBackoffStrategy implements ErrorStrategy {
    
    private final int maxRetries;
    private final long initialDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;
    
    public RetryWithBackoffStrategy(int maxRetries, long initialDelayMs) {
        this(maxRetries, initialDelayMs, 2.0, 30000); // Default: exponential backoff, max 30 seconds
    }
    
    public RetryWithBackoffStrategy(int maxRetries, long initialDelayMs, double backoffMultiplier, long maxDelayMs) {
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMs = maxDelayMs;
    }
    
    @Override
    public void handle(ErrorContext context) throws Exception {
        Throwable error = context.getError();
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        int attempt = 0;
        long delay = initialDelayMs;
        
        while (attempt < maxRetries) {
            attempt++;
            
            try {
                plugin.getLogger().info("Retrying operation (attempt " + attempt + "/" + maxRetries + ")");
                
                // Attempt to retry the operation
                if (retryOperation(context, attempt)) {
                    plugin.getLogger().info("Retry successful on attempt " + attempt);
                    return;
                }
                
            } catch (Exception retryError) {
                plugin.getLogger().warning("Retry attempt " + attempt + " failed: " + retryError.getMessage());
                
                if (attempt < maxRetries) {
                    // Wait before next retry
                    try {
                        Thread.sleep(Math.min(delay, maxDelayMs));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", e);
                    }
                    
                    // Increase delay for next attempt
                    delay = (long) (delay * backoffMultiplier);
                }
            }
        }
        
        // All retries failed
        plugin.getLogger().severe("All retry attempts failed for: " + context.getContext());
        throw new RuntimeException("Operation failed after " + maxRetries + " retries", error);
    }
    
    private boolean retryOperation(ErrorContext context, int attempt) throws Exception {
        // This method should be overridden by subclasses to implement specific retry logic
        // For now, return false to indicate retry should continue
        return false;
    }
    
    // Specific retry strategy for MongoDB operations
    public static class MongoRetryStrategy extends RetryWithBackoffStrategy {
        
        public MongoRetryStrategy() {
            super(5, 1000, 2.0, 10000); // 5 retries, starting at 1 second, up to 10 seconds
        }
        
        @Override
        protected boolean retryOperation(ErrorContext context, int attempt) throws Exception {
            Throwable error = context.getError();
            
            // Only retry certain types of MongoDB errors
            if (error instanceof MongoTimeoutException || 
                error instanceof MongoSocketException ||
                (error instanceof MongoException && isRetryableError((MongoException) error))) {
                
                // Get the operation from context and retry it
                Runnable operation = (Runnable) context.getMetadata("operation");
                if (operation != null) {
                    operation.run();
                    return true;
                }
            }
            
            return false;
        }
        
        private boolean isRetryableError(MongoException error) {
            // Check MongoDB error codes that are safe to retry
            int code = error.getCode();
            return code == 6 || // HostUnreachable
                   code == 7 || // HostNotFound
                   code == 89 || // NetworkTimeout
                   code == 91 || // ShutdownInProgress
                   code == 1000 || // Interrupted
                   code == 10107 || // NotMaster
                   code == 11600 || // InterruptedAtShutdown
                   code == 11602 || // InterruptedDueToReplStateChange
                   code == 13435 || // NotMasterNoSlaveOk
                   code == 13436; // NotMasterOrSecondary
        }
    }
}
```

### Circuit Breaker Strategy

```java
public class CircuitBreakerStrategy implements ErrorStrategy {
    
    private enum CircuitState {
        CLOSED, // Normal operation
        OPEN, // Circuit is open, failing fast
        HALF_OPEN // Testing if service has recovered
    }
    
    private final String serviceName;
    private final int failureThreshold;
    private final long timeoutMs;
    private final long retryTimeoutMs;
    
    private volatile CircuitState state = CircuitState.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;
    private volatile long lastRetryTime = 0;
    
    public CircuitBreakerStrategy(String serviceName, int failureThreshold, 
                                long timeoutMs, long retryTimeoutMs) {
        this.serviceName = serviceName;
        this.failureThreshold = failureThreshold;
        this.timeoutMs = timeoutMs;
        this.retryTimeoutMs = retryTimeoutMs;
    }
    
    @Override
    public void handle(ErrorContext context) throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        switch (state) {
            case CLOSED:
                handleClosedState(context);
                break;
            case OPEN:
                handleOpenState(context);
                break;
            case HALF_OPEN:
                handleHalfOpenState(context);
                break;
        }
    }
    
    private void handleClosedState(ErrorContext context) throws Exception {
        // Normal operation - let the error propagate but count it
        failureCount.incrementAndGet();
        lastFailureTime = System.currentTimeMillis();
        
        // Check if we should open the circuit
        if (failureCount.get() >= failureThreshold) {
            openCircuit();
        }
        
        throw new RuntimeException("Circuit breaker: operation failed", context.getError());
    }
    
    private void handleOpenState(ErrorContext context) throws Exception {
        long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime;
        
        if (timeSinceLastFailure > timeoutMs) {
            // Timeout expired, try to close the circuit
            attemptCloseCircuit(context);
        } else {
            // Circuit is still open, fail fast
            throw new CircuitBreakerOpenException(serviceName, 
                timeoutMs - timeSinceLastFailure);
        }
    }
    
    private void handleHalfOpenState(ErrorContext context) throws Exception {
        // We're testing the service
        try {
            // Attempt a test operation
            if (testService()) {
                // Test successful, close the circuit
                closeCircuit();
                MongoConfigsPlugin.getInstance().getLogger()
                    .info("Circuit breaker closed for " + serviceName);
            } else {
                // Test failed, open the circuit again
                openCircuit();
                throw new RuntimeException("Circuit breaker: test operation failed", 
                    context.getError());
            }
        } catch (Exception e) {
            // Test failed, open the circuit
            openCircuit();
            throw new RuntimeException("Circuit breaker: test operation failed", e);
        }
    }
    
    private void openCircuit() {
        state = CircuitState.OPEN;
        lastRetryTime = System.currentTimeMillis();
        MongoConfigsPlugin.getInstance().getLogger()
            .warning("Circuit breaker opened for " + serviceName);
    }
    
    private void closeCircuit() {
        state = CircuitState.CLOSED;
        failureCount.set(0);
        MongoConfigsPlugin.getInstance().getLogger()
            .info("Circuit breaker closed for " + serviceName);
    }
    
    private void attemptCloseCircuit(ErrorContext context) {
        state = CircuitState.HALF_OPEN;
        lastRetryTime = System.currentTimeMillis();
        MongoConfigsPlugin.getInstance().getLogger()
            .info("Circuit breaker attempting to close for " + serviceName);
        
        // Re-throw the original error to trigger half-open handling
        throw new RuntimeException("Circuit breaker: attempting recovery", context.getError());
    }
    
    private boolean testService() {
        // Implement service-specific test logic
        // This should be a lightweight operation to test if the service is working
        try {
            // Example: test database connection
            MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
            return plugin.getConfigManager().testConnection();
        } catch (Exception e) {
            return false;
        }
    }
    
    public CircuitState getState() {
        return state;
    }
    
    public int getFailureCount() {
        return failureCount.get();
    }
    
    public long getTimeSinceLastFailure() {
        return System.currentTimeMillis() - lastFailureTime;
    }
    
    public boolean isOpen() {
        return state == CircuitState.OPEN;
    }
    
    public static class CircuitBreakerOpenException extends RuntimeException {
        
        private final String serviceName;
        private final long remainingTimeoutMs;
        
        public CircuitBreakerOpenException(String serviceName, long remainingTimeoutMs) {
            super("Circuit breaker is open for service: " + serviceName + 
                  ", remaining timeout: " + remainingTimeoutMs + "ms");
            this.serviceName = serviceName;
            this.remainingTimeoutMs = remainingTimeoutMs;
        }
        
        public String getServiceName() {
            return serviceName;
        }
        
        public long getRemainingTimeoutMs() {
            return remainingTimeoutMs;
        }
    }
}
```

### Fallback Strategy

```java
public class FallbackStrategy implements ErrorStrategy {
    
    private final Map<String, Object> fallbackValues = new ConcurrentHashMap<>();
    private final Map<String, Supplier<Object>> fallbackSuppliers = new ConcurrentHashMap<>();
    
    @Override
    public void handle(ErrorContext context) throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        String contextType = context.getContext();
        
        plugin.getLogger().info("Applying fallback strategy for: " + contextType);
        
        try {
            if (contextType.contains("config")) {
                handleConfigFallback(context);
            } else if (contextType.contains("translation")) {
                handleTranslationFallback(context);
            } else if (contextType.contains("database")) {
                handleDatabaseFallback(context);
            } else {
                // Generic fallback
                handleGenericFallback(context);
            }
        } catch (Exception fallbackError) {
            plugin.getLogger().severe("Fallback strategy failed: " + fallbackError.getMessage());
            throw fallbackError;
        }
    }
    
    private void handleConfigFallback(ErrorContext context) throws Exception {
        // Try to load from cache or use default values
        String configKey = (String) context.getMetadata("config_key");
        if (configKey != null) {
            Object fallbackValue = getFallbackValue(configKey);
            if (fallbackValue != null) {
                context.addMetadata("fallback_value", fallbackValue);
                MongoConfigsPlugin.getInstance().getLogger()
                    .info("Using fallback value for config: " + configKey);
                return;
            }
        }
        
        // Use default configuration
        loadDefaultConfiguration(context);
    }
    
    private void handleTranslationFallback(ErrorContext context) throws Exception {
        String language = (String) context.getMetadata("language");
        String key = (String) context.getMetadata("key");
        
        // Try fallback to default language
        if (language != null && !language.equals("en")) {
            String fallbackTranslation = getFallbackTranslation(key, "en");
            if (fallbackTranslation != null) {
                context.addMetadata("fallback_translation", fallbackTranslation);
                context.addMetadata("fallback_language", "en");
                MongoConfigsPlugin.getInstance().getLogger()
                    .info("Using fallback translation for key: " + key + " in language: en");
                return;
            }
        }
        
        // Use key as fallback
        context.addMetadata("fallback_translation", key);
        MongoConfigsPlugin.getInstance().getLogger()
            .warning("Using key as fallback translation for: " + key);
    }
    
    private void handleDatabaseFallback(ErrorContext context) throws Exception {
        // Switch to offline/cached mode
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        if (!plugin.isOfflineMode()) {
            plugin.setOfflineMode(true);
            plugin.getLogger().info("Switching to offline mode due to database error");
            
            // Notify administrators
            notifyOfflineMode(plugin);
        }
        
        // Use cached data for operations
        context.addMetadata("offline_mode", true);
    }
    
    private void handleGenericFallback(ErrorContext context) throws Exception {
        // Generic fallback: return a safe default value
        Object fallbackValue = getFallbackValue("generic");
        if (fallbackValue != null) {
            context.addMetadata("fallback_value", fallbackValue);
        } else {
            // Last resort: throw a more descriptive error
            throw new RuntimeException("Operation failed and no fallback available", 
                context.getError());
        }
    }
    
    private void loadDefaultConfiguration(ErrorContext context) {
        // Load default configuration values
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("mongodb.uri", "mongodb://localhost:27017");
        defaults.put("mongodb.database", "mongoconfigs");
        defaults.put("language.default-language", "en");
        defaults.put("cache.enabled", true);
        defaults.put("cache.expiry-minutes", 60);
        
        context.addMetadata("default_config", defaults);
        MongoConfigsPlugin.getInstance().getLogger()
            .info("Loaded default configuration as fallback");
    }
    
    private String getFallbackTranslation(String key, String language) {
        // Try to get translation from cache or static fallbacks
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        try {
            return plugin.getTranslationService().translate(language, key);
        } catch (Exception e) {
            // Return static fallbacks for common keys
            switch (key) {
                case "error": return "Error";
                case "success": return "Success";
                case "loading": return "Loading...";
                case "welcome": return "Welcome";
                default: return key; // Return key as last resort
            }
        }
    }
    
    private Object getFallbackValue(String key) {
        // Check static fallback values first
        Object staticValue = fallbackValues.get(key);
        if (staticValue != null) {
            return staticValue;
        }
        
        // Check dynamic fallback suppliers
        Supplier<Object> supplier = fallbackSuppliers.get(key);
        if (supplier != null) {
            try {
                return supplier.get();
            } catch (Exception e) {
                MongoConfigsPlugin.getInstance().getLogger()
                    .warning("Fallback supplier failed for key: " + key);
            }
        }
        
        return null;
    }
    
    private void notifyOfflineMode(MongoConfigsPlugin plugin) {
        String message = "&e[WARNING] &fSystem switched to offline mode due to database connectivity issues";
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("mongoconfigs.admin")) {
                player.sendMessage(ColorHelper.parseComponent(message));
            }
        }
    }
    
    public void registerFallbackValue(String key, Object value) {
        fallbackValues.put(key, value);
    }
    
    public void registerFallbackSupplier(String key, Supplier<Object> supplier) {
        fallbackSuppliers.put(key, supplier);
    }
    
    public void unregisterFallback(String key) {
        fallbackValues.remove(key);
        fallbackSuppliers.remove(key);
    }
    
    public Set<String> getRegisteredFallbacks() {
        Set<String> all = new HashSet<>();
        all.addAll(fallbackValues.keySet());
        all.addAll(fallbackSuppliers.keySet());
        return all;
    }
}
```

## 🔧 Integration Examples

### Error Command

```java
public class ErrorCommand implements CommandExecutor {
    
    private final MongoConfigsPlugin plugin;
    private final ErrorHandler errorHandler;
    
    public ErrorCommand(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.errorHandler = plugin.getErrorHandler();
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
                return handleStats(sender, Arrays.copyOfRange(args, 1, args.length));
            case "clear":
                return handleClear(sender);
            case "test":
                return handleTest(sender, Arrays.copyOfRange(args, 1, args.length));
            case "config":
                return handleConfig(sender);
            default:
                sendUsage(sender);
                return true;
        }
    }
    
    private boolean handleStats(CommandSender sender, String[] args) {
        Map<String, ErrorStatistics> stats = errorHandler.getErrorStatistics();
        
        if (stats.isEmpty()) {
            sender.sendMessage(ColorHelper.parseComponent("&aNo errors recorded"));
            return true;
        }
        
        sender.sendMessage(ColorHelper.parseComponent("&6=== Error Statistics ==="));
        
        for (Map.Entry<String, ErrorStatistics> entry : stats.entrySet()) {
            String errorType = entry.getKey();
            ErrorStatistics stat = entry.getValue();
            
            sender.sendMessage(ColorHelper.parseComponent(
                "&f" + errorType + ": &e" + stat.getTotalCount() + 
                " total, &e" + stat.getCountInLastHour() + " in last hour"));
        }
        
        return true;
    }
    
    private boolean handleClear(CommandSender sender) {
        errorHandler.clearErrorStatistics();
        sender.sendMessage(ColorHelper.parseComponent("&aError statistics cleared"));
        return true;
    }
    
    private boolean handleTest(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorHelper.parseComponent("&cUsage: /mongoconfigs error test <type>"));
            return true;
        }
        
        String testType = args[0].toLowerCase();
        
        try {
            switch (testType) {
                case "connection":
                    testDatabaseConnection(sender);
                    break;
                case "translation":
                    testTranslationService(sender);
                    break;
                case "config":
                    testConfigService(sender);
                    break;
                default:
                    sender.sendMessage(ColorHelper.parseComponent("&cUnknown test type: " + testType));
            }
        } catch (Exception e) {
            sender.sendMessage(ColorHelper.parseComponent("&cTest failed: " + e.getMessage()));
        }
        
        return true;
    }
    
    private boolean handleConfig(CommandSender sender) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("error-handling");
        
        if (config == null) {
            sender.sendMessage(ColorHelper.parseComponent("&cError handling configuration not found"));
            return true;
        }
        
        sender.sendMessage(ColorHelper.parseComponent("&6=== Error Handling Configuration ==="));
        
        for (String key : config.getKeys(true)) {
            Object value = config.get(key);
            sender.sendMessage(ColorHelper.parseComponent("&f" + key + ": &e" + value));
        }
        
        return true;
    }
    
    private void testDatabaseConnection(CommandSender sender) throws Exception {
        sender.sendMessage(ColorHelper.parseComponent("&aTesting database connection..."));
        
        boolean success = plugin.getConfigManager().testConnection();
        
        if (success) {
            sender.sendMessage(ColorHelper.parseComponent("&aDatabase connection test passed"));
        } else {
            sender.sendMessage(ColorHelper.parseComponent("&cDatabase connection test failed"));
        }
    }
    
    private void testTranslationService(CommandSender sender) throws Exception {
        sender.sendMessage(ColorHelper.parseComponent("&aTesting translation service..."));
        
        String testTranslation = plugin.getTranslationService().translate("en", "test");
        
        if (testTranslation != null) {
            sender.sendMessage(ColorHelper.parseComponent("&aTranslation service test passed: " + testTranslation));
        } else {
            sender.sendMessage(ColorHelper.parseComponent("&cTranslation service test failed"));
        }
    }
    
    private void testConfigService(CommandSender sender) throws Exception {
        sender.sendMessage(ColorHelper.parseComponent("&aTesting configuration service..."));
        
        Object testConfig = plugin.getConfigManager().get(String.class, "test_config");
        
        if (testConfig != null) {
            sender.sendMessage(ColorHelper.parseComponent("&aConfiguration service test passed"));
        } else {
            sender.sendMessage(ColorHelper.parseComponent("&cConfiguration service test failed"));
        }
    }
    
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&6Error Handling Commands:"));
        sender.sendMessage(ColorHelper.parseComponent("&f/mongoerror stats &7- Show error statistics"));
        sender.sendMessage(ColorHelper.parseComponent("&f/mongoerror clear &7- Clear error statistics"));
        sender.sendMessage(ColorHelper.parseComponent("&f/mongoerror test <type> &7- Test specific service"));
        sender.sendMessage(ColorHelper.parseComponent("&f/mongoerror config &7- Show error handling configuration"));
    }
}
```

### Error Handling Configuration

```yaml
# config.yml
error-handling:
  enabled: true
  notify-admins: true
  threshold-per-hour: 50
  critical-threshold-per-hour: 200
  log-stack-traces: true
  retry-enabled: true
  circuit-breaker-enabled: true
  fallback-enabled: true
  graceful-degradation: true
  admin-notification-cooldown-minutes: 5
  
  # Retry configuration
  retry:
    max-attempts: 3
    initial-delay-ms: 1000
    backoff-multiplier: 2.0
    max-delay-ms: 30000
  
  # Circuit breaker configuration
  circuit-breaker:
    failure-threshold: 5
    timeout-ms: 60000
    retry-timeout-ms: 30000
  
  # Fallback configuration
  fallback:
    use-cache: true
    use-defaults: true
    offline-mode: true
    static-fallbacks: true
```

---

*Next: Learn about [[Async Operations]] for non-blocking configuration management.*