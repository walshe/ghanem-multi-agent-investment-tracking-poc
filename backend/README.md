# Checkout backend

Spring Boot 4 / Java 21 service for the checkout status PoC. Implements `contract/checkout-api.yaml`
and the rules in `contract/states.md`. Port 8080.

## Run

```bash
set -a; source ../.env; set +a        # PROVIDER_SIGNING_SECRET is required; the app won't start without it
./mvnw spring-boot:run -Dspring-boot.run.profiles=fast
```

The `fast` profile uses the test timings (reconcile 5s, expire 10s, job every 2s).
The provider URL is `provider.base-url` (default `http://localhost:8081`).
The database is in-memory H2, so data is lost on restart. Schema: `src/main/resources/db/migration`.

## Tests

```bash
./mvnw test
```

The provider is faked with WireMock. Time is controlled with a test clock, and the scheduled job is
called directly, so the tests don't wait for real minutes.

## Where the rules live

| Rule (states.md) | Code |
|---|---|
| Allowed status changes | `domain/CheckoutStatus` |
| Creating a checkout | `service/CheckoutService` |
| Callback step 1 (signature, raw bytes) | `callback/CallbackController`, `callback/SignatureVerifier` |
| Callback steps 2–5 (one transaction, retry once) | `callback/CallbackService`, `service/RetryingTransaction` |
| Refunds (at most one request per checkout) | `service/RefundService` |
| Scheduled job | `job/CheckoutJob` |
| Optimistic locking (`version`) | `persistence/CheckoutRepository#update` |

## Decisions where the contract was silent (agreed before building)

1. A SUCCEEDED callback for an EXPIRED checkout is recorded as `IGNORED_STALE`. A refund is
   requested and the status stays EXPIRED.
2. A callback for an unknown checkoutId is stored as `FLAGGED` and returns 200 (no foreign key on events).
3. A callback whose paymentId differs from the checkout's is `FLAGGED`. If the checkout has no
   paymentId yet, it takes the one from the callback.
4. The refund call happens inside the callback transaction. If it fails, everything rolls back and
   the response is 500, so the provider retries with the same eventId. A second optimistic-locking
   conflict also returns 500.
5. The job retries POST /payments for PENDING checkouts without a paymentId. If one reaches
   expire-after with no paymentId, it is expired without a cancel.

Also: ages are measured from `createdAt`. A cancel that fails with a network error or 5xx is handled
like a 409. The provider's final statuses are SUCCEEDED, FAILED, CANCELLED and REFUNDED. Reconcile
also checks amount and currency. A signed but malformed callback returns 400.
