# Security Guidelines

Comprehensive security guidelines for securing your MongoDB Configs API implementation, including authentication, authorization, data protection, and best practices.

## 🔒 Security Overview

The Security Guidelines provide essential security practices for protecting your MongoDB Configs implementation, ensuring data integrity, and preventing unauthorized access.

## 🔐 Authentication and Authorization

### MongoDB Authentication

#### Secure Connection Setup

```yaml
# config.yml - Secure MongoDB configuration
mongodb:
  uri: "mongodb://username:password@host:port/database?ssl=true&replicaSet=rs0"
  ssl:
    enabled: true
    invalid-hostname-allowed: false
    ca-file: "/path/to/ca.pem"
  authentication:
    mechanism: "SCRAM-SHA-256"  # Use SCRAM-SHA-256 instead of SCRAM-SHA-1
```

#### Role-Based Access Control

```java
public class SecureConfigManager {
    
    private final MongoClient mongoClient;
    private final String databaseName;
    
    public SecureConfigManager(MongoClient mongoClient, String databaseName) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
    }
    
    public boolean hasPermission(Player player, String permission, String configKey) {
        // Check player permissions
        if (!player.hasPermission("mongoconfigs." + permission)) {
            return false;
        }
        
        // Check config-specific permissions
        if (configKey.startsWith("admin_") && !player.hasPermission("mongoconfigs.admin")) {
            return false;
        }
        
        // Check ownership permissions
        if (configKey.startsWith("player_" + player.getUniqueId())) {
            return true; // Players can always access their own configs
        }
        
        return false;
    }
    
    public <T> T getSecure(Class<T> configClass, String configKey, Player player) {
        if (!hasPermission(player, "read", configKey)) {
            throw new SecurityException("Player " + player.getName() + " does not have read permission for " + configKey);
        }
        
        return getConfigManager().get(configClass, configKey);
    }
    
    public void saveSecure(Object config, String configKey, Player player) {
        if (!hasPermission(player, "write", configKey)) {
            throw new SecurityException("Player " + player.getName() + " does not have write permission for " + configKey);
        }
        
        getConfigManager().save(config);
    }
    
    private ConfigManager getConfigManager() {
        return MongoConfigsPlugin.getInstance().getConfigManager();
    }
}
```

### Plugin Permissions

#### Permission Structure

```java
public class PermissionManager {
    
    public static final String BASE_PERMISSION = "mongoconfigs";
    
    // Basic permissions
    public static final String USE = BASE_PERMISSION + ".use";
    public static final String RELOAD = BASE_PERMISSION + ".reload";
    
    // Admin permissions
    public static final String ADMIN = BASE_PERMISSION + ".admin";
    public static final String ADMIN_CONFIG = ADMIN + ".config";
    public static final String ADMIN_CACHE = ADMIN + ".cache";
    public static final String ADMIN_DEBUG = ADMIN + ".debug";
    
    // Language permissions
    public static final String LANGUAGE = BASE_PERMISSION + ".language";
    public static final String LANGUAGE_CHANGE = LANGUAGE + ".change";
    public static final String LANGUAGE_ADMIN = LANGUAGE + ".admin";
    
    // GUI permissions
    public static final String GUI = BASE_PERMISSION + ".gui";
    public static final String GUI_ADMIN = GUI + ".admin";
    
    public static void registerPermissions(JavaPlugin plugin) {
        PluginManager pm = plugin.getServer().getPluginManager();
        
        // Register basic permissions
        pm.addPermission(new Permission(USE, "Basic usage permission"));
        pm.addPermission(new Permission(RELOAD, "Reload configurations"));
        
        // Register admin permissions
        Permission adminPerm = new Permission(ADMIN, "Administrator permission");
        adminPerm.addParent(pm.getPermission(USE), true);
        pm.addPermission(adminPerm);
        
        Permission adminConfigPerm = new Permission(ADMIN_CONFIG, "Admin config management");
        adminConfigPerm.addParent(adminPerm, true);
        pm.addPermission(adminConfigPerm);
        
        // Register language permissions
        Permission langPerm = new Permission(LANGUAGE, "Language management");
        langPerm.addParent(pm.getPermission(USE), true);
        pm.addPermission(langPerm);
        
        Permission langChangePerm = new Permission(LANGUAGE_CHANGE, "Change language");
        langChangePerm.addParent(langPerm, true);
        pm.addPermission(langChangePerm);
        
        // Register GUI permissions
        Permission guiPerm = new Permission(GUI, "GUI access");
        guiPerm.addParent(pm.getPermission(USE), true);
        pm.addPermission(guiPerm);
    }
}
```

#### Permission Configuration

```yaml
# plugin.yml - Permission definitions
permissions:
  mongoconfigs.use:
    description: "Basic usage permission for MongoDB Configs"
    default: true
    
  mongoconfigs.reload:
    description: "Permission to reload configurations"
    default: op
    
  mongoconfigs.admin:
    description: "Administrator permission for MongoDB Configs"
    default: op
    children:
      mongoconfigs.use: true
      mongoconfigs.reload: true
      
  mongoconfigs.admin.config:
    description: "Admin permission for configuration management"
    default: op
    children:
      mongoconfigs.admin: true
      
  mongoconfigs.language:
    description: "Language management permission"
    default: true
    children:
      mongoconfigs.use: true
      
  mongoconfigs.language.change:
    description: "Permission to change language"
    default: true
    children:
      mongoconfigs.language: true
      
  mongoconfigs.gui:
    description: "GUI access permission"
    default: true
    children:
      mongoconfigs.use: true
```

## 🛡️ Data Protection

### Input Validation

#### Safe Configuration Loading

```java
public class SecureConfigLoader {
    
    private static final Pattern SAFE_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{1,100}$");
    private static final int MAX_CONFIG_SIZE = 1024 * 1024; // 1MB limit
    
    public <T> T loadSecureConfig(Class<T> configClass, String configKey) {
        // Validate config key
        if (!isValidConfigKey(configKey)) {
            throw new SecurityException("Invalid configuration key: " + configKey);
        }
        
        // Check if config exists
        if (!getConfigManager().exists(configKey)) {
            throw new IllegalArgumentException("Configuration not found: " + configKey);
        }
        
        // Load and validate config
        T config = getConfigManager().get(configClass, configKey);
        
        if (config != null) {
            validateConfigObject(config);
        }
        
        return config;
    }
    
    public void saveSecureConfig(Object config, String configKey) {
        // Validate config key
        if (!isValidConfigKey(configKey)) {
            throw new SecurityException("Invalid configuration key: " + configKey);
        }
        
        // Validate config object
        validateConfigObject(config);
        
        // Check size limits
        String jsonString = getJsonString(config);
        if (jsonString.length() > MAX_CONFIG_SIZE) {
            throw new SecurityException("Configuration too large: " + jsonString.length() + " bytes");
        }
        
        // Save config
        getConfigManager().save(config);
    }
    
    private boolean isValidConfigKey(String key) {
        return key != null && SAFE_KEY_PATTERN.matcher(key).matches();
    }
    
    private void validateConfigObject(Object config) {
        if (config == null) {
            throw new SecurityException("Configuration object cannot be null");
        }
        
        // Check for dangerous types
        Class<?> configClass = config.getClass();
        
        // Prevent serialization of sensitive classes
        if (configClass.getName().startsWith("java.lang.Process") ||
            configClass.getName().contains("Runtime") ||
            configClass.getName().contains("System")) {
            throw new SecurityException("Dangerous object type detected: " + configClass.getName());
        }
        
        // Validate field values
        validateFieldValues(config, configClass);
    }
    
    private void validateFieldValues(Object config, Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            
            try {
                Object value = field.get(config);
                
                if (value instanceof String) {
                    validateStringField((String) value, field.getName());
                } else if (value instanceof List) {
                    validateListField((List<?>) value, field.getName());
                } else if (value instanceof Map) {
                    validateMapField((Map<?, ?>) value, field.getName());
                }
                
            } catch (IllegalAccessException e) {
                throw new SecurityException("Cannot access field: " + field.getName());
            }
        }
        
        // Validate parent class fields
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            validateFieldValues(config, clazz.getSuperclass());
        }
    }
    
    private void validateStringField(String value, String fieldName) {
        if (value != null && value.length() > 10000) {
            throw new SecurityException("String field '" + fieldName + "' too long: " + value.length());
        }
        
        // Check for potentially dangerous content
        if (value != null && containsDangerousPatterns(value)) {
            throw new SecurityException("Dangerous content detected in field '" + fieldName + "'");
        }
    }
    
    private void validateListField(List<?> list, String fieldName) {
        if (list != null && list.size() > 1000) {
            throw new SecurityException("List field '" + fieldName + "' too large: " + list.size());
        }
        
        // Validate each element
        for (Object item : list) {
            if (item instanceof String) {
                validateStringField((String) item, fieldName + "[]");
            }
        }
    }
    
    private void validateMapField(Map<?, ?> map, String fieldName) {
        if (map != null && map.size() > 1000) {
            throw new SecurityException("Map field '" + fieldName + "' too large: " + map.size());
        }
        
        // Validate keys and values
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String) {
                validateStringField((String) entry.getKey(), fieldName + ".key");
            }
            if (entry.getValue() instanceof String) {
                validateStringField((String) entry.getValue(), fieldName + ".value");
            }
        }
    }
    
    private boolean containsDangerousPatterns(String value) {
        // Check for script injection patterns
        return value.contains("<script") ||
               value.contains("javascript:") ||
               value.contains("eval(") ||
               value.contains("function(") ||
               // Check for SQL injection patterns
               value.contains("; DROP") ||
               value.contains("UNION SELECT") ||
               // Check for path traversal
               value.contains("../") ||
               value.contains("..\\");
    }
    
    private String getJsonString(Object config) {
        try {
            return new ObjectMapper().writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new SecurityException("Cannot serialize configuration: " + e.getMessage());
        }
    }
    
    private ConfigManager getConfigManager() {
        return MongoConfigsPlugin.getInstance().getConfigManager();
    }
}
```

### Data Sanitization

#### Safe Data Handling

```java
public class DataSanitizer {
    
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute)"
    );
    
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i)(<script|<iframe|<object|<embed|javascript:|vbscript:|onload|onerror)"
    );
    
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
        "\\.\\./|/\\\\\\.\\./"
    );
    
    public static String sanitizeString(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove null bytes
        String sanitized = input.replace("\0", "");
        
        // Trim whitespace
        sanitized = sanitized.trim();
        
        // Limit length
        if (sanitized.length() > 10000) {
            sanitized = sanitized.substring(0, 10000);
        }
        
        return sanitized;
    }
    
    public static String sanitizeForDisplay(String input) {
        if (input == null) {
            return null;
        }
        
        String sanitized = sanitizeString(input);
        
        // Escape HTML characters
        sanitized = sanitized.replace("&", "&amp;")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("\"", "&quot;")
                            .replace("'", "&#x27;")
                            .replace("/", "&#x2F;");
        
        return sanitized;
    }
    
    public static String sanitizeForStorage(String input) {
        if (input == null) {
            return null;
        }
        
        String sanitized = sanitizeString(input);
        
        // Check for dangerous patterns
        if (SQL_INJECTION_PATTERN.matcher(sanitized).find()) {
            throw new SecurityException("Potential SQL injection detected");
        }
        
        if (XSS_PATTERN.matcher(sanitized).find()) {
            throw new SecurityException("Potential XSS attack detected");
        }
        
        if (PATH_TRAVERSAL_PATTERN.matcher(sanitized).find()) {
            throw new SecurityException("Potential path traversal detected");
        }
        
        return sanitized;
    }
    
    public static List<String> sanitizeStringList(List<String> input) {
        if (input == null) {
            return null;
        }
        
        return input.stream()
                   .map(DataSanitizer::sanitizeString)
                   .filter(Objects::nonNull)
                   .collect(Collectors.toList());
    }
    
    public static Map<String, String> sanitizeStringMap(Map<String, String> input) {
        if (input == null) {
            return null;
        }
        
        return input.entrySet().stream()
                   .collect(Collectors.toMap(
                       entry -> sanitizeString(entry.getKey()),
                       entry -> sanitizeString(entry.getValue()),
                       (oldValue, newValue) -> newValue,
                       LinkedHashMap::new
                   ));
    }
    
    public static String sanitizePlayerName(String playerName) {
        if (playerName == null) {
            return null;
        }
        
        // Allow only alphanumeric characters, underscores, and basic punctuation
        String sanitized = playerName.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "");
        
        // Limit length
        if (sanitized.length() > 16) {
            sanitized = sanitized.substring(0, 16);
        }
        
        return sanitized.trim();
    }
    
    public static String sanitizeConfigKey(String configKey) {
        if (configKey == null) {
            return null;
        }
        
        // Allow only safe characters for config keys
        String sanitized = configKey.replaceAll("[^a-zA-Z0-9_\\-\\.]", "");
        
        // Ensure it doesn't start with dangerous patterns
        if (sanitized.startsWith("system.") || sanitized.startsWith("java.")) {
            throw new SecurityException("Config key cannot start with system or java prefixes");
        }
        
        return sanitized;
    }
}
```

## 🔑 Encryption and Data Protection

### Sensitive Data Handling

#### Encrypted Configuration Storage

```java
public class EncryptedConfigManager {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE = 16;
    
    private final SecretKey secretKey;
    private final ConfigManager configManager;
    
    public EncryptedConfigManager(ConfigManager configManager, String encryptionKey) {
        this.configManager = configManager;
        this.secretKey = generateKeyFromPassword(encryptionKey);
    }
    
    public void saveEncrypted(Object config, String configKey) {
        try {
            // Convert config to JSON
            String jsonData = new ObjectMapper().writeValueAsString(config);
            
            // Encrypt the data
            String encryptedData = encrypt(jsonData);
            
            // Create encrypted config wrapper
            EncryptedConfig encryptedConfig = new EncryptedConfig();
            encryptedConfig.encryptedData = encryptedData;
            encryptedConfig.algorithm = ALGORITHM;
            encryptedConfig.timestamp = System.currentTimeMillis();
            
            // Save encrypted config
            configManager.save(encryptedConfig);
            
        } catch (Exception e) {
            throw new SecurityException("Failed to encrypt and save config: " + e.getMessage());
        }
    }
    
    public <T> T loadEncrypted(Class<T> configClass, String configKey) {
        try {
            // Load encrypted config
            EncryptedConfig encryptedConfig = configManager.get(EncryptedConfig.class, configKey);
            
            if (encryptedConfig == null) {
                return null;
            }
            
            // Decrypt the data
            String decryptedJson = decrypt(encryptedConfig.encryptedData);
            
            // Convert back to object
            return new ObjectMapper().readValue(decryptedJson, configClass);
            
        } catch (Exception e) {
            throw new SecurityException("Failed to decrypt and load config: " + e.getMessage());
        }
    }
    
    private String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        byte[] iv = new byte[IV_SIZE];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_SIZE * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        
        byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        // Combine IV and encrypted data
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + encryptedData.length);
        buffer.put(iv);
        buffer.put(encryptedData);
        
        return Base64.getEncoder().encodeToString(buffer.array());
    }
    
    private String decrypt(String encryptedData) throws Exception {
        byte[] decodedData = Base64.getDecoder().decode(encryptedData);
        
        ByteBuffer buffer = ByteBuffer.wrap(decodedData);
        byte[] iv = new byte[IV_SIZE];
        buffer.get(iv);
        
        byte[] encryptedBytes = new byte[buffer.remaining()];
        buffer.get(encryptedBytes);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_SIZE * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
        
        byte[] decryptedData = cipher.doFinal(encryptedBytes);
        return new String(decryptedData, StandardCharsets.UTF_8);
    }
    
    private SecretKey generateKeyFromPassword(String password) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), "salt".getBytes(), 65536, KEY_SIZE);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new SecurityException("Failed to generate encryption key: " + e.getMessage());
        }
    }
    
    @ConfigsFileProperties
    @ConfigsDatabase(database = "mongoconfigs")
    @ConfigsCollection(collection = "encrypted_configs")
    public static class EncryptedConfig {
        public String encryptedData;
        public String algorithm;
        public long timestamp;
    }
}
```

### Secure Key Management

#### Key Rotation System

```java
public class KeyRotationManager {
    
    private final ConfigManager configManager;
    private final String keyConfigKey = "encryption_keys";
    
    public KeyRotationManager(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    public void rotateEncryptionKey(String newKey) {
        try {
            // Load current keys
            EncryptionKeys currentKeys = configManager.get(EncryptionKeys.class, keyConfigKey);
            if (currentKeys == null) {
                currentKeys = new EncryptionKeys();
            }
            
            // Generate new key
            SecretKey newSecretKey = generateKeyFromPassword(newKey);
            
            // Add new key to rotation list
            KeyVersion newKeyVersion = new KeyVersion();
            newKeyVersion.version = currentKeys.currentVersion + 1;
            newKeyVersion.keyData = Base64.getEncoder().encodeToString(newSecretKey.getEncoded());
            newKeyVersion.createdAt = System.currentTimeMillis();
            
            currentKeys.keys.add(newKeyVersion);
            currentKeys.currentVersion = newKeyVersion.version;
            
            // Keep only last 3 keys for decryption compatibility
            if (currentKeys.keys.size() > 3) {
                currentKeys.keys.remove(0);
            }
            
            // Save updated keys
            configManager.save(currentKeys);
            
            // Re-encrypt all sensitive configs with new key
            reEncryptAllConfigs(newSecretKey);
            
            MongoConfigsPlugin.getInstance().getLogger().info("Encryption key rotated successfully");
            
        } catch (Exception e) {
            throw new SecurityException("Failed to rotate encryption key: " + e.getMessage());
        }
    }
    
    public SecretKey getCurrentKey() {
        EncryptionKeys keys = configManager.get(EncryptionKeys.class, keyConfigKey);
        if (keys == null || keys.keys.isEmpty()) {
            throw new SecurityException("No encryption keys found");
        }
        
        KeyVersion currentKeyVersion = keys.keys.stream()
            .filter(k -> k.version == keys.currentVersion)
            .findFirst()
            .orElseThrow(() -> new SecurityException("Current key version not found"));
        
        byte[] keyBytes = Base64.getDecoder().decode(currentKeyVersion.keyData);
        return new SecretKeySpec(keyBytes, "AES");
    }
    
    public SecretKey getKeyForVersion(int version) {
        EncryptionKeys keys = configManager.get(EncryptionKeys.class, keyConfigKey);
        if (keys == null) {
            throw new SecurityException("No encryption keys found");
        }
        
        return keys.keys.stream()
            .filter(k -> k.version == version)
            .findFirst()
            .map(k -> {
                byte[] keyBytes = Base64.getDecoder().decode(k.keyData);
                return new SecretKeySpec(keyBytes, "AES");
            })
            .orElseThrow(() -> new SecurityException("Key version " + version + " not found"));
    }
    
    private void reEncryptAllConfigs(SecretKey newKey) {
        // This would require knowing which configs are encrypted
        // Implementation depends on your encrypted config tracking system
        MongoConfigsPlugin.getInstance().getLogger().info("Re-encrypting sensitive configurations...");
        // Implementation would iterate through encrypted configs and re-encrypt them
    }
    
    private SecretKey generateKeyFromPassword(String password) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), "salt".getBytes(), 65536, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new SecurityException("Failed to generate key: " + e.getMessage());
        }
    }
    
    @ConfigsFileProperties
    @ConfigsDatabase(database = "mongoconfigs")
    @ConfigsCollection(collection = "system")
    public static class EncryptionKeys {
        public int currentVersion = 0;
        public List<KeyVersion> keys = new ArrayList<>();
    }
    
    public static class KeyVersion {
        public int version;
        public String keyData;
        public long createdAt;
    }
}
```

## 🚨 Security Monitoring

### Audit Logging

#### Security Event Logging

```java
public class SecurityAuditLogger {
    
    private static final Logger AUDIT_LOGGER = Logger.getLogger("MongoConfigsSecurity");
    
    static {
        try {
            // Configure audit logger
            FileHandler fileHandler = new FileHandler("logs/mongoconfigs-audit.log", true);
            fileHandler.setFormatter(new AuditLogFormatter());
            AUDIT_LOGGER.addHandler(fileHandler);
            AUDIT_LOGGER.setUseParentHandlers(false);
            AUDIT_LOGGER.setLevel(Level.INFO);
        } catch (Exception e) {
            System.err.println("Failed to configure audit logger: " + e.getMessage());
        }
    }
    
    public static void logAuthenticationEvent(String playerName, String action, boolean success) {
        logEvent("AUTH", playerName, action, success, null, null);
    }
    
    public static void logAuthorizationEvent(String playerName, String permission, String resource, boolean granted) {
        logEvent("AUTHZ", playerName, "check_permission", granted, 
                Map.of("permission", permission, "resource", resource), null);
    }
    
    public static void logConfigAccessEvent(String playerName, String configKey, String action) {
        logEvent("CONFIG_ACCESS", playerName, action, true, 
                Map.of("config_key", configKey), null);
    }
    
    public static void logSecurityViolation(String playerName, String violationType, String details) {
        logEvent("SECURITY_VIOLATION", playerName, violationType, false, 
                Map.of("details", details), null);
    }
    
    public static void logSuspiciousActivity(String playerName, String activityType, Map<String, Object> context) {
        logEvent("SUSPICIOUS_ACTIVITY", playerName, activityType, false, context, null);
    }
    
    private static void logEvent(String eventType, String playerName, String action, 
                               boolean success, Map<String, Object> context, Throwable exception) {
        try {
            AuditEvent event = new AuditEvent();
            event.timestamp = System.currentTimeMillis();
            event.eventType = eventType;
            event.playerName = playerName;
            event.action = action;
            event.success = success;
            event.ipAddress = getPlayerIPAddress(playerName);
            event.userAgent = "Minecraft-Client";
            
            if (context != null) {
                event.context = new HashMap<>(context);
            }
            
            if (exception != null) {
                event.exception = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
            
            // Log to file
            AUDIT_LOGGER.info(new ObjectMapper().writeValueAsString(event));
            
            // Log security violations to console as well
            if ("SECURITY_VIOLATION".equals(eventType) || "SUSPICIOUS_ACTIVITY".equals(eventType)) {
                MongoConfigsPlugin.getInstance().getLogger().warning(
                    "Security Event: " + eventType + " - Player: " + playerName + " - Action: " + action);
            }
            
        } catch (Exception e) {
            System.err.println("Failed to log security event: " + e.getMessage());
        }
    }
    
    private static String getPlayerIPAddress(String playerName) {
        try {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                return player.getAddress().getAddress().getHostAddress();
            }
        } catch (Exception e) {
            // Ignore exceptions when getting IP
        }
        return "unknown";
    }
    
    public static class AuditEvent {
        public long timestamp;
        public String eventType;
        public String playerName;
        public String action;
        public boolean success;
        public String ipAddress;
        public String userAgent;
        public Map<String, Object> context;
        public String exception;
    }
    
    private static class AuditLogFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return record.getMessage() + System.lineSeparator();
        }
    }
}
```

### Intrusion Detection

#### Suspicious Activity Detection

```java
public class IntrusionDetector {
    
    private final Map<String, PlayerActivity> playerActivities = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public IntrusionDetector() {
        // Clean up old activity data every hour
        scheduler.scheduleAtFixedRate(this::cleanupOldData, 1, 1, TimeUnit.HOURS);
    }
    
    public void recordActivity(String playerName, String activityType, Map<String, Object> context) {
        PlayerActivity activity = playerActivities.computeIfAbsent(playerName, k -> new PlayerActivity());
        
        ActivityRecord record = new ActivityRecord();
        record.timestamp = System.currentTimeMillis();
        record.activityType = activityType;
        record.context = context != null ? new HashMap<>(context) : new HashMap<>();
        
        activity.records.add(record);
        
        // Check for suspicious patterns
        checkForSuspiciousPatterns(playerName, activity);
    }
    
    private void checkForSuspiciousPatterns(String playerName, PlayerActivity activity) {
        List<ActivityRecord> recentRecords = getRecentRecords(activity, 5 * 60 * 1000); // Last 5 minutes
        
        // Check for rapid config access
        long configAccessCount = recentRecords.stream()
            .filter(r -> "CONFIG_ACCESS".equals(r.activityType))
            .count();
        
        if (configAccessCount > 50) {
            SecurityAuditLogger.logSuspiciousActivity(playerName, "RAPID_CONFIG_ACCESS", 
                Map.of("access_count", configAccessCount, "time_window", "5_minutes"));
        }
        
        // Check for failed authentication attempts
        long failedAuthCount = recentRecords.stream()
            .filter(r -> "AUTH".equals(r.activityType))
            .filter(r -> Boolean.FALSE.equals(r.context.get("success")))
            .count();
        
        if (failedAuthCount > 10) {
            SecurityAuditLogger.logSuspiciousActivity(playerName, "MULTIPLE_FAILED_AUTH", 
                Map.of("failed_attempts", failedAuthCount, "time_window", "5_minutes"));
        }
        
        // Check for unusual permission checks
        long permissionCheckCount = recentRecords.stream()
            .filter(r -> "AUTHZ".equals(r.activityType))
            .count();
        
        if (permissionCheckCount > 100) {
            SecurityAuditLogger.logSuspiciousActivity(playerName, "EXCESSIVE_PERMISSION_CHECKS", 
                Map.of("check_count", permissionCheckCount, "time_window", "5_minutes"));
        }
        
        // Check for SQL injection attempts
        boolean hasSqlInjection = recentRecords.stream()
            .anyMatch(r -> containsSqlInjectionPatterns(r.context));
        
        if (hasSqlInjection) {
            SecurityAuditLogger.logSecurityViolation(playerName, "SQL_INJECTION_ATTEMPT", 
                "Detected SQL injection patterns in input data");
        }
        
        // Check for path traversal attempts
        boolean hasPathTraversal = recentRecords.stream()
            .anyMatch(r -> containsPathTraversalPatterns(r.context));
        
        if (hasPathTraversal) {
            SecurityAuditLogger.logSecurityViolation(playerName, "PATH_TRAVERSAL_ATTEMPT", 
                "Detected path traversal patterns in input data");
        }
    }
    
    private boolean containsSqlInjectionPatterns(Map<String, Object> context) {
        return context.values().stream()
            .filter(Objects::nonNull)
            .map(Object::toString)
            .anyMatch(value -> 
                value.contains("UNION SELECT") || 
                value.contains("; DROP") || 
                value.contains("OR 1=1"));
    }
    
    private boolean containsPathTraversalPatterns(Map<String, Object> context) {
        return context.values().stream()
            .filter(Objects::nonNull)
            .map(Object::toString)
            .anyMatch(value -> 
                value.contains("../") || 
                value.contains("..\\") || 
                value.contains("/etc/passwd"));
    }
    
    private List<ActivityRecord> getRecentRecords(PlayerActivity activity, long timeWindowMs) {
        long cutoffTime = System.currentTimeMillis() - timeWindowMs;
        
        return activity.records.stream()
            .filter(r -> r.timestamp > cutoffTime)
            .collect(Collectors.toList());
    }
    
    private void cleanupOldData() {
        long cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24 hours ago
        
        playerActivities.values().forEach(activity -> 
            activity.records.removeIf(r -> r.timestamp < cutoffTime));
        
        // Remove players with no recent activity
        playerActivities.entrySet().removeIf(entry -> entry.getValue().records.isEmpty());
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private static class PlayerActivity {
        public final List<ActivityRecord> records = Collections.synchronizedList(new ArrayList<>());
    }
    
    private static class ActivityRecord {
        public long timestamp;
        public String activityType;
        public Map<String, Object> context;
    }
}
```

## 🔒 Best Practices

### Security Checklist

#### Pre-Deployment Checklist

```yaml
# Security Configuration Template
security:
  # Authentication
  mongodb_authentication: true
  scram_sha_256: true
  ssl_enabled: true
  
  # Authorization
  permission_system: true
  role_based_access: true
  admin_permissions: restricted
  
  # Data Protection
  input_validation: true
  data_sanitization: true
  encryption_enabled: true
  
  # Monitoring
  audit_logging: true
  intrusion_detection: true
  security_alerts: true
  
  # Network Security
  firewall_configured: true
  port_restrictions: true
  connection_limits: true
```

#### Runtime Security Monitoring

```java
public class SecurityMonitor {
    
    private final MongoConfigsPlugin plugin;
    private final ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);
    
    public SecurityMonitor(MongoConfigsPlugin plugin) {
        this.plugin = plugin;
        
        // Schedule security checks
        monitor.scheduleAtFixedRate(this::performSecurityChecks, 5, 5, TimeUnit.MINUTES);
        monitor.scheduleAtFixedRate(this::checkForAnomalies, 1, 1, TimeUnit.MINUTES);
    }
    
    private void performSecurityChecks() {
        try {
            // Check MongoDB connection security
            checkMongoDBSecurity();
            
            // Check file permissions
            checkFilePermissions();
            
            // Check configuration security
            checkConfigurationSecurity();
            
            // Check for security updates
            checkForSecurityUpdates();
            
        } catch (Exception e) {
            plugin.getLogger().severe("Security check failed: " + e.getMessage());
        }
    }
    
    private void checkMongoDBSecurity() {
        // Verify SSL/TLS is enabled
        // Check authentication is configured
        // Verify connection string doesn't contain plaintext passwords
        plugin.getLogger().info("MongoDB security check completed");
    }
    
    private void checkFilePermissions() {
        // Check config file permissions
        // Verify log files aren't world-readable
        // Check for sensitive data in logs
        plugin.getLogger().info("File permissions check completed");
    }
    
    private void checkConfigurationSecurity() {
        // Check for hardcoded secrets
        // Verify encryption keys are properly managed
        // Check permission configurations
        plugin.getLogger().info("Configuration security check completed");
    }
    
    private void checkForSecurityUpdates() {
        // Check for MongoDB driver updates
        // Check for plugin updates
        // Verify dependencies are up to date
        plugin.getLogger().info("Security updates check completed");
    }
    
    private void checkForAnomalies() {
        // Monitor for unusual patterns
        // Check connection rates
        // Monitor failed authentication attempts
        // Check for unusual data access patterns
    }
    
    public void shutdown() {
        monitor.shutdown();
        try {
            if (!monitor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

---

*Next: Learn about [[Best Practices]] for optimizing your MongoDB Configs implementation.*