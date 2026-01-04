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
        } else if (idObj instanceof String strId) {
            // Nie próbuj parsować "config" lub nazwy kolekcji jako ObjectId
            if (!"config".equals(strId)) {
                try {
                    config.id = new ObjectId(strId);
                } catch (IllegalArgumentException e) {
                    // To jest prawdopodobnie nazwa kolekcji, nie ObjectId
                    config.id = null;
                }
            }
        }

        config.name = doc.getString("name");
        
        // Sprawdź czy dane są w polu "data" (tradycyjny format) 
        // czy bezpośrednio w dokumencie (flat format)
        Document dataDoc = doc.get("data", Document.class);
        if (dataDoc != null && !dataDoc.isEmpty()) {
            // Tradycyjny format: dane w polu "data"
            config.data = new HashMap<>(dataDoc);
        } else {
            // Flat format: dane bezpośrednio w dokumencie
            // Kopiuj wszystko oprócz pól systemowych
            config.data = new HashMap<>();
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                String key = entry.getKey();
                // Pomijaj pola systemowe
                if ("_id".equals(key) || "name".equals(key) || "updatedAt".equals(key) || 
                    "_type".equals(key) || "_version".equals(key) || "data".equals(key)) {
                    continue;
                }
                config.data.put(key, entry.getValue());
            }
        }
        
        // Obsłuż updatedAt zapisane jako różne typy
        Object updatedRaw = doc.get("updatedAt");
        if (updatedRaw instanceof Date) {
            config.updatedAt = (Date) updatedRaw;
        } else if (updatedRaw instanceof Long) {
            config.updatedAt = new Date((Long) updatedRaw);
        } else if (updatedRaw instanceof String strDate) {
            try {
                config.updatedAt = Date.from(Instant.parse(strDate));
            } catch (Exception ignored) {
                config.updatedAt = Date.from(Instant.now());
            }
        } else {
            config.updatedAt = Date.from(Instant.now());
        }
        
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

