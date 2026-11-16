package fr.elias.ultimateChristmas.core.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns live display rigs, locomotion selection and detached death sequences. */
public final class DisplayModelRenderer {
    private static final double WALK_SPEED = 0.015;
    private static final double TICK_SECONDS = 0.05;

    public record StateClips(String idle, String walk, String run, String carry,
                             double runThreshold, double movementRate) {
        public StateClips(String idle, String walk, String run) {
            this(idle, walk, run, null, .14, 1);
        }
        public static final StateClips DEFAULT = new StateClips("idle", "walk", "run");
    }

    private static final class Instance {
        final RiggedEntity model;
        final StateClips clips;
        Location lastLocation;
        Location deathAnchor;
        float deathYaw;
        double deathRemaining;

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

    public RigRegistry registry() { return registry; }

    public boolean attach(LivingEntity base, String rigId, RiggedEntity.Placement placement, StateClips clips) {
        ModelRig rig = registry.get(rigId);
        if (rig == null || base == null) return false;
        detach(base.getUniqueId());
        RiggedEntity model = new RiggedEntity(plugin, rig, placement);
        model.spawn(base);
        model.loop(firstPresent(rig, clips.idle(), clips.walk(), "idle"));
        model.tick(0, base);
        Instance instance = new Instance(model, clips);
        instance.lastLocation = base.getLocation();
        instances.put(base.getUniqueId(), instance);
        return true;
    }

    public boolean attach(LivingEntity base, String rigId, RiggedEntity.Placement placement) {
        return attach(base, rigId, placement, StateClips.DEFAULT);
    }

    public boolean play(UUID id, String clip) { return play(id, clip, 30); }

    public boolean play(UUID id, String clip, int priority) {
        Instance instance = instances.get(id);
        return instance != null && instance.model.playOnce(clip, priority);
    }

    /** Retains the model at the death location until its final pose has been shown. */
    public boolean finish(LivingEntity base, String clip) {
        Instance instance = instances.get(base.getUniqueId());
        if (instance == null) return false;
        if (!instance.model.finish(clip)) {
            detach(base.getUniqueId());
            return false;
        }
        instance.deathAnchor = base.getLocation().clone();
        instance.deathYaw = base.getBodyYaw();
        instance.deathRemaining = instance.model.rig().animation(clip).length() + .3;
        instance.model.tick(0, instance.deathAnchor, instance.deathYaw);
        return true;
    }

    public Set<String> animationNames(UUID id) {
        Instance instance = instances.get(id);
        return instance == null ? Set.of() : instance.model.rig().animationNames();
    }

    public boolean has(UUID id) { return instances.containsKey(id); }
    public double animationLength(UUID id, String clip) {
        Instance instance = instances.get(id);
        ModelRig.Animation animation = instance == null ? null : instance.model.rig().animation(clip);
        return animation == null ? 0 : animation.length();
    }
    public int count() { return instances.size(); }

    public void detach(UUID id) {
        Instance instance = instances.remove(id);
        if (instance == null) return;
        Entity base = Bukkit.getEntity(id);
        instance.model.remove(base instanceof LivingEntity living ? living : null);
    }

    public void clear() {
        for (UUID id : Set.copyOf(instances.keySet())) detach(id);
    }

    public void tickAll() {
        for (Map.Entry<UUID, Instance> entry : Map.copyOf(instances).entrySet()) {
            Instance instance = entry.getValue();
            if (instance.deathAnchor != null) {
                instance.model.tick(TICK_SECONDS, instance.deathAnchor, instance.deathYaw);
                instance.deathRemaining -= TICK_SECONDS;
                if (instance.deathRemaining <= 0) detach(entry.getKey());
                continue;
            }
            Entity base = Bukkit.getEntity(entry.getKey());
            if (!(base instanceof LivingEntity living) || base.isDead() || !base.isValid()) {
                detach(entry.getKey());
                continue;
            }
            autoSelectClip(instance, living);
            instance.model.tick(TICK_SECONDS, living);
        }
    }

    private void autoSelectClip(Instance instance, LivingEntity base) {
        Location now = base.getLocation();
        double speed = 0;
        if (instance.lastLocation != null && instance.lastLocation.getWorld() == now.getWorld()) {
            double dx = now.getX() - instance.lastLocation.getX();
            double dz = now.getZ() - instance.lastLocation.getZ();
            speed = Math.hypot(dx, dz);
        }
        // Always advance the sample, including during attacks; otherwise the
        // first tick afterwards sees the entire clip's travel as one fast step.
        instance.lastLocation = now;
        if (instance.model.isPlayingOnce()) return;
        ModelRig rig = instance.model.rig();
        boolean carrying = base instanceof Mob mob && mob.getTarget() == null;
        String clip = movementClip(rig, instance.clips, speed, carrying);
        instance.model.movementRate(speed >= WALK_SPEED ? instance.clips.movementRate() : 1);
        instance.model.loop(clip);
    }

    public static String movementClip(ModelRig rig, StateClips clips, double speed, boolean carrying) {
        if (speed >= clips.runThreshold() && rig.has(clips.run())) return clips.run();
        if (speed >= WALK_SPEED && rig.has(clips.walk())) return clips.walk();
        if (carrying && rig.has(clips.carry())) return clips.carry();
        return firstPresent(rig, clips.idle(), clips.walk(), null);
    }

    private static String firstPresent(ModelRig rig, String... names) {
        for (String name : names) if (rig.has(name)) return name;
        return rig.animationNames().stream().sorted().findFirst().orElse(null);
    }
}
