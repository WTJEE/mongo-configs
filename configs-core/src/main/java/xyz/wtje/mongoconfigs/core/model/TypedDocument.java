package xyz.wtje.mongoconfigs.core.model;

import org.bson.Document;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TypedDocument {
    private String id; // stored as _id in Mongo
    private Map<String, Object> fields;
    private Date updatedAt;
    private long version;

    public TypedDocument() {
        this.updatedAt = Date.from(Instant.now());
        this.version = 1L;
        this.fields = new HashMap<>();
    }

    public TypedDocument(String id, Map<String, Object> fields) {
        this.id = id;
        this.fields = fields != null ? new HashMap<>(fields) : new HashMap<>();
        this.updatedAt = Date.from(Instant.now());
        this.version = 1L;
    }

    public static TypedDocument fromDocument(Document doc) {
        TypedDocument out = new TypedDocument();
        out.id = doc.getString("_id");
        out.updatedAt = doc.getDate("updatedAt");
        out.version = doc.getLong("version") != null ? doc.getLong("version") : 1L;
        Map<String, Object> f = new HashMap<>();
        for (Map.Entry<String, Object> e : doc.entrySet()) {
            String k = e.getKey();
            if ("_id".equals(k) || "updatedAt".equals(k) || "version".equals(k)) {
                continue;
            }
            f.put(k, e.getValue());
        }
        out.fields = f;
        return out;
    }

    public Document toDocument() {
        Document doc = new Document();
        doc.put("_id", id);
        doc.put("updatedAt", updatedAt);
        doc.put("version", version);
        if (fields != null) {
            for (Map.Entry<String, Object> e : fields.entrySet()) {
                doc.put(e.getKey(), e.getValue());
            }
        }
        return doc;
    }

    public void updateTimestamp() {
        this.updatedAt = Date.from(Instant.now());
        this.version++;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Map<String, Object> getFields() { return fields; }
    public void setFields(Map<String, Object> fields) { this.fields = fields; }

    public Map<String, Object> getData() { return fields; } // Alias for getFields for compatibility

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
