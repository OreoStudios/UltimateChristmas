package fr.elias.ultimateChristmas.modules.tree.api;

/**
 * Published when the community tree becomes fully decorated.
 *
 * @param total ornaments on the tree when it completed
 */
public record TreeDecoratedEvent(int total) {
}
