package fr.elias.ultimateChristmas.core.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * A chest-style interface.
 *
 * <p>Subclasses describe their contents in {@link #build(MenuView)}; the view
 * can call {@code build} again at any time to repaint in place, so a menu
 * showing live data (a balance, a countdown) stays correct without reopening
 * and without the flicker that causes.
 */
public abstract class Menu {

    /** Title shown to this player. Called once, when the inventory is created. */
    public abstract Component title(Player player);

    /** Height in rows, 1 to 6. */
    public abstract int rows();

    /** Populates the view. Called on open and on every refresh. */
    public abstract void build(MenuView view);

    /** Called after the inventory closes. */
    public void onClose(MenuView view) {
    }

    /**
     * Whether the player may interact with their own inventory while this menu
     * is open. Off by default, which is right for every menu here; a menu that
     * accepts deposited items turns it on.
     */
    public boolean allowPlayerInventory() {
        return false;
    }

    public final int size() {
        return Math.max(1, Math.min(6, rows())) * 9;
    }
}
