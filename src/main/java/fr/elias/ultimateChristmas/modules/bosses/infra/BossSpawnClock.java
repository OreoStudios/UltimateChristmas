package fr.elias.ultimateChristmas.modules.bosses.infra;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Wall-clock deadlines for automatic boss appearances.
 *
 * Claiming a due slot advances its deadline atomically before scheduling the
 * world operation, preventing overlapping timer ticks from double-spawning.
 */
public final class BossSpawnClock {
    private final Map<String, Long> nextSpawn = new HashMap<>();

    public synchronized void initialize(String bossId, long now, int intervalSeconds) {
        nextSpawn.putIfAbsent(bossId, now + intervalSeconds * 1_000L);
    }

    public synchronized boolean claim(String bossId, long now, int intervalSeconds) {
        Long due = nextSpawn.get(bossId);
        if (due == null || now < due) return false;
        nextSpawn.put(bossId, now + intervalSeconds * 1_000L);
        return true;
    }

    public synchronized void afterDeath(String bossId, long now, int respawnSeconds) {
        nextSpawn.put(bossId, now + respawnSeconds * 1_000L);
    }

    public synchronized void retain(Set<String> enabledIds) {
        nextSpawn.keySet().retainAll(enabledIds);
    }

    public synchronized void clear() {
        nextSpawn.clear();
    }

    public synchronized Long nextSpawnAt(String bossId) {
        return nextSpawn.get(bossId);
    }
}
