package fr.elias.ultimateChristmas;

import fr.elias.ultimateChristmas.modules.events.infra.EventWindow;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventWindowTest {
    private final ZoneId paris = ZoneId.of("Europe/Paris");

    @Test
    void daytimeWindowUsesLocalDaysAndEndIsExclusive() {
        EventWindow window = new EventWindow("weekend", true, true, paris,
                LocalTime.of(18, 0), LocalTime.of(20, 0), Set.of(DayOfWeek.SATURDAY),
                null, null, List.of(), List.of());
        assertTrue(window.activeAt(Instant.parse("2026-11-21T18:00:00Z"), true));
        assertFalse(window.activeAt(Instant.parse("2026-11-21T19:00:00Z"), true));
        assertFalse(window.activeAt(Instant.parse("2026-11-21T18:00:00Z"), false));
    }

    @Test
    void overnightWindowUsesPreviousDayBeforeEnd() {
        EventWindow window = new EventWindow("night", true, false, paris,
                LocalTime.of(23, 0), LocalTime.of(2, 0), Set.of(DayOfWeek.SATURDAY),
                null, null, List.of(), List.of());
        assertTrue(window.activeAt(Instant.parse("2026-11-21T22:30:00Z"), true));
        assertTrue(window.activeAt(Instant.parse("2026-11-22T00:30:00Z"), true));
        assertFalse(window.activeAt(Instant.parse("2026-11-22T01:00:00Z"), true));
    }

    @Test
    void disablingWindowKeepsCalendarButPreventsActivation() {
        EventWindow window = new EventWindow("test", true, false, paris,
                LocalTime.of(18, 0), LocalTime.of(20, 0), Set.of(DayOfWeek.SATURDAY),
                null, null, List.of(), List.of());
        assertTrue(window.activeAt(Instant.parse("2026-11-21T18:00:00Z"), true));
        assertFalse(window.withEnabled(false).activeAt(
                Instant.parse("2026-11-21T18:00:00Z"), true));
    }

    @Test
    void absoluteWindowIsDateBounded() {
        EventWindow window = new EventWindow("eve", true, false, paris,
                null, null, Set.of(), Instant.parse("2026-12-24T17:00:00Z"),
                Instant.parse("2026-12-24T19:00:00Z"), List.of(), List.of());
        assertFalse(window.activeAt(Instant.parse("2026-12-24T16:59:59Z"), true));
        assertTrue(window.activeAt(Instant.parse("2026-12-24T17:00:00Z"), true));
        assertFalse(window.activeAt(Instant.parse("2026-12-24T19:00:00Z"), true));
    }
}
