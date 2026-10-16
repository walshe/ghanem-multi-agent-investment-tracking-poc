package poc.checkout.callback;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import poc.checkout.config.ProviderProperties;

/** Step 1 of processing a callback: X-Signature must be the lowercase hex HMAC-SHA256 of the raw body. */
@Component
public class SignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public SignatureVerifier(ProviderProperties properties) {
        String secret = properties.signingSecret();
        // Spring Boot binds an unresolvable placeholder as the literal text "${PROVIDER_SIGNING_SECRET}".
        // That would make a publicly known string the secret, so refuse to start instead.
        if (secret == null || secret.isBlank() || secret.contains("${")) {
            throw new IllegalStateException("provider.signing-secret is not set. Set PROVIDER_SIGNING_SECRET.");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** @param rawBody the exact request body bytes, before any parsing */
    public boolean isValid(byte[] rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isEmpty()) {
            return false;
        }
        String expected = sign(rawBody);
        // Constant-time comparison. Exact match: the contract says lowercase hex.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                signatureHeader.getBytes(StandardCharsets.US_ASCII));
    }

    public String sign(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
