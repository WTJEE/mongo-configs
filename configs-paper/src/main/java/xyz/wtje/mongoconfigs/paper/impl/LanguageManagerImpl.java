package xyz.wtje.mongoconfigs.paper.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;
import xyz.wtje.mongoconfigs.api.ConfigManager;
import xyz.wtje.mongoconfigs.api.LanguageManager;
import xyz.wtje.mongoconfigs.core.model.PlayerLanguageDocument;
import xyz.wtje.mongoconfigs.core.mongo.MongoManager;
import xyz.wtje.mongoconfigs.core.mongo.PublisherAdapter;
import xyz.wtje.mongoconfigs.paper.config.LanguageConfiguration;
import xyz.wtje.mongoconfigs.paper.gui.LanguageSelectionGUI;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class LanguageManagerImpl implements LanguageManager {

    private static final Logger LOGGER = Logger.getLogger(LanguageManagerImpl.class.getName());

    private final ConfigManager configManager;
    private final LanguageConfiguration config;
    private final Cache<String, String> playerLanguagesCache;
    private final MongoManager mongoManager;
    private final String collectionName;
    private final String databaseName;

    public LanguageManagerImpl(ConfigManager configManager, LanguageConfiguration config, MongoManager mongoManager) {
        this(configManager, config, mongoManager, config.getPlayerLanguagesDatabase(), config.getPlayerLanguagesCollection());
    }

    public LanguageManagerImpl(ConfigManager configManager, LanguageConfiguration config, MongoManager mongoManager, String collectionName) {
        this(configManager, config, mongoManager, config.getPlayerLanguagesDatabase(), collectionName);
    }

    public LanguageManagerImpl(ConfigManager configManager, LanguageConfiguration config, MongoManager mongoManager, String databaseName, String collectionName) {
        this.configManager = configManager;
        this.config = config;
        this.mongoManager = mongoManager;
        this.databaseName = databaseName;
        this.collectionName = collectionName;

        this.playerLanguagesCache = Caffeine.newBuilder()
                .maximumSize(50000)
                .expireAfterWrite(Duration.ofHours(6))
                .expireAfterAccess(Duration.ofHours(2))
                .recordStats()
                .build();
    }

    public void initialize() {
        if (!databaseName.equals(mongoManager.getDatabase().getName())) {
            MongoCollection<Document> collection = mongoManager.getCollection(databaseName, collectionName);
            LOGGER.info("Player languages will be stored in database: " + databaseName + ", collection: " + collectionName);
        } else {
            mongoManager.collectionExists(collectionName).thenCompose(exists -> {
                if (!exists) {
                    LOGGER.info("Creating player languages collection: " + collectionName);
                    return mongoManager.createCollection(collectionName);
                } else {
                    LOGGER.info("Player languages collection already exists: " + collectionName);
                    return CompletableFuture.completedFuture(null);
                }
            }).thenRun(() -> {
                LOGGER.info("Player languages collection ready: " + collectionName);
            }).exceptionally(throwable -> {
                LOGGER.warning("Error initializing player languages collection: " + throwable.getMessage());
                return null;
            });
        }

        LOGGER.info("LanguageManager initialized");
    }

    public void shutdown() {
        LOGGER.info("LanguageManager shutdown");
    }

    public void reload() {
        playerLanguagesCache.invalidateAll();
        LanguageSelectionGUI.clearCache();
        LOGGER.info("LanguageManager reloaded - cache cleared");
    }

    @Override
    public CompletableFuture<String> getPlayerLanguage(String playerId) {
        return getPlayerLanguageAsync(playerId);
    }

    public CompletableFuture<String> getPlayerLanguageAsync(String playerId) {
        String cached = playerLanguagesCache.getIfPresent(playerId);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return CompletableFuture.supplyAsync(() -> {
            try {
                MongoCollection<Document> collection = mongoManager.getCollection(databaseName, collectionName);
                Document doc = PublisherAdapter.toCompletableFuture(
                    collection.find(Filters.eq("_id", playerId)).first()
                ).join();
                String lang = doc != null ? doc.getString("language") : config.getDefaultLanguage();
                playerLanguagesCache.put(playerId, lang);
                return lang;
            } catch (Exception e) {
                LOGGER.warning("Error loading player language for " + playerId + ": " + e.getMessage());
                return config.getDefaultLanguage();
            }
        });
    }

    @Override
    public CompletableFuture<Void> setPlayerLanguage(String playerId, String language) {
        return setPlayerLanguageAsync(playerId, language);
    }

    public CompletableFuture<Void> setPlayerLanguage(java.util.UUID playerId, String language) {
        return setPlayerLanguageAsync(playerId.toString(), language);
    }

    public CompletableFuture<Void> setPlayerLanguageAsync(String playerId, String language) {
        return isLanguageSupported(language).thenCompose(supported -> {
            if (!supported) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Unsupported language: " + language));
            }

            String currentLanguage = playerLanguagesCache.getIfPresent(playerId);
            if (language.equals(currentLanguage)) {
                LOGGER.fine("Language already set for player " + playerId + ": " + language + ", skipping update");
                return CompletableFuture.completedFuture(null);
            }

            playerLanguagesCache.put(playerId, language);
            LOGGER.fine("Updated language for player " + playerId + " to " + language);

            return CompletableFuture.runAsync(() -> {
                try {
                    MongoCollection<Document> collection = mongoManager.getCollection(databaseName, collectionName);
                    Document currentDoc = PublisherAdapter.toCompletableFuture(
                        collection.find(Filters.eq("_id", playerId)).first()
                    ).join();

                    String currentDbLanguage = currentDoc != null ? currentDoc.getString("language") : null;
                    if (language.equals(currentDbLanguage)) {
                        LOGGER.fine("Language already set in database for player " + playerId + ": " + language + ", skipping DB update");
                        return;
                    }

                    PlayerLanguageDocument doc = new PlayerLanguageDocument(playerId, language);

                    PublisherAdapter.toCompletableFuture(
                        collection.replaceOne(
                            Filters.eq("_id", playerId),
                            doc.toDocument(),
                            new ReplaceOptions().upsert(true)
                        )
                    ).join();

                    LOGGER.fine("Saved language preference for " + playerId + ": " + language);
                } catch (Exception e) {
                    LOGGER.warning("Error saving player language: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        });
    }

    @Override
    public CompletableFuture<String> getDefaultLanguage() {
        return CompletableFuture.completedFuture(config.getDefaultLanguage());
    }

    @Override
    public CompletableFuture<String[]> getSupportedLanguages() {
        return CompletableFuture.completedFuture(config.getSupportedLanguages().toArray(new String[0]));
    }

    @Override
    public CompletableFuture<Boolean> isLanguageSupported(String language) {
        return CompletableFuture.completedFuture(config.getSupportedLanguages().contains(language));
    }

    public void clearCache() {
        playerLanguagesCache.invalidateAll();
        LOGGER.info("Cleared all cached player languages");
    }

    @Override
    public CompletableFuture<String> getLanguageDisplayName(String language) {
        try {
            Map<String, String> displayNames = config.getLanguageDisplayNames();
            String displayName = displayNames.get(language);

            if (displayName == null) {
                return CompletableFuture.completedFuture(language);
            }

            if (isBase64Encoded(displayName)) {
                try {
                    displayName = new String(Base64.getDecoder().decode(displayName));
                } catch (Exception e) {
                    LOGGER.warning("Failed to decode base64 display name for language: " + language);
                }
            }

            return CompletableFuture.completedFuture(displayName);
        } catch (Exception e) {
            LOGGER.warning("Error resolving display name for " + language + ": " + e.getMessage());
            return CompletableFuture.completedFuture(language);
        }
    }

    private boolean isBase64Encoded(String str) {
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

