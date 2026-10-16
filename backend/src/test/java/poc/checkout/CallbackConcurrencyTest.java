package poc.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** Duplicates arriving at the same time, and optimistic locking between different callbacks. */
class CallbackConcurrencyTest extends IntegrationTestBase {

    @RepeatedTest(3)
    void sameCallbackSentByTenThreadsAtOnceIsAppliedExactlyOnce() throws Exception {
        String checkoutId = createCheckout(10050, "EUR");
        String body = callbackBody(newEventId(), checkoutId, paymentIdFor(checkoutId), "SUCCEEDED", 10050, "EUR");
        String signature = sign(body);
        long versionBefore = version(checkoutId);

        List<Integer> statuses = sendAtOnce(10, () -> postCallback(body, signature).statusCode());

        assertThat(statuses).hasSize(10).containsOnly(200);
        assertThat(eventRowCount()).isEqualTo(1);
        JsonNode events = events(checkoutId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("outcome").stringValue()).isEqualTo("APPLIED");
        assertThat(status(checkoutId)).isEqualTo("SUCCEEDED");
        // Applied once: exactly one update of the checkout row.
        assertThat(version(checkoutId)).isEqualTo(versionBefore + 1);
    }

    @RepeatedTest(5)
    void differentCallbacksForTheSameCheckoutAtOnceCannotBothApply() throws Exception {
        String checkoutId = createCheckout(10050, "EUR");
        String processing = callbackBody(newEventId(), checkoutId, paymentIdFor(checkoutId), "PROCESSING", 10050, "EUR");
        String succeeded = callbackBody(newEventId(), checkoutId, paymentIdFor(checkoutId), "SUCCEEDED", 10050, "EUR");

        List<Integer> statuses = sendAtOnce(2, List.of(
                () -> postCallback(processing).statusCode(),
                () -> postCallback(succeeded).statusCode()));

        // The loser of the optimistic-locking race retries once with fresh data, so both get 200.
        assertThat(statuses).containsOnly(200);
        // Whatever the order, PROCESSING never overwrites SUCCEEDED.
        assertThat(status(checkoutId)).isEqualTo("SUCCEEDED");
        JsonNode events = events(checkoutId);
        assertThat(events).hasSize(2);
        long applied = countOutcome(events, "APPLIED");
        long stale = countOutcome(events, "IGNORED_STALE");
        // Either PROCESSING then SUCCEEDED (both applied), or SUCCEEDED then a stale PROCESSING.
        assertThat(applied + stale).isEqualTo(2);
        assertThat(version(checkoutId) - 1).isEqualTo(applied); // -1: storing the paymentId at creation
    }

    @Test
    void conflictingFinalCallbacksAtOnceLeaveExactlyOneFinalStatus() throws Exception {
        String checkoutId = createCheckout(10050, "EUR");
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String status = i % 2 == 0 ? "SUCCEEDED" : "FAILED";
            String body = callbackBody(newEventId(), checkoutId, paymentIdFor(checkoutId), status, 10050, "EUR");
            tasks.add(() -> postCallback(body).statusCode());
        }

        List<Integer> statuses = sendAtOnce(10, tasks);

        assertThat(statuses).hasSize(10).doesNotContain(401, 400);
        JsonNode events = events(checkoutId);
        assertThat(countOutcome(events, "APPLIED")).isEqualTo(1);
        String finalStatus = status(checkoutId);
        assertThat(finalStatus).isIn("SUCCEEDED", "FAILED");
        for (JsonNode event : events) {
            if (event.get("outcome").stringValue().equals("APPLIED")) {
                assertThat(event.get("status").stringValue()).isEqualTo(finalStatus);
            }
        }
    }

    private long version(String checkoutId) {
        return jdbc.queryForObject("SELECT version FROM checkout WHERE id = ?", Long.class, UUID.fromString(checkoutId));
    }

    private static long countOutcome(JsonNode events, String outcome) {
        long count = 0;
        for (JsonNode event : events) {
            if (event.get("outcome").stringValue().equals(outcome)) {
                count++;
            }
        }
        return count;
    }

    private static List<Integer> sendAtOnce(int copies, Callable<Integer> task) throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < copies; i++) {
            tasks.add(task);
        }
        return sendAtOnce(copies, tasks);
    }

    /** Starts all tasks on their own threads and releases them at the same moment. */
    private static List<Integer> sendAtOnce(int threads, List<Callable<Integer>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }
}
