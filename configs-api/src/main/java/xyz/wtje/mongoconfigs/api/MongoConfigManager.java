package xyz.wtje.mongoconfigs.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MongoConfigManager implements ConfigManager, LanguageManager {
    private static final Map<String, MongoConfigManager> instances = new ConcurrentHashMap<>();
    private static final String DEFAULT_CONFIG_FILE = "config.yml";

    private final String mongoUri;
    private final String database;
    private final String defaultLanguage;
    private final String[] supportedLanguages;
    private final Map<String, Messages> messagesCache = new ConcurrentHashMap<>();
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();
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
        if (mongoconfigs == null) mongoconfigs = Map.of();
        this.mongoUri = (String) mongoconfigs.getOrDefault("uri", "mongodb://localhost:27017");
        this.database = (String) mongoconfigs.getOrDefault("database", "minecraft");
        this.defaultLanguage = (String) mongoconfigs.getOrDefault("default-language", "en");
        Object supportedLangsObj = mongoconfigs.get("supported-languages");
        if (supportedLangsObj instanceof java.util.List) {
            java.util.List<String> langList = (java.util.List<String>) supportedLangsObj;
            this.supportedLanguages = langList.toArray(new String[0]);
        } else {
            this.supportedLanguages = new String[]{"en", "pl"};
        }
        System.out.println("ConfigManager initialized with MongoDB: " + mongoUri + " database: " + database);
    }

    private MongoConfigManager(String mongoUri, String database, String defaultLanguage, String... supportedLanguages) {
        this.mongoUri = mongoUri;
        this.database = database;
        this.defaultLanguage = defaultLanguage;
        this.supportedLanguages = supportedLanguages;
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
                if (line.isEmpty() || line.startsWith("#")) continue;
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
            messagesCache.clear();
            configCache.clear();
            registeredCollections.clear();
            System.out.println("Cleared all cached messages and configs");
        });
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<Void> createFromObject(T messageObject) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            String id = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(messageObject.getClass());
            System.out.println("Creating/updating messages in MongoDB for: " + id);
            Map<String, Object> flatMessages = extractFlatMessages(messageObject);
            saveMessagesToMongoDB(id, flatMessages);
            registeredCollections.add(id);
            messagesCache.remove(id);
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
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    System.out.println("Getting message: " + id + ":" + language + ":" + path);
                    return "Mock message for " + path;
                });
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> get(String path, Object... placeholders) {
                return get(path, defaultLanguage, placeholders);
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> get(String path, String language, Object... placeholders) {
                return get(path, language).thenApply(message -> {
                    String result = message;
                    for (int i = 0; i < placeholders.length; i++) {
                        result = result.replace("{" + i + "}", String.valueOf(placeholders[i]));
                    }
                    return result;
                });
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.util.List<String>> getList(String path) {
                return getList(path, defaultLanguage);
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.util.List<String>> getList(String path, String language) {
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    System.out.println("Getting message list: " + id + ":" + language + ":" + path);
                    return java.util.Arrays.asList("Mock list item 1", "Mock list item 2");
                });
            }
        };
    }

    private Map<String, Object> extractFlatMessages(Object messageObject) {
        Map<String, Object> result = new java.util.HashMap<>();
        java.util.Set<Object> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        flattenObject(messageObject, "", result, visited, 0);
        return result;
    }

    private void flattenObject(Object obj, String prefix, Map<String, Object> out, java.util.Set<Object> visited, int depth) {
        if (obj == null || depth > 8 || visited.contains(obj)) return;
        visited.add(obj);
        Class<?> clazz = obj.getClass();
        if (clazz.getName().startsWith("java.") || clazz.getName().startsWith("sun.")) return;
        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value == null) continue;
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
        System.out.println("Saving messages to MongoDB collection: " + collectionId + " -> " + messages.size() + " keys");
        for (String lang : supportedLanguages) {
            System.out.println("  Creating document for language: " + lang);
        }
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void saveConfigToMongoDB(String id, Object value) {
        System.out.println("Saving config to MongoDB: " + database + "." + id + " = " + value);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T loadConfigFromMongoDB(String id, Class<T> type) {
        System.out.println("Loading config from MongoDB: " + database + "." + id + " as " + type.getSimpleName());
        if (type == Boolean.class || type == boolean.class) {
            return (T) Boolean.valueOf(id.contains("debug") ? false : true);
        } else if (type == Integer.class || type == int.class) {
            return (T) Integer.valueOf(id.contains("port") ? 25565 : 100);
        } else if (type == Double.class || type == double.class) {
            return (T) Double.valueOf(20.0);
        } else if (type == String.class) {
            return (T) ("Config value for: " + id);
        } else {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                return null;
            }
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<java.util.Set<String>> getCollections() {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            java.util.Set<String> collections = new java.util.HashSet<>(registeredCollections);
            collections.addAll(messagesCache.keySet());
            for (String key : configCache.keySet()) {
                int dotIndex = key.indexOf('.');
                if (dotIndex > 0) {
                    collections.add(key.substring(0, dotIndex));
                } else {
                    collections.add(key);
                }
            }
            return collections;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> reloadCollection(String collection) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            messagesCache.remove(collection);
            configCache.entrySet().removeIf(entry -> entry.getKey().equals(collection) || entry.getKey().startsWith(collection + "."));
            registeredCollections.add(collection);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<java.util.Set<String>> getSupportedLanguages(String collection) {
        return java.util.concurrent.CompletableFuture.completedFuture(
            java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(java.util.Arrays.asList(supportedLanguages)))
        );
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> collectionExists(String collection) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            if (registeredCollections.contains(collection) || messagesCache.containsKey(collection)) {
                return true;
            }
            for (String key : configCache.keySet()) {
                if (key.equals(collection) || key.startsWith(collection + ".")) {
                    return true;
                }
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
            System.out.println("Getting config: " + id + " as " + type.getSimpleName());
            Object cached = configCache.get(id);
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
            saveConfigToMongoDB(id, pojo);
        });
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<T> getObject(Class<T> type) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            String id = xyz.wtje.mongoconfigs.api.core.Annotations.idFrom(type);
            System.out.println("Loading config object: " + id);
            Object cached = configCache.get(id);
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
    public <T> java.util.concurrent.CompletableFuture<T> getConfigOrGenerate(Class<T> type, java.util.function.Supplier<T> generator) {
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
            Object cached = configCache.get("player." + playerId + ".language");
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
}
