package fr.elias.ultimateChristmas.modules.santa.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.Location;

/** The Santa NPC, as other modules see it. */
@ModuleApi("santa")
public interface SantaService {

    /** Whether Santa is currently spawned. */
    boolean present();

    /** Where Santa is, or {@code null} if he is not around. */
    Location location();

    /** Spawns Santa, returning where, or {@code null} if he could not appear. */
    Location summon();

    /** Sends Santa away. */
    void dismiss();
}
