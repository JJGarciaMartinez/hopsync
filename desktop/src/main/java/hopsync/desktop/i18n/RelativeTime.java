package hopsync.desktop.i18n;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * How long ago something happened, the way the player says it: "hace un momento", "hace 5 min", "hace 2 h",
 * "ayer a las 21:14", "el 3 nov". Pure: the current time is passed in.
 */
public final class RelativeTime {
    /** How often a shown "hace N min" is read again: often enough that it never lags a whole minute. */
    public static final int REFRESH_MS = 30_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"));

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
            return Messages.get("time.yesterday", TIME.withZone(zone).format(then));
        }
        // Spanish short months may end in a dot ("nov."); the sentence reads better without it
        return Messages.get("time.date", DAY.withZone(zone).format(then).replace(".", ""));
    }
}
