package poc.checkout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * <b>Responsibility:</b> let tests control time. Tests move it forward to make checkouts
 * "older than" reconcile-after or expire-after without waiting.
 */
public class MutableClock extends Clock {

    private volatile Instant now = Instant.now();

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }

    public void reset() {
        now = Instant.now();
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }
}
