package xyz.wtje.mongoconfigs.core.model;

import org.bson.Document;

import java.util.Date;

public class PlayerLanguageDocument {

    private String uuid;
    private String language;
    private Date updatedAt;

    public PlayerLanguageDocument() {
    }

    public PlayerLanguageDocument(String uuid, String language) {
        this.uuid = uuid;
        this.language = language;
        this.updatedAt = new Date();
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void updateTimestamp() {
        this.updatedAt = new Date();
    }

    public Document toDocument() {
        Document doc = new Document();
        doc.put("_id", uuid);
        doc.put("language", language);
        doc.put("updatedAt", updatedAt);
        return doc;
    }

    public static PlayerLanguageDocument fromDocument(Document doc) {
        if (doc == null) return null;

        PlayerLanguageDocument playerLang = new PlayerLanguageDocument();
        playerLang.uuid = doc.getString("_id");
        playerLang.language = doc.getString("language");
        playerLang.updatedAt = doc.getDate("updatedAt");
        return playerLang;
    }
}
