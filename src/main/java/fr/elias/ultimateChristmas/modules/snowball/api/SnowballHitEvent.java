package fr.elias.ultimateChristmas.modules.snowball.api;

import java.util.UUID;

/**
 * Published when a thrown snowball hits something the module cares about.
 *
 * @param thrower who threw it
 * @param victim  the player hit, or {@code null} if it hit a mob
 * @param kind    the trick-snowball id, or {@code "plain"}
 */
public record SnowballHitEvent(UUID thrower, UUID victim, String kind) {
}
