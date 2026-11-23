package fr.elias.ultimateChristmas.modules.events.infra;

import org.bukkit.configuration.ConfigurationSection;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Absolute or recurring daily calendar window. Recurrences whose end precedes
 * start cross midnight, with weekday matching the START day.
 */
public record EventWindow(String id, boolean enabled, boolean seasonOnly, ZoneId timezone,
                          LocalTime startTime, LocalTime endTime, Set<DayOfWeek> days,
                          Instant startsAt, Instant endsAt,
                          List<String> startCommands, List<String> endCommands) {

    public EventWindow {
        days = Set.copyOf(days);
        startCommands = List.copyOf(startCommands);
        endCommands = List.copyOf(endCommands);
    }

    public boolean activeAt(Instant now, boolean seasonActive) {
        if (!enabled || (seasonOnly && !seasonActive)) return false;
        if (startsAt != null && endsAt != null) {
            return !now.isBefore(startsAt) && now.isBefore(endsAt);
        }
        if (startTime == null || endTime == null || startTime.equals(endTime)) return false;
        ZonedDateTime time = now.atZone(timezone);
        LocalTime clock = time.toLocalTime();
        if (startTime.isBefore(endTime)) {
            return days.contains(time.getDayOfWeek())
                    && !clock.isBefore(startTime) && clock.isBefore(endTime);
        }
        return (days.contains(time.getDayOfWeek()) && !clock.isBefore(startTime))
                || (days.contains(time.minusDays(1).getDayOfWeek()) && clock.isBefore(endTime));
    }

    public EventWindow withEnabled(boolean newEnabled) {
        return new EventWindow(id, newEnabled, seasonOnly, timezone,
                startTime, endTime, days, startsAt, endsAt, startCommands, endCommands);
    }

    public String description() {
        if (startsAt != null) return startsAt + " to " + endsAt;
        return startTime + "-" + endTime + " " + timezone.getId() + " (" + days + ")";
    }

    /** Invalid schedules are excluded rather than accidentally running indefinitely. */
    public static EventWindow read(String id, ConfigurationSection config,
                                   ZoneId defaultZone, Logger logger) {
        try {
            ZoneId zone = ZoneId.of(config.getString("timezone", defaultZone.getId()));
            boolean enabled = config.getBoolean("enabled", false);
            boolean seasonOnly = config.getBoolean("season-only", true);
            Set<DayOfWeek> days = EnumSet.allOf(DayOfWeek.class);
            if (config.contains("days")) {
                days = EnumSet.noneOf(DayOfWeek.class);
                for (String name : config.getStringList("days")) {
                    days.add(DayOfWeek.valueOf(name.toUpperCase(Locale.ROOT)));
                }
            }
            String absoluteStart = config.getString("start-at", "");
            String absoluteEnd = config.getString("end-at", "");
            Instant from = null;
            Instant until = null;
            LocalTime start = null;
            LocalTime end = null;
            if (!absoluteStart.isBlank() || !absoluteEnd.isBlank()) {
                if (absoluteStart.isBlank() || absoluteEnd.isBlank()) {
                    throw new IllegalArgumentException("start-at and end-at must both be set");
                }
                from = parseDateTime(absoluteStart, zone);
                until = parseDateTime(absoluteEnd, zone);
                if (!until.isAfter(from)) throw new IllegalArgumentException("end-at must follow start-at");
            } else {
                start = LocalTime.parse(config.getString("start-time", ""));
                end = LocalTime.parse(config.getString("end-time", ""));
                if (start.equals(end)) throw new IllegalArgumentException("zero-length daily window");
                if (days.isEmpty()) throw new IllegalArgumentException("days cannot be empty");
            }
            return new EventWindow(id, enabled, seasonOnly, zone, start, end, days,
                    from, until, config.getStringList("start-commands"),
                    config.getStringList("end-commands"));
        } catch (RuntimeException ex) {
            logger.warning("events.yml: skipping event '" + id + "': " + ex.getMessage());
            return null;
        }
    }

    private static Instant parseDateTime(String value, ZoneId zone) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (java.time.format.DateTimeParseException ignored) {
            return LocalDateTime.parse(value).atZone(zone).toInstant();
        }
    }
}
