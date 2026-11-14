package fr.elias.ultimateChristmas.modules.countdown.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

/**
 * Typed settings for the countdown, built by its functor.
 *
 * @param bossBar       show a countdown boss bar to players
 * @param updateTicks   how often the bar refreshes
 * @param midnightShow  fire a celebration at midnight on Christmas Day
 * @param fireworkCount rockets per player in that show
 */
public record CountdownParameters(
        boolean bossBar,
        long updateTicks,
        boolean midnightShow,
        int fireworkCount
) {

    public static final CountdownParameters DEFAULTS =
            new CountdownParameters(true, 100L, true, 12);

    public static CountdownParameters from(ModuleConfiguration config,
                                           CountdownParameters defaults) {
        return new CountdownParameters(
                config.getBoolean("boss-bar.enabled", defaults.bossBar()),
                Math.max(20L, config.getLong("boss-bar.update-ticks", defaults.updateTicks())),
                config.getBoolean("midnight-show.enabled", defaults.midnightShow()),
                Math.max(0, Math.min(60,
                        config.getInt("midnight-show.fireworks", defaults.fireworkCount()))));
    }
}
