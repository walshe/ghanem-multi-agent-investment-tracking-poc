package poc.checkout.domain;

/** What was done with a payment event (audit trail). */
public enum EventOutcome {
    APPLIED,
    IGNORED_STALE,
    FLAGGED
}
