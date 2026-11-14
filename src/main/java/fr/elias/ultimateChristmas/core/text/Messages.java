package fr.elias.ultimateChristmas.core.text;

import fr.elias.ultimateChristmas.core.config.ConfigFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every player-facing string, resolved from lang.yml.
 *
 * <p>Keys missing from lang.yml render as a visible {@code <missing: key>}
 * marker instead of silently sending nothing, so a typo in a config surfaces
 * immediately rather than looking like a broken feature.
 */
public final class Messages {

    private final ConfigFile lang;
    private String prefix = "";

    public Messages(ConfigFile lang) {
        this.lang = lang;
        reload();
    }

    public void reload() {
        prefix = lang.getString("prefix", "<red><bold>[Christmas]</bold></red> ");
    }

    // ---------------------------------------------------------------- lookup

    /** The raw configured string for a key, or {@code null} if absent. */
    public String raw(String key) {
        return lang.raw().getString(key);
    }

    public boolean has(String key) {
        return lang.raw().isString(key) || lang.raw().isList(key);
    }

    /** A key rendered to a component, without the plugin prefix. */
    public Component get(String key, Map<String, String> placeholders) {
        String raw = raw(key);
        if (raw == null) {
            return Component.text("<missing lang key: " + key + ">");
        }
        return Text.parse(raw, placeholders);
    }

    public Component get(String key) {
        return get(key, Map.of());
    }

    /** A key rendered with the plugin prefix in front. */
    public Component prefixed(String key, Map<String, String> placeholders) {
        return Text.parse(prefix).append(get(key, placeholders));
    }

    public Component prefix() {
        return Text.parse(prefix);
    }

    // ------------------------------------------------------------------ send

    public void send(CommandSender to, String key) {
        send(to, key, Map.of());
    }

    /**
     * Sends a lang key. A key configured as an empty string sends nothing,
     * which is how an owner silences an individual message.
     */
    public void send(CommandSender to, String key, Map<String, String> placeholders) {
        String raw = raw(key);
        if (raw != null && raw.isEmpty()) {
            return;
        }
        to.sendMessage(prefixed(key, placeholders));
    }

    /** Sends a key configured as a list, one chat line per entry. */
    public void sendList(CommandSender to, String key, Map<String, String> placeholders) {
        List<String> lines = lang.getStringList(key);
        for (String line : lines) {
            to.sendMessage(Text.parse(line, placeholders));
        }
    }

    public void sendRaw(CommandSender to, String rawMessage, Map<String, String> placeholders) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return;
        }
        to.sendMessage(Text.parse(rawMessage, placeholders));
    }

    public void actionBar(Player to, String key, Map<String, String> placeholders) {
        to.sendActionBar(get(key, placeholders));
    }

    /**
     * Shows a title/subtitle pair from two lang keys.
     *
     * <p>Timings are in ticks to match the rest of the config surface.
     */
    public void title(Player to, String titleKey, String subtitleKey,
                      Map<String, String> placeholders, int fadeIn, int stay, int fadeOut) {
        Component main = titleKey == null ? Component.empty() : get(titleKey, placeholders);
        Component sub = subtitleKey == null ? Component.empty() : get(subtitleKey, placeholders);
        to.showTitle(Title.title(main, sub, Title.Times.times(
                ticks(fadeIn), ticks(stay), ticks(fadeOut))));
    }

    public void sound(Player to, Sound sound, float volume, float pitch) {
        to.playSound(to.getLocation(), sound, volume, pitch);
    }

    private static Duration ticks(int count) {
        return Duration.ofMillis(Math.max(0, count) * 50L);
    }

    // ----------------------------------------------------------- placeholders

    /** Builds a mutable placeholder map from alternating key/value pairs. */
    public static Map<String, String> of(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Expected key/value pairs, got " + pairs.length);
        }
        Map<String, String> map = new HashMap<>(Math.max(4, pairs.length));
        for (int index = 0; index < pairs.length; index += 2) {
            map.put(pairs[index], pairs[index + 1]);
        }
        return map;
    }
}
