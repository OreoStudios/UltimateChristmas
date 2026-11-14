package fr.elias.ultimateChristmas.modules.bosses.api;

import java.util.List;
import java.util.UUID;

/**
 * Published when a festive boss is defeated.
 *
 * @param bossId       which boss
 * @param contributors the players who dealt damage, highest first
 */
public record BossDefeatedEvent(String bossId, List<UUID> contributors) {
}
