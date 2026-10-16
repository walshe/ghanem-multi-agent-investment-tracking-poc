package poc.checkout.provider;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import poc.checkout.config.ProviderProperties;

/**
 * <b>Responsibility:</b> talk HTTP to the payment provider (contract/provider-api.yaml): create,
 * get, cancel and refund a payment.
 *
 * <p>Sets the Idempotency-Key headers, so retries are always safe: {@code {checkoutId}} when creating
 * a payment, {@code refund-{checkoutId}} when refunding. Translates HTTP results into Java: 409 on
 * cancel becomes {@link CancelResult#REFUSED}; everything else that fails becomes a
 * {@link ProviderException}.
 *
 * <p><b>Not responsible for:</b> deciding when to call the provider or what to do with the answer
 * (the services and the job).
 */
@Component
public class ProviderClient {

    public enum CancelResult {
        /** 200: cancelled (or already cancelled). */
        CANCELLED,
        /** 409: too late to cancel. The payment may still complete. */
        REFUSED
    }

    private record CreatePaymentRequest(String checkoutId, long amountMinor, String currency, String callbackUrl) {
    }

    private final RestClient http;

    public ProviderClient(ProviderProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.http = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** POST /payments with {@code Idempotency-Key: {checkoutId}}. Retrying with the same key never creates a second payment. */
    public ProviderPayment createPayment(UUID checkoutId, long amountMinor, String currency, String callbackUrl) {
        try {
            return requireBody(http.post()
                    .uri("/payments")
                    .header("Idempotency-Key", checkoutId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreatePaymentRequest(checkoutId.toString(), amountMinor, currency, callbackUrl))
                    .retrieve()
                    .body(ProviderPayment.class), "create payment for checkout " + checkoutId);
        } catch (RestClientException e) {
            throw new ProviderException("Could not create payment for checkout " + checkoutId, e);
        }
    }

    /** GET /payments/{paymentId}. */
    public ProviderPayment getPayment(String paymentId) {
        try {
            return requireBody(http.get()
                    .uri("/payments/{paymentId}", paymentId)
                    .retrieve()
                    .body(ProviderPayment.class), "get payment " + paymentId);
        } catch (RestClientException e) {
            throw new ProviderException("Could not get payment " + paymentId, e);
        }
    }

    /** POST /payments/{paymentId}/cancel. */
    public CancelResult cancelPayment(String paymentId) {
        try {
            http.post()
                    .uri("/payments/{paymentId}/cancel", paymentId)
                    .retrieve()
                    .toBodilessEntity();
            return CancelResult.CANCELLED;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                return CancelResult.REFUSED;
            }
            throw new ProviderException("Could not cancel payment " + paymentId, e);
        } catch (RestClientException e) {
            throw new ProviderException("Could not cancel payment " + paymentId, e);
        }
    }

    /**
     * POST /payments/{paymentId}/refund with {@code Idempotency-Key: refund-{checkoutId}}.
     * The same key always gives the same refund, so retrying can never refund twice.
     */
    public void refundPayment(String paymentId, UUID checkoutId) {
        try {
            http.post()
                    .uri("/payments/{paymentId}/refund", paymentId)
                    .header("Idempotency-Key", "refund-" + checkoutId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new ProviderException("Could not refund payment " + paymentId + " for checkout " + checkoutId, e);
        }
    }

    private static ProviderPayment requireBody(ProviderPayment payment, String what) {
        if (payment == null || payment.paymentId() == null || payment.status() == null) {
            throw new ProviderException("Provider sent no usable payment for: " + what);
        }
        return payment;
    }
}
