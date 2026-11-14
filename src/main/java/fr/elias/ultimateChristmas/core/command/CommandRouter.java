package fr.elias.ultimateChristmas.core.command;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.core.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Dispatches {@code /christmas <sub> ...} to the subcommands modules register.
 *
 * <p>Handles permissions, player-only checks, paged help and tab completion once
 * so no module has to repeat it, and reports an unexpected exception to the
 * sender instead of leaving them staring at a silent console error.
 */
public final class CommandRouter implements CommandExecutor, TabCompleter {

    private static final int HELP_PER_PAGE = 8;

    private final UltimateChristmas plugin;
    private final Map<String, SubCommand> byName = new LinkedHashMap<>();

    public CommandRouter(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    public void register(SubCommand command) {
        byName.put(command.name().toLowerCase(Locale.ROOT), command);
        for (String alias : command.aliases()) {
            byName.putIfAbsent(alias.toLowerCase(Locale.ROOT), command);
        }
    }

    public void unregister(SubCommand command) {
        byName.values().removeIf(existing -> existing == command);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Messages messages = plugin.messages();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label, args.length > 1 ? parsePage(args[1]) : 1);
            return true;
        }

        SubCommand target = byName.get(args[0].toLowerCase(Locale.ROOT));
        if (target == null) {
            messages.send(sender, "command.unknown", Map.of("input", args[0]));
            suggest(sender, label, args[0]);
            return true;
        }
        if (target.permission() != null && !sender.hasPermission(target.permission())) {
            messages.send(sender, "command.no-permission",
                    Map.of("permission", target.permission()));
            return true;
        }
        if (target.playerOnly() && !(sender instanceof Player)) {
            messages.send(sender, "command.player-only");
            return true;
        }

        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        try {
            target.execute(sender, rest);
        } catch (CommandError expected) {
            // Thrown deliberately by a subcommand to report bad input.
            sender.sendMessage(expected.component());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "/" + label + " " + target.name() + " failed for " + sender.getName(), ex);
            messages.send(sender, "command.error");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0] : "";
            List<String> names = new ArrayList<>();
            for (SubCommand sub : visibleTo(sender)) {
                names.add(sub.name());
            }
            names.add("help");
            return StringUtil.copyPartialMatches(prefix, names, new ArrayList<>());
        }

        SubCommand target = byName.get(args[0].toLowerCase(Locale.ROOT));
        if (target == null
                || (target.permission() != null && !sender.hasPermission(target.permission()))) {
            return List.of();
        }
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        List<String> options = target.complete(sender, rest);
        return StringUtil.copyPartialMatches(rest[rest.length - 1], options, new ArrayList<>());
    }

    // ------------------------------------------------------------------ help

    private void sendHelp(CommandSender sender, String label, int page) {
        List<SubCommand> available = visibleTo(sender);
        available.sort(Comparator.comparing(SubCommand::name));
        if (available.isEmpty()) {
            plugin.messages().send(sender, "command.no-commands");
            return;
        }

        int pages = Math.max(1, (available.size() + HELP_PER_PAGE - 1) / HELP_PER_PAGE);
        int shown = Math.min(Math.max(1, page), pages);
        int from = (shown - 1) * HELP_PER_PAGE;
        int to = Math.min(available.size(), from + HELP_PER_PAGE);

        plugin.messages().send(sender, "command.help-header",
                Map.of("page", String.valueOf(shown), "pages", String.valueOf(pages)));
        for (SubCommand sub : available.subList(from, to)) {
            String line = "/" + label + " " + sub.name()
                    + (sub.usage().isEmpty() ? "" : " " + sub.usage());
            sender.sendMessage(Component.text()
                    .append(Component.text("  " + line, NamedTextColor.GOLD))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(sub.description(), NamedTextColor.GRAY))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Click to put this in your chat box", NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.suggestCommand(
                            "/" + label + " " + sub.name() + " "))
                    .build());
        }
        if (pages > 1 && shown < pages) {
            plugin.messages().send(sender, "command.help-more",
                    Map.of("label", label, "page", String.valueOf(shown + 1)));
        }
    }

    /** Offers the closest matching subcommand after a typo. */
    private void suggest(CommandSender sender, String label, String input) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (SubCommand sub : visibleTo(sender)) {
            int distance = editDistance(input.toLowerCase(Locale.ROOT), sub.name());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = sub.name();
            }
        }
        // Only suggest a genuinely close match; past a third of the word it is noise.
        if (best != null && bestDistance <= Math.max(1, input.length() / 3 + 1)) {
            plugin.messages().send(sender, "command.did-you-mean",
                    Map.of("label", label, "suggestion", best));
        }
    }

    private List<SubCommand> visibleTo(CommandSender sender) {
        List<SubCommand> out = new ArrayList<>();
        for (SubCommand sub : new ArrayList<>(byName.values())) {
            if (out.contains(sub) || sub.hidden()) {
                continue;
            }
            if (sub.permission() == null || sender.hasPermission(sub.permission())) {
                out.add(sub);
            }
        }
        return out;
    }

    private static int parsePage(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    /** Levenshtein distance, used only to suggest a correction for a typo. */
    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
