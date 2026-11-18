package fr.elias.ultimateChristmas.modules.santa.infra;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.hook.ModelHook;
import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.item.Keys;
import fr.elias.ultimateChristmas.core.model.DisplayModelRenderer;
import fr.elias.ultimateChristmas.core.model.ModelRig;
import fr.elias.ultimateChristmas.core.model.RigPose;
import fr.elias.ultimateChristmas.core.model.RiggedEntity;
import fr.elias.ultimateChristmas.core.text.Text;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.santa.lifecycle.SantaParameters;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Spawns, animates and despawns the Santa NPC, and tracks the presents he drops.
 *
 * <p>Santa is an invulnerable, AI-less villager tagged in persistent data so he
 * is recognised reliably without depending on his name. When his rig is loaded
 * the villager is hidden under the animated Blockbench model; otherwise he is a
 * named villager (wearing a LibsDisguises skin if configured).
 *
 * <p>He has no vanilla AI, so this class walks him: short strolls around the
 * point he appeared at, resting between them. Each one-shot clip pauses the
 * stroll until it has played, so he never slides while waving or dropping.
 */
public final class SantaManager {

    private static final String ROLE = "santa";
    private static final String GIFT_TAG = "santa";
    private static final String GIFT_BONE = "gift";
    /** The tag 3.x put on its presents, so ones still in inventories keep opening. */
    private static final org.bukkit.NamespacedKey LEGACY_PRESENT =
            org.bukkit.NamespacedKey.fromString("ultimatechristmas:uc_present");
    private static final float TURN_PER_TICK = 18f;
    private static final int STROLL_TIMEOUT_TICKS = 600;

    private final ChristmasPlatform platform;
    private final Supplier<SantaParameters> parameters;

    private UUID santaId;
    private Location home;
    private boolean animated;
    private boolean modelled;

    private Location target;
    private int pauseTicks;
    private int strollTicks;
    private int busyTicks;
    private int dropCountdown;
    private boolean leaving;
    private final Set<UUID> greeted = new HashSet<>();
    private final List<Pending> pending = new ArrayList<>();

    /** Dropped presents and the tick count they expire at. */
    private final Map<UUID, Long> gifts = new LinkedHashMap<>();
    private long ticks;

    private volatile String resolvedTexture;
    private String resolvingFor;
    private boolean warnedItemsAdder;

    private static final class Pending {
        int ticks;
        final Runnable action;

        Pending(int ticks, Runnable action) {
            this.ticks = ticks;
            this.action = action;
        }
    }

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
        return entity != null && ROLE.equals(Keys.getString(
                entity.getPersistentDataContainer(), Keys.npcRole()));
    }

    /** Whether an item is one of Santa's unopened presents, including ones from 3.x. */
    public static boolean isGift(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        var data = stack.getItemMeta().getPersistentDataContainer();
        return GIFT_TAG.equals(Keys.getString(data, Keys.giftType()))
                || (LEGACY_PRESENT != null && data.has(LEGACY_PRESENT));
    }

    /** Whether a ground item was dropped by Santa (not thrown down by a player). */
    private static boolean droppedBySanta(Entity entity) {
        return GIFT_TAG.equals(Keys.getString(entity.getPersistentDataContainer(), Keys.giftType()));
    }

    public boolean isLeaving() {
        return leaving;
    }

    /** True while a one-shot clip is playing or he is on his way out. */
    public boolean isBusy() {
        return leaving || busyTicks > 0;
    }

    // ------------------------------------------------------------ appearance

    /**
     * Spawns Santa at one of the configured locations, or at {@code fallback}
     * when none is configured.
     *
     * @return the spawn location, or {@code null} if none could be used
     */
    public Location spawn(Location fallback) {
        if (isSpawned()) {
            return santa().getLocation();
        }
        Location where = pickLocation();
        if (where == null && fallback != null) {
            where = fallback.clone();
        }
        if (where == null || where.getWorld() == null) {
            return null;
        }
        where.setPitch(0);

        SantaParameters settings = parameters.get();
        World world = where.getWorld();
        Villager villager = world.spawn(where, Villager.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setCollidable(false);
            entity.setRemoveWhenFarAway(false);
            entity.setPersistent(true);
            entity.setProfession(Villager.Profession.NITWIT);
            entity.customName(Text.parse(settings.displayName()));
            entity.setCustomNameVisible(true);
            entity.getPersistentDataContainer()
                    .set(Keys.npcRole(), PersistentDataType.STRING, ROLE);
        });
        this.santaId = villager.getUniqueId();
        this.home = where.clone();
        this.target = null;
        this.pauseTicks = 40;
        this.busyTicks = 0;
        this.leaving = false;
        // The first present comes soon after he arrives, so players see him at it.
        this.dropCountdown = Math.min(200, settings.drops().nextIntervalTicks());
        this.greeted.clear();
        this.pending.clear();

        turn(villager, where.getYaw());
        animated = applyModel(villager, settings.model());
        if (!modelled && !settings.skin().isBlank()) {
            platform.hooks().disguises().disguiseAsPlayer(villager, settings.skin());
        }
        resolveTexture(settings.present().headTexture());

        Fx.helix(where, Particle.END_ROD, 3.0, 1.0);
        Fx.jingle(where);
        perform(settings.model().arrive(), 2.0);
        return where;
    }

    public void despawn() {
        Entity santa = santa();
        pending.clear();
        leaving = false;
        if (santa == null) {
            return;
        }
        DisplayModelRenderer renderer = platform.modelRenderer();
        if (renderer != null) {
            renderer.detach(santa.getUniqueId());
        }
        platform.hooks().disguises().undisguise(santa);
        Fx.burst(santa.getLocation().add(0, 1, 0), Particle.POOF, 20, 0.5);
        santa.remove();
        santaId = null;
        animated = false;
        modelled = false;
        busyTicks = 0;
    }

    /**
     * Plays the farewell clip, then removes Santa once it has finished.
     *
     * @param done run after he is gone
     */
    public void leave(Runnable done) {
        if (!isSpawned() || leaving) {
            return;
        }
        leaving = true;
        target = null;
        int length = perform(parameters.get().model().farewell(), 1.5);
        Entity santa = santa();
        if (santa != null) {
            Fx.sound(santa.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1f, 0.7f);
        }
        pending.add(new Pending(length, () -> {
            despawn();
            done.run();
        }));
    }

    /** Removes leftover Santas and presents across all worlds (e.g. after a crash). */
    public void cleanupStale() {
        for (World world : platform.plugin().getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Villager.class)) {
                if (isSanta(entity) && (santaId == null
                        || !entity.getUniqueId().equals(santaId))) {
                    platform.hooks().disguises().undisguise(entity);
                    entity.remove();
                }
            }
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (droppedBySanta(item) && !gifts.containsKey(item.getUniqueId())) {
                    item.remove();
                }
            }
        }
    }

    /** Removes every present Santa has dropped that nobody collected. */
    public void clearGifts() {
        for (UUID id : gifts.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        gifts.clear();
    }

    public void forgetGift(UUID id) {
        gifts.remove(id);
    }

    public int giftsOnGround() {
        return gifts.size();
    }

    /** Shows his model; returns true only when our renderer animates it. */
    private boolean applyModel(LivingEntity santa, SantaParameters.Model model) {
        modelled = false;
        if (model.provider() == ModelHook.Provider.NONE || model.id().isBlank()) {
            return false;
        }
        DisplayModelRenderer renderer = platform.modelRenderer();
        boolean ourRig = renderer != null && renderer.registry().has(model.id());
        // Unlike a boss, AUTO prefers our rig: an external plugin would only
        // know Santa if the admin imported the model there by hand.
        boolean useDisplay = model.provider() == ModelHook.Provider.DISPLAY
                || (model.provider() == ModelHook.Provider.AUTO && ourRig);
        if (useDisplay) {
            if (!ourRig) {
                platform.plugin().getLogger().warning("Santa wants the display model '"
                        + model.id() + "' but no rig by that name is loaded (rigs/"
                        + model.id() + ".json). Using the plain villager.");
                return false;
            }
            modelled = renderer.attach(santa, model.id(),
                    new RiggedEntity.Placement(model.scale(), model.offsetY(), model.yawOffset()),
                    new DisplayModelRenderer.StateClips(model.idle(), model.walk(), model.walk()));
            return modelled;
        }
        // ModelEngine / BetterModel render him, but their clips are not ours to drive.
        modelled = platform.hooks().models().apply(santa, model.id(), model.provider())
                != ModelHook.Provider.NONE;
        return false;
    }

    // ------------------------------------------------------------- behaviour

    /** Advances strolls, greetings, drops and timed actions. Call every tick. */
    public void tick() {
        ticks++;
        expireGifts();

        Entity entity = santa();
        if (!(entity instanceof LivingEntity santa)) {
            pending.clear();
            return;
        }
        runPending();
        if (busyTicks > 0) {
            busyTicks--;
            return;
        }
        if (leaving) {
            return;
        }
        SantaParameters settings = parameters.get();
        if (ticks % 10 == 0 && greet(santa, settings)) {
            return;
        }
        if (settings.drops().enabled() && --dropCountdown <= 0) {
            dropCountdown = settings.drops().nextIntervalTicks();
            if (gifts.size() < settings.drops().maxOnGround()) {
                dropGift(santa, settings);
                return;
            }
        }
        if (settings.wander().enabled()) {
            stroll(santa, settings.wander());
        }
    }

    /** A gentle particle idle, so Santa is easy to spot. */
    public void idleEffects() {
        Entity santa = santa();
        if (santa != null) {
            Fx.snowfall(santa.getLocation().add(0, 2.5, 0), 1.5, 3);
        }
    }

    /**
     * Plays a one-shot clip and holds Santa still for its length.
     *
     * @param fallbackSeconds how long to pause when no animated model is shown
     * @return the number of ticks he is busy
     */
    public int perform(String clip, double fallbackSeconds) {
        Entity santa = santa();
        double seconds = fallbackSeconds;
        DisplayModelRenderer renderer = platform.modelRenderer();
        if (santa != null && animated && renderer != null && clip != null
                && renderer.play(santa.getUniqueId(), clip, 20)) {
            seconds = renderer.animationLength(santa.getUniqueId(), clip);
        }
        int length = (int) Math.ceil(seconds * 20);
        busyTicks = Math.max(busyTicks, length);
        return length;
    }

    /** Turns Santa toward a point, e.g. the player he is talking to. */
    public void face(Location at) {
        Entity entity = santa();
        if (!(entity instanceof LivingEntity santa) || at.getWorld() != santa.getWorld()) {
            return;
        }
        Location from = santa.getLocation();
        float yaw = yawTowards(at.getX() - from.getX(), at.getZ() - from.getZ());
        turn(santa, yaw);
    }

    /** Has Santa celebrate, if he is free and near the given point. */
    public void celebrateNear(Location at) {
        Entity santa = santa();
        if (santa == null || leaving || busyTicks > 0 || at.getWorld() != santa.getWorld()
                || at.distanceSquared(santa.getLocation()) > 32 * 32) {
            return;
        }
        target = null;
        face(at);
        perform(parameters.get().model().celebrate(), 1.5);
        Fx.burst(santa.getLocation().add(0, 2.2, 0), Particle.HAPPY_VILLAGER, 12, 0.6);
    }

    /** Starts the drop clip now; returns false if he is busy or absent. */
    public boolean dropNow() {
        Entity entity = santa();
        if (!(entity instanceof LivingEntity santa) || leaving || busyTicks > 0) {
            return false;
        }
        dropGift(santa, parameters.get());
        return true;
    }

    /** A player right-clicked him: he turns to them and reacts, if he is free. */
    public void clickedBy(Player player) {
        if (santa() == null || isBusy()) {
            return;
        }
        target = null;
        face(player.getLocation());
        perform(parameters.get().model().clicked(), 1.0);
    }

    private boolean greet(LivingEntity santa, SantaParameters settings) {
        double radius = settings.wander().greetRadius();
        if (radius <= 0) {
            return false;
        }
        for (Player player : santa.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(santa.getLocation()) > radius * radius
                    || !greeted.add(player.getUniqueId())) {
                continue;
            }
            target = null;
            face(player.getLocation());
            perform(settings.model().greet(), 1.5);
            Fx.sound(santa.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 1f, 0.6f);
            return true;
        }
        return false;
    }

    private void dropGift(LivingEntity santa, SantaParameters settings) {
        target = null;
        String clip = settings.model().dropGift();
        int length = perform(clip, 2.0);
        DisplayModelRenderer renderer = platform.modelRenderer();
        ModelRig rig = animated && renderer != null
                ? renderer.registry().get(settings.model().id()) : null;
        GiftLanding landing = GiftLanding.find(rig, clip, GIFT_BONE);

        Location base = santa.getLocation();
        float yaw = santa.getBodyYaw();
        Location rest;
        int landTicks;
        if (landing != null) {
            SantaParameters.Model model = settings.model();
            Vector3f offset = RigPose.rootTransform(model.scale(), model.offsetY(), yaw,
                    model.yawOffset()).transformPosition(new Vector3f(landing.position()));
            rest = base.clone().add(offset.x, offset.y, offset.z);
            landTicks = (int) Math.round(landing.landTime() * 20);
        } else {
            Vector forward = base.getDirection().setY(0).normalize().multiply(0.8);
            rest = base.clone().add(forward);
            landTicks = length / 2;
        }
        rest.setY(Math.max(rest.getY(), base.getY()));

        pending.add(new Pending(landTicks, () -> {
            Fx.sound(rest, Sound.BLOCK_WOOL_PLACE, 1f, 0.8f);
            Fx.burst(rest.clone().add(0, 0.2, 0), Particle.SNOWFLAKE, 10, 0.3);
        }));
        // Hand over from the animated present to a real head as the clip ends,
        // where the model leaves its present resting on the ground.
        pending.add(new Pending(length, () -> spawnGift(rest, settings)));
    }

    private void spawnGift(Location at, SantaParameters settings) {
        if (at.getWorld() == null) {
            return;
        }
        ItemStack stack = presentItem(1);
        Item item = at.getWorld().dropItem(at, stack, dropped -> {
            dropped.setVelocity(new Vector());
            dropped.setPickupDelay(10);
            dropped.setUnlimitedLifetime(true);
            dropped.setCanMobPickup(false);
            dropped.customName(Text.parse(settings.present().name()));
            dropped.setCustomNameVisible(true);
            dropped.getPersistentDataContainer()
                    .set(Keys.giftType(), PersistentDataType.STRING, GIFT_TAG);
        });
        gifts.put(item.getUniqueId(), ticks + settings.drops().lifetimeSeconds() * 20L);
        Fx.sound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        Fx.burst(at.clone().add(0, 0.4, 0), Particle.END_ROD, 8, 0.2);
    }

    /**
     * Builds unopened presents: a textured player head, an ItemsAdder item or a
     * vanilla material, as configured, tagged so they open on right-click.
     */
    public ItemStack presentItem(int amount) {
        SantaParameters.Present present = parameters.get().present();
        ItemStack base = null;
        if ("itemsadder".equals(present.type())) {
            base = platform.hooks().itemsAdder().item(present.itemsAdderId(), amount);
            if (base == null && !warnedItemsAdder) {
                warnedItemsAdder = true;
                platform.plugin().getLogger().warning("Santa's present is set to ItemsAdder item '"
                        + present.itemsAdderId() + "' but it is not available; using a player head.");
            }
        }
        ItemBuilder builder;
        if (base != null) {
            builder = ItemBuilder.wrap(base);
        } else {
            Material material = "material".equals(present.type())
                    ? ItemBuilder.material(present.material(), Material.PLAYER_HEAD)
                    : Material.PLAYER_HEAD;
            builder = ItemBuilder.of(material, amount)
                    .name(present.name())
                    .lore(present.lore())
                    .modelData(present.customModelData());
            String texture = resolvedTexture;
            if (material == Material.PLAYER_HEAD && texture != null) {
                builder.skull(texture);
            }
        }
        ItemStack stack = builder.tag(Keys.giftType(), GIFT_TAG).build();
        if (base == null && resolvedTexture == null && isPlayerName(present.headTexture())
                && stack.getItemMeta() instanceof SkullMeta skull) {
            // Not resolved yet: a name-only profile, which never blocks the main thread.
            skull.setPlayerProfile(Bukkit.createProfile(present.headTexture()));
            stack.setItemMeta(skull);
        }
        return stack;
    }

    /** Re-reads the present's head texture after a reload. */
    public void prepare() {
        warnedItemsAdder = false;
        resolveTexture(parameters.get().present().headTexture());
    }

    /** Fetches a player-name head texture once, off the main thread. */
    private void resolveTexture(String value) {
        if (value == null || value.isBlank()) {
            resolvedTexture = null;
            return;
        }
        if (!isPlayerName(value)) {
            resolvedTexture = value.trim();
            return;
        }
        if (value.equals(resolvingFor)) {
            return;
        }
        resolvingFor = value;
        resolvedTexture = null;
        try {
            Bukkit.createProfile(value).update().thenAccept(profile -> {
                for (ProfileProperty property : profile.getProperties()) {
                    if ("textures".equals(property.getName()) && value.equals(resolvingFor)) {
                        resolvedTexture = property.getValue();
                    }
                }
            }).exceptionally(error -> {
                platform.plugin().getLogger().warning("Could not look up the head texture of '"
                        + value + "' for Santa's presents (" + error.getMessage()
                        + "). Set gifts.present.head-texture to a base64 value instead.");
                return null;
            });
        } catch (RuntimeException ex) {
            resolvingFor = null;
        }
    }

    private static boolean isPlayerName(String value) {
        return value != null && value.trim().matches("[A-Za-z0-9_]{1,16}");
    }

    private void runPending() {
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending next = it.next();
            if (--next.ticks <= 0) {
                it.remove();
                due.add(next.action);
            }
        }
        due.forEach(Runnable::run);
    }

    private void expireGifts() {
        if (ticks % 20 != 0 || gifts.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Long>> it = gifts.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> entry = it.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid()) {
                // Unloaded with its chunk; it is swept by cleanupStale later.
                it.remove();
            } else if (ticks >= entry.getValue()) {
                Fx.burst(entity.getLocation().add(0, 0.3, 0), Particle.POOF, 6, 0.2);
                entity.remove();
                it.remove();
            }
        }
    }

    // ---------------------------------------------------------------- walking

    private void stroll(LivingEntity santa, SantaParameters.Wander wander) {
        if (target == null) {
            if (--pauseTicks > 0) {
                return;
            }
            target = pickTarget(wander.radius());
            strollTicks = 0;
            if (target == null) {
                pauseTicks = 40;
                return;
            }
        }
        Location at = santa.getLocation();
        double dx = target.getX() - at.getX();
        double dz = target.getZ() - at.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance < 0.2 || ++strollTicks > STROLL_TIMEOUT_TICKS) {
            rest(wander);
            return;
        }
        double step = Math.min(wander.speed(), distance);
        double nx = at.getX() + dx / distance * step;
        double nz = at.getZ() + dz / distance * step;
        Double ny = groundAt(at.getWorld(), nx, at.getY(), nz, 1, 3);
        if (ny == null) {
            rest(wander);
            return;
        }
        float yaw = approach(at.getYaw(), yawTowards(dx, dz), TURN_PER_TICK);
        santa.teleport(new Location(at.getWorld(), nx, ny, nz, yaw, 0));
        santa.setBodyYaw(yaw);
    }

    private void rest(SantaParameters.Wander wander) {
        target = null;
        pauseTicks = wander.pauseMinTicks() + ThreadLocalRandom.current()
                .nextInt(Math.max(1, wander.pauseMaxTicks() - wander.pauseMinTicks() + 1));
    }

    private Location pickTarget(double radius) {
        if (home == null || home.getWorld() == null) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = random.nextDouble(Math.PI * 2);
            double distance = random.nextDouble(Math.min(2, radius), radius + 0.001);
            double x = home.getX() + Math.cos(angle) * distance;
            double z = home.getZ() + Math.sin(angle) * distance;
            Double y = groundAt(home.getWorld(), x, home.getY(), z, 4, 4);
            if (y != null) {
                return new Location(home.getWorld(), x, y, z);
            }
        }
        return null;
    }

    /**
     * The standing height at (x, z) within {@code up} blocks above and
     * {@code down} below {@code y}, preferring the level he is already on.
     * Returns {@code null} over liquid, a drop or into a wall.
     */
    static Double groundAt(World world, double x, double y, double z, int up, int down) {
        if (world == null) {
            return null;
        }
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = (int) Math.floor(y + 0.01);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return null;
        }
        List<Integer> levels = new ArrayList<>();
        levels.add(0);
        for (int dy = 1; dy <= Math.max(up, down); dy++) {
            if (dy <= up) levels.add(dy);
            if (dy <= down) levels.add(-dy);
        }
        for (int dy : levels) {
            int feet = by + dy;
            Block feetBlock = world.getBlockAt(bx, feet, bz);
            Block below = feetBlock.getRelative(0, -1, 0);
            Block head = feetBlock.getRelative(0, 1, 0);
            if (feetBlock.isLiquid() || head.isLiquid() || !head.isPassable()) {
                continue;
            }
            if (feetBlock.isPassable() && below.isSolid() && !below.isLiquid()) {
                return (double) feet;
            }
            // A slab, path or carpet underfoot: stand on top of it if there is room.
            double top = feetBlock.getBoundingBox().getMaxY() - feet;
            if (!feetBlock.isPassable() && top > 0 && top <= 0.5
                    && feetBlock.getRelative(0, 2, 0).isPassable()) {
                return feet + top;
            }
        }
        return null;
    }

    private static float yawTowards(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float approach(float from, float to, float maxStep) {
        float delta = ((to - from) % 360 + 540) % 360 - 180;
        return from + Math.max(-maxStep, Math.min(maxStep, delta));
    }

    private static void turn(LivingEntity santa, float yaw) {
        santa.setRotation(yaw, 0);
        santa.setBodyYaw(yaw);
    }

    private Location pickLocation() {
        List<Location> spots = parameters.get().spawnLocations();
        if (spots.isEmpty()) {
            return null;
        }
        return spots.get(ThreadLocalRandom.current().nextInt(spots.size())).clone();
    }
}
