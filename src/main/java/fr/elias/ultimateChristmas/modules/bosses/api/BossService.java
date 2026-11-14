package fr.elias.ultimateChristmas.modules.bosses.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.Location;

import java.util.Set;

/** Festive boss fights, as other modules see it. */
@ModuleApi("bosses")
public interface BossService {

    /** The configured boss ids. */
    Set<String> bossTypes();

    /** Whether any boss is currently active. */
    boolean anyActive();

    /** Whether a specific boss is active. */
    boolean isActive(String bossId);

    /** Spawns a boss at a location, returning true if it appeared. */
    boolean spawn(String bossId, Location where);
}
