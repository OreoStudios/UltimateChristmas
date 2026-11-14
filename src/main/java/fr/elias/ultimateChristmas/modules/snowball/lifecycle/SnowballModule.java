package fr.elias.ultimateChristmas.modules.snowball.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.reward.RewardTable;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.snowball.api.SnowballService;
import fr.elias.ultimateChristmas.modules.snowball.infra.SnowballKinds;
import fr.elias.ultimateChristmas.modules.snowball.infra.SnowballListener;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Snowball fights: trick snowballs, hit rewards and standing changes. */
@PluginModule(value = "snowball", dependencies = "platform::platform", configuration = true)
public final class SnowballModule extends FeatureModule implements SnowballService {

    private SnowballKinds kinds;
    private RewardTable hitRewards = RewardTable.read(null, null);
    private volatile SnowballParameters parameters;

    public SnowballModule(ChristmasPlatform platform, SnowballParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        kinds = new SnowballKinds(platform.plugin().getLogger());
        reloadData();

        listen(new SnowballListener(platform, kinds, () -> parameters,
                () -> hitRewards, this::emit));
        command(new SnowballCommand());

        publish(SnowballService.class, this);
    }

    @Override
    protected void onReload() {
        parameters = SnowballParameters.from(config(), SnowballParameters.DEFAULTS);
        reloadData();
    }

    private void reloadData() {
        kinds.load(section("kinds"));
        hitRewards = RewardTable.read(
                hasSection("hit-rewards") ? section("hit-rewards") : null,
                platform.plugin().getLogger());
    }

    // ------------------------------------------------------------ public API

    @Override
    public Set<String> kinds() {
        return kinds.ids();
    }

    @Override
    public ItemStack create(String kind, int amount) {
        return kinds.create(kind, amount);
    }

    @Override
    public boolean give(Player player, String kind, int amount) {
        ItemStack item = kinds.create(kind, amount);
        if (item == null) {
            return false;
        }
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(extra ->
                player.getWorld().dropItemNaturally(player.getLocation(), extra));
        return true;
    }

    /** {@code /christmas snowball give <kind> [player] [amount]}. */
    private final class SnowballCommand implements SubCommand {

        @Override
        public String name() {
            return "snowball";
        }

        @Override
        public String permission() {
            return "ultimatechristmas.snowball";
        }

        @Override
        public String usage() {
            return "give <kind> [player] [amount]";
        }

        @Override
        public String description() {
            return "List or hand out trick snowballs";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                platform.messages().send(sender, "snowball.list",
                        Messages.of("kinds", String.join(", ", kinds.ids())));
                return;
            }
            if (!args[0].equalsIgnoreCase("give")) {
                throw new CommandError("Usage: /christmas snowball " + usage());
            }
            if (!sender.hasPermission("ultimatechristmas.admin.snowball")) {
                throw new CommandError("You may not hand out snowballs.");
            }
            if (args.length < 2) {
                throw new CommandError("Which kind? One of: "
                        + String.join(", ", kinds.ids()));
            }

            Player target;
            if (args.length >= 3) {
                target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    throw new CommandError("'" + args[2] + "' is not online.");
                }
            } else if (sender instanceof Player self) {
                target = self;
            } else {
                throw new CommandError("From the console, name a player.");
            }

            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                } catch (NumberFormatException ex) {
                    throw new CommandError("'" + args[3] + "' is not a number.");
                }
            }

            if (!give(target, args[1], amount)) {
                throw new CommandError("No snowball kind called '" + args[1]
                        + "'. Known: " + String.join(", ", kinds.ids()));
            }
            platform.messages().send(sender, "snowball.given", Messages.of(
                    "amount", String.valueOf(amount),
                    "kind", args[1],
                    "player", target.getName()));
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return List.of("list", "give");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                return new ArrayList<>(kinds.ids());
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
            return List.of();
        }
    }
}
