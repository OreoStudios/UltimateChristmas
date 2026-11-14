package fr.elias.ultimateChristmas.modules.tree.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.season.Season;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.tree.api.TreeDecoratedEvent;
import fr.elias.ultimateChristmas.modules.tree.api.TreeService;
import fr.elias.ultimateChristmas.modules.tree.infra.TreeState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * A single community tree the whole server decorates together.
 *
 * <p>Players hang ornaments toward a shared goal; when it is met, everyone
 * online is rewarded. The tree's location and progress are server state, so
 * they live in the module config and survive a restart.
 */
@PluginModule(value = "tree", dependencies = "platform::platform", configuration = true)
public final class TreeModule extends FeatureModule implements TreeService {

    private TreeState state;
    private volatile TreeParameters parameters;

    public TreeModule(ChristmasPlatform platform, TreeParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        state = new TreeState(config());
        command(new TreeCommand());

        if (parameters.ambientParticles()) {
            timer(this::ambient, 40L, 40L);
        }
        publish(TreeService.class, this);
    }

    @Override
    protected void onReload() {
        parameters = TreeParameters.from(config(), TreeParameters.DEFAULTS);
        state.read();
    }

    /** Floating particles around the tree, so players can find it. */
    private void ambient() {
        Location tree = state.location();
        if (tree == null || tree.getWorld() == null
                || tree.getWorld().getPlayers().isEmpty()) {
            return;
        }
        Fx.ring(tree.clone().add(0, 2, 0), Particle.END_ROD, 2.0, 12);
        Fx.snowfall(tree.clone().add(0, 4, 0), 3.0, 4);
    }

    // ------------------------------------------------------------ public API

    @Override
    public Location location() {
        return state.location();
    }

    @Override
    public int ornamentCount() {
        return state.ornaments();
    }

    @Override
    public int goal() {
        return parameters.goal();
    }

    @Override
    public boolean complete() {
        return state.ornaments() >= parameters.goal();
    }

    /** A player hangs an ornament, if they are near enough and off cooldown. */
    private void hang(Player player) {
        Location tree = state.location();
        if (tree == null) {
            platform.messages().send(player, "tree.not-set");
            return;
        }
        if (player.getWorld() != tree.getWorld()
                || player.getLocation().distanceSquared(tree)
                   > parameters.radius() * parameters.radius()) {
            platform.messages().send(player, "tree.too-far");
            return;
        }
        if (complete()) {
            platform.messages().send(player, "tree.status", Messages.of(
                    "total", String.valueOf(state.ornaments()),
                    "goal", String.valueOf(parameters.goal())));
            return;
        }

        PlayerProfile profile = platform.profiles().get(player);
        if (parameters.cooldownSeconds() > 0 && profile.onCooldown("tree-hang")) {
            platform.messages().send(player, "tree.cooldown", Messages.of(
                    "remaining", Season.format(Duration.ofMillis(
                            profile.cooldownRemaining("tree-hang")))));
            return;
        }

        Material required = parameters.requiredMaterial();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (required != null) {
            if (inHand.getType() != required) {
                platform.messages().sendRaw(player,
                        "<red>Hold a " + required.name().toLowerCase(Locale.ROOT)
                                + " to hang it on the tree.", java.util.Map.of());
                return;
            }
            inHand.setAmount(inHand.getAmount() - 1);
        }

        String ornamentName = required == null ? "an ornament"
                : required.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        int total = state.addOrnament();
        if (parameters.cooldownSeconds() > 0) {
            profile.setCooldown("tree-hang", parameters.cooldownSeconds() * 1000L);
        }
        if (parameters.ornamentShards() > 0) {
            platform.economy().give(player, parameters.ornamentShards());
        }
        if (parameters.nicePoints() > 0) {
            profile.addNaughtyNice(parameters.nicePoints());
        }
        platform.progress().record(player, PlayerProfile.STAT_ORNAMENTS_HUNG, 1);

        Fx.helix(tree, Particle.HAPPY_VILLAGER, 3.0, 1.5);
        Fx.sound(tree, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
        platform.messages().send(player, "tree.hung", Messages.of(
                "ornament", ornamentName,
                "total", String.valueOf(total),
                "goal", String.valueOf(parameters.goal())));

        if (total >= parameters.goal() && !state.completed()) {
            onTreeComplete();
        }
    }

    /** Rewards everyone online once the goal is met. */
    private void onTreeComplete() {
        state.markCompleted();
        emit(new TreeDecoratedEvent(state.ornaments()));

        Bukkit.broadcast(platform.messages().prefixed("tree.complete",
                Messages.of("goal", String.valueOf(parameters.goal()))));

        Location tree = state.location();
        if (tree != null) {
            Fx.fireworkShow(platform.plugin(), tree, 15, 8L);
        }
        // The shared reward is configured as an ordinary reward table entry.
        var completionReward = fr.elias.ultimateChristmas.core.reward.RewardTable
                .read(hasSection("completion-reward")
                        ? section("completion-reward") : null,
                        platform.plugin().getLogger());
        if (!completionReward.isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                var reward = completionReward.roll();
                if (reward != null) {
                    platform.rewards().grant(player, reward);
                }
            }
        }
    }

    /** {@code /christmas tree [hang|status|sethere|reset]}. */
    private final class TreeCommand implements SubCommand {

        @Override
        public String name() {
            return "tree";
        }

        @Override
        public String permission() {
            return "ultimatechristmas.tree";
        }

        @Override
        public String usage() {
            return "[hang|status|sethere|reset]";
        }

        @Override
        public String description() {
            return "Decorate the community Christmas tree";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "status" -> platform.messages().send(sender, "tree.status", Messages.of(
                        "total", String.valueOf(state.ornaments()),
                        "goal", String.valueOf(parameters.goal())));
                case "hang" -> {
                    if (!(sender instanceof Player player)) {
                        throw new CommandError("Only a player can decorate the tree.");
                    }
                    hang(player);
                }
                case "sethere" -> {
                    requireAdmin(sender);
                    if (!(sender instanceof Player player)) {
                        throw new CommandError("Stand where the tree should be, then run this.");
                    }
                    state.setLocation(player.getLocation());
                    platform.messages().send(sender, "tree.set");
                }
                case "reset" -> {
                    requireAdmin(sender);
                    state.reset();
                    platform.messages().send(sender, "tree.reset");
                }
                default -> throw new CommandError("Usage: /christmas tree " + usage());
            }
        }

        private void requireAdmin(CommandSender sender) {
            if (!sender.hasPermission("ultimatechristmas.admin.tree")) {
                throw new CommandError("You may not manage the tree.");
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return sender.hasPermission("ultimatechristmas.admin.tree")
                        ? List.of("hang", "status", "sethere", "reset")
                        : List.of("hang", "status");
            }
            return List.of();
        }
    }
}
