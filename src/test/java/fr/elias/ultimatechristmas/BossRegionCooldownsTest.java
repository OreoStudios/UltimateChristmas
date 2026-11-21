package fr.elias.ultimateChristmas;

import fr.elias.ultimateChristmas.modules.bosses.infra.BossRegionCooldowns;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BossRegionCooldownsTest {
    @Test
    void oneBossCannotTriggerRepeatedlyInsideSameRegionBeforeCooldown() {
        BossRegionCooldowns cooldowns = new BossRegionCooldowns();
        assertTrue(cooldowns.claim("Grinch", "Christmas_Arena", 1_000L, 60));
        assertFalse(cooldowns.claim("grinch", "christmas_arena", 60_999L, 60));
        assertTrue(cooldowns.claim("grinch", "christmas_arena", 61_000L, 60));
    }

    @Test
    void differentBossesAndRegionsKeepIndependentTimers() {
        BossRegionCooldowns cooldowns = new BossRegionCooldowns();
        assertTrue(cooldowns.claim("grinch", "arena_a", 100L, 60));
        assertTrue(cooldowns.claim("grinch", "arena_b", 100L, 60));
        assertTrue(cooldowns.claim("krampus", "arena_a", 100L, 60));
        assertFalse(cooldowns.claim("grinch", "arena_a", 101L, 60));
        cooldowns.clear();
        assertTrue(cooldowns.claim("grinch", "arena_a", 101L, 60));
    }
}
