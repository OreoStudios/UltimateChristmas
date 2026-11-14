package fr.elias.ultimateChristmas.modules.pass.api;

import java.util.UUID;

/**
 * Published when a player reaches a new festive pass tier.
 *
 * @param player who reached it
 * @param tier   the tier number now unlocked
 */
public record PassTierUnlockedEvent(UUID player, int tier) {
}
