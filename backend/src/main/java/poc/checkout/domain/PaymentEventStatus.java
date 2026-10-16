package poc.checkout.domain;

import java.util.Optional;

/**
 * <b>Responsibility:</b> the statuses a payment event can carry (PaymentEventStatus in
 * checkout-api.yaml), and how each one maps to a {@link CheckoutStatus}.
 *
 * <p>PENDING and EXPIRED are ours, never the provider's, so they are not here. The provider's own
 * PENDING, CANCELLED and REFUNDED have no counterpart, so {@link #parse} returns empty for them.
 */
public enum PaymentEventStatus {
    PROCESSING,
    SUCCEEDED,
    FAILED;

    public CheckoutStatus toCheckoutStatus() {
        return CheckoutStatus.valueOf(name());
    }

    /** Parses a status name. Also used for provider payment statuses, which have extra values (PENDING, CANCELLED, REFUNDED). */
    public static Optional<PaymentEventStatus> parse(String value) {
        for (PaymentEventStatus s : values()) {
            if (s.name().equals(value)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
