# Installation

This guide covers the minimum steps required to add MongoConfigs to your project and connect it to a MongoDB instance.

## Requirements

- Java 17 or newer (matches the target version of the project)
- Access to a MongoDB server (Atlas, local, or self-hosted)
- Build system with Maven or Gradle support

## 1. Pull the dependency

### Maven

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.wtje</groupId>
    <artifactId>mongo-configs</artifactId>
    <version>VERSION</version>
</dependency>
```

Replace `VERSION` with the latest release tag.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.wtje:mongo-configs:VERSION")
}
```

## 2. Provide MongoDB credentials

Create a `MongoConfig` instance with your connection details. At minimum you need the connection string, database name, and collection names for configs, messages, and typed configs.

```java
MongoConfig mongoConfig = MongoConfig.builder()
    .connectionString("mongodb://localhost:27017")
    .database("my-plugin")
    .configsCollection("configs")
    .messagesCollection("messages")
    .typedConfigsCollection("typed-configs")
    .build();
```

## 3. Bootstrap the manager

Instantiate the `MongoConfigManager` with the config and call `initialize()` on startup. Pair it with `shutdown()` when your plugin/app closes to clean up threads.

```java
MongoConfigManager configManager = new MongoConfigManager(mongoConfig);
configManager.initialize();

// On disable / shutdown
configManager.shutdown();
```

The manager will create missing collections and seed cache structures automatically. Continue with [Creating Configs](Creating-Configs) to model data or jump straight to the [Example Plugin](Example-Plugin).
