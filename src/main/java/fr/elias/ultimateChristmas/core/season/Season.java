package fr.elias.ultimateChristmas.core.season;

import fr.elias.ultimateChristmas.core.config.ConfigFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

/**
 * The season clock: one answer to "what day of Christmas is it?".
 *
 * <p>The 3.x code worked this out in three places with three different notions
 * of a day boundary — the advent calendar used day-of-month, the claim cooldown
 * used an epoch day in one timezone, and the calendar index used another — so a
 * player could be on "day 4" and "day 12" at the same time. Everything now
 * reads from here.
 */
public final class Season {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ConfigFile config;
    private final Logger logger;

    private ZoneId zone = ZoneId.systemDefault();
    private int resetHour;
    private LocalDate start;
    private int length = 25;
    private LocalDate christmasDay;

    public Season(ConfigFile config, Logger logger) {
        this.config = config;
        this.logger = logger;
        reload();
    }

    public void reload() {
        String zoneId = config.getString("season.timezone", "UTC");
        try {
            zone = ZoneId.of(zoneId);
        } catch (RuntimeException ex) {
            logger.warning("Unknown timezone '" + zoneId + "' in config.yml; using UTC.");
            zone = ZoneId.of("UTC");
        }

        resetHour = Math.max(0, Math.min(23, config.getInt("season.reset-hour", 0)));
        length = Math.max(1, config.getInt("season.length-days", 25));
        start = parseDate(config.getString("season.start", null), "season.start");
        christmasDay = parseDate(config.getString("season.christmas-day", null),
                "season.christmas-day");

        if (start == null) {
            // Default to 1 December of the season we are in or heading into. In
            // January that is last December, so the calendar stays claimable
            // through the end of the holidays instead of jumping 11 months.
            LocalDate reference = LocalDate.now(zone);
            int year = reference.getMonthValue() == 1
                    ? reference.getYear() - 1
                    : reference.getYear();
            start = LocalDate.of(year, 12, 1);
            logger.info("season.start is unset; defaulting to " + start + ".");
        }
        if (christmasDay == null) {
            christmasDay = LocalDate.of(start.getYear(), 12, 25);
        }
    }

    private LocalDate parseDate(String raw, String path) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), ISO);
        } catch (DateTimeParseException ex) {
            logger.warning("Unreadable date '" + raw + "' at " + path
                    + "; expected yyyy-MM-dd.");
            return null;
        }
    }

    // ------------------------------------------------------------------ clock

    public ZoneId zone() {
        return zone;
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(zone);
    }

    /**
     * Today's date, shifted so the day rolls over at the configured reset hour
     * rather than at midnight. With {@code reset-hour: 5}, 03:00 still counts as
     * the previous day.
     */
    public LocalDate today() {
        ZonedDateTime moment = now();
        return moment.getHour() < resetHour
                ? moment.toLocalDate().minusDays(1)
                : moment.toLocalDate();
    }

    /** {@link #today()} as an epoch day, the key used for streak arithmetic. */
    public long todayEpochDay() {
        return today().toEpochDay();
    }

    /** When the current day rolls over. */
    public ZonedDateTime nextReset() {
        ZonedDateTime moment = now();
        ZonedDateTime resetToday = moment.withHour(resetHour)
                .withMinute(0).withSecond(0).withNano(0);
        return moment.isBefore(resetToday) ? resetToday : resetToday.plusDays(1);
    }

    public Duration untilReset() {
        return Duration.between(now(), nextReset());
    }

    // ----------------------------------------------------------------- season

    public LocalDate start() {
        return start;
    }

    public LocalDate end() {
        return start.plusDays(length - 1L);
    }

    public int length() {
        return length;
    }

    /**
     * Which day of the season today is, 1-based.
     *
     * @return 1..{@link #length()} inside the season, or -1 outside it
     */
    public int currentDay() {
        long elapsed = ChronoUnit.DAYS.between(start, today());
        int day = (int) elapsed + 1;
        return (day < 1 || day > length) ? -1 : day;
    }

    /** Whether today falls inside the configured season window. */
    public boolean active() {
        return currentDay() > 0;
    }

    /** The date a given season day falls on. */
    public LocalDate dateOfDay(int day) {
        return start.plusDays(day - 1L);
    }

    /** Whether a season day is in the past, and so claimable as a catch-up. */
    public boolean dayHasPassed(int day) {
        return dateOfDay(day).isBefore(today());
    }

    /** Whether a season day is still in the future. */
    public boolean dayIsFuture(int day) {
        return dateOfDay(day).isAfter(today());
    }

    // -------------------------------------------------------------- christmas

    public LocalDate christmasDay() {
        return christmasDay;
    }

    /** Whether today is Christmas Day. */
    public boolean isChristmas() {
        return today().equals(christmasDay);
    }

    /** Whole days until Christmas; 0 on the day, negative once it has passed. */
    public long daysUntilChristmas() {
        return ChronoUnit.DAYS.between(today(), christmasDay);
    }

    /** Time until midnight opening Christmas Day, for the countdown bar. */
    public Duration untilChristmas() {
        ZonedDateTime target = christmasDay.atStartOfDay(zone);
        return Duration.between(now(), target);
    }

    // --------------------------------------------------------------- display

    /**
     * Formats a duration for display, showing the two largest useful units:
     * {@code 3d 4h}, {@code 4h 12m}, {@code 12m 30s} or {@code 30s}.
     */
    public static String format(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "0s";
        }
        long days = duration.toDays();
        if (days > 0) {
            return days + "d " + duration.toHoursPart() + "h";
        }
        long hours = duration.toHours();
        if (hours > 0) {
            return hours + "h " + duration.toMinutesPart() + "m";
        }
        long minutes = duration.toMinutes();
        if (minutes > 0) {
            return minutes + "m " + duration.toSecondsPart() + "s";
        }
        return duration.toSecondsPart() + "s";
    }

    @Override
    public String toString() {
        return "Season[" + start + " .. " + end() + " day=" + currentDay()
                + " zone=" + zone + " reset=" + resetHour + ":00]";
    }

    /** Exposed for the test suite, which needs a season anchored to a fixed day. */
    LocalDateTime debugNow() {
        return LocalDateTime.now(zone);
    }
}
