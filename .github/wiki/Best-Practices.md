# Best Practices

Follow these recommendations to keep MongoConfigs responsive and maintainable in production environments.

## Threading and Async

- Treat every API call as asynchronous. Chain continuations with `thenAccept`/`thenCompose` or reschedule on your main thread executor.
- Never block on `CompletableFuture#get` in the server thread; it can freeze the tick loop.
- Prefer batching reloads with `reloadAll()` during scheduled maintenance instead of hammering the database per-request.

## Schema management

- Keep POJOs small and focused. Split unrelated concerns into separate classes and collections.
- Use `@SupportedLanguages` only for languages you actually serve—extra entries trigger unnecessary lookups.
- Version large structural changes by renaming the `@ConfigsFileProperties` value so you can migrate old data gradually.

## Performance tuning

- Configure cache TTL and size in `MongoConfig` to match your workload. Set `cacheRecordStats` to true temporarily when diagnosing hit rates.
- Call `setColorProcessor` once on start-up if you perform colour translation (e.g. MiniMessage). The message formatter reuses it for every lookup.
- Monitor the MongoDB server for index and throughput metrics; add indexes on `_id` plus custom keys if you use `TypedConfigManager#set`/`get` heavily.

## Operations and tooling

- Run `reloadCollection("name")` after editing documents directly in MongoDB to invalidate caches hot.
- Couple configuration changes with automated tests. Store representative POJOs in your test sources and assert round-trip correctness using the API module.
- Document placeholders (e.g. `{player}`, `{lang}`) near the fields so translators know which tokens are available.

Continue to the [Example Plugin](Example-Plugin) for an end-to-end scenario.
