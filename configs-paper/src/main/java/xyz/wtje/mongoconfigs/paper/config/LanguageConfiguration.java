package xyz.wtje.mongoconfigs.paper.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LanguageConfiguration {

    private final Plugin plugin;
    private FileConfiguration config;
    private File configFile;

    public LanguageConfiguration(Plugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "languages.yml");
        saveDefaultConfig();
        reloadConfig();
    }

    private void saveDefaultConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("languages.yml", false);
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save languages.yml: " + e.getMessage());
        }
    }

    public String getDefaultLanguage() {
        return config.getString("default", "en");
    }

    public List<String> getSupportedLanguages() {
        return config.getStringList("supported");
    }

    public Map<String, String> getLanguageDisplayNames() {
        Map<String, String> displayNames = new HashMap<>();
        var section = config.getConfigurationSection("display-names");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                displayNames.put(key, section.getString(key));
            }
        }
        return displayNames;
    }

    public Map<String, String> getLanguageHeadTextures() {
        Map<String, String> textures = new HashMap<>();
        var section = config.getConfigurationSection("head-textures");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                textures.put(key, section.getString(key));
            }
        }
        return textures;
    }

    public Map<String, org.bukkit.Material> getFallbackMaterials() {
        Map<String, org.bukkit.Material> materials = new HashMap<>();
        var section = config.getConfigurationSection("fallback-materials");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    materials.put(key, org.bukkit.Material.valueOf(section.getString(key)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return materials;
    }

    public List<String> getLanguageItemLore(String language) {
        return config.getStringList("language-items." + language + ".lore");
    }

    public String getGuiTitle() {
        return config.getString("gui.language-selection.title", "Language Selection");
    }

    public int getGuiSize() {
        return config.getInt("gui.language-selection.size", 27);
    }

    public int getGuiStartSlot() {
        return config.getInt("gui.language-selection.start-slot", 10);
    }

    public String getCloseButtonMaterial() {
        return config.getString("gui.language-selection.close-button.material", "BARRIER");
    }

    public int getCloseButtonSlot() {
        return config.getInt("gui.language-selection.close-button.slot", 22);
    }

    public String getCloseButtonName() {
        return config.getString("gui.language-selection.close-button.name", "&cClose");
    }

    public List<String> getCloseButtonLore() {
        return config.getStringList("gui.language-selection.close-button.lore");
    }

    public String getMessage(String path) {
        return config.getString("messages." + path, "Missing message: " + path);
    }

    public String getMessage(String path, String language) {
        String localizedMessage = config.getString("messages." + path + "." + language);
        if (localizedMessage != null) {
            return localizedMessage;
        }

        String englishMessage = config.getString("messages." + path + ".en");
        if (englishMessage != null) {
            return englishMessage;
        }

        String defaultMessage = config.getString("messages." + path);
        if (defaultMessage != null) {
            return defaultMessage;
        }

        return "Missing message: " + path + " for language: " + language;
    }

    public String getSelectedMessage() {
        return getMessage("selection-status.selected");
    }

    public String getSelectedMessage(String language) {
        return getMessage("selection-status.selected", language);
    }

    public String getNotSelectedMessage() {
        return getMessage("selection-status.not-selected");
    }

    public String getNotSelectedMessage(String language) {
        return getMessage("selection-status.not-selected", language);
    }

    public String getDisplayName(String language) {
        return config.getString("display-names." + language, language);
    }

    public String getPlayerLanguagesCollection() {
        return config.getString("player-languages.collection", "player_languages");
    }

    public String getPlayerLanguagesDatabase() {
        return config.getString("player-languages.database", "minecraft");
    }
}

