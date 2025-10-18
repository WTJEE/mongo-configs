package xyz.wtje.mongoconfigs.velocity;

import org.junit.jupiter.api.Test;
import xyz.wtje.mongoconfigs.velocity.util.VelocityColorProcessor;

import static org.junit.jupiter.api.Assertions.*;

public class VelocityColorProcessorTest {

    @Test
    void colorize_basicLegacy() {
        VelocityColorProcessor p = new VelocityColorProcessor();
        String in = "&aHello &lworld";
        String out = p.colorize(in);
        assertNotNull(out);
        assertFalse(out.isEmpty());
    }

    @Test
    void strip_basic() {
        VelocityColorProcessor p = new VelocityColorProcessor();
        String in = "<bold>Hello</bold>";
        String out = p.stripColors(in);
        assertEquals("Hello", out);
    }
}
