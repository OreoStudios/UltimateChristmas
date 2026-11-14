package fr.elias.ultimateChristmas.modules.economy.api;

import java.util.UUID;

/**
 * Published whenever a player earns shards from a gameplay source.
 *
 * <p>Other modules subscribe to this rather than hooking block-break and
 * mob-death themselves: the quest module uses it for "earn N shards" goals and
 * the pass module converts it into progress, neither of which needs to know how
 * earning works.
 *
 * @param player  who earned them
 * @param amount  how many, always positive
 * @param source  a short label for where they came from, e.g. {@code block:IRON_ORE}
 * @param balance their balance after the award
 */
public record ShardEarnedEvent(UUID player, long amount, String source, long balance) {
}
