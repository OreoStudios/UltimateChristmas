package fr.elias.ultimateChristmas.modules.santa.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.reward.Reward;
import fr.elias.ultimateChristmas.core.reward.RewardTable;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.santa.api.SantaService;
import fr.elias.ultimateChristmas.modules.santa.infra.SantaManager;
import fr.elias.ultimateChristmas.core.effect.Fx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
import java.util.Locale;

/**
 * A wandering Santa NPC who leaves presents on the ground.
 *
 * <p>Santa appears on a timer during the season, strolls around dropping
 * presents at random intervals, then leaves. Players pick the presents up and
 * right-click them to open them. His appearances are driven by a single
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
        manager.prepare();
        gifts = RewardTable.read(section("gifts.rewards"), platform.plugin().getLogger());

        listen(new InteractListener());
        listen(new GiftListener());
        command(new SantaCommand());

        nextAutoSpawn = System.currentTimeMillis()
                + parameters.cooldownSeconds() * 1000L;
        timer(this::tick, 40L, 40L);
        timer(manager::tick, 1L, 1L);

        publish(SantaService.class, this);
    }

    @Override
    protected void onDisable() {
        if (manager != null) {
            manager.despawn();
            manager.clearGifts();
        }
    }

    @Override
    protected void onReload() {
        parameters = SantaParameters.from(config(), SantaParameters.DEFAULTS);
        gifts = RewardTable.read(section("gifts.rewards"), platform.plugin().getLogger());
        manager.prepare();
    }

    /** The appearance loop: spawn when due, leave when his time is up. */
    private void tick() {
        long now = System.currentTimeMillis();

        if (manager.isSpawned()) {
            manager.idleEffects();
            if (leaveAt > 0 && now >= leaveAt && !manager.isLeaving()) {
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
        appear(announce, null);
    }

    private void appear(boolean announce, Location fallback) {
        Location where = manager.spawn(fallback);
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

    /** Santa laughs goodbye, then leaves once the clip has played. */
    private void leave() {
        if (!manager.isSpawned()) {
            return;
        }
        leaveAt = 0;
        nextAutoSpawn = System.currentTimeMillis() + parameters.cooldownSeconds() * 1000L;
        manager.leave(() -> Bukkit.broadcast(
                platform.messages().prefixed("santa.left", java.util.Map.of())));
    }

    /** Opens one present from the player's hand, if they are somewhere allowed. */
    private void openPresent(Player player, org.bukkit.inventory.EquipmentSlot hand) {
        if (!parameters.openRegions().isEmpty() && platform.hooks().worldGuard().active()
                && !platform.hooks().worldGuard().inAnyRegion(player.getLocation(),
                        parameters.openRegions())) {
            platform.messages().send(player, "santa.present-wrong-place");
            return;
        }
        Reward reward = gifts.roll();
        if (reward == null) {
            platform.messages().send(player, "reward.none-configured");
            return;
        }
        org.bukkit.inventory.ItemStack held = player.getInventory().getItem(hand);
        if (!SantaManager.isGift(held)) {
            return;
        }
        held.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(hand, held.getAmount() <= 0 ? null : held);

        Location at = player.getLocation();
        Fx.burst(at.clone().add(0, 1.2, 0), Particle.TOTEM_OF_UNDYING, 20, 0.4);
        Fx.jingle(at);
        platform.rewards().grant(player, reward);
        platform.progress().record(player, PlayerProfile.STAT_GIFTS_OPENED, 1);
        platform.messages().send(player, "santa.gift-found", Messages.of("reward", reward.id()));
        manager.celebrateNear(at);
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
            // He hands nothing out on click: presents only come from his drops.
            event.setCancelled(true);
            manager.clickedBy(event.getPlayer());
            platform.messages().send(event.getPlayer(), "santa.clicked");
        }
    }

    /** Santa's presents: picked up like any item, opened by right-clicking them. */
    private final class GiftListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPickup(EntityPickupItemEvent event) {
            Item item = event.getItem();
            if (!SantaManager.isGift(item.getItemStack())) {
                return;
            }
            manager.forgetGift(item.getUniqueId());
            if (event.getEntity() instanceof Player player) {
                Fx.sound(item.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.4f);
                platform.messages().send(player, "santa.present-picked-up");
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onUse(PlayerInteractEvent event) {
            if (event.getAction() != Action.RIGHT_CLICK_AIR
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            if (event.getHand() == null || !SantaManager.isGift(event.getItem())) {
                return;
            }
            // Also stops a head present being placed as a block.
            event.setCancelled(true);
            openPresent(event.getPlayer(), event.getHand());
        }

        @EventHandler(ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            if (SantaManager.isGift(event.getItemInHand())) {
                event.setCancelled(true);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onHopper(InventoryPickupItemEvent event) {
            if (SantaManager.isGift(event.getItem().getItemStack())) {
                event.setCancelled(true);
            }
        }
    }

    /** {@code /christmas santa [spawn|despawn|where|drop]}. */
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
            return "<spawn|despawn|where|drop|present [amount]>";
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
                    appear(true, sender instanceof Player player ? player.getLocation() : null);
                    if (!manager.isSpawned()) {
                        throw new CommandError("Santa could not appear - check "
                                + "spawn-locations in modules/santa.yml.");
                    }
                }
                case "present" -> {
                    if (!(sender instanceof Player player)) {
                        throw new CommandError("Only a player can receive a present.");
                    }
                    int amount = 1;
                    if (args.length > 1) {
                        try {
                            amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
                        } catch (NumberFormatException ex) {
                            throw new CommandError("Amount must be a number.");
                        }
                    }
                    player.getInventory().addItem(manager.presentItem(amount)).values().forEach(
                            leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                    platform.messages().send(player, "santa.present-received");
                }
                case "drop" -> {
                    if (!manager.isSpawned()) {
                        platform.messages().send(sender, "santa.not-here");
                        return;
                    }
                    if (!manager.dropNow()) {
                        throw new CommandError("Santa is busy - try again in a moment.");
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
            return args.length == 1 ? List.of("spawn", "despawn", "where", "drop", "present") : List.of();
        }
    }
}
