package poc.checkout.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Timings and settings from contract/states.md ("Late and missing callbacks").
 *
 * @param callbackUrl        sent to the provider as the payment's callbackUrl
 * @param reconcileAfter     a non-final checkout older than this is reconciled with the provider
 * @param expireAfter        a PENDING checkout older than this is expired
 * @param jobInterval        delay between runs of the scheduled job
 * @param jobEnabled         turn the scheduled job off (tests call it directly)
 * @param corsAllowedOrigins origins of the web and mobile apps
 */
@ConfigurationProperties("checkout")
public record CheckoutProperties(
        String callbackUrl,
        Duration reconcileAfter,
        Duration expireAfter,
        Duration jobInterval,
        boolean jobEnabled,
        List<String> corsAllowedOrigins) {
}
