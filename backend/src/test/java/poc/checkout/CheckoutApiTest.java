package poc.checkout;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.tomakehurst.wiremock.http.Fault;

import tools.jackson.databind.JsonNode;

/** POST /checkouts, GET /checkouts/{id}, GET /checkouts/{id}/events, CORS; states.md "Creating a checkout". */
class CheckoutApiTest extends IntegrationTestBase {

    @Test
    void createSavesPendingCallsProviderWithIdempotencyKeyAndStoresPaymentId() {
        HttpResponse<String> response = postJson("/checkouts", "{\"amountMinor\": 10050, \"currency\": \"EUR\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(response.body());
        String checkoutId = body.get("checkoutId").stringValue();
        assertThat(UUID.fromString(checkoutId)).isNotNull();
        assertThat(body.get("status").stringValue()).isEqualTo("PENDING");
        assertThat(body.get("amountMinor").isIntegralNumber()).isTrue();
        assertThat(body.get("amountMinor").longValue()).isEqualTo(10050);
        assertThat(body.get("currency").stringValue()).isEqualTo("EUR");
        assertThat(body.get("paymentId").stringValue()).isEqualTo(paymentIdFor(checkoutId));
        assertThat(body.get("refundRequested").booleanValue()).isFalse();
        assertThat(body.get("createdAt").isString()).isTrue();
        assertThat(body.get("updatedAt").isString()).isTrue();

        provider.verify(1, postRequestedFor(urlPathEqualTo("/payments"))
                .withHeader("Idempotency-Key", equalTo(checkoutId))
                .withRequestBody(equalToJson("""
                        {"checkoutId": "%s", "amountMinor": 10050, "currency": "EUR",
                         "callbackUrl": "http://localhost:8080/callbacks"}""".formatted(checkoutId))));

        assertThat(checkout(checkoutId).get("paymentId").stringValue()).isEqualTo(paymentIdFor(checkoutId));
    }

    @Test
    void providerUnreachableLeavesCheckoutPendingWithoutPaymentIdAndJobRetriesWithSameKey() {
        provider.stubFor(post(urlPathEqualTo("/payments"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        HttpResponse<String> response = postJson("/checkouts", "{\"amountMinor\": 10050, \"currency\": \"EUR\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(response.body());
        String checkoutId = body.get("checkoutId").stringValue();
        assertThat(body.get("status").stringValue()).isEqualTo("PENDING");
        assertThat(body.has("paymentId")).isTrue();
        assertThat(body.get("paymentId").isNull()).isTrue();

        // Provider is back. The scheduled job retries POST /payments with the same Idempotency-Key.
        provider.resetAll();
        stubCreatePaymentAccepted();
        advance(Duration.ofSeconds(1));
        job.runOnce();

        assertThat(checkout(checkoutId).get("paymentId").stringValue()).isEqualTo(paymentIdFor(checkoutId));
        assertThat(status(checkoutId)).isEqualTo("PENDING");
        provider.verify(1, postRequestedFor(urlPathEqualTo("/payments"))
                .withHeader("Idempotency-Key", equalTo(checkoutId)));
    }

    @Test
    void providerErrorIsTreatedLikeUnreachable() {
        provider.stubFor(post(urlPathEqualTo("/payments")).willReturn(aResponse().withStatus(503)));

        HttpResponse<String> response = postJson("/checkouts", "{\"amountMinor\": 10050, \"currency\": \"EUR\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(json.readTree(response.body()).get("paymentId").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"amountMinor\": 99, \"currency\": \"EUR\"}",
            "{\"amountMinor\": 0, \"currency\": \"EUR\"}",
            "{\"amountMinor\": -100, \"currency\": \"EUR\"}",
            "{\"amountMinor\": 100.5, \"currency\": \"EUR\"}",
            "{\"amountMinor\": 1.0005e3, \"currency\": \"EUR\"}",
            "{\"amountMinor\": \"10050\", \"currency\": \"EUR\"}",
            "{\"amountMinor\": 99999999999999999999, \"currency\": \"EUR\"}",
            "{\"currency\": \"EUR\"}",
            "{\"amountMinor\": 10050}",
            "{\"amountMinor\": 10050, \"currency\": \"eur\"}",
            "{\"amountMinor\": 10050, \"currency\": \"EURO\"}",
            "{\"amountMinor\": 10050, \"currency\": 978}",
            "not json"
    })
    void invalidAmountOrCurrencyIsRejected(String body) {
        HttpResponse<String> response = postJson("/checkouts", body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout", Integer.class)).isZero();
        provider.verify(0, postRequestedFor(urlPathEqualTo("/payments")));
    }

    @Test
    void smallestAllowedAmountIsAccepted() {
        assertThat(postJson("/checkouts", "{\"amountMinor\": 100, \"currency\": \"USD\"}").statusCode())
                .isEqualTo(201);
    }

    @Test
    void unknownCheckoutIs404() {
        String unknown = UUID.randomUUID().toString();
        assertThat(getPath("/checkouts/" + unknown).statusCode()).isEqualTo(404);
        assertThat(getPath("/checkouts/" + unknown + "/events").statusCode()).isEqualTo(404);
        assertThat(getPath("/checkouts/not-a-uuid").statusCode()).isEqualTo(404);
    }

    @Test
    void newCheckoutHasNoEvents() {
        String checkoutId = createCheckout(10050, "EUR");
        assertThat(events(checkoutId)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "http://localhost:5174"})
    void corsAllowsTheWebAndMobileApps(String origin) {
        HttpResponse<String> preflight = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/checkouts"))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));

        assertThat(preflight.statusCode()).isEqualTo(200);
        assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).hasValue(origin);

        String checkoutId = createCheckout(10050, "EUR");
        HttpResponse<String> get = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/checkouts/" + checkoutId))
                .header("Origin", origin).GET());
        assertThat(get.headers().firstValue("Access-Control-Allow-Origin")).hasValue(origin);
    }

    @Test
    void corsRejectsOtherOrigins() {
        HttpResponse<String> preflight = send(HttpRequest.newBuilder(URI.create(baseUrl() + "/checkouts"))
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));

        assertThat(preflight.statusCode()).isEqualTo(403);
        assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }
}
