# Example Plugin

This example wires together installation, configuration, and message lookups for a Paper/Spigot plugin. Adjust package names and platform APIs to fit your project.

## Files

```
src/main/java/
 └─ xyz/wtje/example/
    ├─ ExamplePlugin.java
    ├─ config/PluginSettings.java
    └─ messages/PluginMessages.java
```

## PluginSettings.java

```java
package xyz.wtje.example.config;

import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;

@ConfigsFileProperties(name = "plugin-settings")
public class PluginSettings {
    public boolean maintenanceMode = false;
    public int broadcastIntervalSeconds = 180;
}
```

## PluginMessages.java

```java
package xyz.wtje.example.messages;

import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

import java.util.List;

@ConfigsFileProperties(name = "plugin-messages")
@SupportedLanguages({"en", "pl"})
public class PluginMessages {
    public General general = new General();

    public static class General {
        public String prefix = "&8[&aExample&8] ";
        public String toggleOn = "&aMaintenance enabled";
        public String toggleOff = "&cMaintenance disabled";
        public List<String> status = List.of(
            "&7Maintenance: {state}",
            "&7Players online: {online}"
        );
    }
}
```

## ExamplePlugin.java

```java
package xyz.wtje.example;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.Messages;
import xyz.wtje.mongoconfigs.api.MongoConfigManager;
import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.example.config.PluginSettings;
import xyz.wtje.example.messages.PluginMessages;

public final class ExamplePlugin extends JavaPlugin {
    private ConfigManager configManager;
    private PluginSettings settings;
    private Messages messages;

    @Override
    public void onEnable() {
        MongoConfig mongoConfig = MongoConfig.builder()
            .connectionString("mongodb://localhost:27017")
            .database("example-plugin")
            .configsCollection("configs")
            .messagesCollection("messages")
            .typedConfigsCollection("typed-configs")
            .cacheTtlSeconds(300)
            .cacheMaxSize(2048)
            .build();

        this.configManager = new MongoConfigManager(mongoConfig);
        configManager.initialize();

        configManager.getConfigOrGenerate(PluginSettings.class, PluginSettings::new)
            .thenAccept(cfg -> this.settings = cfg);

        configManager.getOrCreateFromObject(new PluginMessages())
            .thenAccept(msgs -> this.messages = msgs);
    }

    @Override
    public void onDisable() {
        if (configManager != null) {
            configManager.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"maintenance".equalsIgnoreCase(command.getName())) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can toggle maintenance");
            return true;
        }

        settings.maintenanceMode = !settings.maintenanceMode;
        configManager.setObject(settings);

        if (messages != null) {
            String key = settings.maintenanceMode ? "general.toggleOn" : "general.toggleOff";
            messages.get(key).thenAccept(player::sendMessage);
        }

        return true;
    }
}
```

## Flow

1. The plugin loads MongoConfigs using the credentials from `MongoConfig`.
2. On enable it seeds `PluginSettings` and `PluginMessages`, storing both handles for later.
3. A `/maintenance` command flips the maintenance flag, persists it, and sends feedback using the message bundle.

Use this as a baseline and expand with scheduled reloads, custom colour processors, or integration tests as outlined in [Best Practices](Best-Practices).
