package fr.elias.ultimateChristmas.modules.admin.infra;

import dev.oreo.modulith.core.ModuleState;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /christmas module <list|enable|disable|restart|reload> [id]}.
 *
 * <p>Turns one feature on or off without touching the rest of the plugin. The
 * whole point of the module runtime: in 3.x every feature was wired into one
 * {@code onEnable}, so the only way to switch a broken feature off was to edit
 * a config and restart the server.
 *
 * <p>The listing is clickable, so an owner can see the state of everything and
 * act on it without typing module ids.
 */
public final class ModuleCommand implements SubCommand {

    private final ChristmasPlatform platform;
    private final ModuleControl control;

    public ModuleCommand(ChristmasPlatform platform, ModuleControl control) {
        this.platform = platform;
        this.control = control;
    }

    @Override
    public String name() {
        return "module";
    }

    @Override
    public List<String> aliases() {
        return List.of("modules");
    }

    @Override
    public String permission() {
        return "ultimatechristmas.admin.modules";
    }

    @Override
    public String usage() {
        return "<list|enable|disable|restart|reload> [id]";
    }

    @Override
    public String description() {
        return "Turn one feature module on, off, or reload it";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            list(sender);
            return;
        }

        if (args.length < 2) {
            throw new CommandError("Usage: /christmas module " + usage());
        }
        String id = args[1].toLowerCase(Locale.ROOT);

        ModuleControl.Result result = switch (action) {
            case "enable", "start", "on" -> control.enable(id);
            case "disable", "stop", "off" -> control.disable(id);
            case "restart" -> control.restart(id);
            case "reload" -> control.reload(id);
            default -> throw new CommandError("Unknown action '" + action
                    + "'. Use list, enable, disable, restart or reload.");
        };

        sender.sendMessage(Component.text()
                .append(platform.messages().prefix())
                .append(Component.text(result.message(),
                        result.ok() ? NamedTextColor.GREEN : NamedTextColor.RED))
                .build());
    }

    private void list(CommandSender sender) {
        Map<String, ModuleState> states = control.states();
        List<String> disabled = control.persistentlyDisabled();

        sender.sendMessage(Component.text()
                .append(platform.messages().prefix())
                .append(Component.text("Modules (" + states.size() + ")", NamedTextColor.GOLD))
                .build());

        states.forEach((id, state) -> {
            Component line = Component.text()
                    .append(Component.text("  " + symbol(state) + " ", colour(state)))
                    .append(Component.text(id, NamedTextColor.WHITE))
                    .append(Component.text(" " + state.name().toLowerCase(Locale.ROOT),
                            NamedTextColor.DARK_GRAY))
                    .append(disabled.contains(id)
                            ? Component.text(" (off in config)", NamedTextColor.DARK_GRAY)
                            : Component.empty())
                    .append(actionLinks(id, state))
                    .build();
            sender.sendMessage(line);

            Throwable failure = control.failure(id);
            if (failure != null) {
                sender.sendMessage(Component.text("      " + describe(failure),
                        NamedTextColor.RED));
            }
        });
    }

    /** Clickable enable/disable/reload suggestions next to each module. */
    private Component actionLinks(String id, ModuleState state) {
        var builder = Component.text();
        if (state == ModuleState.RUNNING) {
            builder.append(link(" [reload]", "reload", id, NamedTextColor.AQUA))
                    .append(link(" [restart]", "restart", id, NamedTextColor.YELLOW))
                    .append(link(" [off]", "disable", id, NamedTextColor.RED));
        } else {
            builder.append(link(" [on]", "enable", id, NamedTextColor.GREEN));
        }
        return builder.build();
    }

    private Component link(String label, String action, String id, NamedTextColor colour) {
        String command = "/christmas module " + action + " " + id;
        return Component.text(label, colour)
                .hoverEvent(HoverEvent.showText(Component.text(command, NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private static String symbol(ModuleState state) {
        return switch (state) {
            case RUNNING -> "●";
            case FAILED -> "✖";
            case SKIPPED -> "○";
            default -> "○";
        };
    }

    private static NamedTextColor colour(ModuleState state) {
        return switch (state) {
            case RUNNING -> NamedTextColor.GREEN;
            case FAILED -> NamedTextColor.RED;
            case STARTING, STOPPING -> NamedTextColor.YELLOW;
            default -> NamedTextColor.GRAY;
        };
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("list", "enable", "disable", "restart", "reload");
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            Map<String, ModuleState> states = control.states();
            List<String> ids = new ArrayList<>();
            states.forEach((id, state) -> {
                boolean running = state == ModuleState.RUNNING;
                boolean wantRunning = !action.startsWith("en") && !action.equals("on")
                        && !action.equals("start");
                if (running == wantRunning) {
                    ids.add(id);
                }
            });
            return ids;
        }
        return List.of();
    }
}
