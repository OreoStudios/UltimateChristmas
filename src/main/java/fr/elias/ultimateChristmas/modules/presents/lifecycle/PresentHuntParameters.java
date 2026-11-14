package fr.elias.ultimateChristmas.modules.presents.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;
import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

/**
 * Typed settings for the present hunt, built by its functor.
 *
 * @param spawnIntervalSeconds how often a new present appears
 * @param maxActive            cap on presents hidden at once
 * @param perSpawn             how many appear each interval
 * @param icon                 the block shown on the present marker
 * @param nearPlayers          hide presents near players rather than near spawn
 * @param minDistance          closest a present spawns to its anchor
 * @param maxDistance          furthest a present spawns from its anchor
 * @param announce             broadcast when presents are hidden
 * @param showHologram         float a label above each present
 * @param hologram             the label text
 * @param region               WorldGuard region to confine presents to, or blank
 * @param worlds               worlds presents may appear in; empty means all
 */
public record PresentHuntParameters(
        int spawnIntervalSeconds,
        int maxActive,
        int perSpawn,
        String icon,
        boolean nearPlayers,
        int minDistance,
        int maxDistance,
        boolean announce,
        boolean showHologram,
        String hologram,
        String region,
        Set<String> worlds
) {

    public PresentHuntParameters {
        worlds = Set.copyOf(worlds);
        // A present that can spawn on top of the player is no fun to hunt.
        minDistance = Math.max(8, minDistance);
        maxDistance = Math.max(minDistance + 8, maxDistance);
    }

    public static final PresentHuntParameters DEFAULTS = new PresentHuntParameters(
            600, 12, 2, "RED_SHULKER_BOX", true, 24, 120, true, true,
            "&c&l✦ &aPresent &c&l✦", "", Set.of());

    public static PresentHuntParameters from(ModuleConfiguration config,
                                             PresentHuntParameters defaults) {
        List<String> worlds = config.getStringList("worlds");
        return new PresentHuntParameters(
                Math.max(10, config.getInt("spawn-interval-seconds",
                        defaults.spawnIntervalSeconds())),
                Math.max(1, config.getInt("max-active", defaults.maxActive())),
                Math.max(1, config.getInt("per-spawn", defaults.perSpawn())),
                config.getString("icon", defaults.icon()),
                config.getBoolean("near-players", defaults.nearPlayers()),
                config.getInt("min-distance", defaults.minDistance()),
                config.getInt("max-distance", defaults.maxDistance()),
                config.getBoolean("announce", defaults.announce()),
                config.getBoolean("hologram.enabled", defaults.showHologram()),
                config.getString("hologram.text", defaults.hologram()),
                config.getString("region", defaults.region()),
                worlds == null || worlds.isEmpty() ? defaults.worlds() : Set.copyOf(worlds));
    }

    public Material presentMaterial() {
        return ItemBuilder.material(icon, Material.RED_SHULKER_BOX);
    }
}
