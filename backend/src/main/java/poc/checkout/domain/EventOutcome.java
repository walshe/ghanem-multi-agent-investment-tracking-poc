package poc.checkout.domain;

/**
 * <b>Responsibility:</b> name what was done with a payment event, for the audit trail
 * (EventOutcome in checkout-api.yaml).
 *
 * <ul>
 *   <li>APPLIED: the checkout's status was changed.</li>
 *   <li>IGNORED_STALE: the change wasn't allowed from the current status, so nothing changed.
 *       This includes a late success for an EXPIRED checkout, which also triggers a refund.</li>
 *   <li>FLAGGED: amount, currency, paymentId or checkout didn't match, so nothing changed.</li>
 * </ul>
 */
public enum EventOutcome {
    APPLIED,
    IGNORED_STALE,
    FLAGGED
}
