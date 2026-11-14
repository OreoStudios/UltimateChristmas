package fr.elias.ultimateChristmas.core.reward;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A fixed number or an inclusive range, as written in config.
 *
 * <p>Accepts {@code 5}, {@code "5"}, {@code "5-10"} and {@code "5..10"}, so a
 * reward can hand out a varying amount without a second config key.
 */
public record Amount(long min, long max) {

    public static final Amount ZERO = new Amount(0, 0);

    public Amount {
        if (max < min) {
            long swap = min;
            min = max;
            max = swap;
        }
    }

    public static Amount fixed(long value) {
        return new Amount(value, value);
    }

    /**
     * A range: two integers joined by {@code -} or {@code ..}. The optional sign
     * on each bound is matched explicitly so {@code -5--1} and {@code -5..-1}
     * both read as a negative range rather than a failed split.
     */
    private static final java.util.regex.Pattern RANGE =
            java.util.regex.Pattern.compile("(-?\\d+)\\s*(?:\\.\\.|-)\\s*(-?\\d+)");

    /** Parses a config value, returning {@link #ZERO} for anything unreadable. */
    public static Amount parse(Object raw) {
        if (raw == null) {
            return ZERO;
        }
        if (raw instanceof Number number) {
            return fixed(number.longValue());
        }
        String text = raw.toString().trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return ZERO;
        }
        java.util.regex.Matcher range = RANGE.matcher(text);
        if (range.matches()) {
            try {
                return new Amount(Long.parseLong(range.group(1)), Long.parseLong(range.group(2)));
            } catch (NumberFormatException ignored) {
                return ZERO;
            }
        }
        try {
            return fixed(Long.parseLong(text));
        } catch (NumberFormatException ex) {
            return ZERO;
        }
    }

    /** A value for this grant: the fixed number, or a uniform roll in range. */
    public long roll() {
        if (min == max) {
            return min;
        }
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    public boolean isZero() {
        return min == 0 && max == 0;
    }

    @Override
    public String toString() {
        return min == max ? String.valueOf(min) : min + "-" + max;
    }
}
