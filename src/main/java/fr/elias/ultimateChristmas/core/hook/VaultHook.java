package fr.elias.ultimateChristmas.core.hook;

import fr.elias.ultimateChristmas.UltimateChristmas;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Server-currency access, so shop entries can be priced in real money as well
 * as in shards.
 */
public final class VaultHook extends PluginHook {

    private Economy economy;

    public VaultHook(UltimateChristmas plugin) {
        super(plugin);
    }

    @Override
    public String pluginName() {
        return "Vault";
    }

    @Override
    protected boolean setUp() {
        RegisteredServiceProvider<Economy> provider =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            plugin.getLogger().info(
                    "Vault is installed but no economy plugin is registered with it.");
            return false;
        }
        economy = provider.getProvider();
        return economy != null;
    }

    public String currencyName() {
        return active() ? economy.currencyNamePlural() : "coins";
    }

    public double balance(OfflinePlayer player) {
        return active() ? economy.getBalance(player) : 0;
    }

    public boolean has(OfflinePlayer player, double amount) {
        return active() && economy.has(player, amount);
    }

    /** Takes money, returning false if the player could not afford it. */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!active() || amount <= 0) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!active() || amount <= 0) {
            return false;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        return active() ? economy.format(amount) : String.valueOf(amount);
    }
}
