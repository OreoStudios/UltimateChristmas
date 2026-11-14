package fr.elias.ultimateChristmas.modules.pass.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.entity.Player;

/** The festive pass, as other modules see it. */
@ModuleApi("pass")
public interface PassService {

    /** The tier a player has reached, 0 if none. */
    int tierOf(Player player);

    /** The highest configured tier. */
    int maxTier();

    /** Adds pass experience and announces any tier-up. */
    void addXp(Player player, long amount);

    /** Whether this player holds the premium pass. */
    boolean isPremium(Player player);

    /** Opens the pass menu. */
    void openMenu(Player player);
}
