package fr.elias.ultimateChristmas.modules.economy.infra;

import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.economy.lifecycle.EconomyParameters;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@code /christmas pay <player> <amount>} — send shards to someone.
 *
 * <p>In the spirit of the season, and it gives the naughty/nice standing
 * something generous to reward.
 */
public final class PayCommand implements SubCommand {

    private static final long NICE_COOLDOWN_MILLIS = 3_600_000L;

    private final ChristmasPlatform platform;
    private final Supplier<EconomyParameters> parameters;

    public PayCommand(ChristmasPlatform platform, Supplier<EconomyParameters> parameters) {
        this.platform = platform;
        this.parameters = parameters;
    }

    @Override
    public String name() {
        return "pay";
    }

    @Override
    public String permission() {
        return "ultimatechristmas.pay";
    }

    @Override
    public String usage() {
        return "<player> <amount>";
    }

    @Override
    public String description() {
        return "Send some of your shards to another player";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        EconomyParameters settings = parameters.get();
        if (!settings.payEnabled()) {
            throw new CommandError("Sending shards is disabled on this server.");
        }

        Player from = (Player) sender;
        if (args.length < 2) {
            throw new CommandError("Usage: /christmas pay " + usage());
        }

        Player to = Bukkit.getPlayerExact(args[0]);
        if (to == null) {
            throw new CommandError("'" + args[0] + "' is not online.");
        }
        if (to.equals(from)) {
            throw new CommandError("You cannot pay yourself.");
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException ex) {
            throw new CommandError("'" + args[1] + "' is not a whole number.");
        }
        if (amount < settings.payMinimum()) {
            throw new CommandError("The smallest amount you can send is "
                    + EconomyService.format(settings.payMinimum()) + ".");
        }

        PlayerProfile source = platform.profiles().get(from);
        if (!platform.economy().take(source, amount)) {
            platform.messages().send(from, "economy.not-enough", Messages.of(
                    "needed", EconomyService.format(amount),
                    "balance", EconomyService.format(source.shards())));
            return;
        }

        PlayerProfile target = platform.profiles().get(to);
        platform.economy().give(target, amount);

        // Generosity counts towards being nice, but rate-limited so it cannot
        // be farmed by two accounts passing shards back and forth.
        if (settings.payNicePoints() > 0 && !source.onCooldown("pay-nice")) {
            source.addNaughtyNice(settings.payNicePoints());
            source.setCooldown("pay-nice", NICE_COOLDOWN_MILLIS);
        }
        platform.progress().record(from, PlayerProfile.STAT_GIFTS_GIVEN, 1);

        platform.messages().send(from, "economy.pay-sent", Messages.of(
                "amount", EconomyService.format(amount),
                "player", to.getName(),
                "balance", EconomyService.format(source.shards())));
        platform.messages().send(to, "economy.pay-received", Messages.of(
                "amount", EconomyService.format(amount),
                "player", from.getName(),
                "balance", EconomyService.format(target.shards())));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.equals(sender)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
