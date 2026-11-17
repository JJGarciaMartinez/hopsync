package hopsync.desktop.i18n;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelativeTimeTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    private static String ago(Duration elapsed) {
        return RelativeTime.ago(NOW.minus(elapsed), NOW, ZoneOffset.UTC);
    }

    @Test
    void underAMinuteIsJustNow() {
        assertEquals(Messages.get("time.justNow"), ago(Duration.ZERO));
        assertEquals(Messages.get("time.justNow"), ago(Duration.ofSeconds(59)));
        // A check stamped a moment ahead of the clock still reads as just now
        assertEquals(Messages.get("time.justNow"), ago(Duration.ofSeconds(-3)));
    }

    @Test
    void minutesThenHoursWithinTheSameDay() {
        assertEquals(Messages.get("time.minutes", 1), ago(Duration.ofSeconds(60)));
        assertEquals(Messages.get("time.minutes", 59), ago(Duration.ofMinutes(59).plusSeconds(59)));
        assertEquals(Messages.get("time.hours", 1), ago(Duration.ofMinutes(60)));
        assertEquals(Messages.get("time.hours", 11), ago(Duration.ofHours(11).plusMinutes(59)));
    }

    @Test
    void yesterdayGivesTheTimeAndOlderGivesTheDate() {
        assertEquals(Messages.get("time.yesterday", "21:14"), ago(Duration.ofHours(14).plusMinutes(46)));
        assertEquals(Messages.get("time.date", "3 nov"),
            RelativeTime.ago(Instant.parse("2026-11-03T09:00:00Z"), NOW, ZoneOffset.UTC));
    }

    @Test
    void daysFollowTheLocalZone() {
        // 23:30 UTC the day before is already 00:30 today in Madrid
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        Instant then = Instant.parse("2026-11-12T23:30:00Z");
        assertEquals(Messages.get("time.yesterday", "23:30"), RelativeTime.ago(then, NOW, ZoneOffset.UTC));
        assertEquals(Messages.get("time.hours", 12), RelativeTime.ago(then, NOW, madrid));
    }
}
