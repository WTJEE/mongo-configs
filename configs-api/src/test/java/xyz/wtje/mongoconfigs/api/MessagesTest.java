package xyz.wtje.mongoconfigs.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagesTest {

    @Mock
    private Messages messages;

    

    

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
            public java.util.concurrent.CompletableFuture<String> get(String path) {
                return java.util.concurrent.CompletableFuture.completedFuture(path);
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> get(String path, String language) {
                String value = data.getOrDefault(language, Map.of()).getOrDefault(path, path);
                return java.util.concurrent.CompletableFuture.completedFuture(value);
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> get(String path, Object... placeholders) {
                return java.util.concurrent.CompletableFuture.completedFuture(path);
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> get(String path, String language, Object... placeholders) {
                String template = data.getOrDefault(language, Map.of()).getOrDefault(path, path);
                String res = template;
                if (placeholders != null) {
                    for (int i = 0; i < placeholders.length; i++) {
                        res = res.replace("{" + i + "}", String.valueOf(placeholders[i]));
                    }
                }
                return java.util.concurrent.CompletableFuture.completedFuture(res);
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.util.List<String>> getList(String path) {
                return java.util.concurrent.CompletableFuture.completedFuture(java.util.Collections.emptyList());
            }

            @Override
            public java.util.concurrent.CompletableFuture<java.util.List<String>> getList(String path, String language) {
                return java.util.concurrent.CompletableFuture.completedFuture(java.util.Collections.emptyList());
            }
        };

        assertEquals("Hello", concreteMessages.get("greeting", "en").join());
        assertEquals("Cześć", concreteMessages.get("greeting", "pl").join());
        assertEquals("Goodbye", concreteMessages.get("farewell", "en").join());
    }
}

