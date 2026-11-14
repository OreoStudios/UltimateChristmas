package fr.elias.ultimateChristmas.modules.economy.infra;

import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** {@code /christmas shards [player]} — shows a shard balance. */
public final class ShardsCommand implements SubCommand {

    private final ChristmasPlatform platform;

    public ShardsCommand(ChristmasPlatform platform) {
        this.platform = platform;
    }

    @Override
    public String name() {
        return "shards";
    }

    @Override
    public List<String> aliases() {
        return List.of("balance", "bal");
    }

    @Override
    public String permission() {
        return "ultimatechristmas.shards";
    }

    @Override
    public String usage() {
        return "[player]";
    }

    @Override
    public String description() {
        return "Check your shard balance";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                platform.messages().send(sender, "command.player-only");
                return;
            }
            long balance = platform.economy().balance(player);
            platform.messages().send(sender, "economy.balance", Messages.of(
                    "amount", EconomyService.format(balance),
                    "currency", platform.economy().currencyName(balance)));
            return;
        }

        if (!sender.hasPermission("ultimatechristmas.shards.others")) {
            platform.messages().send(sender, "command.no-permission",
                    Messages.of("permission", "ultimatechristmas.shards.others"));
            return;
        }

        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            report(sender, online.getName(), platform.economy().balance(online));
            return;
        }

        // An offline lookup reads storage, so it happens off the main thread.
        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
        if (!offline.hasPlayedBefore()) {
            platform.messages().send(sender, "command.unknown-player",
                    Messages.of("player", args[0]));
            return;
        }
        platform.profiles().loadAsync(offline.getUniqueId(), offline.getName())
                .thenAccept(profile -> Bukkit.getScheduler().runTask(platform.plugin(),
                        () -> report(sender, profile.name(), profile.shards())));
    }

    private void report(CommandSender sender, String who, long balance) {
        platform.messages().send(sender, "economy.balance-other", Messages.of(
                "player", who,
                "amount", EconomyService.format(balance),
                "currency", platform.economy().currencyName(balance)));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("ultimatechristmas.shards.others")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        return List.of();
    }
}
