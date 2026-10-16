package poc.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/** states.md, callback step 1: Signature. Missing or wrong: return 401 and store nothing. */
class CallbackSignatureTest extends IntegrationTestBase {

    @Test
    void missingSignatureIs401AndStoresNothing() {
        String checkoutId = createCheckout(10050, "EUR");
        String body = callbackBody(newEventId(), checkoutId, paymentIdFor(checkoutId), "SUCCEEDED", 10050, "EUR");

        HttpResponse<String> response = postCallback(body, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(eventRowCount()).isZero();
        assertThat(status(checkoutId)).isEqualTo("PENDING");
    }

    @Test
    void wrongSignatureIs401AndStoresNothing() {
        String checkoutId = createCheckout(10050, "EUR");
        String body = callbackBody(newEventId(), checkoutId, paymentIdFor(checkoutId), "SUCCEEDED", 10050, "EUR");

        assertThat(postCallback(body, "00".repeat(32)).statusCode()).isEqualTo(401);
        assertThat(postCallback(body, "").statusCode()).isEqualTo(401);
        // A valid signature, but for different bytes (one trailing space).
        assertThat(postCallback(body, sign(body + " ")).statusCode()).isEqualTo(401);

        assertThat(eventRowCount()).isZero();
        assertThat(status(checkoutId)).isEqualTo("PENDING");
    }

    @Test
    void signatureMustBeLowercaseHex() {
        String checkoutId = createCheckout(10050, "EUR");
        String body = callbackBody(newEventId(), checkoutId, paymentIdFor(checkoutId), "SUCCEEDED", 10050, "EUR");

        assertThat(postCallback(body, sign(body).toUpperCase(Locale.ROOT)).statusCode()).isEqualTo(401);
        assertThat(eventRowCount()).isZero();
    }

    @Test
    void signatureIsCheckedAgainstTheRawBytesNotReserializedJson() {
        String checkoutId = createCheckout(10050, "EUR");
        // Unusual whitespace, field order and a non-ASCII escape: any re-serialization would change the bytes.
        String body = """
                {
                  "status" : "SUCCEEDED",   "eventId":"%s",
                  "checkoutId":"%s", "paymentId":"%s",
                  "amountMinor":10050, "currency":"EUR", "occurredAt":"2026-10-16T10:00:00Z",
                  "note":"caf\\u00e9 é"
                }
                """.formatted(newEventId(), checkoutId, paymentIdFor(checkoutId));

        HttpResponse<String> response = postCallback(body, sign(body));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(status(checkoutId)).isEqualTo("SUCCEEDED");
    }

    @Test
    void invalidSignatureIsRejectedBeforeTheBodyIsParsed() {
        // Not JSON at all: a 401 (not 400) proves the signature is checked first.
        assertThat(postCallback("this is not json", "abc").statusCode()).isEqualTo(401);
        assertThat(eventRowCount()).isZero();
    }

    @Test
    void signedButMalformedBodyIs400AndStoresNothing() {
        String checkoutId = createCheckout(10050, "EUR");
        String notJson = "this is not json";
        String fractionalAmount = callbackBody(newEventId(), checkoutId, paymentIdFor(checkoutId), "SUCCEEDED", 0, "EUR")
                .replace("\"amountMinor\":0", "\"amountMinor\":100.50");
        String unknownStatus = callbackBody(newEventId(), checkoutId, paymentIdFor(checkoutId), "EXPIRED", 10050, "EUR");

        assertThat(postCallback(notJson).statusCode()).isEqualTo(400);
        assertThat(postCallback(fractionalAmount).statusCode()).isEqualTo(400);
        assertThat(postCallback(unknownStatus).statusCode()).isEqualTo(400);
        assertThat(eventRowCount()).isZero();
        assertThat(status(checkoutId)).isEqualTo("PENDING");
    }
}
