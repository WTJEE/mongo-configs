package xyz.wtje.mongoconfigs.core.model;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ConfigDocument {
    private ObjectId id;
    private String name;
    private Map<String, Object> data;
    private Date updatedAt;

    public ConfigDocument() {
        this.data = new HashMap<>();
        this.updatedAt = Date.from(Instant.now());
    }

    public ConfigDocument(String name, Map<String, Object> data) {
        this.name = name;
        setData(data);
        this.updatedAt = Date.from(Instant.now());
    }

    public static ConfigDocument fromDocument(Document doc) {
        ConfigDocument config = new ConfigDocument();

        Object idObj = doc.get("_id");
        if (idObj instanceof ObjectId) {
            config.id = (ObjectId) idObj;
        } else if (idObj instanceof String) {
            if (!"config".equals(idObj)) {
                try {
                    config.id = new ObjectId((String) idObj);
                } catch (IllegalArgumentException e) {
                    config.id = new ObjectId();
                }
            }
        }

        config.name = doc.getString("name");
        Document dataDoc = doc.get("data", Document.class);
        if (dataDoc != null) {
            config.data = new HashMap<>(dataDoc);
        } else {
            config.data = new HashMap<>();
        }
        config.updatedAt = doc.getDate("updatedAt");
        return config;
    }

    public Document toDocument() {
        Document doc = new Document();
        if (id != null) {
            doc.put("_id", id);
        }
        doc.put("name", name);
        Map<String, Object> dataMap = getData();
        doc.put("data", new Document(dataMap));
        doc.put("updatedAt", updatedAt);
        return doc;
    }

    public void updateTimestamp() {
        this.updatedAt = Date.from(Instant.now());
    }

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<String, Object> getData() {
        if (data == null) {
            data = new HashMap<>();
        }
        return data;
    }

    public void setData(Map<String, Object> data) {
        if (data == null) {
            this.data = new HashMap<>();
        } else if (data instanceof Document) {
            this.data = new HashMap<>((Document) data);
        } else {
            this.data = new HashMap<>(data);
        }
    }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "ConfigDocument{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", data=" + data +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

