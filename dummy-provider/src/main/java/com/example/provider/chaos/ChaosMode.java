package com.example.provider.chaos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Responsibility: list the chaos modes, with JSON names exactly as in the contract.
 *
 * <p>What each mode does is implemented in
 * {@link com.example.provider.payment.PaymentService} (lifecycle and which callbacks are sent).
 */
public enum ChaosMode {
    /** Send every callback twice. */
    @JsonProperty("duplicate") DUPLICATE,
    /** Send the final callback (SUCCEEDED or FAILED) before PROCESSING. */
    @JsonProperty("reverse") REVERSE,
    /** Send no callbacks automatically. The payment still completes. */
    @JsonProperty("drop") DROP,
    /** Sign callbacks with the wrong secret. */
    @JsonProperty("badSignature") BAD_SIGNATURE,
    /** The payment ends FAILED instead of SUCCEEDED. */
    @JsonProperty("fail") FAIL,
    /** Cancel requests get 409 and the payment still completes. */
    @JsonProperty("cancelTooLate") CANCEL_TOO_LATE
}
