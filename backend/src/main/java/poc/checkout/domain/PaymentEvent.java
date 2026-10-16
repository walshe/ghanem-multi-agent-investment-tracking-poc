package poc.checkout.domain;

import java.time.Instant;

/** A payment event as stored in the audit trail (from a callback or from reconciliation). */
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
