package xyz.wtje.mongoconfigs.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.wtje.mongoconfigs.api.Messages;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;

import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class MessagePlaceholdersTest {
    
    @Test
    void testPlaceholderFormats() {
        
        String message = "Hello {player}! Welcome to {world}. You have {money} coins.";
        
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("player", "TestPlayer");
        placeholders.put("world", "Overworld");
        placeholders.put("money", 1000);
        
        String expected = "Hello TestPlayer! Welcome to Overworld. You have 1000 coins.";
        
        
        String result = replacePlaceholders(message, placeholders);
        assertEquals(expected, result);
    }
    
    @Test
    void testLanguagePlaceholder() {
        String message = "Your language is {lang}";
        Map<String, Object> placeholders = Map.of("lang", "en");
        
        String result = replacePlaceholders(message, placeholders);
        assertEquals("Your language is en", result);
    }
    
    @Test
    void testCustomPlaceholders() {
        String message = "Item {item} costs {price} coins. Discount: {discount}%";
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("item", "Diamond Sword");
        placeholders.put("price", 500);
        placeholders.put("discount", 10);
        
        String result = replacePlaceholders(message, placeholders);
        assertEquals("Item Diamond Sword costs 500 coins. Discount: 10%", result);
    }
    
    @Test
    void testEmptyPlaceholders() {
        String message = "No placeholders here!";
        Map<String, Object> placeholders = new HashMap<>();
        
        String result = replacePlaceholders(message, placeholders);
        assertEquals("No placeholders here!", result);
    }
    
    @Test
    void testMissingPlaceholders() {
        String message = "Hello {player}! Missing: {missing}";
        Map<String, Object> placeholders = Map.of("player", "TestPlayer");
        
        String result = replacePlaceholders(message, placeholders);
        assertEquals("Hello TestPlayer! Missing: {missing}", result);
    }
    
    
    private String replacePlaceholders(String message, Map<String, Object> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) return message;
        
        String result = message;
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = String.valueOf(entry.getValue());
            result = result.replace(placeholder, value);
        }
        return result;
    }
}