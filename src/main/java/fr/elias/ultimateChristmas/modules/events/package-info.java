/**
 * Timed Christmas event orchestration.
 *
 * <p>Events execute administrator-configured commands on window transitions.
 * The module owns its timer and command tree and has no knowledge of the
 * other modules' implementation details.
 */
@ApplicationModule(id = "events")
package fr.elias.ultimateChristmas.modules.events;

import dev.oreo.modulith.core.ApplicationModule;
