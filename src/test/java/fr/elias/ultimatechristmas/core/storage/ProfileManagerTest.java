package fr.elias.ultimateChristmas.core.storage;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProfileManagerTest {
    @Test
    void immediateLoadDoesNotRecursivelyUpdateTheInFlightMap() {
        var store = new MemoryStore();
        var manager = new ProfileManager(null, store, Runnable::run);
        UUID id = UUID.randomUUID();

        PlayerProfile profile = manager.loadAsync(id, "Bot_12").join();

        assertSame(profile, manager.cached(id));
        assertEquals("Bot_12", profile.name());
        assertTrue(profile.dirty());
        assertEquals(1, store.reads.get());
    }

    @Test
    void completedLoadIsRemovedWithoutOverwritingUnsavedProgress() {
        var store = new MemoryStore();
        var manager = new ProfileManager(null, store, Runnable::run);
        UUID id = UUID.randomUUID();
        PlayerProfile profile = manager.loadAsync(id, "Bot_12").join();
        profile.addStat(PlayerProfile.STAT_SHARDS, 42);

        assertSame(profile, manager.loadAsync(id, "Bot_12").join());
        assertEquals(2, store.reads.get(), "A completed in-flight entry must be removed");
        assertEquals(42, profile.shards(), "Reload must preserve newer cached progress");
    }

    @Test
    void overlappingRequestsShareOneReadAndCompletedProfilesStayCached() {
        var store = new MemoryStore();
        var executor = new QueuedExecutor();
        var manager = new ProfileManager(null, store, executor);
        UUID id = UUID.randomUUID();
        store.profile = PlayerProfile.blank(id, "Bot_56");
        store.profile.setStat(PlayerProfile.STAT_SHARDS, 17);
        store.profile.markClean();

        CompletableFuture<PlayerProfile> first = manager.loadAsync(id, "Bot_56");
        CompletableFuture<PlayerProfile> second = manager.loadAsync(id, "Bot_56");
        assertSame(first, second);
        assertFalse(first.isDone());
        assertEquals(1, executor.tasks.size());
        executor.runNext();

        assertSame(first.join(), second.join());
        assertEquals(17, first.join().shards());
        assertSame(first.join(), manager.loadAsync(id, "Bot_56").join());
        assertEquals(1, store.reads.get());
        assertTrue(executor.tasks.isEmpty());
    }

    @Test
    void rapidBotLoginsKeepSeparateProfilesWhenLoadsFinishImmediately() {
        var store = new MemoryStore();
        var manager = new ProfileManager(null, store, Runnable::run);
        for (int index = 0; index < 256; index++) {
            UUID id = new UUID(0, index);
            PlayerProfile profile = manager.loadAsync(id, "Bot_" + index).join();
            assertEquals(id, profile.id());
            assertEquals("Bot_" + index, profile.name());
        }
        assertEquals(256, store.reads.get());
        assertEquals(256, manager.loaded().size());
    }

    private static final class QueuedExecutor implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) { tasks.add(task); }
        void runNext() { tasks.remove().run(); }
    }

    private static final class MemoryStore implements ProfileStore {
        final AtomicInteger reads = new AtomicInteger();
        PlayerProfile profile;
        @Override public PlayerProfile load(UUID id) { reads.incrementAndGet(); return profile; }
        @Override public void open() { }
        @Override public void close() { }
        @Override public void save(PlayerProfile profile) { }
        @Override public void saveAll(Collection<PlayerProfile> profiles) { }
        @Override public List<Entry> top(String stat, int limit) { return List.of(); }
        @Override public Collection<UUID> knownPlayers() { return List.of(); }
    }
}
