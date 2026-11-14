package fr.elias.ultimateChristmas.core.storage;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * In-memory cache of {@link PlayerProfile}s, with all disk work off the main thread.
 *
 * <p>The 3.x {@code ShardManager} wrote the whole {@code playerdata.yml} to disk
 * synchronously on every single shard change — once per ore mined, per mob
 * killed, per purchase. On a busy server that is a main-thread file write
 * several times a second, and it grew with the player count. Here a mutation
 * only flips a dirty flag; a repeating async task batches the writes.
 *
 * <p>Profiles are loaded during {@link AsyncPlayerPreLoginEvent}, which already
 * runs off-thread, so by the time a player joins their data is in memory and
 * every feature can read it without blocking.
 */
public final class ProfileManager implements Listener {

    private final UltimateChristmas plugin;
    private final ProfileStore store;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    /** Loads in flight, so two events for one player share a single read. */
    private final Map<UUID, CompletableFuture<PlayerProfile>> loading = new ConcurrentHashMap<>();

    private BukkitTask autoSaveTask;

    public ProfileManager(UltimateChristmas plugin, ProfileStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void start(long autoSaveSeconds) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long ticks = Math.max(20L * 30, autoSaveSeconds * 20L);
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::flushDirty, ticks, ticks);

        // Players are already online after a /reload, so pull them in.
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadAsync(player.getUniqueId(), player.getName());
        }
    }

    /** Flushes everything and releases the store. Blocks, as disable must. */
    public void shutdown() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        List<PlayerProfile> pending = new ArrayList<>();
        for (PlayerProfile profile : cache.values()) {
            if (profile.dirty()) {
                pending.add(profile);
            }
        }
        if (!pending.isEmpty()) {
            try {
                store.saveAll(pending);
                pending.forEach(PlayerProfile::markClean);
                plugin.getLogger().info("Saved " + pending.size() + " player profile(s).");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save profiles on shutdown", ex);
            }
        }
        cache.clear();
        loading.clear();
        store.close();
    }

    // ----------------------------------------------------------------- access

    /**
     * The cached profile for an online player.
     *
     * <p>Never {@code null} for a player who has joined: if a load somehow has
     * not completed, a blank profile is created so a feature can keep working
     * and the player is not handed an exception. Progress made in that window is
     * saved normally.
     */
    public PlayerProfile get(Player player) {
        return get(player.getUniqueId(), player.getName());
    }

    public PlayerProfile get(UUID id, String name) {
        PlayerProfile profile = cache.get(id);
        if (profile == null) {
            profile = cache.computeIfAbsent(id, key -> {
                plugin.getLogger().fine("Creating a fresh profile for " + name
                        + "; its load had not finished.");
                return PlayerProfile.blank(key, name);
            });
            loadAsync(id, name);
        } else if (name != null) {
            profile.name(name);
        }
        return profile;
    }

    /** The cached profile, or {@code null} if this player is not loaded. */
    public PlayerProfile cached(UUID id) {
        return cache.get(id);
    }

    /**
     * Loads a profile that may belong to an offline player.
     *
     * <p>Completes on an async thread; callers that touch the world must hop
     * back to the main thread themselves.
     */
    public CompletableFuture<PlayerProfile> loadAsync(UUID id, String name) {
        PlayerProfile cached = cache.get(id);
        if (cached != null && !cached.dirty()) {
            return CompletableFuture.completedFuture(cached);
        }
        return loading.computeIfAbsent(id, key -> CompletableFuture.supplyAsync(() -> {
            try {
                PlayerProfile stored = store.load(key);
                final PlayerProfile loaded;
                if (stored == null) {
                    loaded = PlayerProfile.blank(key, name);
                    loaded.markDirty();
                } else {
                    loaded = stored;
                    if (name != null) {
                        loaded.name(name);
                    }
                }
                // An entry created while the read was in flight holds newer
                // state than the disk, so keep it and merge nothing.
                return cache.computeIfAbsent(key, ignored -> loaded);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not load profile " + key, ex);
                return cache.computeIfAbsent(key, ignored -> PlayerProfile.blank(key, name));
            }
        }).whenComplete((profile, error) -> loading.remove(id)));
    }

    public Collection<PlayerProfile> loaded() {
        return List.copyOf(cache.values());
    }

    // -------------------------------------------------------------- persistence

    /** Writes out every profile with pending changes. Runs async. */
    public void flushDirty() {
        List<PlayerProfile> pending = new ArrayList<>();
        for (PlayerProfile profile : cache.values()) {
            if (profile.dirty()) {
                profile.pruneCooldowns();
                pending.add(profile);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        // Clear the flag before writing: a change made during the write marks
        // the profile dirty again and is picked up next pass, which is better
        // than clearing afterwards and losing it.
        pending.forEach(PlayerProfile::markClean);
        try {
            store.saveAll(pending);
        } catch (Exception ex) {
            pending.forEach(PlayerProfile::markDirty);
            plugin.getLogger().log(Level.WARNING,
                    "Could not save " + pending.size() + " profile(s); will retry", ex);
        }
    }

    public CompletableFuture<List<ProfileStore.Entry>> top(String stat, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return store.top(stat, limit);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Leaderboard query failed for " + stat, ex);
                return List.<ProfileStore.Entry>of();
            }
        });
    }

    public CompletableFuture<Collection<UUID>> knownPlayers() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return store.knownPlayers();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Could not list known players", ex);
                return List.of();
            }
        });
    }

    // ------------------------------------------------------------------ events

    /**
     * Pre-login already runs off the main thread, so this is the cheapest point
     * to have a profile ready before the player actually spawns.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            loadAsync(event.getUniqueId(), event.getName()).join();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        get(event.getPlayer()).touch();
    }

    /** Saves and evicts on quit so an idle server holds nothing in memory. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        PlayerProfile profile = cache.remove(id);
        if (profile == null) {
            return;
        }
        profile.touch();
        profile.pruneCooldowns();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                store.save(profile);
                profile.markClean();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not save " + profile.name() + " on quit", ex);
            }
        });
    }
}
