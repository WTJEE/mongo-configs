# Example Plugin

This example shows how a Paper plugin consumes MongoConfigs when the `mongo-configs-paper` module is present. The Paper module reads `config.yml` and provides ready-to-use managers through `MongoConfigsAPI`, so you never touch the builder yourself.

## plugin.yml

```yaml
name: ExamplePlugin
version: 1.0.0
main: xyz.wtje.example.ExamplePlugin
api-version: '1.20'
depend: [MongoConfigs]
```

Declaring the dependency guarantees the managers are initialised before your plugin enables.

## Source layout

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

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.Messages;
import xyz.wtje.mongoconfigs.api.MongoConfigsAPI;
import xyz.wtje.example.config.PluginSettings;
import xyz.wtje.example.messages.PluginMessages;

public final class ExamplePlugin extends JavaPlugin {
    private ConfigManager configManager;
    private PluginSettings settings;
    private Messages messages;

    @Override
    public void onEnable() {
        this.configManager = MongoConfigsAPI.getConfigManager();

        configManager.getConfigOrGenerate(PluginSettings.class, PluginSettings::new)
            .thenAccept(loaded -> {
                this.settings = loaded;
                getLogger().info("Loaded maintenance toggle=" + loaded.maintenanceMode);
            })
            .exceptionally(throwable -> {
                getLogger().severe("Failed to load settings: " + throwable.getMessage());
                return null;
            });

        configManager.getOrCreateFromObject(new PluginMessages())
            .thenAccept(msgs -> this.messages = msgs)
            .exceptionally(throwable -> {
                getLogger().severe("Failed to load messages: " + throwable.getMessage());
                return null;
            });
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
        configManager.setObject(settings); // async, no blocking

        if (messages != null) {
            String key = settings.maintenanceMode ? "general.toggleOn" : "general.toggleOff";
            messages.get(key).thenAccept(msg ->
                Bukkit.getScheduler().runTask(this, () -> player.sendMessage(msg))
            );
        }

        return true;
    }
}
```

## Flow

1. `MongoConfigs` loads first, reads `config.yml`, and initialises the managers on worker pools defined in the YAML.
2. Your plugin fetches the managers through `MongoConfigsAPI` and schedules async loads with `getConfigOrGenerate` / `getOrCreateFromObject`.
3. Command handlers mutate the config, persist it asynchronously, and send translated messages once the futures complete (rescheduling to the main thread only for Bukkit calls).

This pattern keeps the server thread free from MongoDB I/O (`Server thread 0.00%` in timings) while letting you reuse the default config shipped by the Paper module.
