package xyz.wtje.mongoconfigs.api;

import java.util.Map;
import java.util.Set;

/**
 * Data class for batch collection setup operations
 */
public class CollectionSetupData {
    private final Set<String> languages;
    private final Map<String, Object> configValues;
    private final Map<String, Map<String, String>> languageMessages;
    
    public CollectionSetupData(Set<String> languages, 
                              Map<String, Object> configValues, 
                              Map<String, Map<String, String>> languageMessages) {
        this.languages = languages;
        this.configValues = configValues;
        this.languageMessages = languageMessages;
    }
    
    public Set<String> getLanguages() {
        return languages;
    }
    
    public Map<String, Object> getConfigValues() {
        return configValues;
    }
    
    public Map<String, Map<String, String>> getLanguageMessages() {
        return languageMessages;
    }
    
    /**
     * Builder for easier construction
     */
    public static class Builder {
        private Set<String> languages;
        private Map<String, Object> configValues;
        private Map<String, Map<String, String>> languageMessages;
        
        public Builder languages(Set<String> languages) {
            this.languages = languages;
            return this;
        }
        
        public Builder configValues(Map<String, Object> configValues) {
            this.configValues = configValues;
            return this;
        }
        
        public Builder languageMessages(Map<String, Map<String, String>> languageMessages) {
            this.languageMessages = languageMessages;
            return this;
        }
        
        public CollectionSetupData build() {
            return new CollectionSetupData(languages, configValues, languageMessages);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}