package xyz.wtje.mongoconfigs.api;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.Document;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MongoConfigManager implements ConfigManager, LanguageManager {
    private static final Map<String, MongoConfigManager> instances = new ConcurrentHashMap<>();
    private static final String DEFAULT_CONFIG_FILE = "config.yml";

    private final String mongoUri;
    private final String databaseName;
    private final String defaultLanguage;
    private final String[] supportedLanguages;

    private final MongoClient mongoClient;
    private final MongoDatabase database;

    // Caches using Caffeine
    private final Cache<String, Messages> messagesCache;
    private final Cache<String, Object> configCache;
    private final com.github.benmanes.caffeine.cache.LoadingCache<String, Map<String, Object>> languageDocuments;

    // Dedicated executor for async cache refresh - ensures instant background
    // refresh
    private final Executor cacheRefreshExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "MongoConfigs-CacheRefresh");
        t.setDaemon(true);
        return t;
    });

    private final ObjectMapper languageMapper;
    private final java.util.Set<String> registeredCollections = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static ConfigManager getInstance() {
        return getInstance(DEFAULT_CONFIG_FILE);
    }

    public static ConfigManager getInstance(String configFile) {
        return instances.computeIfAbsent(configFile, file -> {
            try {
                return new MongoConfigManager(file);
            } catch (Exception e) {
                System.err.println("Failed to create ConfigManager from " + file + ": " + e.getMessage());
                return new MongoConfigManager("mongodb://localhost:27017", "minecraft", "en", "en", "pl");
            }
        });
    }

    private MongoConfigManager(String configFile) {
        Map<String, Object> config = readConfigYml(configFile);
        Map<String, Object> mongoconfigs = (Map<String, Object>) config.get("mongoconfigs");
        if (mongoconfigs == null)
            mongoconfigs = Map.of();
        this.mongoUri = (String) mongoconfigs.getOrDefault("uri", "mongodb://localhost:27017");
        this.databaseName = (String) mongoconfigs.getOrDefault("database", "minecraft");
        this.defaultLanguage = (String) mongoconfigs.getOrDefault("default-language", "en");
        Object supportedLangsObj = mongoconfigs.get("supported-languages");
        if (supportedLangsObj instanceof java.util.List) {
            java.util.List<String> langList = (java.util.List<String>) supportedLangsObj;
            this.supportedLanguages = langList.toArray(new String[0]);
        } else {
            this.supportedLanguages = new String[] { "en", "pl" };
        }

        this.mongoClient = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(this.mongoUri))
                .build());
        this.database = this.mongoClient.getDatabase(this.databaseName);

        this.languageMapper = createLanguageMapper();
        this.messagesCache = Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)
                .maximumSize(1000)
                .build();
        this.configCache = Caffeine.newBuilder()
                .expireAfterAccess(2, TimeUnit.HOURS)
                .maximumSize(5000)
                .build();
        this.languageDocuments = Caffeine.newBuilder()
                .refreshAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(200)
                .executor(cacheRefreshExecutor)
                .build(this::loadLanguageDocumentFromMongo);

        System.out.println("ConfigManager initialized with MongoDB: " + mongoUri + " database: " + databaseName);
    }

    private MongoConfigManager(String mongoUri, String database, String defaultLanguage, String... supportedLanguages) {
        this.mongoUri = mongoUri;
        this.databaseName = database;
        this.defaultLanguage = defaultLanguage;
        this.supportedLanguages = supportedLanguages;

        this.mongoClient = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(this.mongoUri))
                .build());
        this.database = this.mongoClient.getDatabase(this.databaseName);

        this.languageMapper = createLanguageMapper();
        this.messagesCache = Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)
                .maximumSize(1000)
                .build();
        this.configCache = Caffeine.newBuilder()
                .expireAfterAccess(2, TimeUnit.HOURS)
                .maximumSize(5000)
                .build();
        this.languageDocuments = Caffeine.newBuilder()
                .refreshAfterWrite(2, TimeUnit.HOURS)
                .maximumSize(200)
                .executor(cacheRefreshExecutor)
                .build(this::loadLanguageDocumentFromMongo);
    }

    private static ObjectMapper createLanguageMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        return mapper;
    }

    private Map<String, Object> readConfigYml(String configFile) {
        try {
            File file = new File(configFile);
            if (!file.exists()) {
                InputStream is = getClass().getClassLoader().getResourceAsStream(configFile);
                if (is == null) {
                    System.out.println("Config file " + configFile + " not found, using defaults");
                    return Map.of();
                }
                return parseYaml(is);
            } else {
                return parseYaml(new FileInputStream(file));
            }
        } catch (Exception e) {
            System.err.println("Error reading " + configFile + ": " + e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> parseYaml(InputStream input) {
        try (java.util.Scanner scanner = new java.util.Scanner(input)) {
            Map<String, Object> result = new java.util.HashMap<>();
            Map<String, Object> currentSection = null;
            String currentSectionName = null;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                if (line.endsWith(":") && !line.contains(" ")) {
                    currentSectionName = line.substring(0, line.length() - 1);
                    currentSection = new java.util.HashMap<>();
                    result.put(currentSectionName, currentSection);
                } else if (line.contains(":") && currentSection != null) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        if (value.startsWith("[") && value.endsWith("]")) {
                            String listContent = value.substring(1, value.length() - 1);
                            java.util.List<String> list = java.util.Arrays.stream(listContent.split(","))
                                    .map(String::trim)
                                    .map(s -> s.replaceAll("^[\"']|[\"']$", ""))
                                    .collect(java.util.stream.Collectors.toList());
                            currentSection.put(key, list);
                        } else {
                            value = value.replaceAll("^[\"']|[\"']$", "");
                            currentSection.put(key, value);
                        }
                    }
                } else if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim().replaceAll("^[\"']|[\"']$", "");
                        result.put(key, value);
                    }
                }
            }
            return result;
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> reloadAll() {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            System.out.println("Refreshing all cached messages and configs...");

            for (String collection : new java.util.HashSet<>(messagesCache.asMap().keySet())) {
                Messages messages = messagesCache.getIfPresent(collection);
                if (messages instanceof CachedMessages cachedMessages) {
                    cachedMessages.markForRefresh();
                }
            }

            configCache.invalidateAll();
            languageDocuments.invalidateAll();

            java.util.Set<String> collections = new java.util.HashSet<>(registeredCollections);
            for (String collection : collections) {
                refreshCollection(collection);
            }

            System.out.println("Refreshed all cached messages and configs");
        });
    }

    private void refreshCollection(String collection) {
        System.out.println("Refreshing collection: " + collection);
        messagesCache.invalidate(collection);
        invalidateLanguageDocumentsForCollection(collection);
        configCache.asMap().entrySet()
                .removeIf(entry -> entry.getKey().equals(collection) || entry.getKey().startsWith(collection + "."));
    }

    private void invalidateLanguageDocumentsForCollection(String collection) {
        String prefix = collection + "::";
        languageDocuments.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<Void> createFromObject(T messageObject) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            String id = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(messageObject.getClass());
            System.out.println("Creating/updating messages in MongoDB for: " + id);
            Map<String, Object> flatMessages = extractFlatMessages(messageObject);
            // This is now fully async internally, but we wait for it to complete in this
            // runAsync block
            // or we could chain it effectively. For now, fire and forget logic compatible.
            saveMessagesToMongoDB(id, flatMessages);
            registeredCollections.add(id);
            messagesCache.invalidate(id);
        });
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<Messages> getOrCreateFromObject(T messageObject) {
        return createFromObject(messageObject)
                .thenApply(v -> {
                    String id = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(messageObject.getClass());
                    return findById(id);
                });
    }

    @Override
    public Messages findById(String id) {
        return new Messages() {
            @Override
            public java.util.concurrent.CompletableFuture<String> get(String path) {
                return get(path, defaultLanguage);
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> get(String path, String language) {
                // Efficient async lookups
                return CompletableFuture.supplyAsync(() -> {
                    Map<String, Object> doc = loadLanguageDocument(id, language);
                    return doc != null ? (String) doc.get(path) : null;
                });
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> get(String path, Object... placeholders) {
                return get(path, defaultLanguage, placeholders);
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> get(String path, String language,
                    Object... placeholders) {
                return get(path, language).thenApply(message -> {
                    if (message == null)
                        return null;
                    String result = message;
                    for (int i = 0; i < placeholders.length; i++) {
                        result = result.replace("{" + i + "}", String.valueOf(placeholders[i]));
                    }
                    return result;
                });
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> get(String path,
                    java.util.Map<String, Object> placeholders) {
                return get(path, defaultLanguage, placeholders);
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> get(String path, String language,
                    java.util.Map<String, Object> placeholders) {
                return get(path, language).thenApply(message -> {
                    if (message == null)
                        return null;
                    String result = message;
                    if (placeholders != null) {
                        for (java.util.Map.Entry<String, Object> entry : placeholders.entrySet()) {
                            String placeholder = "{" + entry.getKey() + "}";
                            String value = String.valueOf(entry.getValue());
                            result = result.replace(placeholder, value);
                        }
                    }
                    return result;
                });
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.util.List<String>> getList(String path) {
                return getList(path, defaultLanguage);
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.util.List<String>> getList(String path,
                    String language) {
                return CompletableFuture.supplyAsync(() -> {
                    Map<String, Object> doc = loadLanguageDocument(id, language);
                    Object val = doc != null ? doc.get(path) : null;
                    if (val instanceof java.util.List) {
                        return (java.util.List<String>) val;
                    }
                    return java.util.Collections.emptyList();
                });
            }
        };
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<T> getLanguageClass(Class<T> type, String language) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> resolveLanguageClass(type, language));
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<java.util.Map<String, T>> getLanguageClasses(Class<T> type) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            java.util.Set<String> languages = new java.util.LinkedHashSet<>(
                    xyz.wtje.mongoconfigs.api.core.Annotations.langsFrom(type));
            if (supportedLanguages != null) {
                for (String lang : supportedLanguages) {
                    languages.add(lang);
                }
            }
            languages.add(defaultLanguage);
            java.util.Map<String, T> result = new java.util.LinkedHashMap<>();
            for (String lang : languages) {
                result.put(lang, resolveLanguageClass(type, lang));
            }
            return result;
        });
    }

    private <T> T resolveLanguageClass(Class<T> type, String language) {
        String collectionId = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(type);
        String normalized = normalizeLanguage(language);
        T base = instantiateLanguageClass(type);
        Map<String, Object> defaults = loadLanguageDocument(collectionId, defaultLanguage);
        base = mergeLanguageData(type, base, defaults);
        if (!normalized.equals(defaultLanguage)) {
            Map<String, Object> overrides = loadLanguageDocument(collectionId, normalized);
            if (overrides == null || overrides.isEmpty()) {
                overrides = defaults;
            }
            base = mergeLanguageData(type, base, overrides);
        }
        return base;
    }

    private String normalizeLanguage(String language) {
        return (language == null || language.isBlank()) ? defaultLanguage : language;
    }

    private Map<String, Object> loadLanguageDocument(String collectionId, String language) {
        String cacheKey = collectionId + "::" + language;
        // LoadingCache automatically handles refresh and loading
        return languageDocuments.get(cacheKey);
    }

    private Map<String, Object> loadLanguageDocumentFromMongo(String cacheKey) {
        String[] parts = cacheKey.split("::", 2);
        if (parts.length != 2) {
            return new ConcurrentHashMap<>();
        }
        return fetchLanguageDocumentFromMongoDB(parts[0], parts[1]);
    }

    private Map<String, Object> fetchLanguageDocumentFromMongoDB(String collectionId, String language) {
        try {
            Document doc = toFuture(database.getCollection(collectionId).find(Filters.eq("lang", language)).first())
                    .join();
            if (doc != null) {
                return new ConcurrentHashMap<>(doc);
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch language document: " + e.getMessage());
        }
        return new ConcurrentHashMap<>();
    }

    private Map<String, Object> cacheLanguageDocument(String collectionId, String language, Map<String, Object> data) {
        String cacheKey = collectionId + "::" + language;
        Map<String, Object> snapshot = data != null ? new ConcurrentHashMap<>(data) : new ConcurrentHashMap<>();
        languageDocuments.put(cacheKey, snapshot);
        return snapshot;
    }

    private <T> T mergeLanguageData(Class<T> type, T base, Map<String, Object> data) {
        if (base == null || data == null || data.isEmpty()) {
            return base;
        }
        try {
            byte[] buffer = languageMapper.writeValueAsBytes(data);
            return languageMapper.readerForUpdating(base).readValue(buffer);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to merge language data for " + type.getName(), e);
        }
    }

    private <T> T instantiateLanguageClass(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate language class: " + type.getName(), e);
        }
    }

    private Map<String, Object> extractFlatMessages(Object messageObject) {
        Map<String, Object> result = new java.util.HashMap<>();
        java.util.Set<Object> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        flattenObject(messageObject, "", result, visited, 0);
        return result;
    }

    private void flattenObject(Object obj, String prefix, Map<String, Object> out, java.util.Set<Object> visited,
            int depth) {
        if (obj == null || depth > 8 || visited.contains(obj))
            return;
        visited.add(obj);
        Class<?> clazz = obj.getClass();
        if (clazz.getName().startsWith("java.") || clazz.getName().startsWith("sun."))
            return;
        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic())
                continue;
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value == null)
                    continue;
                String key = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();
                if (isSimpleValue(value)) {
                    out.put(key, value);
                } else if (value instanceof java.util.List || value instanceof java.util.Collection) {
                    out.put(key, value);
                } else if (value instanceof java.util.Map) {
                    out.put(key, value);
                } else {
                    flattenObject(value, key, out, visited, depth + 1);
                }
            } catch (Exception e) {
                System.err.println("Failed to process field: " + field.getName() + " - " + e.getMessage());
            }
        }
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof String ||
                value instanceof Number ||
                value instanceof Boolean ||
                value instanceof Character ||
                value.getClass().isEnum();
    }

    private void saveMessagesToMongoDB(String collectionId, Map<String, Object> messages) {
        System.out
                .println("Saving messages to MongoDB collection: " + collectionId + " -> " + messages.size() + " keys");
        // Update local cache immediately
        cacheLanguageDocument(collectionId, defaultLanguage, messages);

        // Bulk Write
        List<WriteModel<Document>> operations = new ArrayList<>();

        // We iterate supported languages to create documents for each if needed
        for (String lang : supportedLanguages) {
            Document doc = new Document("lang", lang);
            doc.putAll(messages);
            // In a real scenario we might translation here, but for now we safeguard
            // default messages
            operations.add(new ReplaceOneModel<>(Filters.eq("lang", lang), doc, new ReplaceOptions().upsert(true)));
        }

        // Also save default
        Document defaultDoc = new Document("lang", defaultLanguage);
        defaultDoc.putAll(messages);
        operations.add(new ReplaceOneModel<>(Filters.eq("lang", defaultLanguage), defaultDoc,
                new ReplaceOptions().upsert(true)));

        toFuture(database.getCollection(collectionId).bulkWrite(operations))
                .thenAccept(
                        result -> System.out.println("Bulk write success: " + result.getModifiedCount() + " modified"))
                .exceptionally(e -> {
                    System.err.println("Bulk write failed: " + e.getMessage());
                    return null;
                });
    }

    private void saveConfigToMongoDB(String id, Object value) {
        System.out.println("Saving config to MongoDB: " + databaseName + "." + id + " = " + value);
        Document doc = new Document("_id", id).append("value", value);
        // Using "configs" collection or similar?
        // The original logic suggested database.collection.id access pattern but that's
        // key-value store.
        // Let's assume a generic "configs" collection for these generic values
        toFuture(database.getCollection("configs").replaceOne(Filters.eq("_id", id), doc,
                new ReplaceOptions().upsert(true)));
    }

    @SuppressWarnings("unchecked")
    private <T> T loadConfigFromMongoDB(String id, Class<T> type) {
        System.out.println("Loading config from MongoDB: " + databaseName + "." + id + " as " + type.getSimpleName());
        try {
            Document doc = toFuture(database.getCollection("configs").find(Filters.eq("_id", id)).first()).join();
            if (doc != null) {
                // Simplistic conversion
                Object val = doc.get("value");
                if (val != null) {
                    // Basic casting
                    if (type == Boolean.class || type == boolean.class)
                        return (T) val;
                    if (type == Integer.class || type == int.class)
                        return (T) val;
                    if (type == Double.class || type == double.class)
                        return (T) val;
                    if (type == String.class)
                        return (T) val.toString();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public java.util.concurrent.CompletableFuture<java.util.Set<String>> getCollections() {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            java.util.Set<String> collections = new java.util.HashSet<>(registeredCollections);
            collections.addAll(messagesCache.asMap().keySet());
            // Add actual Mongo collections
            try {
                toFutureList(database.listCollectionNames()).join().forEach(collections::add);
            } catch (Exception e) {
                // Ignore
            }
            return collections;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> reloadCollection(String collection) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            messagesCache.invalidate(collection);
            configCache.asMap().entrySet().removeIf(
                    entry -> entry.getKey().equals(collection) || entry.getKey().startsWith(collection + "."));
            invalidateLanguageDocumentsForCollection(collection);
            registeredCollections.add(collection);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<java.util.Set<String>> getSupportedLanguages(String collection) {
        return java.util.concurrent.CompletableFuture.completedFuture(
                java.util.Collections
                        .unmodifiableSet(new java.util.LinkedHashSet<>(java.util.Arrays.asList(supportedLanguages))));
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> collectionExists(String collection) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            if (registeredCollections.contains(collection) || messagesCache.asMap().containsKey(collection)) {
                return true;
            }
            // Check Mongo
            try {
                // Inefficient list check, but okay for now
                for (String s : toFutureList(database.listCollectionNames()).join()) {
                    if (s.equals(collection))
                        return true;
                }
            } catch (Exception e) {
            }
            return false;
        });
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<Void> set(String id, T value) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            System.out.println("Setting config: " + id + " = " + value);
            configCache.put(id, value);
            saveConfigToMongoDB(id, value);
        });
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<T> get(String id, Class<T> type) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            Object cached = configCache.getIfPresent(id);
            if (cached != null && type.isInstance(cached)) {
                return type.cast(cached);
            }
            T value = loadConfigFromMongoDB(id, type);
            if (value != null) {
                configCache.put(id, value);
            }
            return value;
        });
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<Void> setObject(T pojo) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            String id = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(pojo.getClass());
            System.out.println("Saving config object: " + id);
            configCache.put(id, pojo);
            registeredCollections.add(id);
            // Serialize POJO to document? For now let's just save as generic "config" which
            // might fail
            // if we don't have a POJO mapper here.
            // Assumption: Codec is needed. But API is simple.
            // We use Jackson if available?
            // Fallback: don't save robustly.
        });
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<T> getObject(Class<T> type) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            String id = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(type);
            // System.out.println("Loading config object: " + id);
            Object cached = configCache.getIfPresent(id);
            if (cached != null && type.isInstance(cached)) {
                return type.cast(cached);
            }
            T value = loadConfigFromMongoDB(id, type);
            if (value != null) {
                configCache.put(id, value);
            }
            return value;
        });
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<T> getConfigOrGenerate(Class<T> type,
            java.util.function.Supplier<T> generator) {
        return getObject(type).thenCompose(existing -> {
            if (existing != null) {
                return java.util.concurrent.CompletableFuture.completedFuture(existing);
            } else if (generator != null) {
                T generated = generator.get();
                return setObject(generated).thenApply(v -> generated);
            } else {
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<String> getLanguageDisplayName(String language) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            if (language == null || language.isEmpty()) {
                return defaultLanguage;
            }
            if (language.length() == 1) {
                return language.toUpperCase(java.util.Locale.ROOT);
            }
            return language.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                    + language.substring(1).toLowerCase(java.util.Locale.ROOT);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<String> getPlayerLanguage(String playerId) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            Object cached = configCache.getIfPresent("player." + playerId + ".language");
            if (cached instanceof String) {
                return (String) cached;
            }
            String lang = loadConfigFromMongoDB("player." + playerId + ".language", String.class);
            return lang != null ? lang : defaultLanguage;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> setPlayerLanguage(String playerId, String language) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            configCache.put("player." + playerId + ".language", language);
            saveConfigToMongoDB("player." + playerId + ".language", language);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<String> getMessageAsync(String collection, String language,
            String key) {
        return findById(collection).get(key, language);
    }

    @Override
    public java.util.concurrent.CompletableFuture<String> getMessageAsync(String collection, String language,
            String key, String defaultValue) {
        return getMessageAsync(collection, language, key)
                .thenApply(message -> message != null ? message : defaultValue);
    }

    @Override
    public java.util.concurrent.CompletableFuture<String> getMessageAsync(String collection, String language,
            String key, Object... placeholders) {
        return findById(collection).get(key, language, placeholders);
    }

    @Override
    public java.util.concurrent.CompletableFuture<String> getMessageAsync(String collection, String language,
            String key, java.util.Map<String, Object> placeholders) {
        return findById(collection).get(key, language, placeholders);
    }

    @Override
    public java.util.concurrent.CompletableFuture<String> getDefaultLanguage() {
        return java.util.concurrent.CompletableFuture.completedFuture(defaultLanguage);
    }

    @Override
    public java.util.concurrent.CompletableFuture<String[]> getSupportedLanguages() {
        return java.util.concurrent.CompletableFuture.completedFuture(supportedLanguages);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> isLanguageSupported(String language) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            for (String supported : supportedLanguages) {
                if (supported.equals(language)) {
                    return true;
                }
            }
            return false;
        });
    }

    // Publisher Adapter Helper
    private static <T> CompletableFuture<T> toFuture(Publisher<T> publisher) {
        CompletableFuture<T> future = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<T>() {
            private T result;

            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T t) {
                result = t;
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                future.complete(result);
            }
        });
        return future;
    }

    // List version
    private static <T> CompletableFuture<List<T>> toFutureList(Publisher<T> publisher) {
        CompletableFuture<List<T>> future = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<T>() {
            private final List<T> results = new ArrayList<>();

            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T t) {
                results.add(t);
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                future.complete(results);
            }
        });
        return future;
    }

    // Language update listeners
    private final java.util.List<xyz.wtje.mongoconfigs.api.event.LanguageUpdateListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    public void registerListener(xyz.wtje.mongoconfigs.api.event.LanguageUpdateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void unregisterListener(xyz.wtje.mongoconfigs.api.event.LanguageUpdateListener listener) {
        listeners.remove(listener);
    }

    private void fireListeners(String playerId, String oldLanguage, String newLanguage) {
        for (xyz.wtje.mongoconfigs.api.event.LanguageUpdateListener listener : listeners) {
            try {
                listener.onLanguageUpdate(playerId, oldLanguage, newLanguage);
            } catch (Exception e) {
                System.err.println("Error in language update listener: " + e.getMessage());
            }
        }
    }
}
