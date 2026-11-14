package fr.elias.ultimateChristmas.modules.snowball.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** Trick snowballs, as other modules see it. */
@ModuleApi("snowball")
public interface SnowballService {

    /** The configured trick-snowball ids. */
    Set<String> kinds();

    /** Builds a trick snowball, or {@code null} if the id is unknown. */
    ItemStack create(String kind, int amount);

    /** Gives a player some trick snowballs. Returns false if the id is unknown. */
    boolean give(Player player, String kind, int amount);
}
