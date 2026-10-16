package com.example.provider.callback;

/**
 * Responsibility: list the statuses a callback can report.
 *
 * <p>CallbackStatus from provider-api.yaml, which must match PaymentEventStatus in
 * checkout-api.yaml. Deliberately narrower than
 * {@link com.example.provider.payment.PaymentStatus}: PENDING, CANCELLED and REFUNDED are never
 * sent as callbacks.
 */
public enum CallbackStatus {
    PROCESSING, SUCCEEDED, FAILED
}
