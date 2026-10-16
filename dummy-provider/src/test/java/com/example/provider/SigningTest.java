package com.example.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.provider.callback.Signer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Responsibility: check callback signing: the HMAC itself, that callbacks are signed over the
 * exact bytes sent with the configured secret, and that the body is a valid PaymentEvent.
 */
class SigningTest extends ProviderTestBase {

    @Test
    void hmacMatchesRfc4231TestVector() {
        // RFC 4231, test case 2
        String hex = Signer.hmacHex("Jefe".getBytes(StandardCharsets.UTF_8),
                "what do ya want for nothing?".getBytes(StandardCharsets.UTF_8));
        assertThat(hex).isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void callbacksAreSignedOverTheExactBodyBytesWithTheConfiguredSecret() {
        UUID checkoutId = UUID.randomUUID();
        String paymentId = createPayment(checkoutId.toString(), checkoutId).json().get("paymentId").asString();

        List<FakeBackend.Received> callbacks = awaitCallbacks(paymentId, 2);

        for (FakeBackend.Received r : callbacks) {
            String expected = Signer.hmacHex(TEST_SECRET.getBytes(StandardCharsets.UTF_8), r.body());
            assertThat(r.signature()).isEqualTo(expected).matches("^[0-9a-f]{64}$");
            assertThat(r.answered()).isEqualTo(200);
        }
    }

    @Test
    void callbackBodyIsAPaymentEvent() {
        UUID checkoutId = UUID.randomUUID();
        String paymentId = createPayment(checkoutId.toString(), checkoutId).json().get("paymentId").asString();

        List<FakeBackend.Received> callbacks = awaitCallbacks(paymentId, 2);

        assertThat(callbacks).extracting(FakeBackend.Received::status).containsExactly("PROCESSING", "SUCCEEDED");
        assertThat(callbacks.get(0).eventId()).isNotEqualTo(callbacks.get(1).eventId());
        for (FakeBackend.Received r : callbacks) {
            JsonNode e = r.json();
            assertThat(e.propertyNames()).containsExactlyInAnyOrder(
                    "eventId", "checkoutId", "paymentId", "status", "amountMinor", "currency", "occurredAt");
            assertThat(e.get("checkoutId").asString()).isEqualTo(checkoutId.toString());
            assertThat(e.get("paymentId").asString()).isEqualTo(paymentId);
            assertThat(e.get("amountMinor").isIntegralNumber()).isTrue();
            assertThat(e.get("amountMinor").asLong()).isEqualTo(10050L);
            assertThat(e.get("currency").asString()).isEqualTo("EUR");
            assertThat(e.get("occurredAt").asString()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");
        }
    }
}
