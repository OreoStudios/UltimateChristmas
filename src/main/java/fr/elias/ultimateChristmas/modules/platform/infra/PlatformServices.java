package fr.elias.ultimateChristmas.modules.platform.infra;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.core.command.CommandRouter;
import fr.elias.ultimateChristmas.core.config.ConfigManager;
import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.hook.Hooks;
import fr.elias.ultimateChristmas.core.menu.MenuManager;
import fr.elias.ultimateChristmas.core.progress.ProgressService;
import fr.elias.ultimateChristmas.core.reward.RewardService;
import fr.elias.ultimateChristmas.core.season.Season;
import fr.elias.ultimateChristmas.core.storage.ProfileManager;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Exposes the plugin's core services as the platform contract.
 *
 * <p>A thin delegate on purpose. The services themselves are owned by the
 * plugin, because several of them must exist before any module starts (player
 * profiles are loaded during pre-login, for instance). This class is what makes
 * them reachable across a module boundary without a feature module ever holding
 * a reference to {@link UltimateChristmas} itself.
 */
public final class PlatformServices implements ChristmasPlatform {

    private final UltimateChristmas plugin;

    public PlatformServices(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    @Override
    public JavaPlugin plugin() {
        return plugin;
    }

    @Override
    public ConfigManager configs() {
        return plugin.configs();
    }

    @Override
    public Messages messages() {
        return plugin.messages();
    }

    @Override
    public Season season() {
        return plugin.season();
    }

    @Override
    public ProfileManager profiles() {
        return plugin.profiles();
    }

    @Override
    public EconomyService economy() {
        return plugin.economy();
    }

    @Override
    public ProgressService progress() {
        return plugin.progress();
    }

    @Override
    public RewardService rewards() {
        return plugin.rewards();
    }

    @Override
    public MenuManager menus() {
        return plugin.menus();
    }

    @Override
    public Hooks hooks() {
        return plugin.hooks();
    }

    @Override
    public fr.elias.ultimateChristmas.core.model.DisplayModelRenderer modelRenderer() {
        return plugin.modelRenderer();
    }

    @Override
    public CommandRouter commands() {
        return plugin.commands();
    }

    /** The concrete plugin, for the few core helpers that still need it. */
    public UltimateChristmas core() {
        return plugin;
    }
}
