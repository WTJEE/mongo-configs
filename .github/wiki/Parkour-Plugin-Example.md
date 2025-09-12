# Parkour Plugin Example

Complete parkour system with leaderboards, timing, checkpoints, and statistics using MongoDB Configs API with real-time updates.

## 📋 Overview

This example demonstrates a feature-rich parkour plugin including:
- Course timing and leaderboards
- Checkpoint system with progress saving
- Player statistics and achievements
- Real-time leaderboard updates
- Anti-cheat measures and validation

## 🏗️ Project Structure

```
parkour-plugin/
├── src/main/java/xyz/wtje/parkour/
│   ├── ParkourPlugin.java          # Main plugin class
│   ├── commands/                   # Command handlers
│   │   ├── ParkourCommand.java
│   │   ├── LeaderboardCommand.java
│   │   └── AdminParkourCommand.java
│   ├── gui/                        # GUI components
│   │   ├── CourseSelectionGUI.java
│   │   ├── LeaderboardGUI.java
│   │   ├── StatisticsGUI.java
│   │   └── CheckpointGUI.java
│   ├── models/                     # Data models
│   │   ├── ParkourCourse.java
│   │   ├── PlayerRun.java
│   │   ├── Checkpoint.java
│   │   ├── LeaderboardEntry.java
│   │   └── PlayerStats.java
│   ├── managers/                   # Business logic
│   │   ├── CourseManager.java
│   │   ├── TimingManager.java
│   │   ├── LeaderboardManager.java
│   │   └── StatisticsManager.java
│   ├── events/                     # Event handlers
│   │   ├── CourseEvents.java
│   │   └── PlayerEvents.java
│   └── messages/                   # Multilingual messages
│       ├── ParkourMessages.java
│       └── AchievementMessages.java
└── src/main/resources/
    └── plugin.yml
```

## 🔧 Configuration Classes

### ParkourCourse.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "parkour_system")
@ConfigsCollection(collection = "courses")
public class ParkourCourse {
    
    @ConfigsField
    private String id;
    
    @ConfigsField
    private String nameKey;  // Message key for course name
    
    @ConfigsField
    private String descriptionKey;
    
    @ConfigsField
    private Location startLocation;
    
    @ConfigsField
    private Location endLocation;
    
    @ConfigsField
    private List<Checkpoint> checkpoints;
    
    @ConfigsField
    private long bestTime;  // Best completion time in milliseconds
    
    @ConfigsField
    private String bestPlayerId;
    
    @ConfigsField
    private Difficulty difficulty;
    
    @ConfigsField
    private boolean active;
    
    @ConfigsField
    private Material icon;
    
    @ConfigsField
    private List<Reward> rewards;
    
    public enum Difficulty {
        EASY, MEDIUM, HARD, EXTREME
    }
    
    public static class Reward {
        private String type;  // "money", "item", "permission"
        private String value;
        private int amount;
        
        // Getters and setters...
    }
    
    // Getters and setters...
}
```

### PlayerRun.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "parkour_system")
@ConfigsCollection(collection = "player_runs")
public class PlayerRun {
    
    @ConfigsField
    private String id;  // playerId_courseId_timestamp
    
    @ConfigsField
    private String playerId;
    
    @ConfigsField
    private String courseId;
    
    @ConfigsField
    private long startTime;
    
    @ConfigsField
    private long endTime;
    
    @ConfigsField
    private long totalTime;  // endTime - startTime
    
    @ConfigsField
    private RunStatus status;
    
    @ConfigsField
    private List<CheckpointTime> checkpointTimes;
    
    @ConfigsField
    private int deaths;
    
    @ConfigsField
    private boolean cheated;  // Anti-cheat flag
    
    @ConfigsField
    private String cheatedReason;
    
    public enum RunStatus {
        IN_PROGRESS, COMPLETED, FAILED, CHEATED
    }
    
    public static class CheckpointTime {
        private int checkpointIndex;
        private long timestamp;
        private Location location;
        
        // Getters and setters...
    }
    
    // Getters and setters...
    
    public void addCheckpointTime(int index, Location location) {
        CheckpointTime cpTime = new CheckpointTime();
        cpTime.setCheckpointIndex(index);
        cpTime.setTimestamp(System.currentTimeMillis());
        cpTime.setLocation(location);
        
        if (checkpointTimes == null) {
            checkpointTimes = new ArrayList<>();
        }
        checkpointTimes.add(cpTime);
    }
    
    public long getLastCheckpointTime() {
        if (checkpointTimes == null || checkpointTimes.isEmpty()) {
            return startTime;
        }
        return checkpointTimes.get(checkpointTimes.size() - 1).getTimestamp();
    }
}
```

### Checkpoint.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "parkour_system")
@ConfigsCollection(collection = "checkpoints")
public class Checkpoint {
    
    @ConfigsField
    private String id;
    
    @ConfigsField
    private String courseId;
    
    @ConfigsField
    private int index;  // Order in course
    
    @ConfigsField
    private Location location;
    
    @ConfigsField
    private double radius;  // Detection radius
    
    @ConfigsField
    private String nameKey;
    
    @ConfigsField
    private Material particleMaterial;
    
    @ConfigsField
    private boolean required;  // Must visit this checkpoint
    
    // Getters and setters...
    
    public boolean isPlayerAtCheckpoint(Player player) {
        return player.getLocation().distance(location) <= radius;
    }
}
```

### LeaderboardEntry.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "parkour_system")
@ConfigsCollection(collection = "leaderboard")
public class LeaderboardEntry {
    
    @ConfigsField
    private String id;  // courseId_playerId
    
    @ConfigsField
    private String courseId;
    
    @ConfigsField
    private String playerId;
    
    @ConfigsField
    private String playerName;
    
    @ConfigsField
    private long bestTime;
    
    @ConfigsField
    private long achievedAt;
    
    @ConfigsField
    private int attempts;
    
    @ConfigsField
    private int completions;
    
    @ConfigsField
    private int position;  // Current leaderboard position
    
    // Getters and setters...
}
```

### PlayerStats.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "parkour_system")
@ConfigsCollection(collection = "player_stats")
public class PlayerStats {
    
    @ConfigsField
    private String playerId;
    
    @ConfigsField
    private String playerName;
    
    @ConfigsField
    private long totalPlayTime;
    
    @ConfigsField
    private int totalRuns;
    
    @ConfigsField
    private int totalCompletions;
    
    @ConfigsField
    private int totalDeaths;
    
    @ConfigsField
    private Map<String, CourseStats> courseStats;
    
    @ConfigsField
    private List<Achievement> achievements;
    
    @ConfigsField
    private long firstJoinTime;
    
    @ConfigsField
    private long lastPlayTime;
    
    public static class CourseStats {
        private String courseId;
        private long bestTime;
        private int attempts;
        private int completions;
        private long totalTime;
        
        // Getters and setters...
    }
    
    public static class Achievement {
        private String id;
        private long unlockedAt;
        private String nameKey;
        
        // Getters and setters...
    }
    
    // Getters and setters...
    
    public void updateCourseStats(String courseId, long time, boolean completed) {
        CourseStats stats = courseStats.computeIfAbsent(courseId, k -> {
            CourseStats cs = new CourseStats();
            cs.setCourseId(courseId);
            cs.setAttempts(0);
            cs.setCompletions(0);
            cs.setTotalTime(0);
            return cs;
        });
        
        stats.setAttempts(stats.getAttempts() + 1);
        stats.setTotalTime(stats.getTotalTime() + time);
        
        if (completed) {
            stats.setCompletions(stats.getCompletions() + 1);
            if (stats.getBestTime() == 0 || time < stats.getBestTime()) {
                stats.setBestTime(time);
            }
        }
    }
}
```

## 💬 Message Classes

### ParkourMessages.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "parkour_system")
@ConfigsCollection(collection = "parkour_messages")
@SupportedLanguages({"en", "pl", "es", "de", "fr"})
public class ParkourMessages extends MongoMessages {
    
    // Course messages
    public String getCourseStarted(String courseName) {
        return get("en", "parkour.course.started", courseName);
    }
    
    public String getCourseStarted(String lang, String courseName) {
        return get(lang, "parkour.course.started", courseName);
    }
    
    public String getCourseCompleted(String courseName, String timeFormatted) {
        return get("en", "parkour.course.completed", courseName, timeFormatted);
    }
    
    public String getCourseCompleted(String lang, String courseName, String timeFormatted) {
        return get(lang, "parkour.course.completed", courseName, timeFormatted);
    }
    
    // Timing messages
    public String getPersonalBest(String timeFormatted) {
        return get("en", "parkour.timing.personal_best", timeFormatted);
    }
    
    public String getPersonalBest(String lang, String timeFormatted) {
        return get(lang, "parkour.timing.personal_best", timeFormatted);
    }
    
    public String getWorldRecord(String playerName, String timeFormatted) {
        return get("en", "parkour.timing.world_record", playerName, timeFormatted);
    }
    
    public String getWorldRecord(String lang, String playerName, String timeFormatted) {
        return get(lang, "parkour.timing.world_record", playerName, timeFormatted);
    }
    
    // Checkpoint messages
    public String getCheckpointReached(int current, int total) {
        return get("en", "parkour.checkpoint.reached", current, total);
    }
    
    public String getCheckpointReached(String lang, int current, int total) {
        return get(lang, "parkour.checkpoint.reached", current, total);
    }
    
    // Leaderboard messages
    public String getLeaderboardHeader(String courseName) {
        return get("en", "parkour.leaderboard.header", courseName);
    }
    
    public String getLeaderboardHeader(String lang, String courseName) {
        return get(lang, "parkour.leaderboard.header", courseName);
    }
    
    public String getLeaderboardEntry(int position, String playerName, String timeFormatted) {
        return get("en", "parkour.leaderboard.entry", position, playerName, timeFormatted);
    }
    
    public String getLeaderboardEntry(String lang, int position, String playerName, String timeFormatted) {
        return get(lang, "parkour.leaderboard.entry", position, playerName, timeFormatted);
    }
}
```

## 🎯 Main Plugin Class

### ParkourPlugin.java

```java
public class ParkourPlugin extends JavaPlugin {
    
    private ConfigManager configManager;
    private CourseManager courseManager;
    private TimingManager timingManager;
    private LeaderboardManager leaderboardManager;
    private StatisticsManager statisticsManager;
    private ParkourMessages parkourMessages;
    private AchievementMessages achievementMessages;
    
    // Active runs tracking
    private final Map<String, PlayerRun> activeRuns = new ConcurrentHashMap<>();
    
    @Override
    public void onEnable() {
        try {
            // Initialize MongoDB Configs API
            configManager = MongoConfigsAPI.createConfigManager(
                getConfig().getString("mongodb.uri", "mongodb://localhost:27017"),
                getConfig().getString("mongodb.database", "parkour_system")
            );
            
            // Initialize message systems
            parkourMessages = configManager.messagesOf(ParkourMessages.class);
            achievementMessages = configManager.messagesOf(AchievementMessages.class);
            
            // Initialize managers
            courseManager = new CourseManager(this);
            timingManager = new TimingManager(this);
            leaderboardManager = new LeaderboardManager(this);
            statisticsManager = new StatisticsManager(this);
            
            // Register commands
            getCommand("parkour").setExecutor(new ParkourCommand(this));
            getCommand("leaderboard").setExecutor(new LeaderboardCommand(this));
            getCommand("adminparkour").setExecutor(new AdminParkourCommand(this));
            
            // Register events
            getServer().getPluginManager().registerEvents(new CourseEvents(this), this);
            getServer().getPluginManager().registerEvents(new PlayerEvents(this), this);
            
            // Start background tasks
            startParticleTask();
            startLeaderboardUpdateTask();
            
            getLogger().info("Parkour Plugin enabled successfully!");
            
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Parkour Plugin: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    private void startParticleTask() {
        // Show checkpoint particles every 5 ticks
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                PlayerRun run = activeRuns.get(player.getUniqueId().toString());
                if (run != null) {
                    showCheckpointParticles(player, run);
                }
            }
        }, 5L, 5L);
    }
    
    private void startLeaderboardUpdateTask() {
        // Update leaderboards every 5 minutes
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                leaderboardManager.updateAllLeaderboards();
            } catch (Exception e) {
                getLogger().severe("Failed to update leaderboards: " + e.getMessage());
            }
        }, 20 * 60 * 5, 20 * 60 * 5);
    }
    
    private void showCheckpointParticles(Player player, PlayerRun run) {
        ParkourCourse course = courseManager.getCourse(run.getCourseId());
        if (course == null) return;
        
        for (Checkpoint checkpoint : course.getCheckpoints()) {
            if (!checkpoint.isRequired()) continue;
            
            Location loc = checkpoint.getLocation();
            player.spawnParticle(Particle.REDSTONE, loc, 10, 0.5, 0.5, 0.5, 
                new Particle.DustOptions(Color.GREEN, 1.0f));
        }
    }
    
    // Getters...
    public Map<String, PlayerRun> getActiveRuns() { return activeRuns; }
}
```

## 🛠️ Managers

### CourseManager.java

```java
public class CourseManager {
    
    private final ParkourPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, ParkourCourse> courseCache = new ConcurrentHashMap<>();
    
    public CourseManager(ParkourPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        
        loadCourses();
        setupChangeStreams();
    }
    
    private void loadCourses() {
        try {
            List<ParkourCourse> courses = configManager.getAll(ParkourCourse.class);
            courses.forEach(course -> courseCache.put(course.getId(), course));
            plugin.getLogger().info("Loaded " + courses.size() + " parkour courses");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load courses: " + e.getMessage());
        }
    }
    
    private void setupChangeStreams() {
        configManager.watchCollection(ParkourCourse.class, changeEvent -> {
            ParkourCourse course = changeEvent.getDocument();
            if (course != null) {
                courseCache.put(course.getId(), course);
                plugin.getLogger().info("Course updated: " + course.getId());
            }
        });
    }
    
    public ParkourCourse getCourse(String courseId) {
        return courseCache.get(courseId);
    }
    
    public List<ParkourCourse> getActiveCourses() {
        return courseCache.values().stream()
            .filter(ParkourCourse::isActive)
            .collect(Collectors.toList());
    }
    
    public boolean isPlayerInCourse(Player player) {
        return plugin.getActiveRuns().containsKey(player.getUniqueId().toString());
    }
    
    public ParkourCourse getPlayerCourse(Player player) {
        PlayerRun run = plugin.getActiveRuns().get(player.getUniqueId().toString());
        return run != null ? getCourse(run.getCourseId()) : null;
    }
    
    public boolean startCourse(Player player, String courseId) {
        if (isPlayerInCourse(player)) {
            return false; // Already in a course
        }
        
        ParkourCourse course = getCourse(courseId);
        if (course == null || !course.isActive()) {
            return false;
        }
        
        // Teleport to start
        player.teleport(course.getStartLocation());
        
        // Create run
        PlayerRun run = new PlayerRun();
        run.setId(player.getUniqueId().toString() + "_" + courseId + "_" + System.currentTimeMillis());
        run.setPlayerId(player.getUniqueId().toString());
        run.setCourseId(courseId);
        run.setStartTime(System.currentTimeMillis());
        run.setStatus(PlayerRun.RunStatus.IN_PROGRESS);
        run.setCheckpointTimes(new ArrayList<>());
        run.setDeaths(0);
        run.setCheated(false);
        
        plugin.getActiveRuns().put(player.getUniqueId().toString(), run);
        
        // Send start message
        String message = plugin.getParkourMessages().getCourseStarted(
            getPlayerLanguage(player), course.getNameKey()
        );
        player.sendMessage(ColorHelper.parseComponent(message));
        
        return true;
    }
    
    public boolean endCourse(Player player, boolean completed) {
        PlayerRun run = plugin.getActiveRuns().remove(player.getUniqueId().toString());
        if (run == null) return false;
        
        run.setEndTime(System.currentTimeMillis());
        run.setTotalTime(run.getEndTime() - run.getStartTime());
        run.setStatus(completed ? PlayerRun.RunStatus.COMPLETED : PlayerRun.RunStatus.FAILED);
        
        try {
            // Save run
            configManager.save(run);
            
            // Update statistics
            plugin.getStatisticsManager().updatePlayerStats(player, run);
            
            if (completed) {
                // Update leaderboard
                plugin.getLeaderboardManager().updateLeaderboard(run);
                
                // Check achievements
                plugin.getStatisticsManager().checkAchievements(player);
                
                // Send completion message
                ParkourCourse course = getCourse(run.getCourseId());
                String timeFormatted = formatTime(run.getTotalTime());
                String message = plugin.getParkourMessages().getCourseCompleted(
                    getPlayerLanguage(player), course.getNameKey(), timeFormatted
                );
                player.sendMessage(ColorHelper.parseComponent(message));
                
                // Give rewards
                giveRewards(player, course);
            }
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to end course for " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    private void giveRewards(Player player, ParkourCourse course) {
        for (ParkourCourse.Reward reward : course.getRewards()) {
            switch (reward.getType()) {
                case "money":
                    // Integration with economy plugin
                    giveMoney(player, Double.parseDouble(reward.getValue()));
                    break;
                case "item":
                    giveItem(player, Material.valueOf(reward.getValue()), reward.getAmount());
                    break;
                case "permission":
                    givePermission(player, reward.getValue());
                    break;
            }
        }
    }
    
    private String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long centiseconds = (milliseconds % 1000) / 10;
        
        return String.format("%d:%02d.%02d", minutes, seconds, centiseconds);
    }
    
    private String getPlayerLanguage(Player player) {
        return MongoConfigsAPI.getLanguageManager().getPlayerLanguageOrDefault(
            player.getUniqueId().toString()
        );
    }
    
    // Helper methods for rewards...
    private void giveMoney(Player player, double amount) { /* Implementation */ }
    private void giveItem(Player player, Material material, int amount) { /* Implementation */ }
    private void givePermission(Player player, String permission) { /* Implementation */ }
}
```

### TimingManager.java

```java
public class TimingManager {
    
    private final ParkourPlugin plugin;
    
    public TimingManager(ParkourPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void handleCheckpoint(Player player, Checkpoint checkpoint) {
        PlayerRun run = plugin.getActiveRuns().get(player.getUniqueId().toString());
        if (run == null) return;
        
        // Add checkpoint time
        run.addCheckpointTime(checkpoint.getIndex(), player.getLocation());
        
        // Send checkpoint message
        String language = getPlayerLanguage(player);
        ParkourCourse course = plugin.getCourseManager().getCourse(run.getCourseId());
        
        if (course != null) {
            int current = run.getCheckpointTimes().size();
            int total = course.getCheckpoints().size();
            
            String message = plugin.getParkourMessages().getCheckpointReached(language, current, total);
            player.sendMessage(ColorHelper.parseComponent(message));
            
            // Show progress bar
            showProgressBar(player, current, total);
        }
    }
    
    public void handleDeath(Player player) {
        PlayerRun run = plugin.getActiveRuns().get(player.getUniqueId().toString());
        if (run == null) return;
        
        run.setDeaths(run.getDeaths() + 1);
        
        // Respawn at last checkpoint or start
        Location respawnLocation = getRespawnLocation(run);
        player.teleport(respawnLocation);
        
        // Send death message
        String language = getPlayerLanguage(player);
        String message = plugin.getParkourMessages().getDeathMessage(language, run.getDeaths());
        player.sendMessage(ColorHelper.parseComponent(message));
    }
    
    public void checkForCheating(Player player) {
        PlayerRun run = plugin.getActiveRuns().get(player.getUniqueId().toString());
        if (run == null) return;
        
        // Anti-cheat checks
        if (isFlying(player) && !hasFlyPermission(player)) {
            flagCheating(run, "flying_detected");
        }
        
        if (isSpeedHacking(player)) {
            flagCheating(run, "speed_hack_detected");
        }
        
        if (isClippingThroughBlocks(player)) {
            flagCheating(run, "block_clipping_detected");
        }
    }
    
    private void flagCheating(PlayerRun run, String reason) {
        run.setCheated(true);
        run.setCheatedReason(reason);
        run.setStatus(PlayerRun.RunStatus.CHEATED);
        
        plugin.getActiveRuns().remove(run.getPlayerId());
        
        Player player = plugin.getServer().getPlayer(UUID.fromString(run.getPlayerId()));
        if (player != null) {
            String language = getPlayerLanguage(player);
            String message = plugin.getParkourMessages().getCheatingDetected(language);
            player.sendMessage(ColorHelper.parseComponent(message));
        }
    }
    
    private Location getRespawnLocation(PlayerRun run) {
        ParkourCourse course = plugin.getCourseManager().getCourse(run.getCourseId());
        if (course == null) return null;
        
        // Get last reached checkpoint
        List<PlayerRun.CheckpointTime> cpTimes = run.getCheckpointTimes();
        if (cpTimes != null && !cpTimes.isEmpty()) {
            PlayerRun.CheckpointTime lastCp = cpTimes.get(cpTimes.size() - 1);
            Checkpoint checkpoint = course.getCheckpoints().get(lastCp.getCheckpointIndex());
            return checkpoint.getLocation();
        }
        
        // No checkpoints reached, respawn at start
        return course.getStartLocation();
    }
    
    private void showProgressBar(Player player, int current, int total) {
        StringBuilder bar = new StringBuilder();
        bar.append("§aProgress: §f[");
        
        int barLength = 20;
        int filled = (int) ((double) current / total * barLength);
        
        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                bar.append("§a█");
            } else {
                bar.append("§7█");
            }
        }
        
        bar.append("§f] §a").append(current).append("§f/§a").append(total);
        
        player.sendActionBar(Component.text(ColorHelper.parseString(bar.toString())));
    }
    
    private String getPlayerLanguage(Player player) {
        return MongoConfigsAPI.getLanguageManager().getPlayerLanguageOrDefault(
            player.getUniqueId().toString()
        );
    }
    
    // Anti-cheat helper methods...
    private boolean isFlying(Player player) { return player.isFlying(); }
    private boolean hasFlyPermission(Player player) { return player.hasPermission("parkour.fly"); }
    private boolean isSpeedHacking(Player player) { /* Implementation */ return false; }
    private boolean isClippingThroughBlocks(Player player) { /* Implementation */ return false; }
}
```

### LeaderboardManager.java

```java
public class LeaderboardManager {
    
    private final ParkourPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, List<LeaderboardEntry>> leaderboardCache = new ConcurrentHashMap<>();
    
    public LeaderboardManager(ParkourPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        
        loadLeaderboards();
        setupChangeStreams();
    }
    
    private void loadLeaderboards() {
        try {
            List<LeaderboardEntry> entries = configManager.getAll(LeaderboardEntry.class);
            
            // Group by course
            Map<String, List<LeaderboardEntry>> grouped = entries.stream()
                .collect(Collectors.groupingBy(LeaderboardEntry::getCourseId));
            
            // Sort each leaderboard and update positions
            for (Map.Entry<String, List<LeaderboardEntry>> courseEntries : grouped.entrySet()) {
                List<LeaderboardEntry> sorted = courseEntries.getValue().stream()
                    .sorted(Comparator.comparingLong(LeaderboardEntry::getBestTime))
                    .collect(Collectors.toList());
                
                // Update positions
                for (int i = 0; i < sorted.size(); i++) {
                    sorted.get(i).setPosition(i + 1);
                }
                
                leaderboardCache.put(courseEntries.getKey(), sorted);
            }
            
            plugin.getLogger().info("Loaded leaderboards for " + grouped.size() + " courses");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load leaderboards: " + e.getMessage());
        }
    }
    
    private void setupChangeStreams() {
        configManager.watchCollection(LeaderboardEntry.class, changeEvent -> {
            // Reload all leaderboards when any entry changes
            loadLeaderboards();
        });
    }
    
    public void updateLeaderboard(PlayerRun run) {
        if (run.getStatus() != PlayerRun.RunStatus.COMPLETED) return;
        
        String courseId = run.getCourseId();
        String playerId = run.getPlayerId();
        
        try {
            LeaderboardEntry entry = configManager.findFirst(LeaderboardEntry.class, 
                Filters.and(
                    Filters.eq("courseId", courseId),
                    Filters.eq("playerId", playerId)
                ));
            
            boolean isNewEntry = (entry == null);
            
            if (isNewEntry) {
                entry = new LeaderboardEntry();
                entry.setId(courseId + "_" + playerId);
                entry.setCourseId(courseId);
                entry.setPlayerId(playerId);
                entry.setPlayerName(getPlayerName(playerId));
                entry.setAttempts(0);
                entry.setCompletions(0);
            }
            
            // Update stats
            entry.setAttempts(entry.getAttempts() + 1);
            entry.setCompletions(entry.getCompletions() + 1);
            
            // Check for new best time
            boolean newBestTime = (entry.getBestTime() == 0 || run.getTotalTime() < entry.getBestTime());
            if (newBestTime) {
                entry.setBestTime(run.getTotalTime());
                entry.setAchievedAt(System.currentTimeMillis());
            }
            
            configManager.save(entry);
            
            // Update course best time if needed
            updateCourseBestTime(courseId, entry);
            
            // Trigger leaderboard reload
            loadLeaderboards();
            
            // Notify player of leaderboard changes
            notifyLeaderboardUpdate(playerId, courseId, entry.getPosition(), newBestTime);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update leaderboard for " + playerId + ": " + e.getMessage());
        }
    }
    
    private void updateCourseBestTime(String courseId, LeaderboardEntry entry) {
        try {
            ParkourCourse course = plugin.getCourseManager().getCourse(courseId);
            if (course != null && (course.getBestTime() == 0 || entry.getBestTime() < course.getBestTime())) {
                course.setBestTime(entry.getBestTime());
                course.setBestPlayerId(entry.getPlayerId());
                configManager.save(course);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update course best time: " + e.getMessage());
        }
    }
    
    public List<LeaderboardEntry> getLeaderboard(String courseId, int limit) {
        List<LeaderboardEntry> entries = leaderboardCache.get(courseId);
        if (entries == null) return Collections.emptyList();
        
        return entries.stream()
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    public int getPlayerPosition(String courseId, String playerId) {
        List<LeaderboardEntry> entries = leaderboardCache.get(courseId);
        if (entries == null) return -1;
        
        for (LeaderboardEntry entry : entries) {
            if (entry.getPlayerId().equals(playerId)) {
                return entry.getPosition();
            }
        }
        
        return -1;
    }
    
    public void updateAllLeaderboards() {
        loadLeaderboards();
        plugin.getLogger().info("Leaderboards updated");
    }
    
    private void notifyLeaderboardUpdate(String playerId, String courseId, int position, boolean newBestTime) {
        Player player = plugin.getServer().getPlayer(UUID.fromString(playerId));
        if (player == null || !player.isOnline()) return;
        
        String language = getPlayerLanguage(player);
        
        if (newBestTime) {
            String timeFormatted = formatTime(getPlayerBestTime(courseId, playerId));
            String message = plugin.getParkourMessages().getPersonalBest(language, timeFormatted);
            player.sendMessage(ColorHelper.parseComponent(message));
            
            // Check if it's a world record
            ParkourCourse course = plugin.getCourseManager().getCourse(courseId);
            if (course != null && playerId.equals(course.getBestPlayerId())) {
                String worldRecordMessage = plugin.getParkourMessages().getWorldRecord(
                    language, player.getName(), timeFormatted
                );
                plugin.getServer().broadcastMessage(ColorHelper.parseComponent(worldRecordMessage));
            }
        }
        
        if (position > 0 && position <= 10) {
            String message = plugin.getParkourMessages().getLeaderboardPosition(language, position);
            player.sendMessage(ColorHelper.parseComponent(message));
        }
    }
    
    private String getPlayerName(String playerId) {
        Player player = plugin.getServer().getPlayer(UUID.fromString(playerId));
        return player != null ? player.getName() : "Unknown";
    }
    
    private long getPlayerBestTime(String courseId, String playerId) {
        LeaderboardEntry entry = configManager.findFirst(LeaderboardEntry.class,
            Filters.and(
                Filters.eq("courseId", courseId),
                Filters.eq("playerId", playerId)
            ));
        return entry != null ? entry.getBestTime() : 0;
    }
    
    private String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long centiseconds = (milliseconds % 1000) / 10;
        return String.format("%d:%02d.%02d", minutes, seconds, centiseconds);
    }
    
    private String getPlayerLanguage(Player player) {
        return MongoConfigsAPI.getLanguageManager().getPlayerLanguageOrDefault(
            player.getUniqueId().toString()
        );
    }
}
```

## 🎨 GUI Components

### LeaderboardGUI.java

```java
public class LeaderboardGUI {
    
    private final ParkourPlugin plugin;
    private final ParkourMessages messages;
    
    public LeaderboardGUI(ParkourPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getParkourMessages();
    }
    
    public void openLeaderboard(Player player, String courseId) {
        ParkourCourse course = plugin.getCourseManager().getCourse(courseId);
        if (course == null) return;
        
        String language = getPlayerLanguage(player);
        String title = messages.getLeaderboardGUITitle(language, course.getNameKey());
        
        Inventory inventory = Bukkit.createInventory(null, 54, ColorHelper.parseString(title));
        
        // Add leaderboard entries
        List<LeaderboardEntry> entries = plugin.getLeaderboardManager().getLeaderboard(courseId, 45);
        
        for (int i = 0; i < entries.size() && i < 45; i++) {
            LeaderboardEntry entry = entries.get(i);
            addLeaderboardEntry(inventory, entry, i, language);
        }
        
        // Add player stats
        addPlayerStats(inventory, player, courseId, language);
        
        // Add navigation
        addNavigationItems(inventory, language);
        
        player.openInventory(inventory);
    }
    
    private void addLeaderboardEntry(Inventory inventory, LeaderboardEntry entry, int slot, String language) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        
        // Set skull owner for player head
        if (meta instanceof SkullMeta) {
            ((SkullMeta) meta).setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(entry.getPlayerId())));
        }
        
        String timeFormatted = formatTime(entry.getBestTime());
        String displayName = messages.getLeaderboardEntryName(language, 
            entry.getPosition(), entry.getPlayerName());
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        List<String> lore = new ArrayList<>();
        lore.add(ColorHelper.parseString("&7Time: &f" + timeFormatted));
        lore.add(ColorHelper.parseString("&7Completions: &f" + entry.getCompletions()));
        lore.add(ColorHelper.parseString("&7Attempts: &f" + entry.getAttempts()));
        
        if (entry.getPosition() <= 3) {
            lore.add("");
            lore.add(ColorHelper.parseString(getPositionColor(entry.getPosition()) + 
                messages.getTopPosition(language, entry.getPosition())));
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    private void addPlayerStats(Inventory inventory, Player player, String courseId, String language) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        
        String displayName = messages.getPlayerStatsTitle(language);
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        List<String> lore = new ArrayList<>();
        lore.add(ColorHelper.parseString("&7Player: &f" + player.getName()));
        
        int position = plugin.getLeaderboardManager().getPlayerPosition(courseId, player.getUniqueId().toString());
        if (position > 0) {
            lore.add(ColorHelper.parseString("&7Position: &f#" + position));
        } else {
            lore.add(ColorHelper.parseString("&7Position: &fNot ranked"));
        }
        
        PlayerStats stats = plugin.getStatisticsManager().getPlayerStats(player.getUniqueId().toString());
        if (stats != null && stats.getCourseStats().containsKey(courseId)) {
            PlayerStats.CourseStats courseStats = stats.getCourseStats().get(courseId);
            lore.add(ColorHelper.parseString("&7Best Time: &f" + formatTime(courseStats.getBestTime())));
            lore.add(ColorHelper.parseString("&7Attempts: &f" + courseStats.getAttempts()));
            lore.add(ColorHelper.parseString("&7Completions: &f" + courseStats.getCompletions()));
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        inventory.setItem(49, item);
    }
    
    private void addNavigationItems(Inventory inventory, String language) {
        // Back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ColorHelper.parseString("&7" + messages.getBackButton(language)));
        backItem.setItemMeta(backMeta);
        inventory.setItem(45, backItem);
        
        // Refresh button
        ItemStack refreshItem = new ItemStack(Material.CLOCK);
        ItemMeta refreshMeta = refreshItem.getItemMeta();
        refreshMeta.setDisplayName(ColorHelper.parseString("&a" + messages.getRefreshButton(language)));
        refreshItem.setItemMeta(refreshMeta);
        inventory.setItem(53, refreshItem);
    }
    
    private String getPositionColor(int position) {
        switch (position) {
            case 1: return "&6"; // Gold
            case 2: return "&f"; // Silver  
            case 3: return "&6"; // Bronze
            default: return "&7"; // Gray
        }
    }
    
    private String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long centiseconds = (milliseconds % 1000) / 10;
        return String.format("%d:%02d.%02d", minutes, seconds, centiseconds);
    }
    
    private String getPlayerLanguage(Player player) {
        return MongoConfigsAPI.getLanguageManager().getPlayerLanguageOrDefault(
            player.getUniqueId().toString()
        );
    }
}
```

## 🔄 Real-time Updates

The parkour system uses Change Streams for real-time leaderboard updates:

```java
// Real-time leaderboard updates
configManager.watchCollection(LeaderboardEntry.class, changeEvent -> {
    LeaderboardEntry entry = changeEvent.getDocument();
    if (entry != null) {
        // Update cached leaderboard
        updateCachedLeaderboard(entry.getCourseId());
        
        // Notify players viewing this leaderboard
        notifyLeaderboardViewers(entry.getCourseId());
    }
});

// Course record updates
configManager.watchCollection(ParkourCourse.class, changeEvent -> {
    ParkourCourse course = changeEvent.getDocument();
    if (course != null && course.getBestPlayerId() != null) {
        // Broadcast new world record
        broadcastNewRecord(course);
    }
});
```

## 📊 Advanced Features

### Anti-cheat System

```java
public void performAntiCheatChecks(Player player) {
    PlayerRun run = activeRuns.get(player.getUniqueId().toString());
    if (run == null) return;
    
    // Speed check
    if (isMovingTooFast(player, run)) {
        flagCheating(run, "speed_hack");
    }
    
    // Flight check
    if (isFlyingWithoutPermission(player)) {
        flagCheating(run, "unauthorized_flight");
    }
    
    // Block clipping check
    if (isInsideBlock(player)) {
        flagCheating(run, "block_clipping");
    }
    
    // Teleport check
    if (detectedTeleport(player, run)) {
        flagCheating(run, "teleport_detected");
    }
}
```

### Achievement System

```java
public void checkAchievements(Player player) {
    PlayerStats stats = getPlayerStats(player.getUniqueId().toString());
    if (stats == null) return;
    
    // Check completion achievements
    if (stats.getTotalCompletions() >= 10 && !hasAchievement(stats, "first_10_completions")) {
        unlockAchievement(player, "first_10_completions");
    }
    
    if (stats.getTotalCompletions() >= 100 && !hasAchievement(stats, "century_club")) {
        unlockAchievement(player, "century_club");
    }
    
    // Check speed achievements
    for (PlayerStats.CourseStats courseStats : stats.getCourseStats().values()) {
        if (courseStats.getBestTime() > 0 && courseStats.getBestTime() < 30000) { // Under 30 seconds
            unlockAchievement(player, "speed_demon_" + courseStats.getCourseId());
        }
    }
    
    // Check streak achievements
    if (hasCompletionStreak(player, 5)) {
        unlockAchievement(player, "completion_streak_5");
    }
}
```

## 📈 Performance Optimizations

- **Memory Caching**: Active runs and leaderboards cached in memory
- **Async Operations**: Database operations run asynchronously
- **Batch Updates**: Multiple leaderboard updates batched together
- **Efficient Queries**: Indexed database queries for fast lookups
- **Particle Optimization**: Checkpoint particles only shown to relevant players

---

*Next: Learn about [[Home System Example]] for player home management.*