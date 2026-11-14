package fr.elias.ultimateChristmas.modules.santa.infra;

import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.item.Keys;
import fr.elias.ultimateChristmas.core.text.Text;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.santa.lifecycle.SantaParameters;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Spawns, tracks and despawns the Santa NPC.
 *
 * <p>Santa is a stationary, invulnerable villager, tagged in persistent data so
 * he is recognised reliably without depending on his name (which a player could
 * otherwise spoof). If LibsDisguises is installed he is given a player skin;
 * without it he is simply a named villager, so the feature needs no extra
 * plugin to work.
 */
public final class SantaManager {

    private static final String ROLE = "santa";

    private final ChristmasPlatform platform;
    private final Supplier<SantaParameters> parameters;

    private java.util.UUID santaId;

    public SantaManager(ChristmasPlatform platform, Supplier<SantaParameters> parameters) {
        this.platform = platform;
        this.parameters = parameters;
    }

    public boolean isSpawned() {
        return santa() != null;
    }

    /** The live Santa entity, or {@code null}. */
    public Entity santa() {
        if (santaId == null) {
            return null;
        }
        Entity entity = platform.plugin().getServer().getEntity(santaId);
        if (entity == null || entity.isDead() || !entity.isValid()) {
            santaId = null;
            return null;
        }
        return entity;
    }

    /** Whether an entity is the Santa NPC. */
    public static boolean isSanta(Entity entity) {
        return entity != null && Keys.getString(
                entity.getPersistentDataContainer(), Keys.npcRole()) != null
                && ROLE.equals(Keys.getString(
                        entity.getPersistentDataContainer(), Keys.npcRole()));
    }

    /**
     * Spawns Santa at one of the configured locations.
     *
     * @return the spawn location, or {@code null} if none could be used
     */
    public Location spawn() {
        if (isSpawned()) {
            return santa().getLocation();
        }
        Location where = pickLocation();
        if (where == null || where.getWorld() == null) {
            return null;
        }

        World world = where.getWorld();
        Villager villager = world.spawn(where, Villager.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setCollidable(false);
            entity.setRemoveWhenFarAway(false);
            entity.setPersistent(true);
            entity.setProfession(Villager.Profession.NITWIT);
            entity.customName(Text.parse(parameters.get().displayName()));
            entity.setCustomNameVisible(true);
            entity.getPersistentDataContainer()
                    .set(Keys.npcRole(), PersistentDataType.STRING, ROLE);
        });
        this.santaId = villager.getUniqueId();

        String skin = parameters.get().skin();
        if (!skin.isBlank()) {
            platform.hooks().disguises().disguiseAsPlayer(villager, skin);
        }

        Fx.helix(where, Particle.END_ROD, 3.0, 1.0);
        Fx.jingle(where);
        return where;
    }

    public void despawn() {
        Entity santa = santa();
        if (santa == null) {
            return;
        }
        platform.hooks().disguises().undisguise(santa);
        Fx.burst(santa.getLocation().add(0, 1, 0), Particle.POOF, 20, 0.5);
        santa.remove();
        santaId = null;
    }

    /** A gentle particle idle, so Santa is easy to spot. */
    public void idleEffects() {
        Entity santa = santa();
        if (santa == null) {
            return;
        }
        Fx.snowfall(santa.getLocation().add(0, 2.5, 0), 1.5, 3);
        if (santa instanceof LivingEntity living) {
            // Keep him facing nothing in particular but upright.
            living.setRotation(living.getLocation().getYaw() + 8, 0);
        }
    }

    private Location pickLocation() {
        List<Location> spots = parameters.get().spawnLocations();
        if (spots.isEmpty()) {
            return null;
        }
        return spots.get(ThreadLocalRandom.current().nextInt(spots.size())).clone();
    }

    /** Removes any leftover Santa entities across all worlds (e.g. after a crash). */
    public void cleanupStale() {
        for (World world : platform.plugin().getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Villager.class)) {
                if (isSanta(entity) && (santaId == null
                        || !entity.getUniqueId().equals(santaId))) {
                    platform.hooks().disguises().undisguise(entity);
                    entity.remove();
                }
            }
        }
    }
}
