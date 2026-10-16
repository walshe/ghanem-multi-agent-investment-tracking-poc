# Checkout statuses and payment rules

This file is owned by a person, not an agent. Agents follow it exactly.

## Statuses

| Status | Meaning | Final? |
|---|---|---|
| PENDING | Checkout created. Payment not started or not confirmed yet. | No |
| PROCESSING | The provider is processing the payment. | No |
| SUCCEEDED | Payment taken. The investment is made. | Yes |
| FAILED | Payment failed. Nothing was taken. | Yes |
| EXPIRED | No payment within the time limit. No investment was made. | Yes |

## Allowed status changes

| From | To |
|---|---|
| PENDING | PROCESSING, SUCCEEDED, FAILED, EXPIRED |
| PROCESSING | SUCCEEDED, FAILED |

Every other change is not allowed. Final statuses never change.
PENDING can go straight to SUCCEEDED or FAILED, because the PROCESSING callback may arrive
late or not at all.

## Creating a checkout

1. Save the checkout as PENDING.
2. Call the provider's POST /payments with `Idempotency-Key: {checkoutId}`.
3. Store the returned paymentId on the checkout.

If the provider can't be reached, the checkout stays PENDING without a paymentId. The
scheduled job retries POST /payments with the same Idempotency-Key, so the customer can
never be charged twice.

## Processing a callback (POST /callbacks)

Do these steps in order:

1. **Signature.** `X-Signature` must equal the lowercase hex HMAC-SHA256 of the raw request
   body, keyed with the signing secret. Missing or wrong: return 401 and store nothing.
2. **Duplicate.** Insert the event. `eventId` has a unique constraint and is the
   idempotency key. If it already exists: return 200 and change nothing.
3. **Amount.** `amountMinor` and `currency` must match the checkout. If not: record the
   event as FLAGGED, change nothing, return 200.
4. **Order.** Apply the status only if the table above allows the change from the current
   status. If not: record the event as IGNORED_STALE, change nothing, return 200.
5. **Late success.** A SUCCEEDED callback for an EXPIRED checkout means the customer paid
   for an investment we did not make. Request a refund (see below). The status stays EXPIRED.

Steps 2 to 5 run in one database transaction. Checkouts have a version column (optimistic
locking), so two callbacks for the same checkout can't both apply. Retry once on conflict.

Duplicates, stale events and flagged events return 200 on purpose: an error would make the
provider keep retrying a callback we have already dealt with.

## Late and missing callbacks (scheduled job)

The job runs every `checkout.job-interval`. For each checkout:

1. **Reconcile.** Not final and older than `checkout.reconcile-after`: ask the provider
   (GET /payments/{paymentId}). If the provider's status is an allowed change, apply it
   and record it as an event with eventId `reconcile-{checkoutId}-{status}`.
2. **Expire.** Still PENDING and older than `checkout.expire-after`: mark it EXPIRED and
   call POST /payments/{paymentId}/cancel.
3. **Refused cancel.** If the provider answers the cancel with 409, the payment may still
   complete. Keep checking that payment on every run until the provider reports a final
   status. If it ends SUCCEEDED, request a refund.
4. **Stuck in PROCESSING.** A checkout still PROCESSING after `checkout.expire-after` is
   never expired automatically, because money may be moving. Log a warning so a person
   checks it.

| Setting | Default | `fast` profile (for tests) |
|---|---|---|
| checkout.reconcile-after | 10 minutes | 5 seconds |
| checkout.expire-after | 30 minutes | 10 seconds |
| checkout.job-interval | 60 seconds | 2 seconds |

## Refunds

Call POST /payments/{paymentId}/refund with `Idempotency-Key: refund-{checkoutId}`.
The same key always gives the same refund, so retrying can never refund twice.
Record on the checkout that a refund was requested.

## Money

All amounts are integer minor units (cents) with an ISO 4217 currency code, e.g.
`amountMinor: 10050, currency: "EUR"` is EUR 100.50. Never use floating point.

## Audit trail

Every callback that passes the signature check is stored with its outcome:
APPLIED, IGNORED_STALE or FLAGGED. GET /checkouts/{checkoutId}/events returns them.
