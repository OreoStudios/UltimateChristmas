package fr.elias.ultimateChristmas.modules.music.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

import java.util.List;

/**
 * Typed settings for the jukebox, built by its functor.
 *
 * @param volume         playback volume, 0 to 1
 * @param radioEnabled   let players opt into a continuous playlist
 * @param radioShuffle   shuffle the radio playlist
 * @param radioPlaylist  track keys the radio cycles; empty means every track
 * @param cuesEnabled    play short cues on plugin events, e.g. a boss spawning
 * @param playOnJoin     a track key to start for players as they join, or empty
 */
public record MusicParameters(
        double volume,
        boolean radioEnabled,
        boolean radioShuffle,
        List<String> radioPlaylist,
        boolean cuesEnabled,
        String playOnJoin
) {

    public MusicParameters {
        radioPlaylist = List.copyOf(radioPlaylist);
    }

    public static final MusicParameters DEFAULTS = new MusicParameters(
            0.7, true, true,
            List.of("jingle_bells", "deck_the_halls", "we_wish_you_a_merry_christmas",
                    "carol_of_the_bells", "silent_night"),
            true, "");

    public static MusicParameters from(ModuleConfiguration config, MusicParameters defaults) {
        List<String> playlist = config.getStringList("radio.playlist");
        return new MusicParameters(
                Math.max(0, Math.min(1.0, config.getDouble("volume", defaults.volume()))),
                config.getBoolean("radio.enabled", defaults.radioEnabled()),
                config.getBoolean("radio.shuffle", defaults.radioShuffle()),
                playlist == null || playlist.isEmpty() ? defaults.radioPlaylist() : playlist,
                config.getBoolean("cues.enabled", defaults.cuesEnabled()),
                config.getString("play-on-join", defaults.playOnJoin()));
    }
}
