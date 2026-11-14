package fr.elias.ultimateChristmas.core.hook;

import fr.elias.ultimateChristmas.UltimateChristmas;

/**
 * Base for an optional third-party integration.
 *
 * <p>{@link #detect()} runs once at startup and must never throw: a hook that
 * cannot resolve its plugin simply reports itself inactive, and the feature
 * depending on it falls back.
 */
public abstract class PluginHook {

    protected final UltimateChristmas plugin;
    private boolean active;

    protected PluginHook(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    /** The plugin name as it appears in plugin.yml, for logs and detection. */
    public abstract String pluginName();

    /** Wires up the integration. Returns false if the plugin is unusable. */
    protected abstract boolean setUp() throws Exception;

    final boolean detect() {
        if (!Hooks.present(pluginName())) {
            active = false;
            return false;
        }
        try {
            active = setUp();
        } catch (Exception | NoClassDefFoundError error) {
            // A version mismatch shows up as a linkage error here rather than
            // mid-game, so report it clearly and carry on without the feature.
            plugin.getLogger().warning("Found " + pluginName()
                    + " but could not use its API (" + error.getClass().getSimpleName()
                    + ": " + error.getMessage() + "); continuing without it.");
            active = false;
        }
        return active;
    }

    public final boolean active() {
        return active;
    }
}
