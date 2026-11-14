package fr.elias.ultimateChristmas.modules.economy.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

/**
 * Typed settings for the economy module, built by its functor.
 *
 * @param ignorePlayerPlaced  skip blocks a player placed, closing the
 *                            place-and-rebreak farm that 3.x allowed
 * @param announceEarnings    show an action-bar message when shards are earned
 * @param announceThreshold   smallest award worth announcing
 * @param payEnabled          whether players may send shards to each other
 * @param payMinimum          smallest amount {@code /christmas pay} accepts
 * @param payNicePoints       naughty/nice credit for paying someone, rate-limited
 */
public record EconomyParameters(
        boolean ignorePlayerPlaced,
        boolean announceEarnings,
        int announceThreshold,
        boolean payEnabled,
        long payMinimum,
        int payNicePoints
) {

    /** Used when a key is missing from {@code modules/economy.yml}. */
    public static final EconomyParameters DEFAULTS =
            new EconomyParameters(true, true, 1, true, 1L, 1);

    /** Reads the module config, falling back to {@code defaults} per key. */
    public static EconomyParameters from(ModuleConfiguration config, EconomyParameters defaults) {
        return new EconomyParameters(
                config.getBoolean("earning.ignore-player-placed", defaults.ignorePlayerPlaced()),
                config.getBoolean("earning.announce", defaults.announceEarnings()),
                config.getInt("earning.announce-threshold", defaults.announceThreshold()),
                config.getBoolean("pay.enabled", defaults.payEnabled()),
                config.getLong("pay.minimum", defaults.payMinimum()),
                config.getInt("pay.nice-points", defaults.payNicePoints()));
    }
}
