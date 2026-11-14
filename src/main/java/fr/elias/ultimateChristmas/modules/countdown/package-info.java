/**
 * The countdown to Christmas.
 *
 * <p>The whole package hierarchy is one module boundary. Only the
 * {@code @ModuleApi} types under {@code api} are visible to other modules;
 * {@code infra} is private to this module and {@code lifecycle} holds the
 * {@code @PluginModule} class and its functor parameter record.
 */
@ApplicationModule(id = "countdown")
package fr.elias.ultimateChristmas.modules.countdown;

import dev.oreo.modulith.core.ApplicationModule;
