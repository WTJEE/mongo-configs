package xyz.wtje.mongoconfigs.api.core;

import org.junit.jupiter.api.Test;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsCollection;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsDatabase;
import xyz.wtje.mongoconfigs.api.annotations.ConfigsFileProperties;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationsTest {

    @Test
    void testIdFromWithConfigsFileProperties() {
        String result = Annotations.idFrom(TestConfigWithFileProperties.class);
        assertEquals("test-config", result);
    }

    @Test
    void testIdFromWithoutAnnotation() {
        assertThrows(IllegalStateException.class, () -> {
            Annotations.idFrom(TestConfigWithoutAnnotation.class);
        });
    }

    @Test
    void testDatabaseFromWithConfigsDatabase() {
        String result = Annotations.databaseFrom(TestConfigWithDatabase.class);
        assertEquals("test-database", result);
    }

    @Test
    void testDatabaseFromWithoutAnnotation() {
        String result = Annotations.databaseFrom(TestConfigWithoutAnnotation.class);
        assertNull(result);
    }

    @Test
    void testCollectionFromWithConfigsCollection() {
        String result = Annotations.collectionFrom(TestConfigWithCollection.class);
        assertEquals("test-collection", result);
    }

    @Test
    void testCollectionFromWithConfigsFileProperties() {
        String result = Annotations.collectionFrom(TestConfigWithFileProperties.class);
        assertEquals("test-config", result);
    }

    @Test
    void testCollectionFromWithoutAnnotation() {
        assertThrows(IllegalStateException.class, () -> {
            Annotations.collectionFrom(TestConfigWithoutAnnotation.class);
        });
    }

    @Test
    void testCollectionFromWithBothAnnotations() {
        String result = Annotations.collectionFrom(TestConfigWithBothAnnotations.class);
        assertEquals("specific-collection", result);
    }

    @Test
    void testAnnotationsWithNullClass() {
        assertThrows(NullPointerException.class, () -> Annotations.idFrom(null));
        assertThrows(NullPointerException.class, () -> Annotations.databaseFrom(null));
        assertThrows(NullPointerException.class, () -> Annotations.collectionFrom(null));
    }

    @ConfigsFileProperties(name = "test-config")
    static class TestConfigWithFileProperties {
    }

    @ConfigsDatabase("test-database")
    static class TestConfigWithDatabase {
    }

    @ConfigsCollection("test-collection")
    static class TestConfigWithCollection {
    }

    @ConfigsFileProperties(name = "test-config")
    @ConfigsCollection("specific-collection")
    static class TestConfigWithBothAnnotations {
    }

    static class TestConfigWithoutAnnotation {
    }
}
