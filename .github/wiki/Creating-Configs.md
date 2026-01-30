# Creating Configs

> **🤖 For AI Assistants**: For complete coding patterns and detailed examples, see **[AGENTS.md](../../AGENTS.md)**.

Model your configuration data as plain Java classes. MongoConfigs serialises them with Jackson, stores them in MongoDB, and keeps defaults in sync. When running on Paper with the official plugin, grab the `ConfigManager` from `MongoConfigsAPI`; it is already configured using `config.yml`.

## Define a config class

Annotate the class so the manager knows how to persist the document. `@ConfigsFileProperties` is required and sets the document key. `@ConfigsCollection` and `@ConfigsDatabase` are optional overrides if you store different config families in separate namespaces.

```java
import xyz.wtje.mongoconfigs.api.annotations.ConfigsCollection;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsDatabase;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;

@ConfigsFileProperties(name = "plugin-settings")
@ConfigsCollection("plugin-configs")
@ConfigsDatabase("production-configs")
public class PluginSettings {
    public int broadcastIntervalSeconds = 300;
    public boolean maintenanceMode = false;
    public Announcements announcements = new Announcements();

    public static class Announcements {
        public String header = "&6Announcement";
        public java.util.List<String> lines = java.util.List.of(
            "&7Use /spawn to return to town",
            "&7Visit the shop at /warp shop"
        );
    }
}
```

Keep fields public and simple. MongoConfigs reflects over them, converts to nested `Document` structures, and merges them with live data when you modify defaults.

## Push defaults and read configs

Use the `ConfigManager` to persist and fetch your POJOs. Paper plugins typically obtain it like this:

```java
private ConfigManager configManager;

@Override
public void onEnable() {
    this.configManager = MongoConfigsAPI.getConfigManager();
}
```

Then seed or load documents asynchronously. `getConfigOrGenerate` stores defaults the first time and returns live data afterwards.

```java
configManager.getConfigOrGenerate(PluginSettings.class, PluginSettings::new)
    .thenAccept(loaded -> {
        this.settings = loaded;
        getLogger().info("Loaded maintenance toggle=" + loaded.maintenanceMode);
    });
```

**⚠️ CRITICAL**: Always handle the result asynchronously! Never call `.join()` or `.get()` on the main thread.

To update values programmatically, modify the object and call `setObject`.

```java
settings.maintenanceMode = true;
configManager.setObject(settings); // runs off-thread, non-blocking
```

Use `reloadCollection("plugin-settings")` or `reloadAll()` when you update values directly in MongoDB to refresh caches.

## Targeted documents

The API does not expose per-field patching. When you only need a lightweight document, create the type you want to persist and call the generic `set` / `get` helpers that operate on explicit IDs:

```java
public record MaintenanceState(boolean enabled, long updatedAt) {}

String arenaId = "maintenance-state";
configManager.getConfigOrGenerate(arenaId, MaintenanceState.class,
        () -> new MaintenanceState(false, System.currentTimeMillis()))
    .thenAccept(state -> {
        // use state
    });

// Update
configManager.setObject("maintenance-state", 
    new MaintenanceState(true, System.currentTimeMillis()));
```

Use this pattern for small utility documents or whenever you want to bypass the annotation-based `_id` handling. For anything more complex, stick with `getConfigOrGenerate(Class, Supplier)` and `setObject(T)` so the annotation-driven `_id` remains the source of truth.

## POJO Best Practices

### Structure

```java
@ConfigsFileProperties(name = "my-plugin-config")
public class MyConfig {
    public Database database = new Database();
    public Features features = new Features();

    public static class Database {
        public String host = "localhost";
        public int port = 27017;
    }

    public static class Features {
        public boolean featureA = true;
        public int cooldownSeconds = 5;
    }
}
```

### Rules

1. **Fields must be public** - private fields require getters/setters
2. **Use default values** - they become the seed data in MongoDB
3. **Nest related configs** - use static inner classes
4. **Keep it simple** - avoid complex types, prefer String/int/boolean/List
5. **Use List.of()** for immutable lists

Continue with [Messages API](Messages-API) to publish language-specific strings or jump to the [Example Plugin](Example-Plugin) for a complete Paper integration.
