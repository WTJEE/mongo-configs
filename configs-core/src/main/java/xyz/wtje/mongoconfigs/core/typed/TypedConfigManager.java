package xyz.wtje.mongoconfigs.core.typed;

import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;
import xyz.wtje.mongoconfigs.core.mongo.MongoManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class TypedConfigManager {
	private final MongoCollection<Document> collection;
	private final MongoManager mongoManager;
	private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

	public TypedConfigManager(MongoCollection<Document> collection, MongoManager mongoManager) {
		this.collection = collection;
		this.mongoManager = mongoManager;
	}

	public <T> CompletableFuture<Void> setObject(T pojo) {
		return CompletableFuture.completedFuture(null); // TODO: implement reflection based storage
	}

	public <T> CompletableFuture<T> getObject(Class<T> type) {
		return CompletableFuture.completedFuture(null);
	}

	public <T> CompletableFuture<Void> setObject(String id, T pojo) {
		return CompletableFuture.completedFuture(null);
	}

	public <T> CompletableFuture<T> getObject(String id, Class<T> type) {
		return CompletableFuture.completedFuture(null);
	}

	public <T> CompletableFuture<Void> set(String id, String key, T value) {
		return CompletableFuture.runAsync(() -> store.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(key, value));
	}

	@SuppressWarnings("unchecked")
	public <T> CompletableFuture<T> get(String id, String key, Class<T> type) {
		return CompletableFuture.supplyAsync(() -> {
			Object v = store.getOrDefault(id, Map.of()).get(key);
			return v != null && type.isInstance(v) ? (T) v : null;
		});
	}

	public <T> CompletableFuture<Void> setObject(String id, T pojo, boolean overwrite) {
		return setObject(id, pojo); // placeholder
	}

	public <T> CompletableFuture<T> getConfigOrGenerate(Class<T> type, Supplier<T> generator) {
		return CompletableFuture.completedFuture(generator.get());
	}
}

