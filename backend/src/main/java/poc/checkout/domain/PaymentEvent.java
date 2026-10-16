package poc.checkout.domain;

import java.time.Instant;

/**
 * <b>Responsibility:</b> represent one entry in the audit trail: a payment event and what was done
 * with it. The event came from a callback or from reconciliation, whose eventId is
 * {@code reconcile-{checkoutId}-{status}}.
 *
 * <p>Immutable. The eventId is the idempotency key: the database stores each eventId only once.
 */
public record PaymentEvent(
        String eventId,
        String checkoutId,
        String paymentId,
        PaymentEventStatus status,
        long amountMinor,
        String currency,
        Instant occurredAt,
        EventOutcome outcome,
        Instant receivedAt) {
}
