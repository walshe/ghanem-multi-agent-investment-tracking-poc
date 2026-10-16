package com.example.provider.callback;

import java.net.URI;

/**
 * Responsibility: carry one callback that is ready to send: target URL, eventId, body bytes and
 * signature.
 *
 * <p>Immutable. The body is serialised once and the signature is computed over exactly those
 * bytes, so every retry and every copy sends byte-identical requests.
 */
public record SignedCallback(URI url, String eventId, byte[] body, String signature) {
}
