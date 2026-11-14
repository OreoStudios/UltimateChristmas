package fr.elias.ultimateChristmas.core.economy;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The shard currency.
 *
 * <p>Balances live on the cached {@link PlayerProfile} and are written out by
 * the profile manager's async batch. A balance change here costs a map update
 * and a dirty flag; the 3.x equivalent saved the entire player data file to disk
 * synchronously on every transaction.
 */
public final class EconomyService {

    private static final DecimalFormat FORMAT =
            new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final UltimateChristmas plugin;

    public EconomyService(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    public long balance(PlayerProfile profile) {
        return profile.shards();
    }

    public long balance(Player player) {
        return plugin.profiles().get(player).shards();
    }

    public boolean has(PlayerProfile profile, long amount) {
        return profile.shards() >= amount;
    }

    /** Adds shards. Negative amounts are ignored; use {@link #take} instead. */
    public void give(PlayerProfile profile, long amount) {
        if (amount <= 0) {
            return;
        }
        profile.shards(profile.shards() + amount);
        profile.addStat(PlayerProfile.STAT_SHARDS_EARNED, amount);
    }

    /**
     * Adds shards to an online player and records the earning for quests and
     * the festive pass.
     */
    public void give(Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        PlayerProfile profile = plugin.profiles().get(player);
        profile.shards(profile.shards() + amount);
        plugin.progress().record(player, PlayerProfile.STAT_SHARDS_EARNED, amount);
    }

    /**
     * Removes shards if the player can afford it.
     *
     * @return false, having changed nothing, if the balance is too low
     */
    public boolean take(PlayerProfile profile, long amount) {
        if (amount <= 0) {
            return true;
        }
        long balance = profile.shards();
        if (balance < amount) {
            return false;
        }
        profile.shards(balance - amount);
        return true;
    }

    public void set(PlayerProfile profile, long amount) {
        profile.shards(Math.max(0, amount));
    }

    /** Formats a balance with thousands separators. */
    public static String format(long amount) {
        return FORMAT.format(amount);
    }

    /**
     * The configured display name for the currency, singular or plural.
     *
     * <p>Read from config.yml rather than the economy module's own file: the
     * name appears in messages from the shop, the advent calendar and the boss
     * rewards, so it is not the economy module's private business.
     */
    public String currencyName(long amount) {
        return plugin.configs().main().getString(
                amount == 1 ? "currency.singular" : "currency.plural",
                amount == 1 ? "shard" : "shards");
    }
}
