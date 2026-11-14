package fr.elias.ultimateChristmas.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Thrown by a subcommand to reject input.
 *
 * <p>{@link CommandRouter} shows the message to the sender and stops; it does
 * not log a stack trace, because bad input from a player is not a plugin fault.
 */
public final class CommandError extends RuntimeException {

    private final Component component;

    public CommandError(String message) {
        this(Component.text(message, NamedTextColor.RED));
    }

    public CommandError(Component component) {
        super(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(component), null, false, false);
        this.component = component;
    }

    public Component component() {
        return component;
    }
}
