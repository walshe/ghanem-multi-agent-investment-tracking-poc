package com.example.provider.payment;

/** PaymentStatus from contract/provider-api.yaml. */
public enum PaymentStatus {
    PENDING, PROCESSING, SUCCEEDED, FAILED, CANCELLED, REFUNDED
}
