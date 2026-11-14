package fr.elias.ultimateChristmas.core.progress;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.core.storage.PlayerProfile;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * The single place a player's progress is recorded.
 *
 * <p>Modules call {@link #record} instead of touching stats directly, and
 * anything that cares — quests, the festive pass, leaderboards, achievements —
 * subscribes here. That is what keeps those features from needing to know about
 * each other: the snowball module does not import the quest module to make
 * "throw 50 snowballs" work, it just records a snowball hit.
 */
public final class ProgressService {

    /** Notified whenever a tracked stat moves. */
    public interface Listener {
        void onProgress(Player player, String stat, long delta, long total);
    }

    private final UltimateChristmas plugin;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public ProgressService(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    public void subscribe(Listener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Adds {@code delta} to a stat and tells every subscriber.
     *
     * @return the new total
     */
    public long record(Player player, String stat, long delta) {
        PlayerProfile profile = plugin.profiles().get(player);
        long total = profile.addStat(stat, delta);
        if (delta != 0) {
            fire(player, stat, delta, total);
        }
        return total;
    }

    public long record(Player player, String stat) {
        return record(player, stat, 1);
    }

    private void fire(Player player, String stat, long delta, long total) {
        for (Listener listener : listeners) {
            try {
                listener.onProgress(player, stat, delta, total);
            } catch (Exception ex) {
                // One misbehaving subscriber must not stop the others, nor
                // unwind the gameplay action that triggered this.
                plugin.getLogger().log(Level.WARNING,
                        "Progress listener failed for stat '" + stat + "'", ex);
            }
        }
    }
}
