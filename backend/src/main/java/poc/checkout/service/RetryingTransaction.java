package poc.checkout.service;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * <b>Responsibility:</b> the "retry once on conflict" rule from contract/states.md.
 *
 * <p>Runs work in one database transaction. If it loses an optimistic-locking race (another
 * transaction updated the same checkout first), everything is rolled back and the work runs once
 * more, so it re-reads fresh data. A second conflict is thrown to the caller.
 *
 * <p><b>Not responsible for:</b> what the work does. The work must re-read the checkout inside the
 * supplier, otherwise the retry would reuse stale data.
 */
@Component
public class RetryingTransaction {

    private static final Logger log = LoggerFactory.getLogger(RetryingTransaction.class);

    private final TransactionTemplate tx;

    public RetryingTransaction(TransactionTemplate tx) {
        this.tx = tx;
    }

    public <T> T run(String what, Supplier<T> work) {
        try {
            return tx.execute(status -> work.get());
        } catch (ConcurrencyFailureException firstConflict) {
            log.info("Conflict while {}, retrying once: {}", what, firstConflict.getMessage());
            return tx.execute(status -> work.get());
        }
    }
}
