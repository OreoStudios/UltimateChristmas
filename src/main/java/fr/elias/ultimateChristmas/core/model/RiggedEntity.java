package fr.elias.ultimateChristmas.core.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One animated model attached to a living entity, drawn with display entities.
 *
 * <p>Each bone that has geometry gets its own {@link ItemDisplay}. Every tick
 * the rig is walked parent-first to build each bone's world transform from the
 * active animation, and that transform is written to the bone's display. The
 * base entity is made invisible so only the model shows.
 *
 * <p>This is a static-skeleton animation player: it interpolates the keyframes
 * the model was authored with, which covers walk / attack / idle and any other
 * named clip. It does not do inverse kinematics or physics.
 *
 * <h4>Conventions and tuning</h4>
 * Rotation order follows Minecraft model parts (Z, then Y, then X). Scale,
 * ground offset and facing are caller-supplied because the exact numbers depend
 * on the model and cannot be checked without a client; the boss config exposes
 * them as {@code model.scale}, {@code model.offset-y} and {@code model.yaw-offset}.
 */
public final class RiggedEntity {

    /** How a model sits on its entity. */
    public record Placement(float scale, double offsetY, float yawOffset) {

        public static final Placement DEFAULT = new Placement(1.0f, 0.0, 180f);
    }

    private static final float PX_PER_BLOCK = 16f;
    private static final Quaternionf IDENTITY = new Quaternionf();

    private final Plugin plugin;
    private final ModelRig rig;
    private final Placement placement;
    private final Map<String, ModelRig.Bone> boneByName = new HashMap<>();
    private final Map<String, ItemDisplay> displays = new HashMap<>();

    // Scratch transforms reused each tick to avoid per-bone allocation churn.
    private final Map<String, Quaternionf> worldRot = new HashMap<>();
    private final Map<String, Vector3f> worldPos = new HashMap<>();

    private String loopAnimation;
    private double loopTime;
    private String oneShot;
    private double oneShotTime;

    public RiggedEntity(Plugin plugin, ModelRig rig, Placement placement) {
        this.plugin = plugin;
        this.rig = rig;
        this.placement = placement;
        for (ModelRig.Bone bone : rig.bones()) {
            boneByName.put(bone.name(), bone);
        }
    }

    // ------------------------------------------------------------- lifecycle

    /** Spawns the bone displays and hides the base entity's own body. */
    public void spawn(LivingEntity base) {
        Location at = base.getLocation();
        for (ModelRig.Bone bone : rig.bones()) {
            if (!bone.hasModel()) {
                continue;
            }
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setItemModel(bone.model());
            item.setItemMeta(meta);

            ItemDisplay display = base.getWorld().spawn(at, ItemDisplay.class, spawned -> {
                spawned.setItemStack(item);
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                spawned.setBillboard(Display.Billboard.FIXED);
                spawned.setPersistent(false);
                spawned.setInvulnerable(true);
                spawned.setInterpolationDelay(0);
                spawned.setInterpolationDuration(1);
                spawned.setTeleportDuration(1);
            });
            displays.put(bone.name(), display);
        }
        base.setInvisible(true);
    }

    /** Removes the bone displays and reveals the base entity again. */
    public void remove(LivingEntity base) {
        displays.values().forEach(ItemDisplay::remove);
        displays.clear();
        if (base != null) {
            base.setInvisible(false);
        }
    }

    public boolean isSpawned() {
        return !displays.isEmpty();
    }

    // ------------------------------------------------------------- animation

    /** Sets the looping clip (idle/walk/run/...). No-op if already playing it. */
    public void loop(String animation) {
        if (animation == null || animation.equals(loopAnimation) || !rig.has(animation)) {
            return;
        }
        loopAnimation = animation;
        loopTime = 0;
    }

    /** Plays a clip once over the loop (attack/laugh/steal/...). */
    public void playOnce(String animation) {
        if (rig.has(animation)) {
            oneShot = animation;
            oneShotTime = 0;
        }
    }

    public boolean isPlayingOnce() {
        return oneShot != null;
    }

    /**
     * Advances the animation by {@code dt} seconds and redraws.
     *
     * @param base the entity the model is attached to
     */
    public void tick(double dt, LivingEntity base) {
        if (displays.isEmpty() || base == null) {
            return;
        }

        ModelRig.Animation loop = rig.animation(loopAnimation);
        ModelRig.Animation shot = rig.animation(oneShot);

        if (loop != null) {
            loopTime += dt;
            if (loop.loop()) {
                loopTime %= loop.length();
            } else if (loopTime > loop.length()) {
                loopTime = loop.length();
            }
        }
        if (shot != null) {
            oneShotTime += dt;
            if (oneShotTime >= shot.length()) {
                oneShot = null;
                shot = null;
            }
        }

        computeWorldTransforms(loop, shot);
        apply(base);
    }

    // --------------------------------------------------------- forward kinematics

    private void computeWorldTransforms(ModelRig.Animation loop, ModelRig.Animation shot) {
        worldRot.clear();
        worldPos.clear();

        for (ModelRig.Bone bone : rig.bones()) {
            // Local rotation and position: a one-shot that animates this bone
            // wins, so an attack can move the arms while the legs keep walking.
            Quaternionf localRot = new Quaternionf();
            Vector3f localOffset = new Vector3f();
            boolean fromShot = shot != null && shot.bones().containsKey(bone.name());
            ModelRig.Animation source = fromShot ? shot : loop;
            double time = fromShot ? oneShotTime : loopTime;
            if (source != null) {
                ModelRig.Channels channels = source.bones().get(bone.name());
                if (channels != null) {
                    applyEuler(localRot, sample(channels.rotation(), time));
                    float[] pos = sample(channels.position(), time);
                    localOffset.set(pos[0] / PX_PER_BLOCK, pos[1] / PX_PER_BLOCK,
                            pos[2] / PX_PER_BLOCK);
                }
            }

            Vector3f pivot = new Vector3f(bone.pivot()[0] / PX_PER_BLOCK,
                    bone.pivot()[1] / PX_PER_BLOCK, bone.pivot()[2] / PX_PER_BLOCK);

            if (bone.parent() == null || !worldRot.containsKey(bone.parent())) {
                worldRot.put(bone.name(), new Quaternionf(localRot));
                worldPos.put(bone.name(), pivot.add(localOffset));
            } else {
                Quaternionf parentRot = worldRot.get(bone.parent());
                Vector3f parentPos = worldPos.get(bone.parent());
                ModelRig.Bone parentBone = boneByName.get(bone.parent());
                Vector3f parentPivot = new Vector3f(
                        parentBone.pivot()[0] / PX_PER_BLOCK,
                        parentBone.pivot()[1] / PX_PER_BLOCK,
                        parentBone.pivot()[2] / PX_PER_BLOCK);

                // Offset of this bone's pivot from its parent's, in the parent frame.
                Vector3f offset = new Vector3f(pivot).sub(parentPivot).add(localOffset);
                parentRot.transform(offset);

                worldRot.put(bone.name(), new Quaternionf(parentRot).mul(localRot));
                worldPos.put(bone.name(), new Vector3f(parentPos).add(offset));
            }
        }
    }

    private void apply(LivingEntity base) {
        Location anchor = base.getLocation().clone();
        anchor.setYaw(0);
        anchor.setPitch(0);

        // Rotate the whole model to the entity's facing. The offset constant is
        // config-tunable because a model's "front" is authored by hand.
        Quaternionf yaw = new Quaternionf().rotationY(
                (float) Math.toRadians(-(base.getBodyYaw() + placement.yawOffset())));
        float scale = placement.scale();

        for (Map.Entry<String, ItemDisplay> entry : displays.entrySet()) {
            ItemDisplay display = entry.getValue();
            if (!display.isValid()) {
                continue;
            }
            Quaternionf rot = new Quaternionf(yaw).mul(worldRot.get(entry.getKey()));
            Vector3f pos = new Vector3f(worldPos.get(entry.getKey()));
            yaw.transform(pos);
            pos.y += (float) placement.offsetY();

            display.teleport(anchor);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTransformation(new Transformation(
                    new Vector3f(pos.x * scale, pos.y * scale, pos.z * scale),
                    rot, new Vector3f(scale, scale, scale), new Quaternionf(IDENTITY)));
        }
    }

    // ------------------------------------------------------------- sampling

    /** Rotation keyframes are Euler degrees; apply in Minecraft's Z, Y, X order. */
    private void applyEuler(Quaternionf target, float[] euler) {
        target.rotationZYX((float) Math.toRadians(euler[2]),
                (float) Math.toRadians(euler[1]), (float) Math.toRadians(euler[0]));
    }

    /**
     * Linear-interpolates a channel at {@code time}.
     *
     * @return the (x, y, z) value, or zeros if the channel is empty
     */
    private float[] sample(List<ModelRig.Keyframe> frames, double time) {
        if (frames.isEmpty()) {
            return new float[]{0, 0, 0};
        }
        if (frames.size() == 1 || time <= frames.get(0).time()) {
            ModelRig.Keyframe only = frames.get(0);
            return new float[]{only.x(), only.y(), only.z()};
        }
        ModelRig.Keyframe last = frames.get(frames.size() - 1);
        if (time >= last.time()) {
            return new float[]{last.x(), last.y(), last.z()};
        }
        for (int index = 0; index < frames.size() - 1; index++) {
            ModelRig.Keyframe a = frames.get(index);
            ModelRig.Keyframe b = frames.get(index + 1);
            if (time >= a.time() && time <= b.time()) {
                double span = b.time() - a.time();
                float t = span <= 0 ? 0 : (float) ((time - a.time()) / span);
                return new float[]{
                        lerp(a.x(), b.x(), t),
                        lerp(a.y(), b.y(), t),
                        lerp(a.z(), b.z(), t)};
            }
        }
        return new float[]{last.x(), last.y(), last.z()};
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public ModelRig rig() {
        return rig;
    }
}
