package xyz.wtje.mongoconfigs.core.model;

import org.bson.Document;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

public class LanguageDocument {
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
        LanguageDocument out = new LanguageDocument();
        out.lang = doc.getString("lang");
        out.updatedAt = doc.getDate("updatedAt");
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, Object> e : doc.entrySet()) {
            String k = e.getKey();
            if ("_id".equals(k) || "lang".equals(k) || "updatedAt".equals(k)) continue;
            map.put(k, e.getValue());
        }
        out.data = map;
        return out;
    }

    public Document toDocument() {
        Document doc = new Document();
        doc.put("lang", lang);
        doc.put("updatedAt", updatedAt);
        if (data != null) {
            for (java.util.Map.Entry<String, Object> e : data.entrySet()) {
                doc.put(e.getKey(), e.getValue());
            }
        }
        return doc;
    }

    public void updateTimestamp() {
        this.updatedAt = Date.from(Instant.now());
    }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "LanguageDocument{" +
                "lang='" + lang + '\'' +
                ", data=" + data +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
