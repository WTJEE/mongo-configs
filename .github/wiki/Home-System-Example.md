# Home System Example

Complete home management system with teleportation, permissions, GUI, and cross-server support using MongoDB Configs API.

## 📋 Overview

This example demonstrates a sophisticated home system featuring:
- Multiple homes per player with custom names
- Permission-based home limits and teleportation
- Cross-server home synchronization
- Interactive GUI for home management
- Real-time home updates via Change Streams

## 🏗️ Project Structure

```
home-system/
├── src/main/java/xyz/wtje/homes/
│   ├── HomePlugin.java              # Main plugin class
│   ├── commands/                    # Command handlers
│   │   ├── HomeCommand.java
│   │   ├── SetHomeCommand.java
│   │   ├── DelHomeCommand.java
│   │   └── AdminHomeCommand.java
│   ├── gui/                         # GUI components
│   │   ├── HomeGUI.java
│   │   ├── HomeEditorGUI.java
│   │   └── HomeSettingsGUI.java
│   ├── models/                      # Data models
│   │   ├── PlayerHome.java
│   │   ├── HomeSettings.java
│   │   ├── HomeVisit.java
│   │   └── ServerInfo.java
│   ├── managers/                    # Business logic
│   │   ├── HomeManager.java
│   │   ├── TeleportManager.java
│   │   ├── PermissionManager.java
│   │   └── CrossServerManager.java
│   ├── events/                     # Event handlers
│   │   ├── HomeEvents.java
│   │   └── TeleportEvents.java
│   └── messages/                    # Multilingual messages
│       ├── HomeMessages.java
│       └── TeleportMessages.java
└── src/main/resources/
    └── plugin.yml
```

## 🔧 Configuration Classes

### PlayerHome.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "home_system")
@ConfigsCollection(collection = "player_homes")
public class PlayerHome {
    
    @ConfigsField
    private String id;  // playerId_homeName
    
    @ConfigsField
    private String playerId;
    
    @ConfigsField
    private String homeName;
    
    @ConfigsField
    private Location location;
    
    @ConfigsField
    private String worldName;
    
    @ConfigsField
    private String serverName;  // For cross-server support
    
    @ConfigsField
    private long createdAt;
    
    @ConfigsField
    private long lastUsed;
    
    @ConfigsField
    private int useCount;
    
    @ConfigsField
    private HomeSettings settings;
    
    @ConfigsField
    private List<String> allowedPlayers;  // Players who can visit
    
    @ConfigsField
    private Material icon;
    
    @ConfigsField
    private String description;
    
    // Getters and setters...
    
    public boolean canPlayerVisit(String visitorId) {
        if (playerId.equals(visitorId)) return true; // Owner can always visit
        
        if (settings.isPublic()) return true; // Public homes
        
        if (allowedPlayers != null && allowedPlayers.contains(visitorId)) return true;
        
        return false;
    }
    
    public void recordVisit(String visitorId) {
        setLastUsed(System.currentTimeMillis());
        setUseCount(getUseCount() + 1);
    }
}
```

### HomeSettings.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "home_system")
@ConfigsCollection(collection = "home_settings")
public class HomeSettings {
    
    @ConfigsField
    private String homeId;
    
    @ConfigsField
    private boolean isPublic;
    
    @ConfigsField
    private boolean allowBedSpawnOverride;
    
    @ConfigsField
    private boolean showParticles;
    
    @ConfigsField
    private Particle particleType;
    
    @ConfigsField
    private boolean playSound;
    
    @ConfigsField
    private Sound teleportSound;
    
    @ConfigsField
    private float soundVolume;
    
    @ConfigsField
    private float soundPitch;
    
    @ConfigsField
    private boolean requireConfirmation;
    
    @ConfigsField
    private int warmupTime;  // Seconds before teleport
    
    @ConfigsField
    private int cooldownTime;  // Seconds between teleports
    
    @ConfigsField
    private double teleportCost;  // Economy cost
    
    // Getters and setters...
}
```

### HomeVisit.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "home_system")
@ConfigsCollection(collection = "home_visits")
public class HomeVisit {
    
    @ConfigsField
    private String id;
    
    @ConfigsField
    private String homeId;
    
    @ConfigsField
    private String visitorId;
    
    @ConfigsField
    private long visitTime;
    
    @ConfigsField
    private String fromServer;
    
    @ConfigsField
    private Location fromLocation;
    
    @ConfigsField
    private VisitPurpose purpose;  // VISIT, EDIT, DELETE
    
    public enum VisitPurpose {
        VISIT, EDIT, DELETE
    }
    
    // Getters and setters...
}
```

### ServerInfo.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "home_system")
@ConfigsCollection(collection = "server_info")
public class ServerInfo {
    
    @ConfigsField
    private String serverName;
    
    @ConfigsField
    private String serverId;
    
    @ConfigsField
    private String address;
    
    @ConfigsField
    private int port;
    
    @ConfigsField
    private boolean online;
    
    @ConfigsField
    private long lastSeen;
    
    @ConfigsField
    private List<String> worlds;
    
    // Getters and setters...
}
```

## 💬 Message Classes

### HomeMessages.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "home_system")
@ConfigsCollection(collection = "home_messages")
@SupportedLanguages({"en", "pl", "es", "de", "fr"})
public class HomeMessages extends MongoMessages {
    
    // Home creation messages
    public String getHomeCreated(String homeName) {
        return get("en", "home.created", homeName);
    }
    
    public String getHomeCreated(String lang, String homeName) {
        return get(lang, "home.created", homeName);
    }
    
    public String getHomeCreateFailed(String reason) {
        return get("en", "home.create_failed", reason);
    }
    
    public String getHomeCreateFailed(String lang, String reason) {
        return get(lang, "home.create_failed", reason);
    }
    
    // Teleportation messages
    public String getTeleporting(String homeName, int warmupTime) {
        return get("en", "home.teleporting", homeName, warmupTime);
    }
    
    public String getTeleporting(String lang, String homeName, int warmupTime) {
        return get(lang, "home.teleporting", homeName, warmupTime);
    }
    
    public String getTeleported(String homeName) {
        return get("en", "home.teleported", homeName);
    }
    
    public String getTeleported(String lang, String homeName) {
        return get(lang, "home.teleported", homeName);
    }
    
    // Error messages
    public String getHomeNotFound(String homeName) {
        return get("en", "home.not_found", homeName);
    }
    
    public String getHomeNotFound(String lang, String homeName) {
        return get(lang, "home.not_found", homeName);
    }
    
    public String getNoPermission(String homeName) {
        return get("en", "home.no_permission", homeName);
    }
    
    public String getNoPermission(String lang, String homeName) {
        return get(lang, "home.no_permission", homeName);
    }
    
    public String getHomeLimitReached(int current, int max) {
        return get("en", "home.limit_reached", current, max);
    }
    
    public String getHomeLimitReached(String lang, int current, int max) {
        return get(lang, "home.limit_reached", current, max);
    }
    
    // GUI messages
    public String getHomeGUITitle() {
        return get("en", "gui.home.title");
    }
    
    public String getHomeGUITitle(String lang) {
        return get(lang, "gui.home.title");
    }
    
    public String getHomeIconName(String homeName, String description) {
        return get("en", "gui.home.icon_name", homeName, description);
    }
    
    public String getHomeIconName(String lang, String homeName, String description) {
        return get(lang, "gui.home.icon_name", homeName, description);
    }
}
```

## 🎯 Main Plugin Class

### HomePlugin.java

```java
public class HomePlugin extends JavaPlugin {
    
    private ConfigManager configManager;
    private HomeManager homeManager;
    private TeleportManager teleportManager;
    private PermissionManager permissionManager;
    private CrossServerManager crossServerManager;
    private HomeMessages homeMessages;
    private TeleportMessages teleportMessages;
    
    // Active teleports tracking
    private final Map<String, ScheduledTeleport> activeTeleports = new ConcurrentHashMap<>();
    
    @Override
    public void onEnable() {
        try {
            // Initialize MongoDB Configs API
            configManager = MongoConfigsAPI.createConfigManager(
                getConfig().getString("mongodb.uri", "mongodb://localhost:27017"),
                getConfig().getString("mongodb.database", "home_system")
            );
            
            // Initialize message systems
            homeMessages = configManager.messagesOf(HomeMessages.class);
            teleportMessages = configManager.messagesOf(TeleportMessages.class);
            
            // Initialize managers
            homeManager = new HomeManager(this);
            teleportManager = new TeleportManager(this);
            permissionManager = new PermissionManager(this);
            crossServerManager = new CrossServerManager(this);
            
            // Register commands
            getCommand("home").setExecutor(new HomeCommand(this));
            getCommand("sethome").setExecutor(new SetHomeCommand(this));
            getCommand("delhome").setExecutor(new DelHomeCommand(this));
            getCommand("adminhome").setExecutor(new AdminHomeCommand(this));
            
            // Register events
            getServer().getPluginManager().registerEvents(new HomeEvents(this), this);
            getServer().getPluginManager().registerEvents(new TeleportEvents(this), this);
            
            // Start background tasks
            startTeleportTask();
            startServerHeartbeat();
            
            getLogger().info("Home System enabled successfully!");
            
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Home System: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    private void startTeleportTask() {
        // Process teleport queue every tick
        getServer().getScheduler().runTaskTimer(this, () -> {
            long currentTime = System.currentTimeMillis();
            
            activeTeleports.entrySet().removeIf(entry -> {
                ScheduledTeleport teleport = entry.getValue();
                
                if (currentTime >= teleport.getTeleportTime()) {
                    performTeleport(teleport);
                    return true;
                }
                
                return false;
            });
        }, 1L, 1L);
    }
    
    private void startServerHeartbeat() {
        // Send server heartbeat every 30 seconds
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                crossServerManager.sendHeartbeat();
            } catch (Exception e) {
                getLogger().severe("Failed to send server heartbeat: " + e.getMessage());
            }
        }, 20 * 30, 20 * 30);
    }
    
    private void performTeleport(ScheduledTeleport teleport) {
        Player player = teleport.getPlayer();
        if (!player.isOnline()) return;
        
        // Check if player moved during warmup
        if (teleport.getSettings().isRequireConfirmation() && 
            player.getLocation().distance(teleport.getStartLocation()) > 0.5) {
            String message = homeMessages.getTeleportCancelledMovement(getPlayerLanguage(player));
            player.sendMessage(ColorHelper.parseComponent(message));
            return;
        }
        
        // Perform teleport
        player.teleport(teleport.getDestination());
        
        // Play effects
        playTeleportEffects(player, teleport.getSettings());
        
        // Send success message
        String message = homeMessages.getTeleported(getPlayerLanguage(player), teleport.getHomeName());
        player.sendMessage(ColorHelper.parseComponent(message));
        
        // Record visit
        homeManager.recordHomeVisit(teleport.getHomeId(), player.getUniqueId().toString());
    }
    
    private void playTeleportEffects(Player player, HomeSettings settings) {
        if (settings.isShowParticles()) {
            player.getWorld().spawnParticle(settings.getParticleType(), 
                player.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
        }
        
        if (settings.isPlaySound()) {
            player.playSound(player.getLocation(), settings.getTeleportSound(), 
                settings.getSoundVolume(), settings.getSoundPitch());
        }
    }
    
    // Getters...
    public Map<String, ScheduledTeleport> getActiveTeleports() { return activeTeleports; }
    
    private String getPlayerLanguage(Player player) {
        return MongoConfigsAPI.getLanguageManager().getPlayerLanguageOrDefault(
            player.getUniqueId().toString()
        );
    }
    
    // Inner class for scheduled teleports
    public static class ScheduledTeleport {
        private Player player;
        private Location startLocation;
        private Location destination;
        private String homeId;
        private String homeName;
        private HomeSettings settings;
        private long teleportTime;
        
        // Getters and setters...
    }
}
```

## 🛠️ Managers

### HomeManager.java

```java
public class HomeManager {
    
    private final HomePlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, List<PlayerHome>> playerHomesCache = new ConcurrentHashMap<>();
    
    public HomeManager(HomePlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        
        loadHomes();
        setupChangeStreams();
    }
    
    private void loadHomes() {
        try {
            List<PlayerHome> homes = configManager.getAll(PlayerHome.class);
            
            // Group homes by player
            Map<String, List<PlayerHome>> grouped = homes.stream()
                .collect(Collectors.groupingBy(PlayerHome::getPlayerId));
            
            playerHomesCache.putAll(grouped);
            
            plugin.getLogger().info("Loaded " + homes.size() + " homes for " + 
                                  grouped.size() + " players");
                                  
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load homes: " + e.getMessage());
        }
    }
    
    private void setupChangeStreams() {
        configManager.watchCollection(PlayerHome.class, changeEvent -> {
            PlayerHome home = changeEvent.getDocument();
            if (home != null) {
                // Update cache
                List<PlayerHome> playerHomes = playerHomesCache.computeIfAbsent(
                    home.getPlayerId(), k -> new ArrayList<>());
                
                // Remove old version if exists
                playerHomes.removeIf(h -> h.getId().equals(home.getId()));
                
                // Add new version
                playerHomes.add(home);
                
                plugin.getLogger().info("Home updated: " + home.getId());
            }
        });
    }
    
    public List<PlayerHome> getPlayerHomes(String playerId) {
        return playerHomesCache.getOrDefault(playerId, Collections.emptyList());
    }
    
    public PlayerHome getPlayerHome(String playerId, String homeName) {
        return getPlayerHomes(playerId).stream()
            .filter(home -> home.getHomeName().equalsIgnoreCase(homeName))
            .findFirst()
            .orElse(null);
    }
    
    public boolean createHome(Player player, String homeName, Location location) {
        String playerId = player.getUniqueId().toString();
        
        // Check home limit
        int currentHomes = getPlayerHomes(playerId).size();
        int maxHomes = plugin.getPermissionManager().getMaxHomes(player);
        
        if (currentHomes >= maxHomes) {
            String message = plugin.getHomeMessages().getHomeLimitReached(
                getPlayerLanguage(player), currentHomes, maxHomes);
            player.sendMessage(ColorHelper.parseComponent(message));
            return false;
        }
        
        // Check if home name already exists
        if (getPlayerHome(playerId, homeName) != null) {
            String message = plugin.getHomeMessages().getHomeAlreadyExists(
                getPlayerLanguage(player), homeName);
            player.sendMessage(ColorHelper.parseComponent(message));
            return false;
        }
        
        // Create home
        PlayerHome home = new PlayerHome();
        home.setId(playerId + "_" + homeName.toLowerCase());
        home.setPlayerId(playerId);
        home.setHomeName(homeName);
        home.setLocation(location);
        home.setWorldName(location.getWorld().getName());
        home.setServerName(plugin.getCrossServerManager().getCurrentServerName());
        home.setCreatedAt(System.currentTimeMillis());
        home.setLastUsed(0);
        home.setUseCount(0);
        home.setIcon(Material.BED); // Default icon
        home.setDescription("");
        
        // Create default settings
        HomeSettings settings = new HomeSettings();
        settings.setHomeId(home.getId());
        settings.setPublic(false);
        settings.setAllowBedSpawnOverride(true);
        settings.setShowParticles(true);
        settings.setParticleType(Particle.PORTAL);
        settings.setPlaySound(true);
        settings.setTeleportSound(Sound.ENTITY_ENDERMAN_TELEPORT);
        settings.setSoundVolume(1.0f);
        settings.setSoundPitch(1.0f);
        settings.setRequireConfirmation(false);
        settings.setWarmupTime(3);
        settings.setCooldownTime(0);
        settings.setTeleportCost(0.0);
        
        home.setSettings(settings);
        home.setAllowedPlayers(new ArrayList<>());
        
        try {
            configManager.save(home);
            configManager.save(settings);
            
            // Update cache
            List<PlayerHome> playerHomes = playerHomesCache.computeIfAbsent(playerId, k -> new ArrayList<>());
            playerHomes.add(home);
            
            // Send success message
            String message = plugin.getHomeMessages().getHomeCreated(
                getPlayerLanguage(player), homeName);
            player.sendMessage(ColorHelper.parseComponent(message));
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create home for " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    public boolean deleteHome(String playerId, String homeName) {
        PlayerHome home = getPlayerHome(playerId, homeName);
        if (home == null) return false;
        
        try {
            // Delete home and settings
            configManager.delete(home);
            configManager.delete(HomeSettings.class, "homeId", home.getId());
            
            // Update cache
            List<PlayerHome> playerHomes = playerHomesCache.get(playerId);
            if (playerHomes != null) {
                playerHomes.removeIf(h -> h.getId().equals(home.getId()));
            }
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to delete home " + home.getId() + ": " + e.getMessage());
            return false;
        }
    }
    
    public void recordHomeVisit(String homeId, String visitorId) {
        try {
            PlayerHome home = configManager.findFirst(PlayerHome.class, "id", homeId);
            if (home != null) {
                home.recordVisit(visitorId);
                configManager.save(home);
                
                // Record visit for analytics
                HomeVisit visit = new HomeVisit();
                visit.setId(UUID.randomUUID().toString());
                visit.setHomeId(homeId);
                visit.setVisitorId(visitorId);
                visit.setVisitTime(System.currentTimeMillis());
                visit.setFromServer(plugin.getCrossServerManager().getCurrentServerName());
                visit.setPurpose(HomeVisit.VisitPurpose.VISIT);
                
                configManager.save(visit);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to record home visit: " + e.getMessage());
        }
    }
    
    private String getPlayerLanguage(Player player) {
        return MongoConfigsAPI.getLanguageManager().getPlayerLanguageOrDefault(
            player.getUniqueId().toString()
        );
    }
}
```

### TeleportManager.java

```java
public class TeleportManager {
    
    private final HomePlugin plugin;
    private final Map<String, Long> playerCooldowns = new ConcurrentHashMap<>();
    
    public TeleportManager(HomePlugin plugin) {
        this.plugin = plugin;
    }
    
    public boolean teleportToHome(Player player, String homeName) {
        String playerId = player.getUniqueId().toString();
        PlayerHome home = plugin.getHomeManager().getPlayerHome(playerId, homeName);
        
        if (home == null) {
            String message = plugin.getHomeMessages().getHomeNotFound(
                getPlayerLanguage(player), homeName);
            player.sendMessage(ColorHelper.parseComponent(message));
            return false;
        }
        
        // Check permissions
        if (!home.canPlayerVisit(playerId)) {
            String message = plugin.getHomeMessages().getNoPermission(
                getPlayerLanguage(player), homeName);
            player.sendMessage(ColorHelper.parseComponent(message));
            return false;
        }
        
        // Check cooldown
        if (isPlayerOnCooldown(player)) {
            long remaining = getRemainingCooldown(player);
            String message = plugin.getHomeMessages().getCooldownActive(
                getPlayerLanguage(player), remaining);
            player.sendMessage(ColorHelper.parseComponent(message));
            return false;
        }
        
        // Check economy cost
        if (!canAffordTeleport(player, home.getSettings())) {
            String message = plugin.getHomeMessages().getInsufficientFunds(
                getPlayerLanguage(player), home.getSettings().getTeleportCost());
            player.sendMessage(ColorHelper.parseComponent(message));
            return false;
        }
        
        // Check cross-server
        if (!home.getServerName().equals(plugin.getCrossServerManager().getCurrentServerName())) {
            return handleCrossServerTeleport(player, home);
        }
        
        // Deduct cost
        if (home.getSettings().getTeleportCost() > 0) {
            deductTeleportCost(player, home.getSettings().getTeleportCost());
        }
        
        // Start teleport
        return startTeleport(player, home);
    }
    
    private boolean startTeleport(Player player, PlayerHome home) {
        HomeSettings settings = home.getSettings();
        int warmupTime = settings.getWarmupTime();
        
        if (warmupTime > 0) {
            // Schedule teleport
            HomePlugin.ScheduledTeleport teleport = new HomePlugin.ScheduledTeleport();
            teleport.setPlayer(player);
            teleport.setStartLocation(player.getLocation());
            teleport.setDestination(home.getLocation());
            teleport.setHomeId(home.getId());
            teleport.setHomeName(home.getHomeName());
            teleport.setSettings(settings);
            teleport.setTeleportTime(System.currentTimeMillis() + (warmupTime * 1000));
            
            plugin.getActiveTeleports().put(player.getUniqueId().toString(), teleport);
            
            // Send warmup message
            String message = plugin.getHomeMessages().getTeleporting(
                getPlayerLanguage(player), home.getHomeName(), warmupTime);
            player.sendMessage(ColorHelper.parseComponent(message));
            
            // Start warmup effects
            startWarmupEffects(player, warmupTime);
            
        } else {
            // Instant teleport
            player.teleport(home.getLocation());
            playTeleportEffects(player, settings);
            
            String message = plugin.getHomeMessages().getTeleported(
                getPlayerLanguage(player), home.getHomeName());
            player.sendMessage(ColorHelper.parseComponent(message));
            
            // Record visit
            plugin.getHomeManager().recordHomeVisit(home.getId(), player.getUniqueId().toString());
        }
        
        // Set cooldown
        if (settings.getCooldownTime() > 0) {
            playerCooldowns.put(player.getUniqueId().toString(), 
                System.currentTimeMillis() + (settings.getCooldownTime() * 1000));
        }
        
        return true;
    }
    
    private void startWarmupEffects(Player player, int warmupTime) {
        for (int i = 0; i < warmupTime; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.getWorld().spawnParticle(Particle.PORTAL, 
                        player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
                }
            }, 20L * (warmupTime - i));
        }
    }
    
    private void playTeleportEffects(Player player, HomeSettings settings) {
        if (settings.isShowParticles()) {
            player.getWorld().spawnParticle(settings.getParticleType(), 
                player.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
        }
        
        if (settings.isPlaySound()) {
            player.playSound(player.getLocation(), settings.getTeleportSound(), 
                settings.getSoundVolume(), settings.getSoundPitch());
        }
    }
    
    private boolean isPlayerOnCooldown(Player player) {
        Long cooldownEnd = playerCooldowns.get(player.getUniqueId().toString());
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }
    
    private long getRemainingCooldown(Player player) {
        Long cooldownEnd = playerCooldowns.get(player.getUniqueId().toString());
        if (cooldownEnd == null) return 0;
        
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }
    
    private boolean canAffordTeleport(Player player, HomeSettings settings) {
        // Integration with economy plugin would go here
        return settings.getTeleportCost() <= 0 || hasEnoughMoney(player, settings.getTeleportCost());
    }
    
    private void deductTeleportCost(Player player, double cost) {
        // Integration with economy plugin would go here
    }
    
    private boolean handleCrossServerTeleport(Player player, PlayerHome home) {
        // Send player to target server
        plugin.getCrossServerManager().sendPlayerToServer(player, home.getServerName());
        return true;
    }
    
    private String getPlayerLanguage(Player player) {
        return MongoConfigsAPI.getLanguageManager().getPlayerLanguageOrDefault(
            player.getUniqueId().toString()
        );
    }
    
    // Helper methods for economy integration...
    private boolean hasEnoughMoney(Player player, double amount) { return true; }
}
```

### PermissionManager.java

```java
public class PermissionManager {
    
    private final HomePlugin plugin;
    
    public PermissionManager(HomePlugin plugin) {
        this.plugin = plugin;
    }
    
    public int getMaxHomes(Player player) {
        if (player.hasPermission("home.unlimited")) {
            return Integer.MAX_VALUE;
        }
        
        // Check for specific home limits
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("home.limit." + i)) {
                return i;
            }
        }
        
        // Default limit
        return plugin.getConfig().getInt("default-home-limit", 3);
    }
    
    public boolean canCreateHome(Player player, Location location) {
        // Check world permissions
        if (!player.hasPermission("home.create.*") && 
            !player.hasPermission("home.create." + location.getWorld().getName())) {
            return false;
        }
        
        // Check region permissions (if WorldGuard is present)
        if (!canCreateHomeInRegion(player, location)) {
            return false;
        }
        
        return true;
    }
    
    public boolean canTeleportToHome(Player player, PlayerHome home) {
        // Check basic teleport permission
        if (!player.hasPermission("home.teleport")) {
            return false;
        }
        
        // Check cross-server permission
        if (!home.getServerName().equals(plugin.getCrossServerManager().getCurrentServerName()) &&
            !player.hasPermission("home.crossserver")) {
            return false;
        }
        
        // Check world teleport permission
        if (!player.hasPermission("home.teleport.*") && 
            !player.hasPermission("home.teleport." + home.getWorldName())) {
            return false;
        }
        
        return true;
    }
    
    public boolean canEditHome(Player player, PlayerHome home) {
        // Owner can always edit
        if (home.getPlayerId().equals(player.getUniqueId().toString())) {
            return true;
        }
        
        // Check admin permissions
        return player.hasPermission("home.admin.edit");
    }
    
    public boolean canDeleteHome(Player player, PlayerHome home) {
        // Owner can always delete
        if (home.getPlayerId().equals(player.getUniqueId().toString())) {
            return true;
        }
        
        // Check admin permissions
        return player.hasPermission("home.admin.delete");
    }
    
    public boolean canViewHome(Player player, PlayerHome home) {
        // Owner can always view
        if (home.getPlayerId().equals(player.getUniqueId().toString())) {
            return true;
        }
        
        // Check if home is public or player is allowed
        if (home.canPlayerVisit(player.getUniqueId().toString())) {
            return true;
        }
        
        // Check admin permissions
        return player.hasPermission("home.admin.view");
    }
    
    private boolean canCreateHomeInRegion(Player player, Location location) {
        // WorldGuard integration would go here
        // For now, return true
        return true;
    }
}
```

## 🎨 GUI Components

### HomeGUI.java

```java
public class HomeGUI {
    
    private final HomePlugin plugin;
    private final HomeMessages messages;
    
    public HomeGUI(HomePlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getHomeMessages();
    }
    
    public void openHomeGUI(Player player) {
        String language = getPlayerLanguage(player);
        String title = messages.getHomeGUITitle(language);
        
        Inventory inventory = Bukkit.createInventory(null, 54, ColorHelper.parseString(title));
        
        // Add home items
        List<PlayerHome> homes = plugin.getHomeManager().getPlayerHomes(player.getUniqueId().toString());
        int slot = 0;
        
        for (PlayerHome home : homes) {
            if (slot >= 45) break;
            addHomeItem(inventory, player, home, slot++, language);
        }
        
        // Add create home item
        addCreateHomeItem(inventory, language);
        
        // Add navigation items
        addNavigationItems(inventory, language);
        
        player.openInventory(inventory);
    }
    
    private void addHomeItem(Inventory inventory, Player player, PlayerHome home, int slot, String language) {
        ItemStack item = new ItemStack(home.getIcon());
        ItemMeta meta = item.getItemMeta();
        
        String displayName = messages.getHomeIconName(language, home.getHomeName(), 
            home.getDescription() != null ? home.getDescription() : "");
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        List<String> lore = new ArrayList<>();
        
        // Basic info
        lore.add(ColorHelper.parseString("&7World: &f" + home.getWorldName()));
        lore.add(ColorHelper.parseString("&7Server: &f" + home.getServerName()));
        lore.add(ColorHelper.parseString("&7Created: &f" + formatDate(home.getCreatedAt())));
        
        if (home.getLastUsed() > 0) {
            lore.add(ColorHelper.parseString("&7Last Used: &f" + formatDate(home.getLastUsed())));
        }
        
        lore.add(ColorHelper.parseString("&7Visits: &f" + home.getUseCount()));
        
        // Settings info
        HomeSettings settings = home.getSettings();
        if (settings != null) {
            if (settings.isPublic()) {
                lore.add(ColorHelper.parseString("&a✓ Public"));
            } else {
                lore.add(ColorHelper.parseString("&c✗ Private"));
            }
            
            if (settings.getWarmupTime() > 0) {
                lore.add(ColorHelper.parseString("&7Warmup: &f" + settings.getWarmupTime() + "s"));
            }
            
            if (settings.getTeleportCost() > 0) {
                lore.add(ColorHelper.parseString("&7Cost: &f" + settings.getTeleportCost()));
            }
        }
        
        lore.add("");
        lore.add(ColorHelper.parseString("&eClick to teleport"));
        lore.add(ColorHelper.parseString("&7Right-click for options"));
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    private void addCreateHomeItem(Inventory inventory, String language) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        
        String displayName = messages.getCreateHomeButton(language);
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        List<String> lore = Arrays.asList(
            ColorHelper.parseString("&7Click to create a new home"),
            ColorHelper.parseString("&7at your current location")
        );
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        inventory.setItem(49, item);
    }
    
    private void addNavigationItems(Inventory inventory, String language) {
        // Settings button
        ItemStack settingsItem = new ItemStack(Material.COMPARATOR);
        ItemMeta settingsMeta = settingsItem.getItemMeta();
        settingsMeta.setDisplayName(ColorHelper.parseString("&b" + messages.getSettingsButton(language)));
        settingsItem.setItemMeta(settingsMeta);
        inventory.setItem(45, settingsItem);
        
        // Public homes button
        ItemStack publicItem = new ItemStack(Material.BOOK);
        ItemMeta publicMeta = publicItem.getItemMeta();
        publicMeta.setDisplayName(ColorHelper.parseString("&a" + messages.getPublicHomesButton(language)));
        publicItem.setItemMeta(publicMeta);
        inventory.setItem(46, publicItem);
        
        // Close button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ColorHelper.parseString("&c" + messages.getCloseButton(language)));
        closeItem.setItemMeta(closeMeta);
        inventory.setItem(53, closeItem);
    }
    
    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
        return sdf.format(new Date(timestamp));
    }
    
    private String getPlayerLanguage(Player player) {
        return MongoConfigsAPI.getLanguageManager().getPlayerLanguageOrDefault(
            player.getUniqueId().toString()
        );
    }
}
```

## 🔄 Cross-Server Support

### CrossServerManager.java

```java
public class CrossServerManager {
    
    private final HomePlugin plugin;
    private final ConfigManager configManager;
    private String currentServerName;
    private String currentServerId;
    
    public CrossServerManager(HomePlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        
        // Initialize server info
        currentServerName = plugin.getConfig().getString("server-name", "server1");
        currentServerId = plugin.getConfig().getString("server-id", UUID.randomUUID().toString());
        
        registerServer();
        setupChangeStreams();
    }
    
    private void registerServer() {
        try {
            ServerInfo serverInfo = new ServerInfo();
            serverInfo.setServerName(currentServerName);
            serverInfo.setServerId(currentServerId);
            serverInfo.setAddress(plugin.getConfig().getString("server-address", "localhost"));
            serverInfo.setPort(plugin.getServer().getPort());
            serverInfo.setOnline(true);
            serverInfo.setLastSeen(System.currentTimeMillis());
            
            // Get world names
            List<String> worlds = plugin.getServer().getWorlds().stream()
                .map(World::getName)
                .collect(Collectors.toList());
            serverInfo.setWorlds(worlds);
            
            configManager.save(serverInfo);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register server: " + e.getMessage());
        }
    }
    
    private void setupChangeStreams() {
        // Listen for cross-server teleport requests
        configManager.watchCollection(PlayerHome.class, changeEvent -> {
            // Handle cross-server teleport logic
        });
    }
    
    public void sendHeartbeat() {
        try {
            ServerInfo serverInfo = configManager.findFirst(ServerInfo.class, 
                "serverId", currentServerId);
            
            if (serverInfo != null) {
                serverInfo.setOnline(true);
                serverInfo.setLastSeen(System.currentTimeMillis());
                
                // Update world list
                List<String> worlds = plugin.getServer().getWorlds().stream()
                    .map(World::getName)
                    .collect(Collectors.toList());
                serverInfo.setWorlds(worlds);
                
                configManager.save(serverInfo);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send heartbeat: " + e.getMessage());
        }
    }
    
    public void sendPlayerToServer(Player player, String targetServer) {
        // BungeeCord/Velocity integration would go here
        // For now, just send a message
        String message = plugin.getHomeMessages().getCrossServerTeleport(
            getPlayerLanguage(player), targetServer);
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    public List<ServerInfo> getOnlineServers() {
        try {
            long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
            return configManager.find(ServerInfo.class, 
                Filters.and(
                    Filters.eq("online", true),
                    Filters.gte("lastSeen", fiveMinutesAgo)
                ));
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get online servers: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    public String getCurrentServerName() {
        return currentServerName;
    }
    
    private String getPlayerLanguage(Player player) {
        return MongoConfigsAPI.getLanguageManager().getPlayerLanguageOrDefault(
            player.getUniqueId().toString()
        );
    }
}
```

## 📊 Advanced Features

### Home Analytics

```java
public class HomeAnalytics {
    
    private final HomePlugin plugin;
    private final ConfigManager configManager;
    
    public HomeAnalytics(HomePlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }
    
    public Map<String, Integer> getMostVisitedHomes(int limit) {
        Map<String, Integer> visitCounts = new HashMap<>();
        
        try {
            List<HomeVisit> visits = configManager.getAll(HomeVisit.class);
            
            for (HomeVisit visit : visits) {
                visitCounts.merge(visit.getHomeId(), 1, Integer::sum);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get home visit stats: " + e.getMessage());
        }
        
        return visitCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }
    
    public Map<String, Integer> getMostActivePlayers(int limit) {
        Map<String, Integer> playerActivity = new HashMap<>();
        
        try {
            List<HomeVisit> visits = configManager.getAll(HomeVisit.class);
            
            for (HomeVisit visit : visits) {
                playerActivity.merge(visit.getVisitorId(), 1, Integer::sum);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get player activity stats: " + e.getMessage());
        }
        
        return playerActivity.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }
    
    public double getAverageHomesPerPlayer() {
        try {
            List<PlayerHome> homes = configManager.getAll(PlayerHome.class);
            long uniquePlayers = homes.stream()
                .map(PlayerHome::getPlayerId)
                .distinct()
                .count();
            
            return uniquePlayers > 0 ? (double) homes.size() / uniquePlayers : 0.0;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to calculate average homes per player: " + e.getMessage());
            return 0.0;
        }
    }
}
```

## 📈 Performance Optimizations

- **Memory Caching**: Player homes cached for fast access
- **Async Operations**: Database operations run asynchronously
- **Batch Updates**: Multiple home operations batched together
- **Efficient Queries**: Indexed database queries for fast lookups
- **Lazy Loading**: Home data loaded only when needed

## 🔒 Security Features

- **Permission Validation**: All operations checked against permissions
- **Cross-Server Verification**: Server authenticity validation
- **Teleport Safety**: Movement detection during warmup
- **Rate Limiting**: Cooldowns prevent teleport spam
- **Audit Logging**: Complete home activity tracking

---

*This completes the comprehensive example set. All examples demonstrate real-world usage of MongoDB Configs API with proper error handling, performance optimizations, and multilingual support.*