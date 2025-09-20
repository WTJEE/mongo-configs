# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

Repository overview
- Java, Maven multi-module project for MongoDB-backed configuration and i18n used in Minecraft Paper plugins.
- Modules:
  - configs-api: Public API (ConfigManager, Messages, LanguageManager) and annotations (ConfigsDatabase, ConfigsCollection, SupportedLanguages, ConfigsFileProperties).
  - configs-core: Implementation (Mongo reactive driver, Caffeine cache, typed documents, change stream watchers, Jackson/DSL-JSON codecs, Micrometer metrics).
  - configs-paper: Paper plugin integration (commands, listeners, GUI, default resources) and shaded distribution JAR.
- Example consumer code lives in .github/example (a small Help GUI plugin) illustrating API usage.
- CI builds on JDK 21 but targets Java 17 (maven.compiler.release=17). Release artifact is the shaded JAR from configs-paper.

Common commands
- Build all modules (with tests):
```powershell path=null start=null
mvn -q clean package
```
- Build all modules (skip tests):
```powershell path=null start=null
mvn -q clean package -DskipTests
```
- Build only the Paper plugin module (and its dependencies):
```powershell path=null start=null
mvn -q -pl configs-paper -am clean package -DskipTests
```
- Where to find the plugin JAR after a release build (version substituted by Maven):
  configs-paper/target/MongoConfigs-<version>.jar

Testing
- Run all tests across modules:
```powershell path=null start=null
mvn -q test
```
- Run tests in a specific module:
```powershell path=null start=null
mvn -q -pl configs-core -am test
```
- Run a single test class:
```powershell path=null start=null
mvn -q -Dtest=ConfigManagerTest test
```
- Run a single test method:
```powershell path=null start=null
mvn -q -Dtest=ConfigManagerTest#createsDefaults test
```
Notes:
- Tests leverage Testcontainers (MongoDB). Ensure Docker is available when running tests that start containers.

Architecture and data flow (big picture)
- Configuration model
  - API exposes typed configuration and message access via ConfigManager and Messages. Annotations (ConfigsDatabase, ConfigsCollection, ConfigsFileProperties, SupportedLanguages) declare where and how types map to MongoDB collections and file-based defaults.
  - i18n is centralized via LanguageManager and Messages (configs-api) with language metadata and merging support (Jackson) for language documents.
- Core runtime (configs-core)
  - MongoManager wraps the MongoDB Reactive Streams driver; JacksonCodec and DSL-JSON integration handle serialization for typed documents.
  - CacheManager/AsyncCache (Caffeine) front the datastore; Asyncs/AsyncUtils coordinate non-blocking workflows.
  - ChangeStreamWatcher and TypedChangeStreamManager subscribe to MongoDB change streams to hot-reload configs and invalidate caches on updates.
  - TypedConfigManager implements the high-level typed-config CRUD and watches collections defined by annotations; Micrometer instruments key operations.
- Paper integration (configs-paper)
  - Binds API/core into the Paper lifecycle (MongoConfigsPlugin). Commands (e.g., reload/hotreload and language selection) and listeners wire user actions to API calls. BukkitColorProcessor bridges Adventure components and Paper coloring.
  - Shaded distribution: maven-shade-plugin relocates third-party packages (MongoDB, Caffeine, JCTools, FastUtil, DSL-JSON, Micrometer) under xyz.wtje.mongoconfigs.shaded.* to avoid runtime conflicts.
- Example plugin (.github/example)
  - Demonstrates consuming ConfigManager and LanguageManager from another plugin to build a help GUI and persist per-player language.

Release pipeline
- GitHub Actions (.github/workflows/main.yml) tags a version, sets the Maven project version, builds with tests skipped, and uploads the shaded configs-paper JAR to the GitHub Release.

Documentation wiki
- A workflow (.github/workflows/wiki-sync.yml) syncs documentation from .github/wiki to the repository wiki. Keep docs in .github/wiki to publish them to GitHub Wiki automatically.