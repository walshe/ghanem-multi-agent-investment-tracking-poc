package com.example.provider.api;

import com.example.provider.callback.CallbackStatus;
import com.example.provider.callback.ManualCallbackService.CopyResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;

/**
 * Responsibility: define the HTTP request and response bodies from contract/provider-api.yaml,
 * with their validation rules.
 *
 * <p>Plain data only. The Payment response body is
 * {@link com.example.provider.payment.PaymentView}, and the Chaos body is
 * {@link com.example.provider.chaos.Chaos}.
 */
public final class ApiModels {

    private ApiModels() {
    }

    public record CreatePaymentRequest(
            @NotNull UUID checkoutId,
            @NotNull Long amountMinor,
            @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotBlank String callbackUrl) {
    }

    public record SendCallbackRequest(
            @NotBlank String paymentId,
            @NotNull CallbackStatus status,
            String eventId,
            Long amountMinor,
            Boolean validSignature,
            @Min(1) @Max(50) Integer copies) {
    }

    public record SendCallbackResult(List<CopyResult> results) {
    }
}
