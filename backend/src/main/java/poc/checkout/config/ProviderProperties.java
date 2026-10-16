package poc.checkout.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <b>Responsibility:</b> hold the payment provider settings from application.yml (prefix
 * {@code provider}): where it is, how long to wait for it, and the shared signing secret.
 * {@link #toString()} leaves the secret out so it can never end up in a log.
 *
 * @param baseUrl      the payment provider, e.g. http://localhost:8081
 * @param signingSecret shared HMAC secret, from the PROVIDER_SIGNING_SECRET environment variable
 */
@ConfigurationProperties("provider")
public record ProviderProperties(
        String baseUrl,
        String signingSecret,
        Duration connectTimeout,
        Duration readTimeout) {

    @Override
    public String toString() {
        // Never log the secret.
        return "ProviderProperties[baseUrl=" + baseUrl + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
