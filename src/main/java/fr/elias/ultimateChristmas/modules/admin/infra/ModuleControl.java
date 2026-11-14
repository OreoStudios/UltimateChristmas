package fr.elias.ultimateChristmas.modules.admin.infra;

import dev.oreo.modulith.core.ModuleDependency;
import dev.oreo.modulith.core.ModuleRuntime;
import dev.oreo.modulith.core.ModuleState;
import fr.elias.ultimateChristmas.core.config.ConfigFile;
import fr.elias.ultimateChristmas.core.config.ConfigManager;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Enables, disables, restarts and reloads one module at a time.
 *
 * <p>Wraps the Modulith runtime with the two things it deliberately leaves to
 * the host plugin:
 *
 * <ul>
 *   <li><b>Persistence.</b> Modulith's {@code stop(id)} lasts until the next
 *       server start by design. A disabled module is also recorded under
 *       {@code modules.disabled} in config.yml and re-stopped on the next boot,
 *       so an owner who turns a feature off gets it to stay off.</li>
 *   <li><b>Readable errors.</b> The runtime refuses to stop a module that
 *       another running module requires. That refusal is turned into a message
 *       naming the modules in the way, rather than a raw exception.</li>
 * </ul>
 */
public final class ModuleControl {

    /** What a lifecycle operation did. */
    public record Result(boolean ok, String message) {

        static Result failed(String message) {
            return new Result(false, message);
        }

        static Result done(String message) {
            return new Result(true, message);
        }
    }

    private static final String DISABLED_PATH = "modules.disabled";

    private final ChristmasPlatform platform;
    /**
     * Resolved on each use, not captured.
     *
     * <p>The admin module is enabled <em>by</em> the runtime, so the plugin has
     * not been handed its {@code ModuleRuntime} reference yet at that point.
     */
    private final Supplier<ModuleRuntime> runtimeSupplier;

    public ModuleControl(ChristmasPlatform platform, Supplier<ModuleRuntime> runtimeSupplier) {
        this.platform = platform;
        this.runtimeSupplier = runtimeSupplier;
    }

    private ModuleRuntime runtime() {
        ModuleRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            throw new IllegalStateException(
                    "The module runtime is not available yet; startup is still in progress.");
        }
        return runtime;
    }

    /** Module ids, with their current runtime state, sorted for display. */
    public Map<String, ModuleState> states() {
        return new TreeMap<>(runtime().states());
    }

    public List<String> moduleIds() {
        return new ArrayList<>(states().keySet());
    }

    public boolean known(String id) {
        return runtime().states().containsKey(id);
    }

    /** The recorded startup failure for a module, or {@code null}. */
    public Throwable failure(String id) {
        return runtime().failures().get(id);
    }

    /** Ids an owner has switched off, which stay off across restarts. */
    public List<String> persistentlyDisabled() {
        return platform.configs().main().getStringList(DISABLED_PATH);
    }

    // ------------------------------------------------------------ operations

    public Result enable(String id) {
        if (!known(id)) {
            return Result.failed("There is no module called '" + id + "'.");
        }
        if (runtime().states().get(id) == ModuleState.RUNNING) {
            return Result.failed("Module '" + id + "' is already running.");
        }
        try {
            runtime().start(id);
        } catch (Exception ex) {
            return Result.failed("Could not enable '" + id + "': " + reason(ex));
        }
        setPersistentlyDisabled(id, false);
        return Result.done("Enabled module '" + id + "'.");
    }

    public Result disable(String id) {
        if (!known(id)) {
            return Result.failed("There is no module called '" + id + "'.");
        }
        if (runtime().states().get(id) != ModuleState.RUNNING) {
            return Result.failed("Module '" + id + "' is not running.");
        }
        List<String> blockers = requiredBy(id);
        if (!blockers.isEmpty()) {
            return Result.failed("'" + id + "' is required by " + String.join(", ", blockers)
                    + ". Disable those first.");
        }
        try {
            runtime().stop(id);
        } catch (Exception ex) {
            return Result.failed("Could not disable '" + id + "': " + reason(ex));
        }
        setPersistentlyDisabled(id, true);
        return Result.done("Disabled module '" + id + "'.");
    }

    public Result restart(String id) {
        if (!known(id)) {
            return Result.failed("There is no module called '" + id + "'.");
        }
        try {
            runtime().restart(id);
        } catch (Exception ex) {
            // A failed restart leaves the module stopped; say so plainly,
            // because the previous instance is not coming back on its own.
            return Result.failed("Restart of '" + id + "' failed and it is now stopped: "
                    + reason(ex) + ". Fix the problem, then enable it again.");
        }
        setPersistentlyDisabled(id, false);
        return Result.done("Restarted module '" + id + "'.");
    }

    public Result reload(String id) {
        if (!known(id)) {
            return Result.failed("There is no module called '" + id + "'.");
        }
        if (runtime().states().get(id) != ModuleState.RUNNING) {
            return Result.failed("Module '" + id + "' is not running, so there is "
                    + "nothing to reload. Enable it instead.");
        }
        try {
            runtime().reload(id);
        } catch (Exception ex) {
            // Modulith leaves the module RUNNING on a reload failure, possibly
            // with configuration half applied, so recommend a restart.
            return Result.failed("Config reload for '" + id + "' failed: " + reason(ex)
                    + ". It is still running, but restart it to be sure.");
        }
        return Result.done("Reloaded the config for module '" + id + "'.");
    }

    /**
     * Re-applies the persisted disabled list after a full startup.
     *
     * <p>Called once, just after the runtime starts. Modules are stopped in the
     * reverse of their listed order so a provider is not stopped before a
     * dependent that is also being disabled.
     */
    public int applyPersistentlyDisabled() {
        List<String> disabled = persistentlyDisabled();
        if (disabled.isEmpty()) {
            return 0;
        }
        int stopped = 0;
        for (int index = disabled.size() - 1; index >= 0; index--) {
            String id = disabled.get(index);
            if (!known(id)) {
                platform.plugin().getLogger().warning("config.yml lists unknown module '"
                        + id + "' under " + DISABLED_PATH + ".");
                continue;
            }
            if (runtime().states().get(id) != ModuleState.RUNNING) {
                continue;
            }
            try {
                runtime().stop(id);
                stopped++;
            } catch (Exception ex) {
                platform.plugin().getLogger().warning("Module '" + id
                        + "' is disabled in config.yml but could not be stopped: " + reason(ex));
            }
        }
        if (stopped > 0) {
            platform.plugin().getLogger().info("Disabled " + stopped
                    + " module(s) from config.yml: " + disabled);
        }
        return stopped;
    }

    // -------------------------------------------------------------- internals

    /**
     * Running modules that declare a required dependency on {@code id}.
     *
     * <p>Checked before stopping so the player gets a useful message instead of
     * the runtime's refusal.
     */
    private List<String> requiredBy(String id) {
        List<String> blockers = new ArrayList<>();
        Map<String, ModuleState> states = runtime().states();
        for (var descriptor : runtime().modules()) {
            if (states.get(descriptor.id()) != ModuleState.RUNNING) {
                continue;
            }
            // Only 'dependencies' pin a provider's lifecycle; 'uses' and
            // 'after' grant access or ordering without requiring it to run.
            for (String selector : descriptor.dependencies()) {
                if (ModuleDependency.parse(selector).moduleId().equals(id)) {
                    blockers.add(descriptor.id());
                    break;
                }
            }
        }
        return blockers;
    }

    private void setPersistentlyDisabled(String id, boolean disabled) {
        ConfigFile config = platform.configs().get(ConfigManager.MAIN);
        List<String> current = new ArrayList<>(config.getStringList(DISABLED_PATH));
        boolean changed = disabled ? (!current.contains(id) && current.add(id))
                : current.remove(id);
        if (changed) {
            config.raw().set(DISABLED_PATH, current);
            config.save();
        }
    }

    private static String reason(Exception ex) {
        String message = ex.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        Throwable cause = ex.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return ex.getClass().getSimpleName();
    }
}
