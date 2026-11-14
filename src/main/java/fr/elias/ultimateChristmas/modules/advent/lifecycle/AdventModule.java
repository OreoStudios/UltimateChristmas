package fr.elias.ultimateChristmas.modules.advent.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.modules.advent.api.AdventService;
import fr.elias.ultimateChristmas.modules.advent.infra.AdventCalendar;
import fr.elias.ultimateChristmas.modules.advent.infra.AdventCommand;
import fr.elias.ultimateChristmas.modules.advent.infra.AdventMenu;
import fr.elias.ultimateChristmas.modules.advent.infra.AdventReminderListener;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * The advent calendar: one door a day, with streaks and paid catch-up.
 *
 * <p>Fully reloadable in place: the rewards, the door mapping and the
 * parameters are all rebuilt from config, and the menu reads through this
 * module, so {@code /christmas module reload advent} takes effect on the next
 * menu open with nothing re-registered.
 */
@PluginModule(value = "advent", dependencies = "platform::platform", configuration = true)
public final class AdventModule extends FeatureModule implements AdventService {

    private AdventCalendar calendar;
    private volatile AdventParameters parameters;

    public AdventModule(ChristmasPlatform platform, AdventParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        calendar = new AdventCalendar(platform, parameters, this::emit);
        calendar.load(section("rewards"), section("doors"), parameters);

        command(new AdventCommand(calendar));
        listen(new AdventReminderListener(platform, calendar, () -> parameters));

        publish(AdventService.class, this);
    }

    @Override
    protected void onReload() {
        parameters = AdventParameters.from(config(), AdventParameters.DEFAULTS);
        calendar.load(section("rewards"), section("doors"), parameters);
    }

    // ------------------------------------------------------------ public API

    @Override
    public int currentDay() {
        return platform.season().currentDay();
    }

    @Override
    public int doorCount() {
        return calendar.doorCount();
    }

    @Override
    public Set<Integer> openedDoors(Player player) {
        return platform.profiles().get(player).adventClaimed();
    }

    @Override
    public boolean canClaimToday(Player player) {
        int today = currentDay();
        return today > 0 && calendar.check(player, today) == AdventCalendar.Outcome.CLAIMED;
    }

    @Override
    public void openMenu(Player player) {
        platform.menus().open(player, new AdventMenu(calendar));
    }

    /** Used by the reminder listener to decide whether to nudge a player. */
    public PlayerProfile profileOf(Player player) {
        return platform.profiles().get(player);
    }
}
