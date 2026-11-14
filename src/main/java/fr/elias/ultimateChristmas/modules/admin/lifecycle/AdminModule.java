package fr.elias.ultimateChristmas.modules.admin.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.modules.admin.infra.ArchitectureCommand;
import fr.elias.ultimateChristmas.modules.admin.infra.ModuleCommand;
import fr.elias.ultimateChristmas.modules.admin.infra.ModuleControl;
import fr.elias.ultimateChristmas.modules.admin.infra.ProfileCommand;
import fr.elias.ultimateChristmas.modules.admin.infra.ReloadCommand;
import fr.elias.ultimateChristmas.modules.admin.infra.SeasonCommand;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;

/**
 * Administration, diagnostics and per-module lifecycle control.
 *
 * <p>The one module that legitimately holds the plugin itself, because its job
 * is to operate on the plugin: reloading global config and starting, stopping
 * and reloading the other modules.
 */
@PluginModule(value = "admin", dependencies = "platform::platform", configuration = true)
public final class AdminModule extends FeatureModule {

    private final UltimateChristmas plugin;
    private volatile AdminParameters parameters;

    public AdminModule(ChristmasPlatform platform, AdminParameters parameters,
                       UltimateChristmas plugin) {
        super(platform);
        this.parameters = parameters;
        this.plugin = plugin;
    }

    @Override
    protected void onEnable() {
        // Resolved lazily: this module is started by the runtime, so the plugin
        // has not been handed its reference to it yet.
        ModuleControl control = new ModuleControl(platform, plugin::runtime);

        command(new ReloadCommand(platform, plugin));
        command(new ModuleCommand(platform, control));
        command(new SeasonCommand(platform));
        command(new ProfileCommand(platform));
        if (parameters.exposeArchitecture()) {
            command(new ArchitectureCommand(platform, plugin));
        }

        if (parameters.applyDisabledOnStart()) {
            // Deferred by a tick: during onEnable the other modules may not
            // have started yet, and there would be nothing to stop.
            later(control::applyPersistentlyDisabled, 1L);
        }
    }

    @Override
    protected void onReload() {
        parameters = AdminParameters.from(config(), AdminParameters.DEFAULTS);
    }
}
