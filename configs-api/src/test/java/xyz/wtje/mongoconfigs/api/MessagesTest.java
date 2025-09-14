package xyz.wtje.mongoconfigs.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagesTest {

    @Mock
    private Messages messages;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testGetWithType() {
        String lang = "en";
        String key = "test.key";
        String expectedValue = "Test Value";

    when(messages.get(lang, key, String.class)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(expectedValue));

    String result = messages.get(lang, key, String.class).join();

        assertEquals(expectedValue, result);
        verify(messages).get(lang, key, String.class);
    }

    @Test
    void testGetWithVarArgs() {
        String lang = "en";
        String key = "greeting.message";
        String expectedValue = "Hello, John!";
        Object[] placeholders = {"John"};

    when(messages.get(lang, key, placeholders)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(expectedValue));

    String result = messages.get(lang, key, placeholders).join();

        assertEquals(expectedValue, result);
        verify(messages).get(lang, key, placeholders);
    }

    @Test
    void testGetWithMap() {
        String lang = "en";
        String key = "welcome.message";
        String expectedValue = "Welcome, John! You have 5 messages.";
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("name", "John");
        placeholders.put("count", 5);

    when(messages.get(lang, key, placeholders)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(expectedValue));

    String result = messages.get(lang, key, placeholders).join();

        assertEquals(expectedValue, result);
        verify(messages).get(lang, key, placeholders);
    }

    @Test
    void testGetWithEmptyPlaceholders() {
        String lang = "en";
        String key = "simple.message";
        String expectedValue = "Simple message";

    when(messages.get(lang, key)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(expectedValue));

    String result = messages.get(lang, key).join();

        assertEquals(expectedValue, result);
        verify(messages).get(lang, key);
    }

    @Test
    void testGetWithNullPlaceholders() {
        String lang = "en";
        String key = "null.test";
        String expectedValue = "Null test";
        Object[] nullArray = null;

    when(messages.get(lang, key, nullArray)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(expectedValue));

    String result = messages.get(lang, key, nullArray).join();

        assertEquals(expectedValue, result);
        verify(messages).get(lang, key, nullArray);
    }

    @Test
    void testDifferentLanguages() {
        String englishKey = "hello";
        String polishKey = "hello";
        String englishValue = "Hello";
        String polishValue = "Cześć";

    when(messages.get("en", englishKey, String.class)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(englishValue));
    when(messages.get("pl", polishKey, String.class)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(polishValue));

    String englishResult = messages.get("en", englishKey, String.class).join();
    String polishResult = messages.get("pl", polishKey, String.class).join();

        assertEquals(englishValue, englishResult);
        assertEquals(polishValue, polishResult);

    verify(messages).get("en", englishKey, String.class);
    verify(messages).get("pl", polishKey, String.class);
    }

    @Test
    void testConcreteImplementation() {
        Messages concreteMessages = new Messages() {
            private final Map<String, Map<String, String>> data = Map.of(
                "en", Map.of("greeting", "Hello", "farewell", "Goodbye"),
                "pl", Map.of("greeting", "Cześć", "farewell", "Do widzenia")
            );

            @Override
            public <T> java.util.concurrent.CompletableFuture<T> get(String lang, String key, Class<T> type) {
                String value = data.getOrDefault(lang, Map.of()).getOrDefault(key, key);
                return java.util.concurrent.CompletableFuture.completedFuture(type.cast(value));
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> get(String lang, String key, Object... placeholders) {
                String template = data.getOrDefault(lang, Map.of()).getOrDefault(key, key);
                String res;
                if (placeholders != null && placeholders.length > 0) {
                    res = String.format(template, placeholders);
                } else {
                    res = template;
                }
                return java.util.concurrent.CompletableFuture.completedFuture(res);
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> get(String lang, String key, Map<String, Object> placeholders) {
                String template = data.getOrDefault(lang, Map.of()).getOrDefault(key, key);
                String result = template;
                if (placeholders != null && !placeholders.isEmpty()) {
                    for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
                        result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
                    }
                }
                return java.util.concurrent.CompletableFuture.completedFuture(result);
            }
        };

        assertEquals("Hello", concreteMessages.get("en", "greeting", String.class).join());
        assertEquals("Cześć", concreteMessages.get("pl", "greeting", String.class).join());
        assertEquals("Do widzenia", concreteMessages.get("pl", "farewell").join());

        Map<String, Object> placeholders = Map.of("name", "John");
        String result = concreteMessages.get("en", "greeting", placeholders).join();
        assertNotNull(result);
    }
}
