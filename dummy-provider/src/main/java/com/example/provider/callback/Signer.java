package com.example.provider.callback;

import com.example.provider.ProviderProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Signs callback bodies: lowercase hex HMAC-SHA256 of the exact body bytes. */
@Component
public class Signer {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    /** A random key that is never the real secret. Used for badSignature and validSignature=false. */
    private final byte[] wrongSecret;

    public Signer(ProviderProperties properties) {
        this.secret = properties.signingSecret().getBytes(StandardCharsets.UTF_8);
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        this.wrongSecret = random;
    }

    public String sign(byte[] body) {
        return hmacHex(secret, body);
    }

    public String signWithWrongSecret(byte[] body) {
        return hmacHex(wrongSecret, body);
    }

    public static String hmacHex(byte[] key, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }
}
