package fr.elias.ultimateChristmas.modules.bosses.infra;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** Optional WorldGuard region-entry encounter settings for one boss. */
public record BossRegionTrigger(boolean enabled, List<String> regions,
                                int cooldownSeconds, int maxAlive) {

    public BossRegionTrigger {
        regions = List.copyOf(regions);
    }

    public static BossRegionTrigger read(ConfigurationSection config, Logger logger) {
        if (config == null) return new BossRegionTrigger(false, List.of(), 600, 1);
        List<String> regions = config.getStringList("regions").stream()
                .map(region -> region.trim().toLowerCase(Locale.ROOT))
                .filter(region -> !region.isBlank())
                .distinct()
                .toList();
        boolean enabled = config.getBoolean("enabled", false);
        if (enabled && regions.isEmpty()) {
            logger.warning("Enabled boss region trigger has no WorldGuard region ids.");
        }
        return new BossRegionTrigger(enabled, regions,
                Math.max(10, config.getInt("cooldown-seconds", 600)),
                Math.max(1, config.getInt("max-alive", 1)));
    }
}
