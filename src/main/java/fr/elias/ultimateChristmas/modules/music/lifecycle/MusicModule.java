package fr.elias.ultimateChristmas.modules.music.lifecycle;

import com.xxmicloxx.NoteBlockAPI.model.Song;
import dev.oreo.modulith.core.PluginModule;
import fr.elias.ultimateChristmas.core.command.CommandError;
import fr.elias.ultimateChristmas.core.command.SubCommand;
import fr.elias.ultimateChristmas.core.modulith.FeatureModule;
import fr.elias.ultimateChristmas.core.text.Messages;
import fr.elias.ultimateChristmas.modules.music.api.MusicService;
import fr.elias.ultimateChristmas.modules.music.infra.MusicLibrary;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The carol jukebox: 23 bundled note block tracks, a radio and event cues.
 *
 * <p>Playback needs NoteBlockAPI, which is optional. Without it the module
 * still starts and the commands explain what is missing, rather than the plugin
 * refusing to load — 3.x declared NoteBlockAPI a hard dependency, so a server
 * without it could not run the plugin at all.
 */
@PluginModule(value = "music", dependencies = "platform::platform", configuration = true)
public final class MusicModule extends FeatureModule implements MusicService {

    private MusicLibrary library;
    private volatile MusicParameters parameters;

    public MusicModule(ChristmasPlatform platform, MusicParameters parameters) {
        super(platform);
        this.parameters = parameters;
    }

    @Override
    protected void onEnable() {
        library = new MusicLibrary(platform);
        reloadData();

        listen(new JoinListener());
        command(new MusicCommand());
        publish(MusicService.class, this);

        if (!platform.hooks().noteBlock().active()) {
            log().info("NoteBlockAPI is not installed, so the carols will stay quiet. "
                    + "Install it to hear them.");
        }
    }

    @Override
    protected void onDisable() {
        platform.hooks().noteBlock().stopAll();
    }

    @Override
    protected void onReload() {
        parameters = MusicParameters.from(config(), MusicParameters.DEFAULTS);
        reloadData();
    }

    private void reloadData() {
        library.load(section("tracks"), section("cues"), parameters.radioPlaylist());
    }

    // ------------------------------------------------------------ public API

    @Override
    public boolean available() {
        return platform.hooks().noteBlock().active() && !library.isEmpty();
    }

    @Override
    public Set<String> tracks() {
        return library.keys();
    }

    @Override
    public boolean play(Player player, String trackKey) {
        MusicLibrary.Track track = library.track(trackKey);
        if (track == null) {
            return false;
        }
        Song song = library.songOf(track);
        if (song == null) {
            return false;
        }
        if (!platform.hooks().noteBlock().play(player, song,
                (float) parameters.volume(), false)) {
            return false;
        }
        platform.messages().send(player, "music.playing",
                Messages.of("track", track.displayName(), "source", track.source()));
        return true;
    }

    @Override
    public void cue(Player player, String cueName) {
        if (!parameters.cuesEnabled() || !available()) {
            return;
        }
        MusicLibrary.Track track = library.forCue(cueName);
        if (track == null) {
            return;
        }
        Song song = library.songOf(track);
        if (song != null) {
            platform.hooks().noteBlock().play(player, song, (float) parameters.volume(), false);
        }
    }

    @Override
    public void stop(Player player) {
        platform.hooks().noteBlock().stop(player);
    }

    /** Starts the radio playlist for one player. */
    private boolean startRadio(Player player) {
        List<Song> songs = library.radioSongs();
        if (songs.isEmpty()) {
            return false;
        }
        return platform.hooks().noteBlock().playPlaylist(player, songs,
                (float) parameters.volume(), parameters.radioShuffle());
    }

    private final class JoinListener implements Listener {

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            if (!available()) {
                return;
            }
            Player player = event.getPlayer();
            // Players who opted into the radio get it back on reconnect.
            if (parameters.radioEnabled()
                    && platform.profiles().get(player).flag("radio")) {
                Bukkit.getScheduler().runTaskLater(platform.plugin(),
                        () -> {
                            if (player.isOnline()) {
                                startRadio(player);
                            }
                        }, 40L);
                return;
            }
            if (!parameters.playOnJoin().isBlank()) {
                Bukkit.getScheduler().runTaskLater(platform.plugin(),
                        () -> {
                            if (player.isOnline()) {
                                play(player, parameters.playOnJoin());
                            }
                        }, 40L);
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            platform.hooks().noteBlock().stop(event.getPlayer());
        }
    }

    /** {@code /christmas music [list|play <track>|radio|stop]}. */
    private final class MusicCommand implements SubCommand {

        @Override
        public String name() {
            return "music";
        }

        @Override
        public List<String> aliases() {
            return List.of("carol", "playchristmas");
        }

        @Override
        public String permission() {
            return "ultimatechristmas.music";
        }

        @Override
        public String usage() {
            return "[list|play <track>|radio|stop]";
        }

        @Override
        public String description() {
            return "Play a Christmas carol";
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);

            if (action.equals("list")) {
                platform.messages().send(sender, "music.list", Messages.of(
                        "count", String.valueOf(library.keys().size()),
                        "tracks", String.join(", ", library.keys())));
                return;
            }

            if (!platform.hooks().noteBlock().active()) {
                throw new CommandError("NoteBlockAPI is not installed, so there is "
                        + "nothing to play it through. The carols are already in "
                        + "plugins/UltimateChristmas/music/ and will work once it is.");
            }

            switch (action) {
                case "play" -> {
                    Player target = requirePlayer(sender, args, 2);
                    if (args.length < 2) {
                        throw new CommandError("Which track? " + String.join(", ", library.keys()));
                    }
                    if (!play(target, args[1])) {
                        throw new CommandError("No track called '" + args[1]
                                + "'. Try /christmas music list.");
                    }
                }
                case "radio" -> {
                    Player player = requirePlayer(sender, args, 1);
                    if (!parameters.radioEnabled()) {
                        throw new CommandError("The radio is switched off on this server.");
                    }
                    var profile = platform.profiles().get(player);
                    if (profile.flag("radio")) {
                        profile.flag("radio", false);
                        stop(player);
                        platform.messages().send(player, "music.radio-off");
                        return;
                    }
                    if (!startRadio(player)) {
                        throw new CommandError("The radio playlist is empty or unreadable.");
                    }
                    profile.flag("radio", true);
                    platform.messages().send(player, "music.radio-on");
                }
                case "stop" -> {
                    if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
                        if (!sender.hasPermission("ultimatechristmas.admin.music")) {
                            throw new CommandError("You may not stop music for everyone.");
                        }
                        int count = platform.hooks().noteBlock().stopAll();
                        platform.messages().send(sender, "music.stopped-all",
                                Messages.of("count", String.valueOf(count)));
                        return;
                    }
                    Player player = requirePlayer(sender, args, 1);
                    platform.profiles().get(player).flag("radio", false);
                    stop(player);
                    platform.messages().send(player, "music.stopped");
                }
                default -> throw new CommandError("Usage: /christmas music " + usage());
            }
        }

        /** The target player: an explicit name for admins, else the sender. */
        private Player requirePlayer(CommandSender sender, String[] args, int nameIndex) {
            if (args.length > nameIndex
                    && sender.hasPermission("ultimatechristmas.admin.music")) {
                Player named = Bukkit.getPlayerExact(args[nameIndex]);
                if (named == null) {
                    throw new CommandError("'" + args[nameIndex] + "' is not online.");
                }
                return named;
            }
            if (sender instanceof Player self) {
                return self;
            }
            throw new CommandError("From the console, name a player.");
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return List.of("list", "play", "radio", "stop");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("play")) {
                return new ArrayList<>(library.keys());
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("stop")
                    && sender.hasPermission("ultimatechristmas.admin.music")) {
                return List.of("all");
            }
            return List.of();
        }
    }
}
