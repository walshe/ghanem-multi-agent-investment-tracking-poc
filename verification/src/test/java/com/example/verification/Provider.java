package com.example.verification;

import static io.restassured.RestAssured.given;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** HTTP calls to the dummy payment provider, as described in contract/provider-api.yaml. */
final class Provider {

    private static final RequestSpecification SPEC = new RequestSpecBuilder()
            .setBaseUri(TestConfig.PROVIDER_URL)
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .build();

    private Provider() {
    }

    /** A payment as returned by GET /payments/{paymentId}. */
    record Payment(String paymentId, String checkoutId, String status, long amountMinor,
                   String currency, int refundCount) {
    }

    /** One entry of SendCallbackResult.results. */
    record CallbackResult(String eventId, int backendStatus) {
    }

    /** PUT /admin/chaos. Applies to payments created after this call. Expects 204. */
    static void setChaos(List<String> modes, int delaySeconds) {
        given(SPEC)
                .body(Map.of("modes", modes, "delaySeconds", delaySeconds))
                .when()
                .put("/admin/chaos")
                .then()
                .log().ifValidationFails(LogDetail.ALL)
                .statusCode(204);
    }

    /** Resets chaos to the defaults every test starts from. */
    static void resetChaos() {
        setChaos(List.of(), 1);
    }

    /** GET /payments/{paymentId}. Expects 200. */
    static Payment getPayment(String paymentId) {
        Map<String, Object> json = given(SPEC)
                .when()
                .get("/payments/{paymentId}", paymentId)
                .then()
                .log().ifValidationFails(LogDetail.ALL)
                .statusCode(200)
                .extract().jsonPath().getMap("$");
        return new Payment(
                (String) json.get("paymentId"),
                (String) json.get("checkoutId"),
                (String) json.get("status"),
                ((Number) json.get("amountMinor")).longValue(),
                (String) json.get("currency"),
                ((Number) json.get("refundCount")).intValue());
    }

    /** Builder-style options for POST /admin/send-callback. Null fields are left out of the request. */
    static final class CallbackRequest {
        private final String paymentId;
        private final String status;
        private String eventId;
        private Long amountMinor;
        private Boolean validSignature;
        private Integer copies;

        private CallbackRequest(String paymentId, String status) {
            this.paymentId = paymentId;
            this.status = status;
        }

        static CallbackRequest of(String paymentId, String status) {
            return new CallbackRequest(paymentId, status);
        }

        CallbackRequest eventId(String value) {
            this.eventId = value;
            return this;
        }

        CallbackRequest amountMinor(long value) {
            this.amountMinor = value;
            return this;
        }

        CallbackRequest validSignature(boolean value) {
            this.validSignature = value;
            return this;
        }

        CallbackRequest copies(int value) {
            this.copies = value;
            return this;
        }

        Map<String, Object> toJson() {
            Map<String, Object> body = new HashMap<>();
            body.put("paymentId", paymentId);
            body.put("status", status);
            if (eventId != null) {
                body.put("eventId", eventId);
            }
            if (amountMinor != null) {
                body.put("amountMinor", amountMinor);
            }
            if (validSignature != null) {
                body.put("validSignature", validSignature);
            }
            if (copies != null) {
                body.put("copies", copies);
            }
            return body;
        }
    }

    /** POST /admin/send-callback. Expects 200 and returns what the backend answered for each copy. */
    static List<CallbackResult> sendCallback(CallbackRequest request) {
        List<Map<String, Object>> results = given(SPEC)
                .body(request.toJson())
                .when()
                .post("/admin/send-callback")
                .then()
                .log().ifValidationFails(LogDetail.ALL)
                .statusCode(200)
                .extract().jsonPath().getList("results");
        return results.stream()
                .map(r -> new CallbackResult(
                        (String) r.get("eventId"),
                        ((Number) r.get("backendStatus")).intValue()))
                .toList();
    }
}
