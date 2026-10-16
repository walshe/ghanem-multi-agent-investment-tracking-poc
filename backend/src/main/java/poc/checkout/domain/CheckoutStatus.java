package poc.checkout.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Checkout statuses and the allowed status changes from contract/states.md. */
public enum CheckoutStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    EXPIRED;

    /** The "Allowed status changes" table. Every other change is not allowed. */
    private static final Map<CheckoutStatus, Set<CheckoutStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(PROCESSING, SUCCEEDED, FAILED, EXPIRED),
            PROCESSING, EnumSet.of(SUCCEEDED, FAILED));

    public boolean canChangeTo(CheckoutStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isFinal() {
        return this == SUCCEEDED || this == FAILED || this == EXPIRED;
    }
}
