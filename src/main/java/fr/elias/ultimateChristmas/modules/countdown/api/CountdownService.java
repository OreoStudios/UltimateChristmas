package fr.elias.ultimateChristmas.modules.countdown.api;

import dev.oreo.modulith.core.ModuleApi;

/** The countdown to Christmas, as other modules see it. */
@ModuleApi("countdown")
public interface CountdownService {

    /** Whole days until Christmas; 0 on the day, negative once it has passed. */
    long daysUntilChristmas();

    /** Whether today is Christmas Day. */
    boolean isChristmas();
}
