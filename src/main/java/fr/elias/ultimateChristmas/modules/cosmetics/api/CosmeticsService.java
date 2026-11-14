package fr.elias.ultimateChristmas.modules.cosmetics.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.entity.Player;

import java.util.Set;

/** Festive cosmetics, as other modules see it. */
@ModuleApi("cosmetics")
public interface CosmeticsService {

    /** All configured cosmetic ids. */
    Set<String> catalogue();

    /** Cosmetics this player owns. */
    Set<String> owned(Player player);

    /** Grants a cosmetic. Returns false if the id is unknown. */
    boolean grant(Player player, String cosmeticId);

    /** Equips a cosmetic the player owns. Returns false if they cannot. */
    boolean equip(Player player, String cosmeticId);

    /** Opens the wardrobe. */
    void openMenu(Player player);
}
