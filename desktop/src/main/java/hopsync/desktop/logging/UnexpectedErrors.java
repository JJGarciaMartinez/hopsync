package hopsync.desktop.logging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Catches what no code expected, on any thread (the EDT and the virtual threads included): writes it to the log
 * with its stack trace, then tells the player through {@link #onNotice}. The notice waits {@link #QUIET} between
 * two showings, so an error that repeats, such as one while painting, cannot flood the window.
 */
public final class UnexpectedErrors implements Thread.UncaughtExceptionHandler {
    static final Duration QUIET = Duration.ofSeconds(30);
    private static final System.Logger LOG = System.getLogger(UnexpectedErrors.class.getName());

    private final Clock clock;
    private volatile Runnable notice = () -> { };
    private Instant lastNotice;

    public UnexpectedErrors(Clock clock) {
        this.clock = clock;
    }

    /** What tells the player; until the window exists, errors only go to the log. */
    public void onNotice(Runnable notice) {
        this.notice = notice;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
        LOG.log(System.Logger.Level.ERROR, "Unexpected error in thread " + thread.getName(), error);
        if (!dueForNotice()) return;
        try {
            notice.run();
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Could not tell the player about an unexpected error", e);
        }
    }

    private synchronized boolean dueForNotice() {
        Instant now = clock.instant();
        if (lastNotice != null && now.isBefore(lastNotice.plus(QUIET))) return false;
        lastNotice = now;
        return true;
    }
}
