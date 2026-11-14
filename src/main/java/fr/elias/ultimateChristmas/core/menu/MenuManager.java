package fr.elias.ultimateChristmas.core.menu;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

/**
 * Opens menus and routes inventory events to them.
 *
 * <p>Replaces the shaded SmartInvs library the 3.x build carried: that project
 * is unmaintained, had to be bundled into the jar, and needed a static
 * plugin-holder hack to work at all. This is a few hundred lines against the
 * Bukkit API, with no shading and no static state.
 */
public final class MenuManager implements Listener {

    private final UltimateChristmas plugin;

    public MenuManager(UltimateChristmas plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Builds and shows a menu. Must be called on the main thread. */
    public MenuView open(Player player, Menu menu) {
        MenuView view = new MenuView(menu, player);
        try {
            menu.build(view);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Menu " + menu.getClass().getSimpleName() + " failed to build", ex);
            plugin.messages().send(player, "menu.error");
            return null;
        }
        player.openInventory(view.getInventory());
        return view;
    }

    /** Reopens a menu one tick later, so it is safe to call from a click handler. */
    public void openLater(Player player, Menu menu) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                open(player, menu);
            }
        });
    }

    /** The menu view the player currently has open, or {@code null}. */
    public MenuView viewOf(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuView view
                && view.player().equals(player)) {
            return view;
        }
        return null;
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (viewOf(player) != null) {
                player.closeInventory();
            }
        }
    }

    // ----------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuView view)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean inMenu = event.getClickedInventory() == view.getInventory();

        // Shift-click and number-key swaps can move items between the two
        // inventories, so they are blocked even when the click itself landed in
        // the player's own inventory.
        boolean movesAcross = event.getClick().isShiftClick()
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                || event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND;

        if (!inMenu) {
            if (movesAcross || !view.menu().allowPlayerInventory()) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        MenuItem item = view.itemAt(event.getSlot());
        if (item == null || !item.clickable()) {
            return;
        }
        try {
            item.onClick().accept(new MenuItem.Click(view, event.getSlot(), event.getClick()));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Menu click handler failed for "
                    + player.getName() + " in " + view.menu().getClass().getSimpleName(), ex);
            plugin.messages().send(player, "menu.error");
        }
    }

    /** Dragging across menu slots would duplicate or delete icons. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuView view)) {
            return;
        }
        if (view.menu().allowPlayerInventory()) {
            for (int slot : event.getRawSlots()) {
                if (slot < view.size()) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuView view) {
            try {
                view.menu().onClose(view);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Menu close handler failed", ex);
            }
        }
    }

    /** A player quitting with a menu open still gets its close hook run. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        MenuView view = viewOf(event.getPlayer());
        if (view != null) {
            try {
                view.menu().onClose(view);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Menu close handler failed on quit", ex);
            }
        }
    }
}
