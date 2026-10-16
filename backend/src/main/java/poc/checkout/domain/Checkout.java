package poc.checkout.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A checkout as stored. Immutable: the "with" methods return a copy that still carries the
 * version it was read with, so the repository can do an optimistic-locking update.
 */
public record Checkout(
        UUID id,
        CheckoutStatus status,
        long amountMinor,
        String currency,
        String paymentId,
        boolean refundRequested,
        boolean cancelRefused,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static Checkout newPending(UUID id, long amountMinor, String currency, Instant now) {
        return new Checkout(id, CheckoutStatus.PENDING, amountMinor, currency, null, false, false, now, now, 0);
    }

    public Checkout withStatus(CheckoutStatus newStatus) {
        return new Checkout(id, newStatus, amountMinor, currency, paymentId, refundRequested, cancelRefused,
                createdAt, updatedAt, version);
    }

    public Checkout withPaymentId(String newPaymentId) {
        return new Checkout(id, status, amountMinor, currency, newPaymentId, refundRequested, cancelRefused,
                createdAt, updatedAt, version);
    }

    public Checkout withRefundRequested(boolean value) {
        return new Checkout(id, status, amountMinor, currency, paymentId, value, cancelRefused,
                createdAt, updatedAt, version);
    }

    public Checkout withCancelRefused(boolean value) {
        return new Checkout(id, status, amountMinor, currency, paymentId, refundRequested, value,
                createdAt, updatedAt, version);
    }

    public boolean matchesMoney(long otherAmountMinor, String otherCurrency) {
        return amountMinor == otherAmountMinor && currency.equals(otherCurrency);
    }
}
