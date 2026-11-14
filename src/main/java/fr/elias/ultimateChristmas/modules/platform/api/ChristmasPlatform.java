package fr.elias.ultimateChristmas.modules.platform.api;

import dev.oreo.modulith.core.ModuleApi;
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
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The shared infrastructure every feature module is allowed to use.
 *
 * <p>This is the one service contract that crosses every module boundary. It
 * exists so a feature module never imports another feature module's internals
 * to get at storage, text or rewards: it depends on {@code platform::platform}
 * and asks for what it needs.
 *
 * <p>Feature-to-feature communication does <em>not</em> go through here — that
 * uses each module's own {@code @ModuleApi} contract, or a domain event on the
 * Modulith event bus.
 */
@ModuleApi("platform")
public interface ChristmasPlatform {

    /** The owning plugin, for anything that needs a {@code Plugin} handle. */
    JavaPlugin plugin();

    /** Global config.yml and lang.yml. Per-module settings live in a module's own config. */
    ConfigManager configs();

    Messages messages();

    /** The season clock: today's advent day, the countdown, day rollover. */
    Season season();

    ProfileManager profiles();

    EconomyService economy();

    /** Where a module records that a player did something trackable. */
    ProgressService progress();

    /** Grants a parsed {@code Reward} to a player. */
    RewardService rewards();

    MenuManager menus();

    /** Optional third-party integrations, each safe to call when absent. */
    Hooks hooks();

    /** The plugin's own animated-model renderer (the {@code display} provider). */
    fr.elias.ultimateChristmas.core.model.DisplayModelRenderer modelRenderer();

    /** The {@code /christmas} command tree a module contributes subcommands to. */
    CommandRouter commands();
}
