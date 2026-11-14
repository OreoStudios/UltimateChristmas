package fr.elias.ultimateChristmas.modules.presents.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.Location;

/** The present hunt, as other modules see it. */
@ModuleApi("presents")
public interface PresentService {

    /** How many presents are hidden right now. */
    int activeCount();

    /** Hides one present at a random eligible spot. Returns where, or null. */
    Location spawnOne();

    /** Removes every hidden present. Returns how many were removed. */
    int clearAll();
}
