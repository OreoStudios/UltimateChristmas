package fr.elias.ultimateChristmas.modules.advent.api;

import java.util.UUID;

/**
 * Published when a player opens an advent door.
 *
 * @param player   who opened it
 * @param day      the season day, 1-based
 * @param rewardId the reward key that was granted
 * @param streak   their claim streak after this door
 * @param catchUp  true if this was a paid catch-up on a missed day
 */
public record AdventClaimedEvent(UUID player, int day, String rewardId,
                                 int streak, boolean catchUp) {
}
