package fr.elias.ultimateChristmas.modules.admin.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

/**
 * Typed settings for the admin module, built by its functor.
 *
 * @param applyDisabledOnStart re-stop modules listed under {@code modules.disabled}
 *                             in config.yml once the runtime has started
 * @param exposeArchitecture   allow {@code /christmas architecture} to dump the
 *                             module graph, which is useful in development and
 *                             noise in production
 */
public record AdminParameters(
        boolean applyDisabledOnStart,
        boolean exposeArchitecture
) {

    public static final AdminParameters DEFAULTS = new AdminParameters(true, true);

    public static AdminParameters from(ModuleConfiguration config, AdminParameters defaults) {
        return new AdminParameters(
                config.getBoolean("apply-disabled-on-start", defaults.applyDisabledOnStart()),
                config.getBoolean("expose-architecture", defaults.exposeArchitecture()));
    }
}
