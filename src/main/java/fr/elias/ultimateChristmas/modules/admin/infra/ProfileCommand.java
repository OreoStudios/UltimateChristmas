package fr.elias.ultimateChristmas.modules.admin.infra;

import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /christmas profile [player]} — everything the plugin remembers about
 * someone.
 *
 * <p>The answer to "did my reward actually save?", which in 3.x meant opening
 * {@code playerdata.yml} by hand.
 */
public final class ProfileCommand implements SubCommand {

    private final ChristmasPlatform platform;

    public ProfileCommand(ChristmasPlatform platform) {
        this.platform = platform;
    }

    @Override
    public String name() {
        return "profile";
    }

    @Override
    public List<String> aliases() {
        return List.of("stats");
    }

    @Override
    public String permission() {
        return "ultimatechristmas.profile";
    }

    @Override
    public String usage() {
        return "[player]";
    }

    @Override
    public String description() {
        return "Show a player's festive stats and progress";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player self)) {
                throw new CommandError("From the console, name a player: "
                        + "/christmas profile <player>");
            }
            render(sender, platform.profiles().get(self));
            return;
        }

        if (!sender.hasPermission("ultimatechristmas.profile.others")) {
            throw new CommandError("You may only look at your own profile.");
        }

        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            render(sender, platform.profiles().get(online));
            return;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
        if (!offline.hasPlayedBefore()) {
            throw new CommandError("No player called '" + args[0] + "' has joined before.");
        }
        platform.profiles().loadAsync(offline.getUniqueId(), offline.getName())
                .thenAccept(profile -> Bukkit.getScheduler().runTask(platform.plugin(),
                        () -> render(sender, profile)));
    }

    private void render(CommandSender sender, PlayerProfile profile) {
        sender.sendMessage(Component.text()
                .append(platform.messages().prefix())
                .append(Component.text(profile.name(), NamedTextColor.GOLD))
                .build());

        row(sender, "Shards", EconomyService.format(profile.shards()));
        row(sender, "Naughty / nice", profile.naughtyNice() + " ("
                + standing(profile.naughtyNice()) + ")");
        row(sender, "Advent", profile.adventClaimed().size() + " door(s), streak "
                + profile.adventStreak() + ", best " + profile.adventBestStreak());
        row(sender, "Festive pass", profile.passXp() + " xp"
                + (profile.passPremium() ? ", premium" : "")
                + ", " + profile.passClaimed().size() + " tier(s) claimed");
        row(sender, "Cosmetics", profile.cosmeticsOwned().size() + " owned, equipped "
                + (profile.cosmeticsEquipped().isEmpty() ? "none"
                : profile.cosmeticsEquipped().toString()));

        Map<String, Long> stats = new TreeMap<>(profile.stats());
        if (!stats.isEmpty()) {
            sender.sendMessage(Component.text("  Stats:", NamedTextColor.GRAY));
            stats.forEach((key, value) -> sender.sendMessage(Component.text()
                    .append(Component.text("    " + key + ": ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(String.valueOf(value), NamedTextColor.WHITE))
                    .build()));
        }
    }

    private static String standing(int score) {
        if (score >= 60) {
            return "on the nice list";
        }
        if (score >= 20) {
            return "mostly nice";
        }
        if (score > -20) {
            return "undecided";
        }
        if (score > -60) {
            return "a bit naughty";
        }
        return "firmly on the naughty list";
    }

    private void row(CommandSender sender, String label, String value) {
        sender.sendMessage(Component.text()
                .append(Component.text("  " + label + ": ", NamedTextColor.GRAY))
                .append(Component.text(value, NamedTextColor.WHITE))
                .build());
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("ultimatechristmas.profile.others")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        return List.of();
    }
}
