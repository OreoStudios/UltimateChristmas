package fr.elias.ultimateChristmas.modules.shop.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.entity.Player;

import java.util.Set;

/** Santa's Workshop store, as other modules see it. */
@ModuleApi("shop")
public interface ShopService {

    /** Opens the store for a player. */
    void openShop(Player player);

    /** The configured category ids, in display order. */
    Set<String> categories();

    /** How many entries the store has across all categories. */
    int entryCount();
}
