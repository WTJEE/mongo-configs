package xyz.wtje.mongoconfigs.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bson.Document;

import java.time.Instant;
import java.util.Map;

public final class JacksonCodec {

    private final ObjectMapper mapper;

    public JacksonCodec() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Document toDocument(Object pojo, String id, String type, int version) {
        @SuppressWarnings("unchecked")
        var map = mapper.convertValue(pojo, Map.class);
        var doc = new Document(map);
        doc.put("_id", id);
        doc.put("_type", type);
        doc.put("_version", version);
        doc.put("updatedAt", Instant.now());
        return doc;
    }

    public <T> T toPojo(Document doc, Class<T> type) {
        return mapper.convertValue(doc, type);
    }

    public String toJson(Object pojo) {
        try {
            return mapper.writeValueAsString(pojo);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize from JSON", e);
        }
    }
}
