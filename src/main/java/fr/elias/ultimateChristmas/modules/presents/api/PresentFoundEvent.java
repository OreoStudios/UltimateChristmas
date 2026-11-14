package fr.elias.ultimateChristmas.modules.presents.api;

import java.util.UUID;

/**
 * Published when a player opens a hidden present.
 *
 * @param player   who found it
 * @param rewardId the reward that was granted
 * @param world    where it was hidden
 */
public record PresentFoundEvent(UUID player, String rewardId, String world) {
}
