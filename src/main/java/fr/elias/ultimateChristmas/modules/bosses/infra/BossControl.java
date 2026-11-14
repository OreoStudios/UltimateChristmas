package fr.elias.ultimateChristmas.modules.bosses.infra;

import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.Location;

import java.util.Collection;

/**
 * The slice of the boss module the control-center menu drives.
 *
 * <p>A narrow interface so the menu (infra) does not reach into the module
 * (lifecycle) for more than it needs: it can read the configured types, see the
 * live fight for a boss, and spawn or despawn one.
 */
public interface BossControl {

    /** Every configured boss type. */
    Collection<BossType> types();

    /** The live fight for a boss id, or {@code null} if it is not active. */
    BossFight activeFight(String bossId);

    /** Spawns a boss at a location. Returns true if it appeared. */
    boolean spawn(String bossId, Location where);

    /** Despawns every live instance of a boss id. Returns how many were removed. */
    int despawn(String bossId);

    /** Shared services, for messages and menu navigation. */
    ChristmasPlatform platform();

    /** Whether a model plugin is installed, so the menu can say so. */
    boolean modelPluginAvailable();
}
