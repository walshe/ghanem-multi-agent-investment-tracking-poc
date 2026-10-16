package com.example.provider.payment;

import java.util.UUID;

/** Payment schema from contract/provider-api.yaml. */
public record PaymentView(
        String paymentId,
        UUID checkoutId,
        PaymentStatus status,
        long amountMinor,
        String currency,
        int refundCount) {
}
