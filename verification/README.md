# Verification: black-box tests

These tests check the checkout backend and the dummy payment provider **only over HTTP**,
using what `contract/` says. They don't use any code from `backend/` or `dummy-provider/`.

Stack: Java 21, JUnit 5, RestAssured, Awaitility, AssertJ, Maven wrapper.

## How the tests reach the services

The tests never call backend code directly. They drive the system from the outside: they
create checkouts on the backend, and use the provider's `/admin` endpoints to set chaos modes
and to send hand-made callbacks. Then they read the results back from both services.

```mermaid
flowchart LR
    subgraph tests["verification/ (JUnit)"]
        T["CheckoutScenariosTest"]
    end

    subgraph backend["Backend :8080"]
        BC["POST /checkouts<br/>GET /checkouts/{id}<br/>GET /checkouts/{id}/events"]
        CB["POST /callbacks"]
        JOB["Scheduled job<br/>(reconcile, expire, refund)"]
    end

    subgraph provider["Dummy provider :8081"]
        ADMIN["PUT /admin/chaos<br/>POST /admin/send-callback"]
        PAY["POST /payments<br/>GET /payments/{id}<br/>cancel / refund"]
    end

    T -- "create checkout, read status and events" --> BC
    T -- "set chaos, send callbacks" --> ADMIN
    T -- "read payment status and refundCount" --> PAY

    BC -- "start payment" --> PAY
    JOB -- "check, cancel, refund" --> PAY
    PAY -- "signed callbacks (automatic)" --> CB
    ADMIN -- "signed callbacks (on demand)" --> CB
```

## How the test code is split

Each class has one job (see the class-level Javadoc). Only `CheckoutScenariosTest` decides
whether the system behaved correctly. The two clients only make calls and turn JSON into records.

```mermaid
classDiagram
    direction LR
    class CheckoutScenariosTest {
        Responsibility: all expectations
        one test per scenario
        resetChaos() before each test
    }
    class Backend {
        Responsibility: HTTP + JSON for the backend
        createCheckout()
        getCheckout()
        getEvents()
    }
    class Provider {
        Responsibility: HTTP + JSON for the provider
        setChaos() / resetChaos()
        getPayment()
        sendCallback()
    }
    class CallbackRequest {
        Responsibility: one send-callback body
        eventId() amountMinor()
        validSignature() copies()
    }
    class TestConfig {
        Responsibility: service URLs
        BACKEND_URL
        PROVIDER_URL
    }
    CheckoutScenariosTest --> Backend
    CheckoutScenariosTest --> Provider
    Provider ..> CallbackRequest : sends
    Backend --> TestConfig : base URL
    Provider --> TestConfig : base URL
```

## Before you run them

Both services must already be running:

| Service | Default URL | Notes |
|---|---|---|
| Backend | http://localhost:8080 | Must use the **`fast`** profile (reconcile-after 5s, expire-after 10s, job-interval 2s). |
| Dummy provider | http://localhost:8081 | Nothing to configure for callbacks: the backend sends its `callbackUrl` with each payment. |

Both must use the same `PROVIDER_SIGNING_SECRET` (any test value). The tests don't need the
secret: they ask the provider to sign callbacks via `POST /admin/send-callback`.

## Running

```bash
cd verification
./mvnw test
```

Use other URLs with system properties or environment variables (system properties win):

```bash
./mvnw test -Dbackend.url=http://localhost:9080 -Dprovider.url=http://localhost:9081
# or
BACKEND_URL=http://localhost:9080 PROVIDER_URL=http://localhost:9081 ./mvnw test
```

Run one scenario:

```bash
./mvnw test -Dtest='CheckoutScenariosTest#lateSuccessIsRefunded'
```

To only check that the tests compile: `./mvnw test-compile`.

A full run takes about 1 minute, mostly waiting for the scheduled job (expiry, late success).

## Important: one test at a time

Chaos settings on the provider are global. The tests run one at a time (JUnit parallel
execution is off), and each test resets chaos to `{"modes": [], "delaySeconds": 1}` before
it starts. **Don't run two test runs at the same time against the same provider**, and don't
change chaos by hand during a run.

## Scenarios

All in `CheckoutScenariosTest`. Amounts are `10050 EUR`.

| # | Test | Setup | Expected |
|---|---|---|---|
| 1 | `happyPath` | default chaos | Checkout ends SUCCEEDED. |
| 2 | `failedPayment` | `fail` | FAILED. |
| 3 | `duplicateCallbacks` | `duplicate` | SUCCEEDED; every eventId appears once in `/events` (checked for 3s so late copies are included). |
| 4 | `reversedCallbacks` | `reverse` | SUCCEEDED; the PROCESSING event is `IGNORED_STALE`. |
| 5 | `concurrentIdenticalCallbacks` | `drop`, then send-callback SUCCEEDED, one eventId, `copies=10` | Every copy answered 200; SUCCEEDED; that eventId appears once (`APPLIED`). |
| 6 | `badSignature` | `drop`, then send-callback with `validSignature=false` | Backend answered 401; still PENDING; nothing stored for that eventId. |
| 7 | `droppedCallbacksReconciled` | `drop` | SUCCEEDED via the scheduled job; event `reconcile-{checkoutId}-SUCCEEDED` exists. |
| 8 | `expiry` | `delaySeconds=60` | EXPIRED; provider payment CANCELLED. |
| 9 | `lateSuccessIsRefunded` | `cancelTooLate`, `delaySeconds=15` | Stays EXPIRED; `refundRequested=true`; provider payment REFUNDED with `refundCount=1` (and stays so for 6s). |
| 10 | `wrongAmountIsFlagged` | `drop`, then send-callback SUCCEEDED with `amountMinor` 10049 | Answered 200; event `FLAGGED`; still PENDING. |
| 11 | `randomOrderAndDuplicates` (×20) | `drop`, then PROCESSING and SUCCEEDED callbacks in random order with random duplicates (same eventIds, 1–3 copies each) | All answered 200; SUCCEEDED within 3s of the first callback; the SUCCEEDED event is `APPLIED`. The random seed is printed for each run. |

### Which tests cover which status change

The allowed status changes from `contract/states.md`, labelled with the tests that drive the
checkout through them. Most tests only assert the final status, not each step. Final statuses
never change.

Gap: no test sends FAILED straight to a PENDING checkout (PENDING → FAILED). Test 2 reaches
FAILED through PROCESSING.

```mermaid
stateDiagram-v2
    [*] --> PENDING : POST /checkouts
    PENDING --> PROCESSING : PROCESSING callback (tests 1, 2, 3, 11)
    PENDING --> SUCCEEDED : SUCCEEDED callback (tests 4, 5, 11) or reconcile (test 7)
    PENDING --> FAILED : FAILED callback (no test yet)
    PENDING --> EXPIRED : scheduled job after expire-after (tests 8, 9)
    PROCESSING --> SUCCEEDED : SUCCEEDED callback (tests 1, 3, 11)
    PROCESSING --> FAILED : FAILED callback (test 2)
    SUCCEEDED --> [*]
    FAILED --> [*]
    EXPIRED --> [*]

    note right of PENDING
        Stays PENDING on a bad signature (test 6)
        or a wrong amount (test 10)
    end note
    note right of EXPIRED
        A late SUCCEEDED keeps EXPIRED
        and triggers one refund (test 9)
    end note
```

### What happens to each callback, and which test checks it

The backend handles every callback in the order set out in `states.md`. Each exit is checked
by at least one test.

```mermaid
flowchart TD
    IN["POST /callbacks"] --> SIG{"1. Signature valid?"}
    SIG -- no --> R401["401, nothing stored<br/>test 6"]
    SIG -- yes --> DUP{"2. eventId already stored?"}
    DUP -- yes --> RDUP["200, nothing changes<br/>tests 3, 5, 11"]
    DUP -- no --> AMT{"3. amountMinor and currency<br/>match the checkout?"}
    AMT -- no --> RFLAG["200, event FLAGGED<br/>test 10"]
    AMT -- yes --> ORD{"4. Status change allowed<br/>from current status?"}
    ORD -- no --> RSTALE["200, event IGNORED_STALE<br/>test 4"]
    ORD -- yes --> RAPP["200, event APPLIED<br/>tests 5, 11"]
    ORD -- "SUCCEEDED for an<br/>EXPIRED checkout" --> RLATE["5. Stays EXPIRED,<br/>refund requested<br/>test 9"]
```

### Two ways the tests produce callbacks

Most tests let the provider send callbacks itself, shaped by a chaos mode (`duplicate`,
`reverse`, `fail`, ...). Tests 5, 6, 10 and 11 turn automatic callbacks off with `drop` and
send exactly the callbacks they want through `POST /admin/send-callback`. This is test 5:

```mermaid
sequenceDiagram
    autonumber
    participant T as Test
    participant P as Provider
    participant B as Backend

    T->>P: PUT /admin/chaos {"modes": ["drop"]}
    T->>B: POST /checkouts (10050 EUR)
    B->>P: POST /payments (Idempotency-Key = checkoutId)
    P-->>B: 201 paymentId
    B-->>T: 201 PENDING, paymentId
    Note over P,B: drop: the provider sends no callbacks by itself

    T->>P: POST /admin/send-callback<br/>SUCCEEDED, eventId X, copies 10
    par 10 identical copies at the same time
        P->>B: POST /callbacks (eventId X)
    end
    B-->>P: 200 for every copy
    P-->>T: results: 10 x backendStatus 200

    T->>B: GET /checkouts/{id}
    B-->>T: SUCCEEDED
    T->>B: GET /checkouts/{id}/events
    B-->>T: eventId X appears once, APPLIED
```

### The slowest test: late success (test 9)

Tests 7, 8 and 9 rely on the scheduled job, using the `fast` profile timings. Test 9 is the
trickiest: the payment completes after the checkout has already expired, so the customer
paid for an investment that was never made and must get exactly one refund.

```mermaid
sequenceDiagram
    participant T as Test
    participant B as Backend
    participant J as Scheduled job (every 2s)
    participant P as Provider

    T->>P: PUT /admin/chaos cancelTooLate, delaySeconds 15
    T->>B: POST /checkouts
    B->>P: POST /payments
    Note over P: payment PENDING for 15s

    Note over J: about 10-12s: PENDING longer than expire-after
    J->>B: mark checkout EXPIRED
    J->>P: POST /payments/{id}/cancel
    P-->>J: 409 too late (cancelTooLate)
    Note over J: keep checking this payment on every run

    Note over P: about 15s: PROCESSING
    P->>B: PROCESSING callback
    Note over B: EXPIRED to PROCESSING not allowed: IGNORED_STALE
    Note over P: about 16s: SUCCEEDED
    P->>B: SUCCEEDED callback
    B->>P: POST /payments/{id}/refund<br/>Idempotency-Key refund-{checkoutId}
    Note over B,J: the job may also ask for the refund,<br/>with the same key, so it happens only once

    loop poll until true, then keep checking for 6s
        T->>B: GET /checkouts/{id}
        B-->>T: EXPIRED, refundRequested true
        T->>P: GET /payments/{id}
        P-->>T: REFUNDED, refundCount 1
    end
```

### "Still PENDING" checks

Tests 6 and 10 check PENDING before the scheduled job could reconcile the checkout. The test
fails if that check happens 5s (`reconcile-after` in the fast profile) or more after the
create request was sent, rather than passing for the wrong reason.

## Assumptions

- The backend stores the paymentId when the checkout is created (states.md, "Creating a
  checkout"). Tests that need it wait up to 3s for it.
- Test 7 relies on the reconcile eventId format `reconcile-{checkoutId}-{status}` from states.md.
