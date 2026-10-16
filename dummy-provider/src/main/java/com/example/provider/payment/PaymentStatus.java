package com.example.provider.payment;

/**
 * Responsibility: list every status a payment can have inside the provider.
 *
 * <p>PaymentStatus from contract/provider-api.yaml. Only PROCESSING, SUCCEEDED and FAILED are
 * ever sent as callbacks (see {@link com.example.provider.callback.CallbackStatus}).
 */
public enum PaymentStatus {
    PENDING, PROCESSING, SUCCEEDED, FAILED, CANCELLED, REFUNDED
}
