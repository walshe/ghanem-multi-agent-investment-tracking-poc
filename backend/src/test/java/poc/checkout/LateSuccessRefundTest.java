package poc.checkout;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** states.md, callback step 5 (Late success) and "Refunds". */
class LateSuccessRefundTest extends IntegrationTestBase {

    @Test
    void successAfterExpiryRequestsExactlyOneRefund() {
        String checkoutId = expiredCheckout();
        String paymentId = paymentIdFor(checkoutId);
        stubRefund(paymentId, 200);
        String lateSuccess = newEventId();

        HttpResponse<String> response = sendCallback(lateSuccess, checkoutId, "SUCCEEDED");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode checkout = checkout(checkoutId);
        assertThat(checkout.get("status").stringValue()).isEqualTo("EXPIRED");
        assertThat(checkout.get("refundRequested").booleanValue()).isTrue();
        provider.verify(1, postRequestedFor(urlPathEqualTo("/payments/" + paymentId + "/refund"))
                .withHeader("Idempotency-Key", equalTo("refund-" + checkoutId)));

        JsonNode events = events(checkoutId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("eventId").stringValue()).isEqualTo(lateSuccess);
        assertThat(events.get(0).get("outcome").stringValue()).isEqualTo("IGNORED_STALE");
    }

    @Test
    void repeatedLateSuccessesFromEveryPathStillRequestExactlyOneRefund() {
        String checkoutId = expiredCheckout();
        String paymentId = paymentIdFor(checkoutId);
        stubRefund(paymentId, 200);
        String eventId = newEventId();

        sendCallback(eventId, checkoutId, "SUCCEEDED");
        sendCallback(eventId, checkoutId, "SUCCEEDED");      // duplicate
        sendCallback(newEventId(), checkoutId, "SUCCEEDED"); // a second, different success event
        job.runOnce();
        job.runOnce();

        provider.verify(1, postRequestedFor(urlPathMatching("/payments/.*/refund")));
        assertThat(status(checkoutId)).isEqualTo("EXPIRED");
    }

    @Test
    void lateSuccessesArrivingAtOnceRequestExactlyOneRefund() throws Exception {
        String checkoutId = expiredCheckout();
        String paymentId = paymentIdFor(checkoutId);
        stubRefund(paymentId, 200);

        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                String eventId = newEventId();
                futures.add(pool.submit(() -> {
                    go.await();
                    return sendCallback(eventId, checkoutId, "SUCCEEDED").statusCode();
                }));
            }
            go.countDown();
            for (Future<Integer> future : futures) {
                future.get();
            }
        }

        provider.verify(1, postRequestedFor(urlPathMatching("/payments/.*/refund")));
        assertThat(checkout(checkoutId).get("refundRequested").booleanValue()).isTrue();
        assertThat(status(checkoutId)).isEqualTo("EXPIRED");
    }

    @Test
    void failedRefundRequestStoresNothingSoTheProviderRetries() {
        String checkoutId = expiredCheckout();
        String paymentId = paymentIdFor(checkoutId);
        stubRefund(paymentId, 503);
        String lateSuccess = newEventId();

        HttpResponse<String> first = sendCallback(lateSuccess, checkoutId, "SUCCEEDED");

        assertThat(first.statusCode()).isEqualTo(500);
        assertThat(events(checkoutId)).isEmpty();
        assertThat(checkout(checkoutId).get("refundRequested").booleanValue()).isFalse();

        // Provider retries the same callback once the refund works.
        stubRefund(paymentId, 200);
        HttpResponse<String> retry = sendCallback(lateSuccess, checkoutId, "SUCCEEDED");

        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(checkout(checkoutId).get("refundRequested").booleanValue()).isTrue();
        assertThat(events(checkoutId)).hasSize(1);
        // Same Idempotency-Key both times, so the provider can only ever refund once.
        provider.verify(2, postRequestedFor(urlPathEqualTo("/payments/" + paymentId + "/refund"))
                .withHeader("Idempotency-Key", equalTo("refund-" + checkoutId)));
    }

    @Test
    void otherLateCallbacksForAnExpiredCheckoutDoNotRefund() {
        String checkoutId = expiredCheckout();

        assertThat(sendCallback(newEventId(), checkoutId, "PROCESSING").statusCode()).isEqualTo(200);
        assertThat(sendCallback(newEventId(), checkoutId, "FAILED").statusCode()).isEqualTo(200);

        assertThat(status(checkoutId)).isEqualTo("EXPIRED");
        assertThat(checkout(checkoutId).get("refundRequested").booleanValue()).isFalse();
        provider.verify(0, anyRequestedFor(urlPathMatching("/payments/.*/refund")));
    }

    @Test
    void lateSuccessWithWrongAmountIsFlaggedNotRefunded() {
        String checkoutId = expiredCheckout();

        postCallback(callbackBody(newEventId(), checkoutId, paymentIdFor(checkoutId), "SUCCEEDED", 99999, "EUR"));

        assertThat(events(checkoutId).get(0).get("outcome").stringValue()).isEqualTo("FLAGGED");
        provider.verify(0, anyRequestedFor(urlPathMatching("/payments/.*/refund")));
    }
}
