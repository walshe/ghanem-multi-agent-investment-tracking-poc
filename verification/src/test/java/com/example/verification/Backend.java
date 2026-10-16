package com.example.verification;

import static io.restassured.RestAssured.given;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;

/**
 * <b>Responsibility:</b> talk HTTP to the checkout backend, exactly as described in
 * {@code contract/checkout-api.yaml}, and turn its JSON into simple Java records.
 *
 * <p>Each method calls one endpoint and checks only the HTTP status the contract promises
 * for a normal call (for example 201 for POST /checkouts). It makes no decisions about
 * whether a checkout's status or events are <em>correct</em>; that is the job of
 * {@link CheckoutScenariosTest}.
 *
 * <p>It does not call the provider (see {@link Provider}), and it gets its base URL from
 * {@link TestConfig}.
 */
final class Backend {

    private static final RequestSpecification SPEC = new RequestSpecBuilder()
            .setBaseUri(TestConfig.BACKEND_URL)
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .build();

    private Backend() {
    }

    /** A checkout as returned by GET /checkouts/{checkoutId}. Amounts are integer minor units. */
    record Checkout(String checkoutId, String status, long amountMinor, String currency,
                    String paymentId, boolean refundRequested) {
    }

    /** An entry from GET /checkouts/{checkoutId}/events. */
    record Event(String eventId, String status, String outcome, Long amountMinor) {
    }

    /** POST /checkouts. Expects 201 and a PENDING checkout. */
    static Checkout createCheckout(long amountMinor, String currency) {
        Map<String, Object> json = given(SPEC)
                .body(Map.of("amountMinor", amountMinor, "currency", currency))
                .when()
                .post("/checkouts")
                .then()
                .log().ifValidationFails(LogDetail.ALL)
                .statusCode(201)
                .extract().jsonPath().getMap("$");
        return toCheckout(json);
    }

    /** GET /checkouts/{checkoutId}. Expects 200. */
    static Checkout getCheckout(String checkoutId) {
        Map<String, Object> json = given(SPEC)
                .when()
                .get("/checkouts/{checkoutId}", checkoutId)
                .then()
                .log().ifValidationFails(LogDetail.ALL)
                .statusCode(200)
                .extract().jsonPath().getMap("$");
        return toCheckout(json);
    }

    /** GET /checkouts/{checkoutId}/events. Expects 200. Oldest first. */
    static List<Event> getEvents(String checkoutId) {
        List<Map<String, Object>> json = given(SPEC)
                .when()
                .get("/checkouts/{checkoutId}/events", checkoutId)
                .then()
                .log().ifValidationFails(LogDetail.ALL)
                .statusCode(200)
                .extract().jsonPath().getList("$");
        return json.stream()
                .map(e -> new Event(
                        (String) e.get("eventId"),
                        (String) e.get("status"),
                        (String) e.get("outcome"),
                        e.get("amountMinor") == null ? null : ((Number) e.get("amountMinor")).longValue()))
                .toList();
    }

    private static Checkout toCheckout(Map<String, Object> json) {
        Object refund = json.get("refundRequested");
        return new Checkout(
                (String) json.get("checkoutId"),
                (String) json.get("status"),
                ((Number) json.get("amountMinor")).longValue(),
                (String) json.get("currency"),
                (String) json.get("paymentId"),
                Boolean.TRUE.equals(refund));
    }
}
