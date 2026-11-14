package fr.elias.ultimateChristmas.modules.santa.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.reward.Reward;
import fr.elias.ultimateChristmas.core.reward.RewardTable;
import fr.elias.ultimateChristmas.core.season.Season;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.santa.api.SantaService;
import fr.elias.ultimateChristmas.modules.santa.infra.SantaManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * A wandering Santa NPC who hands out gifts.
 *
 * <p>Santa appears on a timer during the season, stays a while, then leaves. A
 * player right-clicks him to receive a gift, limited to once per configured
 * window so he cannot be farmed. His appearances are driven by a single
 * scheduled loop owned by the module lifecycle, so disabling the module stops
 * everything cleanly.
 */
@PluginModule(value = "santa", dependencies = "platform::platform", configuration = true)
public final class SantaModule extends FeatureModule implements SantaService {

    private SantaManager manager;
    private RewardTable gifts = RewardTable.read(null, null);
    private volatile SantaParameters parameters;

    /** Epoch millis when Santa is next allowed to appear automatically. */
    private long nextAutoSpawn;
    /** Epoch millis when the current appearance should end, or 0 if absent. */
    private long leaveAt;

    public SantaModule(ChristmasPlatform platform, SantaParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        manager = new SantaManager(platform, () -> parameters);
        manager.cleanupStale();
        gifts = RewardTable.read(section("gifts.rewards"), platform.plugin().getLogger());

        listen(new InteractListener());
        command(new SantaCommand());

        nextAutoSpawn = System.currentTimeMillis()
                + parameters.cooldownSeconds() * 1000L;
        timer(this::tick, 40L, 40L);

        publish(SantaService.class, this);
    }

    @Override
    protected void onDisable() {
        if (manager != null) {
            manager.despawn();
        }
    }

    @Override
    protected void onReload() {
        parameters = SantaParameters.from(config(), SantaParameters.DEFAULTS);
        gifts = RewardTable.read(section("gifts.rewards"), platform.plugin().getLogger());
    }

    /** The appearance loop: spawn when due, leave when his time is up. */
    private void tick() {
        long now = System.currentTimeMillis();

        if (manager.isSpawned()) {
            manager.idleEffects();
            if (leaveAt > 0 && now >= leaveAt) {
                leave();
            }
            return;
        }

        // Only appear on his own when the season is active and players are on.
        if (parameters.autoSpawn() && now >= nextAutoSpawn
                && platform.season().active() && !Bukkit.getOnlinePlayers().isEmpty()) {
            appear(true);
        }
    }

    private void appear(boolean announce) {
        Location where = manager.spawn();
        if (where == null) {
            // No usable spawn point: back off so we do not retry every tick.
            nextAutoSpawn = System.currentTimeMillis() + 60_000L;
            return;
        }
        leaveAt = System.currentTimeMillis() + parameters.staySeconds() * 1000L;
        if (announce) {
            Bukkit.broadcast(platform.messages().prefixed("santa.spawned", Messages.of(
                    "x", String.valueOf(where.getBlockX()),
                    "y", String.valueOf(where.getBlockY()),
                    "z", String.valueOf(where.getBlockZ()),
                    "world", where.getWorld() == null ? "?" : where.getWorld().getName())));
        }
    }

    private void leave() {
        Location where = location();
        manager.despawn();
        leaveAt = 0;
        nextAutoSpawn = System.currentTimeMillis() + parameters.cooldownSeconds() * 1000L;
        if (where != null) {
            Bukkit.broadcast(platform.messages().prefixed("santa.left", java.util.Map.of()));
        }
    }

    /** Gives a gift, respecting the per-player cooldown. */
    private void giveGift(Player player) {
        PlayerProfile profile = platform.profiles().get(player);
        if (parameters.giftCooldownHours() > 0 && profile.onCooldown("santa-gift")) {
            platform.messages().send(player, "santa.cooldown", Messages.of(
                    "remaining", Season.format(Duration.ofMillis(
                            profile.cooldownRemaining("santa-gift")))));
            return;
        }
        Reward reward = gifts.roll();
        if (reward == null) {
            platform.messages().send(player, "reward.none-configured");
            return;
        }
        if (parameters.giftCooldownHours() > 0) {
            profile.setCooldown("santa-gift",
                    parameters.giftCooldownHours() * 3_600_000L);
        }
        platform.rewards().grant(player, reward);
        platform.messages().send(player, "santa.gift", Messages.of("reward", reward.id()));
    }

    // ------------------------------------------------------------ public API

    @Override
    public boolean present() {
        return manager.isSpawned();
    }

    @Override
    public Location location() {
        return manager.isSpawned() ? manager.santa().getLocation() : null;
    }

    @Override
    public Location summon() {
        if (manager.isSpawned()) {
            return manager.santa().getLocation();
        }
        appear(true);
        return location();
    }

    @Override
    public void dismiss() {
        if (manager.isSpawned()) {
            leave();
        }
    }

    private final class InteractListener implements Listener {

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onInteract(PlayerInteractEntityEvent event) {
            if (!SantaManager.isSanta(event.getRightClicked())) {
                return;
            }
            event.setCancelled(true);
            giveGift(event.getPlayer());
        }
    }

    /** {@code /christmas santa [spawn|despawn]}. */
    private final class SantaCommand implements SubCommand {

        @Override
        public String name() {
            return "santa";
        }

        @Override
        public String permission() {
            return "ultimatechristmas.admin.santa";
        }

        @Override
        public String usage() {
            return "<spawn|despawn|where>";
        }

        @Override
        public String description() {
            return "Spawn or despawn the Santa NPC";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            String action = args.length == 0 ? "where" : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "spawn" -> {
                    if (manager.isSpawned()) {
                        platform.messages().send(sender, "santa.already-here");
                        return;
                    }
                    appear(true);
                    if (!manager.isSpawned()) {
                        throw new CommandError("Santa could not appear - check "
                                + "spawn-locations in modules/santa.yml.");
                    }
                }
                case "despawn" -> {
                    if (!manager.isSpawned()) {
                        platform.messages().send(sender, "santa.not-here");
                        return;
                    }
                    leave();
                    platform.messages().send(sender, "santa.despawned");
                }
                case "where" -> {
                    Location where = location();
                    if (where == null) {
                        platform.messages().send(sender, "santa.not-here");
                    } else {
                        platform.messages().sendRaw(sender, "<green>Santa is at <white>"
                                + where.getBlockX() + ", " + where.getBlockY() + ", "
                                + where.getBlockZ() + "</white> in "
                                + where.getWorld().getName(), java.util.Map.of());
                    }
                }
                default -> throw new CommandError("Usage: /christmas santa " + usage());
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            return args.length == 1 ? List.of("spawn", "despawn", "where") : List.of();
        }
    }
}
