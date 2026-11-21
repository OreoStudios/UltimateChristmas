package fr.elias.ultimateChristmas.modules.events.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

/** Typed defaults for scheduled Christmas events. */
public record EventParameters(boolean enabled, String timezone) {
    public static final EventParameters DEFAULTS = new EventParameters(true, "Europe/Paris");

    public static EventParameters from(ModuleConfiguration config, EventParameters defaults) {
        return new EventParameters(config.getBoolean("enabled", defaults.enabled()),
                config.getString("timezone", defaults.timezone()));
    }
}
