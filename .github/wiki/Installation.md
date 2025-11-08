# Installation

MongoConfigs can be consumed in two ways:

1. **Paper plugin (`mongo-configs-paper`)** – drop the plugin on your server and let it read `config.yml` for the MongoDB URI and cache settings. Your own plugins access the API through `MongoConfigsAPI` without building the config manually.
2. **Embedded library** – add the dependency to an arbitrary JVM application and create a `MongoConfig` instance yourself.

Pick the path that matches your environment.

## Using the Paper plugin (recommended)

1. Place the released `mongo-configs-paper` JAR in your server's `plugins/` directory and restart.
2. Edit `plugins/MongoConfigs/config.yml` with your MongoDB URI, database, and cache settings. The plugin also manages `languages.yml`.
3. In your plugin's `plugin.yml`, declare a soft or hard dependency so the API is initialised first:

   ```yaml
   depend: [MongoConfigs]
   ```
4. Grab the managers from `MongoConfigsAPI` inside `onEnable` – the Paper module has already created and configured them from the YAML files, so you do **not** call the builder yourself.

   ```java
   private ConfigManager configManager;
   private LanguageManager languageManager;

   @Override
   public void onEnable() {
       configManager = MongoConfigsAPI.getConfigManager();
       languageManager = MongoConfigsAPI.getLanguageManager();
   }
   ```

All asynchronous work (MongoDB I/O, serialization, caching) runs on the worker pools defined in `config.yml`. Your plugin only schedules continuations when it needs to touch Bukkit state.

## Embedded library usage (advanced)

If you embed MongoConfigs in another JVM application, depend on the `configs-core` module and instantiate `ConfigManagerImpl` yourself. The API module only exposes the interfaces.

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

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.wtje:mongo-configs:VERSION")
}
```

Construct `MongoConfig` with setters and pass it to `ConfigManagerImpl`. That implementation wires up the MongoDB client, caches, and change-stream subscriptions exactly like the Paper/Velocity modules.

```java
import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;

MongoConfig config = new MongoConfig();
config.setConnectionString("mongodb://localhost:27017");
config.setDatabase("my-app");
config.setConfigsCollection("configs");
config.setTypedConfigsCollection("typed_configs");
config.setPlayerLanguagesCollection("player_languages");
config.setPlayerLanguagesDatabase("my-app");

ConfigManagerImpl manager = new ConfigManagerImpl(config);
manager.initialize();
```

`ConfigManagerImpl` implements the `ConfigManager` API, so you interact with it identically to the server modules. Call `manager.shutdown()` during application shutdown to close the MongoDB client, executor services, and change-stream watchers. If you need a language layer outside Paper/Velocity you must supply your own `LanguageManager` implementation, since the bundled ones depend on Bukkit/Velocity services.
