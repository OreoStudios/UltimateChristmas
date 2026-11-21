package fr.elias.ultimateChristmas.modules.bosses.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.reward.Reward;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.core.text.Text;
import fr.elias.ultimateChristmas.modules.bosses.api.BossDefeatedEvent;
import fr.elias.ultimateChristmas.modules.bosses.api.BossService;
import fr.elias.ultimateChristmas.modules.bosses.infra.BossAutoSpawn;
import fr.elias.ultimateChristmas.modules.bosses.infra.BossSpawnClock;
import fr.elias.ultimateChristmas.modules.bosses.infra.BossRegionTrigger;
import fr.elias.ultimateChristmas.modules.bosses.infra.BossRegionCooldowns;
import fr.elias.ultimateChristmas.modules.bosses.infra.BossAbilities;
import fr.elias.ultimateChristmas.modules.bosses.infra.BossControl;
import fr.elias.ultimateChristmas.modules.bosses.infra.BossControlMenu;
import fr.elias.ultimateChristmas.modules.bosses.infra.BossFight;
import fr.elias.ultimateChristmas.modules.bosses.infra.BossType;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Festive boss fights: the Grinch, Krampus and the Frost Monarch.
 *
 * <p>Each boss is a configured mob with inflated stats, a weighted ability it
 * uses on a timer, and loot shared among its top damage-dealers so a fight
 * rewards the people who actually fought it. Damage tracking, the health bar
 * and the ability loop are all owned by this module's lifecycle, so disabling
 * it tears everything down cleanly and despawns any active boss.
 */
@PluginModule(value = "bosses", dependencies = "platform::platform", configuration = true)
public final class BossModule extends FeatureModule implements BossService, BossControl {

    private final Map<String, BossType> types = new LinkedHashMap<>();
    private final BossSpawnClock autoSpawnClock = new BossSpawnClock();
    private final BossRegionCooldowns regionCooldowns = new BossRegionCooldowns();
    private volatile boolean running;
    /** Active fights, keyed by the boss entity's UUID. */
    private final Map<UUID, BossFight> fights = new LinkedHashMap<>();
    private volatile BossParameters parameters;

    private int abilityCounter;
    private final Map<UUID, BossType.ModelSettings> modelSettings = new LinkedHashMap<>();
    private final Map<UUID, UUID> summonedOwners = new LinkedHashMap<>();
    private final Map<UUID, Integer> minionSequence = new LinkedHashMap<>();
    private final java.util.Set<UUID> ownedModels = new java.util.HashSet<>();
    private final java.util.Set<UUID> enraged = new java.util.HashSet<>();

    public BossModule(ChristmasPlatform platform, BossParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        running = true;
        loadTypes();
        listen(new CombatListener());
        listen(new RegionEntryListener());
        command(new BossCommand());

        // One loop drives health bars every second and abilities on the
        // configured interval, so there is a single tracked task.
        timer(this::tick, 20L, 20L);

        publish(BossService.class, this);
    }

    @Override
    protected void onDisable() {
        running = false;
        autoSpawnClock.clear();
        regionCooldowns.clear();
        // Despawn active bosses so none is orphaned when the module stops.
        for (BossFight fight : new ArrayList<>(fights.values())) {
            fight.hideBar();
            fight.releaseChunk();
            platform.modelRenderer().detach(fight.entity().getUniqueId());
            if (fight.alive()) {
                fight.entity().remove();
            }
        }
        for (UUID id : new ArrayList<>(summonedOwners.keySet())) {
            var minion = Bukkit.getEntity(id);
            if (minion != null) minion.remove();
        }
        for (UUID id : ownedModels) platform.modelRenderer().detach(id);
        fights.clear();
        modelSettings.clear();
        summonedOwners.clear();
        minionSequence.clear();
        ownedModels.clear();
        enraged.clear();
    }

    @Override
    protected void onReload() {
        parameters = BossParameters.from(config(), BossParameters.DEFAULTS);
        regionCooldowns.clear();
        loadTypes();
    }

    private void loadTypes() {
        types.clear();
        var section = section("bosses");
        for (String id : section.getKeys(false)) {
            var child = section.getConfigurationSection(id);
            if (child == null) {
                continue;
            }
            BossType type = BossType.read(child, platform.plugin().getLogger());
            if (type != null) {
                types.put(id.toLowerCase(Locale.ROOT), type);
            }
        }
        java.util.Set<String> autoEnabled = new java.util.HashSet<>();
        long now = System.currentTimeMillis();
        for (BossType type : types.values()) {
            if (type.autoSpawn().enabled()) {
                autoEnabled.add(type.id());
                autoSpawnClock.initialize(type.id(), now, type.autoSpawn().intervalSeconds());
            }
        }
        autoSpawnClock.retain(autoEnabled);
        if (!platform.hooks().worldGuard().active() && types.values().stream()
                .anyMatch(type -> type.regionTrigger().enabled())) {
            log().warning("Boss region triggers are configured, but WorldGuard is unavailable; skipping region events.");
        }
        platform.plugin().getLogger().info("Bosses: " + types.size() + " type(s).");
    }

    /** The per-second loop: refresh bars, drop dead fights, fire abilities. */
    private void tick() {
        tickAutomaticSpawns();
        ownedModels.removeIf(id -> !platform.modelRenderer().has(id));
        modelSettings.keySet().removeIf(id -> !ownedModels.contains(id));
        summonedOwners.keySet().removeIf(id -> {
            var entity = Bukkit.getEntity(id);
            return entity == null || !entity.isValid();
        });
        if (fights.isEmpty()) {
            return;
        }
        abilityCounter++;
        boolean abilityTick = abilityCounter >= parameters.abilityIntervalSeconds();
        if (abilityTick) {
            abilityCounter = 0;
        }

        for (BossFight fight : new ArrayList<>(fights.values())) {
            if (!fight.alive()) {
                // Removed by something other than a tracked death (e.g. /kill):
                // clean up its bar, release its chunk and drop it.
                fight.hideBar();
                fight.releaseChunk();
                platform.modelRenderer().detach(fight.entity().getUniqueId());
                fights.remove(fight.entity().getUniqueId());
                removeMinions(fight.entity().getUniqueId(), false);
                enraged.remove(fight.entity().getUniqueId());
                continue;
            }
            fight.updateBar();
            if (abilityTick) {
                fireAbility(fight);
            }
        }
    }

    /**
     * Automatic encounters use the same boss lifecycle as manual summons.
     * Actual entity creation is scheduled for the target location's region,
     * rather than doing world work on the global timer (Folia-safe).
     */
    private void tickAutomaticSpawns() {
        if (!running || !platform.season().active() || Bukkit.getOnlinePlayers().isEmpty()) return;
        long now = System.currentTimeMillis();
        for (BossType type : types.values()) {
            BossAutoSpawn policy = type.autoSpawn();
            if (!policy.enabled() || policy.locations().isEmpty()
                    || activeCount(type.id()) >= policy.maxAlive()) continue;
            if (!autoSpawnClock.claim(type.id(), now, policy.intervalSeconds())) continue;
            Location location = policy.pickLocation();
            if (location == null || location.getWorld() == null) continue;

            Bukkit.getRegionScheduler().execute(platform.plugin(), location, () -> {
                if (!running || !platform.season().active()
                        || activeCount(type.id()) >= policy.maxAlive()) return;
                spawnAt(type.id(), location, policy.announce());
            });
        }
    }

    private long activeCount(String bossId) {
        return fights.values().stream()
                .filter(fight -> fight.alive() && fight.type().id().equalsIgnoreCase(bossId))
                .count();
    }

    private void fireAbility(BossFight fight) {
        List<String> abilities = fight.type().abilities();
        if (abilities.isEmpty() || !fight.alive()) return;
        Mob mob = fight.entity();
        String ability = abilities.get(ThreadLocalRandom.current().nextInt(abilities.size()));
        String clip = fight.type().modelSettings().abilityClips().get(ability.toLowerCase(Locale.ROOT));
        if (clip == null) clip = fight.type().modelSettings().abilityClips().get(BossAbilities.canonicalName(ability));
        boolean animated = platform.modelRenderer().play(mob.getUniqueId(), clip, 40);
        long windup = animated ? Math.min(40, Math.round(
                platform.modelRenderer().animationLength(mob.getUniqueId(), clip) * 10)) : 0;
        later(() -> {
            if (!fight.alive() || !fights.containsKey(mob.getUniqueId())) return;
            int count = fight.type().minionTypes().isEmpty() ? 2 : fight.type().minionTypes().size();
            BossAbilities.run(ability, mob, count, minion -> configureMinion(fight, minion));
        }, Math.max(1, windup));
    }

    private void configureMinion(BossFight owner, org.bukkit.entity.Zombie minion) {
        BossType type = owner.type();
        List<String> variants = type.minionTypes();
        BossType variant = null;
        if (!variants.isEmpty()) {
            int index = minionSequence.merge(owner.entity().getUniqueId(), 1, Integer::sum) - 1;
            variant = types.get(variants.get(index % variants.size()));
        }
        summonedOwners.put(minion.getUniqueId(), owner.entity().getUniqueId());
        if (variant != null) {
            setAttribute(minion, org.bukkit.attribute.Attribute.MAX_HEALTH,
                    variant.health() * type.minionHealthMultiplier());
            minion.setHealth(minion.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
            setAttribute(minion, org.bukkit.attribute.Attribute.MOVEMENT_SPEED, variant.speed());
            setAttribute(minion, org.bukkit.attribute.Attribute.ATTACK_DAMAGE, variant.attackDamage());
            setAttribute(minion, org.bukkit.attribute.Attribute.SCALE, variant.hitboxScale());
            attachModel(minion, variant.model(), variant.modelProvider(), variant.modelSettings(),
                    "minion '" + variant.id() + "'");
        } else if (type.hasMinionModel()) {
            attachModel(minion, type.minionModel(), type.minionProvider(), type.minionSettings(),
                    "minion of '" + type.id() + "'");
        }
        if (owner.entity().getTarget() != null) minion.setTarget(owner.entity().getTarget());
    }

    private static void setAttribute(Mob mob, org.bukkit.attribute.Attribute attribute, double value) {
        var instance = mob.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private void removeMinions(UUID owner, boolean animated) {
        for (var entry : new ArrayList<>(summonedOwners.entrySet())) {
            if (!owner.equals(entry.getValue())) continue;
            UUID id = entry.getKey();
            var entity = Bukkit.getEntity(id);
            BossType.ModelSettings settings = modelSettings.remove(id);
            if (animated && entity instanceof org.bukkit.entity.LivingEntity living && settings != null) {
                platform.modelRenderer().finish(living, settings.deathClip());
            } else {
                platform.modelRenderer().detach(id);
                ownedModels.remove(id);
            }
            if (entity != null) entity.remove();
            summonedOwners.remove(id);
        }
        minionSequence.remove(owner);
    }

    // ------------------------------------------------------------ public API

    @Override
    public java.util.Set<String> bossTypes() {
        return types.keySet();
    }

    @Override
    public boolean anyActive() {
        return fights.values().stream().anyMatch(BossFight::alive);
    }

    @Override
    public boolean isActive(String bossId) {
        String id = bossId.toLowerCase(Locale.ROOT);
        return fights.values().stream()
                .anyMatch(fight -> fight.alive() && fight.type().id().equalsIgnoreCase(id));
    }

    @Override
    public boolean spawn(String bossId, Location where) {
        return spawnAt(bossId, where, true);
    }

    private boolean spawnAt(String bossId, Location where, boolean announce) {
        BossType type = types.get(bossId.toLowerCase(Locale.ROOT));
        if (type == null || where == null || where.getWorld() == null) {
            return false;
        }
        Mob mob = BossFight.spawn(platform, type, where);
        if (type.hasModel()) {
            applyModel(mob, type);
        }

        BossFight fight = new BossFight(platform, type, mob);
        fights.put(mob.getUniqueId(), fight);

        if (announce) {
            if (parameters.taunt()) {
                for (String line : type.spawnLines()) {
                    Bukkit.broadcast(Text.parse(line));
                }
            }
            Bukkit.broadcast(platform.messages().prefixed("boss.spawned", Messages.of(
                    "name", Text.plain(Text.parse(type.displayName())),
                    "x", String.valueOf(where.getBlockX()),
                    "y", String.valueOf(where.getBlockY()),
                    "z", String.valueOf(where.getBlockZ()))));
        }
        Fx.fireworkShow(platform.plugin(), where, 3, 5L);
        return true;
    }

    // ----------------------------------------------------- BossControl (menu)

    @Override
    public java.util.Collection<BossType> types() {
        return types.values();
    }

    @Override
    public BossFight activeFight(String bossId) {
        for (BossFight fight : fights.values()) {
            if (fight.alive() && fight.type().id().equalsIgnoreCase(bossId)) {
                return fight;
            }
        }
        return null;
    }

    @Override
    public int despawn(String bossId) {
        int removed = 0;
        for (BossFight fight : new ArrayList<>(fights.values())) {
            if (fight.type().id().equalsIgnoreCase(bossId)) {
                fight.hideBar();
                fight.releaseChunk();
                platform.modelRenderer().detach(fight.entity().getUniqueId());
                if (fight.alive()) {
                    fight.entity().remove();
                }
                fights.remove(fight.entity().getUniqueId());
                removeMinions(fight.entity().getUniqueId(), false);
                enraged.remove(fight.entity().getUniqueId());
                removed++;
            }
        }
        return removed;
    }

    @Override
    public ChristmasPlatform platform() {
        return platform;
    }

    @Override
    public boolean modelPluginAvailable() {
        return platform.modelRenderer().registry().has("grinch")
                || platform.hooks().models().anyAvailable();
    }

    /** Opens the boss control-center menu for an admin. */
    public void openControlCenter(Player admin) {
        platform.menus().open(admin, new BossControlMenu(this));
    }

    /**
     * Applies a boss's custom model, choosing the renderer.
     *
     * <p>The {@code display} provider (and {@code auto} when our rig exists)
     * uses the plugin's own animated renderer — no third-party plugin. Otherwise
     * it falls back to ModelEngine / BetterModel via the hook. Either way an
     * absent model leaves the vanilla mob, so nothing breaks.
     */
    private void applyModel(Mob mob, BossType type) {
        attachModel(mob, type.model(), type.modelProvider(), type.modelSettings(),
                "boss '" + type.id() + "'");
    }

    /**
     * Attaches a model to any living entity (a boss or a minion), choosing the
     * renderer and falling back to the vanilla entity if nothing applies.
     */
    private void attachModel(org.bukkit.entity.LivingEntity entity, String modelId,
                             fr.elias.ultimateChristmas.core.hook.ModelHook.Provider provider,
                             BossType.ModelSettings settings, String who) {
        boolean ourRig = platform.modelRenderer() != null
                && platform.modelRenderer().registry().has(modelId);
        boolean useDisplay = provider
                == fr.elias.ultimateChristmas.core.hook.ModelHook.Provider.DISPLAY
                || (provider == fr.elias.ultimateChristmas.core.hook.ModelHook.Provider.AUTO
                    && ourRig && !platform.hooks().models().anyAvailable());

        if (useDisplay) {
            if (!ourRig) {
                platform.plugin().getLogger().warning(who + " wants the display model '"
                        + modelId + "' but no rig by that name is loaded (rigs/" + modelId
                        + ".json). Using the vanilla entity.");
                return;
            }
            platform.modelRenderer().attach(entity, modelId,
                    new fr.elias.ultimateChristmas.core.model.RiggedEntity.Placement(
                            settings.scale(), settings.offsetY(), settings.yawOffset()),
                    new fr.elias.ultimateChristmas.core.model.DisplayModelRenderer.StateClips(
                            settings.idle(), settings.walk(), settings.run(), settings.carryClip(),
                            settings.runThreshold(), settings.movementRate()));
            modelSettings.put(entity.getUniqueId(), settings);
            ownedModels.add(entity.getUniqueId());
            if (settings.spawnClip() != null) {
                platform.modelRenderer().play(entity.getUniqueId(), settings.spawnClip(), 10);
            }
            return;
        }

        var applied = platform.hooks().models().apply(entity, modelId, provider);
        if (applied == fr.elias.ultimateChristmas.core.hook.ModelHook.Provider.NONE
                && platform.hooks().models().anyAvailable()) {
            platform.plugin().getLogger().warning(who + " has model '" + modelId
                    + "' but it could not be applied; using the vanilla entity.");
        }
    }

    /** Resolves a config or admin spawn location for a boss. */
    private Location resolveSpawn(BossType type, CommandSender sender) {
        if (type.spawnLocation() != null) {
            return type.spawnLocation();
        }
        if (sender instanceof Player player) {
            return player.getLocation();
        }
        return null;
    }

    // ------------------------------------------------------------- death flow

    private void handleDeath(BossFight fight) {
        fights.remove(fight.entity().getUniqueId());
        BossAutoSpawn spawnPolicy = fight.type().autoSpawn();
        if (spawnPolicy.enabled()) {
            autoSpawnClock.afterDeath(fight.type().id(), System.currentTimeMillis(),
                    spawnPolicy.respawnSeconds());
        }
        fight.hideBar();
        fight.releaseChunk();
        BossType type = fight.type();
        platform.modelRenderer().finish(fight.entity(), type.modelSettings().deathClip());
        modelSettings.remove(fight.entity().getUniqueId());
        enraged.remove(fight.entity().getUniqueId());
        Location where = fight.location();

        if (parameters.taunt()) {
            for (String line : type.deathLines()) {
                Bukkit.broadcast(Text.parse(line));
            }
        }

        List<Map.Entry<UUID, Double>> top = fight.topContributors(type.rewardTop());
        List<UUID> contributorIds = new ArrayList<>();
        top.forEach(entry -> contributorIds.add(entry.getKey()));

        if (top.isEmpty() || fight.type().rewards().isEmpty()) {
            Bukkit.broadcast(platform.messages().prefixed("boss.escaped",
                    Messages.of("name", Text.plain(Text.parse(type.displayName())))));
        } else {
            Bukkit.broadcast(platform.messages().prefixed("boss.defeated",
                    Messages.of("name", Text.plain(Text.parse(type.displayName())))));

            Map.Entry<UUID, Double> best = top.get(0);
            Player bestPlayer = Bukkit.getPlayer(best.getKey());
            if (bestPlayer != null) {
                Bukkit.broadcast(platform.messages().prefixed("boss.top-damage", Messages.of(
                        "name", Text.plain(Text.parse(type.displayName())),
                        "player", bestPlayer.getName(),
                        "damage", String.valueOf(Math.round(best.getValue())))));
            }

            // Each top contributor rolls the reward table independently, so the
            // fight rewards everyone who mattered, not just the finisher.
            for (Map.Entry<UUID, Double> entry : top) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) {
                    continue;
                }
                platform.progress().record(player, PlayerProfile.STAT_BOSSES_SLAIN, 1);
                Reward reward = fight.type().rewards().roll();
                if (reward != null) {
                    platform.rewards().grant(player, reward);
                }
            }
        }

        if (where != null) {
            Fx.fireworkShow(platform.plugin(), where, 8, 6L);
        }
        if (parameters.removeMinionsOnDeath()) removeMinions(fight.entity().getUniqueId(), true);

        emit(new BossDefeatedEvent(type.id(), contributorIds));
    }

    /**
     * Fire when a player ENTERS a named WorldGuard region, not on every move
     * within it. Both the global per-region cooldown and per-boss max-active
     * guard prevent repeated or concurrent encounters.
     */
    private final class RegionEntryListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onMove(PlayerMoveEvent event) {
            if (!running || !platform.season().active()
                    || !platform.hooks().worldGuard().active()) return;
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null || (from.getWorld() == to.getWorld()
                    && from.getBlockX() == to.getBlockX()
                    && from.getBlockY() == to.getBlockY()
                    && from.getBlockZ() == to.getBlockZ())) return;
            if (types.values().stream().noneMatch(type -> type.regionTrigger().enabled())) return;

            java.util.Set<String> entered = new java.util.HashSet<>();
            for (String region : platform.hooks().worldGuard().regionsAt(to)) {
                entered.add(region.toLowerCase(Locale.ROOT));
            }
            for (String region : platform.hooks().worldGuard().regionsAt(from)) {
                entered.remove(region.toLowerCase(Locale.ROOT));
            }
            if (entered.isEmpty()) return;

            long now = System.currentTimeMillis();
            for (BossType type : types.values()) {
                BossRegionTrigger trigger = type.regionTrigger();
                if (!trigger.enabled() || activeCount(type.id()) >= trigger.maxAlive()) continue;
                for (String region : trigger.regions()) {
                    if (!entered.contains(region)) continue;
                    if (!regionCooldowns.claim(type.id(), region, now, trigger.cooldownSeconds())) continue;

                    // Use the configured boss coordinates, falling back to the entry point.
                    Location destination = type.autoSpawn().pickLocation();
                    if (destination == null) destination = to.clone();
                    Location target = destination;
                    Bukkit.getRegionScheduler().execute(platform.plugin(), target, () -> {
                        if (!running || !platform.season().active()
                                || activeCount(type.id()) >= trigger.maxAlive()) return;
                        spawn(type.id(), target);
                    });
                    break;
                }
            }
        }
    }

    private final class CombatListener implements Listener {

        /** Tallies damage dealt to a boss, including by arrows. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDamage(EntityDamageByEntityEvent event) {
            var damager = event.getDamager();
            BossType.ModelSettings settings = modelSettings.get(damager.getUniqueId());
            if (settings != null && event.getFinalDamage() > 0) {
                platform.modelRenderer().play(damager.getUniqueId(), settings.attackClip(), 30);
            }

            BossFight fight = fights.get(event.getEntity().getUniqueId());
            if (fight == null) {
                return;
            }
            Player attacker = resolveAttacker(event.getDamager());
            if (attacker != null) {
                fight.recordDamage(attacker, event.getFinalDamage());
                platform.progress().record(attacker,
                        PlayerProfile.STAT_BOSS_DAMAGE, Math.round(event.getFinalDamage()));
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHurt(org.bukkit.event.entity.EntityDamageEvent event) {
            if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity living)
                    || event.getFinalDamage() <= 0 || living.getHealth() <= event.getFinalDamage()) return;
            BossType.ModelSettings settings = modelSettings.get(living.getUniqueId());
            if (settings == null) return;
            BossFight fight = fights.get(living.getUniqueId());
            if (fight != null && !enraged.contains(living.getUniqueId())
                    && (living.getHealth() - event.getFinalDamage()) / fight.maxHealth() <= settings.rageThreshold()) {
                if (platform.modelRenderer().play(living.getUniqueId(), settings.rageClip(), 50)) {
                    enraged.add(living.getUniqueId());
                    return;
                }
            }
            platform.modelRenderer().play(living.getUniqueId(), settings.hurtClip(), 20);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onDeath(EntityDeathEvent event) {
            UUID id = event.getEntity().getUniqueId();
            BossFight fight = fights.get(id);
            if (fight != null) {
                handleDeath(fight);
            } else {
                BossType.ModelSettings settings = modelSettings.remove(id);
                if (settings != null) platform.modelRenderer().finish(event.getEntity(), settings.deathClip());
                summonedOwners.remove(id);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
            if (!(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent hit)) return;
            org.bukkit.entity.Entity killer = hit.getDamager();
            if (killer instanceof org.bukkit.entity.Projectile projectile
                    && projectile.getShooter() instanceof org.bukkit.entity.Entity shooter) killer = shooter;
            BossType.ModelSettings settings = modelSettings.get(killer.getUniqueId());
            if (settings != null) platform.modelRenderer().play(killer.getUniqueId(), settings.victoryClip(), 60);
        }

        /** The player behind a hit, whether melee or a projectile they fired. */
        private Player resolveAttacker(org.bukkit.entity.Entity damager) {
            if (damager instanceof Player player) {
                return player;
            }
            if (damager instanceof org.bukkit.entity.Projectile projectile) {
                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof Player player) {
                    return player;
                }
            }
            return null;
        }
    }

    /** {@code /christmas boss <spawn|despawn|list> [id]}. */
    private final class BossCommand implements SubCommand {

        @Override
        public String name() {
            return "boss";
        }

        @Override
        public List<String> aliases() {
            return List.of("bosses");
        }

        @Override
        public String permission() {
            return "ultimatechristmas.admin.boss";
        }

        @Override
        public String usage() {
            return "[control|spawn|despawn|list|animate] [id] [clip]";
        }

        @Override
        public String description() {
            return "Open the boss control center, or spawn/despawn a boss";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            // With no arguments a player gets the control center; the console
            // gets the text list, since it cannot open a menu.
            String action = args.length == 0
                    ? (sender instanceof Player ? "control" : "list")
                    : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "control", "menu", "gui" -> {
                    if (!(sender instanceof Player player)) {
                        throw new CommandError("Only a player can open the control center.");
                    }
                    openControlCenter(player);
                }
                case "list" -> platform.messages().sendRaw(sender,
                        "<gray>Bosses: <white>" + String.join(", ", types.keySet()),
                        Map.of());
                case "spawn" -> {
                    if (args.length < 2) {
                        throw new CommandError("Which boss? One of: "
                                + String.join(", ", types.keySet()));
                    }
                    BossType type = types.get(args[1].toLowerCase(Locale.ROOT));
                    if (type == null) {
                        throw new CommandError("No boss called '" + args[1] + "'.");
                    }
                    Location where = resolveSpawn(type, sender);
                    if (where == null) {
                        throw new CommandError("No spawn location is set for '" + type.id()
                                + "', and you are not a player. Set 'spawn' in bosses.yml.");
                    }
                    if (!spawn(type.id(), where)) {
                        throw new CommandError("That boss could not be spawned.");
                    }
                }
                case "despawn", "kill" -> {
                    int removed = 0;
                    for (String id : new ArrayList<>(types.keySet())) {
                        if (args.length < 2 || args[1].equalsIgnoreCase(id)) removed += despawn(id);
                    }
                    platform.messages().sendRaw(sender,
                            "<green>Despawned <white>" + removed + "</white> boss(es).", Map.of());
                }
                case "animate" -> {
                    if (args.length < 3) throw new CommandError("Usage: /christmas boss animate <id> <clip>");
                    BossFight fight = activeFight(args[1]);
                    if (fight == null) throw new CommandError("Spawn that boss first.");
                    if (!platform.modelRenderer().play(fight.entity().getUniqueId(), args[2], 70)) {
                        throw new CommandError("Clip unavailable or already playing. Available: "
                                + String.join(", ", platform.modelRenderer().animationNames(fight.entity().getUniqueId())));
                    }
                    platform.messages().sendRaw(sender, "<green>Playing <white>" + args[2], Map.of());
                }
                default -> throw new CommandError("Usage: /christmas boss " + usage());
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return List.of("control", "spawn", "despawn", "list", "animate");
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("spawn")
                    || args[0].equalsIgnoreCase("despawn") || args[0].equalsIgnoreCase("animate"))) {
                return new ArrayList<>(types.keySet());
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("animate")) {
                BossType type = types.get(args[1].toLowerCase(Locale.ROOT));
                var rig = type == null ? null : platform.modelRenderer().registry().get(type.model());
                if (rig != null) return rig.animationNames().stream().sorted().toList();
            }
            return List.of();
        }
    }
}
