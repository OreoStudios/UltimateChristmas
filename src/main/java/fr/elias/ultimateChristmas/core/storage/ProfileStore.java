package fr.elias.ultimateChristmas.core.storage;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Persistence behind {@link ProfileManager}.
 *
 * <p>Every method is called from an async worker thread, never from the main
 * thread, so implementations are free to block on disk or a database.
 */
public interface ProfileStore {

    /** Opens the backing file or database, creating it if needed. */
    void open() throws Exception;

    /** Flushes and releases resources. Called once on plugin disable. */
    void close();

    /** Loads one profile, or {@code null} if the player has no record yet. */
    PlayerProfile load(UUID id) throws Exception;

    /** Writes one profile. */
    void save(PlayerProfile profile) throws Exception;

    /** Writes a batch; implementations may use a single transaction. */
    void saveAll(Collection<PlayerProfile> profiles) throws Exception;

    /**
     * Top players by a stat, highest first, for leaderboards.
     *
     * @param stat  stat key, e.g. {@link PlayerProfile#STAT_SHARDS}
     * @param limit maximum rows to return
     */
    List<Entry> top(String stat, int limit) throws Exception;

    /** All known player ids. Used by admin tooling and season resets. */
    Collection<UUID> knownPlayers() throws Exception;

    /** One leaderboard row. */
    record Entry(UUID id, String name, long value) {
    }
}
