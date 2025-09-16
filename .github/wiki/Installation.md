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

If you are integrating MongoConfigs in a non-Paper environment, add the dependency manually and build the configuration object.

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

Construct `MongoConfig` with your connection info (either through setters or the builder) and pass it to `MongoConfigManager`.

```java
MongoConfig config = MongoConfig.builder()
    .connectionString("mongodb://localhost:27017")
    .database("my-app")
    .configsCollection("configs")
    .messagesCollection("messages")
    .typedConfigsCollection("typed-configs")
    .build();

MongoConfigManager manager = new MongoConfigManager(config);
manager.initialize();
```

Shut the manager down when your application stops to close the MongoDB client and thread pools.
