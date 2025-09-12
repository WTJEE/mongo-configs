# Connection Configuration

> **Advanced MongoDB connection settings and optimization**

## Connection String Formats

### Basic Connection String

```
mongodb://username:password@hostname:port/database
```

**Example:**
```
mongodb://minecraft:password@localhost:27017/minecraft
```

### Replica Set Connection

```
mongodb://username:password@host1:port1,host2:port2,host3:port3/database?replicaSet=rs0
```

**Example:**
```
mongodb://minecraft:password@mongodb1:27017,mongodb2:27018,mongodb3:27019/minecraft?replicaSet=rs0
```

### Advanced Connection String

```
mongodb://username:password@hostname:port/database?option1=value1&option2=value2
```

**Example:**
```
mongodb://minecraft:password@localhost:27017/minecraft?replicaSet=rs0&connectTimeoutMS=10000&socketTimeoutMS=45000&maxPoolSize=20
```

## Connection Options

### Authentication Options

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `authSource` | Database to authenticate against | `admin` | `authSource=minecraft` |
| `authMechanism` | Authentication mechanism | `SCRAM-SHA-256` | `authMechanism=SCRAM-SHA-1` |
| `ssl` | Enable SSL/TLS | `false` | `ssl=true` |
| `ssl_ca_certs` | Path to CA certificate file | - | `ssl_ca_certs=/path/to/ca.crt` |
| `tlsCertificateKeyFile` | Path to client certificate | - | `tlsCertificateKeyFile=/path/to/client.pem` |

### Connection Pool Options

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `maxPoolSize` | Maximum connections in pool | `100` | `maxPoolSize=20` |
| `minPoolSize` | Minimum connections in pool | `0` | `minPoolSize=5` |
| `maxIdleTimeMS` | Max time connection can be idle | `0` (no limit) | `maxIdleTimeMS=30000` |
| `waitQueueTimeoutMS` | Max time to wait for connection | `0` (no limit) | `waitQueueTimeoutMS=5000` |

### Timeout Options

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `connectTimeoutMS` | Connection timeout | `10000` | `connectTimeoutMS=15000` |
| `socketTimeoutMS` | Socket timeout | `0` (no timeout) | `socketTimeoutMS=45000` |
| `serverSelectionTimeoutMS` | Server selection timeout | `30000` | `serverSelectionTimeoutMS=5000` |
| `maxTimeMS` | Max time for operations | `0` (no limit) | `maxTimeMS=30000` |

### Replica Set Options

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `replicaSet` | Replica set name | - | `replicaSet=rs0` |
| `readPreference` | Read preference | `primary` | `readPreference=secondaryPreferred` |
| `readConcern` | Read concern level | `local` | `readConcern=majority` |
| `writeConcern` | Write concern level | `1` | `writeConcern=majority` |

## Configuration Examples

### Development Configuration

```yaml
# config.yml
mongodb-configs:
  connection-string: "mongodb://localhost:27017/minecraft"
  database: "minecraft"
  cache-enabled: true
  cache-size: 500
  change-streams-enabled: false
```

### Production Configuration

```yaml
# config.yml
mongodb-configs:
  connection-string: "mongodb://minecraft_app:secure_password@mongodb1:27017,mongodb2:27018,mongodb3:27019/minecraft?replicaSet=rs0&connectTimeoutMS=10000&socketTimeoutMS=45000&maxPoolSize=20&minPoolSize=5&readPreference=secondaryPreferred"
  database: "minecraft"
  cache-enabled: true
  cache-size: 2000
  cache-ttl: 600
  change-streams-enabled: true
  max-await-time: 1000
  batch-size: 100
```

### Multi-Region Configuration

```yaml
# config.yml
mongodb-configs:
  connection-string: "mongodb://app:password@us-east-1:27017,eu-west-1:27017,asia-pacific-1:27017/minecraft?replicaSet=global-rs&readPreference=nearest&connectTimeoutMS=15000&socketTimeoutMS=60000"
  database: "minecraft"
  cache-enabled: true
  cache-size: 5000
  change-streams-enabled: true
```

## Connection Pool Management

### Pool Configuration

```java
public class MongoConnectionManager {

    private static final int MAX_POOL_SIZE = 20;
    private static final int MIN_POOL_SIZE = 5;
    private static final int MAX_IDLE_TIME_MS = 30000;
    private static final int CONNECTION_TIMEOUT_MS = 10000;

    private MongoClient mongoClient;

    public void initialize() {
        ConnectionString connectionString = new ConnectionString(
            "mongodb://username:password@localhost:27017/minecraft?replicaSet=rs0"
        );

        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .applyToConnectionPoolSettings(builder ->
                builder.maxSize(MAX_POOL_SIZE)
                       .minSize(MIN_POOL_SIZE)
                       .maxConnectionIdleTime(MAX_IDLE_TIME_MS, TimeUnit.MILLISECONDS)
                       .maxConnectionLifeTime(5, TimeUnit.MINUTES)
                       .maxWaitTime(5000, TimeUnit.MILLISECONDS)
            )
            .applyToSocketSettings(builder ->
                builder.connectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                       .readTimeout(45000, TimeUnit.MILLISECONDS)
            )
            .applyToServerSettings(builder ->
                builder.heartbeatFrequency(10000, TimeUnit.MILLISECONDS)
                       .minHeartbeatFrequency(500, TimeUnit.MILLISECONDS)
            )
            .build();

        mongoClient = MongoClients.create(settings);
    }

    public void shutdown() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
```

### Connection Monitoring

```java
public class ConnectionMonitor {

    private final MongoClient mongoClient;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ConnectionMonitor(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
        startMonitoring();
    }

    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(this::checkConnections, 0, 30, TimeUnit.SECONDS);
    }

    private void checkConnections() {
        try {
            // Get connection pool statistics
            MongoDatabase adminDb = mongoClient.getDatabase("admin");
            Document serverStatus = adminDb.runCommand(new Document("serverStatus", 1));

            Document connections = (Document) serverStatus.get("connections");
            int available = connections.getInteger("available");
            int current = connections.getInteger("current");
            int totalCreated = connections.getInteger("totalCreated");

            getLogger().info(String.format(
                "MongoDB Connections - Available: %d, Current: %d, Total Created: %d",
                available, current, totalCreated
            ));

            // Alert if connection pool is near capacity
            if (current > 15) { // 75% of max pool size (20)
                getLogger().warning("Connection pool usage is high: " + current + "/20");
            }

        } catch (Exception e) {
            getLogger().error("Failed to check connection statistics", e);
        }
    }

    private Logger getLogger() {
        return Logger.getLogger("ConnectionMonitor");
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
```

## SSL/TLS Configuration

### Client SSL Configuration

```java
public class SSLMongoClient {

    public MongoClient createSSLClient() throws Exception {
        // Load trust store
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream("/path/to/truststore.jks")) {
            trustStore.load(fis, "truststore_password".toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Load key store (client certificate)
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream("/path/to/keystore.jks")) {
            keyStore.load(fis, "keystore_password".toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "key_password".toCharArray());

        // Create SSL context
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        // Configure MongoDB client
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(
                "mongodb://username:password@localhost:27017/minecraft?ssl=true"
            ))
            .applyToSslSettings(builder ->
                builder.enabled(true)
                       .context(sslContext)
                       .invalidHostNameAllowed(false)
            )
            .build();

        return MongoClients.create(settings);
    }
}
```

### Certificate Generation

```bash
# Generate CA private key
openssl genrsa -out ca.key 4096

# Generate CA certificate
openssl req -new -x509 -days 365 -key ca.key -out ca.crt -subj "/CN=MongoDB-CA"

# Generate client private key
openssl genrsa -out client.key 4096

# Generate certificate signing request
openssl req -new -key client.key -out client.csr -subj "/CN=minecraft-client"

# Sign client certificate
openssl x509 -req -days 365 -in client.csr -CA ca.crt -CAkey ca.key -out client.crt

# Create PKCS12 keystore
openssl pkcs12 -export -in client.crt -inkey client.key -out client.p12 -name minecraft-client

# Convert to JKS format
keytool -importkeystore -srckeystore client.p12 -srcstoretype PKCS12 -destkeystore client.jks -deststoretype JKS
```

## Connection Testing

### Connection Test Utility

```java
public class ConnectionTester {

    public static boolean testConnection(String connectionString) {
        try {
            MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .applyToSocketSettings(builder ->
                    builder.connectTimeout(5000, TimeUnit.MILLISECONDS)
                )
                .build();

            try (MongoClient client = MongoClients.create(settings)) {
                MongoDatabase database = client.getDatabase("admin");
                Document ping = database.runCommand(new Document("ping", 1));

                return ping.getDouble("ok") == 1.0;
            }

        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }

    public static void testReplicaSet(String connectionString) {
        try {
            MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .build();

            try (MongoClient client = MongoClients.create(settings)) {
                MongoDatabase database = client.getDatabase("admin");
                Document replStatus = database.runCommand(new Document("replSetGetStatus", 1));

                System.out.println("Replica Set Status: " + replStatus.toJson());

                @SuppressWarnings("unchecked")
                List<Document> members = (List<Document>) replStatus.get("members");

                for (Document member : members) {
                    String name = member.getString("name");
                    String stateStr = member.getString("stateStr");
                    System.out.println("Member: " + name + " - State: " + stateStr);
                }
            }

        } catch (Exception e) {
            System.err.println("Replica set test failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String connectionString = "mongodb://username:password@localhost:27017/minecraft?replicaSet=rs0";

        System.out.println("Testing basic connection...");
        boolean basicTest = testConnection(connectionString);
        System.out.println("Basic connection: " + (basicTest ? "SUCCESS" : "FAILED"));

        System.out.println("\nTesting replica set...");
        testReplicaSet(connectionString);
    }
}
```

### Performance Tuning

#### Optimal Settings for Different Workloads

```yaml
# High-throughput configuration
mongodb-configs:
  connection-string: "mongodb://app:password@host:27017/db?maxPoolSize=50&minPoolSize=10&maxIdleTimeMS=60000&socketTimeoutMS=60000"
  cache-enabled: true
  cache-size: 5000
  change-streams-enabled: true

# Low-latency configuration
mongodb-configs:
  connection-string: "mongodb://app:password@host:27017/db?maxPoolSize=10&minPoolSize=2&connectTimeoutMS=5000&socketTimeoutMS=15000"
  cache-enabled: true
  cache-size: 1000
  change-streams-enabled: false

# High-availability configuration
mongodb-configs:
  connection-string: "mongodb://app:password@host1:27017,host2:27018,host3:27019/db?replicaSet=rs0&readPreference=secondaryPreferred&serverSelectionTimeoutMS=5000"
  cache-enabled: true
  cache-size: 2000
  change-streams-enabled: true
```

---

*Connection configured? Check out [[Change Streams]] for real-time synchronization setup.*