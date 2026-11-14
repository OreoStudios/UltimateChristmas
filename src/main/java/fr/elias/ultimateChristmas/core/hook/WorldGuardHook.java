package fr.elias.ultimateChristmas.core.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.Set;

/**
 * Region lookups for features that are fenced to an area.
 *
 * <p>Without WorldGuard installed every "is this inside region X" question
 * answers true, so a server that does not use regions is not silently locked
 * out of Santa, present spawning or the tree.
 */
public final class WorldGuardHook extends PluginHook {

    public WorldGuardHook(UltimateChristmas plugin) {
        super(plugin);
    }

    @Override
    public String pluginName() {
        return "WorldGuard";
    }

    @Override
    protected boolean setUp() {
        // Touch the platform so a missing or incompatible build fails here.
        WorldGuard.getInstance().getPlatform().getRegionContainer();
        return true;
    }

    /** Whether {@code location} lies inside the named region. */
    public boolean inRegion(Location location, String regionId) {
        if (!active() || regionId == null || regionId.isBlank()) {
            return true;
        }
        ProtectedRegion region = region(location.getWorld(), regionId);
        return region != null && region.contains(
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** Whether {@code location} lies inside any of the named regions. */
    public boolean inAnyRegion(Location location, java.util.List<String> regionIds) {
        if (!active() || regionIds == null || regionIds.isEmpty()) {
            return true;
        }
        for (String id : regionIds) {
            if (inRegion(location, id)) {
                return true;
            }
        }
        return false;
    }

    /** The ids of every region containing {@code location}. */
    public Set<String> regionsAt(Location location) {
        if (!active()) {
            return Collections.emptySet();
        }
        RegionManager manager = manager(location.getWorld());
        if (manager == null) {
            return Collections.emptySet();
        }
        return manager.getApplicableRegions(BukkitAdapter.asBlockVector(location))
                .getRegions().stream()
                .map(ProtectedRegion::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    public ProtectedRegion region(World world, String regionId) {
        if (!active() || world == null) {
            return null;
        }
        RegionManager manager = manager(world);
        return manager == null ? null : manager.getRegion(regionId);
    }

    private RegionManager manager(World world) {
        if (world == null) {
            return null;
        }
        return WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
    }

    /** A random point inside a region, or {@code null} if it has no volume. */
    public Location randomPointIn(World world, String regionId) {
        ProtectedRegion region = region(world, regionId);
        if (region == null) {
            return null;
        }
        var min = region.getMinimumPoint();
        var max = region.getMaximumPoint();
        var random = java.util.concurrent.ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = min.x() == max.x() ? min.x() : random.nextInt(min.x(), max.x() + 1);
            int z = min.z() == max.z() ? min.z() : random.nextInt(min.z(), max.z() + 1);
            // Drop to the surface, then confirm the column is still in the region.
            int y = world.getHighestBlockYAt(x, z) + 1;
            if (y < min.y() || y > max.y()) {
                continue;
            }
            if (region.contains(x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }
}
