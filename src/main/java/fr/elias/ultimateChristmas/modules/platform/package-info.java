/**
 * Shared infrastructure published to every other module.
 *
 * <p>The whole package hierarchy is one module boundary; only the
 * {@code @ModuleApi} types under {@code api} are visible to other modules.
 */
@ApplicationModule(id = "platform")
package fr.elias.ultimateChristmas.modules.platform;

import dev.oreo.modulith.core.ApplicationModule;
