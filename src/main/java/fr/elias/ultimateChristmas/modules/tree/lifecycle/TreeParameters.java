package fr.elias.ultimateChristmas.modules.tree.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;
import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import org.bukkit.Material;

/**
 * Typed settings for the community tree, built by its functor.
 *
 * @param goal              ornaments needed to fully decorate the tree
 * @param radius            how close a player must be to hang one
 * @param cooldownSeconds   gap between a player's ornament contributions
 * @param ornamentMaterial  the item a player hangs (consumed from their hand)
 * @param ornamentShards    shards awarded for hanging one
 * @param nicePoints        naughty/nice credit for hanging one
 * @param ambientParticles  float particles around the tree
 */
public record TreeParameters(
        int goal,
        double radius,
        int cooldownSeconds,
        String ornamentMaterial,
        long ornamentShards,
        int nicePoints,
        boolean ambientParticles
) {

    public static final TreeParameters DEFAULTS = new TreeParameters(
            500, 6.0, 300, "", 25, 1, true);

    public static TreeParameters from(ModuleConfiguration config, TreeParameters defaults) {
        return new TreeParameters(
                Math.max(1, config.getInt("goal", defaults.goal())),
                Math.max(1.0, config.getDouble("radius", defaults.radius())),
                Math.max(0, config.getInt("cooldown-seconds", defaults.cooldownSeconds())),
                config.getString("ornament-material", defaults.ornamentMaterial()),
                config.getLong("reward.shards", defaults.ornamentShards()),
                config.getInt("reward.nice-points", defaults.nicePoints()),
                config.getBoolean("ambient-particles", defaults.ambientParticles()));
    }

    /** A specific required ornament item, or {@code null} to accept anything. */
    public Material requiredMaterial() {
        if (ornamentMaterial.isBlank()) {
            return null;
        }
        return ItemBuilder.material(ornamentMaterial, null);
    }
}
