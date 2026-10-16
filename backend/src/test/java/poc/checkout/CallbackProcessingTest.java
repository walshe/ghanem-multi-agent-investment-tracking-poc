package poc.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** states.md, callback steps 2 to 4, and the audit trail. */
class CallbackProcessingTest extends IntegrationTestBase {

    // ---------- step 2: duplicate ----------

    @Test
    void duplicateEventIdReturns200AndChangesNothing() {
        String checkoutId = createCheckout(10050, "EUR");
        String eventId = newEventId();
        assertThat(sendCallback(eventId, checkoutId, "PROCESSING").statusCode()).isEqualTo(200);
        long versionAfterFirst = version(checkoutId);

        // Same eventId again, even with a different status: nothing changes.
        assertThat(sendCallback(eventId, checkoutId, "PROCESSING").statusCode()).isEqualTo(200);
        assertThat(sendCallback(eventId, checkoutId, "SUCCEEDED").statusCode()).isEqualTo(200);

        assertThat(status(checkoutId)).isEqualTo("PROCESSING");
        assertThat(version(checkoutId)).isEqualTo(versionAfterFirst);
        JsonNode events = events(checkoutId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("status").stringValue()).isEqualTo("PROCESSING");
        assertThat(events.get(0).get("outcome").stringValue()).isEqualTo("APPLIED");
    }

    // ---------- step 3: amount ----------

    @Test
    void amountMismatchIsFlaggedAndChangesNothing() {
        String checkoutId = createCheckout(10050, "EUR");
        String eventId = newEventId();

        HttpResponse<String> response = postCallback(
                callbackBody(eventId, checkoutId, paymentIdFor(checkoutId), "SUCCEEDED", 10049, "EUR"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(status(checkoutId)).isEqualTo("PENDING");
        assertOnlyEvent(checkoutId, eventId, "SUCCEEDED", "FLAGGED");
        assertThat(events(checkoutId).get(0).get("amountMinor").longValue()).isEqualTo(10049);
    }

    @Test
    void currencyMismatchIsFlaggedAndChangesNothing() {
        String checkoutId = createCheckout(10050, "EUR");
        String eventId = newEventId();

        HttpResponse<String> response = postCallback(
                callbackBody(eventId, checkoutId, paymentIdFor(checkoutId), "SUCCEEDED", 10050, "USD"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(status(checkoutId)).isEqualTo("PENDING");
        assertOnlyEvent(checkoutId, eventId, "SUCCEEDED", "FLAGGED");
    }

    @Test
    void paymentIdMismatchIsFlaggedAndChangesNothing() {
        String checkoutId = createCheckout(10050, "EUR");
        String eventId = newEventId();

        HttpResponse<String> response = postCallback(
                callbackBody(eventId, checkoutId, "pay-someone-else", "SUCCEEDED", 10050, "EUR"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(status(checkoutId)).isEqualTo("PENDING");
        assertOnlyEvent(checkoutId, eventId, "SUCCEEDED", "FLAGGED");
    }

    @Test
    void callbackForUnknownCheckoutIsStoredAsFlaggedAndReturns200() {
        String unknownCheckoutId = UUID.randomUUID().toString();
        String eventId = newEventId();

        HttpResponse<String> response = postCallback(
                callbackBody(eventId, unknownCheckoutId, "pay-x", "SUCCEEDED", 10050, "EUR"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT outcome FROM payment_event WHERE event_id = ?", String.class, eventId))
                .isEqualTo("FLAGGED");
    }

    @Test
    void callbackForCheckoutWithoutPaymentIdAdoptsThePaymentId() {
        // POST /payments reached the provider but its answer was lost.
        provider.resetAll();
        String checkoutId = json.readTree(postJson("/checkouts", "{\"amountMinor\": 10050, \"currency\": \"EUR\"}")
                .body()).get("checkoutId").stringValue();
        assertThat(checkout(checkoutId).get("paymentId").isNull()).isTrue();

        assertThat(sendCallback(newEventId(), checkoutId, "PROCESSING").statusCode()).isEqualTo(200);

        JsonNode checkout = checkout(checkoutId);
        assertThat(checkout.get("status").stringValue()).isEqualTo("PROCESSING");
        assertThat(checkout.get("paymentId").stringValue()).isEqualTo(paymentIdFor(checkoutId));
    }

    // ---------- step 4: order ----------

    @Test
    void inOrderCallbacksAreApplied() {
        String checkoutId = createCheckout(10050, "EUR");

        assertThat(sendCallback(newEventId(), checkoutId, "PROCESSING").statusCode()).isEqualTo(200);
        assertThat(status(checkoutId)).isEqualTo("PROCESSING");
        assertThat(sendCallback(newEventId(), checkoutId, "SUCCEEDED").statusCode()).isEqualTo(200);
        assertThat(status(checkoutId)).isEqualTo("SUCCEEDED");
    }

    @Test
    void pendingCanGoStraightToSucceeded() {
        String checkoutId = createCheckout(10050, "EUR");

        assertThat(sendCallback(newEventId(), checkoutId, "SUCCEEDED").statusCode()).isEqualTo(200);

        assertThat(status(checkoutId)).isEqualTo("SUCCEEDED");
    }

    @Test
    void pendingCanGoStraightToFailed() {
        String checkoutId = createCheckout(10050, "EUR");

        assertThat(sendCallback(newEventId(), checkoutId, "FAILED").statusCode()).isEqualTo(200);

        assertThat(status(checkoutId)).isEqualTo("FAILED");
    }

    @Test
    void processingArrivingAfterSucceededIsIgnoredAsStale() {
        String checkoutId = createCheckout(10050, "EUR");
        String success = newEventId();
        String lateProcessing = newEventId();

        assertThat(sendCallback(success, checkoutId, "SUCCEEDED").statusCode()).isEqualTo(200);
        assertThat(sendCallback(lateProcessing, checkoutId, "PROCESSING").statusCode()).isEqualTo(200);

        assertThat(status(checkoutId)).isEqualTo("SUCCEEDED");
        JsonNode events = events(checkoutId);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("eventId").stringValue()).isEqualTo(success);
        assertThat(events.get(0).get("outcome").stringValue()).isEqualTo("APPLIED");
        assertThat(events.get(1).get("eventId").stringValue()).isEqualTo(lateProcessing);
        assertThat(events.get(1).get("outcome").stringValue()).isEqualTo("IGNORED_STALE");
    }

    @Test
    void finalStatusNeverChanges() {
        String checkoutId = createCheckout(10050, "EUR");
        assertThat(sendCallback(newEventId(), checkoutId, "FAILED").statusCode()).isEqualTo(200);

        String success = newEventId();
        assertThat(sendCallback(success, checkoutId, "SUCCEEDED").statusCode()).isEqualTo(200);

        assertThat(status(checkoutId)).isEqualTo("FAILED");
        assertThat(events(checkoutId).get(1).get("outcome").stringValue()).isEqualTo("IGNORED_STALE");
        assertThat(checkout(checkoutId).get("refundRequested").booleanValue()).isFalse();
    }

    @Test
    void processingAfterProcessingIsIgnoredAsStale() {
        String checkoutId = createCheckout(10050, "EUR");
        assertThat(sendCallback(newEventId(), checkoutId, "PROCESSING").statusCode()).isEqualTo(200);

        assertThat(sendCallback(newEventId(), checkoutId, "PROCESSING").statusCode()).isEqualTo(200);

        assertThat(events(checkoutId).get(1).get("outcome").stringValue()).isEqualTo("IGNORED_STALE");
    }

    // ---------- audit trail ----------

    @Test
    void auditTrailListsEveryAcceptedCallbackOldestFirstWithItsOutcome() {
        String checkoutId = createCheckout(10050, "EUR");
        String flagged = newEventId();
        String processing = newEventId();
        String succeeded = newEventId();
        String stale = newEventId();

        postCallback(callbackBody(flagged, checkoutId, paymentIdFor(checkoutId), "SUCCEEDED", 1, "EUR"));
        sendCallback(processing, checkoutId, "PROCESSING");
        sendCallback(succeeded, checkoutId, "SUCCEEDED");
        sendCallback(stale, checkoutId, "PROCESSING");
        sendCallback(succeeded, checkoutId, "SUCCEEDED"); // duplicate: not stored again
        postCallback(callbackBody(newEventId(), checkoutId, paymentIdFor(checkoutId), "FAILED", 10050, "EUR"),
                "bad"); // bad signature: not stored

        JsonNode events = events(checkoutId);
        assertThat(events).hasSize(4);
        assertEvent(events.get(0), flagged, "SUCCEEDED", "FLAGGED");
        assertEvent(events.get(1), processing, "PROCESSING", "APPLIED");
        assertEvent(events.get(2), succeeded, "SUCCEEDED", "APPLIED");
        assertEvent(events.get(3), stale, "PROCESSING", "IGNORED_STALE");
        for (JsonNode event : events) {
            assertThat(event.get("receivedAt").isString()).isTrue();
            assertThat(event.get("amountMinor").isIntegralNumber()).isTrue();
        }
    }

    private long version(String checkoutId) {
        return jdbc.queryForObject("SELECT version FROM checkout WHERE id = ?", Long.class, UUID.fromString(checkoutId));
    }

    private void assertOnlyEvent(String checkoutId, String eventId, String status, String outcome) {
        JsonNode events = events(checkoutId);
        assertThat(events).hasSize(1);
        assertEvent(events.get(0), eventId, status, outcome);
    }

    private static void assertEvent(JsonNode event, String eventId, String status, String outcome) {
        assertThat(event.get("eventId").stringValue()).isEqualTo(eventId);
        assertThat(event.get("status").stringValue()).isEqualTo(status);
        assertThat(event.get("outcome").stringValue()).isEqualTo(outcome);
    }
}
