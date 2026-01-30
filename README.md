# MongoConfigs - Project Import Analysis & Documentation

> ⚠️ **REQUIRES JAVA 21+** - This library uses Virtual Threads (Project Loom) for zero main-thread blocking!

> 🤖 **For AI Assistants**: See [AGENTS.md](AGENTS.md) for complete API documentation, coding patterns, and rules for generating code that uses this library.

> 📚 **Wiki**: Visit our [GitHub Wiki](.github/wiki/Home.md) for user documentation.

## 🚀 Why Java 21?

MongoConfigs leverages **Java 21+ Virtual Threads** for maximum performance:

| Feature | Benefit |
|---------|--------|
| `Executors.newVirtualThreadPerTaskExecutor()` | Ultra-lightweight threads (~1KB vs ~1MB) |
| Non-blocking async operations | Zero main thread lag |
| Millions of concurrent tasks | Handle any server load |

```java
// All async operations run on virtual threads - NEVER blocks main thread!
messages.use("welcome", msg -> player.sendMessage(msg));
```

---

This document provides a comprehensive analysis of every library and import used within the **MongoConfigs** project. It details exactly what each import does, which module it belongs to, and how it contributes to the library's functionality.

## 📊 Import Summary

| Category | Package Prefix | Role | Count (Approx) |
|----------|---------------|------|----------------|
| **Core Java** | `java.*` | Standard functions (Async, I/O, Util) | ~60+ |
| **Database** | `com.mongodb.*`, `org.bson.*` | Reactive MongoDB interaction | ~25+ |
| **Reactive** | `org.reactivestreams.*` | Async Stream Standards | ~5 |
| **Caching** | `com.github.benmanes.caffeine.*` | High-performance in-memory caching | ~2 |
| **JSON/Serialization** | `com.fasterxml.jackson.*` | Object <> Document Mapping | ~10 |
| **Text Engine** | `net.kyori.adventure.*` | Modern Formatting (MiniMessage) | ~15 |
| **Bukkit/Paper** | `org.bukkit.*` | Minecraft Server (Spigot) API | ~30+ |
| **Velocity** | `com.velocitypowered.*` | Minecraft Proxy API | ~25+ |
| **Testing** | `org.junit.*`, `org.mockito.*` | Unit Testing & Mocking | ~15+ |

---

## 📘 1. Java Standard Library (`java.*`)

These are the building blocks of the application, included in the JDK.

### ⚡ Concurrency (`java.util.concurrent`)
**Purpose:** Handles all asynchronous tasks to ensure the main server thread never freezes.
- `java.util.concurrent.CompletableFuture`: The backbone of this library. Represents a result that will be available in the future. Used for almost all API return types (`getMessageAsync`).
- `java.util.concurrent.ConcurrentHashMap`: A thread-safe map used for caching configs and languages in memory without race conditions.
- `java.util.concurrent.Executor`, `ExecutorService`: Interfaces for managing thread pools (worker threads).
- `java.util.concurrent.ForkJoinPool`: The default pool used for non-blocking async operations.
- `java.util.concurrent.atomic.*` (`AtomicBoolean`, `AtomicInteger`, `AtomicReference`): Variables that can be updated thread-safely without locks. Used for tracking state (e.g., initial load completion).
- `java.util.concurrent.TimeUnit`: Utility for specifying time durations (e.g., "SECONDS", "MILLISECONDS").

### 📦 Collections & Util (`java.util`)
**Purpose:** storage structures for data.
- `List`, `ArrayList`, `CopyOnWriteArrayList`: Ordered lists of items (e.g., list of loaded languages). `CopyOnWrite` is used for thread safety when reading frequently.
- `Map`, `HashMap`, `LinkedHashMap`: Key-Value storage (e.g., `Config Key -> Value`).
- `Set`, `HashSet`: Unique collections (e.g., set of active collection names).
- `UUID`: Universally Unique Identifier, used for identifying players.
- `Optional`: Container object which may or may not contain a non-null value.
- `Base64`: Used for encoding/decoding custom texture data (skulls) in GUIs.

### 📥 Input/Output (`java.io`, `java.nio`)
**Purpose:** Reading local files like `config.yml`.
- `File`, `FileInputStream`, `InputStream`: accessing raw file streams.
- `Files`, `Path`: Newer NIO (Non-blocking I/O) file access methods used in the Velocity module.
- `IOException`: Error thrown when file reading fails.

### 🧠 Reflection & Annotations (`java.lang.reflect`, `java.lang.annotation`)
**Purpose:** Parsing the custom `@Config` annotations to dynamically load classes.
- `Method`, `Field`:Accessing class members dynamically.
- `@Retention`, `@Target`, `@ElementType`: Meta-annotations defining how our custom annotations (`@Config`, `@Collection`) behave.

---

## 🍃 2. MongoDB Reactive Driver (`com.mongodb.*`, `org.bson.*`)

**Purpose:** Connects to the database in a purely non-blocking way using Reactive Streams.

### Client & Connection
- `com.mongodb.reactivestreams.client.MongoClient`: The root connection object to the database server.
- `com.mongodb.reactivestreams.client.MongoDatabase`: Represents a specific database (e.g., "minecraft").
- `com.mongodb.reactivestreams.client.MongoCollection`: Represents a collection (table) inside the DB.
- `com.mongodb.ConnectionString`: Parses the URI (e.g., `mongodb://user:pass@host...`).

### Models & Logic
- `org.bson.Document`: The standard representation of a BSON document (JSON-like) in Java.
- `com.mongodb.client.model.Filters`: Helper to create queries (e.g., `Filters.eq("playerId", uuid)`).
- `com.mongodb.client.model.ReplaceOptions`: Settings for saving data (e.g., "Upsert": create if not exists).
- `com.mongodb.client.model.changestream.*` (`ChangeStreamDocument`, `FullDocument`): Classes for handling **Change Streams**, which allow the plugin to react instantly to database updates made elsewhere.

### 🌊 Reactive Streams (`org.reactivestreams`)
**Purpose:** Standard interface for asynchronous stream processing with non-blocking back pressure.
- `Publisher`: Emits data (e.g., a query result).
- `Subscriber`: Listens for data.
- `Subscription`: Manages the link between Publisher and Subscriber.

---

## ☕ 3. Caffeine Cache (`com.github.benmanes.caffeine`)

**Purpose:** State-of-the-art caching library that performs significantly better than standard Maps.

- `com.github.benmanes.caffeine.cache.Cache`: The main interface for the cache.
- `com.github.benmanes.caffeine.cache.Caffeine`: The builder used to configure the cache (timings, size limits).
- **Usage:** Used in `CacheManager` to store messages. Configured with `expireAfterAccess` to automatically remove unused messages after a set time.

---

## 📜 4. Jackson JSON (`com.fasterxml.jackson`)

**Purpose:** Advanced Object Mapping. Converting Java Objects (POJOs) to Database Documents and vice-versa.

- `ObjectMapper`: The heavy-lifter that performs the conversion.
- `JsonAutoDetect`, `PropertyAccessor`: Configures Jackson to see private fields in your config classes.
- `DeserializationFeature`: Configures tolerance (e.g., don't crash if a field is missing).
- `JavaTimeModule`: Helper to serialize Java `Date` and `Instant` objects correctly.

---

## 🎨 5. Adventure API (`net.kyori.adventure`)

**Purpose:** The modern standard for Minecraft text, colors, and translations.

- `net.kyori.adventure.text.Component`: Represents a text message with style (supersedes simple Strings).
- `net.kyori.adventure.text.minimessage.MiniMessage`: Parser for the `<red>Hello <gradient:red:blue>World` format.
- `net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer`: Converts modern components back to `&aOld &cColors` for legacy support.
- `net.kyori.adventure.text.format.NamedTextColor`: Standard colors (Red, Blue, etc.).

---

## 🎮 6. Platform Specific Imports

### A. Spigot / Paper (`org.bukkit.*`)
Used only in the **configs-paper** module.
- `org.bukkit.plugin.java.JavaPlugin`: The main class for any Spigot plugin.
- `org.bukkit.event.Listener`, `@EventHandler`: Handling server events (Join, Quit).
- `org.bukkit.command.*`: Handling commands (`/mongo reload`).
- `org.bukkit.entity.Player`: Represents a connected player.
- `org.bukkit.inventory.*`: GUI creation (Inventories, ItemStacks).

### B. Velocity (`com.velocitypowered.*`)
Used only in the **configs-velocity** module.
- `com.velocitypowered.api.plugin.Plugin`: Velocity plugin entry point.
- `com.velocitypowered.api.proxy.ProxyServer`: Interaction with the proxy.
- `com.velocitypowered.api.event.Subscribe`: Listener for Velocity events.
- `com.google.inject.Inject`: **Guice Injection**. Velocity uses dependency injection to give you instances of the Server, Logger, etc.

---

## 🧩 7. Internal Project Modules (`xyz.wtje.mongoconfigs`)

How the project parts talk to each other.

- **`xyz.wtje.mongoconfigs.api.*`**: The public interfaces.
    - `MongoConfigManager`: Singleton access point.
    - `ConfigManager`: Interface for getting/setting data.
    - `annotations.*`: `@Config`, `@Collection` definition.
- **`xyz.wtje.mongoconfigs.core.*`**: The brains of the operation.
    - `mongo.MongoManager`: Manages the DB connection.
    - `impl.ConfigManagerImpl`: The code that actually runs the logic defined in the API.
    - `util.*`: Helpers for Color and Async logic.
- **`xyz.wtje.mongoconfigs.paper/velocity`**: Platform wrappers.
    - Connect the specific platform (Paper/Velocity) to the Core.

---

## 🧪 8. Testing (`org.junit`, `org.mockito`)
Used only in `src/test`.
- `org.junit.jupiter.api.Test`: Marks a method as a test.
- `org.junit.jupiter.api.Assertions.*`: Checks if results are correct (`assertEquals`, `assertTrue`).
- `org.mockito.*`: Creates "fake" objects (Mocking) to simulate a database or player connecting without actually starting a server.

---

*Generated by GitHub Copilot on request.*
