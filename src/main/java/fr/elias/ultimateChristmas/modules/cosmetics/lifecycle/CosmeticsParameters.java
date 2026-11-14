package fr.elias.ultimateChristmas.modules.cosmetics.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

/**
 * Typed settings for the wardrobe, built by its functor.
 *
 * @param trailTickPeriod ticks between particle-trail passes
 * @param trailParticles  particles emitted per pass
 * @param replaceHelmet   let a hat occupy the helmet slot, swapping out armour
 */
public record CosmeticsParameters(
        long trailTickPeriod,
        int trailParticles,
        boolean replaceHelmet
) {

    public static final CosmeticsParameters DEFAULTS =
            new CosmeticsParameters(6L, 3, true);

    public static CosmeticsParameters from(ModuleConfiguration config,
                                           CosmeticsParameters defaults) {
        return new CosmeticsParameters(
                Math.max(2L, config.getLong("trails.tick-period", defaults.trailTickPeriod())),
                Math.max(1, Math.min(20,
                        config.getInt("trails.particles", defaults.trailParticles()))),
                config.getBoolean("hats.use-helmet-slot", defaults.replaceHelmet()));
    }
}
