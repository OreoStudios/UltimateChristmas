package fr.elias.ultimateChristmas.modules.music.infra;

import com.xxmicloxx.NoteBlockAPI.model.Song;
import fr.elias.ultimateChristmas.core.hook.NoteBlockHook;
import fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The configured tracks, resolved to files on disk.
 *
 * <p>Note block songs are loaded from {@code plugins/UltimateChristmas/music/},
 * which the plugin fills with its bundled carols on first start. A track whose
 * file is missing is reported once at load rather than failing silently when a
 * player tries to play it.
 */
public final class MusicLibrary {

    /**
     * One playable track.
     *
     * @param key         config key, used by commands
     * @param file        the .nbs file
     * @param displayName shown to players
     * @param source      attribution, e.g. the composer and year
     */
    public record Track(String key, File file, String displayName, String source) {
    }

    private final ChristmasPlatform platform;
    private final File folder;
    private final Map<String, Track> tracks = new LinkedHashMap<>();
    private final Map<String, String> cues = new LinkedHashMap<>();

    private List<String> radioPlaylist = List.of();

    public MusicLibrary(ChristmasPlatform platform) {
        this.platform = platform;
        this.folder = new File(platform.plugin().getDataFolder(), "music");
    }

    /**
     * Reloads the track list and cue mapping.
     *
     * @param tracksSection the {@code tracks} section
     * @param cuesSection   the {@code cues} section, mapping cue name to track key
     */
    public void load(ConfigurationSection tracksSection, ConfigurationSection cuesSection,
                     List<String> playlist) {
        tracks.clear();
        cues.clear();
        platform.hooks().noteBlock().clearCache();

        List<String> missing = new ArrayList<>();
        for (String key : tracksSection.getKeys(false)) {
            ConfigurationSection child = tracksSection.getConfigurationSection(key);
            String fileName = child == null
                    ? tracksSection.getString(key)
                    : child.getString("file", key + ".nbs");
            if (fileName == null || fileName.isBlank()) {
                continue;
            }
            File file = new File(folder, fileName);
            if (!file.isFile()) {
                missing.add(fileName);
                continue;
            }
            tracks.put(key, new Track(
                    key,
                    file,
                    child == null ? "&f" + key : child.getString("display_name", "&f" + key),
                    child == null ? "" : child.getString("source", "")));
        }

        for (String cue : cuesSection.getKeys(false)) {
            String trackKey = cuesSection.getString(cue);
            if (trackKey != null && !trackKey.isBlank()) {
                cues.put(cue, trackKey);
            }
        }

        this.radioPlaylist = playlist == null ? List.of() : List.copyOf(playlist);

        platform.plugin().getLogger().info("Music: " + tracks.size() + " track(s), "
                + cues.size() + " cue(s)"
                + (platform.hooks().noteBlock().active() ? "" : " (NoteBlockAPI not installed)"));
        if (!missing.isEmpty()) {
            platform.plugin().getLogger().warning("Missing music files in music/: "
                    + String.join(", ", missing));
        }
    }

    public Track track(String key) {
        return key == null ? null : tracks.get(key);
    }

    public java.util.Set<String> keys() {
        return tracks.keySet();
    }

    public boolean isEmpty() {
        return tracks.isEmpty();
    }

    /** The track mapped to a cue name, or {@code null} if the cue is unmapped. */
    public Track forCue(String cueName) {
        return track(cues.get(cueName));
    }

    /** Decoded songs for the radio playlist, skipping anything unreadable. */
    public List<Song> radioSongs() {
        NoteBlockHook hook = platform.hooks().noteBlock();
        List<Song> songs = new ArrayList<>();
        List<String> keys = radioPlaylist.isEmpty()
                ? new ArrayList<>(tracks.keySet())
                : radioPlaylist;
        for (String key : keys) {
            Track track = track(key);
            if (track == null) {
                continue;
            }
            Song song = hook.song(track.file());
            if (song != null) {
                songs.add(song);
            }
        }
        return songs;
    }

    public Song songOf(Track track) {
        return platform.hooks().noteBlock().song(track.file());
    }
}
