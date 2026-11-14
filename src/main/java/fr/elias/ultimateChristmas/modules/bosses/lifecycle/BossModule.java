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
    /** Active fights, keyed by the boss entity's UUID. */
    private final Map<UUID, BossFight> fights = new LinkedHashMap<>();
    private volatile BossParameters parameters;

    private int abilityCounter;

    public BossModule(ChristmasPlatform platform, BossParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        loadTypes();
        listen(new CombatListener());
        command(new BossCommand());

        // One loop drives health bars every second and abilities on the
        // configured interval, so there is a single tracked task.
        timer(this::tick, 20L, 20L);

        publish(BossService.class, this);
    }

    @Override
    protected void onDisable() {
        // Despawn active bosses so none is orphaned when the module stops.
        for (BossFight fight : new ArrayList<>(fights.values())) {
            fight.hideBar();
            fight.releaseChunk();
            platform.modelRenderer().detach(fight.entity().getUniqueId());
            if (fight.alive()) {
                fight.entity().remove();
            }
        }
        fights.clear();
    }

    @Override
    protected void onReload() {
        parameters = BossParameters.from(config(), BossParameters.DEFAULTS);
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
        platform.plugin().getLogger().info("Bosses: " + types.size() + " type(s).");
    }

    /** The per-second loop: refresh bars, drop dead fights, fire abilities. */
    private void tick() {
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
                continue;
            }
            fight.updateBar();
            if (abilityTick) {
                fireAbility(fight);
            }
        }
    }

    private void fireAbility(BossFight fight) {
        List<String> abilities = fight.type().abilities();
        if (abilities.isEmpty() || !(fight.entity() instanceof Mob mob)) {
            return;
        }
        String ability = abilities.get(ThreadLocalRandom.current().nextInt(abilities.size()));
        BossAbilities.run(ability, mob);

        // Play the animation the config maps this ability to (laugh, celebrate...)
        // on the boss's own model, if it has one.
        String clip = fight.type().modelSettings().abilityClips()
                .get(ability.toLowerCase(Locale.ROOT));
        if (clip != null && platform.modelRenderer().has(mob.getUniqueId())) {
            platform.modelRenderer().play(mob.getUniqueId(), clip);
        }
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
        return platform.hooks().models().anyAvailable();
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
                            settings.idle(), settings.walk(), settings.run()));
            if (settings.spawnClip() != null) {
                platform.modelRenderer().play(entity.getUniqueId(), settings.spawnClip());
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
        fight.hideBar();
        fight.releaseChunk();
        platform.modelRenderer().detach(fight.entity().getUniqueId());
        BossType type = fight.type();
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
        if (parameters.removeMinionsOnDeath() && where != null && where.getWorld() != null) {
            where.getWorld().getNearbyEntities(where, 24, 24, 24).stream()
                    .filter(entity -> entity.hasMetadata("uc_boss_minion"))
                    .forEach(org.bukkit.entity.Entity::remove);
        }

        emit(new BossDefeatedEvent(type.id(), contributorIds));
    }

    private final class CombatListener implements Listener {

        /** Tallies damage dealt to a boss, including by arrows. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDamage(EntityDamageByEntityEvent event) {
            // A boss or minion landing a hit plays its attack clip on its model.
            var damager = event.getDamager();
            if (platform.modelRenderer().has(damager.getUniqueId())) {
                BossFight dealer = fights.get(damager.getUniqueId());
                String clip = null;
                if (dealer != null) {
                    clip = dealer.type().modelSettings().attackClip();
                } else if (damager.hasMetadata("uc_boss_minion")) {
                    BossType type = types.get(damager.getMetadata("uc_boss_minion")
                            .get(0).asString().toLowerCase(Locale.ROOT));
                    clip = type == null ? null : type.minionSettings().attackClip();
                }
                if (clip != null) {
                    platform.modelRenderer().play(damager.getUniqueId(), clip);
                }
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

        @EventHandler(priority = EventPriority.MONITOR)
        public void onDeath(EntityDeathEvent event) {
            BossFight fight = fights.get(event.getEntity().getUniqueId());
            if (fight != null) {
                handleDeath(fight);
            }
        }

        /** Gives a freshly summoned minion its boss's configured minion model. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onMinionSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
            org.bukkit.entity.LivingEntity minion = event.getEntity();
            if (!minion.hasMetadata("uc_boss_minion")) {
                return;
            }
            String bossId = minion.getMetadata("uc_boss_minion").get(0).asString();
            if (bossId == null || bossId.isBlank()) {
                return;
            }
            BossType type = types.get(bossId.toLowerCase(Locale.ROOT));
            if (type == null || !type.hasMinionModel()) {
                return;
            }
            // Defer a tick so the minion is fully spawned before we attach
            // display entities and hide it.
            later(() -> {
                if (minion.isValid() && !minion.isDead()) {
                    attachModel(minion, type.minionModel(), type.minionProvider(),
                            type.minionSettings(), "minion of '" + type.id() + "'");
                }
            }, 1L);
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
            return "[control|spawn|despawn|list] [id]";
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
                    for (BossFight fight : new ArrayList<>(fights.values())) {
                        if (args.length < 2
                                || fight.type().id().equalsIgnoreCase(args[1])) {
                            fight.hideBar();
                            fight.releaseChunk();
                            platform.modelRenderer().detach(fight.entity().getUniqueId());
                            if (fight.alive()) {
                                fight.entity().remove();
                            }
                            fights.remove(fight.entity().getUniqueId());
                            removed++;
                        }
                    }
                    platform.messages().sendRaw(sender,
                            "<green>Despawned <white>" + removed + "</white> boss(es).",
                            Map.of());
                }
                default -> throw new CommandError("Usage: /christmas boss " + usage());
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return List.of("control", "spawn", "despawn", "list");
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("spawn")
                    || args[0].equalsIgnoreCase("despawn"))) {
                return new ArrayList<>(types.keySet());
            }
            return List.of();
        }
    }
}
