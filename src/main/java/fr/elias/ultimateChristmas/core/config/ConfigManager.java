package fr.elias.ultimateChristmas.core.config;

import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The plugin's two global YAML files.
 *
 * <p>Per-feature settings are <em>not</em> here: each module owns
 * {@code plugins/UltimateChristmas/modules/<module-id>.yml}, loaded and reloaded
 * by Modulith and reached through {@code FeatureModule#config()}. Only genuinely
 * cross-cutting configuration lives in this manager.
 *
 * <p>Instance-scoped on purpose: the 3.x code kept configs in a static map,
 * which survived plugin disable and handed stale data to the next instance.
 */
public final class ConfigManager {

    /** Global settings: storage, season window, feature-independent toggles. */
    public static final String MAIN = "config.yml";
    /** Every player-facing string. */
    public static final String LANG = "lang.yml";

    private static final String[] ALL = {MAIN, LANG};

    private final Plugin plugin;
    private final Map<String, ConfigFile> files = new LinkedHashMap<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        files.clear();
        for (String name : ALL) {
            ConfigFile file = new ConfigFile(plugin, name);
            file.load();
            files.put(name, file);
        }
    }

    public void reloadAll() {
        for (ConfigFile file : files.values()) {
            file.load();
        }
    }

    public void saveAll() {
        for (ConfigFile file : files.values()) {
            file.save();
        }
    }

    /**
     * One of the global files.
     *
     * @throws IllegalArgumentException if the name is not a tracked global file,
     *                                 which can only be a coding mistake
     */
    public ConfigFile get(String name) {
        ConfigFile file = files.get(name);
        if (file == null) {
            throw new IllegalArgumentException("Unknown global config file: " + name
                    + " (known: " + files.keySet() + "). Per-module settings live in "
                    + "modules/<module-id>.yml and are read through the module context.");
        }
        return file;
    }

    public ConfigFile main() {
        return get(MAIN);
    }

    public ConfigFile lang() {
        return get(LANG);
    }
}
