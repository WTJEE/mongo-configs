# Monitoring

Comprehensive monitoring and alerting system for tracking MongoDB Configs API performance, health, and usage patterns.

## 📊 Monitoring Overview

The Monitoring system provides real-time tracking of system performance, health metrics, and usage patterns with automated alerting and reporting capabilities.

## 📋 Core Monitoring Components

### Health Checker

```java
public class HealthChecker {
    
    private final MongoConfigsPlugin plugin;
    private final ScheduledExecutorService healthExecutor;
    private final Map<String, HealthCheck> healthChecks = new ConcurrentHashMap<>();
    private final Map<String, HealthStatus> componentStatus = new ConcurrentHashMap<>();
    private volatile OverallHealthStatus overallStatus = OverallHealthStatus.HEALTHY;
    
    public HealthChecker(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.healthExecutor = Executors.newScheduledThreadPool(3);
        
        // Register default health checks
        registerDefaultHealthChecks();
        
        // Start periodic health checking
        startHealthMonitoring();
    }
    
    private void registerDefaultHealthChecks() {
        registerHealthCheck("mongodb", new MongoDBHealthCheck());
        registerHealthCheck("cache", new CacheHealthCheck());
        registerHealthCheck("memory", new MemoryHealthCheck());
        registerHealthCheck("disk", new DiskHealthCheck());
        registerHealthCheck("translations", new TranslationHealthCheck());
        registerHealthCheck("async_operations", new AsyncOperationsHealthCheck());
    }
    
    private void startHealthMonitoring() {
        // Run comprehensive health check every 30 seconds
        healthExecutor.scheduleAtFixedRate(this::performHealthChecks, 0, 30, TimeUnit.SECONDS);
        
        // Run detailed health check every 5 minutes
        healthExecutor.scheduleAtFixedRate(this::performDetailedHealthChecks, 0, 5, TimeUnit.MINUTES);
    }
    
    private void performHealthChecks() {
        Map<String, HealthStatus> results = new HashMap<>();
        OverallHealthStatus newOverallStatus = OverallHealthStatus.HEALTHY;
        
        for (Map.Entry<String, HealthCheck> entry : healthChecks.entrySet()) {
            String component = entry.getKey();
            HealthCheck check = entry.getValue();
            
            try {
                HealthStatus status = check.check();
                results.put(component, status);
                
                // Update overall status
                if (status.getStatus() == HealthStatus.Status.UNHEALTHY) {
                    newOverallStatus = OverallHealthStatus.UNHEALTHY;
                } else if (status.getStatus() == HealthStatus.Status.DEGRADED && 
                          newOverallStatus == OverallHealthStatus.HEALTHY) {
                    newOverallStatus = OverallHealthStatus.DEGRADED;
                }
                
            } catch (Exception e) {
                plugin.getLogger().warning("Health check failed for " + component + ": " + e.getMessage());
                results.put(component, HealthStatus.unhealthy("Health check failed: " + e.getMessage()));
                newOverallStatus = OverallHealthStatus.UNHEALTHY;
            }
        }
        
        // Update component statuses
        componentStatus.putAll(results);
        overallStatus = newOverallStatus;
        
        // Log status changes
        logStatusChanges(results);
        
        // Trigger alerts if necessary
        if (newOverallStatus != OverallHealthStatus.HEALTHY) {
            triggerHealthAlerts(results);
        }
    }
    
    private void performDetailedHealthChecks() {
        // Perform more comprehensive checks less frequently
        for (Map.Entry<String, HealthCheck> entry : healthChecks.entrySet()) {
            String component = entry.getKey();
            HealthCheck check = entry.getValue();
            
            if (check instanceof DetailedHealthCheck) {
                try {
                    DetailedHealthResult result = ((DetailedHealthCheck) check).detailedCheck();
                    plugin.getLogger().info("Detailed health check for " + component + ": " + result.getDetails());
                } catch (Exception e) {
                    plugin.getLogger().warning("Detailed health check failed for " + component + ": " + e.getMessage());
                }
            }
        }
    }
    
    private void logStatusChanges(Map<String, HealthStatus> newResults) {
        for (Map.Entry<String, HealthStatus> entry : newResults.entrySet()) {
            String component = entry.getKey();
            HealthStatus newStatus = entry.getValue();
            HealthStatus oldStatus = componentStatus.get(component);
            
            if (oldStatus == null || oldStatus.getStatus() != newStatus.getStatus()) {
                String level = newStatus.getStatus() == HealthStatus.Status.HEALTHY ? "INFO" : "WARNING";
                plugin.getLogger().info(String.format("[%s] %s status changed: %s -> %s (%s)",
                    level, component, 
                    oldStatus != null ? oldStatus.getStatus() : "UNKNOWN",
                    newStatus.getStatus(), newStatus.getMessage()));
            }
        }
    }
    
    private void triggerHealthAlerts(Map<String, HealthStatus> results) {
        List<String> unhealthyComponents = results.entrySet().stream()
            .filter(entry -> entry.getValue().getStatus() != HealthStatus.Status.HEALTHY)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        if (!unhealthyComponents.isEmpty()) {
            String alertMessage = "Health check failed for components: " + 
                String.join(", ", unhealthyComponents);
            
            // Send alerts to administrators
            alertAdministrators(alertMessage, results);
            
            // Log alert
            plugin.getLogger().warning("HEALTH ALERT: " + alertMessage);
        }
    }
    
    private void alertAdministrators(String message, Map<String, HealthStatus> results) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("mongoconfigs.admin")) {
                player.sendMessage(ColorHelper.parseComponent("&c[HEALTH ALERT] &f" + message));
                
                // Send detailed status for each unhealthy component
                for (Map.Entry<String, HealthStatus> entry : results.entrySet()) {
                    if (entry.getValue().getStatus() != HealthStatus.Status.HEALTHY) {
                        player.sendMessage(ColorHelper.parseComponent(String.format(
                            "&f- %s: %s", entry.getKey(), entry.getValue().getMessage())));
                    }
                }
            }
        }
    }
    
    public void registerHealthCheck(String name, HealthCheck check) {
        healthChecks.put(name, check);
        plugin.getLogger().info("Registered health check: " + name);
    }
    
    public void unregisterHealthCheck(String name) {
        healthChecks.remove(name);
        componentStatus.remove(name);
        plugin.getLogger().info("Unregistered health check: " + name);
    }
    
    public HealthStatus getComponentStatus(String component) {
        return componentStatus.get(component);
    }
    
    public Map<String, HealthStatus> getAllComponentStatuses() {
        return new HashMap<>(componentStatus);
    }
    
    public OverallHealthStatus getOverallStatus() {
        return overallStatus;
    }
    
    public boolean isHealthy() {
        return overallStatus == OverallHealthStatus.HEALTHY;
    }
    
    public void shutdown() {
        healthExecutor.shutdown();
        try {
            if (!healthExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                healthExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthExecutor.shutdownNow();
        }
    }
    
    // Health Check Interface
    public interface HealthCheck {
        HealthStatus check() throws Exception;
    }
    
    // Detailed Health Check Interface
    public interface DetailedHealthCheck extends HealthCheck {
        DetailedHealthResult detailedCheck() throws Exception;
    }
    
    // Health Status
    public static class HealthStatus {
        
        public enum Status { HEALTHY, DEGRADED, UNHEALTHY }
        
        private final Status status;
        private final String message;
        private final Map<String, Object> details;
        private final long timestamp;
        
        private HealthStatus(Status status, String message, Map<String, Object> details) {
            this.status = status;
            this.message = message;
            this.details = details != null ? new HashMap<>(details) : new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public static HealthStatus healthy(String message) {
            return new HealthStatus(Status.HEALTHY, message, null);
        }
        
        public static HealthStatus degraded(String message) {
            return new HealthStatus(Status.DEGRADED, message, null);
        }
        
        public static HealthStatus unhealthy(String message) {
            return new HealthStatus(Status.UNHEALTHY, message, null);
        }
        
        public static HealthStatus withDetails(Status status, String message, Map<String, Object> details) {
            return new HealthStatus(status, message, details);
        }
        
        public Status getStatus() { return status; }
        public String getMessage() { return message; }
        public Map<String, Object> getDetails() { return new HashMap<>(details); }
        public long getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("HealthStatus{status=%s, message='%s', timestamp=%d}", 
                status, message, timestamp);
        }
    }
    
    // Overall Health Status
    public enum OverallHealthStatus {
        HEALTHY("All systems operational"),
        DEGRADED("Some systems degraded"),
        UNHEALTHY("Critical systems unhealthy");
        
        private final String description;
        
        OverallHealthStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Detailed Health Result
    public static class DetailedHealthResult {
        
        private final HealthStatus status;
        private final Map<String, Object> details;
        private final List<String> recommendations;
        
        public DetailedHealthResult(HealthStatus status, Map<String, Object> details, 
                                  List<String> recommendations) {
            this.status = status;
            this.details = details != null ? new HashMap<>(details) : new HashMap<>();
            this.recommendations = recommendations != null ? 
                new ArrayList<>(recommendations) : new ArrayList<>();
        }
        
        public HealthStatus getStatus() { return status; }
        public Map<String, Object> getDetails() { return new HashMap<>(details); }
        public List<String> getRecommendations() { return new ArrayList<>(recommendations); }
    }
}
```

### MongoDB Health Check

```java
public class MongoDBHealthCheck implements HealthChecker.HealthCheck, HealthChecker.DetailedHealthCheck {
    
    @Override
    public HealthChecker.HealthStatus check() throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        
        try {
            // Simple connection test
            boolean connected = plugin.getConfigManager().testConnection();
            
            if (connected) {
                return HealthChecker.HealthStatus.healthy("MongoDB connection is healthy");
            } else {
                return HealthChecker.HealthStatus.unhealthy("MongoDB connection failed");
            }
            
        } catch (Exception e) {
            return HealthChecker.HealthStatus.unhealthy("MongoDB health check failed: " + e.getMessage());
        }
    }
    
    @Override
    public HealthChecker.DetailedHealthResult detailedCheck() throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        Map<String, Object> details = new HashMap<>();
        List<String> recommendations = new ArrayList<>();
        
        try {
            // Get database stats
            Document stats = plugin.getConfigManager().getDatabase().runCommand(new Document("dbStats", 1));
            
            details.put("db_name", stats.getString("db"));
            details.put("collections", stats.getInteger("collections"));
            details.put("objects", stats.getLong("objects"));
            details.put("data_size_mb", stats.getLong("dataSize") / 1024.0 / 1024.0);
            details.put("storage_size_mb", stats.getLong("storageSize") / 1024.0 / 1024.0);
            details.put("indexes", stats.getInteger("indexes"));
            details.put("index_size_mb", stats.getLong("indexSize") / 1024.0 / 1024.0);
            
            // Check for potential issues
            long dataSize = stats.getLong("dataSize");
            long storageSize = stats.getLong("storageSize");
            double storageEfficiency = dataSize > 0 ? (double) dataSize / storageSize : 0;
            
            if (storageEfficiency < 0.5) {
                recommendations.add("Consider running database compaction to improve storage efficiency");
            }
            
            if (stats.getInteger("indexes") > 20) {
                recommendations.add("High number of indexes detected - review index usage");
            }
            
            // Check connection pool stats
            Document serverStatus = plugin.getConfigManager().getDatabase()
                .runCommand(new Document("serverStatus", 1));
            
            Document connections = (Document) serverStatus.get("connections");
            if (connections != null) {
                details.put("active_connections", connections.getInteger("active"));
                details.put("available_connections", connections.getInteger("available"));
                details.put("total_created", connections.getLong("totalCreated"));
                
                int active = connections.getInteger("active");
                int available = connections.getInteger("available");
                
                if (active > available * 0.8) {
                    recommendations.add("Connection pool usage is high - consider increasing pool size");
                }
            }
            
            return new HealthChecker.DetailedHealthResult(
                HealthChecker.HealthStatus.healthy("MongoDB detailed check completed"),
                details,
                recommendations
            );
            
        } catch (Exception e) {
            details.put("error", e.getMessage());
            recommendations.add("Check MongoDB server logs for detailed error information");
            recommendations.add("Verify network connectivity to MongoDB server");
            
            return new HealthChecker.DetailedHealthResult(
                HealthChecker.HealthStatus.unhealthy("MongoDB detailed check failed: " + e.getMessage()),
                details,
                recommendations
            );
        }
    }
}
```

### Cache Health Check

```java
public class CacheHealthCheck implements HealthChecker.HealthCheck, HealthChecker.DetailedHealthCheck {
    
    @Override
    public HealthChecker.HealthStatus check() throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        MultiLevelCache cache = plugin.getCache();
        
        if (cache == null) {
            return HealthChecker.HealthStatus.unhealthy("Cache system is not initialized");
        }
        
        try {
            // Test basic cache operations
            String testKey = "health_check_test_" + System.currentTimeMillis();
            String testValue = "test_value";
            
            cache.put(testKey, testValue);
            String retrieved = cache.get(testKey, String.class);
            
            if (!testValue.equals(retrieved)) {
                return HealthChecker.HealthStatus.unhealthy("Cache read/write test failed");
            }
            
            cache.invalidate(testKey);
            
            return HealthChecker.HealthStatus.healthy("Cache system is operational");
            
        } catch (Exception e) {
            return HealthChecker.HealthStatus.unhealthy("Cache health check failed: " + e.getMessage());
        }
    }
    
    @Override
    public HealthChecker.DetailedHealthResult detailedCheck() throws Exception {
        MongoConfigsPlugin plugin = MongoConfigsPlugin.getInstance();
        MultiLevelCache cache = plugin.getCache();
        Map<String, Object> details = new HashMap<>();
        List<String> recommendations = new ArrayList<>();
        
        try {
            MultiLevelCache.CacheStats stats = cache.getStats();
            
            details.put("l1_cache_size", stats.getL1Size());
            details.put("l2_cache_size", stats.getL2Size());
            details.put("l1_hit_rate", stats.getL1HitRate());
            details.put("l1_hits", stats.getL1Hits());
            details.put("l1_misses", stats.getL1Misses());
            details.put("total_requests", stats.getL1Hits() + stats.getL1Misses());
            
            // Performance analysis
            double hitRate = stats.getL1HitRate();
            if (hitRate < 0.7) {
                recommendations.add("Cache hit rate is low (" + String.format("%.2f%%", hitRate * 100) + 
                    ") - consider increasing cache size or adjusting TTL");
            }
            
            long totalRequests = stats.getL1Hits() + stats.getL1Misses();
            if (totalRequests > 10000) {
                recommendations.add("High cache request volume detected - monitor for performance impact");
            }
            
            // Memory usage check
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryUsage = (double) usedMemory / maxMemory;
            
            details.put("memory_usage_percent", memoryUsage * 100);
            
            if (memoryUsage > 0.8) {
                recommendations.add("High memory usage detected - cache may be consuming too much memory");
            }
            
            return new HealthChecker.DetailedHealthResult(
                HealthChecker.HealthStatus.healthy("Cache detailed check completed"),
                details,
                recommendations
            );
            
        } catch (Exception e) {
            details.put("error", e.getMessage());
            recommendations.add("Check cache configuration and logs for detailed error information");
            
            return new HealthChecker.DetailedHealthResult(
                HealthChecker.HealthStatus.unhealthy("Cache detailed check failed: " + e.getMessage()),
                details,
                recommendations
            );
        }
    }
}
```

## 🔧 Monitoring Dashboard

### Metrics Dashboard

```java
public class MetricsDashboard {
    
    private final MongoConfigsPlugin plugin;
    private final HealthChecker healthChecker;
    private final PerformanceMetrics performanceMetrics;
    private final Map<String, DashboardPanel> panels = new HashMap<>();
    
    public MetricsDashboard(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.healthChecker = plugin.getHealthChecker();
        this.performanceMetrics = plugin.getPerformanceMetrics();
        
        // Register default panels
        registerDefaultPanels();
    }
    
    private void registerDefaultPanels() {
        registerPanel("system_health", new SystemHealthPanel());
        registerPanel("performance", new PerformancePanel());
        registerPanel("database", new DatabasePanel());
        registerPanel("cache", new CachePanel());
        registerPanel("memory", new MemoryPanel());
        registerPanel("operations", new OperationsPanel());
    }
    
    public void registerPanel(String name, DashboardPanel panel) {
        panels.put(name, panel);
    }
    
    public String generateDashboard(CommandSender sender) {
        StringBuilder dashboard = new StringBuilder();
        
        dashboard.append("&6=== MongoDB Configs Dashboard ===\n");
        dashboard.append("&fGenerated: &e").append(new Date()).append("\n\n");
        
        // Overall health status
        HealthChecker.OverallHealthStatus overallHealth = healthChecker.getOverallStatus();
        String healthColor = getHealthColor(overallHealth);
        dashboard.append("&fOverall Health: ").append(healthColor)
                .append(overallHealth.getDescription()).append("\n\n");
        
        // Generate each panel
        for (Map.Entry<String, DashboardPanel> entry : panels.entrySet()) {
            String panelName = entry.getKey();
            DashboardPanel panel = entry.getValue();
            
            try {
                String panelContent = panel.generateContent(sender);
                if (!panelContent.isEmpty()) {
                    dashboard.append("&6").append(panelName.toUpperCase()).append("\n");
                    dashboard.append(panelContent).append("\n");
                }
            } catch (Exception e) {
                dashboard.append("&cError generating ").append(panelName).append(" panel: ")
                        .append(e.getMessage()).append("\n");
            }
        }
        
        return dashboard.toString();
    }
    
    private String getHealthColor(HealthChecker.OverallHealthStatus status) {
        switch (status) {
            case HEALTHY: return "&a";
            case DEGRADED: return "&e";
            case UNHEALTHY: return "&c";
            default: return "&f";
        }
    }
    
    public void sendDashboard(CommandSender sender) {
        String dashboard = generateDashboard(sender);
        String[] lines = dashboard.split("\n");
        
        for (String line : lines) {
            sender.sendMessage(ColorHelper.parseComponent(line));
        }
    }
    
    // Dashboard Panel Interface
    public interface DashboardPanel {
        String generateContent(CommandSender sender) throws Exception;
    }
    
    // System Health Panel
    public class SystemHealthPanel implements DashboardPanel {
        
        @Override
        public String generateContent(CommandSender sender) throws Exception {
            StringBuilder content = new StringBuilder();
            Map<String, HealthChecker.HealthStatus> statuses = healthChecker.getAllComponentStatuses();
            
            for (Map.Entry<String, HealthChecker.HealthStatus> entry : statuses.entrySet()) {
                String component = entry.getKey();
                HealthChecker.HealthStatus status = entry.getValue();
                
                String statusColor = getStatusColor(status.getStatus());
                content.append(statusColor).append(component).append(": &f")
                      .append(status.getMessage()).append("\n");
            }
            
            return content.toString();
        }
        
        private String getStatusColor(HealthChecker.HealthStatus.Status status) {
            switch (status) {
                case HEALTHY: return "&a";
                case DEGRADED: return "&e";
                case UNHEALTHY: return "&c";
                default: return "&f";
            }
        }
    }
    
    // Performance Panel
    public class PerformancePanel implements DashboardPanel {
        
        @Override
        public String generateContent(CommandSender sender) throws Exception {
            StringBuilder content = new StringBuilder();
            Map<String, Object> metrics = performanceMetrics.getMetricsSnapshot();
            
            // Memory usage
            @SuppressWarnings("unchecked")
            Map<String, Object> memory = (Map<String, Object>) metrics.get("memory");
            if (memory != null) {
                long used = (Long) memory.get("used");
                long total = (Long) memory.get("total");
                double usagePercent = (double) used / total * 100;
                
                content.append("&fMemory Usage: &e")
                      .append(String.format("%.1fMB / %.1fMB (%.1f%%)", 
                          used / 1024.0 / 1024.0, total / 1024.0 / 1024.0, usagePercent))
                      .append("\n");
            }
            
            return content.toString();
        }
    }
    
    // Database Panel
    public class DatabasePanel implements DashboardPanel {
        
        @Override
        public String generateContent(CommandSender sender) throws Exception {
            StringBuilder content = new StringBuilder();
            
            try {
                Document stats = plugin.getConfigManager().getDatabase()
                    .runCommand(new Document("dbStats", 1));
                
                content.append("&fDatabase: &e").append(stats.getString("db")).append("\n");
                content.append("&fCollections: &e").append(stats.getInteger("collections")).append("\n");
                content.append("&fDocuments: &e").append(stats.getLong("objects")).append("\n");
                content.append("&fData Size: &e")
                      .append(String.format("%.2fMB", stats.getLong("dataSize") / 1024.0 / 1024.0))
                      .append("\n");
                content.append("&fStorage Size: &e")
                      .append(String.format("%.2fMB", stats.getLong("storageSize") / 1024.0 / 1024.0))
                      .append("\n");
                
            } catch (Exception e) {
                content.append("&cDatabase stats unavailable: ").append(e.getMessage()).append("\n");
            }
            
            return content.toString();
        }
    }
    
    // Cache Panel
    public class CachePanel implements DashboardPanel {
        
        @Override
        public String generateContent(CommandSender sender) throws Exception {
            StringBuilder content = new StringBuilder();
            MultiLevelCache.CacheStats stats = plugin.getCache().getStats();
            
            content.append("&fL1 Cache Size: &e").append(stats.getL1Size()).append(" items\n");
            content.append("&fL2 Cache Size: &e").append(stats.getL2Size()).append(" items\n");
            content.append("&fL1 Hit Rate: &e")
                  .append(String.format("%.1f%%", stats.getL1HitRate() * 100)).append("\n");
            content.append("&fTotal Requests: &e")
                  .append(stats.getL1Hits() + stats.getL1Misses()).append("\n");
            
            return content.toString();
        }
    }
    
    // Memory Panel
    public class MemoryPanel implements DashboardPanel {
        
        @Override
        public String generateContent(CommandSender sender) throws Exception {
            StringBuilder content = new StringBuilder();
            Runtime runtime = Runtime.getRuntime();
            
            long used = runtime.totalMemory() - runtime.freeMemory();
            long free = runtime.freeMemory();
            long total = runtime.totalMemory();
            long max = runtime.maxMemory();
            
            content.append("&fUsed Memory: &e")
                  .append(String.format("%.2fMB", used / 1024.0 / 1024.0)).append("\n");
            content.append("&fFree Memory: &e")
                  .append(String.format("%.2fMB", free / 1024.0 / 1024.0)).append("\n");
            content.append("&fTotal Memory: &e")
                  .append(String.format("%.2fMB", total / 1024.0 / 1024.0)).append("\n");
            content.append("&fMax Memory: &e")
                  .append(String.format("%.2fMB", max / 1024.0 / 1024.0)).append("\n");
            content.append("&fMemory Usage: &e")
                  .append(String.format("%.1f%%", (double) used / total * 100)).append("\n");
            
            return content.toString();
        }
    }
    
    // Operations Panel
    public class OperationsPanel implements DashboardPanel {
        
        @Override
        public String generateContent(CommandSender sender) throws Exception {
            StringBuilder content = new StringBuilder();
            Map<String, Object> metrics = performanceMetrics.getMetricsSnapshot();
            
            // Operations metrics
            @SuppressWarnings("unchecked")
            Map<String, PerformanceMetrics.OperationMetrics> operations = 
                (Map<String, PerformanceMetrics.OperationMetrics>) metrics.get("operations");
            
            if (operations != null && !operations.isEmpty()) {
                content.append("&fTop Operations:\n");
                operations.entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(e2.getValue().getTotalCount(), e1.getValue().getTotalCount()))
                    .limit(5)
                    .forEach(entry -> {
                        String opName = entry.getKey();
                        PerformanceMetrics.OperationMetrics opMetrics = entry.getValue();
                        content.append("&f- ").append(opName).append(": &e")
                              .append(opMetrics.getTotalCount()).append(" calls, &e")
                              .append(String.format("%.2fms", opMetrics.getAverageDuration()))
                              .append(" avg\n");
                    });
            }
            
            return content.toString();
        }
    }
}
```

## 🔧 Integration Examples

### Monitoring Command

```java
public class MonitoringCommand implements CommandExecutor {
    
    private final MongoConfigsPlugin plugin;
    private final HealthChecker healthChecker;
    private final MetricsDashboard dashboard;
    
    public MonitoringCommand(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        this.healthChecker = plugin.getHealthChecker();
        this.dashboard = plugin.getMetricsDashboard();
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
            case "health":
                return handleHealth(sender, Arrays.copyOfRange(args, 1, args.length));
            case "dashboard":
                return handleDashboard(sender);
            case "alerts":
                return handleAlerts(sender);
            case "metrics":
                return handleMetrics(sender, Arrays.copyOfRange(args, 1, args.length));
            default:
                sendUsage(sender);
                return true;
        }
    }
    
    private boolean handleHealth(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Show overall health
            HealthChecker.OverallHealthStatus status = healthChecker.getOverallStatus();
            String statusColor = getStatusColor(status);
            
            sender.sendMessage(ColorHelper.parseComponent("&6=== System Health ==="));
            sender.sendMessage(ColorHelper.parseComponent("&fOverall Status: " + statusColor + 
                status.getDescription()));
            
            return true;
        }
        
        String component = args[0].toLowerCase();
        HealthChecker.HealthStatus status = healthChecker.getComponentStatus(component);
        
        if (status == null) {
            sender.sendMessage(ColorHelper.parseComponent("&cUnknown component: " + component));
            return true;
        }
        
        String statusColor = getStatusColor(status.getStatus());
        sender.sendMessage(ColorHelper.parseComponent("&6=== " + component.toUpperCase() + " Health ==="));
        sender.sendMessage(ColorHelper.parseComponent("&fStatus: " + statusColor + status.getStatus()));
        sender.sendMessage(ColorHelper.parseComponent("&fMessage: &f" + status.getMessage()));
        
        return true;
    }
    
    private boolean handleDashboard(CommandSender sender) {
        dashboard.sendDashboard(sender);
        return true;
    }
    
    private boolean handleAlerts(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&6=== Recent Alerts ==="));
        
        // This would typically query an alerts log
        // For now, just show current unhealthy components
        Map<String, HealthChecker.HealthStatus> statuses = healthChecker.getAllComponentStatuses();
        
        boolean hasAlerts = false;
        for (Map.Entry<String, HealthChecker.HealthStatus> entry : statuses.entrySet()) {
            if (entry.getValue().getStatus() != HealthChecker.HealthStatus.Status.HEALTHY) {
                sender.sendMessage(ColorHelper.parseComponent("&c- " + entry.getKey() + ": " + 
                    entry.getValue().getMessage()));
                hasAlerts = true;
            }
        }
        
        if (!hasAlerts) {
            sender.sendMessage(ColorHelper.parseComponent("&aNo active alerts"));
        }
        
        return true;
    }
    
    private boolean handleMetrics(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorHelper.parseComponent("&cUsage: /monitor metrics <type>"));
            return true;
        }
        
        String type = args[0].toLowerCase();
        
        switch (type) {
            case "performance":
                return handlePerformanceMetrics(sender);
            case "memory":
                return handleMemoryMetrics(sender);
            case "cache":
                return handleCacheMetrics(sender);
            default:
                sender.sendMessage(ColorHelper.parseComponent("&cUnknown metrics type: " + type));
                return true;
        }
    }
    
    private boolean handlePerformanceMetrics(CommandSender sender) {
        Map<String, Object> metrics = plugin.getPerformanceMetrics().getMetricsSnapshot();
        
        sender.sendMessage(ColorHelper.parseComponent("&6=== Performance Metrics ==="));
        
        // Memory metrics
        @SuppressWarnings("unchecked")
        Map<String, Object> memory = (Map<String, Object>) metrics.get("memory");
        if (memory != null) {
            long used = (Long) memory.get("used");
            long total = (Long) memory.get("total");
            sender.sendMessage(ColorHelper.parseComponent(String.format(
                "&fMemory: &e%.2fMB used / %.2fMB total",
                used / 1024.0 / 1024.0, total / 1024.0 / 1024.0)));
        }
        
        return true;
    }
    
    private boolean handleMemoryMetrics(CommandSender sender) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long free = runtime.freeMemory();
        long total = runtime.totalMemory();
        long max = runtime.maxMemory();
        
        sender.sendMessage(ColorHelper.parseComponent("&6=== Memory Metrics ==="));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fUsed: &e%.2fMB", used / 1024.0 / 1024.0)));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fFree: &e%.2fMB", free / 1024.0 / 1024.0)));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fTotal: &e%.2fMB", total / 1024.0 / 1024.0)));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fMax: &e%.2fMB", max / 1024.0 / 1024.0)));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fUsage: &e%.1f%%", (double) used / total * 100)));
        
        return true;
    }
    
    private boolean handleCacheMetrics(CommandSender sender) {
        MultiLevelCache.CacheStats stats = plugin.getCache().getStats();
        
        sender.sendMessage(ColorHelper.parseComponent("&6=== Cache Metrics ==="));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fL1 Cache: &e%d items", stats.getL1Size())));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fL2 Cache: &e%d items", stats.getL2Size())));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fL1 Hit Rate: &e%.1f%%", stats.getL1HitRate() * 100)));
        sender.sendMessage(ColorHelper.parseComponent(String.format(
            "&fTotal Requests: &e%d", stats.getL1Hits() + stats.getL1Misses())));
        
        return true;
    }
    
    private String getStatusColor(HealthChecker.OverallHealthStatus status) {
        switch (status) {
            case HEALTHY: return "&a";
            case DEGRADED: return "&e";
            case UNHEALTHY: return "&c";
            default: return "&f";
        }
    }
    
    private String getStatusColor(HealthChecker.HealthStatus.Status status) {
        switch (status) {
            case HEALTHY: return "&a";
            case DEGRADED: return "&e";
            case UNHEALTHY: return "&c";
            default: return "&f";
        }
    }
    
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ColorHelper.parseComponent("&6Monitoring Commands:"));
        sender.sendMessage(ColorHelper.parseComponent("&f/monitor health [component] &7- Show health status"));
        sender.sendMessage(ColorHelper.parseComponent("&f/monitor dashboard &7- Show full metrics dashboard"));
        sender.sendMessage(ColorHelper.parseComponent("&f/monitor alerts &7- Show recent alerts"));
        sender.sendMessage(ColorHelper.parseComponent("&f/monitor metrics <type> &7- Show specific metrics"));
    }
}
```

### Monitoring Configuration

```yaml
# config.yml
monitoring:
  enabled: true
  health-check-interval-seconds: 30
  detailed-check-interval-minutes: 5
  
  # Health check thresholds
  thresholds:
    memory-usage-percent: 80
    cache-hit-rate-min: 0.7
    connection-pool-usage-max: 0.8
    response-time-max-ms: 5000
  
  # Alert settings
  alerts:
    enabled: true
    admin-notification: true
    console-logging: true
    alert-cooldown-minutes: 5
  
  # Metrics collection
  metrics:
    enabled: true
    collection-interval-seconds: 60
    retention-days: 7
  
  # Dashboard settings
  dashboard:
    enabled: true
    refresh-interval-seconds: 30
    max-display-items: 10
```

---

*Next: Learn about [[Troubleshooting]] for diagnosing and resolving common issues.*