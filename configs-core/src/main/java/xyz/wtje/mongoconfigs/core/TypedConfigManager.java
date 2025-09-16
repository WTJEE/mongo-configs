package xyz.wtje.mongoconfigs.core;

import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;
import xyz.wtje.mongoconfigs.api.core.Annotations;
import xyz.wtje.mongoconfigs.core.mongo.MongoManager;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class TypedConfigManager {

    private final MongoCollection<Document> defaultCollection;
    private final MongoManager mongoManager;
    private final JacksonCodec codec = new JacksonCodec();

    public TypedConfigManager(MongoCollection<Document> defaultCollection, MongoManager mongoManager) {
        this.defaultCollection = defaultCollection;
        this.mongoManager = mongoManager;
    }

    private MongoCollection<Document> getCollectionForType(Class<?> type) {
        try {
            return mongoManager.getCollectionForType(type);
        } catch (Exception e) {
            return defaultCollection;
        }
    }

    public <T> CompletableFuture<Void> set(String id, String key, T value) {
        var update = new Document("$set", new Document(key, value))
                .append("$currentDate", new Document("updatedAt", true))
                .append("$inc", new Document("_version", 1));

        return Asyncs.one(defaultCollection.updateOne(
                new Document("_id", id), 
                update, 
                new UpdateOptions().upsert(true)
        )).thenApply(result -> null);
    }

    public <T> CompletableFuture<T> get(String id, String key, Class<T> type) {
        var projection = new Document(key, 1).append("_id", 0);

        return Asyncs.one(defaultCollection.find(new Document("_id", id))
                .projection(projection)
                .first())
                .thenApply(doc -> {
                    if (doc == null) return null;
                    Object value = doc.get(key);
                    return type.cast(value);
                });
    }

    public <T> CompletableFuture<Void> setObject(T pojo) {
        var id = Annotations.idFrom(pojo.getClass());
        var collection = getCollectionForType(pojo.getClass());
        var document = codec.toDocument(pojo, id, pojo.getClass().getName(), 1);

        return Asyncs.one(collection.replaceOne(
                new Document("_id", id), 
                document, 
                new ReplaceOptions().upsert(true)
        )).thenApply(result -> null);
    }

    public <T> CompletableFuture<T> getObject(Class<T> type) {
        var id = Annotations.idFrom(type);
        var collection = getCollectionForType(type);

        return Asyncs.one(collection.find(new Document("_id", id)).first())
                .thenApply(doc -> doc == null ? null : codec.toPojo(doc, type));
    }

    public <T> CompletableFuture<T> getConfigOrGenerate(Class<T> type, Supplier<T> generator) {
        return getObject(type).thenCompose(current -> {
            if (current != null) {
                return CompletableFuture.completedFuture(current);
            }

            var created = generator.get();
            return setObject(created).thenApply(v -> created);
        });
    }

    public <T> CompletableFuture<Void> setObject(String id, T pojo) {
        var document = codec.toDocument(pojo, id, pojo.getClass().getName(), 1);
        return Asyncs.one(defaultCollection.replaceOne(
                new Document("_id", id),
                document,
                new ReplaceOptions().upsert(true)
        )).thenApply(r -> null);
    }

    public <T> CompletableFuture<T> getObject(String id, Class<T> type) {
        return Asyncs.one(defaultCollection.find(new Document("_id", id)).first())
                .thenApply(doc -> doc == null ? null : codec.toPojo(doc, type));
    }

    public <T> CompletableFuture<Void> set(String id, T value) {
        var doc = codec.toDocument(value, id, value.getClass().getName(), 1);
        return Asyncs.one(defaultCollection.replaceOne(new Document("_id", id), doc, new ReplaceOptions().upsert(true)))
                .thenApply(r -> null);
    }

    public <T> CompletableFuture<T> get(String id, Class<T> type) {
        return Asyncs.one(defaultCollection.find(new Document("_id", id)).first())
                .thenApply(doc -> doc == null ? null : codec.toPojo(doc, type));
    }
}

