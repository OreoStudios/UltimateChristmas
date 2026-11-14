package fr.elias.ultimateChristmas.modules.leaderboard.api;

import dev.oreo.modulith.core.ModuleApi;
import fr.elias.ultimateChristmas.core.storage.ProfileStore;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Festive leaderboards, as other modules see it. */
@ModuleApi("leaderboards")
public interface LeaderboardService {

    /** The configured board ids. */
    Set<String> boards();

    /**
     * The top rows of a board.
     *
     * <p>Completes off the main thread, and may be served from a short-lived
     * cache so repeated calls do not re-query storage.
     */
    CompletableFuture<List<ProfileStore.Entry>> top(String board, int limit);
}
