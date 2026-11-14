/**
 * Hats, trails and festive pets.
 *
 * <p>The whole package hierarchy is one module boundary. Only the
 * {@code @ModuleApi} types under {@code api} are visible to other modules;
 * {@code infra} is private to this module and {@code lifecycle} holds the
 * {@code @PluginModule} class and its functor parameter record.
 */
@ApplicationModule(id = "cosmetics")
package fr.elias.ultimateChristmas.modules.cosmetics;

import dev.oreo.modulith.core.ApplicationModule;
