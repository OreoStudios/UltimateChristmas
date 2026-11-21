package fr.elias.ultimateChristmas.modules.bosses.infra;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

/** Atomically throttles region-entry spawns independently per boss and region. */
public final class BossRegionCooldowns {
    private final Map<String, Long> nextAllowed = new HashMap<>();

    public synchronized boolean claim(String bossId, String regionId, long now, int cooldownSeconds) {
        String key = bossId.toLowerCase(Locale.ROOT) + ":" + regionId.toLowerCase(Locale.ROOT);
        if (now < nextAllowed.getOrDefault(key, 0L)) return false;
        nextAllowed.put(key, now + cooldownSeconds * 1_000L);
        return true;
    }

    public synchronized void clear() {
        nextAllowed.clear();
    }
}
