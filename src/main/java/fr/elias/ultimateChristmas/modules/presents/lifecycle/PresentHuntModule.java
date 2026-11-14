package fr.elias.ultimateChristmas.modules.presents.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.reward.Reward;
import fr.elias.ultimateChristmas.core.reward.RewardTable;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.presents.api.PresentFoundEvent;
import fr.elias.ultimateChristmas.modules.presents.api.PresentService;
import fr.elias.ultimateChristmas.modules.presents.infra.PresentSpawner;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.List;
import java.util.Locale;

/**
 * Presents hidden around the world for players to find.
 *
 * <p>Presents are entity markers rather than placed blocks, so the hunt never
 * modifies the world and nothing is left behind if the module is disabled
 * mid-season.
 */
@PluginModule(value = "presents", dependencies = "platform::platform", configuration = true)
public final class PresentHuntModule extends FeatureModule implements PresentService {

    private PresentSpawner spawner;
    private RewardTable rewards = RewardTable.read(null, null);
    private volatile PresentHuntParameters parameters;

    public PresentHuntModule(ChristmasPlatform platform, PresentHuntParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        spawner = new PresentSpawner(platform, () -> parameters);
        rewards = RewardTable.read(section("rewards"), platform.plugin().getLogger());

        listen(new PresentListener());
        command(new PresentCommand());

        long period = parameters.spawnIntervalSeconds() * 20L;
        timer(this::spawnWave, period, period);

        publish(PresentService.class, this);
    }

    /**
     * Presents left in the world would be orphaned once the module stops, so
     * they are cleared on disable. That makes disable/enable a clean reset
     * rather than an accumulation.
     */
    @Override
    protected void onDisable() {
        int cleared = spawner.clearAll();
        if (cleared > 0) {
            log().info("Removed " + cleared + " hidden present(s) on shutdown.");
        }
    }

    @Override
    protected void onReload() {
        parameters = PresentHuntParameters.from(config(), PresentHuntParameters.DEFAULTS);
        rewards = RewardTable.read(section("rewards"), platform.plugin().getLogger());
    }

    /** One scheduled wave of presents. */
    private void spawnWave() {
        if (!platform.season().active() || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        int active = spawner.active().size();
        int room = parameters.maxActive() - active;
        if (room <= 0) {
            return;
        }
        int spawned = 0;
        for (int index = 0; index < Math.min(room, parameters.perSpawn()); index++) {
            if (spawner.spawnOne() != null) {
                spawned++;
            }
        }
        if (spawned > 0 && parameters.announce()) {
            Bukkit.broadcast(platform.messages().prefixed("presents.announce",
                    Messages.of("count", String.valueOf(spawned))));
        }
    }

    // ------------------------------------------------------------ public API

    @Override
    public int activeCount() {
        return spawner.active().size();
    }

    @Override
    public Location spawnOne() {
        return spawner.spawnOne();
    }

    @Override
    public int clearAll() {
        return spawner.clearAll();
    }

    /** Opens a present for whoever reached it first. */
    private void open(Player player, org.bukkit.entity.Entity present) {
        // Removing before granting means two players clicking in the same tick
        // cannot both be paid for the same present.
        if (!present.isValid()) {
            platform.messages().send(player, "presents.not-yours");
            return;
        }
        Location where = present.getLocation();
        present.remove();

        Reward reward = rewards.roll();
        if (reward == null) {
            platform.messages().send(player, "reward.none-configured");
            return;
        }
        platform.rewards().grant(player, reward);
        platform.progress().record(player, PlayerProfile.STAT_PRESENTS_FOUND, 1);
        emit(new PresentFoundEvent(player.getUniqueId(), reward.id(),
                where.getWorld() == null ? "?" : where.getWorld().getName()));

        Fx.helix(where, org.bukkit.Particle.HAPPY_VILLAGER, 2.0, 0.5);
        Fx.jingle(where);
        platform.messages().send(player, "presents.opened",
                Messages.of("reward", reward.id()));
    }

    private final class PresentListener implements Listener {

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onInteract(PlayerInteractAtEntityEvent event) {
            if (!PresentSpawner.isPresent(event.getRightClicked())) {
                return;
            }
            event.setCancelled(true);
            if (event.getPlayer().hasPermission("ultimatechristmas.presents")) {
                open(event.getPlayer(), event.getRightClicked());
            }
        }

        /** Hitting a present opens it too, which is what players expect. */
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onHit(EntityDamageByEntityEvent event) {
            if (!PresentSpawner.isPresent(event.getEntity())) {
                return;
            }
            event.setCancelled(true);
            if (event.getDamager() instanceof Player player
                    && player.hasPermission("ultimatechristmas.presents")) {
                open(player, event.getEntity());
            }
        }
    }

    /** {@code /christmas presents [status|spawn|clear]}. */
    private final class PresentCommand implements SubCommand {

        @Override
        public String name() {
            return "presents";
        }

        @Override
        public List<String> aliases() {
            return List.of("hunt");
        }

        @Override
        public String permission() {
            return "ultimatechristmas.presents";
        }

        @Override
        public String usage() {
            return "[status|spawn|clear]";
        }

        @Override
        public String description() {
            return "See how many presents are hidden";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "status" -> platform.messages().send(sender, "presents.status",
                        Messages.of("count", String.valueOf(activeCount())));
                case "spawn" -> {
                    requireAdmin(sender);
                    Location where = spawnOne();
                    if (where == null) {
                        throw new CommandError("Could not find anywhere to hide one. "
                                + "Check the worlds, region and distance settings.");
                    }
                    platform.messages().send(sender, "presents.spawned", Messages.of(
                            "x", String.valueOf(where.getBlockX()),
                            "y", String.valueOf(where.getBlockY()),
                            "z", String.valueOf(where.getBlockZ()),
                            "world", where.getWorld() == null
                                    ? "?" : where.getWorld().getName()));
                }
                case "clear" -> {
                    requireAdmin(sender);
                    platform.messages().send(sender, "presents.cleared",
                            Messages.of("count", String.valueOf(clearAll())));
                }
                default -> throw new CommandError("Usage: /christmas presents " + usage());
            }
        }

        private void requireAdmin(CommandSender sender) {
            if (!sender.hasPermission("ultimatechristmas.admin.presents")) {
                throw new CommandError("You may not manage the present hunt.");
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return sender.hasPermission("ultimatechristmas.admin.presents")
                        ? List.of("status", "spawn", "clear")
                        : List.of("status");
            }
            return List.of();
        }
    }
}
