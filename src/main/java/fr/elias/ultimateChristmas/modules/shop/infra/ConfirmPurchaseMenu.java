package fr.elias.ultimateChristmas.modules.shop.infra;

import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.menu.Menu;
import fr.elias.ultimateChristmas.core.menu.MenuItem;
import fr.elias.ultimateChristmas.core.menu.MenuView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * A yes/no step before money changes hands.
 *
 * <p>Confirmation matters here because several entries cost hundreds of shards
 * and a misclick in a paged menu is easy. Declining returns to the store rather
 * than closing it.
 */
public final class ConfirmPurchaseMenu extends Menu {

    private final ShopMenu parent;
    private final ShopPurchases purchases;
    private final ShopCatalog.Entry entry;

    public ConfirmPurchaseMenu(ShopMenu parent, ShopPurchases purchases,
                               ShopCatalog.Entry entry) {
        this.parent = parent;
        this.purchases = purchases;
        this.entry = entry;
    }

    @Override
    public Component title(Player player) {
        return Component.text("Confirm purchase", NamedTextColor.DARK_RED);
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public void build(MenuView view) {
        view.fill(ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

        view.set(MenuView.slot(1, 4), MenuItem.display(
                ItemBuilder.wrap(entry.icon())
                        .addBlankLore()
                        .addLore(entry.paidInShards()
                                ? "<gray>Costs <gold>" + EconomyService.format(entry.price())
                                        + " shards"
                                : "<gray>Costs <gold>" + purchases.platform().hooks().vault()
                                        .format(entry.money()))
                        .build()));

        view.set(MenuView.slot(1, 2), MenuItem.of(
                ItemBuilder.of(Material.LIME_CONCRETE)
                        .name("<green><bold>Buy it")
                        .addLore("<gray>Confirm this purchase")
                        .build(),
                click -> {
                    parent.complete(click.player(), entry);
                    // Back to the store, repainted with the new balance.
                    purchases.platform().menus().openLater(click.player(), parent);
                }));

        view.set(MenuView.slot(1, 6), MenuItem.of(
                ItemBuilder.of(Material.RED_CONCRETE)
                        .name("<red><bold>Cancel")
                        .addLore("<gray>Go back to the store")
                        .build(),
                click -> purchases.platform().menus().openLater(click.player(), parent)));
    }
}
