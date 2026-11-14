package fr.elias.ultimateChristmas.modules.snow.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.entity.Player;

/** Personal snowfall, as other modules see it. */
@ModuleApi("snow")
public interface SnowService {

    /** Whether this player currently has snowfall around them. */
    boolean enabledFor(Player player);

    /** Turns snowfall on or off for a player. Returns the new state. */
    boolean toggle(Player player);

    /** Starts a server-wide blizzard for the given number of seconds. */
    void startBlizzard(int seconds);

    /** Whether a blizzard is running right now. */
    boolean blizzardActive();
}
