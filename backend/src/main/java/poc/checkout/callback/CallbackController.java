package poc.checkout.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import poc.checkout.callback.CallbackParser.Callback;
import poc.checkout.callback.CallbackParser.InvalidCallbackException;
import poc.checkout.provider.ProviderException;

/**
 * <b>Responsibility:</b> the HTTP layer for POST /callbacks. It runs the steps in the right order
 * and turns each result into the status code the provider expects.
 *
 * <p>Order: (1) verify the signature on the raw body bytes, then parse, then pass the event on for
 * steps 2 to 5. Status codes: 401 for a bad signature (nothing stored), 400 for a signed but
 * malformed body, 200 for anything processed (including duplicate, stale and flagged), 500 when
 * nothing was stored and the provider should retry.
 *
 * <p><b>Not responsible for:</b> the HMAC itself ({@link SignatureVerifier}), JSON parsing
 * ({@link CallbackParser}), or the status rules ({@link CallbackService}).
 */
@RestController
public class CallbackController {

    private static final Logger log = LoggerFactory.getLogger(CallbackController.class);

    private final SignatureVerifier signatures;
    private final CallbackParser parser;
    private final CallbackService service;

    public CallbackController(SignatureVerifier signatures, CallbackParser parser, CallbackService service) {
        this.signatures = signatures;
        this.parser = parser;
        this.service = service;
    }

    /**
     * The body is taken as raw bytes so the signature is checked on exactly what was sent,
     * before any JSON parsing.
     */
    @PostMapping("/callbacks")
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "X-Signature", required = false) String signature,
            @RequestBody(required = false) byte[] rawBody) {
        byte[] body = rawBody == null ? new byte[0] : rawBody;

        // Step 1. Signature. Missing or wrong: 401, store nothing.
        if (!signatures.isValid(body, signature)) {
            log.warn("Rejected callback with {} signature", signature == null ? "missing" : "invalid");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Callback callback;
        try {
            callback = parser.parse(body);
        } catch (InvalidCallbackException e) {
            log.warn("Rejected signed but malformed callback: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        // Steps 2 to 5. Duplicates, stale and flagged events all return 200 on purpose,
        // so the provider stops retrying.
        try {
            service.process(callback);
            return ResponseEntity.ok().build();
        } catch (ConcurrencyFailureException | ProviderException e) {
            // Nothing was stored. A non-2xx answer makes the provider retry with the same eventId.
            log.error("Could not process callback {}; asking the provider to retry", callback.eventId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
