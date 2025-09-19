package xyz.wtje.mongoconfigs.api;

import org.junit.jupiter.api.Test;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LanguageClassRetrievalTest {

    private final ConfigManager manager = MongoConfigManager.getInstance();

    @Test
    void getLanguageClassReturnsDefaultInstance() {
        TestMessages messages = manager.getLanguageClass(TestMessages.class, "en").join();
        assertNotNull(messages);
        assertNotNull(messages.general);
        assertEquals("Hello", messages.general.greeting);
    }

    @Test
    void getLanguageClassesReturnsAllSupportedLanguages() {
        Map<String, TestMessages> classes = manager.getLanguageClasses(TestMessages.class).join();
        assertTrue(classes.containsKey("en"));
        assertTrue(classes.containsKey("pl"));
        assertNotSame(classes.get("en"), classes.get("pl"));
        assertEquals("Hello", classes.get("en").general.greeting);
    }

    @ConfigsFileProperties(name = "test-language-class")
    @SupportedLanguages({"en", "pl"})
    static class TestMessages {
        public General general = new General();

        public static class General {
            public String greeting = "Hello";
        }
    }
}
