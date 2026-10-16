package com.example.provider.callback;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.UUID;

/**
 * The callback body: PaymentEvent from contract/checkout-api.yaml.
 * occurredAt is an ISO-8601 UTC date-time string, e.g. 2026-10-16T10:21:00.123Z.
 */
@JsonPropertyOrder({"eventId", "checkoutId", "paymentId", "status", "amountMinor", "currency", "occurredAt"})
public record PaymentEvent(
        String eventId,
        UUID checkoutId,
        String paymentId,
        CallbackStatus status,
        long amountMinor,
        String currency,
        String occurredAt) {
}
