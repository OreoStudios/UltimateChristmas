package fr.elias.ultimateChristmas.modules.admin.infra;

import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.season.Season;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /christmas season} — where the season clock currently stands.
 *
 * <p>Mostly a diagnostic: nearly every "why is this not working?" question
 * about a seasonal feature comes down to the season window or the timezone, and
 * this answers it in one line each.
 */
public final class SeasonCommand implements SubCommand {

    private final ChristmasPlatform platform;

    public SeasonCommand(ChristmasPlatform platform) {
        this.platform = platform;
    }

    @Override
    public String name() {
        return "season";
    }

    @Override
    public List<String> aliases() {
        return List.of("info");
    }

    @Override
    public String permission() {
        return "ultimatechristmas.season";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public String description() {
        return "Show the season window and countdown";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Season season = platform.season();
        int day = season.currentDay();

        sender.sendMessage(Component.text()
                .append(platform.messages().prefix())
                .append(Component.text("The season", NamedTextColor.GOLD))
                .build());
        row(sender, "Window", season.start() + " to " + season.end()
                + " (" + season.length() + " days)");
        row(sender, "Timezone", season.zone().getId()
                + ", day rolls over at " + season.nextReset().getHour() + ":00");
        row(sender, "Today", day > 0
                ? "day " + day + " of " + season.length()
                : "outside the season");
        row(sender, "Next reset", Season.format(season.untilReset()));

        long untilChristmas = season.daysUntilChristmas();
        if (season.isChristmas()) {
            row(sender, "Christmas", "today! Merry Christmas.");
        } else if (untilChristmas > 0) {
            row(sender, "Christmas", season.christmasDay() + ", in "
                    + Season.format(season.untilChristmas()));
        } else {
            row(sender, "Christmas", season.christmasDay() + ", "
                    + Math.abs(untilChristmas) + " day(s) ago");
        }
    }

    private void row(CommandSender sender, String label, String value) {
        sender.sendMessage(Component.text()
                .append(Component.text("  " + label + ": ", NamedTextColor.GRAY))
                .append(Component.text(value, NamedTextColor.WHITE))
                .build());
    }
}
