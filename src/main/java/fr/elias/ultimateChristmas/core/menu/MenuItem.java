package fr.elias.ultimateChristmas.core.menu;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * An icon in a menu, with what happens when it is clicked.
 *
 * @param icon    what the player sees
 * @param onClick handler, or {@code null} for a purely decorative icon
 */
public record MenuItem(ItemStack icon, Consumer<Click> onClick) {

    /** A decorative icon that ignores clicks. */
    public static MenuItem display(ItemStack icon) {
        return new MenuItem(icon, null);
    }

    public static MenuItem of(ItemStack icon, Consumer<Click> onClick) {
        return new MenuItem(icon, onClick);
    }

    public boolean clickable() {
        return onClick != null;
    }

    /** Details of one click on a menu icon. */
    public record Click(MenuView view, int slot, ClickType type) {

        public org.bukkit.entity.Player player() {
            return view.player();
        }

        public boolean left() {
            return type.isLeftClick();
        }

        public boolean right() {
            return type.isRightClick();
        }

        public boolean shift() {
            return type.isShiftClick();
        }
    }
}
