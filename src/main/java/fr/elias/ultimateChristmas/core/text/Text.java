package fr.elias.ultimateChristmas.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns configured strings into Adventure components.
 *
 * <p>Server owners write either flavour and may mix them freely: the legacy
 * {@code &a} / {@code &#ff0088} codes are rewritten into MiniMessage tags first,
 * then the whole string is parsed by MiniMessage. That keeps every config from
 * the 3.x days working while unlocking gradients, hover text and click actions
 * for anyone who wants them.
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** {@code &#rrggbb}, or the legacy {@code &x&r&r&g&g&b&b} spread form. */
    private static final Pattern LEGACY_HEX =
            Pattern.compile("(?i)&#([0-9a-f]{6})|&x(?:&([0-9a-f])){6}");
    private static final Pattern LEGACY_CODE = Pattern.compile("(?i)&([0-9a-fk-or])");
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_]+)%");

    private static final String[] COLOURS = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red",
            "light_purple", "yellow", "white"
    };

    /**
     * Marks a placeholder value as formatted text (see {@link #formatted}). A
     * private-use character, so player names and chat can never carry it.
     */
    private static final char FORMATTED = '';

    private Text() {
    }

    /**
     * Wraps an admin-configured value (a track title, quest name, ...) so that
     * its {@code &6} / MiniMessage formatting is rendered when it fills a
     * placeholder. Plain placeholder values stay literal text; never pass
     * player-controlled input here.
     */
    public static String formatted(String value) {
        return value == null ? "" : FORMATTED + value;
    }

    /** Parses a configured string with no placeholders. */
    public static Component parse(String raw) {
        return parse(raw, Map.of());
    }

    /**
     * Parses a configured string after substituting {@code %key%} placeholders.
     *
     * <p>Placeholder values are inserted as literal text rather than re-parsed,
     * so a player name containing {@code <} or {@code &} can never inject
     * formatting or a click action into a message.
     */
    public static Component parse(String raw, Map<String, String> placeholders) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        String converted = toMiniMessage(raw);
        try {
            return MINI.deserialize(converted, resolverFor(placeholders));
        } catch (RuntimeException ex) {
            // A malformed tag should render as visible text, not break the
            // feature that was trying to send the message.
            return Component.text(stripCodes(substitute(raw, placeholders)));
        }
    }

    /**
     * Parses a line destined for an item name or lore.
     *
     * <p>Item display text is italic by default in vanilla, and configs are
     * written expecting it not to be, so italics are switched off unless the
     * line explicitly asks for them.
     */
    public static Component parseItem(String raw, Map<String, String> placeholders) {
        Component component = parse(raw, placeholders);
        if (component.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            component = component.decoration(TextDecoration.ITALIC, false);
        }
        return component;
    }

    public static Component parseItem(String raw) {
        return parseItem(raw, Map.of());
    }

    public static List<Component> parseLore(List<String> raw, Map<String, String> placeholders) {
        List<Component> lore = new ArrayList<>(raw.size());
        for (String line : raw) {
            lore.add(parseItem(line, placeholders));
        }
        return lore;
    }

    public static List<Component> parseLore(List<String> raw) {
        return parseLore(raw, Map.of());
    }

    /** Flattens a component to readable text, for logs and console output. */
    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    /** Renders a configured string straight to plain text. */
    public static String plain(String raw, Map<String, String> placeholders) {
        return plain(parse(raw, placeholders));
    }

    public static String plain(String raw) {
        return plain(parse(raw));
    }

    // ------------------------------------------------------------------ internals

    /**
     * Resolves the private {@code uc_ph_*} tags that {@link #toMiniMessage}
     * rewrites placeholders into, inserting each value as literal text.
     */
    private static TagResolver resolverFor(Map<String, String> placeholders) {
        if (placeholders.isEmpty()) {
            return TagResolver.empty();
        }
        List<TagResolver> resolvers = new ArrayList<>(placeholders.size());
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String raw = entry.getValue() == null ? "" : entry.getValue();
            Component value = !raw.isEmpty() && raw.charAt(0) == FORMATTED
                    ? parse(raw.substring(1))
                    : Component.text(raw);
            resolvers.add(TagResolver.resolver(tagName(entry.getKey()),
                    (args, ctx) -> Tag.selfClosingInserting(value)));
        }
        return TagResolver.resolver(resolvers);
    }

    /** Rewrites legacy ampersand codes and {@code %placeholders%} into MiniMessage. */
    static String toMiniMessage(String raw) {
        String out = raw;

        // Hex first: &#rrggbb and &x&f&f&0&0&8&8 both become <#rrggbb>.
        Matcher hex = LEGACY_HEX.matcher(out);
        StringBuilder hexed = new StringBuilder();
        while (hex.find()) {
            String colour = hex.group(1);
            if (colour == null) {
                // The spread form repeats one capturing group six times, so only
                // its last match is retained; re-read the digits from the match.
                String matched = hex.group();
                StringBuilder digits = new StringBuilder(6);
                for (int index = 0; index < matched.length(); index++) {
                    char character = matched.charAt(index);
                    if (character != '&' && character != 'x' && character != 'X') {
                        digits.append(character);
                    }
                }
                colour = digits.toString();
            }
            hex.appendReplacement(hexed, "<#" + colour.toLowerCase(java.util.Locale.ROOT) + ">");
        }
        hex.appendTail(hexed);
        out = hexed.toString();

        Matcher code = LEGACY_CODE.matcher(out);
        StringBuilder coded = new StringBuilder();
        while (code.find()) {
            code.appendReplacement(coded, Matcher.quoteReplacement(tagFor(code.group(1).charAt(0))));
        }
        code.appendTail(coded);
        out = coded.toString();

        Matcher placeholder = PLACEHOLDER.matcher(out);
        StringBuilder placed = new StringBuilder();
        while (placeholder.find()) {
            placeholder.appendReplacement(placed, "<" + tagName(placeholder.group(1)) + "/>");
        }
        placeholder.appendTail(placed);
        return placed.toString();
    }

    /** MiniMessage tag names must be lowercase, so placeholder keys are folded. */
    private static String tagName(String key) {
        return "uc_ph_" + key.toLowerCase(java.util.Locale.ROOT);
    }

    private static String tagFor(char code) {
        char lower = Character.toLowerCase(code);
        int index = "0123456789abcdef".indexOf(lower);
        if (index >= 0) {
            return "<" + COLOURS[index] + ">";
        }
        return switch (lower) {
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> "";
        };
    }

    private static String substitute(String raw, Map<String, String> placeholders) {
        String out = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace("%" + entry.getKey() + "%",
                    entry.getValue() == null ? "" : entry.getValue());
        }
        return out.replace(String.valueOf(FORMATTED), "");
    }

    private static String stripCodes(String raw) {
        return LEGACY_CODE.matcher(LEGACY_HEX.matcher(raw).replaceAll("")).replaceAll("");
    }
}
