package poc.checkout.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import poc.checkout.domain.Checkout;
import poc.checkout.domain.CheckoutStatus;
import poc.checkout.domain.EventOutcome;
import poc.checkout.domain.PaymentEvent;
import poc.checkout.domain.PaymentEventStatus;
import poc.checkout.service.CheckoutService;
import tools.jackson.databind.JsonNode;

/** The "checkouts" and "audit" endpoints of contract/checkout-api.yaml. */
@RestController
public class CheckoutController {

    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final long MIN_AMOUNT_MINOR = 100;

    /** Checkout schema. */
    public record CheckoutResponse(
            UUID checkoutId,
            CheckoutStatus status,
            long amountMinor,
            String currency,
            String paymentId,
            boolean refundRequested,
            Instant createdAt,
            Instant updatedAt) {

        static CheckoutResponse of(Checkout c) {
            return new CheckoutResponse(c.id(), c.status(), c.amountMinor(), c.currency(), c.paymentId(),
                    c.refundRequested(), c.createdAt(), c.updatedAt());
        }
    }

    /** CheckoutEvent schema. */
    public record CheckoutEventResponse(
            String eventId,
            PaymentEventStatus status,
            long amountMinor,
            EventOutcome outcome,
            Instant receivedAt) {

        static CheckoutEventResponse of(PaymentEvent e) {
            return new CheckoutEventResponse(e.eventId(), e.status(), e.amountMinor(), e.outcome(), e.receivedAt());
        }
    }

    private final CheckoutService service;

    public CheckoutController(CheckoutService service) {
        this.service = service;
    }

    /**
     * The body is read as a JSON tree and checked by hand so that amounts like 100.5 or "100"
     * are rejected instead of being coerced to an integer.
     */
    @PostMapping("/checkouts")
    public ResponseEntity<?> create(@RequestBody JsonNode body) {
        JsonNode amount = body == null ? null : body.get("amountMinor");
        JsonNode currency = body == null ? null : body.get("currency");
        if (amount == null || !amount.isIntegralNumber() || !amount.canConvertToLong()
                || amount.longValue() < MIN_AMOUNT_MINOR) {
            return badRequest("amountMinor must be an integer number of minor units, at least " + MIN_AMOUNT_MINOR);
        }
        if (currency == null || !currency.isString() || !CURRENCY.matcher(currency.stringValue()).matches()) {
            return badRequest("currency must be a 3-letter ISO 4217 code, e.g. EUR");
        }
        Checkout checkout = service.create(amount.longValue(), currency.stringValue());
        return ResponseEntity.created(URI.create("/checkouts/" + checkout.id())).body(CheckoutResponse.of(checkout));
    }

    @GetMapping("/checkouts/{checkoutId}")
    public ResponseEntity<CheckoutResponse> get(@PathVariable String checkoutId) {
        return parseUuid(checkoutId)
                .flatMap(service::find)
                .map(c -> ResponseEntity.ok(CheckoutResponse.of(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/checkouts/{checkoutId}/events")
    public ResponseEntity<List<CheckoutEventResponse>> events(@PathVariable String checkoutId) {
        Optional<Checkout> checkout = parseUuid(checkoutId).flatMap(service::find);
        if (checkout.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(service.events(checkout.get().id()).stream().map(CheckoutEventResponse::of).toList());
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
