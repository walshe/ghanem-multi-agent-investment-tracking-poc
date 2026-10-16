package com.example.provider.payment;

import java.util.UUID;

/**
 * Responsibility: be the Payment response body from contract/provider-api.yaml.
 *
 * <p>An immutable snapshot of a {@link Payment}, taken under its lock. Leaves out internal fields
 * such as the callback URL and chaos settings.
 */
public record PaymentView(
        String paymentId,
        UUID checkoutId,
        PaymentStatus status,
        long amountMinor,
        String currency,
        int refundCount) {
}
