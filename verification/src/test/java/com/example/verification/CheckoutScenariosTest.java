package com.example.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.verification.Backend.Checkout;
import com.example.verification.Backend.Event;
import com.example.verification.Provider.CallbackRequest;
import com.example.verification.Provider.CallbackResult;
import com.example.verification.Provider.Payment;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Black-box scenarios for the checkout backend and the dummy provider.
 *
 * <p>Assumes the backend runs with the "fast" profile (contract/states.md):
 * reconcile-after 5s, expire-after 10s, job-interval 2s.
 *
 * <p>Chaos settings on the provider are global, so these tests must run one at a time.
 */
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CheckoutScenariosTest {

    private static final long AMOUNT_MINOR = 10050; // EUR 100.50
    private static final String CURRENCY = "EUR";

    /** fast profile: reconcile-after. A "still PENDING" check must happen before this. */
    private static final Duration RECONCILE_AFTER = Duration.ofSeconds(5);

    /** Enough for callbacks of a default-chaos payment (1s delay + 1s) to arrive. */
    private static final Duration CALLBACK_TIMEOUT = Duration.ofSeconds(15);

    /** Enough for the scheduled job: expire-after 10s + a few job intervals of 2s. */
    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(30);

    @BeforeAll
    static void configureAwaitility() {
        Awaitility.setDefaultPollInterval(Duration.ofMillis(200));
        Awaitility.setDefaultPollDelay(Duration.ZERO);
    }

    @BeforeEach
    void resetChaos() {
        Provider.resetChaos();
    }

    // ---------------------------------------------------------------------------------------
    // 1. Happy path
    // ---------------------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("1. Happy path: a new checkout ends SUCCEEDED")
    void happyPath() {
        Checkout created = Backend.createCheckout(AMOUNT_MINOR, CURRENCY);
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.amountMinor()).isEqualTo(AMOUNT_MINOR);
        assertThat(created.currency()).isEqualTo(CURRENCY);

        awaitStatus(created.checkoutId(), "SUCCEEDED", CALLBACK_TIMEOUT);
    }

    // ---------------------------------------------------------------------------------------
    // 2. Failure
    // ---------------------------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("2. Failure: chaos fail gives FAILED")
    void failedPayment() {
        Provider.setChaos(List.of("fail"), 1);

        Checkout created = Backend.createCheckout(AMOUNT_MINOR, CURRENCY);

        awaitStatus(created.checkoutId(), "FAILED", CALLBACK_TIMEOUT);
    }

    // ---------------------------------------------------------------------------------------
    // 3. Duplicates
    // ---------------------------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("3. Duplicates: chaos duplicate gives SUCCEEDED and each eventId appears once")
    void duplicateCallbacks() {
        Provider.setChaos(List.of("duplicate"), 1);

        Checkout created = Backend.createCheckout(AMOUNT_MINOR, CURRENCY);
        String checkoutId = created.checkoutId();

        awaitStatus(checkoutId, "SUCCEEDED", CALLBACK_TIMEOUT);

        // Both the PROCESSING and the SUCCEEDED callback have arrived.
        await("PROCESSING and SUCCEEDED events recorded")
                .atMost(CALLBACK_TIMEOUT)
                .untilAsserted(() -> assertThat(Backend.getEvents(checkoutId))
                        .extracting(Event::status)
                        .contains("PROCESSING", "SUCCEEDED"));

        // The second copy of each callback may still be on its way: keep checking for a while.
        await("every eventId appears once")
                .during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> assertEventIdsUnique(Backend.getEvents(checkoutId)));

        assertThat(Backend.getCheckout(checkoutId).status()).isEqualTo("SUCCEEDED");
    }

    // ---------------------------------------------------------------------------------------
    // 4. Out of order
    // ---------------------------------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("4. Out of order: chaos reverse gives SUCCEEDED and the PROCESSING event is IGNORED_STALE")
    void reversedCallbacks() {
        Provider.setChaos(List.of("reverse"), 1);

        Checkout created = Backend.createCheckout(AMOUNT_MINOR, CURRENCY);
        String checkoutId = created.checkoutId();

        awaitStatus(checkoutId, "SUCCEEDED", CALLBACK_TIMEOUT);

        // The PROCESSING callback arrives after SUCCEEDED.
        await("PROCESSING event recorded as IGNORED_STALE")
                .atMost(CALLBACK_TIMEOUT)
                .untilAsserted(() -> {
                    List<Event> processing = Backend.getEvents(checkoutId).stream()
                            .filter(e -> "PROCESSING".equals(e.status()))
                            .toList();
                    assertThat(processing).hasSize(1);
                    assertThat(processing.get(0).outcome()).isEqualTo("IGNORED_STALE");
                });

        assertThat(Backend.getCheckout(checkoutId).status()).isEqualTo("SUCCEEDED");
    }

    // ---------------------------------------------------------------------------------------
    // 5. Same callback at once
    // ---------------------------------------------------------------------------------------

    @Test
    @Order(5)
    @DisplayName("5. Same callback at once: 10 concurrent copies give SUCCEEDED and one event")
    void concurrentIdenticalCallbacks() {
        Provider.setChaos(List.of("drop"), 1);

        Checkout created = Backend.createCheckout(AMOUNT_MINOR, CURRENCY);
        String checkoutId = created.checkoutId();
        String paymentId = awaitPaymentId(checkoutId);

        String eventId = "concurrent-" + UUID.randomUUID();
        List<CallbackResult> results = Provider.sendCallback(
                CallbackRequest.of(paymentId, "SUCCEEDED").eventId(eventId).copies(10));

        assertThat(results).hasSize(10);
        // Duplicates must return 200 so the provider stops retrying (states.md).
        assertThat(results).allSatisfy(r -> {
            assertThat(r.eventId()).isEqualTo(eventId);
            assertThat(r.backendStatus()).isEqualTo(200);
        });

        assertThat(Backend.getCheckout(checkoutId).status()).isEqualTo("SUCCEEDED");

        List<Event> events = Backend.getEvents(checkoutId);
        List<Event> matching = events.stream().filter(e -> eventId.equals(e.eventId())).toList();
        assertThat(matching).as("events with eventId %s", eventId).hasSize(1);
        assertThat(matching.get(0).status()).isEqualTo("SUCCEEDED");
        assertThat(matching.get(0).outcome()).isEqualTo("APPLIED");
        assertEventIdsUnique(events);
    }

    // ---------------------------------------------------------------------------------------
    // 6. Bad signature
    // ---------------------------------------------------------------------------------------

    @Test
    @Order(6)
    @DisplayName("6. Bad signature: backend answers 401 and status stays PENDING")
    void badSignature() {
        Provider.setChaos(List.of("drop"), 1);

        long start = System.nanoTime();
        Checkout created = Backend.createCheckout(AMOUNT_MINOR, CURRENCY);
        String checkoutId = created.checkoutId();
        String paymentId = awaitPaymentId(checkoutId);

        String eventId = "bad-signature-" + UUID.randomUUID();
        List<CallbackResult> results = Provider.sendCallback(
                CallbackRequest.of(paymentId, "SUCCEEDED").eventId(eventId).validSignature(false));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).backendStatus()).isEqualTo(401);

        assertStillPendingBeforeJobCouldRun(checkoutId, start);

        // "Missing or wrong: return 401 and store nothing."
        assertThat(Backend.getEvents(checkoutId))
                .extracting(Event::eventId)
                .doesNotContain(eventId);
    }

    // ---------------------------------------------------------------------------------------
    // 7. Missing callbacks
    // ---------------------------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("7. Missing callbacks: chaos drop still ends SUCCEEDED via the scheduled job")
    void droppedCallbacksReconciled() {
        Provider.setChaos(List.of("drop"), 1);

        Checkout created = Backend.createCheckout(AMOUNT_MINOR, CURRENCY);
        String checkoutId = created.checkoutId();

        awaitStatus(checkoutId, "SUCCEEDED", JOB_TIMEOUT);

        // No callback was sent, so the change must come from reconciliation.
        assertThat(Backend.getEvents(checkoutId))
                .filteredOn(e -> ("reconcile-" + checkoutId + "-SUCCEEDED").equals(e.eventId()))
                .singleElement()
                .extracting(Event::status)
                .isEqualTo("SUCCEEDED");
    }

    // ---------------------------------------------------------------------------------------
    // 8. Expiry
    // ---------------------------------------------------------------------------------------

    @Test
    @Order(8)
    @DisplayName("8. Expiry: delaySeconds=60 gives EXPIRED and the provider payment is CANCELLED")
    void expiry() {
        Provider.setChaos(List.of(), 60);

        Checkout created = Backend.createCheckout(AMOUNT_MINOR, CURRENCY);
        String checkoutId = created.checkoutId();
        String paymentId = awaitPaymentId(checkoutId);

        awaitStatus(checkoutId, "EXPIRED", JOB_TIMEOUT);

        await("provider payment CANCELLED")
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(Provider.getPayment(paymentId).status())
                        .isEqualTo("CANCELLED"));

        assertThat(Backend.getCheckout(checkoutId).status()).isEqualTo("EXPIRED");
    }

    // ---------------------------------------------------------------------------------------
    // 9. Late success
    // ---------------------------------------------------------------------------------------

    @Test
    @Order(9)
    @DisplayName("9. Late success: cancelTooLate keeps EXPIRED and refunds exactly once")
    void lateSuccessIsRefunded() {
        // Checkout expires at ~10-12s, cancel gets 409, payment still succeeds at ~16s.
        Provider.setChaos(List.of("cancelTooLate"), 15);

        Checkout created = Backend.createCheckout(AMOUNT_MINOR, CURRENCY);
        String checkoutId = created.checkoutId();
        String paymentId = awaitPaymentId(checkoutId);

        awaitStatus(checkoutId, "EXPIRED", JOB_TIMEOUT);

        await("refund requested and provider payment REFUNDED")
                .atMost(Duration.ofSeconds(45))
                .untilAsserted(() -> {
                    Checkout checkout = Backend.getCheckout(checkoutId);
                    assertThat(checkout.status()).isEqualTo("EXPIRED");
                    assertThat(checkout.refundRequested()).isTrue();
                    Payment payment = Provider.getPayment(paymentId);
                    assertThat(payment.status()).isEqualTo("REFUNDED");
                    assertThat(payment.refundCount()).isEqualTo(1);
                });

        // The callback and the scheduled job may both request the refund: it must still happen once.
        await("still EXPIRED with exactly one refund")
                .during(Duration.ofSeconds(6))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Checkout checkout = Backend.getCheckout(checkoutId);
                    assertThat(checkout.status()).isEqualTo("EXPIRED");
                    assertThat(checkout.refundRequested()).isTrue();
                    Payment payment = Provider.getPayment(paymentId);
                    assertThat(payment.status()).isEqualTo("REFUNDED");
                    assertThat(payment.refundCount()).isEqualTo(1);
                });
    }

    // ---------------------------------------------------------------------------------------
    // 10. Wrong amount
    // ---------------------------------------------------------------------------------------

    @Test
    @Order(10)
    @DisplayName("10. Wrong amount: event is FLAGGED and status stays PENDING")
    void wrongAmountIsFlagged() {
        Provider.setChaos(List.of("drop"), 1);

        long start = System.nanoTime();
        Checkout created = Backend.createCheckout(AMOUNT_MINOR, CURRENCY);
        String checkoutId = created.checkoutId();
        String paymentId = awaitPaymentId(checkoutId);

        String eventId = "wrong-amount-" + UUID.randomUUID();
        List<CallbackResult> results = Provider.sendCallback(
                CallbackRequest.of(paymentId, "SUCCEEDED").eventId(eventId).amountMinor(AMOUNT_MINOR - 1));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).backendStatus()).isEqualTo(200);

        List<Event> matching = Backend.getEvents(checkoutId).stream()
                .filter(e -> eventId.equals(e.eventId()))
                .toList();
        assertThat(matching).as("events with eventId %s", eventId).hasSize(1);
        assertThat(matching.get(0).outcome()).isEqualTo("FLAGGED");

        assertStillPendingBeforeJobCouldRun(checkoutId, start);
    }

    // ---------------------------------------------------------------------------------------
    // 11. Random order
    // ---------------------------------------------------------------------------------------

    @RepeatedTest(value = 20, name = "11. Random order: run {currentRepetition}/{totalRepetitions}")
    @Order(11)
    void randomOrderAndDuplicates(RepetitionInfo repetition) {
        long seed = System.nanoTime();
        Random random = new Random(seed);
        System.out.printf("Random order run %d uses seed %d%n", repetition.getCurrentRepetition(), seed);

        Provider.setChaos(List.of("drop"), 1);

        Checkout created = Backend.createCheckout(AMOUNT_MINOR, CURRENCY);
        String checkoutId = created.checkoutId();
        String paymentId = awaitPaymentId(checkoutId);

        String processingId = "processing-" + UUID.randomUUID();
        String succeededId = "succeeded-" + UUID.randomUUID();

        // Each callback at least once, plus 0-4 random duplicates reusing the same eventIds.
        List<CallbackRequest> plan = new ArrayList<>();
        plan.add(CallbackRequest.of(paymentId, "PROCESSING").eventId(processingId));
        plan.add(CallbackRequest.of(paymentId, "SUCCEEDED").eventId(succeededId));
        int extra = random.nextInt(5);
        for (int i = 0; i < extra; i++) {
            plan.add(random.nextBoolean()
                    ? CallbackRequest.of(paymentId, "PROCESSING").eventId(processingId)
                    : CallbackRequest.of(paymentId, "SUCCEEDED").eventId(succeededId));
        }
        Collections.shuffle(plan, random);
        for (CallbackRequest request : plan) {
            request.copies(1 + random.nextInt(3));
        }
        System.out.printf("  order: %s%n", plan.stream()
                .map(r -> r.toJson().get("status") + "x" + r.toJson().get("copies"))
                .collect(Collectors.joining(", ")));

        long sendStart = System.nanoTime();
        for (CallbackRequest request : plan) {
            List<CallbackResult> results = Provider.sendCallback(request);
            assertThat(results).allSatisfy(r -> assertThat(r.backendStatus()).isEqualTo(200));
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - sendStart);
        Duration remaining = Duration.ofSeconds(3).minus(elapsed);
        if (remaining.compareTo(Duration.ofMillis(250)) < 0) {
            remaining = Duration.ofMillis(250);
        }
        await("SUCCEEDED within 3 seconds of the first callback")
                .atMost(remaining)
                .untilAsserted(() -> assertThat(Backend.getCheckout(checkoutId).status()).isEqualTo("SUCCEEDED"));

        // The SUCCEEDED callback is allowed from both PENDING and PROCESSING, so it must be APPLIED.
        assertThat(Backend.getEvents(checkoutId))
                .filteredOn(e -> succeededId.equals(e.eventId()))
                .singleElement()
                .extracting(Event::outcome)
                .isEqualTo("APPLIED");
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private static void awaitStatus(String checkoutId, String expected, Duration timeout) {
        await("checkout " + checkoutId + " becomes " + expected)
                .atMost(timeout)
                .untilAsserted(() -> assertThat(Backend.getCheckout(checkoutId).status()).isEqualTo(expected));
    }

    /** The backend stores the provider's paymentId when the checkout is created. */
    private static String awaitPaymentId(String checkoutId) {
        return await("checkout " + checkoutId + " has a paymentId")
                .atMost(Duration.ofSeconds(3))
                .until(() -> Backend.getCheckout(checkoutId).paymentId(), id -> id != null && !id.isBlank());
    }

    /**
     * Checks the status is PENDING, and that the check happened before the scheduled job could
     * reconcile the checkout. {@code startNanos} is taken before the checkout was created.
     */
    private static void assertStillPendingBeforeJobCouldRun(String checkoutId, long startNanos) {
        String status = Backend.getCheckout(checkoutId).status();
        Duration sinceCreate = Duration.ofNanos(System.nanoTime() - startNanos);
        assertThat(sinceCreate)
                .as("PENDING check must happen within %s of creating the checkout", RECONCILE_AFTER)
                .isLessThan(RECONCILE_AFTER);
        assertThat(status).isEqualTo("PENDING");
    }

    private static void assertEventIdsUnique(List<Event> events) {
        Map<String, Long> counts = events.stream()
                .collect(Collectors.groupingBy(Event::eventId, Collectors.counting()));
        assertThat(events).isNotEmpty();
        assertThat(counts)
                .as("number of times each eventId appears in /events")
                .allSatisfy((eventId, count) -> assertThat(count).as(eventId).isEqualTo(1L));
    }
}
