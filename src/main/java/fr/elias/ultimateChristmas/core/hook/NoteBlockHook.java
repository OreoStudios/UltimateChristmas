package fr.elias.ultimateChristmas.core.hook;

import com.xxmicloxx.NoteBlockAPI.model.Playlist;
import com.xxmicloxx.NoteBlockAPI.model.RepeatMode;
import com.xxmicloxx.NoteBlockAPI.model.Song;
import com.xxmicloxx.NoteBlockAPI.songplayer.RadioSongPlayer;
import com.xxmicloxx.NoteBlockAPI.utils.NBSDecoder;
import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Note block song playback.
 *
 * <p>Decoded songs are cached by file, because {@code NBSDecoder} reads and
 * parses the file from disk on every call and the 3.x code did that on the main
 * thread each time a track started.
 */
public final class NoteBlockHook extends PluginHook {

    private final Map<String, Song> songCache = new HashMap<>();
    private final Map<UUID, RadioSongPlayer> active = new HashMap<>();

    public NoteBlockHook(UltimateChristmas plugin) {
        super(plugin);
    }

    @Override
    public String pluginName() {
        return "NoteBlockAPI";
    }

    @Override
    protected boolean setUp() {
        // Resolving the decoder class is enough to confirm a usable build.
        return NBSDecoder.class.getName() != null;
    }

    /** Loads and caches a song, or returns {@code null} if it will not parse. */
    public Song song(File file) {
        if (!active() || file == null || !file.isFile()) {
            return null;
        }
        String key = file.getAbsolutePath();
        Song cached = songCache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Song parsed = NBSDecoder.parse(file);
            if (parsed == null) {
                plugin.getLogger().warning("Could not read note block song " + file.getName());
                return null;
            }
            songCache.put(key, parsed);
            return parsed;
        } catch (Exception ex) {
            plugin.getLogger().warning("Invalid note block song " + file.getName()
                    + ": " + ex.getMessage());
            return null;
        }
    }

    public void clearCache() {
        songCache.clear();
    }

    /** Starts a song for one player, replacing anything they were hearing. */
    public boolean play(Player player, Song song, float volume, boolean loop) {
        if (!active() || song == null) {
            return false;
        }
        stop(player);
        RadioSongPlayer radio = new RadioSongPlayer(song);
        radio.addPlayer(player);
        radio.setVolume((byte) Math.max(0, Math.min(100, Math.round(volume * 100))));
        radio.setRepeatMode(loop ? RepeatMode.ONE : RepeatMode.NO);
        radio.setPlaying(true);
        active.put(player.getUniqueId(), radio);
        return true;
    }

    /** Starts a shuffled or ordered playlist for one player. */
    public boolean playPlaylist(Player player, List<Song> songs, float volume, boolean shuffle) {
        if (!active() || songs.isEmpty()) {
            return false;
        }
        stop(player);
        RadioSongPlayer radio = new RadioSongPlayer(new Playlist(songs.toArray(new Song[0])));
        radio.addPlayer(player);
        radio.setVolume((byte) Math.max(0, Math.min(100, Math.round(volume * 100))));
        radio.setRepeatMode(RepeatMode.ALL);
        radio.setRandom(shuffle);
        radio.setPlaying(true);
        active.put(player.getUniqueId(), radio);
        return true;
    }

    public boolean isPlaying(Player player) {
        RadioSongPlayer radio = active.get(player.getUniqueId());
        return radio != null && radio.isPlaying();
    }

    public void stop(Player player) {
        RadioSongPlayer radio = active.remove(player.getUniqueId());
        if (radio != null) {
            radio.setPlaying(false);
            radio.removePlayer(player);
            radio.destroy();
        }
    }

    /** Stops every player and returns how many were listening. */
    public int stopAll() {
        int count = active.size();
        for (RadioSongPlayer radio : List.copyOf(active.values())) {
            radio.setPlaying(false);
            radio.destroy();
        }
        active.clear();
        return count;
    }

    /** The title stored in a song file, falling back to its file name. */
    public static String titleOf(Song song, String fallback) {
        if (song == null) {
            return fallback;
        }
        String title = song.getTitle();
        return title == null || title.isBlank() ? fallback : title;
    }
}
