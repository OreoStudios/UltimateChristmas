package fr.elias.ultimateChristmas.modules.music.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.entity.Player;

import java.util.Set;

/** The carol jukebox, as other modules see it. */
@ModuleApi("music")
public interface MusicService {

    /** Whether note block playback is available at all. */
    boolean available();

    /** Configured track keys. */
    Set<String> tracks();

    /** Plays a track for one player. Returns false if the key is unknown. */
    boolean play(Player player, String trackKey);

    /**
     * Plays a named cue, e.g. {@code boss-spawn}, for one player.
     *
     * <p>Does nothing if the cue is unmapped, so callers need no guard.
     */
    void cue(Player player, String cueName);

    /** Stops whatever a player is hearing. */
    void stop(Player player);
}
