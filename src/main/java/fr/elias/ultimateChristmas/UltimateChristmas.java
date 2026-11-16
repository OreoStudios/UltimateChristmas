package fr.elias.ultimateChristmas;

import dev.oreo.modulith.core.ModuleRuntime;
import dev.oreo.modulith.core.ModuleState;
import dev.oreo.modulith.paper.PaperModulith;
import fr.elias.ultimateChristmas.core.command.CommandRouter;
import fr.elias.ultimateChristmas.core.config.ConfigManager;
import fr.elias.ultimateChristmas.core.economy.EconomyService;
import fr.elias.ultimateChristmas.core.hook.Hooks;
import fr.elias.ultimateChristmas.core.item.Keys;
import fr.elias.ultimateChristmas.core.menu.MenuManager;
import fr.elias.ultimateChristmas.core.progress.ProgressService;
import fr.elias.ultimateChristmas.core.reward.RewardService;
import fr.elias.ultimateChristmas.core.season.Season;
import fr.elias.ultimateChristmas.core.storage.ProfileManager;
import fr.elias.ultimateChristmas.core.storage.ProfileStore;
import fr.elias.ultimateChristmas.core.storage.SqliteProfileStore;
import fr.elias.ultimateChristmas.core.storage.YamlProfileStore;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.ModuleWiring;
import fr.elias.ultimateChristmas.modules.platform.infra.PlatformServices;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Plugin entry point.
 *
 * <p>Deliberately thin. It builds the core services, hands them to
 * MinecraftModulith as the {@code platform} module, and lets the Modulith
 * runtime own module discovery, dependency ordering and per-module lifecycle.
 * Nothing here knows how any individual feature works.
 *
 * @see ModuleWiring the functor bindings
 * @see fr.elias.ultimateChristmas.core.modulith.FeatureModule
 */
public final class UltimateChristmas extends JavaPlugin {

    private ConfigManager configs;
    private Messages messages;
    private Season season;
    private Hooks hooks;
    private ProfileManager profiles;
    private EconomyService economy;
    private ProgressService progress;
    private RewardService rewards;
    private MenuManager menus;
    private CommandRouter commands;
    private fr.elias.ultimateChristmas.core.model.RigRegistry rigs;
    private fr.elias.ultimateChristmas.core.model.DisplayModelRenderer modelRenderer;
    private org.bukkit.scheduler.BukkitTask modelTask;

    private PaperModulith modulith;

    @Override
    public void onEnable() {
        long startedAt = System.currentTimeMillis();

        if (!setUpCore()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            modulith = ModuleWiring.start(this, new PlatformServices(this));
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "The module runtime failed to start", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        reportStartup(System.currentTimeMillis() - startedAt);
    }

    private void reportStartup(long elapsedMs) {
        var states = modulith.runtime().states();
        long running = states.values().stream().filter(state -> state == ModuleState.RUNNING).count();
        getLogger().info("UltimateChristmas " + getPluginMeta().getVersion()
                + " enabled in " + elapsedMs + "ms - " + running + "/" + states.size()
                + " modules running.");

        var failures = modulith.runtime().failures();
        if (!failures.isEmpty()) {
            getLogger().warning("These modules did not start: " + failures.keySet()
                    + ". Run /christmas modules for details.");
        }
        getLogger().info(season.toString());
        if (!season.active()) {
            getLogger().info("Outside the configured season window (" + season.start()
                    + " to " + season.end() + "); seasonal features stay quiet until it starts.");
        }
    }

    /**
     * Builds the core services.
     *
     * @return false if the plugin cannot run at all, which only happens when
     *         player storage will not open
     */
    private boolean setUpCore() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Could not create the plugin data folder.");
            return false;
        }

        configs = new ConfigManager(this);
        configs.loadAll();

        messages = new Messages(configs.lang());
        season = new Season(configs.main(), getLogger());
        Keys.init(this);

        hooks = new Hooks(this);
        hooks.detectAll();

        ProfileStore store = openStore();
        if (store == null) {
            return false;
        }
        profiles = new ProfileManager(this, store);
        profiles.start(configs.main().getInt("storage.auto-save-seconds", 300));

        economy = new EconomyService(this);
        progress = new ProgressService(this);
        rewards = new RewardService(this);
        menus = new MenuManager(this);
        commands = new CommandRouter(this);

        PluginCommand root = getCommand("christmas");
        if (root == null) {
            getLogger().severe("The 'christmas' command is missing from plugin.yml; "
                    + "no commands will work.");
        } else {
            root.setExecutor(commands);
            root.setTabCompleter(commands);
        }

        unpackMusic();
        try {
            fr.elias.ultimateChristmas.core.model.BundledModelAssets.install(
                    getDataFolder().toPath(), this::getResource);
            fr.elias.ultimateChristmas.core.config.BossModelMigration.migrate(
                    getDataFolder().toPath().resolve("modules/bosses.yml"),
                    getResource("modules/bosses.yml"));
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Could not install matching boss models and configuration", ex);
            return false;
        }

        // Our own custom-model provider: an animation engine over display
        // entities, driven once a tick for every attached model.
        rigs = new fr.elias.ultimateChristmas.core.model.RigRegistry(this);
        rigs.reload();
        modelRenderer = new fr.elias.ultimateChristmas.core.model.DisplayModelRenderer(this, rigs);
        modelTask = getServer().getScheduler().runTaskTimer(this, modelRenderer::tickAll, 1L, 1L);

        // Optionally push the model resource pack to players on join.
        var packSender = fr.elias.ultimateChristmas.core.model.ResourcePackSender.createIfConfigured(
                this, configs.main().getString("resource-pack.url", ""),
                configs.main().getBoolean("resource-pack.required", false),
                new File(getDataFolder(), "pack/UltimateChristmas-Models.zip"));
        if (packSender != null) {
            getServer().getPluginManager().registerEvents(packSender, this);
            getLogger().info("Sending the model resource pack to players on join.");
        }
        return true;
    }

    /**
     * Opens player storage, falling back from SQLite to YAML.
     *
     * @return the open store, or {@code null} if neither backend would open
     */
    private ProfileStore openStore() {
        String type = configs.main().getString("storage.type", "yaml")
                .trim().toLowerCase(Locale.ROOT);
        ProfileStore store = switch (type) {
            case "sqlite", "sql", "db" -> new SqliteProfileStore(this);
            case "yaml", "yml", "file" -> new YamlProfileStore(this);
            default -> {
                getLogger().warning("Unknown storage.type '" + type
                        + "'; using yaml. Valid values: yaml, sqlite.");
                yield new YamlProfileStore(this);
            }
        };

        try {
            store.open();
            return store;
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Could not open " + type + " player storage", ex);
        }
        if (store instanceof YamlProfileStore) {
            return null;
        }
        getLogger().severe("Falling back to YAML storage for this session.");
        ProfileStore fallback = new YamlProfileStore(this);
        try {
            fallback.open();
            return fallback;
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "YAML storage failed too", ex);
            return null;
        }
    }

    /**
     * Copies the bundled {@code .nbs} tracks into the data folder on first run.
     *
     * <p>NoteBlockAPI plays from real files, so the soundtrack has to exist on
     * disk. Existing files are never overwritten, which leaves an owner's own
     * tracks and their edits alone.
     */
    private void unpackMusic() {
        File folder = new File(getDataFolder(), "music");
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().warning("Could not create the music folder.");
            return;
        }
        int copied = 0;
        for (String track : BUNDLED_TRACKS) {
            if (new File(folder, track).exists()) {
                continue;
            }
            try {
                saveResource("music/" + track, false);
                copied++;
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Bundled track music/" + track + " is missing from the jar.");
            }
        }
        if (copied > 0) {
            getLogger().info("Unpacked " + copied + " bundled carol(s) into music/.");
        }
    }

    /** Bundled soundtrack, generated by {@code tools/generate_music.py}. */
    private static final String[] BUNDLED_TRACKS = {
            "angels_we_have_heard_on_high.nbs", "away_in_a_manger.nbs",
            "carol_of_the_bells.nbs", "coventry_carol.nbs", "deck_the_halls.nbs",
            "ding_dong_merrily_on_high.nbs", "god_rest_ye_merry_gentlemen.nbs",
            "good_king_wenceslas.nbs", "hark_the_herald_angels_sing.nbs",
            "i_saw_three_ships.nbs", "jingle_bells.nbs",
            "jolly_old_saint_nicholas.nbs", "joy_to_the_world.nbs",
            "o_christmas_tree.nbs", "o_come_all_ye_faithful.nbs",
            "silent_night.nbs", "the_first_noel.nbs", "uc_fanfare.nbs",
            "uc_sleigh_bells.nbs", "uc_winter_siege.nbs", "uc_workshop_theme.nbs",
            "up_on_the_housetop.nbs", "we_wish_you_a_merry_christmas.nbs"
    };

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.closeAll();
        }
        if (modelTask != null) {
            modelTask.cancel();
        }
        if (modelRenderer != null) {
            // Detaches every model and reveals its entity, so no display is
            // orphaned and no boss is left permanently invisible.
            modelRenderer.clear();
        }
        if (modulith != null) {
            // Stops every running module in reverse start order, closing each
            // module's lifecycle scope and with it its listeners and tasks.
            modulith.close();
        }
        if (profiles != null) {
            profiles.shutdown();
        }
        getLogger().info("UltimateChristmas disabled. See you next season.");
    }

    /**
     * Reloads the global configs and every running module's own config.
     *
     * <p>A config-only refresh. To replace a module's listeners and tasks, use
     * {@code /christmas module restart <id>} for that one module.
     */
    public void reloadEverything() {
        menus.closeAll();
        configs.reloadAll();
        messages.reload();
        season.reload();
        rigs.reload();

        ModuleRuntime runtime = modulith.runtime();
        runtime.states().forEach((id, state) -> {
            if (state == ModuleState.RUNNING) {
                try {
                    runtime.reload(id);
                } catch (Exception ex) {
                    getLogger().log(Level.WARNING, "Module '" + id + "' failed to reload", ex);
                }
            }
        });
    }

    // ------------------------------------------------------------- accessors

    public ConfigManager configs() {
        return configs;
    }

    public Messages messages() {
        return messages;
    }

    public Season season() {
        return season;
    }

    public Hooks hooks() {
        return hooks;
    }

    public ProfileManager profiles() {
        return profiles;
    }

    public EconomyService economy() {
        return economy;
    }

    public ProgressService progress() {
        return progress;
    }

    public RewardService rewards() {
        return rewards;
    }

    public MenuManager menus() {
        return menus;
    }

    /** The plugin's own animated-model renderer (display-entity provider). */
    public fr.elias.ultimateChristmas.core.model.DisplayModelRenderer modelRenderer() {
        return modelRenderer;
    }

    public fr.elias.ultimateChristmas.core.model.RigRegistry rigs() {
        return rigs;
    }

    public CommandRouter commands() {
        return commands;
    }

    /** The Modulith runtime, for per-module lifecycle and diagnostics. */
    public ModuleRuntime runtime() {
        return modulith == null ? null : modulith.runtime();
    }
}
