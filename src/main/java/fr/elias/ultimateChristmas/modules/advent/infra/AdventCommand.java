package fr.elias.ultimateChristmas.modules.advent.infra;

import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.season.Season;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /christmas advent [claim|streak|reset <player>]}.
 *
 * <p>With no arguments it opens the calendar, which is what almost everyone
 * wants; the subcommands cover claiming without the menu and admin resets.
 */
public final class AdventCommand implements SubCommand {

    private final AdventCalendar calendar;

    public AdventCommand(AdventCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public String name() {
        return "advent";
    }

    @Override
    public List<String> aliases() {
        return List.of("calendar");
    }

    @Override
    public String permission() {
        return "ultimatechristmas.advent";
    }

    @Override
    public String usage() {
        return "[claim|streak|reset <player>]";
    }

    @Override
    public String description() {
        return "Open the advent calendar";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "open" : args[0].toLowerCase(Locale.ROOT);

        switch (action) {
            case "open" -> {
                Player player = requirePlayer(sender);
                calendar.platform().menus().open(player, new AdventMenu(calendar));
            }
            case "claim" -> {
                Player player = requirePlayer(sender);
                int today = calendar.platform().season().currentDay();
                if (today < 0) {
                    calendar.platform().messages().send(player, "advent.outside-season");
                    return;
                }
                AdventCalendar.Outcome outcome = calendar.claim(player, today);
                if (outcome != AdventCalendar.Outcome.CLAIMED) {
                    calendar.platform().messages().send(player,
                            switch (outcome) {
                                case ALREADY_OPEN -> "advent.already-open";
                                case NO_REWARD -> "advent.no-reward";
                                default -> "advent.not-yet";
                            },
                            Messages.of("day", String.valueOf(today)));
                }
            }
            case "streak" -> showStreak(sender);
            case "reset" -> reset(sender, args);
            default -> throw new CommandError("Usage: /christmas advent " + usage());
        }
    }

    private void showStreak(CommandSender sender) {
        Player player = requirePlayer(sender);
        PlayerProfile profile = calendar.platform().profiles().get(player);
        Season season = calendar.platform().season();
        calendar.platform().messages().send(sender, "advent.streak", Messages.of(
                "streak", String.valueOf(profile.adventStreak()),
                "best", String.valueOf(profile.adventBestStreak()),
                "opened", String.valueOf(profile.adventClaimed().size()),
                "doors", String.valueOf(calendar.doorCount()),
                "remaining", Season.format(season.untilReset())));
    }

    private void reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ultimatechristmas.admin.advent")) {
            throw new CommandError("You may not reset calendars.");
        }
        if (args.length < 2) {
            throw new CommandError("Usage: /christmas advent reset <player>");
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            throw new CommandError("'" + args[1] + "' is not online.");
        }
        calendar.platform().profiles().get(target).clearAdvent();
        calendar.platform().messages().send(sender, "advent.reset",
                Messages.of("player", target.getName()));
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        throw new CommandError("Only a player can do that.");
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return sender.hasPermission("ultimatechristmas.admin.advent")
                    ? List.of("open", "claim", "streak", "reset")
                    : List.of("open", "claim", "streak");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
