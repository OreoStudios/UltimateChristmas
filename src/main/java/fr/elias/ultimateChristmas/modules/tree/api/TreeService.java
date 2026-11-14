package fr.elias.ultimateChristmas.modules.tree.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.Location;

/** The community Christmas tree, as other modules see it. */
@ModuleApi("tree")
public interface TreeService {

    /** Where the tree is, or {@code null} if no location is set. */
    Location location();

    /** Ornaments hung on the tree so far this season. */
    int ornamentCount();

    /** Ornaments needed to fully decorate it. */
    int goal();

    /** Whether the tree is fully decorated. */
    boolean complete();
}
