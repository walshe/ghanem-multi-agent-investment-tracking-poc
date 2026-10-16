package com.example.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Responsibility: check each chaos mode, the chaos endpoints, and that chaos only applies to
 * payments created after it is set.
 */
class ChaosTest extends ProviderTestBase {

    @Test
    void chaosSettingsCanBeReadBack() {
        setChaos("{\"modes\":[\"duplicate\",\"reverse\"],\"delaySeconds\":3}");

        Response r = send("GET", "/admin/chaos", null);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().get("delaySeconds").asInt()).isEqualTo(3);
        assertThat(r.json().get("modes").valueStream().map(n -> n.asString()))
                .containsExactlyInAnyOrder("duplicate", "reverse");
    }

    @Test
    void delaySecondsDefaultsToOne() {
        setChaos("{\"modes\":[]}");
        assertThat(send("GET", "/admin/chaos", null).json().get("delaySeconds").asInt()).isEqualTo(1);
    }

    @Test
    void invalidChaosIsRejected() {
        assertThat(send("PUT", "/admin/chaos", "{\"modes\":[\"explode\"]}").status()).isEqualTo(400);
        assertThat(send("PUT", "/admin/chaos", "{\"delaySeconds\":1}").status()).isEqualTo(400);
        assertThat(send("PUT", "/admin/chaos", "{\"modes\":[],\"delaySeconds\":-1}").status()).isEqualTo(400);
    }

    @Test
    void chaosOnlyAppliesToPaymentsCreatedAfterItIsSet() {
        setChaos("{\"modes\":[],\"delaySeconds\":1}");
        String before = newPayment();
        setChaos("{\"modes\":[\"fail\"],\"delaySeconds\":0}");
        String after = newPayment();
        // Changing chaos again must not affect either existing payment.
        setChaos("{\"modes\":[\"drop\"],\"delaySeconds\":0}");

        awaitStatus(before, "SUCCEEDED");
        awaitStatus(after, "FAILED");
        assertThat(awaitCallbacks(before, 2)).extracting(FakeBackend.Received::status)
                .containsExactly("PROCESSING", "SUCCEEDED");
        assertThat(awaitCallbacks(after, 2)).extracting(FakeBackend.Received::status)
                .containsExactly("PROCESSING", "FAILED");
    }

    @Test
    void duplicateSendsEveryCallbackTwiceWithTheSameEventIdAndBytes() throws Exception {
        setChaos("{\"modes\":[\"duplicate\"],\"delaySeconds\":0}");
        String paymentId = newPayment();

        List<FakeBackend.Received> callbacks = awaitCallbacks(paymentId, 4);
        quietPeriod();
        callbacks = backend.receivedFor(paymentId);

        assertThat(callbacks).hasSize(4);
        assertThat(callbacks).extracting(FakeBackend.Received::status)
                .containsExactly("PROCESSING", "PROCESSING", "SUCCEEDED", "SUCCEEDED");
        assertThat(callbacks.get(0).bodyText()).isEqualTo(callbacks.get(1).bodyText());
        assertThat(callbacks.get(0).signature()).isEqualTo(callbacks.get(1).signature());
        assertThat(callbacks.get(2).bodyText()).isEqualTo(callbacks.get(3).bodyText());
        assertThat(callbacks.get(0).eventId()).isNotEqualTo(callbacks.get(2).eventId());
        assertThat(callbacks).allSatisfy(r -> assertThat(backend.signatureValid(r)).isTrue());
    }

    @Test
    void reverseSendsTheFinalCallbackBeforeProcessing() throws Exception {
        setChaos("{\"modes\":[\"reverse\"],\"delaySeconds\":0}");
        String paymentId = newPayment();

        awaitCallbacks(paymentId, 2);
        quietPeriod();
        List<FakeBackend.Received> callbacks = backend.receivedFor(paymentId);

        assertThat(callbacks).extracting(FakeBackend.Received::status).containsExactly("SUCCEEDED", "PROCESSING");
        assertThat(getPayment(paymentId).paymentStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void reverseWithFailSendsFailedBeforeProcessing() {
        setChaos("{\"modes\":[\"reverse\",\"fail\"],\"delaySeconds\":0}");
        String paymentId = newPayment();

        assertThat(awaitCallbacks(paymentId, 2)).extracting(FakeBackend.Received::status)
                .containsExactly("FAILED", "PROCESSING");
    }

    @Test
    void dropSendsNoCallbacksButThePaymentStillCompletes() throws Exception {
        setChaos("{\"modes\":[\"drop\"],\"delaySeconds\":0}");
        String paymentId = newPayment();

        awaitStatus(paymentId, "SUCCEEDED");
        quietPeriod();

        assertThat(backend.receivedFor(paymentId)).isEmpty();
    }

    @Test
    void badSignatureSignsWithTheWrongSecretAndIsRetried() {
        setChaos("{\"modes\":[\"badSignature\"],\"delaySeconds\":0}");
        String paymentId = newPayment();

        // 2 callbacks x (1 attempt + 3 retries), all answered 401 by the fake backend.
        List<FakeBackend.Received> callbacks = awaitCallbacks(paymentId, 8);

        assertThat(callbacks).allSatisfy(r -> {
            assertThat(r.signature()).matches("^[0-9a-f]{64}$");
            assertThat(backend.signatureValid(r)).isFalse();
            assertThat(r.answered()).isEqualTo(401);
        });
        assertThat(getPayment(paymentId).paymentStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void failEndsThePaymentFailed() {
        setChaos("{\"modes\":[\"fail\"],\"delaySeconds\":0}");
        String paymentId = newPayment();

        awaitStatus(paymentId, "FAILED");
        assertThat(awaitCallbacks(paymentId, 2)).extracting(FakeBackend.Received::status)
                .containsExactly("PROCESSING", "FAILED");
    }

    @Test
    void cancelTooLateRefusesTheCancelAndThePaymentStillCompletes() {
        setChaos("{\"modes\":[\"cancelTooLate\"],\"delaySeconds\":1}");
        String paymentId = newPayment();

        Response cancel = send("POST", "/payments/" + paymentId + "/cancel", null);

        assertThat(cancel.status()).isEqualTo(409);
        assertThat(getPayment(paymentId).paymentStatus()).isEqualTo("PENDING");
        awaitStatus(paymentId, "SUCCEEDED");
        assertThat(awaitCallbacks(paymentId, 2)).extracting(FakeBackend.Received::status)
                .containsExactly("PROCESSING", "SUCCEEDED");
    }

    @Test
    void delaySecondsKeepsThePaymentPendingThatLong() {
        setChaos("{\"modes\":[],\"delaySeconds\":2}");
        long start = System.nanoTime();
        String paymentId = newPayment();

        List<FakeBackend.Received> callbacks = awaitCallbacks(paymentId, 1);

        long processingAfterMs = (callbacks.get(0).receivedAtNanos() - start) / 1_000_000;
        assertThat(processingAfterMs).isGreaterThanOrEqualTo(1900);
    }
}
