# Multi-Server Architecture

> **Advanced multi-server synchronization, load balancing, and cross-server communication**

## Table of Contents
- [Multi-Server Concepts](#multi-server-concepts)
- [Server Discovery](#server-discovery)
- [Load Balancing](#load-balancing)
- [Cross-Server Communication](#cross-server-communication)
- [Data Synchronization](#data-synchronization)
- [Global Configuration](#global-configuration)
- [Player Transfer System](#player-transfer-system)
- [Monitoring and Management](#monitoring-and-management)
- [Complete Examples](#complete-examples)
- [Troubleshooting](#troubleshooting)

---

## Multi-Server Concepts

### Why Multi-Server Architecture?

Multi-server setups provide:
- **Scalability** - Handle more players by distributing load
- **Reliability** - Redundancy prevents single points of failure
- **Performance** - Specialized servers for different game modes
- **Global Features** - Cross-server events and shared economies
- **Geographic Distribution** - Players connect to nearest server

### Common Multi-Server Patterns

#### 1. Hub-and-Spoke
```
[Hub Server]
    ├── Lobby
    ├── Server Selector
    └── Cross-server chat
        ├── [Game Server 1] - Survival
        ├── [Game Server 2] - Creative
        └── [Game Server 3] - PvP
```

#### 2. Distributed Network
```
[Load Balancer]
├── [Region 1 Servers]
│   ├── Server 1 (US-East)
│   └── Server 2 (US-East)
├── [Region 2 Servers]
│   ├── Server 3 (EU-West)
│   └── Server 4 (EU-West)
└── [Region 3 Servers]
    ├── Server 5 (Asia)
    └── Server 6 (Asia)
```

#### 3. Microservice Architecture
```
[API Gateway]
├── [Auth Server]
├── [Lobby Server]
├── [Game Servers]
│   ├── Survival Server
│   ├── Creative Server
│   └── PvP Server
├── [Economy Server]
└── [Database Cluster]
```

---

## Server Discovery

### Server Registry System

```java
@ConfigsFileProperties(name = "server-registry")
@ConfigsDatabase("minecraft")
public class ServerRegistry extends MongoConfig<ServerRegistry> {
    
    private Map<String, ServerInfo> servers = new ConcurrentHashMap<>();
    private Map<String, List<String>> serverGroups = new ConcurrentHashMap<>();
    private Map<String, ServerHealth> serverHealth = new ConcurrentHashMap<>();
    
    public static class ServerInfo {
        private String serverId;
        private String host;
        private int port;
        private String region;
        private String gameMode;
        private int maxPlayers;
        private boolean online = true;
        private long lastHeartbeat;
        
        // Constructors, getters, setters...
        
        public boolean isOnline() {
            return online && System.currentTimeMillis() - lastHeartbeat < 30000; // 30 seconds
        }
        
        public int getCurrentPlayers() {
            // This would be updated by heartbeat
            return 0; // Placeholder
        }
    }
    
    public static class ServerHealth {
        private String serverId;
        private double tps;
        private long memoryUsage;
        private long uptime;
        private int playerCount;
        private long lastUpdate;
        
        // Constructors, getters, setters...
    }
    
    public void registerServer(String serverId, String host, int port, String region, String gameMode) {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setServerId(serverId);
        serverInfo.setHost(host);
        serverInfo.setPort(port);
        serverInfo.setRegion(region);
        serverInfo.setGameMode(gameMode);
        serverInfo.setMaxPlayers(100); // Default
        serverInfo.setLastHeartbeat(System.currentTimeMillis());
        
        servers.put(serverId, serverInfo);
        save();
        
        getLogger().info("Registered server: " + serverId + " in region: " + region);
    }
    
    public void unregisterServer(String serverId) {
        servers.remove(serverId);
        serverHealth.remove(serverId);
        save();
        
        getLogger().info("Unregistered server: " + serverId);
    }
    
    public void updateHeartbeat(String serverId) {
        ServerInfo server = servers.get(serverId);
        if (server != null) {
            server.setLastHeartbeat(System.currentTimeMillis());
            save();
        }
    }
    
    public void updateHealth(String serverId, ServerHealth health) {
        serverHealth.put(serverId, health);
        save();
    }
    
    public List<ServerInfo> getServersByRegion(String region) {
        return servers.values().stream()
            .filter(server -> region.equals(server.getRegion()) && server.isOnline())
            .collect(Collectors.toList());
    }
    
    public List<ServerInfo> getServersByGameMode(String gameMode) {
        return servers.values().stream()
            .filter(server -> gameMode.equals(server.getGameMode()) && server.isOnline())
            .collect(Collectors.toList());
    }
    
    public ServerInfo getLeastLoadedServer(String gameMode) {
        return servers.values().stream()
            .filter(server -> gameMode.equals(server.getGameMode()) && server.isOnline())
            .min(Comparator.comparingInt(ServerInfo::getCurrentPlayers))
            .orElse(null);
    }
    
    public Map<String, Integer> getRegionPlayerCounts() {
        Map<String, Integer> regionCounts = new HashMap<>();
        
        for (ServerInfo server : servers.values()) {
            if (server.isOnline()) {
                String region = server.getRegion();
                regionCounts.put(region, regionCounts.getOrDefault(region, 0) + server.getCurrentPlayers());
            }
        }
        
        return regionCounts;
    }
    
    public List<ServerInfo> getAllOnlineServers() {
        return servers.values().stream()
            .filter(ServerInfo::isOnline)
            .collect(Collectors.toList());
    }
    
    public boolean isServerOnline(String serverId) {
        ServerInfo server = servers.get(serverId);
        return server != null && server.isOnline();
    }
    
    private Logger getLogger() {
        return null; // Return your logger
    }
    
    // Getters and setters...
}
```

### Server Discovery Service

```java
public class ServerDiscoveryService {
    
    private final ServerRegistry serverRegistry;
    private final ScheduledExecutorService heartbeatScheduler;
    private final String currentServerId;
    
    public ServerDiscoveryService(String currentServerId) {
        this.currentServerId = currentServerId;
        ConfigManager configManager = MongoConfigsAPI.getConfigManager();
        this.serverRegistry = configManager.loadObject(ServerRegistry.class);
        
        this.heartbeatScheduler = Executors.newScheduledThreadPool(1);
        
        // Register this server
        registerCurrentServer();
        
        // Start heartbeat
        startHeartbeat();
        
        // Watch for server changes
        watchServerChanges();
    }
    
    private void registerCurrentServer() {
        String host = getServerHost();
        int port = getServerPort();
        String region = getServerRegion();
        String gameMode = getServerGameMode();
        
        serverRegistry.registerServer(currentServerId, host, port, region, gameMode);
    }
    
    private void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                serverRegistry.updateHeartbeat(currentServerId);
                
                // Update health information
                ServerHealth health = collectServerHealth();
                serverRegistry.updateHealth(currentServerId, health);
                
            } catch (Exception e) {
                getLogger().error("Failed to send heartbeat", e);
            }
        }, 0, 10, TimeUnit.SECONDS); // Every 10 seconds
    }
    
    private void watchServerChanges() {
        MongoCollection<Document> collection = MongoConfigsAPI.getMongoManager()
            .getCollection("server-registry");
        
        collection.watch().forEach(change -> {
            if ("insert".equals(change.getOperationType().getValue()) ||
                "update".equals(change.getOperationType().getValue())) {
                
                String changedServerId = change.getDocumentKey().getString("_id").getValue();
                if (!changedServerId.equals(currentServerId)) {
                    onServerChange(changedServerId, change);
                }
            }
        });
    }
    
    private void onServerChange(String serverId, ChangeStreamDocument<Document> change) {
        ServerInfo serverInfo = serverRegistry.getServerInfo(serverId);
        if (serverInfo != null) {
            getLogger().info("Server " + serverId + " changed: " + change.getOperationType());
            
            // Notify other components
            Bukkit.getPluginManager().callEvent(new ServerChangeEvent(serverId, change));
        }
    }
    
    public List<ServerInfo> discoverServers(String gameMode) {
        return serverRegistry.getServersByGameMode(gameMode);
    }
    
    public ServerInfo findBestServer(String gameMode, String preferredRegion) {
        List<ServerInfo> candidates = new ArrayList<>();
        
        // First try preferred region
        if (preferredRegion != null) {
            candidates = serverRegistry.getServersByRegion(preferredRegion).stream()
                .filter(server -> gameMode.equals(server.getGameMode()))
                .collect(Collectors.toList());
        }
        
        // If no servers in preferred region, get all servers for game mode
        if (candidates.isEmpty()) {
            candidates = serverRegistry.getServersByGameMode(gameMode);
        }
        
        // Return least loaded server
        return candidates.stream()
            .min(Comparator.comparingInt(ServerInfo::getCurrentPlayers))
            .orElse(null);
    }
    
    private ServerHealth collectServerHealth() {
        ServerHealth health = new ServerHealth();
        health.setServerId(currentServerId);
        health.setTps(getServerTPS());
        health.setMemoryUsage(getMemoryUsage());
        health.setUptime(getServerUptime());
        health.setPlayerCount(Bukkit.getOnlinePlayers().size());
        health.setLastUpdate(System.currentTimeMillis());
        
        return health;
    }
    
    private String getServerHost() {
        return "localhost"; // Get actual server host
    }
    
    private int getServerPort() {
        return Bukkit.getServer().getPort();
    }
    
    private String getServerRegion() {
        return "us-east"; // Get from config
    }
    
    private String getServerGameMode() {
        return "survival"; // Get from config
    }
    
    private double getServerTPS() {
        // Get TPS from server monitoring
        return 20.0; // Placeholder
    }
    
    private long getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    private long getServerUptime() {
        // Get server uptime
        return System.currentTimeMillis() - getServerStartTime();
    }
    
    private long getServerStartTime() {
        return System.currentTimeMillis() - 3600000; // Placeholder: 1 hour ago
    }
    
    private Logger getLogger() {
        return null; // Return logger
    }
    
    public void shutdown() {
        heartbeatScheduler.shutdown();
        serverRegistry.unregisterServer(currentServerId);
    }
}
```

---

## Load Balancing

### Load Balancer Implementation

```java
public class LoadBalancer {
    
    private final ServerDiscoveryService discoveryService;
    private final Map<String, ServerLoadTracker> loadTrackers = new ConcurrentHashMap<>();
    
    public LoadBalancer(ServerDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }
    
    public ServerInfo getOptimalServer(String gameMode, Player player) {
        List<ServerInfo> availableServers = discoveryService.discoverServers(gameMode);
        
        if (availableServers.isEmpty()) {
            return null;
        }
        
        // Filter out full servers
        availableServers = availableServers.stream()
            .filter(server -> server.getCurrentPlayers() < server.getMaxPlayers())
            .collect(Collectors.toList());
        
        if (availableServers.isEmpty()) {
            return null;
        }
        
        // Use weighted load balancing
        return selectServerByWeightedLoad(availableServers, player);
    }
    
    private ServerInfo selectServerByWeightedLoad(List<ServerInfo> servers, Player player) {
        double totalWeight = 0;
        
        for (ServerInfo server : servers) {
            double weight = calculateServerWeight(server, player);
            totalWeight += weight;
            
            ServerLoadTracker tracker = loadTrackers.computeIfAbsent(
                server.getServerId(), k -> new ServerLoadTracker());
            tracker.setWeight(weight);
        }
        
        // Select server based on weights
        double random = Math.random() * totalWeight;
        double currentWeight = 0;
        
        for (ServerInfo server : servers) {
            ServerLoadTracker tracker = loadTrackers.get(server.getServerId());
            currentWeight += tracker.getWeight();
            
            if (random <= currentWeight) {
                return server;
            }
        }
        
        // Fallback to first server
        return servers.get(0);
    }
    
    private double calculateServerWeight(ServerInfo server, Player player) {
        double baseWeight = 1.0;
        
        // Factor in server load
        double loadFactor = 1.0 - (double) server.getCurrentPlayers() / server.getMaxPlayers();
        baseWeight *= loadFactor;
        
        // Factor in geographic proximity
        double distanceFactor = calculateDistanceFactor(server, player);
        baseWeight *= distanceFactor;
        
        // Factor in server performance
        double performanceFactor = getServerPerformanceFactor(server);
        baseWeight *= performanceFactor;
        
        return Math.max(0.1, baseWeight); // Minimum weight
    }
    
    private double calculateDistanceFactor(ServerInfo server, Player player) {
        // Calculate geographic distance between player and server
        // This would use IP geolocation or player location data
        String playerRegion = getPlayerRegion(player);
        String serverRegion = server.getRegion();
        
        if (playerRegion.equals(serverRegion)) {
            return 1.5; // Prefer same region
        } else if (isNearbyRegion(playerRegion, serverRegion)) {
            return 1.0; // Neutral for nearby regions
        } else {
            return 0.5; // Penalize distant regions
        }
    }
    
    private double getServerPerformanceFactor(ServerInfo server) {
        // Get server TPS and other performance metrics
        // This would integrate with the health monitoring system
        return 1.0; // Placeholder
    }
    
    private String getPlayerRegion(Player player) {
        // Determine player's geographic region
        // This could use IP geolocation or stored player data
        return "us-east"; // Placeholder
    }
    
    private boolean isNearbyRegion(String region1, String region2) {
        // Define which regions are considered nearby
        Map<String, Set<String>> nearbyRegions = Map.of(
            "us-east", Set.of("us-west", "eu-west"),
            "us-west", Set.of("us-east", "asia"),
            "eu-west", Set.of("us-east", "eu-east"),
            "asia", Set.of("us-west", "oceania")
        );
        
        Set<String> nearby = nearbyRegions.get(region1);
        return nearby != null && nearby.contains(region2);
    }
    
    public void updateServerLoad(String serverId, int playerCount) {
        ServerLoadTracker tracker = loadTrackers.computeIfAbsent(serverId, k -> new ServerLoadTracker());
        tracker.setPlayerCount(playerCount);
        tracker.setLastUpdate(System.currentTimeMillis());
    }
    
    public Map<String, Double> getLoadDistribution() {
        Map<String, Double> distribution = new HashMap<>();
        double totalLoad = 0;
        
        for (ServerLoadTracker tracker : loadTrackers.values()) {
            totalLoad += tracker.getPlayerCount();
        }
        
        for (Map.Entry<String, ServerLoadTracker> entry : loadTrackers.entrySet()) {
            double percentage = totalLoad > 0 ? (entry.getValue().getPlayerCount() / totalLoad) * 100 : 0;
            distribution.put(entry.getKey(), percentage);
        }
        
        return distribution;
    }
    
    private static class ServerLoadTracker {
        private double weight = 1.0;
        private int playerCount = 0;
        private long lastUpdate = 0;
        
        // Getters and setters...
    }
}
```

### Intelligent Server Selection

```java
public class IntelligentServerSelector {
    
    private final LoadBalancer loadBalancer;
    private final PlayerTracker playerTracker;
    
    public IntelligentServerSelector(LoadBalancer loadBalancer, PlayerTracker playerTracker) {
        this.loadBalancer = loadBalancer;
        this.playerTracker = playerTracker;
    }
    
    public ServerInfo selectBestServer(Player player, String requestedGameMode) {
        // Get player's preferences and history
        PlayerPreferences prefs = playerTracker.getPlayerPreferences(player);
        
        // Determine optimal game mode if not specified
        String gameMode = requestedGameMode != null ? requestedGameMode : 
            determineOptimalGameMode(player, prefs);
        
        // Get candidate servers
        List<ServerInfo> candidates = getCandidateServers(gameMode, player, prefs);
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        // Apply intelligent filtering and scoring
        return selectBestCandidate(candidates, player, prefs);
    }
    
    private String determineOptimalGameMode(Player player, PlayerPreferences prefs) {
        // Analyze player's recent activity
        Map<String, Integer> recentActivity = prefs.getRecentGameModeActivity();
        
        if (recentActivity.isEmpty()) {
            return "survival"; // Default
        }
        
        // Return most played game mode in last 7 days
        return recentActivity.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("survival");
    }
    
    private List<ServerInfo> getCandidateServers(String gameMode, Player player, PlayerPreferences prefs) {
        List<ServerInfo> candidates = new ArrayList<>();
        
        // Get servers for requested game mode
        List<ServerInfo> gameModeServers = loadBalancer.getLoadBalancer()
            .discoverServers(gameMode);
        
        for (ServerInfo server : gameModeServers) {
            if (isServerSuitable(server, player, prefs)) {
                candidates.add(server);
            }
        }
        
        return candidates;
    }
    
    private boolean isServerSuitable(ServerInfo server, Player player, PlayerPreferences prefs) {
        // Check if server has capacity
        if (server.getCurrentPlayers() >= server.getMaxPlayers()) {
            return false;
        }
        
        // Check player's ban status on server
        if (isPlayerBannedOnServer(player, server)) {
            return false;
        }
        
        // Check server requirements (VIP, rank, etc.)
        if (!meetsServerRequirements(player, server)) {
            return false;
        }
        
        // Check geographic preference
        String preferredRegion = prefs.getPreferredRegion();
        if (preferredRegion != null && !preferredRegion.equals(server.getRegion())) {
            // Allow some flexibility for better load balancing
            if (!isNearbyRegion(preferredRegion, server.getRegion())) {
                return false;
            }
        }
        
        return true;
    }
    
    private ServerInfo selectBestCandidate(List<ServerInfo> candidates, Player player, PlayerPreferences prefs) {
        // Score each candidate
        Map<ServerInfo, Double> scores = new HashMap<>();
        
        for (ServerInfo server : candidates) {
            double score = calculateServerScore(server, player, prefs);
            scores.put(server, score);
        }
        
        // Return highest scoring server
        return scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }
    
    private double calculateServerScore(ServerInfo server, Player player, PlayerPreferences prefs) {
        double score = 0;
        
        // Geographic proximity (0-20 points)
        score += calculateGeographicScore(server, prefs);
        
        // Load balance (0-20 points)
        score += calculateLoadBalanceScore(server);
        
        // Player preference match (0-20 points)
        score += calculatePreferenceScore(server, prefs);
        
        // Server performance (0-20 points)
        score += calculatePerformanceScore(server);
        
        // Friend proximity (0-20 points)
        score += calculateFriendProximityScore(server, player);
        
        return score;
    }
    
    private double calculateGeographicScore(ServerInfo server, PlayerPreferences prefs) {
        String preferredRegion = prefs.getPreferredRegion();
        if (preferredRegion == null) return 10; // Neutral
        
        if (preferredRegion.equals(server.getRegion())) {
            return 20; // Perfect match
        } else if (isNearbyRegion(preferredRegion, server.getRegion())) {
            return 15; // Good match
        } else {
            return 5; // Poor match
        }
    }
    
    private double calculateLoadBalanceScore(ServerInfo server) {
        double loadPercentage = (double) server.getCurrentPlayers() / server.getMaxPlayers();
        return 20 * (1 - loadPercentage); // Higher score for less loaded servers
    }
    
    private double calculatePreferenceScore(ServerInfo server, PlayerPreferences prefs) {
        // Check if server matches player's preferred settings
        if (prefs.getPreferredGameMode().equals(server.getGameMode())) {
            return 20;
        }
        return 10;
    }
    
    private double calculatePerformanceScore(ServerInfo server) {
        // This would use server health data
        return 15; // Placeholder
    }
    
    private double calculateFriendProximityScore(ServerInfo server, Player player) {
        // Check how many friends are on this server
        int friendCount = countFriendsOnServer(server, player);
        return Math.min(20, friendCount * 5); // Up to 20 points
    }
    
    // Helper methods...
    private boolean isPlayerBannedOnServer(Player player, ServerInfo server) {
        return false; // Placeholder
    }
    
    private boolean meetsServerRequirements(Player player, ServerInfo server) {
        return true; // Placeholder
    }
    
    private boolean isNearbyRegion(String region1, String region2) {
        return true; // Placeholder
    }
    
    private int countFriendsOnServer(ServerInfo server, Player player) {
        return 0; // Placeholder
    }
}
```

---

## Cross-Server Communication

### Message Broker System

```java
public class CrossServerMessenger {
    
    private final MongoManager mongoManager;
    private final String serverId;
    private final Map<String, MessageHandler> messageHandlers = new ConcurrentHashMap<>();
    
    public CrossServerMessenger(MongoManager mongoManager, String serverId) {
        this.mongoManager = mongoManager;
        this.serverId = serverId;
        
        // Start listening for messages
        startMessageListener();
    }
    
    public void registerHandler(String messageType, MessageHandler handler) {
        messageHandlers.put(messageType, handler);
    }
    
    public void sendMessage(String targetServerId, String messageType, Map<String, Object> data) {
        Document message = new Document()
            .append("_id", UUID.randomUUID().toString())
            .append("fromServer", serverId)
            .append("toServer", targetServerId)
            .append("messageType", messageType)
            .append("data", data)
            .append("timestamp", System.currentTimeMillis())
            .append("processed", false);
        
        mongoManager.getCollection("cross-server-messages").insertOne(message);
    }
    
    public void broadcastMessage(String messageType, Map<String, Object> data) {
        Document message = new Document()
            .append("_id", UUID.randomUUID().toString())
            .append("fromServer", serverId)
            .append("toServer", "broadcast")
            .append("messageType", messageType)
            .append("data", data)
            .append("timestamp", System.currentTimeMillis())
            .append("processed", false);
        
        mongoManager.getCollection("cross-server-messages").insertOne(message);
    }
    
    private void startMessageListener() {
        MongoCollection<Document> collection = mongoManager.getCollection("cross-server-messages");
        
        List<Bson> pipeline = Arrays.asList(
            match(or(
                eq("toServer", serverId),
                eq("toServer", "broadcast")
            )),
            match(eq("processed", false))
        );
        
        collection.watch(pipeline).forEach(this::processMessage);
    }
    
    private void processMessage(ChangeStreamDocument<Document> change) {
        if (!"insert".equals(change.getOperationType().getValue())) return;
        
        Document message = change.getFullDocument();
        String messageId = message.getString("_id");
        String messageType = message.getString("messageType");
        String fromServer = message.getString("fromServer");
        
        // Mark as processed
        markMessageProcessed(messageId);
        
        // Handle message
        MessageHandler handler = messageHandlers.get(messageType);
        if (handler != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) message.get("data");
                handler.handleMessage(fromServer, data);
            } catch (Exception e) {
                getLogger().error("Error handling cross-server message: " + messageType, e);
            }
        } else {
            getLogger().warning("No handler for message type: " + messageType);
        }
    }
    
    private void markMessageProcessed(String messageId) {
        mongoManager.getCollection("cross-server-messages")
            .updateOne(eq("_id", messageId), set("processed", true));
    }
    
    public interface MessageHandler {
        void handleMessage(String fromServer, Map<String, Object> data);
    }
    
    private Logger getLogger() {
        return null; // Return logger
    }
}
```

### Player Transfer System

```java
public class PlayerTransferSystem {
    
    private final CrossServerMessenger messenger;
    private final ServerDiscoveryService discoveryService;
    
    public PlayerTransferSystem(CrossServerMessenger messenger, ServerDiscoveryService discoveryService) {
        this.messenger = messenger;
        this.discoveryService = discoveryService;
        
        // Register message handlers
        messenger.registerHandler("player-transfer-request", this::handleTransferRequest);
        messenger.registerHandler("player-transfer-response", this::handleTransferResponse);
    }
    
    public void requestPlayerTransfer(Player player, String targetServerId) {
        // Check if target server is online
        if (!discoveryService.isServerOnline(targetServerId)) {
            player.sendMessage(ChatColor.RED + "Target server is not available.");
            return;
        }
        
        // Save player data before transfer
        savePlayerData(player);
        
        // Send transfer request
        Map<String, Object> transferData = Map.of(
            "playerId", player.getUniqueId().toString(),
            "playerName", player.getName(),
            "currentServer", getCurrentServerId(),
            "transferId", UUID.randomUUID().toString()
        );
        
        messenger.sendMessage(targetServerId, "player-transfer-request", transferData);
        
        // Store transfer state
        setPlayerTransferState(player, "pending", targetServerId);
        
        player.sendMessage(ChatColor.YELLOW + "Transferring to " + targetServerId + "...");
    }
    
    private void handleTransferRequest(String fromServer, Map<String, Object> data) {
        String playerId = (String) data.get("playerId");
        String playerName = (String) data.get("playerName");
        String transferId = (String) data.get("transferId");
        
        // Check if server can accept the player
        if (canAcceptPlayer()) {
            // Accept transfer
            Map<String, Object> responseData = Map.of(
                "transferId", transferId,
                "accepted", true,
                "serverAddress", getServerAddress(),
                "serverPort", getServerPort()
            );
            
            messenger.sendMessage(fromServer, "player-transfer-response", responseData);
            
            // Prepare for player arrival
            prepareForPlayerArrival(playerId, playerName);
            
        } else {
            // Reject transfer
            Map<String, Object> responseData = Map.of(
                "transferId", transferId,
                "accepted", false,
                "reason", "Server is full"
            );
            
            messenger.sendMessage(fromServer, "player-transfer-response", responseData);
        }
    }
    
    private void handleTransferResponse(String fromServer, Map<String, Object> data) {
        String transferId = (String) data.get("transferId");
        boolean accepted = (Boolean) data.get("accepted");
        
        Player player = getPlayerByTransferId(transferId);
        if (player == null) return;
        
        if (accepted) {
            String serverAddress = (String) data.get("serverAddress");
            int serverPort = (Integer) data.get("serverPort");
            
            // Connect player to target server
            connectPlayerToServer(player, serverAddress, serverPort);
            
            player.sendMessage(ChatColor.GREEN + "Transferring...");
            
        } else {
            String reason = (String) data.get("reason");
            player.sendMessage(ChatColor.RED + "Transfer failed: " + reason);
            
            // Clear transfer state
            clearPlayerTransferState(player);
        }
    }
    
    private void savePlayerData(Player player) {
        // Save inventory, location, etc.
        PlayerData playerData = new PlayerData();
        playerData.setPlayerId(player.getUniqueId().toString());
        playerData.setInventory(serializeInventory(player.getInventory()));
        playerData.setLocation(serializeLocation(player.getLocation()));
        playerData.setHealth(player.getHealth());
        playerData.setFoodLevel(player.getFoodLevel());
        
        // Save to database
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        cm.saveObject(playerData);
    }
    
    private void prepareForPlayerArrival(String playerId, String playerName) {
        // Load player data
        ConfigManager cm = MongoConfigsAPI.getConfigManager();
        PlayerData playerData = cm.loadObject(PlayerData.class, "playerId", playerId);
        
        if (playerData != null) {
            // Store data for when player connects
            setPendingPlayerData(playerId, playerData);
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerId = player.getUniqueId().toString();
        
        // Check if this player is transferring
        PlayerData pendingData = getPendingPlayerData(playerId);
        if (pendingData != null) {
            // Restore player data
            restorePlayerData(player, pendingData);
            
            // Clear pending data
            clearPendingPlayerData(playerId);
            
            player.sendMessage(ChatColor.GREEN + "Welcome! Your data has been restored.");
        }
    }
    
    private void restorePlayerData(Player player, PlayerData data) {
        // Restore inventory, location, etc.
        player.getInventory().setContents(deserializeInventory(data.getInventory()));
        player.teleport(deserializeLocation(data.getLocation()));
        player.setHealth(data.getHealth());
        player.setFoodLevel(data.getFoodLevel());
    }
    
    // Helper methods for serialization/deserialization...
    private String serializeInventory(Inventory inventory) {
        // Serialize inventory to JSON/string
        return "{}"; // Placeholder
    }
    
    private ItemStack[] deserializeInventory(String data) {
        // Deserialize inventory from string
        return new ItemStack[0]; // Placeholder
    }
    
    private String serializeLocation(Location location) {
        return location.getWorld().getName() + "," + 
               location.getX() + "," + 
               location.getY() + "," + 
               location.getZ();
    }
    
    private Location deserializeLocation(String data) {
        String[] parts = data.split(",");
        World world = Bukkit.getWorld(parts[0]);
        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        return new Location(world, x, y, z);
    }
    
    private boolean canAcceptPlayer() {
        return Bukkit.getOnlinePlayers().size() < Bukkit.getMaxPlayers();
    }
    
    private String getCurrentServerId() {
        return "server-1"; // Get actual server ID
    }
    
    private String getServerAddress() {
        return "localhost";
    }
    
    private int getServerPort() {
        return 25565;
    }
    
    private void connectPlayerToServer(Player player, String address, int port) {
        // Use BungeeCord/Velocity plugin messaging to transfer player
        // This requires a proxy setup
    }
    
    // State management methods...
    private void setPlayerTransferState(Player player, String state, String targetServer) {
        // Store in memory/database
    }
    
    private void clearPlayerTransferState(Player player) {
        // Clear transfer state
    }
    
    private Player getPlayerByTransferId(String transferId) {
        // Find player by transfer ID
        return null; // Placeholder
    }
    
    private void setPendingPlayerData(String playerId, PlayerData data) {
        // Store pending data
    }
    
    private PlayerData getPendingPlayerData(String playerId) {
        // Get pending data
        return null; // Placeholder
    }
    
    private void clearPendingPlayerData(String playerId) {
        // Clear pending data
    }
}
```

---

## Data Synchronization

### Global Player Data

```java
@ConfigsFileProperties(name = "global-player-data")
@ConfigsDatabase("minecraft")
public class GlobalPlayerData extends MongoConfig<GlobalPlayerData> {
    
    private Map<String, PlayerProfile> playerProfiles = new ConcurrentHashMap<>();
    private Map<String, CrossServerInventory> sharedInventories = new ConcurrentHashMap<>();
    private Map<String, GlobalStats> globalStatistics = new ConcurrentHashMap<>();
    
    public static class PlayerProfile {
        private String playerId;
        private String playerName;
        private String rank;
        private long firstJoin;
        private long lastSeen;
        private Set<String> permissions = new HashSet<>();
        private Map<String, Object> preferences = new HashMap<>();
        
        // Constructors, getters, setters...
    }
    
    public static class CrossServerInventory {
        private String playerId;
        private ItemStack[] inventory;
        private ItemStack[] armor;
        private ItemStack[] enderChest;
        private long lastSync;
        
        // Constructors, getters, setters...
    }
    
    public static class GlobalStats {
        private String playerId;
        private int totalPlayTime;
        private int serversVisited;
        private Map<String, Integer> gameModeStats = new HashMap<>();
        private Map<String, Integer> achievementCounts = new HashMap<>();
        
        // Constructors, getters, setters...
    }
    
    public PlayerProfile getPlayerProfile(String playerId) {
        return playerProfiles.computeIfAbsent(playerId, k -> new PlayerProfile());
    }
    
    public void updatePlayerProfile(String playerId, Consumer<PlayerProfile> updater) {
        PlayerProfile profile = getPlayerProfile(playerId);
        updater.accept(profile);
        save();
    }
    
    public CrossServerInventory getSharedInventory(String playerId) {
        return sharedInventories.computeIfAbsent(playerId, k -> new CrossServerInventory());
    }
    
    public void syncPlayerInventory(String playerId, PlayerInventory inventory) {
        CrossServerInventory shared = getSharedInventory(playerId);
        shared.setInventory(inventory.getContents());
        shared.setArmor(inventory.getArmorContents());
        shared.setLastSync(System.currentTimeMillis());
        save();
    }
    
    public void loadPlayerInventory(String playerId, PlayerInventory inventory) {
        CrossServerInventory shared = sharedInventories.get(playerId);
        if (shared != null) {
            inventory.setContents(shared.getInventory());
            inventory.setArmorContents(shared.getArmor());
        }
    }
    
    public GlobalStats getGlobalStats(String playerId) {
        return globalStatistics.computeIfAbsent(playerId, k -> new GlobalStats());
    }
    
    public void updateGlobalStats(String playerId, Consumer<GlobalStats> updater) {
        GlobalStats stats = getGlobalStats(playerId);
        updater.accept(stats);
        save();
    }
    
    public List<String> getTopPlayersByStat(String statName, int limit) {
        return globalStatistics.values().stream()
            .filter(stats -> stats.getAchievementCounts().containsKey(statName))
            .sorted((a, b) -> Integer.compare(
                b.getAchievementCounts().get(statName),
                a.getAchievementCounts().get(statName)))
            .limit(limit)
            .map(GlobalStats::getPlayerId)
            .collect(Collectors.toList());
    }
    
    public Map<String, Integer> getServerPlayerCounts() {
        Map<String, Integer> counts = new HashMap<>();
        
        for (PlayerProfile profile : playerProfiles.values()) {
            // This would require additional tracking
            counts.put("total", counts.getOrDefault("total", 0) + 1);
        }
        
        return counts;
    }
    
    // Getters and setters...
}
```

### Real-time Synchronization

```java
public class DataSynchronizationService {
    
    private final GlobalPlayerData globalData;
    private final CrossServerMessenger messenger;
    private final Map<String, Long> lastSyncTimes = new ConcurrentHashMap<>();
    
    public DataSynchronizationService(GlobalPlayerData globalData, CrossServerMessenger messenger) {
        this.globalData = globalData;
        this.messenger = messenger;
        
        // Register sync handlers
        messenger.registerHandler("player-data-sync", this::handlePlayerDataSync);
        messenger.registerHandler("inventory-sync", this::handleInventorySync);
        messenger.registerHandler("stats-sync", this::handleStatsSync);
    }
    
    public void syncPlayerData(Player player) {
        String playerId = player.getUniqueId().toString();
        long lastSync = lastSyncTimes.getOrDefault(playerId, 0L);
        
        // Only sync if enough time has passed
        if (System.currentTimeMillis() - lastSync < 30000) { // 30 seconds
            return;
        }
        
        // Collect player data
        Map<String, Object> playerData = collectPlayerData(player);
        
        // Send to all servers
        messenger.broadcastMessage("player-data-sync", Map.of(
            "playerId", playerId,
            "data", playerData
        ));
        
        lastSyncTimes.put(playerId, System.currentTimeMillis());
    }
    
    private Map<String, Object> collectPlayerData(Player player) {
        return Map.of(
            "name", player.getName(),
            "health", player.getHealth(),
            "food", player.getFoodLevel(),
            "level", player.getLevel(),
            "exp", player.getExp(),
            "location", serializeLocation(player.getLocation()),
            "gameMode", player.getGameMode().toString()
        );
    }
    
    private void handlePlayerDataSync(String fromServer, Map<String, Object> data) {
        String playerId = (String) data.get("playerId");
        @SuppressWarnings("unchecked")
        Map<String, Object> playerData = (Map<String, Object>) data.get("data");
        
        // Update global player data
        globalData.updatePlayerProfile(playerId, profile -> {
            profile.setPlayerName((String) playerData.get("name"));
            profile.setLastSeen(System.currentTimeMillis());
        });
        
        // Notify local systems
        notifyPlayerDataUpdate(playerId, playerData);
    }
    
    public void syncPlayerInventory(Player player) {
        String playerId = player.getUniqueId().toString();
        
        Map<String, Object> inventoryData = Map.of(
            "inventory", serializeInventory(player.getInventory().getContents()),
            "armor", serializeInventory(player.getInventory().getArmorContents()),
            "enderChest", serializeInventory(player.getEnderChest().getContents())
        );
        
        messenger.broadcastMessage("inventory-sync", Map.of(
            "playerId", playerId,
            "data", inventoryData
        ));
    }
    
    private void handleInventorySync(String fromServer, Map<String, Object> data) {
        String playerId = (String) data.get("playerId");
        @SuppressWarnings("unchecked")
        Map<String, Object> inventoryData = (Map<String, Object>) data.get("data");
        
        // Update shared inventory
        globalData.syncPlayerInventory(playerId, deserializeInventory(inventoryData));
    }
    
    public void syncPlayerStats(Player player, String statName, int value) {
        String playerId = player.getUniqueId().toString();
        
        messenger.broadcastMessage("stats-sync", Map.of(
            "playerId", playerId,
            "statName", statName,
            "value", value
        ));
    }
    
    private void handleStatsSync(String fromServer, Map<String, Object> data) {
        String playerId = (String) data.get("playerId");
        String statName = (String) data.get("statName");
        int value = (Integer) data.get("value");
        
        // Update global stats
        globalData.updateGlobalStats(playerId, stats -> {
            stats.getAchievementCounts().put(statName, value);
        });
    }
    
    private void notifyPlayerDataUpdate(String playerId, Map<String, Object> data) {
        // Notify other plugins/systems about the update
        PlayerDataUpdateEvent event = new PlayerDataUpdateEvent(playerId, data);
        Bukkit.getPluginManager().callEvent(event);
    }
    
    // Serialization helper methods...
    private String serializeLocation(Location location) {
        return location.getWorld().getName() + "," + 
               location.getX() + "," + 
               location.getY() + "," + 
               location.getZ() + "," +
               location.getYaw() + "," +
               location.getPitch();
    }
    
    private String serializeInventory(ItemStack[] items) {
        // Serialize to JSON
        return "[]"; // Placeholder
    }
    
    private PlayerInventory deserializeInventory(Map<String, Object> data) {
        // This would create a temporary inventory object
        return null; // Placeholder
    }
}
```

---

## Global Configuration

### Global Server Configuration

```java
@ConfigsFileProperties(name = "global-server-config")
@ConfigsDatabase("minecraft")
public class GlobalServerConfig extends MongoConfig<GlobalServerConfig> {
    
    private Map<String, ServerConfig> serverConfigs = new ConcurrentHashMap<>();
    private Map<String, GlobalRule> globalRules = new ConcurrentHashMap<>();
    private Map<String, MaintenanceWindow> maintenanceWindows = new ConcurrentHashMap<>();
    
    public static class ServerConfig {
        private String serverId;
        private String serverName;
        private String gameMode;
        private int maxPlayers;
        private boolean pvpEnabled;
        private boolean netherEnabled;
        private boolean endEnabled;
        private Map<String, Object> customSettings = new HashMap<>();
        
        // Constructors, getters, setters...
    }
    
    public static class GlobalRule {
        private String ruleId;
        private String ruleName;
        private String description;
        private boolean enabled;
        private Map<String, Object> parameters = new HashMap<>();
        
        // Constructors, getters, setters...
    }
    
    public static class MaintenanceWindow {
        private String windowId;
        private String serverId;
        private long startTime;
        private long endTime;
        private String reason;
        private boolean active;
        
        // Constructors, getters, setters...
    }
    
    public ServerConfig getServerConfig(String serverId) {
        return serverConfigs.computeIfAbsent(serverId, k -> new ServerConfig());
    }
    
    public void updateServerConfig(String serverId, Consumer<ServerConfig> updater) {
        ServerConfig config = getServerConfig(serverId);
        updater.accept(config);
        save();
        
        // Notify server of config change
        notifyServerConfigChange(serverId);
    }
    
    public GlobalRule getGlobalRule(String ruleId) {
        return globalRules.computeIfAbsent(ruleId, k -> new GlobalRule());
    }
    
    public void updateGlobalRule(String ruleId, Consumer<GlobalRule> updater) {
        GlobalRule rule = getGlobalRule(ruleId);
        updater.accept(rule);
        save();
        
        // Notify all servers of rule change
        notifyGlobalRuleChange(ruleId);
    }
    
    public List<MaintenanceWindow> getActiveMaintenanceWindows() {
        return maintenanceWindows.values().stream()
            .filter(MaintenanceWindow::isActive)
            .collect(Collectors.toList());
    }
    
    public void scheduleMaintenance(String serverId, long startTime, long duration, String reason) {
        MaintenanceWindow window = new MaintenanceWindow();
        window.setWindowId(UUID.randomUUID().toString());
        window.setServerId(serverId);
        window.setStartTime(startTime);
        window.setEndTime(startTime + duration);
        window.setReason(reason);
        window.setActive(true);
        
        maintenanceWindows.put(window.getWindowId(), window);
        save();
        
        // Notify affected server
        notifyMaintenanceScheduled(serverId, window);
    }
    
    public boolean isServerInMaintenance(String serverId) {
        long now = System.currentTimeMillis();
        
        return maintenanceWindows.values().stream()
            .anyMatch(window -> 
                window.getServerId().equals(serverId) && 
                window.isActive() && 
                now >= window.getStartTime() && 
                now <= window.getEndTime());
    }
    
    private void notifyServerConfigChange(String serverId) {
        // Send notification to specific server
        Map<String, Object> data = Map.of(
            "serverId", serverId,
            "config", getServerConfig(serverId)
        );
        
        // This would use the cross-server messenger
        getCrossServerMessenger().sendMessage(serverId, "config-update", data);
    }
    
    private void notifyGlobalRuleChange(String ruleId) {
        // Send notification to all servers
        Map<String, Object> data = Map.of(
            "ruleId", ruleId,
            "rule", getGlobalRule(ruleId)
        );
        
        getCrossServerMessenger().broadcastMessage("global-rule-update", data);
    }
    
    private void notifyMaintenanceScheduled(String serverId, MaintenanceWindow window) {
        Map<String, Object> data = Map.of(
            "window", window
        );
        
        getCrossServerMessenger().sendMessage(serverId, "maintenance-scheduled", data);
    }
    
    private CrossServerMessenger getCrossServerMessenger() {
        // Get from service locator or dependency injection
        return null; // Placeholder
    }
    
    // Getters and setters...
}
```

---

## Monitoring and Management

### Server Monitoring Dashboard

```java
public class ServerMonitoringDashboard {
    
    private final ServerRegistry serverRegistry;
    private final GlobalPlayerData globalData;
    private final ScheduledExecutorService monitoringScheduler;
    
    public ServerMonitoringDashboard(ServerRegistry serverRegistry, GlobalPlayerData globalData) {
        this.serverRegistry = serverRegistry;
        this.globalData = globalData;
        this.monitoringScheduler = Executors.newScheduledThreadPool(1);
        
        startMonitoring();
    }
    
    private void startMonitoring() {
        // Collect metrics every 30 seconds
        monitoringScheduler.scheduleAtFixedRate(this::collectMetrics, 0, 30, TimeUnit.SECONDS);
    }
    
    private void collectMetrics() {
        try {
            NetworkMetrics metrics = collectNetworkMetrics();
            PerformanceMetrics perfMetrics = collectPerformanceMetrics();
            PlayerMetrics playerMetrics = collectPlayerMetrics();
            
            // Store metrics
            storeMetrics(metrics, perfMetrics, playerMetrics);
            
            // Check for alerts
            checkAlerts(metrics, perfMetrics, playerMetrics);
            
        } catch (Exception e) {
            getLogger().error("Error collecting metrics", e);
        }
    }
    
    private NetworkMetrics collectNetworkMetrics() {
        NetworkMetrics metrics = new NetworkMetrics();
        
        List<ServerInfo> servers = serverRegistry.getAllOnlineServers();
        metrics.setTotalServers(servers.size());
        metrics.setOnlineServers((int) servers.stream().filter(ServerInfo::isOnline).count());
        
        Map<String, Integer> regionCounts = serverRegistry.getRegionPlayerCounts();
        metrics.setRegionDistribution(regionCounts);
        
        return metrics;
    }
    
    private PerformanceMetrics collectPerformanceMetrics() {
        PerformanceMetrics metrics = new PerformanceMetrics();
        
        // Collect TPS, memory, CPU from all servers
        List<ServerInfo> servers = serverRegistry.getAllOnlineServers();
        
        double avgTps = servers.stream()
            .mapToDouble(server -> getServerTPS(server.getServerId()))
            .average()
            .orElse(20.0);
        
        metrics.setAverageTps(avgTps);
        metrics.setTotalMemoryUsage(calculateTotalMemoryUsage(servers));
        metrics.setAverageCpuUsage(calculateAverageCpuUsage(servers));
        
        return metrics;
    }
    
    private PlayerMetrics collectPlayerMetrics() {
        PlayerMetrics metrics = new PlayerMetrics();
        
        Map<String, Integer> serverCounts = globalData.getServerPlayerCounts();
        metrics.setTotalPlayers(serverCounts.getOrDefault("total", 0));
        metrics.setServerDistribution(serverCounts);
        
        // Calculate peak concurrent users
        metrics.setPeakConcurrentUsers(calculatePeakConcurrentUsers());
        
        return metrics;
    }
    
    private void checkAlerts(NetworkMetrics network, PerformanceMetrics perf, PlayerMetrics player) {
        List<String> alerts = new ArrayList<>();
        
        // Check server availability
        if (network.getOnlineServers() < network.getTotalServers() * 0.8) {
            alerts.add("Low server availability: " + network.getOnlineServers() + "/" + network.getTotalServers());
        }
        
        // Check performance
        if (perf.getAverageTps() < 18.0) {
            alerts.add("Low average TPS: " + String.format("%.2f", perf.getAverageTps()));
        }
        
        // Check memory usage
        if (perf.getTotalMemoryUsage() > 0.9) { // 90%
            alerts.add("High memory usage: " + String.format("%.1f%%", perf.getTotalMemoryUsage() * 100));
        }
        
        // Send alerts if any
        if (!alerts.isEmpty()) {
            sendAlerts(alerts);
        }
    }
    
    private void sendAlerts(List<String> alerts) {
        // Send to administrators via Discord, email, etc.
        for (String alert : alerts) {
            getLogger().warning("ALERT: " + alert);
            
            // Send to notification system
            sendNotification("Server Alert", alert);
        }
    }
    
    public Map<String, Object> getDashboardData() {
        Map<String, Object> dashboard = new HashMap<>();
        
        dashboard.put("network", collectNetworkMetrics());
        dashboard.put("performance", collectPerformanceMetrics());
        dashboard.put("players", collectPlayerMetrics());
        dashboard.put("alerts", getActiveAlerts());
        
        return dashboard;
    }
    
    public List<String> getActiveAlerts() {
        // Return current active alerts
        return new ArrayList<>(); // Placeholder
    }
    
    // Helper methods...
    private double getServerTPS(String serverId) {
        // Get TPS from server health data
        return 20.0; // Placeholder
    }
    
    private double calculateTotalMemoryUsage(List<ServerInfo> servers) {
        // Calculate total memory usage across all servers
        return 0.5; // Placeholder
    }
    
    private double calculateAverageCpuUsage(List<ServerInfo> servers) {
        // Calculate average CPU usage
        return 0.3; // Placeholder
    }
    
    private int calculatePeakConcurrentUsers() {
        // Calculate peak concurrent users
        return 1000; // Placeholder
    }
    
    private void storeMetrics(NetworkMetrics network, PerformanceMetrics perf, PlayerMetrics player) {
        // Store in database for historical tracking
    }
    
    private void sendNotification(String title, String message) {
        // Send notification via configured channels
    }
    
    private Logger getLogger() {
        return null; // Return logger
    }
    
    public void shutdown() {
        monitoringScheduler.shutdown();
    }
    
    // Metrics classes...
    public static class NetworkMetrics {
        private int totalServers;
        private int onlineServers;
        private Map<String, Integer> regionDistribution = new HashMap<>();
        
        // Getters and setters...
    }
    
    public static class PerformanceMetrics {
        private double averageTps;
        private double totalMemoryUsage;
        private double averageCpuUsage;
        
        // Getters and setters...
    }
    
    public static class PlayerMetrics {
        private int totalPlayers;
        private Map<String, Integer> serverDistribution = new HashMap<>();
        private int peakConcurrentUsers;
        
        // Getters and setters...
    }
}
```

---

## Complete Examples

### Hub Server Implementation

```java
public class HubServer implements ServerInstance {
    
    private final ServerDiscoveryService discoveryService;
    private final LoadBalancer loadBalancer;
    private final PlayerTransferSystem transferSystem;
    private final CrossServerMessenger messenger;
    
    public HubServer() {
        // Initialize services
        MongoManager mongoManager = MongoConfigsAPI.getMongoManager();
        
        this.discoveryService = new ServerDiscoveryService("hub-server");
        this.loadBalancer = new LoadBalancer(discoveryService);
        this.transferSystem = new PlayerTransferSystem(messenger, discoveryService);
        this.messenger = new CrossServerMessenger(mongoManager, "hub-server");
        
        // Register message handlers
        setupMessageHandlers();
        
        // Start services
        startServices();
    }
    
    private void setupMessageHandlers() {
        messenger.registerHandler("server-status-request", this::handleServerStatusRequest);
        messenger.registerHandler("player-count-request", this::handlePlayerCountRequest);
        messenger.registerHandler("transfer-player", this::handlePlayerTransfer);
    }
    
    private void startServices() {
        // Register this server
        discoveryService.registerCurrentServer();
        
        // Start heartbeat
        startHeartbeatTask();
        
        // Start monitoring
        startMonitoringTask();
    }
    
    private void handleServerStatusRequest(String fromServer, Map<String, Object> data) {
        Map<String, Object> status = Map.of(
            "serverId", "hub-server",
            "online", true,
            "playerCount", Bukkit.getOnlinePlayers().size(),
            "maxPlayers", Bukkit.getMaxPlayers(),
            "tps", getServerTPS()
        );
        
        messenger.sendMessage(fromServer, "server-status-response", status);
    }
    
    private void handlePlayerCountRequest(String fromServer, Map<String, Object> data) {
        Map<String, Object> counts = new HashMap<>();
        
        for (ServerInfo server : discoveryService.getAllOnlineServers()) {
            counts.put(server.getServerId(), server.getCurrentPlayers());
        }
        
        messenger.sendMessage(fromServer, "player-count-response", Map.of("counts", counts));
    }
    
    private void handlePlayerTransfer(String fromServer, Map<String, Object> data) {
        String playerId = (String) data.get("playerId");
        String targetServerId = (String) data.get("targetServerId");
        
        Player player = getPlayerById(playerId);
        if (player != null) {
            transferSystem.requestPlayerTransfer(player, targetServerId);
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Send welcome message
        sendWelcomeMessage(player);
        
        // Show server selector
        showServerSelector(player);
    }
    
    private void sendWelcomeMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "Welcome to the Network Hub!");
        player.sendMessage(ChatColor.YELLOW + "Use /servers to see available servers");
    }
    
    private void showServerSelector(Player player) {
        // Create server selector GUI
        Inventory selector = Bukkit.createInventory(null, 27, "Server Selector");
        
        List<ServerInfo> servers = discoveryService.getAllOnlineServers();
        
        for (int i = 0; i < servers.size() && i < 27; i++) {
            ServerInfo server = servers.get(i);
            ItemStack item = createServerItem(server);
            selector.setItem(i, item);
        }
        
        player.openInventory(selector);
    }
    
    private ItemStack createServerItem(ServerInfo server) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(ChatColor.GREEN + server.getServerName());
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Game Mode: " + server.getGameMode());
        lore.add(ChatColor.GRAY + "Players: " + server.getCurrentPlayers() + "/" + server.getMaxPlayers());
        lore.add(ChatColor.GRAY + "Region: " + server.getRegion());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to join!");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("Server Selector")) return;
        
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        
        String serverName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        ServerInfo targetServer = findServerByName(serverName);
        
        if (targetServer != null) {
            transferSystem.requestPlayerTransfer(player, targetServer.getServerId());
        }
    }
    
    private ServerInfo findServerByName(String serverName) {
        return discoveryService.getAllOnlineServers().stream()
            .filter(server -> server.getServerName().equals(serverName))
            .findFirst()
            .orElse(null);
    }
    
    private Player getPlayerById(String playerId) {
        return Bukkit.getPlayer(UUID.fromString(playerId));
    }
    
    private double getServerTPS() {
        // Get server TPS
        return 20.0; // Placeholder
    }
    
    private void startHeartbeatTask() {
        // Send heartbeat every 10 seconds
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            discoveryService.updateHeartbeat();
        }, 0L, 200L); // 200 ticks = 10 seconds
    }
    
    private void startMonitoringTask() {
        // Update monitoring every 30 seconds
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            updateServerMonitoring();
        }, 0L, 600L); // 600 ticks = 30 seconds
    }
    
    private void updateServerMonitoring() {
        // Update player counts for all servers
        for (ServerInfo server : discoveryService.getAllOnlineServers()) {
            int playerCount = getPlayerCountForServer(server.getServerId());
            loadBalancer.updateServerLoad(server.getServerId(), playerCount);
        }
    }
    
    private int getPlayerCountForServer(String serverId) {
        // This would query the actual server or use cached data
        return 50; // Placeholder
    }
}
```

---

## Troubleshooting

### Common Multi-Server Issues

#### 1. Server Discovery Problems

**Symptoms:**
- Servers not appearing in server list
- Heartbeat failures
- Connection timeouts

**Solutions:**
```java
// Check MongoDB connection
public void diagnoseConnection() {
    try {
        MongoManager mongo = MongoConfigsAPI.getMongoManager();
        Document ping = mongo.getDatabase().runCommand(new Document("ping", 1));
        getLogger().info("MongoDB connection successful");
    } catch (Exception e) {
        getLogger().error("MongoDB connection failed", e);
    }
}

// Verify replica set
public void checkReplicaSet() {
    MongoClient mongoClient = MongoConfigsAPI.getMongoManager().getClient();
    MongoDatabase adminDb = mongoClient.getDatabase("admin");
    
    Document replStatus = adminDb.runCommand(new Document("replSetGetStatus", 1));
    getLogger().info("Replica set status: " + replStatus.toJson());
}
```

#### 2. Load Balancing Issues

**Symptoms:**
- Uneven player distribution
- Server overload
- Poor performance

**Solutions:**
```java
// Monitor load distribution
public void monitorLoadDistribution() {
    Map<String, Double> distribution = loadBalancer.getLoadDistribution();
    
    for (Map.Entry<String, Double> entry : distribution.entrySet()) {
        getLogger().info("Server " + entry.getKey() + ": " + 
                        String.format("%.1f%%", entry.getValue()));
    }
}

// Adjust load balancing weights
public void adjustLoadBalancing() {
    for (ServerInfo server : discoveryService.getAllOnlineServers()) {
        double weight = calculateAdjustedWeight(server);
        loadBalancer.updateServerWeight(server.getServerId(), weight);
    }
}

private double calculateAdjustedWeight(ServerInfo server) {
    double baseWeight = 1.0;
    
    // Reduce weight for high-load servers
    double loadPercentage = (double) server.getCurrentPlayers() / server.getMaxPlayers();
    if (loadPercentage > 0.8) {
        baseWeight *= 0.5;
    }
    
    // Increase weight for underutilized servers
    if (loadPercentage < 0.3) {
        baseWeight *= 1.5;
    }
    
    return baseWeight;
}
```

#### 3. Cross-Server Communication Issues

**Symptoms:**
- Messages not being delivered
- Transfer failures
- Synchronization problems

**Solutions:**
```java
// Debug message delivery
public void debugMessageDelivery() {
    // Check message collection
    MongoCollection<Document> messages = mongoManager.getCollection("cross-server-messages");
    
    long pendingCount = messages.countDocuments(eq("processed", false));
    getLogger().info("Pending messages: " + pendingCount);
    
    // Check for stuck messages
    FindIterable<Document> stuckMessages = messages.find(and(
        eq("processed", false),
        lt("timestamp", System.currentTimeMillis() - 300000) // 5 minutes old
    ));
    
    for (Document message : stuckMessages) {
        getLogger().warning("Stuck message: " + message.getString("_id"));
    }
}

// Test server connectivity
public void testServerConnectivity() {
    for (ServerInfo server : discoveryService.getAllOnlineServers()) {
        boolean reachable = testServerReachability(server);
        
        if (!reachable) {
            getLogger().warning("Server " + server.getServerId() + " is not reachable");
            
            // Mark as offline
            serverRegistry.markServerOffline(server.getServerId());
        }
    }
}

private boolean testServerReachability(ServerInfo server) {
    // Ping server or check last heartbeat
    long lastHeartbeat = server.getLastHeartbeat();
    long timeSinceHeartbeat = System.currentTimeMillis() - lastHeartbeat;
    
    return timeSinceHeartbeat < 60000; // 1 minute
}
```

#### 4. Data Synchronization Issues

**Symptoms:**
- Inconsistent player data
- Inventory desync
- Stats not updating

**Solutions:**
```java
// Validate data consistency
public void validateDataConsistency() {
    // Check for data conflicts
    MongoCollection<Document> players = mongoManager.getCollection("global-player-data");
    
    // Find documents with multiple versions
    AggregateIterable<Document> conflicts = players.aggregate(Arrays.asList(
        group("$playerId", AccumulateFields.accumulate("count", Accumulate.sum(1))),
        match(gt("count", 1))
    ));
    
    for (Document conflict : conflicts) {
        getLogger().warning("Data conflict for player: " + conflict.getString("_id"));
        
        // Resolve conflict
        resolveDataConflict(conflict.getString("_id"));
    }
}

// Force data synchronization
public void forceDataSync() {
    for (Player player : Bukkit.getOnlinePlayers()) {
        // Sync player data
        dataSyncService.syncPlayerData(player);
        
        // Sync inventory
        dataSyncService.syncPlayerInventory(player);
        
        // Sync stats
        syncPlayerStats(player);
    }
}

private void resolveDataConflict(String playerId) {
    // Get all conflicting documents
    List<Document> conflicts = new ArrayList<>();
    
    // Choose the most recent version
    Document latest = conflicts.stream()
        .max(Comparator.comparing(doc -> doc.getLong("lastModified")))
        .orElse(null);
    
    if (latest != null) {
        // Update with latest version
        mongoManager.getCollection("global-player-data")
            .replaceOne(eq("_id", playerId), latest);
        
        // Remove old versions
        mongoManager.getCollection("global-player-data")
            .deleteMany(and(
                eq("playerId", playerId),
                ne("_id", latest.getString("_id"))
            ));
    }
}
```

---

*Next: Learn about [[Performance Optimization]] for advanced tuning techniques.*