package fr.elias.ultimateChristmas.modules.snow.infra;

import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.snow.lifecycle.SnowParameters;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Falling snow that follows each player who has it switched on.
 *
 * <p>Particles are the default and are purely visual. Optional ground snow
 * actually places thin snow layers, which is the part that needs care: it is
 * limited per tick, skips anything a player could be standing in or
 * interacting with, respects a roof overhead, and refuses to build up past one
 * layer so it never buries a build.
 *
 * <p>Snow is biased slightly ahead of where the player is walking, so it looks
 * like they are moving into weather rather than dragging it along.
 */
public final class SnowEffect {

    /** Blocks that must never gain a snow layer on top. */
    private static final Set<Material> NEVER_COVER = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.FURNACE,
            Material.CRAFTING_TABLE, Material.ENCHANTING_TABLE, Material.ANVIL,
            Material.LECTERN, Material.BEACON, Material.HOPPER, Material.CAULDRON,
            Material.END_PORTAL_FRAME, Material.SPAWNER);

    private final ChristmasPlatform platform;
    private final Supplier<SnowParameters> parameters;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();

    private long blizzardUntil;

    public SnowEffect(ChristmasPlatform platform, Supplier<SnowParameters> parameters) {
        this.platform = platform;
        this.parameters = parameters;
    }

    public boolean enabledFor(Player player) {
        return active.contains(player.getUniqueId());
    }

    public void enable(Player player) {
        active.add(player.getUniqueId());
    }

    public void disable(Player player) {
        active.remove(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        if (enabledFor(player)) {
            disable(player);
            return false;
        }
        enable(player);
        return true;
    }

    public void clear() {
        active.clear();
    }

    public void startBlizzard(int seconds) {
        blizzardUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    public boolean blizzardActive() {
        return System.currentTimeMillis() < blizzardUntil;
    }

    /** One pass over every player who should see snow. */
    public void tick() {
        SnowParameters settings = parameters.get();
        boolean blizzard = blizzardActive();

        for (Player player : platform.plugin().getServer().getOnlinePlayers()) {
            boolean show = blizzard || active.contains(player.getUniqueId());
            if (!show || !player.isOnline()) {
                continue;
            }
            if (!settings.worlds().isEmpty()
                    && !settings.worlds().contains(player.getWorld().getName())) {
                continue;
            }
            Location centre = lookAhead(player, settings);
            int density = blizzard ? settings.density() * 2 : settings.density();
            Fx.snowfall(centre, settings.radius(), Math.max(1, density / 4));

            if (settings.placeGroundSnow()) {
                placeSnow(player, centre, settings, blizzard);
            }
        }
    }

    /** Biases the snow a little in front of the player's movement. */
    private Location lookAhead(Player player, SnowParameters settings) {
        Location base = player.getLocation();
        if (settings.lookaheadFactor() <= 0) {
            return base;
        }
        Vector direction = base.getDirection().setY(0);
        if (direction.lengthSquared() < 0.01) {
            return base;
        }
        return base.clone().add(direction.normalize()
                .multiply(settings.radius() * settings.lookaheadFactor() * 0.5));
    }

    /**
     * Places a few thin snow layers near the player.
     *
     * <p>Deliberately rate-limited: this is the only part of the effect that
     * writes to the world, and doing it unbounded is how a "pretty" feature
     * turns into lag and a griefed spawn.
     */
    private void placeSnow(Player player, Location centre, SnowParameters settings,
                           boolean blizzard) {
        if (!platform.hooks().worldGuard().regionsAt(player.getLocation()).isEmpty()
                && settings.blockedRegions().stream()
                .anyMatch(region -> platform.hooks().worldGuard()
                        .inRegion(player.getLocation(), region))) {
            return;
        }
        if (settings.respectRoof() && hasRoof(player.getLocation())) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int attempts = blizzard ? settings.blocksPerTick() * 2 : settings.blocksPerTick();
        int radius = settings.radius();

        for (int index = 0; index < attempts; index++) {
            int offsetX = random.nextInt(-radius, radius + 1);
            int offsetZ = random.nextInt(-radius, radius + 1);
            Location spot = centre.clone().add(offsetX, 0, offsetZ);

            // Keep a clear ring around the player's feet so snow never appears
            // inside the block they are standing in.
            if (spot.distanceSquared(player.getLocation())
                    < settings.safeRadius() * settings.safeRadius()) {
                continue;
            }

            Block surface = spot.getWorld().getHighestBlockAt(spot);
            Block above = surface.getRelative(0, 1, 0);
            if (!above.getType().isAir() || !canSupportSnow(surface)) {
                continue;
            }
            if (settings.bannedBlocks().contains(surface.getType().name())) {
                continue;
            }
            if (!above.getWorld().getNearbyEntities(above.getLocation().add(0.5, 0.5, 0.5),
                    settings.entityClearance(), settings.entityClearance(),
                    settings.entityClearance()).isEmpty()) {
                continue;
            }
            BlockData data = Material.SNOW.createBlockData();
            if (data instanceof Snow snow) {
                snow.setLayers(1);
                above.setBlockData(snow, false);
            }
        }
    }

    private boolean canSupportSnow(Block block) {
        if (NEVER_COVER.contains(block.getType())) {
            return false;
        }
        return block.getType().isSolid() && block.getType().isOccluding();
    }

    /** True if there is a block overhead within a reasonable distance. */
    private boolean hasRoof(Location location) {
        Block block = location.getBlock();
        int top = Math.min(location.getWorld().getMaxHeight(), block.getY() + 24);
        for (int y = block.getY() + 2; y < top; y++) {
            if (!location.getWorld().getBlockAt(block.getX(), y, block.getZ())
                    .getType().isAir()) {
                return true;
            }
        }
        return false;
    }
}
