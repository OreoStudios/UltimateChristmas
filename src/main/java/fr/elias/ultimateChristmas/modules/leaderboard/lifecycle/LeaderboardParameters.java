package fr.elias.ultimateChristmas.modules.leaderboard.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

/**
 * Typed settings for the leaderboards, built by its functor.
 *
 * @param defaultSize  rows shown when no count is given
 * @param maxSize      most rows a player may ask for
 * @param cacheSeconds how long a board result is reused before re-querying
 */
public record LeaderboardParameters(
        int defaultSize,
        int maxSize,
        int cacheSeconds
) {

    public static final LeaderboardParameters DEFAULTS =
            new LeaderboardParameters(10, 25, 60);

    public static LeaderboardParameters from(ModuleConfiguration config,
                                             LeaderboardParameters defaults) {
        int max = Math.max(1, config.getInt("max-size", defaults.maxSize()));
        return new LeaderboardParameters(
                Math.max(1, Math.min(max,
                        config.getInt("default-size", defaults.defaultSize()))),
                max,
                Math.max(5, config.getInt("cache-seconds", defaults.cacheSeconds())));
    }
}
