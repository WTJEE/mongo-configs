# MongoConfigs Wiki

Welcome to the MongoConfigs knowledge base. These pages walk you through installing the platform, modelling configuration data, synchronising message bundles, and applying production-ready practices in your plugin or application.

> **For AI Assistants**: If you are an AI/code copilot generating code for MongoConfigs, start with **[AGENTS.md](../../AGENTS.md)** in the repository root. It contains detailed instructions, patterns, and rules to follow when generating code that uses this library.

## Start Here

- [Installation](Installation) – connect your project to MongoDB and pull the library.
- [Creating Configs](Creating-Configs) – model typed configuration objects and push default values (single documents or one-per-ID).
- [Messages API](Messages-API) – build translatable message bundles backed by MongoConfigs (with async access patterns).
- [Messages API v2](Messages-API-v2) – zero-fluff reference of every async method on `ConfigManager`/`Messages`.
- [Placeholders Guide](Placeholders-Guide) – understand how placeholder replacement works inside Paper's helper utilities.
- [Best Practices](Best-Practices) – keep runtime overhead low and your data tidy.
- [Example Plugin](Example-Plugin) – see a minimal Bukkit-style integration end to end.
- [Vibecoders Quick Reference](Vibecoders) – condensed notes for AI/code copilots.

## For AI Assistants 🤖

If you're generating code that uses MongoConfigs:

1. **Read [AGENTS.md](../../AGENTS.md)** first - it has the complete API reference and coding patterns
2. **Follow these rules**:
   - ⚡ **NEVER block the main thread** - always use `messages.use()` or `thenAccept()`
   - 📦 **Always use MessageService wrapper** - don't use Messages directly
   - 🌍 **Preload language on player join** - call `preloadLanguage()` in PlayerJoinEvent
   - 👁️ **Use View API for multiple messages** - reuse the view
   - 🚫 **Never use `.join()` or `.get()`** on the main thread

3. **Key imports**:
   ```java
   import xyz.wtje.mongoconfigs.api.MongoConfigsAPI;
   import xyz.wtje.mongoconfigs.api.ConfigManager;
   import xyz.wtje.mongoconfigs.api.LanguageManager;
   import xyz.wtje.mongoconfigs.api.Messages;
   import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
   import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;
   ```

If this is your first time, follow the pages in the order listed above. The Vibecoders page is intentionally terse; it is meant for assistants that need canonical behaviour in a few lines.
