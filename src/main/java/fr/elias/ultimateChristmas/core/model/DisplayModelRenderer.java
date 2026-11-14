package fr.elias.ultimateChristmas.core.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The plugin's own custom-model provider: plays a bone-rigged, animated model
 * on an entity using display entities, with no ModelEngine or BetterModel.
 *
 * <p>Holds every live rig instance and drives them from one tick loop. Each
 * instance auto-selects its looping clip from how fast the entity is moving
 * (idle / walk / run), while one-shot clips (attack, laugh, steal, ...) are
 * triggered by the gameplay that owns the entity and take over the bones they
 * animate until they finish.
 *
 * <p>Players only see the model with the resource pack installed; without it
 * the bones show as plain items. The base entity is hidden either way, so the
 * fallback is "an invisible mob", not a broken one.
 */
public final class DisplayModelRenderer {

    /** Movement thresholds (blocks moved per tick) that pick the looping clip. */
    private static final double WALK_SPEED = 0.015;
    private static final double RUN_SPEED = 0.14;
    private static final double TICK_SECONDS = 0.05;

    /** Clip names used for the three movement states, with sensible fallbacks. */
    public record StateClips(String idle, String walk, String run) {

        public static final StateClips DEFAULT = new StateClips("idle", "walk", "run");
    }

    private static final class Instance {
        final RiggedEntity model;
        final StateClips clips;
        Location lastLocation;
        boolean locked;        // true while a non-looping clip should hold the state

        Instance(RiggedEntity model, StateClips clips) {
            this.model = model;
            this.clips = clips;
        }
    }

    private final Plugin plugin;
    private final RigRegistry registry;
    private final Map<UUID, Instance> instances = new ConcurrentHashMap<>();

    public DisplayModelRenderer(Plugin plugin, RigRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public RigRegistry registry() {
        return registry;
    }

    /**
     * Attaches an animated model to an entity.
     *
     * @return true if a rig was found and spawned
     */
    public boolean attach(LivingEntity base, String rigId, RiggedEntity.Placement placement,
                          StateClips clips) {
        ModelRig rig = registry.get(rigId);
        if (rig == null || base == null) {
            return false;
        }
        detach(base.getUniqueId());

        RiggedEntity model = new RiggedEntity(plugin, rig, placement);
        model.spawn(base);
        // Start in the best available resting clip.
        model.loop(firstPresent(rig, clips.idle(), clips.walk(), "idle"));

        instances.put(base.getUniqueId(), new Instance(model, clips));
        return true;
    }

    public boolean attach(LivingEntity base, String rigId, RiggedEntity.Placement placement) {
        return attach(base, rigId, placement, StateClips.DEFAULT);
    }

    /** Triggers a one-shot clip (attack, laugh, steal, celebrate, ...). */
    public void play(UUID baseId, String clip) {
        Instance instance = instances.get(baseId);
        if (instance != null) {
            instance.model.playOnce(clip);
        }
    }

    public boolean has(UUID baseId) {
        return instances.containsKey(baseId);
    }

    public int count() {
        return instances.size();
    }

    /** Detaches a model and reveals the entity. */
    public void detach(UUID baseId) {
        Instance instance = instances.remove(baseId);
        if (instance == null) {
            return;
        }
        Entity base = Bukkit.getEntity(baseId);
        instance.model.remove(base instanceof LivingEntity living ? living : null);
    }

    public void clear() {
        for (UUID id : Map.copyOf(instances).keySet()) {
            detach(id);
        }
        instances.clear();
    }

    /**
     * Advances every model one tick. Call once per server tick.
     *
     * <p>Instances whose entity has gone are detached here, so a boss killed by
     * something the owning module did not route through {@link #detach} still
     * cleans up its displays.
     */
    public void tickAll() {
        if (instances.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Instance> entry : Map.copyOf(instances).entrySet()) {
            Entity base = Bukkit.getEntity(entry.getKey());
            if (!(base instanceof LivingEntity living) || base.isDead() || !base.isValid()) {
                detach(entry.getKey());
                continue;
            }
            Instance instance = entry.getValue();
            autoSelectClip(instance, living);
            instance.model.tick(TICK_SECONDS, living);
        }
    }

    /** Picks idle/walk/run from how far the entity moved since last tick. */
    private void autoSelectClip(Instance instance, LivingEntity base) {
        if (instance.model.isPlayingOnce()) {
            return;  // a one-shot owns the pose; leave the loop as-is
        }
        double speed = 0;
        Location now = base.getLocation();
        if (instance.lastLocation != null
                && instance.lastLocation.getWorld() == now.getWorld()) {
            double dx = now.getX() - instance.lastLocation.getX();
            double dz = now.getZ() - instance.lastLocation.getZ();
            speed = Math.sqrt(dx * dx + dz * dz);
        }
        instance.lastLocation = now.clone();

        ModelRig rig = instance.model.rig();
        String clip;
        if (speed >= RUN_SPEED && rig.has(instance.clips.run())) {
            clip = instance.clips.run();
        } else if (speed >= WALK_SPEED && rig.has(instance.clips.walk())) {
            clip = instance.clips.walk();
        } else {
            clip = firstPresent(rig, instance.clips.idle(), instance.clips.walk(), null);
        }
        if (clip != null) {
            instance.model.loop(clip);
        }
    }

    private static String firstPresent(ModelRig rig, String... names) {
        for (String name : names) {
            if (name != null && rig.has(name)) {
                return name;
            }
        }
        // Fall back to any animation the rig has, so something always plays.
        return rig.animationNames().stream().findFirst().orElse(null);
    }
}
