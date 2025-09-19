package xyz.wtje.mongoconfigs.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CachedMessagesTest {

    @Test
    void viewProvidesSynchronousAccess() {
        CachedMessages messages = new CachedMessages("example", "en", new String[]{"en", "pl"});
        messages.replaceDefaults(Map.of(
                "general.welcome", "Welcome {0}",
                "general.lines", List.of("Line 1", "Line 2")
        ));

        Messages.View view = messages.view();
        assertEquals("Welcome Steve", view.format("general.welcome", "Steve"));
        assertEquals(List.of("Line 1", "Line 2"), view.list("general.lines"));
    }

    @Test
    void missingMessagesFallbackToLanguageAwareString() {
        CachedMessages messages = new CachedMessages("example", "en", new String[]{"en"});

        Messages.View polishView = messages.view("pl");
        assertEquals("Missing message: unknown for language: pl", polishView.get("unknown"));
        assertEquals(List.of("Missing message: entries for language: pl"), polishView.list("entries"));
    }
}
