package fr.elias.ultimateChristmas.modules.feasts.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** Festive food and drink, as other modules see it. */
@ModuleApi("feasts")
public interface FeastService {

    /** All configured food ids. */
    Set<String> foods();

    /** Builds a festive food item, or {@code null} if the id is unknown. */
    ItemStack create(String foodId, int amount);

    /** Gives festive food to a player. Returns false if the id is unknown. */
    boolean give(Player player, String foodId, int amount);
}
