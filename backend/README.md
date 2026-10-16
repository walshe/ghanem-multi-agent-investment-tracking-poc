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

## How it works

### The system

```mermaid
flowchart LR
    web["React app<br/>:5173"]
    mobile["Flutter app<br/>:5174"]
    backend["Checkout backend<br/>:8080"]
    db[("H2 database<br/>checkout, payment_event")]
    provider["Payment provider<br/>:8081"]

    web -- "POST /checkouts<br/>GET /checkouts/{id}" --> backend
    mobile -- "POST /checkouts<br/>GET /checkouts/{id}" --> backend
    backend -- "POST /payments<br/>GET /payments/{id}<br/>POST .../cancel, .../refund" --> provider
    provider -- "POST /callbacks<br/>signed with X-Signature" --> backend
    backend --- db
```

Callbacks can arrive late, out of order, more than once, or not at all. The backend has to
cope with all four.

### Checkout statuses

Only these status changes are allowed (`CheckoutStatus`). SUCCEEDED, FAILED and EXPIRED are final.

```mermaid
stateDiagram-v2
    [*] --> PENDING: POST /checkouts
    PENDING --> PROCESSING: callback or reconcile
    PENDING --> SUCCEEDED: callback or reconcile
    PENDING --> FAILED: callback or reconcile
    PENDING --> EXPIRED: job, after expire-after
    PROCESSING --> SUCCEEDED: callback or reconcile
    PROCESSING --> FAILED: callback or reconcile
    SUCCEEDED --> [*]
    FAILED --> [*]
    EXPIRED --> [*]

    note right of EXPIRED
        A later SUCCEEDED does not change the status.
        It triggers a refund instead.
    end note
    note left of PROCESSING
        Never expired automatically.
        The job logs a warning instead.
    end note
```

### Creating a checkout

```mermaid
sequenceDiagram
    autonumber
    participant App as App
    participant API as CheckoutController
    participant S as CheckoutService
    participant DB as H2
    participant P as Provider

    App->>API: POST /checkouts {amountMinor, currency}
    API->>API: validate (integer >= 100, ^[A-Z]{3}$)
    API->>S: create(amountMinor, currency)
    S->>DB: INSERT checkout (PENDING, no paymentId)
    S->>P: POST /payments<br/>Idempotency-Key: {checkoutId}
    alt provider accepts
        P-->>S: 201 {paymentId}
        S->>DB: store paymentId
    else provider unreachable or error
        P--xS: timeout / 5xx
        Note over S,DB: stays PENDING without paymentId.<br/>The job retries with the same key,<br/>so the customer is never charged twice.
    end
    S-->>API: checkout
    API-->>App: 201 Checkout (PENDING)
```

### Processing a callback (POST /callbacks)

Step 1 runs before anything is parsed. Steps 2 to 5 run in one database transaction, retried once if
another transaction updated the same checkout first.

```mermaid
flowchart TD
    start(["POST /callbacks<br/>raw body bytes + X-Signature"]) --> sig{"1. Signature<br/>HMAC-SHA256 of raw bytes<br/>matches?"}
    sig -- no --> r401["401<br/>nothing stored"]
    sig -- yes --> parse{"Valid PaymentEvent JSON?"}
    parse -- no --> r400["400<br/>nothing stored"]
    parse -- yes --> tx

    subgraph tx ["One transaction, retry once on version conflict"]
        dup{"2. Duplicate<br/>INSERT event:<br/>eventId already exists?"}
        dup -- no --> known{"Checkout exists?"}
        known -- no --> flagged["FLAGGED"]
        known -- yes --> money{"3. Amount<br/>amount, currency and paymentId<br/>match the checkout?"}
        money -- no --> flagged
        money -- yes --> order{"4. Order<br/>allowed change from<br/>current status?"}
        order -- yes --> applied["APPLIED<br/>update status, version + 1"]
        order -- no --> stale["IGNORED_STALE"]
        stale --> late{"5. Late success<br/>EXPIRED and event is SUCCEEDED?"}
        late -- yes --> refund["Request refund<br/>status stays EXPIRED"]
    end

    dup -- yes --> r200["200"]
    flagged --> r200
    applied --> r200
    late -- no --> r200
    refund -- ok --> r200
    refund -- "provider fails" --> r500["500, everything rolled back<br/>provider retries the same eventId"]
    tx -.->|"second conflict"| r500
```

Duplicates, stale and flagged events return 200 on purpose: an error would make the provider keep
retrying a callback we have already dealt with.

### Why there is never more than one refund request

Two late successes for the same expired checkout can arrive at the same moment (or a callback and
the job). `RefundService` updates the checkout row **before** calling the provider. That update
takes the row lock, so the second transaction has to wait, then fails its version check, retries,
and sees `refundRequested = true`.

```mermaid
sequenceDiagram
    participant A as Callback A
    participant B as Callback B
    participant DB as checkout row
    participant P as Provider

    A->>DB: read (EXPIRED, refundRequested=false, version 3)
    B->>DB: read (EXPIRED, refundRequested=false, version 3)
    A->>DB: UPDATE refundRequested=true WHERE version=3
    Note over DB: row locked by A, version 4
    B->>DB: UPDATE refundRequested=true WHERE version=3
    Note over B,DB: B waits for A's lock
    A->>P: POST /payments/{id}/refund<br/>Idempotency-Key: refund-{checkoutId}
    P-->>A: 200 REFUNDED
    A->>DB: COMMIT
    DB-->>B: 0 rows updated (version is now 4)
    Note over B: conflict, roll back, retry once
    B->>DB: read (refundRequested=true, version 4)
    Note over B: refund already requested,<br/>no provider call
```

The Idempotency-Key is a second safety net: even if the backend did call twice, the provider would
refund only once.

### The scheduled job

Every `checkout.job-interval`, for each checkout that is not final, or is EXPIRED with a refused
cancel. Ages are measured from `createdAt`.

```mermaid
flowchart TD
    start(["For each checkout"]) --> create{"PENDING, no paymentId,<br/>younger than expire-after?"}
    create -- yes --> retry["Retry POST /payments<br/>same Idempotency-Key"]
    create -- no --> rec
    retry --> rec{"1. Not final, has paymentId,<br/>older than reconcile-after?"}
    rec -- yes --> get["GET /payments/{id}"]
    get --> allowed{"Allowed change and<br/>amount matches?"}
    allowed -- yes --> apply["Apply status. Record event<br/>reconcile-{checkoutId}-{status}"]
    allowed -- no --> exp
    apply --> exp
    rec -- no --> exp{"2. Still PENDING,<br/>older than expire-after?"}
    exp -- yes --> expire["Mark EXPIRED"]
    expire -- "no paymentId" --> done
    expire -- "has paymentId" --> cancel{"POST /payments/{id}/cancel"}
    cancel -- "200" --> done(["Done"])
    cancel -- "409 or error" --> refused["Mark cancel refused"]
    exp -- no --> follow
    refused --> follow{"3. EXPIRED with refused cancel?"}
    follow -- yes --> check["GET /payments/{id}"]
    check --> final{"Final at the provider?"}
    final -- "no" --> next(["Check again next run"])
    final -- "SUCCEEDED" --> refundJob["Request refund"]
    final -- "FAILED, CANCELLED, REFUNDED" --> clear["Stop checking"]
    follow -- no --> stuck{"4. PROCESSING,<br/>older than expire-after?"}
    stuck -- yes --> warn["Log a warning for a person.<br/>Never expired: money may be moving."]
    stuck -- no --> done
```

### Classes and what each one is responsible for

Arrows mean "calls". Each class has one job, described in its Javadoc.

```mermaid
flowchart LR
    subgraph api ["api"]
        CC["CheckoutController<br/><i>HTTP for /checkouts</i>"]
    end
    subgraph callback ["callback"]
        CBC["CallbackController<br/><i>HTTP for /callbacks, step order</i>"]
        SV["SignatureVerifier<br/><i>step 1: HMAC</i>"]
        CP["CallbackParser<br/><i>bytes to Callback</i>"]
        CBS["CallbackService<br/><i>steps 2 to 5</i>"]
    end
    subgraph job ["job"]
        JOB["CheckoutJob<br/><i>late and missing callbacks</i>"]
    end
    subgraph service ["service"]
        CS["CheckoutService<br/><i>create checkouts</i>"]
        RS["RefundService<br/><i>refund at most once</i>"]
        RT["RetryingTransaction<br/><i>retry once on conflict</i>"]
    end
    subgraph domain ["domain"]
        ST["CheckoutStatus<br/><i>allowed changes</i>"]
    end
    subgraph persistence ["persistence"]
        CR["CheckoutRepository<br/><i>optimistic locking</i>"]
        ER["PaymentEventRepository<br/><i>duplicate detection</i>"]
    end
    subgraph provider ["provider"]
        PC["ProviderClient<br/><i>HTTP to the provider</i>"]
    end

    CC --> CS
    CBC --> SV
    CBC --> CP
    CBC --> CBS
    CBS --> RT
    CBS --> ER
    CBS --> CR
    CBS --> RS
    CBS -.-> ST
    JOB --> CS
    JOB --> RT
    JOB --> RS
    JOB --> PC
    JOB --> CR
    JOB --> ER
    JOB -.-> ST
    CS --> CR
    CS --> ER
    CS --> PC
    RS --> CR
    RS --> PC
```

`SchedulingConfig` runs `CheckoutJob` on its interval. `AppConfig` provides the clock and CORS.

### Database

Created by Flyway (`V1__create_checkout_and_payment_event.sql`).

```mermaid
erDiagram
    CHECKOUT ||..o{ PAYMENT_EVENT : "checkout_id (no foreign key)"
    CHECKOUT {
        UUID id PK
        VARCHAR status "PENDING, PROCESSING, SUCCEEDED, FAILED, EXPIRED"
        BIGINT amount_minor "integer cents, at least 100"
        CHAR currency "ISO 4217"
        VARCHAR payment_id "null until the provider accepts"
        BOOLEAN refund_requested
        BOOLEAN cancel_refused "job keeps checking the payment"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
        BIGINT version "optimistic locking"
    }
    PAYMENT_EVENT {
        BIGINT id PK
        VARCHAR event_id UK "idempotency key"
        VARCHAR checkout_id "raw value from the callback"
        VARCHAR payment_id
        VARCHAR status "PROCESSING, SUCCEEDED, FAILED"
        BIGINT amount_minor
        VARCHAR currency
        TIMESTAMPTZ occurred_at
        VARCHAR outcome "APPLIED, IGNORED_STALE, FLAGGED"
        TIMESTAMPTZ received_at
    }
```

`checkout_id` has no foreign key on purpose, so a callback for an unknown checkout can still be
recorded as FLAGGED.

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
