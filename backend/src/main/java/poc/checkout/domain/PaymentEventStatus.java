package poc.checkout.domain;

import java.util.Optional;

/** Statuses the provider can report in a callback. PENDING and EXPIRED are ours, never the provider's. */
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
