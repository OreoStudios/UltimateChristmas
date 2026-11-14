package fr.elias.ultimateChristmas.modules.quests.infra;

import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.menu.MenuItem;
import fr.elias.ultimateChristmas.core.menu.MenuView;
import fr.elias.ultimateChristmas.core.menu.PagedMenu;
import fr.elias.ultimateChristmas.core.season.Season;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The objectives list, with a progress bar on each entry.
 *
 * <p>Read-only: objectives complete themselves as the player plays, so there is
 * nothing to click. The menu exists to answer "what should I be doing?".
 */
public final class QuestMenu extends PagedMenu {

    private final ChristmasPlatform platform;
    private final QuestCatalog catalog;
    private final QuestTracker tracker;

    public QuestMenu(ChristmasPlatform platform, QuestCatalog catalog, QuestTracker tracker) {
        this.platform = platform;
        this.catalog = catalog;
        this.tracker = tracker;
    }

    @Override
    public Component title(Player player) {
        return Component.text("Festive objectives", NamedTextColor.DARK_GREEN);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    protected void decorate(MenuView view) {
        view.border(ItemBuilder.of(Material.GREEN_STAINED_GLASS_PANE).name(" ").build());
        view.set(MenuView.slot(0, 4), MenuItem.display(ItemBuilder.of(Material.CLOCK)
                .name("<gold><bold>Resets")
                .addLore("<gray>Daily objectives reset in <white>"
                        + Season.format(platform.season().untilReset()))
                .addLore("<dark_gray>Weekly objectives reset every 7 days")
                .hideAll()
                .build()));
    }

    @Override
    protected List<MenuItem> entries(MenuView view) {
        PlayerProfile profile = platform.profiles().get(view.player());
        tracker.rollOverIfNeeded(profile);

        List<MenuItem> items = new ArrayList<>();
        for (QuestCatalog.Quest quest : catalog.all()) {
            boolean done = profile.questCompleted(quest.id());
            long progress = Math.min(quest.goal(), profile.questProgress(quest.id()));

            ItemBuilder builder = ItemBuilder.of(done ? Material.LIME_DYE : quest.icon())
                    .name((done ? "<green>" : "<yellow>") + quest.name())
                    .addLore("<dark_gray>" + label(quest.scope()))
                    .addBlankLore()
                    .addLore(bar(progress, quest.goal()))
                    .addLore("<gray>" + progress + " <dark_gray>/ <white>" + quest.goal());

            if (quest.reward() != null) {
                builder.addBlankLore().addLore("<gray>Reward:");
                if (!quest.reward().shards().isZero()) {
                    builder.addLore("<dark_gray>- <gold>" + quest.reward().shards()
                            + " shards");
                }
                if (!quest.reward().passXp().isZero()) {
                    builder.addLore("<dark_gray>- <aqua>" + quest.reward().passXp()
                            + " pass xp");
                }
                for (var item : quest.reward().items()) {
                    builder.addLore("<dark_gray>- <white>" + item.getAmount() + "x "
                            + item.getType().name().toLowerCase(java.util.Locale.ROOT));
                }
            }
            if (done) {
                builder.addBlankLore().addLore("<green>Complete!").glint(true);
            }
            items.add(MenuItem.display(builder.hideAll().build()));
        }
        return items;
    }

    /** A twenty-segment progress bar, which reads clearly in item lore. */
    private static String bar(long progress, long goal) {
        int filled = goal <= 0 ? 0 : (int) Math.round(20.0 * progress / goal);
        filled = Math.max(0, Math.min(20, filled));
        return "<green>" + "▌".repeat(filled)
                + "<dark_gray>" + "▌".repeat(20 - filled);
    }

    private static String label(QuestCatalog.Scope scope) {
        return switch (scope) {
            case DAILY -> "Daily";
            case WEEKLY -> "Weekly";
            case SEASON -> "All season";
        };
    }

    @Override
    protected MenuItem emptyIcon() {
        return MenuItem.display(ItemBuilder.of(Material.BARRIER)
                .name("<gray>No objectives are set")
                .addLore("<dark_gray>Add some to modules/quests.yml")
                .build());
    }
}
