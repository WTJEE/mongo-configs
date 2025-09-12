# Migration Guide

Comprehensive guide for migrating existing configuration systems to the MongoDB Configs API, including data migration, code refactoring, and testing strategies.

## 🔄 Migration Overview

The Migration Guide provides step-by-step instructions for migrating from existing configuration systems (YAML, JSON files, databases) to the MongoDB Configs API.

## 📋 Migration Assessment

### Current System Analysis

#### Configuration Inventory

```java
public class ConfigurationInventory {
    
    private final Map<String, ConfigurationSource> configSources;
    private final List<MigrationIssue> issues;
    
    public ConfigurationInventory() {
        this.configSources = new HashMap<>();
        this.issues = new ArrayList<>();
    }
    
    public void analyzeCurrentSystem() {
        // Analyze file-based configurations
        analyzeFileConfigurations();
        
        // Analyze database configurations
        analyzeDatabaseConfigurations();
        
        // Analyze in-memory configurations
        analyzeInMemoryConfigurations();
        
        // Generate migration report
        generateMigrationReport();
    }
    
    private void analyzeFileConfigurations() {
        File configDir = new File("plugins/YourPlugin/config.yml");
        
        if (configDir.exists()) {
            try {
                // Parse YAML/JSON files
                ConfigurationSource source = new ConfigurationSource();
                source.type = "YAML_FILE";
                source.location = configDir.getAbsolutePath();
                source.size = FileUtils.sizeOf(configDir);
                source.lastModified = configDir.lastModified();
                
                // Analyze structure
                source.structure = analyzeFileStructure(configDir);
                source.dependencies = findDependencies(configDir);
                
                configSources.put("main_config", source);
                
            } catch (Exception e) {
                issues.add(new MigrationIssue(
                    Severity.ERROR,
                    "Failed to analyze config file: " + e.getMessage(),
                    "main_config"
                ));
            }
        }
    }
    
    private void analyzeDatabaseConfigurations() {
        // Check for existing database tables/collections
        // This would depend on your current database system
        try {
            // Example for SQL database
            if (hasSQLDatabase()) {
                ConfigurationSource source = new ConfigurationSource();
                source.type = "SQL_DATABASE";
                source.location = "your_database.configs_table";
                source.size = getTableSize("configs");
                
                configSources.put("database_config", source);
            }
            
        } catch (Exception e) {
            issues.add(new MigrationIssue(
                Severity.WARNING,
                "Database analysis failed: " + e.getMessage(),
                "database_config"
            ));
        }
    }
    
    private void analyzeInMemoryConfigurations() {
        // Analyze configurations stored in memory
        // This might include static variables, maps, etc.
        ConfigurationSource source = new ConfigurationSource();
        source.type = "IN_MEMORY";
        source.location = "Static variables and collections";
        
        configSources.put("memory_config", source);
    }
    
    private Map<String, Object> analyzeFileStructure(File configFile) {
        // Analyze the structure of configuration files
        Map<String, Object> structure = new HashMap<>();
        
        try {
            if (configFile.getName().endsWith(".yml") || configFile.getName().endsWith(".yaml")) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(new FileInputStream(configFile));
                structure.put("format", "YAML");
                structure.put("keys", data.keySet());
                structure.put("complexity", calculateComplexity(data));
                
            } else if (configFile.getName().endsWith(".json")) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> data = mapper.readValue(configFile, Map.class);
                structure.put("format", "JSON");
                structure.put("keys", data.keySet());
                structure.put("complexity", calculateComplexity(data));
            }
            
        } catch (Exception e) {
            structure.put("error", "Failed to parse: " + e.getMessage());
        }
        
        return structure;
    }
    
    private int calculateComplexity(Map<String, Object> data) {
        // Calculate configuration complexity
        return calculateComplexityRecursive(data, 0);
    }
    
    private int calculateComplexityRecursive(Object obj, int depth) {
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            int complexity = map.size();
            for (Object value : map.values()) {
                complexity += calculateComplexityRecursive(value, depth + 1);
            }
            return complexity;
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            int complexity = list.size();
            for (Object item : list) {
                complexity += calculateComplexityRecursive(item, depth + 1);
            }
            return complexity;
        }
        return 1;
    }
    
    private List<String> findDependencies(File configFile) {
        List<String> dependencies = new ArrayList<>();
        
        try {
            String content = FileUtils.readFileToString(configFile, StandardCharsets.UTF_8);
            
            // Find references to other files
            Pattern filePattern = Pattern.compile("(file|path|config):\\s*([^\\s]+)");
            Matcher matcher = filePattern.matcher(content);
            
            while (matcher.find()) {
                dependencies.add(matcher.group(2));
            }
            
        } catch (Exception e) {
            // Ignore errors in dependency analysis
        }
        
        return dependencies;
    }
    
    private void generateMigrationReport() {
        System.out.println("=== MIGRATION ASSESSMENT REPORT ===");
        System.out.println("Configuration Sources Found: " + configSources.size());
        
        for (Map.Entry<String, ConfigurationSource> entry : configSources.entrySet()) {
            ConfigurationSource source = entry.getValue();
            System.out.println("\nSource: " + entry.getKey());
            System.out.println("  Type: " + source.type);
            System.out.println("  Location: " + source.location);
            System.out.println("  Size: " + (source.size / 1024) + " KB");
            System.out.println("  Last Modified: " + new Date(source.lastModified));
        }
        
        if (!issues.isEmpty()) {
            System.out.println("\nIssues Found:");
            for (MigrationIssue issue : issues) {
                System.out.println("  " + issue.severity + ": " + issue.message);
            }
        }
        
        // Estimate migration effort
        MigrationEffort effort = estimateMigrationEffort();
        System.out.println("\nEstimated Migration Effort:");
        System.out.println("  Complexity: " + effort.complexity);
        System.out.println("  Estimated Hours: " + effort.estimatedHours);
        System.out.println("  Risk Level: " + effort.riskLevel);
    }
    
    private MigrationEffort estimateMigrationEffort() {
        int totalComplexity = configSources.values().stream()
            .mapToInt(source -> {
                if (source.structure != null && source.structure.containsKey("complexity")) {
                    return (Integer) source.structure.get("complexity");
                }
                return 1;
            })
            .sum();
        
        String complexity = totalComplexity > 100 ? "HIGH" : totalComplexity > 50 ? "MEDIUM" : "LOW";
        int estimatedHours = totalComplexity * 2; // Rough estimate: 2 hours per complexity unit
        String riskLevel = issues.stream().anyMatch(i -> i.severity == Severity.ERROR) ? "HIGH" : "MEDIUM";
        
        return new MigrationEffort(complexity, estimatedHours, riskLevel);
    }
    
    // Supporting classes
    public static class ConfigurationSource {
        public String type;
        public String location;
        public long size;
        public long lastModified;
        public Map<String, Object> structure;
        public List<String> dependencies;
    }
    
    public static class MigrationIssue {
        public final Severity severity;
        public final String message;
        public final String source;
        
        public MigrationIssue(Severity severity, String message, String source) {
            this.severity = severity;
            this.message = message;
            this.source = source;
        }
    }
    
    public enum Severity {
        INFO, WARNING, ERROR
    }
    
    public static class MigrationEffort {
        public final String complexity;
        public final int estimatedHours;
        public final String riskLevel;
        
        public MigrationEffort(String complexity, int estimatedHours, String riskLevel) {
            this.complexity = complexity;
            this.estimatedHours = estimatedHours;
            this.riskLevel = riskLevel;
        }
    }
    
    // Utility methods
    private boolean hasSQLDatabase() {
        // Check if SQL database is configured
        return false; // Placeholder
    }
    
    private long getTableSize(String tableName) {
        // Get size of database table
        return 0; // Placeholder
    }
}
```

## 🔄 Migration Strategies

### File-Based Migration

#### YAML to MongoDB Migration

```java
public class YamlToMongoMigration {
    
    private final ConfigManager configManager;
    private final ObjectMapper objectMapper;
    private final Yaml yaml;
    
    public YamlToMongoMigration(ConfigManager configManager) {
        this.configManager = configManager;
        this.objectMapper = new ObjectMapper();
        this.yaml = new Yaml();
    }
    
    public void migrateYamlFile(File yamlFile, String configKey) {
        try {
            // Read YAML file
            Map<String, Object> yamlData = yaml.load(new FileInputStream(yamlFile));
            
            // Convert to configuration object
            MigratedConfig config = convertYamlToConfig(yamlData);
            
            // Save to MongoDB
            configManager.save(config);
            
            // Create backup
            createBackup(yamlFile, configKey);
            
            MongoConfigsPlugin.getInstance().getLogger().info(
                "Successfully migrated YAML config: " + configKey);
                
        } catch (Exception e) {
            MongoConfigsPlugin.getInstance().getLogger().severe(
                "Failed to migrate YAML config '" + configKey + "': " + e.getMessage());
            throw new MigrationException("YAML migration failed", e);
        }
    }
    
    private MigratedConfig convertYamlToConfig(Map<String, Object> yamlData) {
        MigratedConfig config = new MigratedConfig();
        
        // Convert top-level fields
        config.serverName = (String) yamlData.get("server-name");
        config.debugMode = (Boolean) yamlData.getOrDefault("debug", false);
        config.maxPlayers = ((Number) yamlData.getOrDefault("max-players", 100)).intValue();
        
        // Convert nested objects
        @SuppressWarnings("unchecked")
        Map<String, Object> databaseConfig = (Map<String, Object>) yamlData.get("database");
        if (databaseConfig != null) {
            config.database = new DatabaseConfig();
            config.database.host = (String) databaseConfig.get("host");
            config.database.port = ((Number) databaseConfig.getOrDefault("port", 3306)).intValue();
            config.database.database = (String) databaseConfig.get("database");
        }
        
        // Convert lists
        @SuppressWarnings("unchecked")
        List<String> worlds = (List<String>) yamlData.get("worlds");
        if (worlds != null) {
            config.worlds = new ArrayList<>(worlds);
        }
        
        // Convert maps
        @SuppressWarnings("unchecked")
        Map<String, Object> permissions = (Map<String, Object>) yamlData.get("permissions");
        if (permissions != null) {
            config.permissions = new HashMap<>();
            for (Map.Entry<String, Object> entry : permissions.entrySet()) {
                if (entry.getValue() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> permList = (List<String>) entry.getValue();
                    config.permissions.put(entry.getKey(), permList);
                }
            }
        }
        
        // Set migration metadata
        config.migratedFrom = "YAML";
        config.migrationDate = System.currentTimeMillis();
        config.originalFormat = "YAML";
        
        return config;
    }
    
    private void createBackup(File originalFile, String configKey) {
        try {
            File backupDir = new File("plugins/YourPlugin/backups");
            backupDir.mkdirs();
            
            File backupFile = new File(backupDir, 
                configKey + "_backup_" + System.currentTimeMillis() + ".yml");
            
            Files.copy(originalFile.toPath(), backupFile.toPath());
            
            MongoConfigsPlugin.getInstance().getLogger().info(
                "Created backup: " + backupFile.getAbsolutePath());
                
        } catch (Exception e) {
            MongoConfigsPlugin.getInstance().getLogger().warning(
                "Failed to create backup for " + configKey + ": " + e.getMessage());
        }
    }
    
    // Batch migration for multiple files
    public void migrateYamlDirectory(File yamlDirectory) {
        if (!yamlDirectory.exists() || !yamlDirectory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + yamlDirectory);
        }
        
        File[] yamlFiles = yamlDirectory.listFiles((dir, name) -> 
            name.endsWith(".yml") || name.endsWith(".yaml"));
        
        if (yamlFiles == null) {
            MongoConfigsPlugin.getInstance().getLogger().info("No YAML files found in directory");
            return;
        }
        
        MongoConfigsPlugin.getInstance().getLogger().info(
            "Starting batch migration of " + yamlFiles.length + " YAML files");
        
        int successCount = 0;
        int failureCount = 0;
        
        for (File yamlFile : yamlFiles) {
            try {
                String configKey = yamlFile.getName().replaceAll("\\.(yml|yaml)$", "");
                migrateYamlFile(yamlFile, configKey);
                successCount++;
                
            } catch (Exception e) {
                failureCount++;
                MongoConfigsPlugin.getInstance().getLogger().severe(
                    "Failed to migrate " + yamlFile.getName() + ": " + e.getMessage());
            }
        }
        
        MongoConfigsPlugin.getInstance().getLogger().info(
            "Batch migration completed: " + successCount + " successful, " + failureCount + " failed");
    }
    
    // Configuration classes
    @ConfigsFileProperties
    @ConfigsDatabase(database = "mongoconfigs")
    @ConfigsCollection(collection = "migrated_configs")
    public static class MigratedConfig {
        public String serverName;
        public boolean debugMode;
        public int maxPlayers;
        public DatabaseConfig database;
        public List<String> worlds;
        public Map<String, List<String>> permissions;
        
        // Migration metadata
        public String migratedFrom;
        public long migrationDate;
        public String originalFormat;
    }
    
    public static class DatabaseConfig {
        public String host;
        public int port;
        public String database;
        public String username;
        public String password;
    }
}
```

#### JSON to MongoDB Migration

```java
public class JsonToMongoMigration {
    
    private final ConfigManager configManager;
    private final ObjectMapper objectMapper;
    
    public JsonToMongoMigration(ConfigManager configManager) {
        this.configManager = configManager;
        this.objectMapper = new ObjectMapper();
    }
    
    public void migrateJsonFile(File jsonFile, String configKey) {
        try {
            // Read JSON file
            JsonNode jsonNode = objectMapper.readTree(jsonFile);
            
            // Convert to configuration object
            MigratedConfig config = convertJsonToConfig(jsonNode);
            
            // Save to MongoDB
            configManager.save(config);
            
            // Create backup
            createBackup(jsonFile, configKey);
            
            MongoConfigsPlugin.getInstance().getLogger().info(
                "Successfully migrated JSON config: " + configKey);
                
        } catch (Exception e) {
            MongoConfigsPlugin.getInstance().getLogger().severe(
                "Failed to migrate JSON config '" + configKey + "': " + e.getMessage());
            throw new MigrationException("JSON migration failed", e);
        }
    }
    
    private MigratedConfig convertJsonToConfig(JsonNode jsonNode) {
        MigratedConfig config = new MigratedConfig();
        
        // Convert fields using Jackson
        config.serverName = jsonNode.get("serverName")?.asText();
        config.debugMode = jsonNode.get("debugMode")?.asBoolean(false);
        config.maxPlayers = jsonNode.get("maxPlayers")?.asInt(100);
        
        // Convert nested objects
        JsonNode databaseNode = jsonNode.get("database");
        if (databaseNode != null && !databaseNode.isNull()) {
            config.database = new DatabaseConfig();
            config.database.host = databaseNode.get("host")?.asText();
            config.database.port = databaseNode.get("port")?.asInt(3306);
            config.database.database = databaseNode.get("database")?.asText();
        }
        
        // Convert arrays
        JsonNode worldsNode = jsonNode.get("worlds");
        if (worldsNode != null && worldsNode.isArray()) {
            config.worlds = new ArrayList<>();
            for (JsonNode worldNode : worldsNode) {
                config.worlds.add(worldNode.asText());
            }
        }
        
        // Convert objects/maps
        JsonNode permissionsNode = jsonNode.get("permissions");
        if (permissionsNode != null && permissionsNode.isObject()) {
            config.permissions = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = permissionsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue().isArray()) {
                    List<String> permList = new ArrayList<>();
                    for (JsonNode permNode : field.getValue()) {
                        permList.add(permNode.asText());
                    }
                    config.permissions.put(field.getKey(), permList);
                }
            }
        }
        
        // Set migration metadata
        config.migratedFrom = "JSON";
        config.migrationDate = System.currentTimeMillis();
        config.originalFormat = "JSON";
        
        return config;
    }
    
    private void createBackup(File originalFile, String configKey) {
        try {
            File backupDir = new File("plugins/YourPlugin/backups");
            backupDir.mkdirs();
            
            File backupFile = new File(backupDir, 
                configKey + "_backup_" + System.currentTimeMillis() + ".json");
            
            Files.copy(originalFile.toPath(), backupFile.toPath());
            
            MongoConfigsPlugin.getInstance().getLogger().info(
                "Created backup: " + backupFile.getAbsolutePath());
                
        } catch (Exception e) {
            MongoConfigsPlugin.getInstance().getLogger().warning(
                "Failed to create backup for " + configKey + ": " + e.getMessage());
        }
    }
    
    // Use the same MigratedConfig class as YAML migration
    @ConfigsFileProperties
    @ConfigsDatabase(database = "mongoconfigs")
    @ConfigsCollection(collection = "migrated_configs")
    public static class MigratedConfig {
        // Same fields as in YAML migration
        public String serverName;
        public boolean debugMode;
        public int maxPlayers;
        public DatabaseConfig database;
        public List<String> worlds;
        public Map<String, List<String>> permissions;
        
        // Migration metadata
        public String migratedFrom;
        public long migrationDate;
        public String originalFormat;
    }
    
    public static class DatabaseConfig {
        public String host;
        public int port;
        public String database;
        public String username;
        public String password;
    }
}
```

### Database Migration

#### SQL Database to MongoDB Migration

```java
public class SqlToMongoMigration {
    
    private final ConfigManager configManager;
    private final DataSource sqlDataSource;
    
    public SqlToMongoMigration(ConfigManager configManager, DataSource sqlDataSource) {
        this.configManager = configManager;
        this.sqlDataSource = sqlDataSource;
    }
    
    public void migrateSqlTable(String tableName, String configKeyPrefix) {
        try (Connection conn = sqlDataSource.getConnection()) {
            
            // Get table metadata
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, tableName, null);
            
            // Build column list
            List<String> columnNames = new ArrayList<>();
            Map<String, Integer> columnTypes = new HashMap<>();
            
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                int columnType = columns.getInt("DATA_TYPE");
                columnNames.add(columnName);
                columnTypes.put(columnName, columnType);
            }
            
            // Query all rows
            String sql = "SELECT * FROM " + tableName;
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                int migratedCount = 0;
                while (rs.next()) {
                    // Convert row to config object
                    MigratedConfig config = convertSqlRowToConfig(rs, columnNames, columnTypes);
                    
                    // Generate unique config key
                    String configKey = configKeyPrefix + "_" + rs.getString("id");
                    
                    // Save to MongoDB
                    configManager.save(config);
                    
                    migratedCount++;
                }
                
                MongoConfigsPlugin.getInstance().getLogger().info(
                    "Migrated " + migratedCount + " rows from table " + tableName);
            }
            
        } catch (Exception e) {
            MongoConfigsPlugin.getInstance().getLogger().severe(
                "Failed to migrate SQL table '" + tableName + "': " + e.getMessage());
            throw new MigrationException("SQL migration failed", e);
        }
    }
    
    private MigratedConfig convertSqlRowToConfig(ResultSet rs, List<String> columnNames, 
                                                Map<String, Integer> columnTypes) throws SQLException {
        MigratedConfig config = new MigratedConfig();
        
        // Convert columns based on their SQL types
        for (String columnName : columnNames) {
            int sqlType = columnTypes.get(columnName);
            
            switch (sqlType) {
                case Types.VARCHAR:
                case Types.LONGVARCHAR:
                    String stringValue = rs.getString(columnName);
                    setConfigField(config, columnName, stringValue);
                    break;
                    
                case Types.INTEGER:
                case Types.SMALLINT:
                    int intValue = rs.getInt(columnName);
                    setConfigField(config, columnName, intValue);
                    break;
                    
                case Types.BOOLEAN:
                case Types.BIT:
                    boolean boolValue = rs.getBoolean(columnName);
                    setConfigField(config, columnName, boolValue);
                    break;
                    
                case Types.TIMESTAMP:
                case Types.DATE:
                    Timestamp timestampValue = rs.getTimestamp(columnName);
                    if (timestampValue != null) {
                        setConfigField(config, columnName, timestampValue.getTime());
                    }
                    break;
                    
                // Add more type conversions as needed
                default:
                    Object objectValue = rs.getObject(columnName);
                    setConfigField(config, columnName, objectValue);
                    break;
            }
        }
        
        // Set migration metadata
        config.migratedFrom = "SQL_DATABASE";
        config.migrationDate = System.currentTimeMillis();
        config.originalFormat = "SQL_ROW";
        
        return config;
    }
    
    private void setConfigField(MigratedConfig config, String fieldName, Object value) {
        try {
            // Use reflection to set field values
            Field field = MigratedConfig.class.getField(fieldName);
            field.setAccessible(true);
            field.set(config, value);
            
        } catch (Exception e) {
            // If field doesn't exist, store in additionalData map
            if (config.additionalData == null) {
                config.additionalData = new HashMap<>();
            }
            config.additionalData.put(fieldName, value);
        }
    }
    
    // Configuration class for SQL migration
    @ConfigsFileProperties
    @ConfigsDatabase(database = "mongoconfigs")
    @ConfigsCollection(collection = "migrated_configs")
    public static class MigratedConfig {
        public String id;
        public String name;
        public String value;
        public boolean enabled;
        public long createdAt;
        public long updatedAt;
        
        // Additional data for unmapped columns
        public Map<String, Object> additionalData;
        
        // Migration metadata
        public String migratedFrom;
        public long migrationDate;
        public String originalFormat;
    }
}
```

## 🔧 Code Migration

### Refactoring Configuration Access

#### Before Migration (File-based)

```java
public class OldConfigManager {
    
    private FileConfiguration config;
    private final JavaPlugin plugin;
    
    public OldConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    private void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
    }
    
    public String getServerName() {
        return config.getString("server-name", "MyServer");
    }
    
    public boolean isDebugMode() {
        return config.getBoolean("debug", false);
    }
    
    public int getMaxPlayers() {
        return config.getInt("max-players", 100);
    }
    
    public List<String> getWorlds() {
        return config.getStringList("worlds");
    }
    
    public void saveConfig() {
        plugin.saveConfig();
    }
    
    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }
}
```

#### After Migration (MongoDB Configs API)

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "mongoconfigs")
@ConfigsCollection(collection = "server_config")
public class ServerConfig {
    
    public String serverName = "MyServer";
    public boolean debugMode = false;
    public int maxPlayers = 100;
    public List<String> worlds = new ArrayList<>();
    
    // Migration metadata
    public boolean migrated = false;
    public long migratedAt;
}

public class NewConfigManager {
    
    private final ConfigManager configManager;
    private final String configKey = "server_config";
    
    public NewConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    public ServerConfig getConfig() {
        ServerConfig config = configManager.get(ServerConfig.class, configKey);
        
        if (config == null) {
            // Create default config
            config = new ServerConfig();
            configManager.save(config);
        }
        
        return config;
    }
    
    public String getServerName() {
        return getConfig().serverName;
    }
    
    public boolean isDebugMode() {
        return getConfig().debugMode;
    }
    
    public int getMaxPlayers() {
        return getConfig().maxPlayers;
    }
    
    public List<String> getWorlds() {
        return getConfig().worlds;
    }
    
    public void updateServerName(String serverName) {
        ServerConfig config = getConfig();
        config.serverName = serverName;
        configManager.save(config);
    }
    
    public void setDebugMode(boolean debugMode) {
        ServerConfig config = getConfig();
        config.debugMode = debugMode;
        configManager.save(config);
    }
    
    public void reloadConfig() {
        // MongoDB Configs API handles real-time updates
        // No manual reload needed
        configManager.invalidateCache(configKey);
    }
}
```

### Migrating Service Classes

#### Before Migration

```java
public class OldMessageService {
    
    private final JavaPlugin plugin;
    private Map<String, Map<String, String>> messages;
    
    public OldMessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }
    
    private void loadMessages() {
        messages = new HashMap<>();
        
        // Load English messages
        FileConfiguration enConfig = YamlConfiguration.loadConfiguration(
            new File(plugin.getDataFolder(), "messages_en.yml"));
        messages.put("en", enConfig.getValues(true).entrySet().stream()
            .filter(entry -> entry.getValue() instanceof String)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> (String) entry.getValue()
            )));
        
        // Load other languages...
    }
    
    public String getMessage(String key, String language) {
        Map<String, String> langMessages = messages.get(language);
        if (langMessages == null) {
            langMessages = messages.get("en"); // Fallback to English
        }
        
        return langMessages.getOrDefault(key, key);
    }
    
    public String formatMessage(String key, String language, Object... args) {
        String message = getMessage(key, language);
        return String.format(message, args);
    }
}
```

#### After Migration

```java
public class NewMessageService {
    
    private final Messages messagesApi;
    private final LanguageManager languageManager;
    
    public NewMessageService(Messages messagesApi, LanguageManager languageManager) {
        this.messagesApi = messagesApi;
        this.languageManager = languageManager;
    }
    
    public String getMessage(String key, String language) {
        try {
            return messagesApi.get(language, key, String.class);
        } catch (Exception e) {
            // Fallback to English
            try {
                return messagesApi.get("en", key, String.class);
            } catch (Exception e2) {
                return key; // Return key if not found
            }
        }
    }
    
    public String formatMessage(String key, String language, Object... args) {
        String message = getMessage(key, language);
        
        // Use MessageFormat for proper placeholder replacement
        MessageFormat format = new MessageFormat(message, Locale.forLanguageTag(language));
        return format.format(args);
    }
    
    public String getMessage(Player player, String key) {
        String language = languageManager.getPlayerLanguage(player);
        return getMessage(key, language);
    }
    
    public String formatMessage(Player player, String key, Object... args) {
        String language = languageManager.getPlayerLanguage(player);
        return formatMessage(key, language, args);
    }
}
```

## 🧪 Testing Migration

### Migration Testing Strategy

```java
public class MigrationTestSuite {
    
    private final ConfigManager configManager;
    private final TestDataGenerator testDataGenerator;
    
    public MigrationTestSuite(ConfigManager configManager) {
        this.configManager = configManager;
        this.testDataGenerator = new TestDataGenerator();
    }
    
    public MigrationTestResult runMigrationTests() {
        MigrationTestResult result = new MigrationTestResult();
        
        try {
            // Test data integrity
            result.dataIntegrityTest = testDataIntegrity();
            
            // Test configuration access
            result.configAccessTest = testConfigurationAccess();
            
            // Test performance
            result.performanceTest = testPerformance();
            
            // Test error handling
            result.errorHandlingTest = testErrorHandling();
            
            // Test concurrent access
            result.concurrencyTest = testConcurrency();
            
            result.success = result.allTestsPassed();
            
        } catch (Exception e) {
            result.success = false;
            result.error = e.getMessage();
        }
        
        return result;
    }
    
    private TestResult testDataIntegrity() {
        try {
            // Generate test data
            TestConfig originalConfig = testDataGenerator.generateTestConfig();
            
            // Save to MongoDB
            String testKey = "test_data_integrity";
            configManager.save(originalConfig);
            
            // Load from MongoDB
            TestConfig loadedConfig = configManager.get(TestConfig.class, testKey);
            
            // Compare data
            boolean dataMatches = compareConfigs(originalConfig, loadedConfig);
            
            if (dataMatches) {
                return new TestResult(true, "Data integrity test passed");
            } else {
                return new TestResult(false, "Data integrity test failed - data mismatch");
            }
            
        } catch (Exception e) {
            return new TestResult(false, "Data integrity test failed: " + e.getMessage());
        }
    }
    
    private TestResult testConfigurationAccess() {
        try {
            // Test basic CRUD operations
            TestConfig config = testDataGenerator.generateTestConfig();
            String testKey = "test_config_access";
            
            // Create
            configManager.save(config);
            
            // Read
            TestConfig loaded = configManager.get(TestConfig.class, testKey);
            if (loaded == null) {
                return new TestResult(false, "Configuration read failed");
            }
            
            // Update
            loaded.testField = "updated_value";
            configManager.save(loaded);
            
            // Verify update
            TestConfig updated = configManager.get(TestConfig.class, testKey);
            if (!"updated_value".equals(updated.testField)) {
                return new TestResult(false, "Configuration update failed");
            }
            
            // Delete
            configManager.delete(testKey);
            
            // Verify deletion
            TestConfig deleted = configManager.get(TestConfig.class, testKey);
            if (deleted != null) {
                return new TestResult(false, "Configuration deletion failed");
            }
            
            return new TestResult(true, "Configuration access test passed");
            
        } catch (Exception e) {
            return new TestResult(false, "Configuration access test failed: " + e.getMessage());
        }
    }
    
    private TestResult testPerformance() {
        try {
            int testIterations = 1000;
            List<TestConfig> testConfigs = new ArrayList<>();
            
            // Generate test data
            for (int i = 0; i < testIterations; i++) {
                testConfigs.add(testDataGenerator.generateTestConfig());
            }
            
            // Test save performance
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < testIterations; i++) {
                configManager.save(testConfigs.get(i));
            }
            long saveTime = System.currentTimeMillis() - startTime;
            
            // Test load performance
            startTime = System.currentTimeMillis();
            for (int i = 0; i < testIterations; i++) {
                configManager.get(TestConfig.class, "test_performance_" + i);
            }
            long loadTime = System.currentTimeMillis() - startTime;
            
            // Check performance thresholds
            double avgSaveTime = (double) saveTime / testIterations;
            double avgLoadTime = (double) loadTime / testIterations;
            
            if (avgSaveTime > 50 || avgLoadTime > 10) { // 50ms save, 10ms load threshold
                return new TestResult(false, 
                    String.format("Performance test failed - Save: %.2fms, Load: %.2fms", 
                        avgSaveTime, avgLoadTime));
            }
            
            return new TestResult(true, 
                String.format("Performance test passed - Save: %.2fms, Load: %.2fms", 
                    avgSaveTime, avgLoadTime));
                    
        } catch (Exception e) {
            return new TestResult(false, "Performance test failed: " + e.getMessage());
        }
    }
    
    private TestResult testErrorHandling() {
        try {
            // Test invalid config key
            TestConfig result = configManager.get(TestConfig.class, "invalid_key_12345");
            if (result != null) {
                return new TestResult(false, "Error handling test failed - should return null for invalid key");
            }
            
            // Test null config
            try {
                configManager.save(null);
                return new TestResult(false, "Error handling test failed - should throw exception for null config");
            } catch (Exception e) {
                // Expected exception
            }
            
            // Test invalid config class
            try {
                configManager.get(String.class, "test_key");
                return new TestResult(false, "Error handling test failed - should throw exception for invalid class");
            } catch (Exception e) {
                // Expected exception
            }
            
            return new TestResult(true, "Error handling test passed");
            
        } catch (Exception e) {
            return new TestResult(false, "Error handling test failed: " + e.getMessage());
        }
    }
    
    private TestResult testConcurrency() {
        try {
            int threadCount = 10;
            int operationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<Void>> futures = new ArrayList<>();
            
            // Submit concurrent operations
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                Future<Void> future = executor.submit(() -> {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "concurrency_test_" + threadId + "_" + j;
                        TestConfig config = testDataGenerator.generateTestConfig();
                        config.testField = "thread_" + threadId + "_op_" + j;
                        
                        configManager.save(config);
                        
                        // Verify the save worked
                        TestConfig loaded = configManager.get(TestConfig.class, key);
                        if (loaded == null || !config.testField.equals(loaded.testField)) {
                            throw new RuntimeException("Concurrency test failed for " + key);
                        }
                    }
                    return null;
                });
                futures.add(future);
            }
            
            // Wait for all operations to complete
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS); // 30 second timeout
            }
            
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            
            return new TestResult(true, "Concurrency test passed");
            
        } catch (Exception e) {
            return new TestResult(false, "Concurrency test failed: " + e.getMessage());
        }
    }
    
    private boolean compareConfigs(TestConfig original, TestConfig loaded) {
        if (original == null || loaded == null) {
            return false;
        }
        
        // Compare all fields
        return Objects.equals(original.testField, loaded.testField) &&
               Objects.equals(original.testNumber, loaded.testNumber) &&
               Objects.equals(original.testList, loaded.testList) &&
               Objects.equals(original.testMap, loaded.testMap);
    }
    
    // Supporting classes
    @ConfigsFileProperties
    @ConfigsDatabase(database = "mongoconfigs")
    @ConfigsCollection(collection = "test_configs")
    public static class TestConfig {
        public String testField;
        public Integer testNumber;
        public List<String> testList;
        public Map<String, String> testMap;
    }
    
    public static class TestResult {
        public final boolean success;
        public final String message;
        
        public TestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
    
    public static class MigrationTestResult {
        public TestResult dataIntegrityTest;
        public TestResult configAccessTest;
        public TestResult performanceTest;
        public TestResult errorHandlingTest;
        public TestResult concurrencyTest;
        public boolean success;
        public String error;
        
        public boolean allTestsPassed() {
            return dataIntegrityTest != null && dataIntegrityTest.success &&
                   configAccessTest != null && configAccessTest.success &&
                   performanceTest != null && performanceTest.success &&
                   errorHandlingTest != null && errorHandlingTest.success &&
                   concurrencyTest != null && concurrencyTest.success;
        }
    }
    
    public static class TestDataGenerator {
        private final Random random = new Random();
        
        public TestConfig generateTestConfig() {
            TestConfig config = new TestConfig();
            config.testField = "test_value_" + random.nextInt(1000);
            config.testNumber = random.nextInt(100);
            config.testList = Arrays.asList("item1", "item2", "item3");
            config.testMap = Map.of("key1", "value1", "key2", "value2");
            return config;
        }
    }
}
```

---

*Next: Learn about [[Help & FAQ]] for common questions and troubleshooting.*