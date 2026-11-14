package fr.elias.ultimateChristmas.modules.economy.infra;

import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * {@code /christmas eco <give|take|set> <player> <amount>} — shard administration.
 *
 * <p>Works on offline players: the profile is loaded, edited and saved
 * asynchronously, so rewarding someone who has logged off behaves exactly like
 * rewarding someone online.
 */
public final class ShardAdminCommand implements SubCommand {

    private final ChristmasPlatform platform;

    public ShardAdminCommand(ChristmasPlatform platform) {
        this.platform = platform;
    }

    @Override
    public String name() {
        return "eco";
    }

    @Override
    public List<String> aliases() {
        return List.of("economy");
    }

    @Override
    public String permission() {
        return "ultimatechristmas.admin.economy";
    }

    @Override
    public String usage() {
        return "<give|take|set> <player> <amount>";
    }

    @Override
    public String description() {
        return "Give, take or set someone's shards";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            throw new CommandError("Usage: /christmas eco " + usage());
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        long amount = parseAmount(args[2]);

        Consumer<PlayerProfile> change = switch (action) {
            case "give", "add" -> profile -> platform.economy().give(profile, amount);
            case "take", "remove" -> profile -> {
                // Taking more than they hold empties the balance rather than
                // failing, which is what an admin almost always means.
                if (!platform.economy().take(profile, amount)) {
                    platform.economy().set(profile, 0);
                }
            };
            case "set" -> profile -> platform.economy().set(profile, amount);
            default -> throw new CommandError("Unknown action '" + action
                    + "'. Use give, take or set.");
        };

        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            PlayerProfile profile = platform.profiles().get(online);
            change.accept(profile);
            report(sender, profile.name(), action, amount, profile.shards());
            platform.messages().send(online, "economy.admin-changed", Messages.of(
                    "action", action,
                    "amount", EconomyService.format(amount),
                    "balance", EconomyService.format(profile.shards())));
            return;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
        if (!offline.hasPlayedBefore()) {
            throw new CommandError("No player called '" + args[1] + "' has joined before.");
        }
        platform.profiles().loadAsync(offline.getUniqueId(), offline.getName())
                .thenAccept(profile -> {
                    change.accept(profile);
                    platform.profiles().flushDirty();
                    Bukkit.getScheduler().runTask(platform.plugin(), () ->
                            report(sender, profile.name(), action, amount, profile.shards()));
                });
    }

    private long parseAmount(String raw) {
        long amount;
        try {
            amount = Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new CommandError("'" + raw + "' is not a whole number.");
        }
        if (amount < 0) {
            throw new CommandError("Use a positive amount; the action decides the direction.");
        }
        return amount;
    }

    private void report(CommandSender sender, String target, String action,
                        long amount, long balance) {
        platform.messages().send(sender, "economy.admin-done", Messages.of(
                "action", action,
                "amount", EconomyService.format(amount),
                "player", target,
                "balance", EconomyService.format(balance)));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("give", "take", "set");
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        if (args.length == 3) {
            return List.of("10", "100", "1000");
        }
        return List.of();
    }
}
