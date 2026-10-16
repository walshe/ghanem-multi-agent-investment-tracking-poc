package poc.checkout.job;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import poc.checkout.config.CheckoutProperties;
import poc.checkout.domain.Checkout;
import poc.checkout.domain.CheckoutStatus;
import poc.checkout.domain.EventOutcome;
import poc.checkout.domain.PaymentEvent;
import poc.checkout.domain.PaymentEventStatus;
import poc.checkout.persistence.CheckoutRepository;
import poc.checkout.persistence.PaymentEventRepository;
import poc.checkout.provider.ProviderClient;
import poc.checkout.provider.ProviderClient.CancelResult;
import poc.checkout.provider.ProviderException;
import poc.checkout.provider.ProviderPayment;
import poc.checkout.service.CheckoutService;
import poc.checkout.service.RefundService;
import poc.checkout.service.RetryingTransaction;

/**
 * <b>Responsibility:</b> look after checkouts whose callbacks are late or missing. This is
 * "Late and missing callbacks (scheduled job)" in contract/states.md.
 *
 * <p>One {@link #runOnce()} goes through every checkout that still needs attention and, in order:
 * <ol start="0">
 *   <li>retries POST /payments if the provider never accepted the payment;</li>
 *   <li>reconciles a non-final checkout older than reconcile-after with the provider's status;</li>
 *   <li>expires a PENDING checkout older than expire-after and cancels its payment;</li>
 *   <li>follows up a refused cancel until the payment is final, and refunds it if it succeeded;</li>
 *   <li>logs a warning for a checkout stuck in PROCESSING (never expired automatically).</li>
 * </ol>
 * Ages are measured from the checkout's createdAt. One failing checkout doesn't stop the others.
 *
 * <p><b>Not responsible for:</b> when it runs ({@link poc.checkout.config.SchedulingConfig}), the
 * allowed-change table ({@link CheckoutStatus}), or how a refund is made ({@link RefundService}).
 */
@Component
public class CheckoutJob {

    private static final Logger log = LoggerFactory.getLogger(CheckoutJob.class);

    private final CheckoutRepository checkouts;
    private final PaymentEventRepository events;
    private final CheckoutService checkoutService;
    private final RefundService refunds;
    private final ProviderClient provider;
    private final RetryingTransaction transaction;
    private final CheckoutProperties properties;
    private final Clock clock;

    public CheckoutJob(CheckoutRepository checkouts, PaymentEventRepository events, CheckoutService checkoutService,
                       RefundService refunds, ProviderClient provider, RetryingTransaction transaction,
                       CheckoutProperties properties, Clock clock) {
        this.checkouts = checkouts;
        this.events = events;
        this.checkoutService = checkoutService;
        this.refunds = refunds;
        this.provider = provider;
        this.transaction = transaction;
        this.properties = properties;
        this.clock = clock;
    }

    /** One run over every checkout that still needs attention. One failing checkout doesn't stop the others. */
    public void runOnce() {
        for (UUID id : checkouts.findIdsForJob()) {
            try {
                handle(id);
            } catch (RuntimeException e) {
                log.warn("Scheduled job could not handle checkout {}; will try again next run: {}", id, e.toString());
            }
        }
    }

    private void handle(UUID id) {
        Instant now = now();
        Checkout c = load(id);
        boolean pastReconcile = c.createdAt().isBefore(now.minus(properties.reconcileAfter()));
        boolean pastExpire = c.createdAt().isBefore(now.minus(properties.expireAfter()));

        // Creating a checkout: the provider couldn't be reached. Retry POST /payments with the same key.
        if (c.status() == CheckoutStatus.PENDING && c.paymentId() == null && !pastExpire) {
            checkoutService.requestPayment(c);
            c = load(id);
        }

        // 1. Reconcile.
        if (!c.status().isFinal() && pastReconcile && c.paymentId() != null) {
            reconcile(c);
            c = load(id);
        }

        // 2. Expire.
        if (c.status() == CheckoutStatus.PENDING && pastExpire) {
            expire(c);
            c = load(id);
        }

        // 3. Refused cancel: keep checking until the provider reports a final status.
        if (c.status() == CheckoutStatus.EXPIRED && c.cancelRefused()) {
            checkAfterRefusedCancel(c);
        }

        // 4. Stuck in PROCESSING. Never expired automatically: money may be moving.
        if (c.status() == CheckoutStatus.PROCESSING && pastExpire) {
            log.warn("Checkout {} (payment {}) is still PROCESSING more than {} after it was created. "
                    + "Money may be moving, so it is not expired automatically. A person must check it.",
                    c.id(), c.paymentId(), properties.expireAfter());
        }
    }

    /** Asks the provider for the payment's status and applies it if the change is allowed. */
    private void reconcile(Checkout c) {
        ProviderPayment payment = provider.getPayment(c.paymentId());
        Optional<PaymentEventStatus> reported = PaymentEventStatus.parse(payment.status());
        if (reported.isEmpty() || !c.status().canChangeTo(reported.get().toCheckoutStatus())) {
            return;
        }
        if (!c.matchesMoney(payment.amountMinor(), payment.currency())) {
            log.warn("Provider reports {} {} for payment {} but checkout {} is {} {}. Not reconciled.",
                    payment.amountMinor(), payment.currency(), payment.paymentId(), c.id(), c.amountMinor(),
                    c.currency());
            return;
        }
        PaymentEventStatus status = reported.get();
        transaction.run("reconciling checkout " + c.id(), () -> {
            Instant now = now();
            Checkout current = load(c.id());
            if (!current.status().canChangeTo(status.toCheckoutStatus())) {
                return null; // A callback got there first.
            }
            PaymentEvent event = new PaymentEvent("reconcile-" + c.id() + "-" + status, c.id().toString(),
                    payment.paymentId(), status, payment.amountMinor(), payment.currency(), now,
                    EventOutcome.APPLIED, now);
            if (events.insertIfNew(event)) {
                checkouts.update(current.withStatus(status.toCheckoutStatus()), now);
                log.info("Reconciled checkout {}: {} -> {}", c.id(), current.status(), status);
            }
            return null;
        });
    }

    /** Marks the checkout EXPIRED first, then cancels the payment. */
    private void expire(Checkout c) {
        Checkout expired = transaction.run("expiring checkout " + c.id(), () -> {
            Checkout current = load(c.id());
            if (current.status() != CheckoutStatus.PENDING) {
                return null; // A callback got there first.
            }
            return checkouts.update(current.withStatus(CheckoutStatus.EXPIRED), now());
        });
        if (expired == null) {
            return;
        }
        log.info("Checkout {} expired", c.id());
        String paymentId = expired.paymentId();
        if (paymentId == null) {
            // No payment to cancel. If one exists after all, its SUCCEEDED callback triggers a refund.
            return;
        }
        CancelResult result;
        try {
            result = provider.cancelPayment(paymentId);
        } catch (ProviderException e) {
            log.warn("Could not cancel payment {} for expired checkout {}; will keep checking it: {}",
                    paymentId, c.id(), e.getMessage());
            result = CancelResult.REFUSED;
        }
        if (result == CancelResult.REFUSED) {
            transaction.run("marking cancel refused for " + c.id(), () -> {
                Checkout current = load(c.id());
                if (!current.refundRequested() && !current.cancelRefused()) {
                    checkouts.update(current.withCancelRefused(true), now());
                }
                return null;
            });
        }
    }

    /** The cancel was refused, so the payment may still complete. If it ends SUCCEEDED, refund it. */
    private void checkAfterRefusedCancel(Checkout c) {
        ProviderPayment payment = provider.getPayment(c.paymentId());
        if (!payment.isFinal()) {
            return; // Check again next run.
        }
        transaction.run("settling refused cancel for " + c.id(), () -> {
            Checkout current = load(c.id());
            if (!current.cancelRefused()) {
                return null;
            }
            if ("SUCCEEDED".equals(payment.status()) && !current.refundRequested()) {
                refunds.requestRefundOnce(current, now()); // also clears cancelRefused
            } else {
                checkouts.update(current.withCancelRefused(false), now());
            }
            return null;
        });
        log.info("Payment {} for expired checkout {} ended {}", payment.paymentId(), c.id(), payment.status());
    }

    private Checkout load(UUID id) {
        return checkouts.findById(id).orElseThrow();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
