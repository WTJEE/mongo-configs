# Creating Configs

Model your configuration data as plain Java classes. MongoConfigs serialises them with Jackson, stores them in MongoDB, and keeps defaults in sync.

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

Use the `ConfigManager` to persist and fetch your POJOs. `getConfigOrGenerate` stores defaults the first time and returns live data afterwards.

```java
PluginSettings settings = new PluginSettings();
configManager.getConfigOrGenerate(PluginSettings.class, PluginSettings::new)
    .thenAccept(loaded -> {
        this.settings = loaded;
        getLogger().info("Loaded maintenance toggle=" + loaded.maintenanceMode);
    });
```

To update values programmatically, modify the object and call `setObject`.

```java
settings.maintenanceMode = true;
configManager.setObject(settings);
```

Use `reloadCollection("plugin-settings")` or `reloadAll()` when you update values directly in MongoDB to refresh caches.

## Partial updates

When you only need to change a single value you can work with the typed config manager directly.

```java
configManager.getTypedConfigManager()
    .set("plugin-settings", "broadcastIntervalSeconds", 120);
```

Call `get("plugin-settings", "broadcastIntervalSeconds", Integer.class)` to read a single property without loading the whole document.

Continue with [Messages API](Messages-API) to publish language-specific strings or jump to the [Example Plugin](Example-Plugin) for a complete walkthrough.
