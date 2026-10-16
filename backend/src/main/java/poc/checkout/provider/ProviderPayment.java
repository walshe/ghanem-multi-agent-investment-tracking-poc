package poc.checkout.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The provider's Payment schema (contract/provider-api.yaml). */
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
