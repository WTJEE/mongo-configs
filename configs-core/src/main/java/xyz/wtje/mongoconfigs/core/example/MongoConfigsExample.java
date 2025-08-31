package xyz.wtje.mongoconfigs.core.example;

import xyz.wtje.mongoconfigs.core.config.MongoConfig;
import xyz.wtje.mongoconfigs.core.impl.ConfigManagerImpl;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Example class demonstrating the MongoDB Configs library usage.
 * This can be used to verify that the _id field immutability issue has been resolved.
 */
public class MongoConfigsExample {
    
    private static final Logger LOGGER = Logger.getLogger(MongoConfigsExample.class.getName());
    
    public static void main(String[] args) {
        // Example configuration - replace with your actual MongoDB connection details
        MongoConfig config = new MongoConfig.Builder()
                .connectionString("mongodb://localhost:27017")
                .database("test_mongo_configs")
                .maxPoolSize(10)
                .build();
        
        ConfigManagerImpl configManager = new ConfigManagerImpl(config);
        
        try {
            // Initialize the config manager
            configManager.initialize();
            
            // Example: Create a collection with languages
            String collectionName = "example_config";
            Set<String> languages = Set.of("en", "es", "fr");
            
            LOGGER.info("Creating collection: " + collectionName);
            configManager.createCollection(collectionName, languages).join();
            
            // Example: Set configuration values (this would previously fail with _id immutability error)
            LOGGER.info("Setting configuration values...");
            configManager.setConfig(collectionName, "server.port", 8080).join();
            configManager.setConfig(collectionName, "server.host", "localhost").join();
            configManager.setConfig(collectionName, "database.timeout", 30000).join();
            
            // Example: Set messages in different languages
            LOGGER.info("Setting messages...");
            configManager.setMessage(collectionName, "en", "welcome.message", "Welcome to our server!").join();
            configManager.setMessage(collectionName, "es", "welcome.message", "¡Bienvenido a nuestro servidor!").join();
            configManager.setMessage(collectionName, "fr", "welcome.message", "Bienvenue sur notre serveur!").join();
            
            // Example: Update existing configuration (this would trigger the replaceOne operation)
            LOGGER.info("Updating existing configuration...");
            configManager.setConfig(collectionName, "server.port", 9090).join();
            configManager.setConfig(collectionName, "server.maxConnections", 100).join();
            
            // Example: Retrieve configuration values
            LOGGER.info("Retrieving configuration values...");
            Integer port = configManager.getConfig(collectionName, "server.port", 8080);
            String host = configManager.getConfig(collectionName, "server.host", "localhost");
            
            LOGGER.info("Server configuration - Port: " + port + ", Host: " + host);
            
            // Example: Retrieve messages
            String welcomeEn = configManager.getMessage(collectionName, "en", "welcome.message");
            String welcomeEs = configManager.getMessage(collectionName, "es", "welcome.message");
            String welcomeFr = configManager.getMessage(collectionName, "fr", "welcome.message");
            
            LOGGER.info("Welcome messages:");
            LOGGER.info("  English: " + welcomeEn);
            LOGGER.info("  Spanish: " + welcomeEs);
            LOGGER.info("  French: " + welcomeFr);
            
            LOGGER.info("Example completed successfully! The _id immutability issue has been resolved.");
            
        } catch (Exception e) {
            LOGGER.severe("Example failed with error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up
            configManager.shutdown();
        }
    }
}