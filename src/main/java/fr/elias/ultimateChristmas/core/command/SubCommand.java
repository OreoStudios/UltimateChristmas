package fr.elias.ultimateChristmas.core.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * One branch of {@code /christmas}.
 *
 * <p>Modules contribute these instead of claiming their own top-level command,
 * so the plugin needs a single entry in plugin.yml and subcommands appear and
 * disappear with the module that owns them.
 */
public interface SubCommand {

    /** Primary name, as typed after {@code /christmas}. */
    String name();

    /** Extra names that resolve to this command. */
    default List<String> aliases() {
        return List.of();
    }

    /** Permission required, or {@code null} for everyone. */
    String permission();

    /** Argument outline shown in help, without the leading command. */
    String usage();

    /** One-line summary shown in help. */
    String description();

    /** Whether this command only makes sense for an in-game player. */
    default boolean playerOnly() {
        return false;
    }

    /** Hide from the help listing (for aliases and debug tools). */
    default boolean hidden() {
        return false;
    }

    /**
     * Runs the command.
     *
     * @param args arguments after the subcommand name
     */
    void execute(CommandSender sender, String[] args);

    /** Completions for the argument currently being typed. */
    default List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }
}
