package fr.elias.ultimateChristmas.modules.ambience.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.season.Season;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.core.text.Text;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ambient festive atmosphere: join greetings and the odd distant sleigh bell.
 *
 * <p>Deliberately light. Visible snow is the snow module's job; this is the
 * incidental polish — a greeting that mentions the countdown, and a rare
 * sound so the world feels seasonal without being noisy.
 */
@PluginModule(value = "ambience", dependencies = "platform::platform", configuration = true)
public final class AmbienceModule extends FeatureModule {

    private volatile AmbienceParameters parameters;

    public AmbienceModule(ChristmasPlatform platform, AmbienceParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        listen(new JoinListener());
        if (parameters.ambientSounds()) {
            timer(this::ambientSounds, parameters.soundPeriodTicks(),
                    parameters.soundPeriodTicks());
        }
    }

    @Override
    protected void onReload() {
        parameters = AmbienceParameters.from(config(), AmbienceParameters.DEFAULTS);
    }

    private boolean active() {
        return !parameters.onlyDuringSeason() || platform.season().active();
    }

    /** Occasionally plays a quiet sleigh bell for a player. */
    private void ambientSounds() {
        if (!active()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (random.nextDouble() < parameters.soundChance()) {
                player.playSound(player.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_BELL, 0.3f, 1.8f);
            }
        }
    }

    private final class JoinListener implements Listener {

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            if (!parameters.greetPlayers() || !active()) {
                return;
            }
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(platform.plugin(), () -> {
                if (player.isOnline()) {
                    greet(player);
                }
            }, parameters.greetDelayTicks());
        }
    }

    private void greet(Player player) {
        // Prefer lang.yml greetings; fall back to the bundled defaults so the
        // feature works before anyone edits the file.
        List<String> greetings = platform.configs().lang()
                .getStringList("ambience.greetings");
        if (greetings.isEmpty()) {
            greetings = AmbienceParameters.defaultGreetings();
        }
        String line = greetings.get(ThreadLocalRandom.current().nextInt(greetings.size()));
        Season season = platform.season();
        player.sendMessage(Text.parse(line, Messages.of(
                "player", player.getName(),
                "days", String.valueOf(Math.max(0, season.daysUntilChristmas())))));
    }
}
