package poc.checkout.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import poc.checkout.config.CheckoutProperties;
import poc.checkout.domain.Checkout;
import poc.checkout.domain.PaymentEvent;
import poc.checkout.persistence.CheckoutRepository;
import poc.checkout.persistence.PaymentEventRepository;
import poc.checkout.provider.ProviderClient;
import poc.checkout.provider.ProviderException;
import poc.checkout.provider.ProviderPayment;

/**
 * <b>Responsibility:</b> create checkouts and read them back. This is "Creating a checkout" in
 * contract/states.md.
 *
 * <p>Saves the checkout as PENDING, asks the provider for a payment, and stores the paymentId. If the
 * provider can't be reached, the checkout stays PENDING without a paymentId; {@link #requestPayment}
 * is then retried by the scheduled job with the same Idempotency-Key.
 *
 * <p><b>Not responsible for:</b> status changes after creation (callbacks and the job), or HTTP
 * validation ({@link poc.checkout.api.CheckoutController}).
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final CheckoutRepository checkouts;
    private final PaymentEventRepository events;
    private final ProviderClient provider;
    private final CheckoutProperties properties;
    private final Clock clock;

    public CheckoutService(CheckoutRepository checkouts, PaymentEventRepository events, ProviderClient provider,
                           CheckoutProperties properties, Clock clock) {
        this.checkouts = checkouts;
        this.events = events;
        this.provider = provider;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * contract/states.md, "Creating a checkout":
     * 1. save as PENDING, 2. POST /payments with Idempotency-Key = checkoutId, 3. store the paymentId.
     * If the provider can't be reached the checkout stays PENDING without a paymentId and the
     * scheduled job retries step 2 with the same key.
     */
    public Checkout create(long amountMinor, String currency) {
        Checkout checkout = Checkout.newPending(UUID.randomUUID(), amountMinor, currency, now());
        checkouts.insert(checkout);
        requestPayment(checkout);
        return checkouts.findById(checkout.id()).orElseThrow();
    }

    /** Steps 2 and 3 of creating a checkout. Also used by the scheduled job. Never throws for provider errors. */
    public void requestPayment(Checkout checkout) {
        try {
            ProviderPayment payment = provider.createPayment(checkout.id(), checkout.amountMinor(),
                    checkout.currency(), properties.callbackUrl());
            checkouts.setPaymentIdIfMissing(checkout.id(), payment.paymentId(), now());
        } catch (ProviderException e) {
            log.warn("Provider did not accept the payment for checkout {}; it stays PENDING and will be retried: {}",
                    checkout.id(), e.getMessage());
        }
    }

    public Optional<Checkout> find(UUID checkoutId) {
        return checkouts.findById(checkoutId);
    }

    public List<PaymentEvent> events(UUID checkoutId) {
        return events.findByCheckoutId(checkoutId.toString());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
