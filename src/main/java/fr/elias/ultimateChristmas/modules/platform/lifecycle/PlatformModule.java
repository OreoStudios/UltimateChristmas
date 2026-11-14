package fr.elias.ultimateChristmas.modules.platform.lifecycle;

import dev.oreo.modulith.core.MinecraftModule;
import dev.oreo.modulith.core.ModuleContext;
import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.platform.infra.PlatformServices;

/**
 * Publishes the shared platform services.
 *
 * <p>Every feature module declares {@code dependencies = "platform::platform"},
 * so Modulith starts this first and refuses to start anything else if it fails.
 * It is deliberately not reloadable or stoppable in isolation: the services it
 * publishes are the plugin's own, and taking them away would strand every other
 * module. Modulith enforces that for us, since it rejects stopping a provider
 * that running modules still require.
 */
@PluginModule("platform")
public final class PlatformModule implements MinecraftModule {

    private final PlatformServices services;

    public PlatformModule(PlatformServices services) {
        this.services = services;
    }

    @Override
    public void enable(ModuleContext context) {
        context.services().publish(ChristmasPlatform.class, services);
        context.logger().info("Platform services published to modules.");
    }
}
