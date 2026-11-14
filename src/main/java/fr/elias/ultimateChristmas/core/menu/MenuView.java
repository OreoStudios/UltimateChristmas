package fr.elias.ultimateChristmas.core.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * One player's live instance of a {@link Menu}.
 *
 * <p>Doubles as the inventory's {@link InventoryHolder}, which is how click
 * events are matched back to a menu. That is sturdier than comparing inventory
 * titles — the trick the 3.x GUIs relied on, which broke as soon as two menus
 * shared a title or a title contained a placeholder.
 */
public final class MenuView implements InventoryHolder {

    private final Menu menu;
    private final Player player;
    private final Map<Integer, MenuItem> items = new HashMap<>();
    private final Inventory inventory;
    /** Set while {@link #refresh()} rebuilds, to keep handlers from recursing. */
    private boolean building;

    MenuView(Menu menu, Player player) {
        this.menu = menu;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, menu.size(), menu.title(player));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Menu menu() {
        return menu;
    }

    public Player player() {
        return player;
    }

    public int rows() {
        return menu.rows();
    }

    public int size() {
        return menu.size();
    }

    // -------------------------------------------------------------- painting

    public MenuView set(int slot, MenuItem item) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return this;
        }
        items.put(slot, item);
        inventory.setItem(slot, item == null ? null : item.icon());
        return this;
    }

    public MenuView set(int slot, ItemStack icon) {
        return set(slot, MenuItem.display(icon));
    }

    public MenuView set(int row, int column, MenuItem item) {
        return set(slot(row, column), item);
    }

    public MenuItem itemAt(int slot) {
        return items.get(slot);
    }

    public MenuView clear() {
        items.clear();
        inventory.clear();
        return this;
    }

    /** Fills every empty slot with a decorative icon. */
    public MenuView fill(ItemStack icon) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                set(slot, MenuItem.display(icon.clone()));
            }
        }
        return this;
    }

    /** Draws a one-slot frame around the edge of the menu. */
    public MenuView border(ItemStack icon) {
        int rows = rows();
        for (int column = 0; column < 9; column++) {
            set(slot(0, column), MenuItem.display(icon.clone()));
            set(slot(rows - 1, column), MenuItem.display(icon.clone()));
        }
        for (int row = 1; row < rows - 1; row++) {
            set(slot(row, 0), MenuItem.display(icon.clone()));
            set(slot(row, 8), MenuItem.display(icon.clone()));
        }
        return this;
    }

    /** Repaints from {@link Menu#build}, keeping the inventory open. */
    public void refresh() {
        if (building) {
            return;
        }
        building = true;
        try {
            clear();
            menu.build(this);
            player.updateInventory();
        } finally {
            building = false;
        }
    }

    /** Converts a zero-based row and column to a slot index. */
    public static int slot(int row, int column) {
        return row * 9 + column;
    }
}
