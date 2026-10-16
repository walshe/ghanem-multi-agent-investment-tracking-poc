package poc.checkout.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import poc.checkout.config.ProviderProperties;

class SignatureVerifierTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "${PROVIDER_SIGNING_SECRET}"})
    void refusesToStartWithoutARealSecret(String secret) {
        // Spring Boot binds a missing environment variable as the literal "${PROVIDER_SIGNING_SECRET}".
        assertThatThrownBy(() -> new SignatureVerifier(properties(secret)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROVIDER_SIGNING_SECRET");
    }

    @Test
    void matchesAKnownHmacSha256Vector() {
        // RFC 4231 test case 2.
        SignatureVerifier verifier = new SignatureVerifier(properties("Jefe"));
        byte[] body = "what do ya want for nothing?".getBytes(StandardCharsets.UTF_8);
        String expected = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";

        assertThat(verifier.isValid(body, expected)).isTrue();
        assertThat(verifier.isValid(body, expected.toUpperCase())).isFalse();
        assertThat(verifier.isValid(body, null)).isFalse();
    }

    private static ProviderProperties properties(String secret) {
        return new ProviderProperties("http://localhost:8081", secret, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }
}
