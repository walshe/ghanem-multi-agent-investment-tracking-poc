package com.example.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class SendCallbackTest extends ProviderTestBase {

    @Test
    void sendsOneSignedCallbackWithANewEventIdAndReportsTheBackendStatus() {
        String paymentId = quietPayment();

        Response r = sendCallback("{\"paymentId\":\"%s\",\"status\":\"SUCCEEDED\"}".formatted(paymentId));

        assertThat(r.status()).isEqualTo(200);
        JsonNode results = r.json().get("results");
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).get("backendStatus").asInt()).isEqualTo(200);

        List<FakeBackend.Received> received = backend.receivedFor(paymentId);
        assertThat(received).hasSize(1);
        assertThat(backend.signatureValid(received.get(0))).isTrue();
        assertThat(received.get(0).eventId()).isEqualTo(results.get(0).get("eventId").asString());
        assertThat(received.get(0).status()).isEqualTo("SUCCEEDED");
        assertThat(received.get(0).json().get("amountMinor").asLong()).isEqualTo(10050L);
    }

    @Test
    void sendsAllCopiesAtTheSameTimeWithIdenticalBytes() {
        String paymentId = quietPayment();
        backend.delayEachRequest(300);

        Response r = sendCallback("{\"paymentId\":\"%s\",\"status\":\"PROCESSING\",\"copies\":20}".formatted(paymentId));

        JsonNode results = r.json().get("results");
        assertThat(results.size()).isEqualTo(20);
        String eventId = results.get(0).get("eventId").asString();
        for (JsonNode result : results) {
            assertThat(result.get("eventId").asString()).isEqualTo(eventId);
            assertThat(result.get("backendStatus").asInt()).isEqualTo(200);
        }
        List<FakeBackend.Received> received = backend.receivedFor(paymentId);
        assertThat(received).hasSize(20);
        assertThat(received).extracting(FakeBackend.Received::bodyText).containsOnly(received.get(0).bodyText());
        // Sent one after another, at most one request would ever be in flight.
        assertThat(backend.maxInFlight()).isGreaterThanOrEqualTo(15);
    }

    @Test
    void reusesAGivenEventIdAndAmount() {
        String paymentId = quietPayment();

        sendCallback("{\"paymentId\":\"%s\",\"status\":\"FAILED\",\"eventId\":\"evt-fixed\",\"amountMinor\":1}"
                .formatted(paymentId));

        FakeBackend.Received received = backend.receivedFor(paymentId).get(0);
        assertThat(received.eventId()).isEqualTo("evt-fixed");
        assertThat(received.json().get("amountMinor").asLong()).isEqualTo(1L);
        assertThat(received.status()).isEqualTo("FAILED");
    }

    @Test
    void invalidSignatureIsSentWhenAskedAndThe401IsReported() {
        String paymentId = quietPayment();

        Response r = sendCallback("{\"paymentId\":\"%s\",\"status\":\"SUCCEEDED\",\"validSignature\":false,\"copies\":2}"
                .formatted(paymentId));

        assertThat(r.json().get("results").valueStream().map(n -> n.get("backendStatus").asInt())).containsOnly(401);
        assertThat(backend.receivedFor(paymentId)).allSatisfy(rec -> assertThat(backend.signatureValid(rec)).isFalse());
    }

    @Test
    void reportsWhateverStatusTheBackendAnswersAndDoesNotRetry() throws Exception {
        String paymentId = quietPayment();
        backend.respondWith(rec -> 500);

        Response r = sendCallback("{\"paymentId\":\"%s\",\"status\":\"SUCCEEDED\"}".formatted(paymentId));

        assertThat(r.json().get("results").get(0).get("backendStatus").asInt()).isEqualTo(500);
        quietPeriod();
        assertThat(backend.receivedFor(paymentId)).hasSize(1);
    }

    @Test
    void worksInEveryChaosModeIncludingDropAndBadSignature() {
        setChaos("{\"modes\":[\"drop\",\"badSignature\",\"duplicate\"],\"delaySeconds\":0}");
        String paymentId = newPayment();
        awaitStatus(paymentId, "SUCCEEDED");

        Response r = sendCallback("{\"paymentId\":\"%s\",\"status\":\"SUCCEEDED\"}".formatted(paymentId));

        assertThat(r.json().get("results").size()).isEqualTo(1);
        assertThat(r.json().get("results").get(0).get("backendStatus").asInt()).isEqualTo(200);
        assertThat(backend.receivedFor(paymentId)).hasSize(1);
    }

    @Test
    void unknownPaymentIs404() {
        assertThat(sendCallback("{\"paymentId\":\"pay_nope\",\"status\":\"SUCCEEDED\"}").status()).isEqualTo(404);
    }

    @Test
    void invalidRequestsAreRejected() {
        String paymentId = quietPayment();
        assertThat(sendCallback("{\"paymentId\":\"%s\",\"status\":\"PENDING\"}".formatted(paymentId)).status()).isEqualTo(400);
        assertThat(sendCallback("{\"paymentId\":\"%s\",\"status\":\"SUCCEEDED\",\"copies\":0}".formatted(paymentId)).status()).isEqualTo(400);
        assertThat(sendCallback("{\"paymentId\":\"%s\",\"status\":\"SUCCEEDED\",\"copies\":51}".formatted(paymentId)).status()).isEqualTo(400);
        assertThat(backend.receivedFor(paymentId)).isEmpty();
    }

    /** A completed payment whose automatic callbacks were dropped, so only manual ones arrive. */
    private String quietPayment() {
        setChaos("{\"modes\":[\"drop\"],\"delaySeconds\":0}");
        String paymentId = newPayment();
        awaitStatus(paymentId, "SUCCEEDED");
        return paymentId;
    }

    private Response sendCallback(String body) {
        return send("POST", "/admin/send-callback", body);
    }
}
