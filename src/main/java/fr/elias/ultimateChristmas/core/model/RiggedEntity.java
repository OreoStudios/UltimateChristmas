package fr.elias.ultimateChristmas.core.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/** One Blockbench rig rendered with item displays and native ZYX bone transforms. */
public final class RiggedEntity {
    public record Placement(float scale, double offsetY, float yawOffset) {
        public static final Placement DEFAULT = new Placement(1, 0, 180);
    }

    private final ModelRig rig;
    private final Placement placement;
    private final AnimationPlayback playback;
    private final Map<String, ItemDisplay> displays = new HashMap<>();
    private boolean wasGlowing;
    private boolean wasInvisible;
    private boolean couldPickupItems;
    private ItemStack[] armor;
    private ItemStack mainHand;
    private ItemStack offHand;
    private double movementRate = 1;

    public RiggedEntity(Plugin plugin, ModelRig rig, Placement placement) {
        this.rig = rig;
        this.placement = placement;
        this.playback = new AnimationPlayback(rig);
    }

    public void spawn(LivingEntity base) {
        for (ModelRig.Bone bone : rig.bones()) {
            if (!bone.hasModel()) continue;
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setItemModel(bone.model());
            item.setItemMeta(meta);
            ItemDisplay display = base.getWorld().spawn(base.getLocation(), ItemDisplay.class, spawned -> {
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
        wasInvisible = base.isInvisible();
        wasGlowing = base.isGlowing();
        couldPickupItems = base.getCanPickupItems();
        base.setInvisible(true);
        base.setGlowing(false);
        base.setCanPickupItems(false);
        if (base.getEquipment() != null) {
            armor = base.getEquipment().getArmorContents();
            mainHand = base.getEquipment().getItemInMainHand();
            offHand = base.getEquipment().getItemInOffHand();
            base.getEquipment().clear();
        }
    }

    public void remove(LivingEntity base) {
        displays.values().forEach(ItemDisplay::remove);
        displays.clear();
        if (base != null && base.isValid() && !base.isDead()) {
            base.setInvisible(wasInvisible);
            base.setGlowing(wasGlowing);
            base.setCanPickupItems(couldPickupItems);
            if (base.getEquipment() != null && armor != null) {
                base.getEquipment().setArmorContents(armor);
                base.getEquipment().setItemInMainHand(mainHand);
                base.getEquipment().setItemInOffHand(offHand);
            }
        }
    }

    public boolean isSpawned() { return !displays.isEmpty(); }
    public int displayCount() { return displays.size(); }
    public ModelRig rig() { return rig; }
    public void loop(String animation) { playback.loop(animation); }
    public boolean playOnce(String animation, int priority) { return playback.play(animation, priority); }
    public boolean finish(String animation) { return playback.finish(animation); }
    public boolean isPlayingOnce() { return playback.playingOnce(); }
    public void movementRate(double rate) { movementRate = rate; }

    public void tick(double dt, LivingEntity base) {
        tick(dt, base.getLocation(), base.getBodyYaw());
    }

    /** A saved anchor lets death finish after the base entity has disappeared. */
    public void tick(double dt, Location location, float bodyYaw) {
        if (displays.isEmpty()) return;
        playback.advance(dt, movementRate);
        Map<String, Matrix4f> pose = RigPose.evaluate(rig, playback.loop(), playback.loopTime(),
                playback.shot(), playback.shotTime());
        Location anchor = location.clone();
        anchor.setYaw(0);
        anchor.setPitch(0);
        // Item models are compiled around (8,8,8), the ItemDisplay center.
        // Full affine matrices preserve scale inheritance, including blinking.
        Matrix4f root = RigPose.rootTransform(placement.scale(), placement.offsetY(),
                bodyYaw, placement.yawOffset());
        for (ModelRig.Bone bone : rig.bones()) {
            ItemDisplay display = displays.get(bone.name());
            if (display == null || !display.isValid()) continue;
            float[] offset = bone.modelOffset();
            Matrix4f transform = RigPose.itemDisplayTransform(root, pose.get(bone.name()), offset);
            display.teleport(anchor);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTransformationMatrix(transform);
        }
    }
}
