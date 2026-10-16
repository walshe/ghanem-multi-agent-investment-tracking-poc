package poc.checkout.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * <b>Responsibility:</b> represent the provider's view of a payment (the Payment schema in
 * contract/provider-api.yaml), and say whether its status is final.
 *
 * <p>The status is kept as the provider's raw string, because the provider has statuses we don't
 * (PENDING, CANCELLED, REFUNDED).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderPayment(
        String paymentId,
        String checkoutId,
        String status,
        long amountMinor,
        String currency,
        int refundCount) {

    /** Provider statuses after which nothing changes any more. */
    public boolean isFinal() {
        return switch (status) {
            case "SUCCEEDED", "FAILED", "CANCELLED", "REFUNDED" -> true;
            default -> false;
        };
    }
}
