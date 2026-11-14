package fr.elias.ultimateChristmas.modules.advent.infra;

import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.advent.lifecycle.AdventParameters;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.function.Supplier;

/**
 * Nudges players who have not opened today's door.
 *
 * <p>Sent a couple of seconds after join, so it lands after the server's own
 * welcome messages rather than being buried by them. The nudge is a clickable
 * line, because telling someone to type a command is worse than letting them
 * click it.
 */
public final class AdventReminderListener implements Listener {

    private static final long JOIN_DELAY_TICKS = 60L;

    private final ChristmasPlatform platform;
    private final AdventCalendar calendar;
    private final Supplier<AdventParameters> parameters;

    public AdventReminderListener(ChristmasPlatform platform, AdventCalendar calendar,
                                  Supplier<AdventParameters> parameters) {
        this.platform = platform;
        this.calendar = calendar;
        this.parameters = parameters;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int today = platform.season().currentDay();
        if (today < 0 || !player.hasPermission("ultimatechristmas.advent")) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(platform.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            if (calendar.check(player, today) != AdventCalendar.Outcome.CLAIMED) {
                return;
            }
            String raw = platform.messages().raw("advent.reminder");
            if (raw != null && raw.isEmpty()) {
                return;
            }
            player.sendMessage(Component.text()
                    .append(platform.messages().prefixed("advent.reminder", Messages.of(
                            "day", String.valueOf(today),
                            "doors", String.valueOf(calendar.doorCount()))))
                    .append(Component.text(" [open]", NamedTextColor.YELLOW)
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("/christmas advent", NamedTextColor.GRAY)))
                            .clickEvent(ClickEvent.runCommand("/christmas advent")))
                    .build());
        }, JOIN_DELAY_TICKS);
    }
}
