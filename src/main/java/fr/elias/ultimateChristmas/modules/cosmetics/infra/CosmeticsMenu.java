package fr.elias.ultimateChristmas.modules.cosmetics.infra;

import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.menu.MenuItem;
import fr.elias.ultimateChristmas.core.menu.MenuView;
import fr.elias.ultimateChristmas.core.menu.PagedMenu;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** The wardrobe: hats and trails, with a tab per kind. */
public final class CosmeticsMenu extends PagedMenu {

    private final ChristmasPlatform platform;
    private final CosmeticCatalog catalog;
    /** Equip or unequip; the module reports its own failures. */
    private final BiConsumer<Player, String> equip;
    private final java.util.function.BiConsumer<Player, CosmeticCatalog.Kind> unequip;

    private CosmeticCatalog.Kind tab = CosmeticCatalog.Kind.HAT;

    public CosmeticsMenu(ChristmasPlatform platform, CosmeticCatalog catalog,
                         BiConsumer<Player, String> equip,
                         java.util.function.BiConsumer<Player, CosmeticCatalog.Kind> unequip) {
        this.platform = platform;
        this.catalog = catalog;
        this.equip = equip;
        this.unequip = unequip;
    }

    @Override
    public Component title(Player player) {
        return Component.text("Festive wardrobe", NamedTextColor.LIGHT_PURPLE);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    protected void decorate(MenuView view) {
        view.border(ItemBuilder.of(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build());

        tabButton(view, MenuView.slot(5, 2), CosmeticCatalog.Kind.HAT,
                Material.LEATHER_HELMET, "Hats");
        tabButton(view, MenuView.slot(5, 6), CosmeticCatalog.Kind.TRAIL,
                Material.FIREWORK_STAR, "Trails");

        view.set(MenuView.slot(5, 4), MenuItem.of(
                ItemBuilder.of(Material.BARRIER)
                        .name("<red>Take off my " + tab.slot())
                        .addLore("<gray>Remove whatever you have equipped")
                        .build(),
                click -> {
                    unequip.accept(click.player(), tab);
                    click.view().refresh();
                }));
    }

    private void tabButton(MenuView view, int slot, CosmeticCatalog.Kind kind,
                           Material icon, String label) {
        boolean active = tab == kind;
        view.set(slot, MenuItem.of(
                ItemBuilder.of(icon)
                        .name((active ? "<green>" : "<gray>") + label)
                        .addLore(active ? "<green>Viewing" : "<gray>Click to view")
                        .glint(active)
                        .hideAll()
                        .build(),
                click -> {
                    tab = kind;
                    click.view().refresh();
                }));
    }

    @Override
    protected List<MenuItem> entries(MenuView view) {
        PlayerProfile profile = platform.profiles().get(view.player());
        String equipped = profile.equipped(tab.slot());

        List<MenuItem> items = new ArrayList<>();
        for (CosmeticCatalog.Cosmetic cosmetic : catalog.ofKind(tab)) {
            boolean owns = cosmetic.freeToAll() || profile.owns(cosmetic.id())
                    || (cosmetic.permission() != null
                        && view.player().hasPermission(cosmetic.permission()));
            boolean wearing = cosmetic.id().equals(equipped);

            ItemBuilder builder = ItemBuilder.wrap(cosmetic.icon())
                    .addBlankLore();
            if (wearing) {
                builder.addLore("<green>Equipped").glint(true);
            } else if (owns) {
                builder.addLore("<yellow>Click to wear");
            } else {
                builder.addLore("<red>You do not own this yet");
            }

            var item = builder.hideAll().build();
            if (!owns || wearing) {
                items.add(MenuItem.display(item));
                continue;
            }
            items.add(MenuItem.of(item, click -> {
                equip.accept(click.player(), cosmetic.id());
                click.player().playSound(click.player().getLocation(),
                        Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.7f, 1.3f);
                click.view().refresh();
            }));
        }
        return items;
    }

    @Override
    protected MenuItem emptyIcon() {
        return MenuItem.display(ItemBuilder.of(Material.BARRIER)
                .name("<gray>Nothing here yet")
                .addLore("<dark_gray>Add some to modules/cosmetics.yml")
                .build());
    }
}
