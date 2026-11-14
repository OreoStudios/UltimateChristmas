package fr.elias.ultimateChristmas.modules.bosses.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

/**
 * Typed settings shared by all festive bosses, built by its functor.
 *
 * @param abilityIntervalSeconds seconds between a boss using an ability
 * @param taunt                  send a boss's spawn/death lines to chat
 * @param removeMinionsOnDeath   clear summoned minions when the boss dies
 */
public record BossParameters(
        int abilityIntervalSeconds,
        boolean taunt,
        boolean removeMinionsOnDeath
) {

    public static final BossParameters DEFAULTS = new BossParameters(12, true, true);

    public static BossParameters from(ModuleConfiguration config, BossParameters defaults) {
        return new BossParameters(
                Math.max(3, config.getInt("ability-interval-seconds",
                        defaults.abilityIntervalSeconds())),
                config.getBoolean("taunt", defaults.taunt()),
                config.getBoolean("remove-minions-on-death",
                        defaults.removeMinionsOnDeath()));
    }
}
