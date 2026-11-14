package fr.elias.ultimateChristmas.modules.advent.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.entity.Player;

import java.util.Set;

/** The advent calendar, as other modules see it. */
@ModuleApi("advent")
public interface AdventService {

    /** Today's door number, or -1 outside the season. */
    int currentDay();

    /** How many doors the calendar has. */
    int doorCount();

    /** Doors this player has already opened. */
    Set<Integer> openedDoors(Player player);

    /** Whether today's door is still unopened. */
    boolean canClaimToday(Player player);

    /** Opens the calendar menu for a player. */
    void openMenu(Player player);
}
