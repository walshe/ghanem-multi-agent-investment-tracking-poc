package com.example.provider.payment;

import com.example.provider.callback.SignedCallback;
import com.example.provider.chaos.Chaos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

/**
 * Responsibility: hold the in-memory state of one payment.
 *
 * <p>The fixed fields never change. Everything mutable is guarded by the payment's own monitor
 * ({@code synchronized (payment)}). State changes are made only by
 * {@link PaymentService}, which owns the rules; this class just stores them.
 */
public final class Payment {

    private final String id;
    private final UUID checkoutId;
    private final long amountMinor;
    private final String currency;
    private final String callbackUrl;
    /** Chaos settings at the moment this payment was created. */
    private final Chaos chaos;

    private volatile PaymentStatus status = PaymentStatus.PENDING;
    private int refundCount;
    /** Refund Idempotency-Keys that produced the refund. */
    private final Set<String> refundKeys = new HashSet<>();
    /** In reverse mode: the PROCESSING callbacks, held back until after the final one. */
    private final List<SignedCallback> heldCallbacks = new ArrayList<>();
    private ScheduledFuture<?> nextChange;

    Payment(String id, UUID checkoutId, long amountMinor, String currency, String callbackUrl, Chaos chaos) {
        this.id = id;
        this.checkoutId = checkoutId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.callbackUrl = callbackUrl;
        this.chaos = chaos;
    }

    public String id() { return id; }
    public UUID checkoutId() { return checkoutId; }
    public long amountMinor() { return amountMinor; }
    public String currency() { return currency; }
    public String callbackUrl() { return callbackUrl; }
    public Chaos chaos() { return chaos; }
    public PaymentStatus status() { return status; }

    /** False once cancelled: no further changes or callbacks, retries included. */
    public boolean wantsCallbacks() {
        return status != PaymentStatus.CANCELLED;
    }

    void status(PaymentStatus status) { this.status = status; }
    int refundCount() { return refundCount; }
    void refundCount(int refundCount) { this.refundCount = refundCount; }
    Set<String> refundKeys() { return refundKeys; }
    List<SignedCallback> heldCallbacks() { return heldCallbacks; }

    void nextChange(ScheduledFuture<?> future) { this.nextChange = future; }

    void cancelNextChange() {
        if (nextChange != null) {
            nextChange.cancel(false);
            nextChange = null;
        }
    }

    synchronized PaymentView view() {
        return new PaymentView(id, checkoutId, status, amountMinor, currency, refundCount);
    }
}
