/**
 * The community Christmas tree.
 *
 * <p>The whole package hierarchy is one module boundary. Only the
 * {@code @ModuleApi} types under {@code api} are visible to other modules;
 * {@code infra} is private to this module and {@code lifecycle} holds the
 * {@code @PluginModule} class and its functor parameter record.
 */
@ApplicationModule(id = "tree")
package fr.elias.ultimateChristmas.modules.tree;

import dev.oreo.modulith.core.ApplicationModule;
