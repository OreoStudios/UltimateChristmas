package fr.elias.ultimateChristmas.modules.shop.infra;

import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.effect.Fx;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.shop.api.ShopPurchaseEvent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Takes payment and delivers a shop entry.
 *
 * <p>Payment happens before delivery, and a failed payment delivers nothing —
 * the ordering that stops a full inventory or a bad command from handing out a
 * free item.
 */
public final class ShopPurchases {

    private final ChristmasPlatform platform;
    private final Consumer<Object> emit;

    public ShopPurchases(ChristmasPlatform platform, Consumer<Object> emit) {
        this.platform = platform;
        this.emit = emit;
    }

    /** Why a purchase was refused, or that it went through. */
    public enum Outcome {
        BOUGHT,
        NO_PERMISSION,
        CANNOT_AFFORD,
        OUT_OF_STOCK,
        TOO_NAUGHTY,
        NO_ECONOMY
    }

    /** Whether this player is allowed to buy the entry at all. */
    public Outcome check(Player player, ShopCatalog.Entry entry) {
        if (entry.permission() != null && !player.hasPermission(entry.permission())) {
            return Outcome.NO_PERMISSION;
        }
        PlayerProfile profile = platform.profiles().get(player);

        if (entry.requiresNice() != Integer.MIN_VALUE
                && profile.naughtyNice() < entry.requiresNice()) {
            return Outcome.TOO_NAUGHTY;
        }
        if (entry.stockPerDay() > 0 && purchasesToday(profile, entry) >= entry.stockPerDay()) {
            return Outcome.OUT_OF_STOCK;
        }
        if (entry.paidInShards()) {
            return platform.economy().has(profile, entry.price())
                    ? Outcome.BOUGHT : Outcome.CANNOT_AFFORD;
        }
        if (!platform.hooks().vault().active()) {
            return Outcome.NO_ECONOMY;
        }
        return platform.hooks().vault().has(player, entry.money())
                ? Outcome.BOUGHT : Outcome.CANNOT_AFFORD;
    }

    /**
     * Charges for and delivers an entry.
     *
     * @return what happened; only {@link Outcome#BOUGHT} delivered anything
     */
    public Outcome buy(Player player, ShopCatalog.Entry entry) {
        Outcome allowed = check(player, entry);
        if (allowed != Outcome.BOUGHT) {
            return allowed;
        }

        PlayerProfile profile = platform.profiles().get(player);
        if (entry.paidInShards()) {
            if (!platform.economy().take(profile, entry.price())) {
                return Outcome.CANNOT_AFFORD;
            }
        } else if (!platform.hooks().vault().withdraw(player, entry.money())) {
            return Outcome.CANNOT_AFFORD;
        }

        if (entry.stockPerDay() > 0) {
            profile.addStat(stockKey(entry), 1);
            profile.setStat(stockDayKey(entry), platform.season().todayEpochDay());
        }

        platform.rewards().grant(player, entry.reward(), Messages.of(
                "entry", entry.id(),
                "price", EconomyService.format(entry.price())));

        platform.messages().send(player, "shop.bought", Messages.of(
                "entry", entry.id(),
                "price", entry.paidInShards()
                        ? EconomyService.format(entry.price()) + " shards"
                        : platform.hooks().vault().format(entry.money()),
                "balance", EconomyService.format(profile.shards())));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        Fx.burst(player.getLocation().add(0, 1, 0), org.bukkit.Particle.HAPPY_VILLAGER, 12, 0.3);

        emit.accept(new ShopPurchaseEvent(player.getUniqueId(), entry.id(),
                entry.price(), entry.money()));
        return Outcome.BOUGHT;
    }

    /**
     * How many of this entry the player has bought today.
     *
     * <p>The count is stored alongside the day it belongs to, so it resets on
     * the season clock's rollover rather than needing a scheduled wipe.
     */
    public long purchasesToday(PlayerProfile profile, ShopCatalog.Entry entry) {
        long day = profile.stat(stockDayKey(entry));
        if (day != platform.season().todayEpochDay()) {
            return 0;
        }
        return profile.stat(stockKey(entry));
    }

    /** Resets the counter if it belongs to a previous day, then returns it. */
    public long remainingStock(PlayerProfile profile, ShopCatalog.Entry entry) {
        if (entry.stockPerDay() <= 0) {
            return Long.MAX_VALUE;
        }
        long used = purchasesToday(profile, entry);
        if (used == 0 && profile.stat(stockKey(entry)) != 0) {
            profile.setStat(stockKey(entry), 0);
        }
        return Math.max(0, entry.stockPerDay() - used);
    }

    private static String stockKey(ShopCatalog.Entry entry) {
        return "shop_bought_" + entry.id();
    }

    private static String stockDayKey(ShopCatalog.Entry entry) {
        return "shop_day_" + entry.id();
    }

    public String messageKey(Outcome outcome) {
        return switch (outcome) {
            case BOUGHT -> "shop.bought";
            case NO_PERMISSION -> "shop.no-permission";
            case CANNOT_AFFORD -> "shop.cannot-afford";
            case OUT_OF_STOCK -> "shop.out-of-stock";
            case TOO_NAUGHTY -> "shop.too-naughty";
            case NO_ECONOMY -> "shop.no-economy";
        };
    }

    public ChristmasPlatform platform() {
        return platform;
    }
}
