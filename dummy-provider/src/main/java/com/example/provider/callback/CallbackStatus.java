package com.example.provider.callback;

/** CallbackStatus from provider-api.yaml. Matches PaymentEventStatus in checkout-api.yaml. */
public enum CallbackStatus {
    PROCESSING, SUCCEEDED, FAILED
}
