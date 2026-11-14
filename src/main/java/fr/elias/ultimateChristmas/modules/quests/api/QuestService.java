package fr.elias.ultimateChristmas.modules.quests.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.entity.Player;

import java.util.Set;

/** Festive objectives, as other modules see it. */
@ModuleApi("quests")
public interface QuestService {

    /** The objective ids currently active for this player. */
    Set<String> activeQuests(Player player);

    /** How far along a player is, or 0 if the objective is unknown. */
    long progressOf(Player player, String questId);

    /** How many objectives this player has finished in the current cycle. */
    int completedCount(Player player);

    /** Opens the objectives menu. */
    void openMenu(Player player);
}
