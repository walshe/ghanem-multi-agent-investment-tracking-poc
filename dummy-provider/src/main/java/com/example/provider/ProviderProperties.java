package com.example.provider;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Responsibility: hold the provider's typed, validated configuration ({@code provider.*}).
 *
 * <p>The defaults in application.yml are the timings from contract/provider-api.yaml. Startup
 * fails if the signing secret is missing. {@link #toString()} never prints the secret.
 *
 * @param signingSecret   HMAC key for callbacks, from PROVIDER_SIGNING_SECRET
 * @param finalAfter      time from PROCESSING to SUCCEEDED/FAILED (contract: 1 second)
 * @param retryInterval   gap between callback retries (contract: 2 seconds)
 * @param maxRetries      retries after the first attempt (contract: 3)
 * @param callbackTimeout how long to wait for the backend to answer one callback
 */
@Validated
@ConfigurationProperties(prefix = "provider")
public record ProviderProperties(
        @NotBlank String signingSecret,
        @NotNull Duration finalAfter,
        @NotNull Duration retryInterval,
        @Min(0) int maxRetries,
        @NotNull Duration callbackTimeout) {

    @Override
    public String toString() {
        // Never log the secret.
        return "ProviderProperties[finalAfter=" + finalAfter + ", retryInterval=" + retryInterval
                + ", maxRetries=" + maxRetries + ", callbackTimeout=" + callbackTimeout + "]";
    }
}
