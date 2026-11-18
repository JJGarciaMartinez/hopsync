package hopsync.desktop.logging;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnexpectedErrorsTest {
    private Instant now = Instant.parse("2026-11-17T12:00:00Z");
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    @Test
    void thePlayerIsToldOnceWhileErrorsRepeat() {
        AtomicInteger notices = new AtomicInteger();
        UnexpectedErrors errors = new UnexpectedErrors(clock);
        errors.onNotice(notices::incrementAndGet);

        errors.uncaughtException(Thread.currentThread(), new IllegalStateException("first"));
        errors.uncaughtException(Thread.currentThread(), new IllegalStateException("again"));
        assertEquals(1, notices.get());

        now = now.plus(UnexpectedErrors.QUIET);
        errors.uncaughtException(Thread.currentThread(), new IllegalStateException("later"));
        assertEquals(2, notices.get());
    }

    @Test
    void aFailingNoticeIsOnlyLogged() {
        UnexpectedErrors errors = new UnexpectedErrors(clock);
        errors.onNotice(() -> {
            throw new IllegalStateException("no window");
        });

        errors.uncaughtException(Thread.currentThread(), new IllegalStateException("boom"));
    }

    @Test
    void beforeTheWindowExistsErrorsOnlyGoToTheLog() {
        new UnexpectedErrors(clock).uncaughtException(Thread.currentThread(), new IllegalStateException("early"));
    }
}
