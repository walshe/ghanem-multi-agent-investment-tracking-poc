package poc.checkout.service;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs work in one database transaction and, if it loses an optimistic-locking race
 * (another transaction updated the same checkout first), rolls back and retries once
 * with fresh data, as contract/states.md requires. A second conflict is thrown to the caller.
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
