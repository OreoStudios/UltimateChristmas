package fr.elias.ultimateChristmas.modules.pass.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Typed settings for the festive pass, built by its functor.
 *
 * @param xpPerAdventDoor    experience for opening an advent door
 * @param xpPerQuest         experience for finishing an objective
 * @param premiumPermission  permission that grants the premium track
 * @param xpRates            experience per unit of a tracked stat
 */
public record PassParameters(
        long xpPerAdventDoor,
        long xpPerQuest,
        String premiumPermission,
        Map<String, Double> xpRates
) {

    public PassParameters {
        xpRates = Map.copyOf(xpRates);
    }

    public static final PassParameters DEFAULTS = new PassParameters(
            100, 50, "ultimatechristmas.pass.premium",
            Map.of(
                    "shards_earned", 0.05,
                    "presents_found", 25.0,
                    "snowballs_hit", 2.0,
                    "bosses_slain", 250.0,
                    "ornaments_hung", 15.0,
                    "gifts_given", 10.0));

    public static PassParameters from(ModuleConfiguration config, PassParameters defaults) {
        return new PassParameters(
                config.getLong("xp.per-advent-door", defaults.xpPerAdventDoor()),
                config.getLong("xp.per-quest", defaults.xpPerQuest()),
                config.getString("premium-permission", defaults.premiumPermission()),
                readRates(config.get("xp.per-stat"), defaults.xpRates()));
    }

    /**
     * Reads the per-stat rates.
     *
     * <p>A rate may be fractional, which is how "shards earned" can be worth a
     * little pass experience each without running away.
     */
    private static Map<String, Double> readRates(Object node, Map<String, Double> fallback) {
        if (!(node instanceof ConfigurationSection section)) {
            return fallback;
        }
        Map<String, Double> rates = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            double rate = section.getDouble(key, 0);
            if (rate > 0) {
                rates.put(key.toLowerCase(Locale.ROOT), rate);
            }
        }
        return rates.isEmpty() ? fallback : rates;
    }
}
