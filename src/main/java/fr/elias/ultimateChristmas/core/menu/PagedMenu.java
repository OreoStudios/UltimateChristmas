package fr.elias.ultimateChristmas.core.menu;

import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import org.bukkit.Material;

import java.util.List;

/**
 * A menu that spreads a list of entries over several pages.
 *
 * <p>Subclasses supply the entries and how to render one; paging, the nav row
 * and the empty state are handled here.
 */
public abstract class PagedMenu extends Menu {

    private int page;

    /** Slots entries are laid into, in order. Defaults to the inner rectangle. */
    protected int[] contentSlots() {
        int rows = rows();
        int[] slots = new int[Math.max(0, (rows - 2) * 7)];
        int index = 0;
        for (int row = 1; row < rows - 1; row++) {
            for (int column = 1; column <= 7; column++) {
                slots[index++] = MenuView.slot(row, column);
            }
        }
        return slots;
    }

    /** The full, unpaged list of entries. */
    protected abstract List<MenuItem> entries(MenuView view);

    /** Painted before the entries, for borders and static icons. */
    protected void decorate(MenuView view) {
        view.border(ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    /** Shown in the middle of the nav row; override to add a custom button. */
    protected MenuItem navCentre(MenuView view, int pages) {
        return null;
    }

    /** Shown when there is nothing to list. */
    protected MenuItem emptyIcon() {
        return MenuItem.display(ItemBuilder.of(Material.BARRIER)
                .name("<gray>Nothing here yet")
                .build());
    }

    @Override
    public final void build(MenuView view) {
        decorate(view);

        List<MenuItem> all = entries(view);
        int[] slots = contentSlots();
        int perPage = Math.max(1, slots.length);
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));

        if (all.isEmpty()) {
            view.set(slots.length > 0 ? slots[slots.length / 2] : 0, emptyIcon());
        } else {
            int from = page * perPage;
            int to = Math.min(all.size(), from + perPage);
            for (int index = from; index < to; index++) {
                view.set(slots[index - from], all.get(index));
            }
        }

        int navRow = rows() - 1;
        if (page > 0) {
            view.set(MenuView.slot(navRow, 3), MenuItem.of(
                    ItemBuilder.of(Material.ARROW)
                            .name("<yellow>Previous page")
                            .addLore("<dark_gray>Page " + page + " of " + pages)
                            .build(),
                    click -> {
                        page--;
                        click.view().refresh();
                    }));
        }
        if (page < pages - 1) {
            view.set(MenuView.slot(navRow, 5), MenuItem.of(
                    ItemBuilder.of(Material.ARROW)
                            .name("<yellow>Next page")
                            .addLore("<dark_gray>Page " + (page + 2) + " of " + pages)
                            .build(),
                    click -> {
                        page++;
                        click.view().refresh();
                    }));
        }
        MenuItem centre = navCentre(view, pages);
        if (centre != null) {
            view.set(MenuView.slot(navRow, 4), centre);
        }
    }

    protected int page() {
        return page;
    }
}
