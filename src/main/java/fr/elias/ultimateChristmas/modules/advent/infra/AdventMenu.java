package fr.elias.ultimateChristmas.modules.advent.infra;

import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import fr.elias.ultimateChristmas.core.item.Keys;
import fr.elias.ultimateChristmas.core.menu.Menu;
import fr.elias.ultimateChristmas.core.menu.MenuItem;
import fr.elias.ultimateChristmas.core.menu.MenuView;
import fr.elias.ultimateChristmas.core.reward.Reward;
import fr.elias.ultimateChristmas.core.season.Season;
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
import java.util.Map;

/**
 * The calendar itself: one clickable door per day.
 *
 * <p>Doors show their real state — opened, available now, buyable as a catch-up,
 * or still locked — and the menu repaints in place after a claim so the door
 * visibly opens without the inventory flickering shut.
 */
public final class AdventMenu extends Menu {

    private final AdventCalendar calendar;

    public AdventMenu(AdventCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public Component title(Player player) {
        int day = calendar.platform().season().currentDay();
        return Text.parse(calendar.parameters().title(), Messages.of(
                "day", day > 0 ? String.valueOf(day) : "-",
                "doors", String.valueOf(calendar.doorCount())));
    }

    @Override
    public int rows() {
        // 24 or 25 doors need four content rows plus a footer.
        return 6;
    }

    @Override
    public void build(MenuView view) {
        view.border(ItemBuilder.of(Material.GREEN_STAINED_GLASS_PANE).name(" ").build());

        int doors = calendar.doorCount();
        int[] slots = doorSlots(doors);
        for (int day = 1; day <= doors && day <= slots.length; day++) {
            view.set(slots[day - 1], doorIcon(view.player(), day));
        }

        view.set(MenuView.slot(5, 4), MenuItem.display(summaryIcon(view.player())));
    }

    /** Lays doors out in rows 1-4, columns 1-7, filling 28 slots. */
    private int[] doorSlots(int doors) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4 && slots.size() < doors; row++) {
            for (int column = 1; column <= 7 && slots.size() < doors; column++) {
                slots.add(MenuView.slot(row, column));
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private MenuItem doorIcon(Player player, int day) {
        AdventCalendar.Outcome state = calendar.check(player, day);
        boolean opened = calendar.opened(player, day);
        Season season = calendar.platform().season();
        int today = season.currentDay();

        Map<String, String> placeholders = Messages.of(
                "day", String.valueOf(day),
                "date", season.dateOfDay(day).toString());

        ItemBuilder builder;
        if (opened) {
            Reward reward = calendar.fixedRewardFor(day);
            ItemStack preview = reward == null ? null : reward.display();
            builder = preview != null
                    ? ItemBuilder.wrap(preview)
                    : ItemBuilder.of(calendar.parameters().openedMaterial());
            builder.name(calendar.parameters().openedName(), placeholders)
                    .addLore("<dark_gray>" + season.dateOfDay(day))
                    .addLore("<green>Opened")
                    .glint(true);
        } else if (state == AdventCalendar.Outcome.CLAIMED && day == today) {
            builder = ItemBuilder.of(calendar.parameters().todayMaterial())
                    .name(calendar.parameters().todayName(), placeholders)
                    .addLore("<dark_gray>" + season.dateOfDay(day))
                    .addLore("<yellow>Click to open")
                    .glint(true);
        } else if (state == AdventCalendar.Outcome.CLAIMED) {
            long cost = calendar.catchUpCost(today - day);
            builder = ItemBuilder.of(calendar.parameters().missedMaterial())
                    .name(calendar.parameters().missedName(), placeholders)
                    .addLore("<dark_gray>" + season.dateOfDay(day))
                    .addLore("<gray>Missed - open it for <gold>"
                            + EconomyService.format(cost) + " shards")
                    .addLore("<yellow>Click to catch up");
        } else if (state == AdventCalendar.Outcome.CANNOT_AFFORD) {
            long cost = calendar.catchUpCost(today - day);
            builder = ItemBuilder.of(calendar.parameters().missedMaterial())
                    .name(calendar.parameters().missedName(), placeholders)
                    .addLore("<dark_gray>" + season.dateOfDay(day))
                    .addLore("<red>Needs " + EconomyService.format(cost) + " shards");
        } else if (state == AdventCalendar.Outcome.CATCH_UP_DISABLED) {
            builder = ItemBuilder.of(calendar.parameters().missedMaterial())
                    .name(calendar.parameters().missedName(), placeholders)
                    .addLore("<dark_gray>" + season.dateOfDay(day))
                    .addLore("<red>Missed");
        } else {
            builder = ItemBuilder.of(calendar.parameters().lockedMaterial())
                    .name(calendar.parameters().lockedName(), placeholders)
                    .addLore("<dark_gray>" + season.dateOfDay(day))
                    .addLore("<gray>Opens on " + season.dateOfDay(day));
        }

        ItemStack icon = builder.amount(Math.min(64, day))
                .tag(Keys.adventDay(), day)
                .hideAll()
                .build();

        boolean clickable = !opened && (state == AdventCalendar.Outcome.CLAIMED);
        if (!clickable) {
            return MenuItem.display(icon);
        }
        return MenuItem.of(icon, click -> {
            AdventCalendar.Outcome result = calendar.claim(click.player(), day);
            if (result != AdventCalendar.Outcome.CLAIMED) {
                report(click.player(), result, day);
                click.player().playSound(click.player().getLocation(),
                        Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            }
            // Repaint either way: a refused claim may have changed state
            // (someone else's catch-up spending their shards, say).
            click.view().refresh();
        });
    }

    private ItemStack summaryIcon(Player player) {
        PlayerProfile profile = calendar.platform().profiles().get(player);
        Season season = calendar.platform().season();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Opened: <white>" + profile.adventClaimed().size()
                + "<gray>/<white>" + calendar.doorCount());
        lore.add("<gray>Streak: <white>" + profile.adventStreak()
                + " <dark_gray>(best " + profile.adventBestStreak() + ")");
        lore.add("<gray>Shards: <white>" + EconomyService.format(profile.shards()));
        lore.add("");
        if (season.currentDay() > 0) {
            lore.add("<gray>Next door in <white>" + Season.format(season.untilReset()));
        } else {
            lore.add("<gray>The season starts <white>" + season.start());
        }
        return ItemBuilder.of(Material.BOOK)
                .name("<gold><bold>Your calendar")
                .lore(lore)
                .hideAll()
                .build();
    }

    private void report(Player player, AdventCalendar.Outcome outcome, int day) {
        Map<String, String> placeholders = Messages.of("day", String.valueOf(day));
        String key = switch (outcome) {
            case ALREADY_OPEN -> "advent.already-open";
            case NOT_YET -> "advent.not-yet";
            case OUTSIDE_SEASON -> "advent.outside-season";
            case CATCH_UP_DISABLED -> "advent.catch-up-disabled";
            case CANNOT_AFFORD -> "advent.cannot-afford";
            case NO_REWARD -> "advent.no-reward";
            case CLAIMED -> null;
        };
        if (key != null) {
            calendar.platform().messages().send(player, key, placeholders);
        }
    }
}
