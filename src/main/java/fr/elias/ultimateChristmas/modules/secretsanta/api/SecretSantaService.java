package fr.elias.ultimateChristmas.modules.secretsanta.api;

import dev.oreo.modulith.core.ModuleApi;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** The Secret Santa exchange, as other modules see it. */
@ModuleApi("secretsanta")
public interface SecretSantaService {

    /** Whether pairings have been drawn. */
    boolean drawn();

    /** How many players have joined this year's exchange. */
    int participantCount();

    /** The player this one is buying for, or {@code null} if unassigned. */
    OfflinePlayer targetOf(Player player);
}
