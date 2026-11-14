package fr.elias.ultimateChristmas.modules.feasts.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

/**
 * Typed settings for festive food, built by its functor.
 *
 * @param particlesOnEat show a small particle flourish when festive food is eaten
 */
public record FeastParameters(boolean particlesOnEat) {

    public static final FeastParameters DEFAULTS = new FeastParameters(true);

    public static FeastParameters from(ModuleConfiguration config, FeastParameters defaults) {
        return new FeastParameters(
                config.getBoolean("particles-on-eat", defaults.particlesOnEat()));
    }
}
