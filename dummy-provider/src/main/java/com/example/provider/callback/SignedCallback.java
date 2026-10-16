package com.example.provider.callback;

import java.net.URI;

/**
 * A callback ready to send. The body bytes are serialised once and the signature is computed
 * over exactly those bytes, so retries and copies send byte-identical requests.
 */
public record SignedCallback(URI url, String eventId, byte[] body, String signature) {
}
