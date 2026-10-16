package com.example.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CancelTest extends ProviderTestBase {

    @Test
    void cancellingAPendingPaymentStopsAllChangesAndCallbacks() throws Exception {
        setChaos("{\"modes\":[],\"delaySeconds\":1}");
        String paymentId = newPayment();

        Response cancel = cancel(paymentId);

        assertThat(cancel.status()).isEqualTo(200);
        assertThat(cancel.paymentStatus()).isEqualTo("CANCELLED");
        Thread.sleep(2000); // past delaySeconds + finalAfter
        assertThat(getPayment(paymentId).paymentStatus()).isEqualTo("CANCELLED");
        assertThat(backend.receivedFor(paymentId)).isEmpty();
    }

    @Test
    void cancellingTwiceReturns200Again() {
        setChaos("{\"modes\":[],\"delaySeconds\":1}");
        String paymentId = newPayment();

        cancel(paymentId);
        Response again = cancel(paymentId);

        assertThat(again.status()).isEqualTo(200);
        assertThat(again.paymentStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancellingAProcessingPaymentStopsTheFinalChangeAndCallback() throws Exception {
        String paymentId = newPayment();
        awaitStatus(paymentId, "PROCESSING");

        Response cancel = cancel(paymentId);

        assertThat(cancel.status()).isEqualTo(200);
        assertThat(cancel.paymentStatus()).isEqualTo("CANCELLED");
        quietPeriod();
        assertThat(getPayment(paymentId).paymentStatus()).isEqualTo("CANCELLED");
        assertThat(backend.receivedFor(paymentId)).extracting(FakeBackend.Received::status)
                .doesNotContain("SUCCEEDED", "FAILED");
    }

    @Test
    void cancellingStopsRetriesOfAlreadySentCallbacks() throws Exception {
        backend.respondWith(r -> 500);
        String paymentId = newPayment();
        awaitCallbacks(paymentId, 1); // first PROCESSING attempt failed, retries pending

        assertThat(cancel(paymentId).status()).isEqualTo(200);
        int seenAtCancel = backend.receivedFor(paymentId).size();
        quietPeriod();

        assertThat(backend.receivedFor(paymentId)).hasSizeLessThanOrEqualTo(seenAtCancel + 1); // at most one in flight
        assertThat(backend.receivedFor(paymentId)).extracting(FakeBackend.Received::status).containsOnly("PROCESSING");
    }

    @Test
    void cancelInReverseModeNeverSendsTheHeldProcessingCallback() throws Exception {
        setChaos("{\"modes\":[\"reverse\"],\"delaySeconds\":0}");
        String paymentId = newPayment();
        awaitStatus(paymentId, "PROCESSING");

        assertThat(cancel(paymentId).status()).isEqualTo(200);
        quietPeriod();

        assertThat(backend.receivedFor(paymentId)).isEmpty();
    }

    @Test
    void cancellingASucceededPaymentIs409() {
        String paymentId = newPayment();
        awaitStatus(paymentId, "SUCCEEDED");

        assertThat(cancel(paymentId).status()).isEqualTo(409);
        assertThat(getPayment(paymentId).paymentStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void cancellingAFailedPaymentIs409() {
        setChaos("{\"modes\":[\"fail\"],\"delaySeconds\":0}");
        String paymentId = newPayment();
        awaitStatus(paymentId, "FAILED");

        assertThat(cancel(paymentId).status()).isEqualTo(409);
    }

    @Test
    void cancellingUnknownPaymentIs404() {
        assertThat(cancel("pay_nope").status()).isEqualTo(404);
        assertThat(getPayment("pay_nope").status()).isEqualTo(404);
    }

    private Response cancel(String paymentId) {
        return send("POST", "/payments/" + paymentId + "/cancel", null);
    }
}
