package poc.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import poc.checkout.service.RetryingTransaction;

/** states.md: "Retry once on conflict." */
class RetryingTransactionTest extends IntegrationTestBase {

    @Autowired
    RetryingTransaction transaction;

    @Test
    void retriesOnceAfterAConflict() {
        AtomicInteger attempts = new AtomicInteger();

        String result = transaction.run("test", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new OptimisticLockingFailureException("lost the race");
            }
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void givesUpAfterTheSecondConflict() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> transaction.run("test", () -> {
            attempts.incrementAndGet();
            throw new OptimisticLockingFailureException("lost the race");
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(attempts).hasValue(2);
    }

    @Test
    void conflictRollsBackEverythingTheFirstAttemptWrote() {
        AtomicInteger attempts = new AtomicInteger();

        transaction.run("test", () -> {
            jdbc.update("INSERT INTO payment_event (event_id, checkout_id, status, amount_minor, currency, outcome, "
                    + "received_at) VALUES (?, 'c', 'PROCESSING', 100, 'EUR', 'APPLIED', CURRENT_TIMESTAMP)",
                    "attempt-" + attempts.incrementAndGet());
            if (attempts.get() == 1) {
                throw new OptimisticLockingFailureException("lost the race");
            }
            return null;
        });

        assertThat(jdbc.queryForList("SELECT event_id FROM payment_event", String.class))
                .containsExactly("attempt-2");
    }
}
