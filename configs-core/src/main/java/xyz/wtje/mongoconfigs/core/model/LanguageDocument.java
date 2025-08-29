package xyz.wtje.mongoconfigs.core.model;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.Date;
import java.util.Map;


public class LanguageDocument {
    private ObjectId id;
    private String lang;
    private Map<String, Object> data;
    private Date updatedAt;
    
    public LanguageDocument() {
        this.updatedAt = Date.from(Instant.now());
    }
    
    public LanguageDocument(String lang, Map<String, Object> data) {
        this.lang = lang;
        this.data = data;
        this.updatedAt = Date.from(Instant.now());
    }
    
    public static LanguageDocument fromDocument(Document doc) {
        LanguageDocument langDoc = new LanguageDocument();
        langDoc.id = doc.getObjectId("_id");
        langDoc.lang = doc.getString("lang");
        langDoc.data = doc.get("data", Document.class);
        langDoc.updatedAt = doc.getDate("updatedAt");
        return langDoc;
    }
    
    public Document toDocument() {
        Document doc = new Document();
        if (id != null) {
            doc.put("_id", id);
        }
        doc.put("lang", lang);
        doc.put("data", data);
        doc.put("updatedAt", updatedAt);
        return doc;
    }
    
    public void updateTimestamp() {
        this.updatedAt = Date.from(Instant.now());
    }
    
    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }
    
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
    
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
    
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    
    @Override
    public String toString() {
        return "LanguageDocument{" +
                "id=" + id +
                ", lang='" + lang + '\'' +
                ", data=" + data +
                ", updatedAt=" + updatedAt +
                '}';
    }
}