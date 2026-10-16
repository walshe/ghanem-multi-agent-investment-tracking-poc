package com.example.provider.payment;

import com.example.provider.ProviderProperties;
import com.example.provider.callback.CallbackSender;
import com.example.provider.callback.CallbackStatus;
import com.example.provider.callback.PaymentEvent;
import com.example.provider.callback.SignedCallback;
import com.example.provider.chaos.Chaos;
import com.example.provider.chaos.ChaosMode;
import com.example.provider.chaos.ChaosSettings;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Responsibility: own the payment rules: create (idempotently), get, cancel, refund, and drive
 * each payment through its timed lifecycle.
 *
 * <pre>
 *   PENDING -> PROCESSING after delaySeconds
 *           -> SUCCEEDED (or FAILED in "fail" mode) finalAfter later
 * </pre>
 *
 * <p>Also the one place that applies the payment-level chaos modes: decides which callbacks a
 * status change produces (drop, duplicate, reverse, badSignature), whether it ends FAILED
 * (fail), and whether cancel is refused (cancelTooLate).
 *
 * <p>Stores payments in memory. Delivery of callbacks is delegated to {@link CallbackSender}.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final ConcurrentMap<String, Payment> paymentsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Payment> paymentsByIdempotencyKey = new ConcurrentHashMap<>();

    private final ChaosSettings chaosSettings;
    private final CallbackSender callbackSender;
    private final ScheduledExecutorService scheduler;
    private final ProviderProperties properties;

    public PaymentService(ChaosSettings chaosSettings, CallbackSender callbackSender,
                          ScheduledExecutorService providerScheduler, ProviderProperties properties) {
        this.chaosSettings = chaosSettings;
        this.callbackSender = callbackSender;
        this.scheduler = providerScheduler;
        this.properties = properties;
    }

    public record CreateResult(PaymentView payment, boolean created) {
    }

    /** Creates a payment, or returns the existing one if this Idempotency-Key was used before. */
    public CreateResult create(String idempotencyKey, UUID checkoutId, long amountMinor, String currency,
                               String callbackUrl) {
        Payment candidate = new Payment("pay_" + UUID.randomUUID(), checkoutId, amountMinor, currency,
                callbackUrl, chaosSettings.get());
        Payment existing = paymentsByIdempotencyKey.putIfAbsent(idempotencyKey, candidate);
        if (existing != null) {
            return new CreateResult(existing.view(), false);
        }
        paymentsById.put(candidate.id(), candidate);
        log.info("Created {} for checkout {} ({} {}) with chaos {}", candidate.id(), checkoutId,
                amountMinor, currency, candidate.chaos());
        synchronized (candidate) {
            candidate.nextChange(scheduler.schedule(() -> toProcessing(candidate),
                    candidate.chaos().delaySeconds(), TimeUnit.SECONDS));
        }
        return new CreateResult(candidate.view(), true);
    }

    public Optional<Payment> find(String paymentId) {
        return Optional.ofNullable(paymentsById.get(paymentId));
    }

    public PaymentView get(String paymentId) {
        return require(paymentId).view();
    }

    public PaymentView cancel(String paymentId) {
        Payment payment = require(paymentId);
        synchronized (payment) {
            if (payment.chaos().has(ChaosMode.CANCEL_TOO_LATE)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Too late to cancel (cancelTooLate). The payment will still complete.");
            }
            switch (payment.status()) {
                case CANCELLED -> { /* already cancelled: same answer */ }
                case PENDING, PROCESSING -> {
                    payment.status(PaymentStatus.CANCELLED);
                    payment.cancelNextChange();
                    payment.heldCallbacks().clear();
                    log.info("Cancelled {}", payment.id());
                }
                default -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Too late to cancel. The payment is " + payment.status() + ".");
            }
            return payment.view();
        }
    }

    public PaymentView refund(String paymentId, String idempotencyKey) {
        Payment payment = require(paymentId);
        synchronized (payment) {
            if (payment.refundKeys().contains(idempotencyKey)) {
                return payment.view();
            }
            if (payment.status() != PaymentStatus.SUCCEEDED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Nothing to refund. The payment is " + payment.status() + ".");
            }
            payment.status(PaymentStatus.REFUNDED);
            payment.refundCount(payment.refundCount() + 1);
            payment.refundKeys().add(idempotencyKey);
            log.info("Refunded {}", payment.id());
            return payment.view();
        }
    }

    private Payment require(String paymentId) {
        return find(paymentId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment with id " + paymentId));
    }

    private void toProcessing(Payment payment) {
        List<SignedCallback> toSend;
        synchronized (payment) {
            if (payment.status() != PaymentStatus.PENDING) {
                return;
            }
            payment.status(PaymentStatus.PROCESSING);
            List<SignedCallback> callbacks = callbacksFor(payment, CallbackStatus.PROCESSING);
            if (payment.chaos().has(ChaosMode.REVERSE)) {
                payment.heldCallbacks().addAll(callbacks);
                toSend = List.of();
            } else {
                toSend = callbacks;
            }
            payment.nextChange(scheduler.schedule(() -> toFinal(payment),
                    properties.finalAfter().toMillis(), TimeUnit.MILLISECONDS));
        }
        log.info("{} is PROCESSING", payment.id());
        callbackSender.sendInOrder(toSend, payment::wantsCallbacks);
    }

    private void toFinal(Payment payment) {
        List<SignedCallback> toSend = new ArrayList<>();
        PaymentStatus finalStatus;
        synchronized (payment) {
            if (payment.status() != PaymentStatus.PROCESSING) {
                return;
            }
            boolean fail = payment.chaos().has(ChaosMode.FAIL);
            finalStatus = fail ? PaymentStatus.FAILED : PaymentStatus.SUCCEEDED;
            payment.status(finalStatus);
            payment.nextChange(null);
            toSend.addAll(callbacksFor(payment, fail ? CallbackStatus.FAILED : CallbackStatus.SUCCEEDED));
            // reverse: the held PROCESSING callbacks go out after the final one.
            toSend.addAll(payment.heldCallbacks());
            payment.heldCallbacks().clear();
        }
        log.info("{} is {}", payment.id(), finalStatus);
        callbackSender.sendInOrder(toSend, payment::wantsCallbacks);
    }

    /**
     * The callbacks for one status change: one event with a new eventId, serialised and signed
     * once. Empty in drop mode, sent twice in duplicate mode.
     */
    private List<SignedCallback> callbacksFor(Payment payment, CallbackStatus status) {
        Chaos chaos = payment.chaos();
        if (chaos.has(ChaosMode.DROP)) {
            return List.of();
        }
        PaymentEvent event = new PaymentEvent(UUID.randomUUID().toString(), payment.checkoutId(), payment.id(),
                status, payment.amountMinor(), payment.currency(), now());
        SignedCallback callback = callbackSender.prepare(payment.callbackUrl(), event,
                !chaos.has(ChaosMode.BAD_SIGNATURE));
        return chaos.has(ChaosMode.DUPLICATE) ? List.of(callback, callback) : List.of(callback);
    }

    public static String now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
    }
}
