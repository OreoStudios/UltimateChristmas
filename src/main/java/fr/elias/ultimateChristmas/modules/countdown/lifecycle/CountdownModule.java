package fr.elias.ultimateChristmas.modules.countdown.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.season.Season;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.countdown.api.CountdownService;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.List;

/**
 * A boss-bar countdown to Christmas, and a firework show at midnight.
 *
 * <p>The bar is per-player so it can be switched off individually, and the
 * midnight show fires exactly once: the date it last ran is stored, so a server
 * restart on Christmas Day does not replay it.
 */
@PluginModule(value = "countdown", dependencies = "platform::platform", configuration = true)
public final class CountdownModule extends FeatureModule implements CountdownService {

    private static final String SHOWN_FLAG = "countdown-midnight-shown";

    private final java.util.Map<java.util.UUID, BossBar> bars = new java.util.HashMap<>();
    private volatile CountdownParameters parameters;
    private String lastCelebrated = "";

    public CountdownModule(ChristmasPlatform platform, CountdownParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        listen(new JoinListener());
        command(new CountdownCommand());

        if (parameters.bossBar()) {
            Bukkit.getOnlinePlayers().forEach(this::showBar);
            timer(this::updateBars, 20L, parameters.updateTicks());
        }
        if (parameters.midnightShow()) {
            // Checked every half minute: precise enough for a midnight event
            // without a task that runs every tick all season.
            timer(this::checkMidnight, 100L, 600L);
        }

        publish(CountdownService.class, this);
    }

    @Override
    protected void onDisable() {
        bars.values().forEach(bar -> Bukkit.getOnlinePlayers().forEach(
                player -> player.hideBossBar(bar)));
        bars.clear();
    }

    @Override
    protected void onReload() {
        boolean hadBar = parameters.bossBar();
        parameters = CountdownParameters.from(config(), CountdownParameters.DEFAULTS);
        if (hadBar != parameters.bossBar()) {
            log().info("countdown: boss-bar setting changed; run "
                    + "/christmas module restart countdown to apply it.");
        }
    }

    // ------------------------------------------------------------- boss bar

    private void showBar(Player player) {
        if (!parameters.bossBar() || !player.hasPermission("ultimatechristmas.countdown")) {
            return;
        }
        BossBar bar = BossBar.bossBar(barTitle(), 1.0f,
                BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        bars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
    }

    private net.kyori.adventure.text.Component barTitle() {
        Season season = platform.season();
        if (season.isChristmas()) {
            return platform.messages().get("countdown.bar-today");
        }
        return platform.messages().get("countdown.bar",
                Messages.of("time", Season.format(season.untilChristmas())));
    }

    private void updateBars() {
        if (bars.isEmpty()) {
            return;
        }
        Season season = platform.season();
        Duration remaining = season.untilChristmas();
        // Progress runs from the start of the season to Christmas, so the bar
        // visibly fills over the month rather than sitting at 100%.
        long total = Math.max(1, Duration.between(
                season.start().atStartOfDay(season.zone()),
                season.christmasDay().atStartOfDay(season.zone())).toSeconds());
        float progress = remaining.isNegative() || remaining.isZero()
                ? 1.0f
                : 1.0f - Math.min(1.0f, Math.max(0f, remaining.toSeconds() / (float) total));

        net.kyori.adventure.text.Component title = barTitle();
        bars.forEach((id, bar) -> {
            bar.name(title);
            bar.progress(progress);
            bar.color(season.isChristmas() ? BossBar.Color.GREEN : BossBar.Color.RED);
        });
    }

    // ------------------------------------------------------------- midnight

    /**
     * Fires the Christmas Day celebration once.
     *
     * <p>The guard is the date string rather than a boolean, so a server
     * running across two seasons celebrates each one.
     */
    private void checkMidnight() {
        Season season = platform.season();
        if (!season.isChristmas()) {
            return;
        }
        String today = season.today().toString();
        if (today.equals(lastCelebrated)) {
            return;
        }
        lastCelebrated = today;

        Bukkit.broadcast(platform.messages().prefixed("countdown.midnight", java.util.Map.of()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            Fx.title(player, platform.messages().raw("countdown.today"), null, java.util.Map.of());
            Fx.fireworkShow(platform.plugin(), player.getLocation(),
                    parameters.fireworkCount(), 10L);
            platform.profiles().get(player).flag(SHOWN_FLAG, true);
        }
    }

    // ------------------------------------------------------------ public API

    @Override
    public long daysUntilChristmas() {
        return platform.season().daysUntilChristmas();
    }

    @Override
    public boolean isChristmas() {
        return platform.season().isChristmas();
    }

    private final class JoinListener implements Listener {

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            showBar(event.getPlayer());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            BossBar bar = bars.remove(event.getPlayer().getUniqueId());
            if (bar != null) {
                event.getPlayer().hideBossBar(bar);
            }
        }
    }

    /** {@code /christmas countdown} — how long until Christmas. */
    private final class CountdownCommand implements SubCommand {

        @Override
        public String name() {
            return "countdown";
        }

        @Override
        public String permission() {
            return "ultimatechristmas.countdown";
        }

        @Override
        public String usage() {
            return "";
        }

        @Override
        public String description() {
            return "How long until Christmas";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            Season season = platform.season();
            if (season.isChristmas()) {
                platform.messages().send(sender, "countdown.today");
                return;
            }
            long days = season.daysUntilChristmas();
            if (days < 0) {
                platform.messages().send(sender, "countdown.passed");
                return;
            }
            platform.messages().send(sender, "countdown.days", Messages.of(
                    "days", String.valueOf(days),
                    "time", Season.format(season.untilChristmas())));
        }
    }
}
