package com.example.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class IdempotencyTest extends ProviderTestBase {

    // ---- payments ----

    @Test
    void sameKeyReturnsTheExistingPaymentWith200() {
        UUID checkoutId = UUID.randomUUID();
        Response first = createPayment(checkoutId.toString(), checkoutId);
        Response second = createPayment(checkoutId.toString(), checkoutId);

        assertThat(first.status()).isEqualTo(201);
        assertThat(first.paymentStatus()).isEqualTo("PENDING");
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.json().get("paymentId").asString()).isEqualTo(first.json().get("paymentId").asString());
    }

    @Test
    void sameKeyWithADifferentBodyStillReturnsTheFirstPayment() {
        String key = UUID.randomUUID().toString();
        Response first = createPayment(key, UUID.randomUUID());
        Response second = createPayment(key, UUID.randomUUID());

        assertThat(second.status()).isEqualTo(200);
        assertThat(second.json().get("paymentId").asString()).isEqualTo(first.json().get("paymentId").asString());
        assertThat(second.json().get("checkoutId").asString()).isEqualTo(first.json().get("checkoutId").asString());
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateOnePaymentAndOneSetOfCallbacks() throws Exception {
        UUID checkoutId = UUID.randomUUID();
        List<Response> responses = concurrently(20,
                () -> createPayment(checkoutId.toString(), checkoutId));

        assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.status() == 200).hasSize(19);
        Set<String> ids = responses.stream().map(r -> r.json().get("paymentId").asString()).collect(Collectors.toSet());
        assertThat(ids).hasSize(1);

        String paymentId = ids.iterator().next();
        awaitCallbacks(paymentId, 2);
        quietPeriod();
        assertThat(backend.receivedFor(paymentId)).hasSize(2);
        assertThat(backend.received().stream().filter(r -> checkoutId.toString().equals(r.json().get("checkoutId").asString())))
                .hasSize(2);
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        Response r = send("POST", "/payments", createBody(UUID.randomUUID()));
        assertThat(r.status()).isEqualTo(400);
    }

    // ---- refunds ----

    @Test
    void sameRefundKeyNeverRefundsTwice() {
        String paymentId = newPayment();
        awaitStatus(paymentId, "SUCCEEDED");

        Response first = refund(paymentId, "refund-1");
        Response again = refund(paymentId, "refund-1");

        assertThat(first.status()).isEqualTo(200);
        assertThat(first.paymentStatus()).isEqualTo("REFUNDED");
        assertThat(first.json().get("refundCount").asInt()).isEqualTo(1);
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.paymentStatus()).isEqualTo("REFUNDED");
        assertThat(again.json().get("refundCount").asInt()).isEqualTo(1);
        assertThat(getPayment(paymentId).json().get("refundCount").asInt()).isEqualTo(1);
    }

    @Test
    void aDifferentRefundKeyAfterARefundIsRejectedAndDoesNotRefundAgain() {
        String paymentId = newPayment();
        awaitStatus(paymentId, "SUCCEEDED");
        refund(paymentId, "refund-a");

        Response other = refund(paymentId, "refund-b");

        assertThat(other.status()).isEqualTo(409);
        assertThat(getPayment(paymentId).json().get("refundCount").asInt()).isEqualTo(1);
    }

    @Test
    void concurrentRefundsWithTheSameKeyRefundOnce() throws Exception {
        String paymentId = newPayment();
        awaitStatus(paymentId, "SUCCEEDED");

        List<Response> responses = concurrently(20, () -> refund(paymentId, "refund-same"));

        assertThat(responses).allSatisfy(r -> {
            assertThat(r.status()).isEqualTo(200);
            assertThat(r.json().get("refundCount").asInt()).isEqualTo(1);
        });
        assertThat(getPayment(paymentId).json().get("refundCount").asInt()).isEqualTo(1);
    }

    @Test
    void concurrentRefundsWithDifferentKeysRefundOnce() throws Exception {
        String paymentId = newPayment();
        awaitStatus(paymentId, "SUCCEEDED");

        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            keys.add("refund-" + i);
        }
        List<Response> responses = concurrently(20, i -> refund(paymentId, keys.get(i)));

        assertThat(responses).filteredOn(r -> r.status() == 200).hasSize(1);
        assertThat(responses).filteredOn(r -> r.status() == 409).hasSize(19);
        assertThat(getPayment(paymentId).json().get("refundCount").asInt()).isEqualTo(1);
    }

    @Test
    void refundBeforeSuccessIs409AndTheSameKeyWorksOnceItSucceeds() {
        setChaos("{\"modes\":[],\"delaySeconds\":1}");
        String paymentId = newPayment();

        Response early = refund(paymentId, "refund-early");
        assertThat(early.status()).isEqualTo(409);
        assertThat(getPayment(paymentId).json().get("refundCount").asInt()).isZero();

        awaitStatus(paymentId, "SUCCEEDED");
        Response later = refund(paymentId, "refund-early");
        assertThat(later.status()).isEqualTo(200);
        assertThat(later.json().get("refundCount").asInt()).isEqualTo(1);
    }

    @Test
    void refundOfAFailedPaymentIs409() {
        setChaos("{\"modes\":[\"fail\"],\"delaySeconds\":0}");
        String paymentId = newPayment();
        awaitStatus(paymentId, "FAILED");

        assertThat(refund(paymentId, "refund-x").status()).isEqualTo(409);
        assertThat(getPayment(paymentId).json().get("refundCount").asInt()).isZero();
    }

    @Test
    void refundOfUnknownPaymentIs404() {
        assertThat(refund("pay_nope", "refund-x").status()).isEqualTo(404);
    }

    private Response refund(String paymentId, String key) {
        return send("POST", "/payments/" + paymentId + "/refund", null, "Idempotency-Key", key);
    }

    private interface Indexed<T> {
        T call(int index);
    }

    private static <T> List<T> concurrently(int n, java.util.function.Supplier<T> task) throws Exception {
        return concurrently(n, i -> task.get());
    }

    private static <T> List<T> concurrently(int n, Indexed<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            List<CompletableFuture<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        gate.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return task.call(index);
                }, pool));
            }
            gate.countDown();
            return futures.stream().map(CompletableFuture::join).toList();
        } finally {
            pool.shutdownNow();
        }
    }
}
