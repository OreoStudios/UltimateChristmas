package fr.elias.ultimateChristmas;

import fr.elias.ultimateChristmas.modules.bosses.infra.BossSpawnClock;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BossSpawnClockTest {
    @Test
    void doesNotSpawnBeforeDeadlineOrTwiceAtSameDeadline() {
        BossSpawnClock clock = new BossSpawnClock();
        clock.initialize("grinch", 1_000L, 60);
        assertFalse(clock.claim("grinch", 60_999L, 60));
        assertTrue(clock.claim("grinch", 61_000L, 60));
        assertFalse(clock.claim("grinch", 61_000L, 60));
        assertTrue(clock.claim("grinch", 121_000L, 60));
    }

    @Test
    void deathUsesDedicatedRespawnCooldown() {
        BossSpawnClock clock = new BossSpawnClock();
        clock.initialize("krampus", 1_000L, 3_600);
        clock.afterDeath("krampus", 15_000L, 120);
        assertFalse(clock.claim("krampus", 134_999L, 3_600));
        assertTrue(clock.claim("krampus", 135_000L, 3_600));
    }

    @Test
    void reloadCanRetainEnabledBossesAndRemoveDisabledOnes() {
        BossSpawnClock clock = new BossSpawnClock();
        clock.initialize("grinch", 0L, 60);
        clock.initialize("krampus", 0L, 60);
        clock.retain(Set.of("grinch"));
        assertNull(clock.nextSpawnAt("krampus"));
        assertEquals(60_000L, clock.nextSpawnAt("grinch"));
        clock.clear();
        assertNull(clock.nextSpawnAt("grinch"));
    }
}
