package fr.elias.ultimateChristmas.modules.events.infra;

import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.menu.MenuItem;
import fr.elias.ultimateChristmas.core.menu.MenuView;
import fr.elias.ultimateChristmas.core.menu.PagedMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin dashboard for every configured Christmas event.
 *
 * Left-click: start now; right-click: stop now; shift-left: restore automatic
 * scheduling; shift-right: persistently enable/disable this event.
 */
public final class EventControlMenu extends PagedMenu {
    private final EventControl control;

    public EventControlMenu(EventControl control) {
        this.control = control;
    }

    @Override
    public Component title(Player player) {
        long running = control.statuses().stream().filter(EventControl.Status::active).count();
        return Component.text("Christmas Events (" + running + " active)", NamedTextColor.DARK_GREEN);
    }

    @Override
    public int rows() { return 6; }

    @Override
    protected void decorate(MenuView view) {
        view.border(ItemBuilder.of(Material.GREEN_STAINED_GLASS_PANE).name(" ").build());
        view.set(MenuView.slot(0, 4), MenuItem.display(
                ItemBuilder.of(Material.CLOCK)
                        .name("<green><bold>Christmas Event Scheduler")
                        .addLore("<gray>Left-click: start event now")
                        .addLore("<gray>Right-click: stop event now")
                        .addLore("<gray>Shift + left: return to automatic schedule")
                        .addLore("<gray>Shift + right: enable/disable persistently")
                        .hideAll().build()));
        view.set(MenuView.slot(5, 8), MenuItem.of(
                ItemBuilder.of(Material.COMPASS).name("<yellow>Refresh status").build(),
                click -> click.view().refresh()));
    }

    @Override
    protected List<MenuItem> entries(MenuView view) {
        List<MenuItem> entries = new ArrayList<>();
        for (EventControl.Status event : control.statuses()) {
            Material icon = !event.enabled() ? Material.BARRIER
                    : event.active() ? Material.EMERALD : Material.CLOCK;
            ItemBuilder builder = ItemBuilder.of(icon)
                    .name("<gold><bold>" + event.id())
                    .addLore("<gray>Schedule: <white>" + event.schedule())
                    .addLore("<gray>Enabled: " + (event.enabled() ? "<green>YES" : "<red>NO"))
                    .addLore("<gray>Active: " + (event.active() ? "<green>YES" : "<red>NO"))
                    .addLore("<gray>Control: " + (event.override() == null
                            ? "<aqua>AUTOMATIC" : event.override()
                            ? "<yellow>MANUAL START" : "<yellow>MANUAL STOP"))
                    .addBlankLore()
                    .addLore("<yellow>Left-click: start now")
                    .addLore("<yellow>Right-click: stop now")
                    .addLore("<yellow>Shift + left: automatic")
                    .addLore("<yellow>Shift + right: toggle enabled")
                    .hideAll();
            if (event.active()) builder.glint(true);
            entries.add(MenuItem.of(builder.build(), click -> {
                boolean succeeded;
                if (click.shift() && click.right()) {
                    succeeded = control.setEnabled(event.id(), !event.enabled());
                } else if (click.shift()) {
                    succeeded = control.auto(event.id());
                } else if (click.right()) {
                    succeeded = control.stopNow(event.id());
                } else {
                    succeeded = control.startNow(event.id());
                }
                if (!succeeded) {
                    click.player().sendMessage(Component.text("Could not update " + event.id(),
                            NamedTextColor.RED));
                }
                click.view().refresh();
            }));
        }
        return entries;
    }
}
