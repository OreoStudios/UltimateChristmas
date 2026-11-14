package fr.elias.ultimateChristmas.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * One YAML file on disk, paired with the copy bundled in the jar.
 *
 * <p>On load, keys present in the bundled defaults but missing from the file on
 * disk are written back into it. That means a server upgrading from an older
 * release picks up new options with their documented defaults instead of
 * silently falling back to whatever the code happens to pass as a default, and
 * the owner can see and edit them.
 */
public final class ConfigFile {

    private final Plugin plugin;
    private final String name;
    private final File file;

    private FileConfiguration config;
    private YamlConfiguration defaults;

    ConfigFile(Plugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        this.file = new File(plugin.getDataFolder(), name);
    }

    public String name() {
        return name;
    }

    public FileConfiguration raw() {
        return config;
    }

    /** Loads from disk, creating the file from the jar copy if it is missing. */
    void load() {
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create directory for " + name);
            }
            try {
                plugin.saveResource(name, false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("No bundled default for " + name
                        + "; starting from an empty file.");
            }
        }

        config = YamlConfiguration.loadConfiguration(file);
        defaults = bundledDefaults();
        if (defaults != null) {
            config.setDefaults(defaults);
            config.options().copyDefaults(false);
            mergeMissingKeys();
        }
    }

    private YamlConfiguration bundledDefaults() {
        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled " + name, ex);
            return null;
        }
    }

    /**
     * Copies keys that exist in the jar defaults but not on disk, then saves.
     *
     * <p>Only genuinely absent keys are touched, so an owner who deliberately
     * changed a value never has it reverted. Lists are treated as single values:
     * merging their elements would fight anyone who removed an entry on purpose.
     */
    private void mergeMissingKeys() {
        List<String> added = new ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!config.contains(key, true)) {
                config.set(key, defaults.get(key));
                added.add(key);
            }
        }
        if (added.isEmpty()) {
            return;
        }
        save();
        plugin.getLogger().info("Added " + added.size() + " new option(s) to " + name
                + " (" + String.join(", ", added.subList(0, Math.min(6, added.size())))
                + (added.size() > 6 ? ", ..." : "") + ")");
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + name, ex);
        }
    }

    // ------------------------------------------------------------- accessors

    public ConfigurationSection section(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? section : config.createSection(path);
    }

    /** The section at {@code path}, or {@code null} if it is absent. */
    public ConfigurationSection sectionOrNull(String path) {
        return config.getConfigurationSection(path);
    }

    public boolean getBoolean(String path, boolean fallback) {
        return config.getBoolean(path, fallback);
    }

    public int getInt(String path, int fallback) {
        return config.getInt(path, fallback);
    }

    public long getLong(String path, long fallback) {
        return config.getLong(path, fallback);
    }

    public double getDouble(String path, double fallback) {
        return config.getDouble(path, fallback);
    }

    public String getString(String path, String fallback) {
        return config.getString(path, fallback);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    public boolean contains(String path) {
        return config.contains(path);
    }
}
