package fr.elias.ultimateChristmas.modules.snowball.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

/**
 * Typed settings for snowball fights, built by its functor.
 *
 * @param rewardCooldownSeconds per-player gap between rewarded hits
 * @param rewardMobHits         also reward hitting a mob, not just a player
 * @param infiniteSnowballs     do not consume trick snowballs when thrown
 */
public record SnowballParameters(
        int rewardCooldownSeconds,
        boolean rewardMobHits,
        boolean infiniteSnowballs
) {

    public static final SnowballParameters DEFAULTS = new SnowballParameters(30, true, false);

    public static SnowballParameters from(ModuleConfiguration config,
                                          SnowballParameters defaults) {
        return new SnowballParameters(
                Math.max(0, config.getInt("reward-cooldown-seconds",
                        defaults.rewardCooldownSeconds())),
                config.getBoolean("reward-mob-hits", defaults.rewardMobHits()),
                config.getBoolean("infinite-snowballs", defaults.infiniteSnowballs()));
    }
}
