package com.example.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Responsibility: check the timings from the contract, using the real values from
 * application.yml: delaySeconds 1 by default, final status 1 second after PROCESSING, and 3
 * retries 2 seconds apart.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "provider.signing-secret=" + ProviderTestBase.TEST_SECRET)
class ContractTimingsTest extends ProviderTestBase {

    @Test
    void lifecycleUsesTheContractTimings() {
        setChaos("{\"modes\":[]}"); // delaySeconds defaults to 1
        long start = System.nanoTime();
        String paymentId = newPayment();

        List<FakeBackend.Received> callbacks = awaitCallbacks(paymentId, 2);

        long processingMs = (callbacks.get(0).receivedAtNanos() - start) / 1_000_000;
        long finalMs = (callbacks.get(1).receivedAtNanos() - callbacks.get(0).receivedAtNanos()) / 1_000_000;
        assertThat(processingMs).isBetween(950L, 1800L);
        assertThat(finalMs).isBetween(900L, 1600L);
    }

    @Test
    void retriesThreeTimesTwoSecondsApart() throws Exception {
        setChaos("{\"modes\":[],\"delaySeconds\":0}");
        backend.respondWith(r -> 500);
        String retried = newPayment();

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15))
                .until(() -> backend.receivedFor(retried).stream().filter(r -> r.status().equals("PROCESSING")).count() == 4);
        Thread.sleep(2500);
        List<FakeBackend.Received> processing = backend.receivedFor(retried).stream()
                .filter(r -> r.status().equals("PROCESSING")).toList();

        assertThat(processing).hasSize(4);
        for (int i = 1; i < processing.size(); i++) {
            long gapMs = (processing.get(i).receivedAtNanos() - processing.get(i - 1).receivedAtNanos()) / 1_000_000;
            assertThat(gapMs).isBetween(1900L, 2600L);
        }
    }
}
