package poc.checkout.callback;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import poc.checkout.callback.CallbackParser.Callback;
import poc.checkout.domain.Checkout;
import poc.checkout.domain.CheckoutStatus;
import poc.checkout.domain.EventOutcome;
import poc.checkout.domain.PaymentEvent;
import poc.checkout.persistence.CheckoutRepository;
import poc.checkout.persistence.PaymentEventRepository;
import poc.checkout.service.RefundService;
import poc.checkout.service.RetryingTransaction;

/**
 * Steps 2 to 5 of "Processing a callback" in contract/states.md, in one database transaction,
 * retried once on an optimistic-locking conflict. Step 1 (signature) is done by the controller
 * on the raw bytes before this is called.
 */
@Service
public class CallbackService {

    /** What happened to a callback. DUPLICATE events are not stored again. */
    public enum Result { APPLIED, IGNORED_STALE, FLAGGED, DUPLICATE }

    private static final Logger log = LoggerFactory.getLogger(CallbackService.class);

    private final CheckoutRepository checkouts;
    private final PaymentEventRepository events;
    private final RefundService refunds;
    private final RetryingTransaction transaction;
    private final Clock clock;

    public CallbackService(CheckoutRepository checkouts, PaymentEventRepository events, RefundService refunds,
                           RetryingTransaction transaction, Clock clock) {
        this.checkouts = checkouts;
        this.events = events;
        this.refunds = refunds;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * @throws org.springframework.dao.ConcurrencyFailureException if it conflicted twice
     * @throws poc.checkout.provider.ProviderException if a required refund could not be requested;
     *         nothing is stored, so the provider's retry processes the callback again
     */
    public Result process(Callback callback) {
        Result result = transaction.run("processing callback " + callback.eventId(), () -> processOnce(callback));
        log.info("Callback {} ({} for checkout {}): {}", callback.eventId(), callback.status(),
                callback.checkoutId(), result);
        return result;
    }

    private Result processOnce(Callback callback) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Optional<UUID> checkoutId = parseUuid(callback.checkoutId());

        // Step 2. Duplicate. The unique constraint on eventId decides. The outcome is
        // provisional here and corrected below, inside the same transaction.
        PaymentEvent event = new PaymentEvent(callback.eventId(),
                checkoutId.map(UUID::toString).orElse(callback.checkoutId()), callback.paymentId(),
                callback.status(), callback.amountMinor(), callback.currency(), callback.occurredAt(),
                EventOutcome.APPLIED, now);
        if (!events.insertIfNew(event)) {
            return Result.DUPLICATE;
        }

        Optional<Checkout> found = checkoutId.flatMap(checkouts::findById);
        if (found.isEmpty()) {
            log.warn("Callback {} is for unknown checkout {}. Flagged.", callback.eventId(), callback.checkoutId());
            return flag(callback);
        }
        Checkout checkout = found.get();

        // Step 3. Amount (and paymentId) must match the checkout.
        if (!checkout.matchesMoney(callback.amountMinor(), callback.currency())) {
            log.warn("Callback {} says {} {} but checkout {} is {} {}. Flagged.", callback.eventId(),
                    callback.amountMinor(), callback.currency(), checkout.id(), checkout.amountMinor(),
                    checkout.currency());
            return flag(callback);
        }
        if (checkout.paymentId() != null && !checkout.paymentId().equals(callback.paymentId())) {
            log.warn("Callback {} is for payment {} but checkout {} has payment {}. Flagged.", callback.eventId(),
                    callback.paymentId(), checkout.id(), checkout.paymentId());
            return flag(callback);
        }
        // We may never have received the provider's answer to POST /payments. Take the paymentId from here.
        boolean adoptPaymentId = checkout.paymentId() == null;
        Checkout withPayment = adoptPaymentId ? checkout.withPaymentId(callback.paymentId()) : checkout;

        // Step 4. Order. Apply only an allowed status change.
        CheckoutStatus target = callback.status().toCheckoutStatus();
        if (checkout.status().canChangeTo(target)) {
            checkouts.update(withPayment.withStatus(target), now);
            return Result.APPLIED;
        }
        events.setOutcome(callback.eventId(), EventOutcome.IGNORED_STALE);

        // Step 5. Late success: paid for an investment we did not make. Refund; status stays EXPIRED.
        if (checkout.status() == CheckoutStatus.EXPIRED && target == CheckoutStatus.SUCCEEDED) {
            refunds.requestRefundOnce(withPayment, now);
        } else if (adoptPaymentId) {
            checkouts.update(withPayment, now);
        }
        return Result.IGNORED_STALE;
    }

    private Result flag(Callback callback) {
        events.setOutcome(callback.eventId(), EventOutcome.FLAGGED);
        return Result.FLAGGED;
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
