package fr.elias.ultimateChristmas.modules.admin.infra;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * {@code /christmas architecture [markdown|json|mermaid|graphviz]} — writes the
 * module graph to a file.
 *
 * <p>Modulith can describe its own module graph, including which module depends
 * on which API. Dumping it is the quickest way to see whether a boundary has
 * been crossed by accident, and the Mermaid form pastes straight into a README.
 */
public final class ArchitectureCommand implements SubCommand {

    private final ChristmasPlatform platform;
    private final UltimateChristmas plugin;

    public ArchitectureCommand(ChristmasPlatform platform, UltimateChristmas plugin) {
        this.platform = platform;
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "architecture";
    }

    @Override
    public List<String> aliases() {
        return List.of("arch", "graph");
    }

    @Override
    public String permission() {
        return "ultimatechristmas.admin.architecture";
    }

    @Override
    public String usage() {
        return "[markdown|json|mermaid|graphviz]";
    }

    @Override
    public String description() {
        return "Export the module dependency graph to a file";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        var runtime = plugin.runtime();
        if (runtime == null) {
            throw new CommandError("The module runtime is not available.");
        }

        String format = (args.length == 0 ? "markdown" : args[0]).toLowerCase(Locale.ROOT);
        String content;
        String extension;
        switch (format) {
            case "markdown", "md" -> {
                content = runtime.architectureMarkdown();
                extension = "md";
            }
            case "json" -> {
                content = runtime.architectureJson();
                extension = "json";
            }
            case "mermaid" -> {
                content = runtime.graphMermaid();
                extension = "mmd";
            }
            case "graphviz", "dot" -> {
                content = runtime.graphGraphviz();
                extension = "dot";
            }
            default -> throw new CommandError("Unknown format '" + format
                    + "'. Use markdown, json, mermaid or graphviz.");
        }

        Path target = plugin.getDataFolder().toPath().resolve("architecture." + extension);
        try {
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new CommandError("Could not write " + target.getFileName()
                    + ": " + ex.getMessage());
        }

        sender.sendMessage(Component.text()
                .append(platform.messages().prefix())
                .append(Component.text("Wrote the module graph to ", NamedTextColor.GREEN))
                .append(Component.text(plugin.getDataFolder().getName() + "/"
                        + target.getFileName(), NamedTextColor.WHITE))
                .build());
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return args.length == 1
                ? List.of("markdown", "json", "mermaid", "graphviz")
                : List.of();
    }
}
