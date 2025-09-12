# Installation

> **Complete setup guide for MongoDB Configs API**

## System Requirements

### Minimum Requirements
- **Java**: 17 or higher
- **MongoDB**: 5.5 or higher
- **Memory**: 512MB RAM (additional for large configurations)
- **Disk**: 100MB free space

### Recommended Requirements
- **Java**: 21 LTS
- **MongoDB**: 7.0+
- **Memory**: 1GB+ RAM
- **CPU**: 2+ cores

## MongoDB Setup

### 1. Install MongoDB

#### Windows (PowerShell)
```powershell
# Using Chocolatey
choco install mongodb

# Or download from official website
# https://www.mongodb.com/try/download/community
```

#### Linux (Ubuntu/Debian)
```bash
# Import MongoDB public GPG key
wget -qO - https://www.mongodb.org/static/pgp/server-7.0.asc | sudo apt-key add -

# Add MongoDB repository
echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/7.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-7.0.list

# Update and install
sudo apt-get update
sudo apt-get install -y mongodb-org

# Start MongoDB
sudo systemctl start mongod
sudo systemctl enable mongod
```

#### Docker
```bash
# Run MongoDB with replica set
docker run -d \
  --name mongodb \
  -p 27017:27017 \
  -v mongodb_data:/data/db \
  mongo:7.0 \
  --replSet rs0

# Initialize replica set
docker exec -it mongodb mongo --eval "rs.initiate()"
```

### 2. Configure Replica Set

Replica sets are required for Change Streams functionality.

```javascript
// Connect to MongoDB shell
mongo

// Initialize replica set
rs.initiate({
  _id: "rs0",
  members: [
    {
      _id: 0,
      host: "localhost:27017"
    }
  ]
})

// Check status
rs.status()
```

### 3. Create Database User

```javascript
// Switch to admin database
use admin

// Create user
db.createUser({
  user: "minecraft",
  pwd: "your_secure_password",
  roles: [
    {
      role: "readWrite",
      db: "minecraft"
    },
    {
      role: "readWrite",
      db: "minecraft_configs"
    }
  ]
})
```

## Plugin Installation

### Method 1: Maven Dependency

Add to your `pom.xml`:

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>my-plugin</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>

    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>

    <dependencies>
        <!-- Spigot API -->
        <dependency>
            <groupId>org.spigotmc</groupId>
            <artifactId>spigot-api</artifactId>
            <version>1.20.4-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>

        <!-- MongoDB Configs API -->
        <dependency>
            <groupId>xyz.wtje.mongoconfigs</groupId>
            <artifactId>mongo-configs-api</artifactId>
            <version>1.0.0</version>
            <scope>compile</scope>
        </dependency>
    </dependencies>
</project>
```

### Method 2: Gradle (Kotlin DSL)

```kotlin
plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.example"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
    implementation("xyz.wtje.mongoconfigs:mongo-configs-api:1.0.0")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("xyz.wtje.mongoconfigs", "com.example.libs.mongoconfigs")
}
```

### Method 3: Manual JAR Download

1. Download the latest JAR from [GitHub Releases](https://github.com/WTJEE/mongo-configs/releases)
2. Add to your project's `lib` folder
3. Add to classpath in your build tool

## Configuration

### Basic Configuration

Create `config.yml` in your plugin's data folder:

```yaml
# MongoDB Configs API Configuration
mongodb-configs:
  # MongoDB connection
  connection-string: "mongodb://minecraft:password@localhost:27017/minecraft?replicaSet=rs0"

  # Database settings
  database: "minecraft"
  config-collection: "configs"
  messages-collection: "messages"

  # Caching
  cache-enabled: true
  cache-size: 1000
  cache-ttl: 300

  # Change Streams
  change-streams-enabled: true
  max-await-time: 1000

  # Language settings
  default-language: "en"
  supported-languages: ["en", "pl", "de", "fr", "es"]
```

### Advanced Configuration

```yaml
mongodb-configs:
  # Connection pool settings
  connection-pool:
    max-size: 20
    min-size: 5
    max-wait-time: 5000
    max-connection-idle-time: 30000

  # SSL/TLS settings
  ssl:
    enabled: false
    trust-store: "/path/to/truststore.jks"
    trust-store-password: "password"

  # Authentication
  authentication:
    mechanism: "SCRAM-SHA-256"
    source: "admin"

  # Performance tuning
  performance:
    batch-size: 100
    buffer-size: 1024
    async-operations: true

  # Monitoring
  monitoring:
    metrics-enabled: true
    health-check-interval: 30
    slow-query-threshold: 1000
```

## Plugin Integration

### Basic Plugin Structure

```java
public class MyPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        try {
            // Initialize MongoDB Configs API
            MongoConfigsAPI.initialize(this);

            // Get managers
            configManager = MongoConfigsAPI.getConfigManager();
            languageManager = MongoConfigsAPI.getLanguageManager();

            // Register commands and listeners
            registerCommands();
            registerListeners();

            getLogger().info("Plugin enabled successfully!");

        } catch (Exception e) {
            getLogger().severe("Failed to initialize MongoDB Configs API: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Cleanup if needed
        getLogger().info("Plugin disabled!");
    }

    private void registerCommands() {
        getCommand("mycommand").setExecutor(new MyCommand());
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new MyListener(), this);
    }
}
```

### Error Handling

```java
@Override
public void onEnable() {
    try {
        MongoConfigsAPI.initialize(this);
        getLogger().info("MongoDB Configs API initialized successfully!");
    } catch (MongoTimeoutException e) {
        getLogger().severe("Cannot connect to MongoDB. Please check your connection string.");
        getLogger().severe("Error: " + e.getMessage());
        getServer().getPluginManager().disablePlugin(this);
    } catch (MongoSecurityException e) {
        getLogger().severe("Authentication failed. Please check your credentials.");
        getLogger().severe("Error: " + e.getMessage());
        getServer().getPluginManager().disablePlugin(this);
    } catch (Exception e) {
        getLogger().severe("Unexpected error during initialization: " + e.getMessage());
        e.printStackTrace();
        getServer().getPluginManager().disablePlugin(this);
    }
}
```

## Verification

### Test Installation

Create a simple test command:

```java
public class TestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("myplugin.test")) {
            sender.sendMessage("§cNo permission!");
            return true;
        }

        try {
            ConfigManager cm = MongoConfigsAPI.getConfigManager();

            // Test configuration loading
            TestConfig config = cm.loadObject(TestConfig.class);
            sender.sendMessage("§aConfiguration loaded: " + config.getServerName());

            // Test message loading
            Messages messages = cm.loadObject(Messages.class);
            sender.sendMessage("§aMessages loaded: " + messages.getWelcome());

            sender.sendMessage("§aAll systems operational!");

        } catch (Exception e) {
            sender.sendMessage("§cTest failed: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }
}
```

### Health Check

```java
public class HealthCheckCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§6=== MongoDB Configs Health Check ===");

        try {
            // Check MongoDB connection
            MongoManager mongoManager = MongoConfigsAPI.getMongoManager();
            Document ping = mongoManager.getDatabase().runCommand(new Document("ping", 1));
            sender.sendMessage("§a✓ MongoDB Connection: OK");

            // Check cache
            ConfigManager cm = MongoConfigsAPI.getConfigManager();
            sender.sendMessage("§a✓ Config Manager: OK");

            // Check language manager
            LanguageManager lm = MongoConfigsAPI.getLanguageManager();
            sender.sendMessage("§a✓ Language Manager: OK");

            // Check collections
            long configCount = mongoManager.getCollection("configs").countDocuments();
            sender.sendMessage("§a✓ Config Documents: " + configCount);

            sender.sendMessage("§a✓ All systems healthy!");

        } catch (Exception e) {
            sender.sendMessage("§c✗ Health check failed: " + e.getMessage());
        }

        return true;
    }
}
```

## Troubleshooting

### Connection Issues

**"Connection refused"**
- Ensure MongoDB is running: `sudo systemctl status mongod`
- Check port: `netstat -tlnp | grep 27017`
- Verify firewall settings

**"Authentication failed"**
- Check username/password in connection string
- Verify user exists: `db.getUsers()`
- Check user roles and permissions

### Performance Issues

**Slow loading times**
- Enable caching in config.yml
- Increase cache size
- Check MongoDB indexes

**High memory usage**
- Reduce cache size
- Enable cache TTL
- Monitor with `/timings` command

### Common Errors

**"Class not found"**
```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>xyz.wtje.mongoconfigs</groupId>
    <artifactId>mongo-configs-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

**"Annotation not found"**
```java
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsDatabase;
```

---

*Installation complete? Try the [[Quick Start]] guide to create your first configuration!*