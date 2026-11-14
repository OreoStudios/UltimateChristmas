package fr.elias.ultimateChristmas.modules.snow.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

import java.util.List;
import java.util.Set;

/**
 * Typed settings for snowfall, built by its functor.
 *
 * @param onByDefault      switch snow on for players as they join
 * @param radius           horizontal radius of the effect, in blocks
 * @param density          particles per tick slice
 * @param tickPeriod       ticks between passes; higher is cheaper
 * @param placeGroundSnow  actually place thin snow layers, not just particles
 * @param blocksPerTick    maximum snow layers placed per pass, per player
 * @param safeRadius       keep this clear around the player's feet
 * @param entityClearance  skip spots within this distance of any entity
 * @param respectRoof      place no ground snow while the player is under cover
 * @param lookaheadFactor  how far ahead of the player to bias the snow, 0 to 2
 * @param worlds           worlds snow may appear in; empty means all
 * @param blockedRegions   WorldGuard regions where ground snow is suppressed
 * @param bannedBlocks     block types that must never be covered
 */
public record SnowParameters(
        boolean onByDefault,
        int radius,
        int density,
        long tickPeriod,
        boolean placeGroundSnow,
        int blocksPerTick,
        double safeRadius,
        double entityClearance,
        boolean respectRoof,
        double lookaheadFactor,
        Set<String> worlds,
        Set<String> blockedRegions,
        Set<String> bannedBlocks
) {

    public SnowParameters {
        worlds = Set.copyOf(worlds);
        blockedRegions = Set.copyOf(blockedRegions);
        bannedBlocks = Set.copyOf(bannedBlocks);
    }

    public static final SnowParameters DEFAULTS = new SnowParameters(
            true, 20, 32, 10L, false, 4, 1.5, 1.25, true, 0.66,
            Set.of(), Set.of(),
            Set.of("OAK_SLAB", "SPRUCE_SLAB", "COBBLESTONE_SLAB", "STONE_BRICK_SLAB",
                    "STONE_BRICK_STAIRS", "COBBLESTONE_STAIRS", "OAK_FENCE",
                    "OAK_FENCE_GATE", "FLOWERING_AZALEA_LEAVES", "FARMLAND"));

    public static SnowParameters from(ModuleConfiguration config, SnowParameters defaults) {
        return new SnowParameters(
                config.getBoolean("on-by-default", defaults.onByDefault()),
                Math.max(4, config.getInt("radius", defaults.radius())),
                Math.max(1, config.getInt("density", defaults.density())),
                Math.max(2, config.getLong("tick-period", defaults.tickPeriod())),
                config.getBoolean("ground-snow.enabled", defaults.placeGroundSnow()),
                Math.max(0, config.getInt("ground-snow.blocks-per-tick",
                        defaults.blocksPerTick())),
                config.getDouble("ground-snow.safe-radius", defaults.safeRadius()),
                config.getDouble("ground-snow.entity-clearance", defaults.entityClearance()),
                config.getBoolean("ground-snow.respect-roof", defaults.respectRoof()),
                Math.max(0, Math.min(2.0,
                        config.getDouble("lookahead-factor", defaults.lookaheadFactor()))),
                toSet(config.getStringList("worlds"), defaults.worlds()),
                toSet(config.getStringList("ground-snow.blocked-regions"),
                        defaults.blockedRegions()),
                upper(toSet(config.getStringList("ground-snow.banned-blocks"),
                        defaults.bannedBlocks())));
    }

    private static Set<String> toSet(List<String> values, Set<String> fallback) {
        return values == null || values.isEmpty() ? fallback : Set.copyOf(values);
    }

    private static Set<String> upper(Set<String> values) {
        return values.stream()
                .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
