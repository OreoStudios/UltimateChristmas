package fr.elias.ultimateChristmas.music;

import com.xxmicloxx.NoteBlockAPI.NoteBlockAPI;
import com.xxmicloxx.NoteBlockAPI.model.Song;
import com.xxmicloxx.NoteBlockAPI.songplayer.RadioSongPlayer;
import com.xxmicloxx.NoteBlockAPI.utils.NBSDecoder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MusicManager {

    private final Plugin plugin;
    private final Logger log;
    // not final → can update when reloading if needed
    private boolean debug;

    private static final String DIR_SONGS = "songs";
    private static final String DIR_MUSIC = "music";

    /**
     * Internal track representation.
     * configKey   = key as written in music.yml (for /playchristmas list)
     * file        = resolved NBS file (case-insensitive lookup)
     * displayName = optional pretty name
     */
    private static class Track {
        final String configKey;
        final File file;
        final String displayName;

        Track(String configKey, File file, String displayName) {
            this.configKey = configKey;
            this.file = file;
            this.displayName = displayName;
        }
    }

    // map key = lowercased configKey, value = track
    private final Map<String, Track> tracks = new LinkedHashMap<>();

    public MusicManager(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.debug = plugin.getConfig().getBoolean("debug", false);
        loadTracks();
    }

    /* ======================================================================
     * PUBLIC API – used by /playchristmas
     * ====================================================================== */

    public boolean isNoteBlockAvailable() {
        try {
            var nbp = Bukkit.getPluginManager().getPlugin("NoteBlockAPI");
            boolean loaded = nbp != null && nbp.isEnabled();
            if (!loaded) return false;

            // Optional diag, does NOT block if getAPI() is null on some forks
            try {
                var api = NoteBlockAPI.getAPI();
                if (api == null && debug) {
                    log.warning("[Music][DEBUG] NoteBlockAPI.getAPI() is null but plugin is enabled. This can be normal on some forks.");
                } else if (debug) {
                    log.info("[Music][DEBUG] NoteBlockAPI.getAPI() OK");
                }
            } catch (Throwable t) {
                if (debug) {
                    log.warning("[Music][DEBUG] getAPI() threw " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }

            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Returns the list of track keys as written in music.yml (for tab-complete + list). */
    public List<String> getKeys() {
        List<String> keys = new ArrayList<>();
        for (Track t : tracks.values()) {
            keys.add(t.configKey);
        }
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        return keys;
    }

    /**
     * Play a track by key.
     * @param key        key typed in the command (any case)
     * @param onlyTarget if non-null, only that player hears the music; else, all online players
     */
    public String playTrack(String key, Player onlyTarget) {
        if (!isNoteBlockAvailable()) {
            return color("&cNoteBlockAPI is not loaded. Install it and add &fsoftdepend: [NoteBlockAPI]&c in plugin.yml.");
        }

        Track track = tracks.get(key.toLowerCase(Locale.ROOT));
        if (track == null) {
            return color("&cUnknown track: &f" + key + "&7. Use &f/playchristmas list&7.");
        }

        if (track.file == null || !track.file.exists()) {
            return color("&cNBS file not found on disk for track &f" + track.configKey + "&c.");
        }

        String displayName = (track.displayName != null && !track.displayName.isBlank())
                ? track.displayName
                : track.configKey;

        if (onlyTarget != null) {
            boolean ok = playRadio(onlyTarget, track.file);
            return ok
                    ? color("&aNow playing &f" + displayName + " &afor &f" + onlyTarget.getName())
                    : color("&cFailed to start &f" + displayName + " &cfor &f" + onlyTarget.getName());
        }

        int okCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playRadio(p, track.file)) okCount++;
        }

        return okCount > 0
                ? color("&aNow playing &f" + displayName + " &afor &f" + okCount + " &aplayer(s).")
                : color("&cCould not start &f" + displayName + " &cfor connected players.");
    }

    public String stopFor(Player p) {
        try {
            NoteBlockAPI.stopPlaying(p);
            return color("&aMusic stopped for &f" + p.getName());
        } catch (Throwable t) {
            log.log(Level.WARNING, "[Music] stopFor error: " + t.getMessage(), t);
            return color("&cError while stopping for &f" + p.getName());
        }
    }

    public int stopAll() {
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                NoteBlockAPI.stopPlaying(p);
                count++;
            } catch (Throwable t) {
                log.log(Level.WARNING, "[Music] stopAll error for " + p.getName() + ": " + t.getMessage(), t);
            }
        }
        return count;
    }

    /* ======================================================================
     * LOW-LEVEL PLAYBACK
     * ====================================================================== */

    private boolean playRadio(Player player, File nbsFile) {
        d("Invoke by " + player.getName() + " for file '" + nbsFile.getName() + "'");

        var nbp = Bukkit.getPluginManager().getPlugin("NoteBlockAPI");
        if (nbp == null || !nbp.isEnabled()) {
            log.warning("[Music] NoteBlockAPI not found or not enabled.");
            return false;
        }

        try {
            if (NoteBlockAPI.getAPI() == null && debug) {
                log.warning("[Music][DEBUG] getAPI() == null (fork/impl). Trying playback anyway.");
            } else if (debug) {
                log.info("[Music][DEBUG] getAPI() OK");
            }
        } catch (Throwable ignored) {}

        d("Using NBS file: " + nbsFile.getAbsolutePath());
        if (!nbsFile.exists()) {
            log.warning("[Music] NBS file does not exist: " + nbsFile.getAbsolutePath());
            return false;
        }

        try {
            Song song = NBSDecoder.parse(nbsFile);
            if (song == null) {
                log.warning("[Music] Failed to parse NBS: " + nbsFile.getName());
                return false;
            }

            d("Parsed song: title=" + song.getTitle() + ", author=" + song.getAuthor()
                    + ", len=" + song.getLength() + " ticks, speed=" + song.getSpeed());

            RadioSongPlayer rsp = new RadioSongPlayer(song);
            rsp.setAutoDestroy(true);
            rsp.addPlayer(player);
            rsp.setPlaying(true);
            d("Starting RadioSongPlayer for " + player.getName());
            return true;

        } catch (Throwable t) {
            log.log(Level.WARNING, "[Music] Failed starting RadioSongPlayer: " + t.getMessage(), t);
            return false;
        }
    }

    /* ======================================================================
     * TRACK LOADING (CONFIG + DISK, CASE-INSENSITIVE)
     * ====================================================================== */

    /** Called from /uc reload via UltimateChristmas.reloadAll() */
    public void reloadTracks() {
        // update debug flag too (in case it changed)
        this.debug = plugin.getConfig().getBoolean("debug", false);
        loadTracks();
    }

    private void loadTracks() {
        tracks.clear();

        // 1) Build a case-insensitive map of all .nbs files in songs/ and music/
        Map<String, File> fileMap = new HashMap<>();

        File dataFolder = plugin.getDataFolder();
        File songsDir = new File(dataFolder, DIR_SONGS);
        File musicDir = new File(dataFolder, DIR_MUSIC);

        if (!songsDir.exists()) songsDir.mkdirs();
        if (!musicDir.exists()) musicDir.mkdirs();

        scanFolderForNbs(songsDir, fileMap);
        scanFolderForNbs(musicDir, fileMap);

        // 2) Read music.yml (or fallback to config.yml)
        File musicFile = new File(dataFolder, "music.yml");
        YamlConfiguration cfg = musicFile.exists()
                ? YamlConfiguration.loadConfiguration(musicFile)
                : YamlConfiguration.loadConfiguration(new File(dataFolder, "config.yml"));

        ConfigurationSection sec = cfg.isConfigurationSection("tracks")
                ? cfg.getConfigurationSection("tracks")
                : cfg.getConfigurationSection("music.tracks");

        if (sec == null) {
            d("No 'tracks' or 'music.tracks' section found in music.yml.");
            return;
        }

        // 3) Parse config entries and resolve to real files (case-insensitive)
        for (String key : sec.getKeys(false)) {
            String fileName = null;
            String display = null;

            if (sec.isString(key)) {
                fileName = sec.getString(key);
            } else if (sec.isConfigurationSection(key)) {
                ConfigurationSection node = sec.getConfigurationSection(key);
                fileName = node.getString("file");
                display = node.getString("display_name");
            }

            if (fileName == null || fileName.isBlank()) continue;

            // normalise extension
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".nbs")) {
                fileName = fileName + ".nbs";
            }

            File resolved = fileMap.get(fileName.toLowerCase(Locale.ROOT));

            if (resolved == null) {
                log.warning("[Music] Config track '" + key + "' refers to '" + fileName
                        + "' but no such NBS file was found (case-insensitive) in '"
                        + DIR_SONGS + "/' or '" + DIR_MUSIC + "/'.");
            } else {
                d("Track loaded: key=" + key + " -> file=" + resolved.getName()
                        + (display != null ? " (" + display + ")" : ""));
            }

            // Store track anyway; playTrack will report if file is missing
            tracks.put(key.toLowerCase(Locale.ROOT), new Track(key, resolved, display));
        }
    }

    private void scanFolderForNbs(File dir, Map<String, File> fileMap) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName();
            if (name.toLowerCase(Locale.ROOT).endsWith(".nbs")) {
                fileMap.put(name.toLowerCase(Locale.ROOT), f);
            }
        }
    }

    /* ======================================================================
     * UTIL
     * ====================================================================== */

    private void d(String msg) {
        if (debug) log.info("[Music][DEBUG] " + msg);
    }

    private String color(String s) {
        return s.replace('&', '§');
    }
}
