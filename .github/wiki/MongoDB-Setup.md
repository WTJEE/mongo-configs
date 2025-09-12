# MongoDB Setup

> **Complete MongoDB configuration guide for production deployments**

## MongoDB Installation

### Windows Installation

#### Using Chocolatey
```powershell
# Install Chocolatey (if not already installed)
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://chocolatey.org/install.ps1'))

# Install MongoDB
choco install mongodb

# Start MongoDB service
net start MongoDB
```

#### Manual Installation
1. Download MongoDB Community Server from [mongodb.com](https://www.mongodb.com/try/download/community)
2. Run the installer
3. Add MongoDB to PATH environment variable
4. Create data directory: `mkdir C:\data\db`
5. Start MongoDB: `mongod`

### Linux Installation (Ubuntu/Debian)

```bash
# Import MongoDB public GPG key
wget -qO - https://www.mongodb.org/static/pgp/server-7.0.asc | sudo apt-key add -

# Add MongoDB repository
echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/7.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-7.0.list

# Update package list
sudo apt-get update

# Install MongoDB
sudo apt-get install -y mongodb-org

# Start MongoDB
sudo systemctl start mongod
sudo systemctl enable mongod

# Verify installation
mongod --version
```

### Docker Installation

```bash
# Create Docker network
docker network create minecraft-network

# Run MongoDB with persistent storage
docker run -d \
  --name mongodb \
  --network minecraft-network \
  -p 27017:27017 \
  -v mongodb_data:/data/db \
  -v mongodb_config:/data/configdb \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=password \
  mongo:7.0 \
  --auth

# Wait for MongoDB to start
sleep 10

# Initialize replica set
docker exec -it mongodb mongo -u admin -p password --eval "
rs.initiate({
  _id: 'rs0',
  members: [{
    _id: 0,
    host: 'mongodb:27017'
  }]
})
"
```

## Replica Set Configuration

### Why Replica Sets?

Replica sets provide:
- **High Availability**: Automatic failover
- **Data Redundancy**: Multiple copies of data
- **Read Scaling**: Distribute read operations
- **Change Streams**: Real-time data synchronization

### Single Node Replica Set

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

### Multi-Node Replica Set

#### Primary Node Configuration
```javascript
// On primary node (mongodb1)
rs.initiate({
  _id: "rs0",
  members: [
    {
      _id: 0,
      host: "mongodb1:27017"
    },
    {
      _id: 1,
      host: "mongodb2:27017"
    },
    {
      _id: 2,
      host: "mongodb3:27017"
    }
  ]
})
```

#### Secondary Nodes Configuration
```javascript
// On secondary nodes (mongodb2, mongodb3)
// No additional configuration needed - they will join automatically
```

### Docker Compose Multi-Node Setup

```yaml
version: '3.8'
services:
  mongodb1:
    image: mongo:7.0
    container_name: mongodb1
    networks:
      - minecraft-network
    ports:
      - "27017:27017"
    volumes:
      - mongodb1_data:/data/db
      - mongodb1_config:/data/configdb
    command: --replSet rs0 --bind_ip_all
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 3

  mongodb2:
    image: mongo:7.0
    container_name: mongodb2
    networks:
      - minecraft-network
    ports:
      - "27018:27017"
    volumes:
      - mongodb2_data:/data/db
      - mongodb2_config:/data/configdb
    command: --replSet rs0 --bind_ip_all
    depends_on:
      - mongodb1

  mongodb3:
    image: mongo:7.0
    container_name: mongodb3
    networks:
      - minecraft-network
    ports:
      - "27019:27017"
    volumes:
      - mongodb3_data:/data/db
      - mongodb3_config:/data/configdb
    command: --replSet rs0 --bind_ip_all
    depends_on:
      - mongodb1

volumes:
  mongodb1_data:
  mongodb1_config:
  mongodb2_data:
  mongodb2_config:
  mongodb3_data:
  mongodb3_config:

networks:
  minecraft-network:
    driver: bridge
```

## Database Configuration

### Create Application Database

```javascript
// Connect to MongoDB
mongo

// Switch to admin database
use admin

// Create application user
db.createUser({
  user: "minecraft_app",
  pwd: "secure_password_123",
  roles: [
    {
      role: "readWrite",
      db: "minecraft"
    },
    {
      role: "readWrite",
      db: "minecraft_configs"
    },
    {
      role: "read",
      db: "admin"
    }
  ]
})

// Switch to application database
use minecraft

// Create collections with initial data
db.createCollection("configs")
db.createCollection("messages")
db.createCollection("player_data")
db.createCollection("server_registry")

// Create indexes
db.configs.createIndex({ "_id": 1 })
db.messages.createIndex({ "language": 1 })
db.player_data.createIndex({ "playerId": 1 }, { unique: true })
db.server_registry.createIndex({ "serverId": 1 }, { unique: true })
```

### Connection String Formats

#### Basic Connection
```
mongodb://username:password@localhost:27017/minecraft
```

#### Replica Set Connection
```
mongodb://username:password@localhost:27017,mongodb2:27018,mongodb3:27019/minecraft?replicaSet=rs0
```

#### Advanced Connection
```
mongodb://username:password@localhost:27017/minecraft?replicaSet=rs0&connectTimeoutMS=10000&socketTimeoutMS=45000&maxPoolSize=20&minPoolSize=5
```

## Security Configuration

### Authentication Setup

```javascript
// Enable authentication
use admin

// Create admin user
db.createUser({
  user: "admin",
  pwd: "admin_password",
  roles: ["userAdminAnyDatabase", "dbAdminAnyDatabase", "readWriteAnyDatabase"]
})

// Create application user
db.createUser({
  user: "minecraft_app",
  pwd: "app_password",
  roles: [
    { role: "readWrite", db: "minecraft" },
    { role: "readWrite", db: "minecraft_configs" }
  ]
})
```

### TLS/SSL Configuration

#### Generate SSL Certificates
```bash
# Create CA private key
openssl genrsa -out ca.key 4096

# Create CA certificate
openssl req -new -x509 -days 365 -key ca.key -sha256 -out ca.crt

# Create server private key
openssl genrsa -out server.key 4096

# Create certificate signing request
openssl req -subj "/CN=localhost" -new -key server.key -out server.csr

# Create server certificate
openssl x509 -req -days 365 -in server.csr -CA ca.crt -CAkey ca.key -out server.crt

# Create PEM file
cat server.crt server.key > server.pem
```

#### MongoDB SSL Configuration
```yaml
# mongod.conf
net:
  tls:
    mode: requireTLS
    certificateKeyFile: /etc/ssl/mongodb/server.pem
    CAFile: /etc/ssl/mongodb/ca.crt

security:
  authorization: enabled
```

#### Connection with SSL
```
mongodb://username:password@localhost:27017/minecraft?ssl=true&ssl_ca_certs=/path/to/ca.crt
```

## Performance Optimization

### Index Creation

```javascript
// Switch to minecraft database
use minecraft

// Config collection indexes
db.configs.createIndex({ "collection": 1 })
db.configs.createIndex({ "lastModified": 1 })

// Message collection indexes
db.messages.createIndex({ "language": 1 })
db.messages.createIndex({ "key": 1 })
db.messages.createIndex({ "language": 1, "key": 1 })

// Player data indexes
db.player_data.createIndex({ "playerId": 1 }, { unique: true })
db.player_data.createIndex({ "lastSeen": 1 })
db.player_data.createIndex({ "level": 1 })

// Server registry indexes
db.server_registry.createIndex({ "serverId": 1 }, { unique: true })
db.server_registry.createIndex({ "region": 1 })
db.server_registry.createIndex({ "online": 1 })
```

### Connection Pool Configuration

```javascript
// Connection pool settings
const connectionOptions = {
  maxPoolSize: 20,           // Maximum connections
  minPoolSize: 5,            // Minimum connections
  maxIdleTimeMS: 30000,      // Close connections after 30s idle
  serverSelectionTimeoutMS: 5000,  // Server selection timeout
  socketTimeoutMS: 45000,    // Socket timeout
  connectTimeoutMS: 10000,   // Connection timeout
  bufferMaxEntries: 0,       // Disable buffering
  bufferCommands: false      // Disable command buffering
};
```

### WiredTiger Storage Engine Tuning

```yaml
# mongod.conf
storage:
  wiredTiger:
    engineConfig:
      cacheSizeGB: 2          # Cache size (50% of RAM by default)
      journalCompressor: snappy
    collectionConfig:
      blockCompressor: snappy
    indexConfig:
      prefixCompression: true

operationProfiling:
  mode: slowOp
  slowOpThresholdMs: 100
```

## Monitoring and Maintenance

### Enable Profiling

```javascript
// Enable database profiling
use minecraft
db.setProfilingLevel(2, { slowms: 100 })

// View slow queries
db.system.profile.find().sort({ ts: -1 }).limit(5)
```

### Backup Strategy

#### Manual Backup
```bash
# Create backup directory
mkdir -p /backup/mongodb

# Perform backup
mongodump \
  --db minecraft \
  --out /backup/mongodb/$(date +%Y%m%d_%H%M%S) \
  --username minecraft_app \
  --password app_password
```

#### Automated Backup Script
```bash
#!/bin/bash
BACKUP_DIR="/backup/mongodb"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_NAME="minecraft_$DATE"

# Create backup
mongodump \
  --db minecraft \
  --out $BACKUP_DIR/$BACKUP_NAME \
  --username minecraft_app \
  --password app_password

# Compress backup
tar -czf $BACKUP_DIR/$BACKUP_NAME.tar.gz -C $BACKUP_DIR $BACKUP_NAME

# Remove uncompressed backup
rm -rf $BACKUP_DIR/$BACKUP_NAME

# Keep only last 7 days
find $BACKUP_DIR -name "*.tar.gz" -mtime +7 -delete

echo "Backup completed: $BACKUP_NAME.tar.gz"
```

### Health Check Script

```bash
#!/bin/bash
# MongoDB Health Check

# Check if MongoDB is running
if ! pgrep -x "mongod" > /dev/null; then
    echo "❌ MongoDB is not running"
    exit 1
fi

# Check MongoDB connectivity
if ! mongo --eval "db.stats()" > /dev/null 2>&1; then
    echo "❌ Cannot connect to MongoDB"
    exit 1
fi

# Check replica set status
REPL_STATUS=$(mongo --eval "rs.status().ok" --quiet)
if [ "$REPL_STATUS" != "1" ]; then
    echo "❌ Replica set is not healthy"
    exit 1
fi

# Check database size
DB_SIZE=$(mongo minecraft --eval "db.stats().dataSize" --quiet)
if [ "$DB_SIZE" -gt 1073741824 ]; then # 1GB
    echo "⚠️  Database size is large: $(($DB_SIZE / 1024 / 1024))MB"
fi

echo "✅ MongoDB is healthy"
```

## Troubleshooting

### Common Issues

#### Connection Refused
```bash
# Check if MongoDB is running
sudo systemctl status mongod

# Check MongoDB logs
sudo tail -f /var/log/mongodb/mongod.log

# Check port
netstat -tlnp | grep 27017

# Try connecting locally
mongo --eval "db.stats()"
```

#### Authentication Failed
```bash
# Check user exists
mongo admin --eval "db.getUsers()"

# Verify user roles
mongo admin --eval "db.getUser('minecraft_app')"

# Test authentication
mongo -u minecraft_app -p --authenticationDatabase minecraft
```

#### Replica Set Issues
```bash
# Check replica set status
mongo --eval "rs.status()"

# Check replica set configuration
mongo --eval "rs.conf()"

# Reinitialize if needed
mongo --eval "rs.reconfig(rs.conf())"
```

#### Performance Issues
```bash
# Check current operations
mongo --eval "db.currentOp()"

# Check slow queries
mongo --eval "db.system.profile.find().sort({ ts: -1 }).limit(5)"

# Check indexes
mongo minecraft --eval "db.configs.getIndexes()"
```

---

*MongoDB configured? Check out [[Connection Configuration]] for application connection settings.*