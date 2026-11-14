package fr.elias.ultimateChristmas.modules.ambience.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

import java.util.List;

/**
 * Typed settings for festive atmosphere, built by its functor.
 *
 * @param greetPlayers      send a festive greeting when a player joins
 * @param greetDelayTicks   how long after join to send it
 * @param ambientSounds     occasional sleigh-bell sounds near players
 * @param soundChance       per-tick-cycle chance a player hears one, 0 to 1
 * @param soundPeriodTicks  ticks between ambient-sound passes
 * @param onlyDuringSeason  keep the atmosphere quiet outside the season window
 */
public record AmbienceParameters(
        boolean greetPlayers,
        long greetDelayTicks,
        boolean ambientSounds,
        double soundChance,
        long soundPeriodTicks,
        boolean onlyDuringSeason
) {

    public static final AmbienceParameters DEFAULTS = new AmbienceParameters(
            true, 40L, true, 0.15, 400L, true);

    public static AmbienceParameters from(ModuleConfiguration config,
                                          AmbienceParameters defaults) {
        return new AmbienceParameters(
                config.getBoolean("greet-players", defaults.greetPlayers()),
                Math.max(0L, config.getLong("greet-delay-ticks", defaults.greetDelayTicks())),
                config.getBoolean("ambient-sounds.enabled", defaults.ambientSounds()),
                Math.max(0, Math.min(1.0,
                        config.getDouble("ambient-sounds.chance", defaults.soundChance()))),
                Math.max(40L, config.getLong("ambient-sounds.period-ticks",
                        defaults.soundPeriodTicks())),
                config.getBoolean("only-during-season", defaults.onlyDuringSeason()));
    }

    /** Default greeting lines, used when lang.yml has none configured. */
    public static List<String> defaultGreetings() {
        return List.of(
                "<gold>Welcome back! <gray>%days% days until Christmas.",
                "<green>Season's greetings, <white>%player%</white>!",
                "<red>Ho ho ho! <gray>Good to see you, <white>%player%</white>.");
    }
}
