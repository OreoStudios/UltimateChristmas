package fr.elias.ultimateChristmas.modules.shop.infra;

import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.menu.MenuItem;
import fr.elias.ultimateChristmas.core.menu.MenuView;
import fr.elias.ultimateChristmas.core.menu.PagedMenu;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.core.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One category of the store, paged.
 *
 * <p>Each entry's lore shows the real price, the player's balance and why they
 * cannot buy it yet, so a refusal is visible before the click rather than as a
 * chat message afterwards.
 */
public final class ShopMenu extends PagedMenu {

    private final ShopCatalog catalog;
    private final ShopPurchases purchases;
    private final String title;
    private final boolean confirm;
    private ShopCatalog.Category category;

    public ShopMenu(ShopCatalog catalog, ShopPurchases purchases, String title,
                    boolean confirm, ShopCatalog.Category category) {
        this.catalog = catalog;
        this.purchases = purchases;
        this.title = title;
        this.confirm = confirm;
        this.category = category;
    }

    @Override
    public Component title(Player player) {
        return Text.parse(title, Messages.of(
                "category", category == null ? "" : Text.plain(category.name()),
                "balance", EconomyService.format(
                        purchases.platform().economy().balance(player))));
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    protected void decorate(MenuView view) {
        view.border(ItemBuilder.of(Material.RED_STAINED_GLASS_PANE).name(" ").build());

        // A category switcher along the bottom, when there is more than one.
        if (catalog.categories().size() > 1) {
            int column = 1;
            for (ShopCatalog.Category option : catalog.categories()) {
                if (column > 7) {
                    break;
                }
                int slot = option.slot() >= 0 ? option.slot() : MenuView.slot(5, column++);
                boolean active = category != null && option.id().equals(category.id());
                view.set(slot, MenuItem.of(
                        ItemBuilder.of(option.icon())
                                .name(option.name())
                                .addLore(active ? "<green>Viewing" : "<gray>Click to view")
                                .addLore("<dark_gray>" + option.entries().size() + " item(s)")
                                .glint(active)
                                .hideAll()
                                .build(),
                        click -> {
                            category = option;
                            click.view().refresh();
                        }));
            }
        }

        view.set(MenuView.slot(0, 4), MenuItem.display(balanceIcon(view.player())));
    }

    private ItemStack balanceIcon(Player player) {
        PlayerProfile profile = purchases.platform().profiles().get(player);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Shards: <gold>" + EconomyService.format(profile.shards()));
        if (purchases.platform().hooks().vault().active()) {
            lore.add("<gray>" + purchases.platform().hooks().vault().currencyName()
                    + ": <gold>" + purchases.platform().hooks().vault()
                    .format(purchases.platform().hooks().vault().balance(player)));
        }
        lore.add("<gray>Standing: <white>" + profile.naughtyNice());
        return ItemBuilder.of(Material.NETHER_STAR)
                .name("<gold><bold>Your wallet")
                .lore(lore)
                .hideAll()
                .build();
    }

    @Override
    protected List<MenuItem> entries(MenuView view) {
        List<MenuItem> items = new ArrayList<>();
        if (category == null) {
            return items;
        }
        Player player = view.player();
        PlayerProfile profile = purchases.platform().profiles().get(player);

        for (ShopCatalog.Entry entry : category.entries()) {
            ShopPurchases.Outcome state = purchases.check(player, entry);
            ItemBuilder builder = ItemBuilder.wrap(entry.icon());
            builder.addBlankLore();
            builder.addLore(entry.paidInShards()
                    ? "<gray>Price: <gold>" + EconomyService.format(entry.price()) + " shards"
                    : "<gray>Price: <gold>" + purchases.platform().hooks().vault()
                            .format(entry.money()));

            if (entry.stockPerDay() > 0) {
                builder.addLore("<gray>Left today: <white>"
                        + purchases.remainingStock(profile, entry)
                        + "<dark_gray>/" + entry.stockPerDay());
            }
            if (entry.requiresNice() != Integer.MIN_VALUE) {
                builder.addLore("<gray>Needs standing <white>" + entry.requiresNice()
                        + " <dark_gray>(you: " + profile.naughtyNice() + ")");
            }
            builder.addLore(switch (state) {
                case BOUGHT -> "<green>Click to buy";
                case CANNOT_AFFORD -> "<red>You cannot afford this";
                case NO_PERMISSION -> "<red>You do not have access to this";
                case OUT_OF_STOCK -> "<red>Sold out until tomorrow";
                case TOO_NAUGHTY -> "<red>You have been too naughty";
                case NO_ECONOMY -> "<red>No server economy is installed";
            });

            ItemStack icon = builder.build();
            if (state != ShopPurchases.Outcome.BOUGHT) {
                items.add(MenuItem.of(icon, click -> {
                    purchases.platform().messages().send(click.player(),
                            purchases.messageKey(state), Messages.of("entry", entry.id()));
                    click.player().playSound(click.player().getLocation(),
                            Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                }));
                continue;
            }

            items.add(MenuItem.of(icon, click -> {
                if (confirm) {
                    purchases.platform().menus().open(click.player(),
                            new ConfirmPurchaseMenu(this, purchases, entry));
                    return;
                }
                complete(click.player(), entry);
                click.view().refresh();
            }));
        }
        return items;
    }

    /** Runs a purchase and reports the result. Shared with the confirm menu. */
    void complete(Player player, ShopCatalog.Entry entry) {
        ShopPurchases.Outcome result = purchases.buy(player, entry);
        if (result != ShopPurchases.Outcome.BOUGHT) {
            purchases.platform().messages().send(player, purchases.messageKey(result),
                    Messages.of("entry", entry.id()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
        }
    }

    @Override
    protected MenuItem navCentre(MenuView view, int pages) {
        // The bottom row is the category switcher when there are several, so
        // only offer a close button when it is free.
        if (catalog.categories().size() > 1) {
            return null;
        }
        return MenuItem.of(ItemBuilder.of(Material.BARRIER).name("<red>Close").build(),
                click -> click.player().closeInventory());
    }
}
