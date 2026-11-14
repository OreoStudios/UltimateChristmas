package fr.elias.ultimateChristmas.core.modulith;

import dev.oreo.modulith.core.MinecraftModule;
import dev.oreo.modulith.core.ModuleConfiguration;
import dev.oreo.modulith.core.ModuleContext;
import dev.oreo.modulith.paper.PaperPlatform;
import dev.oreo.modulith.paper.ScheduledTaskHandle;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.event.Listener;

import java.util.logging.Logger;

/**
 * Shared base for this plugin's feature modules.
 *
 * <p>Modulith already cleans up anything registered against the module's
 * {@link dev.oreo.modulith.core.LifecycleScope}; this class routes every
 * registration through that scope so a module can be stopped, restarted or
 * reloaded on its own without leaking a listener, a task or a subcommand.
 *
 * <p>Subclasses implement {@link #onEnable()} rather than
 * {@link #enable(ModuleContext)}, so the context, platform handle and config
 * are already in place by the time their code runs.
 */
public abstract class FeatureModule implements MinecraftModule {

    protected ChristmasPlatform platform;

    private ModuleContext context;
    private PaperPlatform paper;

    protected FeatureModule(ChristmasPlatform platform) {
        this.platform = platform;
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public final void enable(ModuleContext moduleContext) throws Exception {
        this.context = moduleContext;
        this.paper = moduleContext.platform(PaperPlatform.class);
        onEnable();
    }

    @Override
    public final void disable() throws Exception {
        onDisable();
        // Listeners, tasks and commands registered through the helpers below are
        // released by the lifecycle scope, which Modulith closes after this.
    }

    @Override
    public final void reload(ModuleContext moduleContext) throws Exception {
        this.context = moduleContext;
        onReload();
    }

    /** Register listeners, tasks, commands and read config here. */
    protected abstract void onEnable() throws Exception;

    /** Release anything the helpers below do not already track. */
    protected void onDisable() throws Exception {
    }

    /**
     * Re-read configuration into the running module.
     *
     * <p>This is a config-only refresh: listeners, tasks and commands are
     * untouched and stay registered. Override it in any module that caches
     * config values, which is most of them. A module that cannot sensibly
     * refresh in place should leave this alone and be restarted instead.
     */
    protected void onReload() throws Exception {
    }

    // ------------------------------------------------------- registration API

    /** Registers a Bukkit listener, unregistered when this module stops. */
    protected final <T extends Listener> T listen(T listener) {
        return paper.registerListener(context, listener);
    }

    /** Adds a subcommand to {@code /christmas}, removed when this module stops. */
    protected final void command(SubCommand command) {
        platform.commands().register(command);
        context.lifecycle().onClose(() -> platform.commands().unregister(command));
    }

    /**
     * A repeating task on the global scheduling context, cancelled when this
     * module stops.
     *
     * <p>On Folia the global context is not an entity or region thread, so a
     * task that touches players or blocks must use {@link #entityTask} or hop
     * through {@link #paper()}'s region scheduler instead.
     */
    protected final ScheduledTaskHandle timer(Runnable work, long delayTicks, long periodTicks) {
        return paper.scheduleTimer(context, work, delayTicks, periodTicks);
    }

    protected final ScheduledTaskHandle later(Runnable work, long delayTicks) {
        return paper.scheduleLater(context, work, delayTicks);
    }

    /** Runs work owned by one entity, which is Folia-correct for mob logic. */
    protected final ScheduledTaskHandle entityTask(org.bukkit.entity.Entity entity, Runnable work) {
        return paper.contexts().entity(context, entity, work);
    }

    /** Subscribes to Modulith domain events, unsubscribed when this module stops. */
    protected final void subscribe(Object listener) {
        context.listen(listener);
    }

    /** Publishes this module's API so other modules can resolve it. */
    protected final <T> void publish(Class<T> contract, T implementation) {
        context.services().publish(contract, implementation);
    }

    /** Resolves another module's published API. Requires a declared dependency. */
    protected final <T> T service(Class<T> contract) {
        return context.services().require(contract);
    }

    /** Publishes a domain event on the Modulith event bus. */
    protected final void emit(Object event) {
        context.events().publish(event);
    }

    // ------------------------------------------------------------- accessors

    protected final ModuleContext context() {
        return context;
    }

    protected final PaperPlatform paper() {
        return paper;
    }

    protected final Logger log() {
        return context.logger();
    }

    /** This module's own {@code modules/<id>.yml}. */
    protected final ModuleConfiguration config() {
        return context.config();
    }

    /**
     * A section of this module's config.
     *
     * <p>Returns an empty section rather than {@code null} when the path is
     * absent, so callers can read a missing section without a guard and still
     * get their documented defaults.
     */
    protected final ConfigurationSection section(String path) {
        Object value = context.config().get(path);
        if (value instanceof ConfigurationSection found) {
            return found;
        }
        return new MemoryConfiguration();
    }

    /** True if the config has a real section at {@code path}. */
    protected final boolean hasSection(String path) {
        return context.config().get(path) instanceof ConfigurationSection;
    }
}
