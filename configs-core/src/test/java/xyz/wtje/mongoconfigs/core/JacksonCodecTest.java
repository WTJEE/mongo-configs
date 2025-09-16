package xyz.wtje.mongoconfigs.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.wtje.mongoconfigs.core.model.TypedDocument;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JacksonCodecTest {

    private JacksonCodec codec;

    @BeforeEach
    void setUp() {
        codec = new JacksonCodec();
    }

    @Test
    void testToDocumentWithSimpleObject() {
        TestPojo pojo = new TestPojo();
        pojo.name = "test-name";
        pojo.value = 42;

        String id = "test-id";
        String type = "TestPojo";
        int version = 1;

        org.bson.Document document = codec.toDocument(pojo, id, type, version);

        assertNotNull(document);
        assertEquals(id, document.getString("_id"));
        assertEquals(type, document.getString("_type"));
        assertEquals(version, document.getInteger("_version"));
        assertEquals(pojo.name, document.getString("name"));
        assertEquals(pojo.value, document.getInteger("value"));
        assertNotNull(document.get("updatedAt"));
        assertTrue(document.get("updatedAt") instanceof Instant);
    }

    @Test
    void testToPojoFromDocument() {
        org.bson.Document document = new org.bson.Document();
        document.put("_id", "test-id");
        document.put("_type", "TestPojo");
        document.put("_version", 1);
        document.put("name", "test-name");
        document.put("value", 42);
        document.put("updatedAt", Instant.now());

        TestPojo pojo = codec.toPojo(document, TestPojo.class);

        assertNotNull(pojo);
        assertEquals("test-name", pojo.name);
        assertEquals(42, pojo.value);
    }

    @Test
    void testToJsonSerialization() {
        TestPojo pojo = new TestPojo();
        pojo.name = "test-name";
        pojo.value = 42;

        String json = codec.toJson(pojo);

        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"test-name\""));
        assertTrue(json.contains("\"value\":42"));
    }

    @Test
    void testFromJsonDeserialization() {
        String json = "{\"name\":\"test-name\",\"value\":42}";

        TestPojo pojo = codec.fromJson(json, TestPojo.class);

        assertNotNull(pojo);
        assertEquals("test-name", pojo.name);
        assertEquals(42, pojo.value);
    }

    @Test
    void testToJsonWithError() {
        Object problematicObject = new Object() {
            public Object getSelf() {
                return this;
            }
        };

        assertThrows(RuntimeException.class, () -> codec.toJson(problematicObject));
    }

    @Test
    void testFromJsonWithError() {
        String invalidJson = "{invalid json}";

        assertThrows(RuntimeException.class, () -> codec.fromJson(invalidJson, TestPojo.class));
    }

    @Test
    void testWithComplexObject() {
        ComplexPojo pojo = new ComplexPojo();
        pojo.id = "complex-id";
        pojo.metadata = new HashMap<>();
        pojo.metadata.put("key1", "value1");
        pojo.metadata.put("key2", 123);
        pojo.createdAt = Date.from(Instant.now());

        org.bson.Document document = codec.toDocument(pojo, "doc-id", "ComplexPojo", 2);

        assertNotNull(document);
        assertEquals("doc-id", document.getString("_id"));
        assertEquals("ComplexPojo", document.getString("_type"));
        assertEquals(2, document.getInteger("_version"));
        assertEquals(pojo.id, document.getString("id"));
        assertNotNull(document.get("metadata"));
        assertNotNull(document.get("createdAt"));
        assertNotNull(document.get("updatedAt"));
    }

    @Test
    void testRoundTripConversion() {
        TestPojo original = new TestPojo();
        original.name = "round-trip-test";
        original.value = 999;

        org.bson.Document document = codec.toDocument(original, "rt-id", "TestPojo", 1);
        TestPojo converted = codec.toPojo(document, TestPojo.class);

        assertNotNull(converted);
        assertEquals(original.name, converted.name);
        assertEquals(original.value, converted.value);
    }

    @Test
    void testWithNullValues() {
        TestPojo pojo = new TestPojo();
        pojo.name = null;
        pojo.value = 0;

        org.bson.Document document = codec.toDocument(pojo, "null-test", "TestPojo", 1);

        assertNotNull(document);
        assertNull(document.get("name"));
        assertEquals(0, document.getInteger("value"));
    }

    static class TestPojo {
        public String name;
        public int value;
    }

    static class ComplexPojo {
        public String id;
        public Map<String, Object> metadata;
        public Date createdAt;
    }
}

