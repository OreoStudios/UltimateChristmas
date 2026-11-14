package fr.elias.ultimateChristmas.modules.quests.api;

import java.util.UUID;

/**
 * Published when a player finishes a festive objective.
 *
 * @param player  who completed it
 * @param questId the objective's config key
 * @param scope   daily, weekly or season
 */
public record QuestCompletedEvent(UUID player, String questId, String scope) {
}
