package xyz.wtje.mongoconfigs.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;
import xyz.wtje.mongoconfigs.api.annotations.SupportedLanguages;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LanguageClassRetrievalTest {

    @Mock
    private ConfigManager manager;

    @Test
    void getLanguageClassReturnsDefaultInstance() {        TestMessages messages = new TestMessages();
        when(manager.getLanguageClass(TestMessages.class, "en"))
                .thenReturn(CompletableFuture.completedFuture(messages));

        TestMessages result = manager.getLanguageClass(TestMessages.class, "en").join();
        assertNotNull(result);
        assertNotNull(result.general);
        assertEquals("Hello", result.general.greeting);
    }

    @Test
    void getLanguageClassesReturnsAllSupportedLanguages() {
        TestMessages enMessages = new TestMessages();
        TestMessages plMessages = new TestMessages();
        plMessages.general.greeting = "Cześć";

        Map<String, TestMessages> classesMap = Map.of("en", enMessages, "pl", plMessages);
        when(manager.getLanguageClasses(TestMessages.class))
                .thenReturn(CompletableFuture.completedFuture(classesMap));

        Map<String, TestMessages> classes = manager.getLanguageClasses(TestMessages.class).join();
        assertTrue(classes.containsKey("en"));
        assertTrue(classes.containsKey("pl"));
        assertNotSame(classes.get("en"), classes.get("pl"));
        assertEquals("Hello", classes.get("en").general.greeting);
    }

    @ConfigsFileProperties(name = "test-language-class")
    @SupportedLanguages({ "en", "pl" })
    static class TestMessages {
        public General general = new General();

        public static class General {
            public String greeting = "Hello";
        }
    }
}
