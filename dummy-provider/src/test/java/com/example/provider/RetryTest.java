package com.example.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Responsibility: check callback retries: byte-identical retries until a 2xx, giving up after 3,
 * and that an unreachable backend does not stop the payment.
 */
class RetryTest extends ProviderTestBase {

    @Test
    void non2xxIsRetriedWithTheSameEventIdBodyAndSignatureUntilItSucceeds() throws Exception {
        AtomicInteger processingAttempts = new AtomicInteger();
        backend.respondWith(r -> r.status().equals("PROCESSING") && processingAttempts.incrementAndGet() <= 2 ? 503 : 200);
        String paymentId = newPayment();

        awaitStatus(paymentId, "SUCCEEDED");
        awaitCallbacks(paymentId, 4);
        quietPeriod();
        List<FakeBackend.Received> processing = backend.receivedFor(paymentId).stream()
                .filter(r -> r.status().equals("PROCESSING")).toList();

        assertThat(processing).extracting(FakeBackend.Received::answered).containsExactly(503, 503, 200);
        assertThat(processing).extracting(FakeBackend.Received::bodyText).containsOnly(processing.get(0).bodyText());
        assertThat(processing).extracting(FakeBackend.Received::signature).containsOnly(processing.get(0).signature());
    }

    @Test
    void givesUpAfterThreeRetries() throws Exception {
        backend.respondWith(r -> 500);
        String paymentId = newPayment();

        awaitCallbacks(paymentId, 8);
        quietPeriod();
        List<FakeBackend.Received> all = backend.receivedFor(paymentId);

        assertThat(all).hasSize(8);
        assertThat(all.stream().filter(r -> r.status().equals("PROCESSING")).map(FakeBackend.Received::eventId).distinct())
                .hasSize(1);
        assertThat(all.stream().filter(r -> r.status().equals("PROCESSING"))).hasSize(4);
        assertThat(all.stream().filter(r -> r.status().equals("SUCCEEDED"))).hasSize(4);
    }

    @Test
    void unreachableBackendDoesNotStopThePayment() {
        backend.close();
        String paymentId = newPayment();

        awaitStatus(paymentId, "SUCCEEDED");
    }
}
