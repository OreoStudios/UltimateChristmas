package fr.elias.ultimateChristmas.modules.events.lifecycle;

import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.modules.events.infra.EventControl;
import fr.elias.ultimateChristmas.modules.events.infra.EventWindow;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Starts and ends administrator-configured Christmas events at their calendar
 * boundaries. All commands execute as console, from the module-owned timer.
 */
@PluginModule(value = "events", dependencies = "platform::platform", configuration = true)
public final class EventModule extends FeatureModule implements EventControl {
    private volatile EventParameters parameters;
    private final Map<String, EventWindow> windows = new LinkedHashMap<>();
    private final Map<String, Boolean> active = new HashMap<>();
    private final Map<String, Boolean> overrides = new HashMap<>();

    public EventModule(ChristmasPlatform platform, EventParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        loadWindows();
        command(new EventCommand());
        timer(this::tick, 20L, 20L);
    }

    @Override
    protected void onReload() {
        parameters = EventParameters.from(config(), EventParameters.DEFAULTS);
        loadWindows();
    }

    @Override
    protected void onDisable() {
        for (String id : new ArrayList<>(active.keySet())) {
            EventWindow window = windows.get(id);
            if (window != null && active.getOrDefault(id, false)) {
                dispatchCommands(window.endCommands());
            }
        }
        active.clear();
        overrides.clear();
        windows.clear();
    }

    private void loadWindows() {
        ZoneId zone;
        try {
            zone = ZoneId.of(parameters.timezone());
        } catch (RuntimeException ex) {
            log().warning("events.yml: invalid timezone; using UTC: " + parameters.timezone());
            zone = ZoneId.of("UTC");
        }
        Map<String, EventWindow> next = new LinkedHashMap<>();
        ConfigurationSection section = section("events");
        for (String id : section.getKeys(false)) {
            ConfigurationSection event = section.getConfigurationSection(id);
            if (event == null) continue;
            EventWindow window = EventWindow.read(id, event, zone, log());
            if (window != null) next.put(id.toLowerCase(Locale.ROOT), window);
        }
        for (String removed : new ArrayList<>(active.keySet())) {
            if (!next.containsKey(removed)) {
                EventWindow previous = windows.get(removed);
                if (previous != null && active.getOrDefault(removed, false)) {
                    dispatchCommands(previous.endCommands());
                }
                active.remove(removed);
                overrides.remove(removed);
            }
        }
        windows.clear();
        windows.putAll(next);
        tick();
    }

    private void tick() {
        Instant now = Instant.now();
        for (EventWindow window : windows.values()) {
            boolean scheduled = parameters.enabled()
                    && window.activeAt(now, platform.season().active());
            boolean desired = parameters.enabled()
                    && overrides.getOrDefault(window.id().toLowerCase(Locale.ROOT), scheduled);
            transition(window, desired);
        }
    }

    private void transition(EventWindow window, boolean desired) {
        String id = window.id().toLowerCase(Locale.ROOT);
        boolean previously = active.getOrDefault(id, false);
        if (desired == previously) return;
        active.put(id, desired);
        dispatchCommands(desired ? window.startCommands() : window.endCommands());
        log().info("Christmas event " + id + (desired ? " started" : " finished"));
    }

    private void dispatchCommands(List<String> commands) {
        for (String command : commands) {
            String trimmed = command.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), trimmed);
            } catch (RuntimeException ex) {
                log().warning("Christmas event command failed: " + trimmed + " (" + ex.getMessage() + ")");
            }
        }
    }

    @Override
    public List<Status> statuses() {
        List<Status> result = new ArrayList<>();
        for (EventWindow window : windows.values()) {
            String id = window.id().toLowerCase(Locale.ROOT);
            result.add(new Status(id, window.enabled(),
                    active.getOrDefault(id, false), overrides.get(id), window.description()));
        }
        return result;
    }

    @Override
    public boolean startNow(String id) { return override(id, true); }

    @Override
    public boolean stopNow(String id) { return override(id, false); }

    @Override
    public boolean auto(String id) { return override(id, null); }

    private boolean override(String id, Boolean state) {
        id = id.toLowerCase(Locale.ROOT);
        if (!windows.containsKey(id)) return false;
        if (state == null) overrides.remove(id);
        else overrides.put(id, state);
        tick();
        return true;
    }

    @Override
    public boolean setEnabled(String id, boolean enabled) {
        String normalized = id.toLowerCase(Locale.ROOT);
        EventWindow previous = windows.get(normalized);
        if (previous == null) return false;
        File file = new File(platform.plugin().getDataFolder(), "modules/events.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "events." + previous.id() + ".enabled";
        yaml.set(path, enabled);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            log().warning("Could not persist event setting: " + ex.getMessage());
            return false;
        }
        windows.put(normalized, previous.withEnabled(enabled));
        overrides.remove(normalized);
        tick();
        return true;
    }

    private final class EventCommand implements SubCommand {
        @Override public String name() { return "events"; }
        @Override public List<String> aliases() { return List.of("event"); }
        @Override public String permission() { return "ultimatechristmas.admin.events"; }
        @Override public String usage() { return "<list|start|stop|auto|enable|disable> [id]"; }
        @Override public String description() { return "Control scheduled Christmas events"; }

        @Override
        public void execute(CommandSender sender, String[] args) {
            String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
            if (action.equals("list") || action.equals("status")) {
                for (Status s : statuses()) {
                    platform.messages().sendRaw(sender,
                            "<gold>" + s.id() + "</gold> <gray>(" + s.schedule() + ")</gray> "
                            + (s.active() ? "<green>ACTIVE" : "<red>inactive")
                            + (s.enabled() ? "" : " <dark_gray>[disabled]"), Map.of());
                }
                if (windows.isEmpty()) platform.messages().sendRaw(sender,
                        "<gray>No scheduled events configured.", Map.of());
                return;
            }
            if (args.length < 2) throw new CommandError("Usage: /christmas events " + usage());
            String id = args[1];
            boolean ok = switch (action) {
                case "start" -> startNow(id);
                case "stop" -> stopNow(id);
                case "auto" -> auto(id);
                case "enable" -> setEnabled(id, true);
                case "disable" -> setEnabled(id, false);
                default -> throw new CommandError("Usage: /christmas events " + usage());
            };
            if (!ok) throw new CommandError("Unknown event or could not save: " + id);
            platform.messages().sendRaw(sender, "<green>Event " + id + ": " + action, Map.of());
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return List.of("list", "start", "stop", "auto", "enable", "disable");
            if (args.length == 2) return new ArrayList<>(windows.keySet());
            return List.of();
        }
    }
}
