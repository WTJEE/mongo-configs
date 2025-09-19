# Help GUI Example Plugin

This standalone example shows how to consume **MongoConfigs** from a Paper plugin to build a help GUI that respects the player's selected language. It demonstrates:

- loading typed defaults through `ConfigManager#getOrCreateFromObject`,
- reading per-player languages from `LanguageManager`,
- waiting on `CompletableFuture` chains off the main thread,
- scheduling back to the Bukkit thread before interacting with inventories,
- updating stored language preferences via the API.

The code lives under `src/main/java/xyz/wtje/example/help` and is intentionally small so it can be copy-pasted into an existing plugin.

## Building

Add the paper module as a dependency (adjust the version to match the repository):

```xml
<dependency>
    <groupId>xyz.wtje</groupId>
    <artifactId>mongo-configs-paper</artifactId>
    <version>${mongo-configs.version}</version>
    <scope>provided</scope>
</dependency>
```

The supplied `pom.xml` is configured to compile with Java 17 and depends on Spigot/Paper APIs alongside `mongo-configs-paper`.

## Running the example

1. Drop the built jar into your server alongside the MongoConfigs plugin.
2. Start the server once so MongoConfigs creates the defaults.
3. Use `/helpui` in-game to open the GUI.
4. Click the book to print the configured help lines or click any language entry to switch your stored language on the fly.

The defaults are stored under the collection `help-example` with English and Polish translations. Update them in MongoDB, run `/mongoconfigs reload help-example`, and reopen the GUI to see live changes.
