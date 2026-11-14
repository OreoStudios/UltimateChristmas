package fr.elias.ultimateChristmas.modules.snow.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import fr.elias.ultimateChristmas.modules.snow.api.SnowService;
import fr.elias.ultimateChristmas.modules.snow.infra.SnowEffect;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Locale;

/**
 * Personal snowfall, plus server-wide blizzards.
 *
 * <p>The ticking task is registered against the module's lifecycle, so
 * disabling this module with {@code /christmas module disable snow} stops the
 * task immediately rather than leaving it running until a server restart — the
 * exact leak the 3.x design could not avoid.
 */
@PluginModule(value = "snow", dependencies = "platform::platform", configuration = true)
public final class SnowModule extends FeatureModule implements SnowService {

    private SnowEffect effect;
    private volatile SnowParameters parameters;

    public SnowModule(ChristmasPlatform platform, SnowParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        effect = new SnowEffect(platform, () -> parameters);

        if (parameters.onByDefault()) {
            Bukkit.getOnlinePlayers().forEach(effect::enable);
        }
        listen(new JoinListener());
        timer(effect::tick, 20L, parameters.tickPeriod());
        command(new SnowCommand());

        publish(SnowService.class, this);
    }

    @Override
    protected void onDisable() {
        effect.clear();
    }

    /**
     * Snow's tick period is baked into the scheduled task, so changing it needs
     * a new task. Everything else is read live through the supplier.
     */
    @Override
    protected void onReload() {
        long previousPeriod = parameters.tickPeriod();
        parameters = SnowParameters.from(config(), SnowParameters.DEFAULTS);
        if (parameters.tickPeriod() != previousPeriod) {
            log().info("snow: tick-period changed; run "
                    + "/christmas module restart snow to apply the new rate.");
        }
    }

    // ------------------------------------------------------------ public API

    @Override
    public boolean enabledFor(Player player) {
        return effect.enabledFor(player);
    }

    @Override
    public boolean toggle(Player player) {
        return effect.toggle(player);
    }

    @Override
    public void startBlizzard(int seconds) {
        effect.startBlizzard(seconds);
        Bukkit.getOnlinePlayers().forEach(player ->
                platform.messages().send(player, "snow.blizzard-started",
                        Messages.of("seconds", String.valueOf(seconds))));
    }

    @Override
    public boolean blizzardActive() {
        return effect.blizzardActive();
    }

    private final class JoinListener implements Listener {

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            if (parameters.onByDefault()) {
                effect.enable(event.getPlayer());
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            // Drop the entry so the set does not grow with every player seen.
            effect.disable(event.getPlayer());
        }
    }

    /** {@code /christmas snow [on|off|blizzard <seconds>]}. */
    private final class SnowCommand implements SubCommand {

        @Override
        public String name() {
            return "snow";
        }

        @Override
        public List<String> aliases() {
            return List.of("snowify");
        }

        @Override
        public String permission() {
            return "ultimatechristmas.snow";
        }

        @Override
        public String usage() {
            return "[on|off|blizzard <seconds>]";
        }

        @Override
        public String description() {
            return "Toggle the snow that follows you";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            String action = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);

            if (action.equals("blizzard")) {
                if (!sender.hasPermission("ultimatechristmas.admin.snow")) {
                    throw new CommandError("You may not start a blizzard.");
                }
                int seconds = 60;
                if (args.length > 1) {
                    try {
                        seconds = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ex) {
                        throw new CommandError("'" + args[1] + "' is not a number of seconds.");
                    }
                }
                startBlizzard(Math.max(1, Math.min(3600, seconds)));
                return;
            }

            if (!(sender instanceof Player player)) {
                throw new CommandError("Only a player can toggle their own snow.");
            }
            boolean on = switch (action) {
                case "on", "enable" -> {
                    effect.enable(player);
                    yield true;
                }
                case "off", "disable" -> {
                    effect.disable(player);
                    yield false;
                }
                default -> effect.toggle(player);
            };
            platform.messages().send(player, on ? "snow.enabled" : "snow.disabled");
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return sender.hasPermission("ultimatechristmas.admin.snow")
                        ? List.of("on", "off", "blizzard")
                        : List.of("on", "off");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("blizzard")) {
                return List.of("30", "60", "300");
            }
            return List.of();
        }
    }
}
