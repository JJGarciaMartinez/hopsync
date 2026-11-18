package hopsync.desktop.i18n;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * How long ago something happened, the way the player says it: "hace un momento", "hace 5 min", "hace 2 h",
 * "ayer a las 21:14", "el 3 nov" (or "just now", "5 min ago", "yesterday at 9:14 PM", "on Nov 3"). Pure: the
 * current time is passed in.
 */
public final class RelativeTime {
    /** How often a shown "hace N min" is read again: often enough that it never lags a whole minute. */
    public static final int REFRESH_MS = 30_000;

    private RelativeTime() {
    }

    public static String ago(Instant then, Instant now, ZoneId zone) {
        Duration elapsed = Duration.between(then, now);
        // A clock a little behind the event still reads as "just now"
        if (elapsed.toSeconds() < 60) {
            return Messages.get("time.justNow");
        }
        if (elapsed.toMinutes() < 60) {
            return Messages.get("time.minutes", elapsed.toMinutes());
        }
        LocalDate day = then.atZone(zone).toLocalDate();
        LocalDate today = now.atZone(zone).toLocalDate();
        if (day.equals(today)) {
            return Messages.get("time.hours", elapsed.toHours());
        }
        if (day.equals(today.minusDays(1))) {
            return Messages.get("time.yesterday", format("time.pattern.clock", then, zone));
        }
        // Spanish short months may end in a dot ("nov."); the sentence reads better without it
        return Messages.get("time.date", format("time.pattern.day", then, zone).replace(".", ""));
    }

    /** @param patternKey a {@link DateTimeFormatter} pattern in the texts, since each language orders it its way */
    private static String format(String patternKey, Instant then, ZoneId zone) {
        return DateTimeFormatter.ofPattern(Messages.get(patternKey), Messages.locale()).withZone(zone).format(then);
    }
}
