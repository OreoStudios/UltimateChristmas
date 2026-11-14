package fr.elias.ultimateChristmas.modules.advent.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;
import fr.elias.ultimateChristmas.core.item.ItemBuilder;
import org.bukkit.Material;

/**
 * Typed settings for the advent calendar, built by its functor.
 *
 * @param doors              how many doors to show, capped by the season length
 * @param allowCatchUp       let players buy back a door they missed
 * @param catchUpCost        shard cost per day late for a catch-up
 * @param streakBonusEvery   pay a streak bonus every N consecutive days, 0 to disable
 * @param streakBonusShards  base shards for a streak bonus, multiplied by the streak tier
 * @param nicePointsPerDoor  naughty/nice credit for opening a door
 * @param celebrate          play particles and a jingle on a successful claim
 * @param title              menu title, with {@code %day%} and {@code %doors%}
 * @param openIcon           material shown on an opened door
 * @param todayIcon          material shown on today's unopened door
 * @param missedIcon         material shown on a missed door
 * @param lockedIcon         material shown on a future door
 * @param openedName         display name for an opened door
 * @param todayName          display name for today's door
 * @param missedName         display name for a missed door
 * @param lockedName         display name for a locked door
 */
public record AdventParameters(
        int doors,
        boolean allowCatchUp,
        int catchUpCost,
        int streakBonusEvery,
        int streakBonusShards,
        int nicePointsPerDoor,
        boolean celebrate,
        String title,
        String openIcon,
        String todayIcon,
        String missedIcon,
        String lockedIcon,
        String openedName,
        String todayName,
        String missedName,
        String lockedName
) {

    public static final AdventParameters DEFAULTS = new AdventParameters(
            25, true, 150, 5, 100, 1, true,
            "&c&lAdvent Calendar &7- Day %day%/%doors%",
            "LIME_STAINED_GLASS_PANE", "CHEST", "RED_STAINED_GLASS_PANE", "GRAY_STAINED_GLASS_PANE",
            "&aDay %day% &7- opened", "&e&lDay %day% &7- today!",
            "&cDay %day% &7- missed", "&8Day %day% &7- locked");

    public static AdventParameters from(ModuleConfiguration config, AdventParameters defaults) {
        return new AdventParameters(
                config.getInt("door-count", defaults.doors()),
                config.getBoolean("catch-up.enabled", defaults.allowCatchUp()),
                config.getInt("catch-up.cost-per-day", defaults.catchUpCost()),
                config.getInt("streak.bonus-every", defaults.streakBonusEvery()),
                config.getInt("streak.bonus-shards", defaults.streakBonusShards()),
                config.getInt("nice-points-per-door", defaults.nicePointsPerDoor()),
                config.getBoolean("celebrate", defaults.celebrate()),
                config.getString("menu.title", defaults.title()),
                config.getString("menu.icons.opened", defaults.openIcon()),
                config.getString("menu.icons.today", defaults.todayIcon()),
                config.getString("menu.icons.missed", defaults.missedIcon()),
                config.getString("menu.icons.locked", defaults.lockedIcon()),
                config.getString("menu.names.opened", defaults.openedName()),
                config.getString("menu.names.today", defaults.todayName()),
                config.getString("menu.names.missed", defaults.missedName()),
                config.getString("menu.names.locked", defaults.lockedName()));
    }

    // Resolved on demand so a bad material name in config degrades to a sane
    // icon instead of failing the whole module at startup.

    public Material openedMaterial() {
        return ItemBuilder.material(openIcon, Material.LIME_STAINED_GLASS_PANE);
    }

    public Material todayMaterial() {
        return ItemBuilder.material(todayIcon, Material.CHEST);
    }

    public Material missedMaterial() {
        return ItemBuilder.material(missedIcon, Material.RED_STAINED_GLASS_PANE);
    }

    public Material lockedMaterial() {
        return ItemBuilder.material(lockedIcon, Material.GRAY_STAINED_GLASS_PANE);
    }
}
