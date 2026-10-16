package poc.checkout.callback;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import poc.checkout.domain.PaymentEventStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses a PaymentEvent body. Only called after the signature has been verified.
 * Reads a JSON tree and checks types by hand so an amount like 100.5 is rejected, not truncated.
 */
@Component
public class CallbackParser {

    /** A parsed PaymentEvent from the provider. */
    public record Callback(
            String eventId,
            String checkoutId,
            String paymentId,
            PaymentEventStatus status,
            long amountMinor,
            String currency,
            Instant occurredAt) {
    }

    public static class InvalidCallbackException extends RuntimeException {
        InvalidCallbackException(String message) {
            super(message);
        }
    }

    private final ObjectMapper json;

    public CallbackParser(ObjectMapper json) {
        this.json = json;
    }

    public Callback parse(byte[] rawBody) {
        JsonNode root;
        try {
            root = json.readTree(rawBody);
        } catch (JacksonException e) {
            throw new InvalidCallbackException("Body is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidCallbackException("Body must be a JSON object");
        }
        String statusText = requiredString(root, "status");
        PaymentEventStatus status = PaymentEventStatus.parse(statusText)
                .orElseThrow(() -> new InvalidCallbackException("Unknown status " + statusText));
        JsonNode amount = root.get("amountMinor");
        if (amount == null || !amount.isIntegralNumber() || !amount.canConvertToLong()) {
            throw new InvalidCallbackException("amountMinor must be an integer");
        }
        Instant occurredAt;
        try {
            occurredAt = OffsetDateTime.parse(requiredString(root, "occurredAt")).toInstant();
        } catch (DateTimeParseException e) {
            throw new InvalidCallbackException("occurredAt must be a date-time");
        }
        return new Callback(
                requiredString(root, "eventId"),
                requiredString(root, "checkoutId"),
                requiredString(root, "paymentId"),
                status,
                amount.longValue(),
                requiredString(root, "currency"),
                occurredAt);
    }

    private static String requiredString(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isString() || node.stringValue().isEmpty()) {
            throw new InvalidCallbackException(field + " is required");
        }
        return node.stringValue();
    }
}
