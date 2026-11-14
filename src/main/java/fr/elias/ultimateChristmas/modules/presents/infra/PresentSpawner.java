package fr.elias.ultimateChristmas.modules.presents.infra;

import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.item.Keys;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.presents.lifecycle.PresentHuntParameters;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Hides presents around the world for players to stumble across.
 *
 * <p>A present is a marker armour stand wearing a gift block on its head: no
 * block is placed, so nothing is left behind if the server stops mid-hunt and
 * no build can be griefed by the spawner. Each one is tagged in persistent
 * data, which is how they are recognised and cleaned up even across a restart.
 */
public final class PresentSpawner {

    private final ChristmasPlatform platform;
    private final Supplier<PresentHuntParameters> parameters;

    public PresentSpawner(ChristmasPlatform platform,
                          Supplier<PresentHuntParameters> parameters) {
        this.platform = platform;
        this.parameters = parameters;
    }

    /** Hides one present, or returns {@code null} if no spot could be found. */
    public Location spawnOne() {
        PresentHuntParameters settings = parameters.get();
        World world = pickWorld(settings);
        if (world == null) {
            return null;
        }
        Location spot = findSpot(world, settings);
        if (spot == null) {
            return null;
        }

        world.spawn(spot, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setCustomName(fr.elias.ultimateChristmas.core.text.Text
                    .plain(settings.hologram()));
            stand.setCustomNameVisible(settings.showHologram());
            stand.getEquipment().setItem(EquipmentSlot.HEAD,
                    ItemBuilder.of(settings.presentMaterial()).build());
            stand.setHeadPose(new EulerAngle(0, 0, 0));
            // Tagging the entity is what lets a later restart still find and
            // clear these, which a transient in-memory list could not.
            stand.getPersistentDataContainer()
                    .set(Keys.presentId(), PersistentDataType.STRING, "hidden");
        });

        Fx.burst(spot.clone().add(0, 0.5, 0), org.bukkit.Particle.END_ROD, 12, 0.3);
        return spot;
    }

    private World pickWorld(PresentHuntParameters settings) {
        List<World> candidates = new ArrayList<>();
        for (World world : platform.plugin().getServer().getWorlds()) {
            if (settings.worlds().isEmpty() || settings.worlds().contains(world.getName())) {
                candidates.add(world);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /**
     * Picks a surface spot, preferring somewhere near a player so presents are
     * findable rather than scattered across unloaded chunks.
     */
    private Location findSpot(World world, PresentHuntParameters settings) {
        // A configured region wins, when WorldGuard is present.
        if (!settings.region().isBlank() && platform.hooks().worldGuard().active()) {
            Location inRegion = platform.hooks().worldGuard()
                    .randomPointIn(world, settings.region());
            if (inRegion != null) {
                return inRegion;
            }
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Player> players = new ArrayList<>(world.getPlayers());

        for (int attempt = 0; attempt < 40; attempt++) {
            Location origin;
            if (!players.isEmpty() && settings.nearPlayers()) {
                Player anchor = players.get(random.nextInt(players.size()));
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = settings.minDistance()
                        + random.nextDouble() * (settings.maxDistance() - settings.minDistance());
                origin = anchor.getLocation().clone().add(
                        Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            } else {
                Location centre = world.getSpawnLocation();
                origin = centre.clone().add(
                        random.nextInt(-settings.maxDistance(), settings.maxDistance() + 1), 0,
                        random.nextInt(-settings.maxDistance(), settings.maxDistance() + 1));
            }

            // Never force-load chunks: an unloaded spot is simply skipped, so
            // the hunt cannot drag the server into generating terrain.
            if (!world.isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) {
                continue;
            }

            Block surface = world.getHighestBlockAt(origin);
            Location candidate = surface.getLocation().add(0.5, 1.0, 0.5);
            if (!surface.getType().isSolid() || !candidate.getBlock().getType().isAir()) {
                continue;
            }
            if (!settings.region().isBlank()
                    && !platform.hooks().worldGuard().inRegion(candidate, settings.region())) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    /** Whether an entity is one of our hidden presents. */
    public static boolean isPresent(Entity entity) {
        return entity instanceof ArmorStand
                && entity.getPersistentDataContainer().has(Keys.presentId());
    }

    /** Every hidden present currently loaded. */
    public List<Entity> active() {
        List<Entity> found = new ArrayList<>();
        for (World world : platform.plugin().getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (isPresent(entity)) {
                    found.add(entity);
                }
            }
        }
        return found;
    }

    public int clearAll() {
        List<Entity> present = active();
        present.forEach(Entity::remove);
        return present.size();
    }

    /** Exposed so the module can read the key without importing Keys. */
    public static NamespacedKey key() {
        return Keys.presentId();
    }

    public Material material() {
        return parameters.get().presentMaterial();
    }
}
