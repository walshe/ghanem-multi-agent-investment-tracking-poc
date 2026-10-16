package poc.checkout.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import poc.checkout.domain.Checkout;
import poc.checkout.persistence.CheckoutRepository;
import poc.checkout.provider.ProviderClient;

/**
 * Refunds a payment that succeeded after its checkout expired (contract/states.md, "Refunds").
 *
 * <p>Must run inside the caller's transaction. The checkout row is updated (and so locked)
 * <em>before</em> the provider is called. A concurrent refund attempt for the same checkout
 * waits on that lock, then fails its version check and, on retry, sees refundRequested = true.
 * So the backend sends at most one refund request per checkout. If the provider call fails,
 * the exception rolls the whole transaction back, including refundRequested.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final CheckoutRepository checkouts;
    private final ProviderClient provider;

    public RefundService(CheckoutRepository checkouts, ProviderClient provider) {
        this.checkouts = checkouts;
        this.provider = provider;
    }

    /**
     * @param checkout the checkout as read in this transaction (its version is checked)
     * @return the updated checkout
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Checkout requestRefundOnce(Checkout checkout, Instant now) {
        if (checkout.refundRequested()) {
            log.info("Refund for checkout {} was already requested; not requesting again", checkout.id());
            return checkout;
        }
        if (checkout.paymentId() == null) {
            throw new IllegalStateException("Checkout " + checkout.id() + " has no paymentId to refund");
        }
        // Record the refund first (takes the row lock, checks the version), then call the provider.
        Checkout updated = checkouts.update(checkout.withRefundRequested(true).withCancelRefused(false), now);
        log.warn("Payment {} succeeded after checkout {} expired. Requesting refund.",
                checkout.paymentId(), checkout.id());
        provider.refundPayment(checkout.paymentId(), checkout.id());
        return updated;
    }
}
