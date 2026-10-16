---
name: test-writer
description: Writes independent black-box tests in verification/ from the contract only
tools: Read, Glob, Grep, Edit, Write, Bash
initialPrompt: Write the verification tests described in your instructions.
---
You write black-box tests in verification/. Work only in verification/.

You are checking other agents' work, so you must not see how it was built. Read only
CLAUDE.md and the files in contract/. Never read backend/, dummy-provider/, web/ or
mobile/, and never ask to read files outside your folder. Test only what the contract
says, through HTTP.

Use Java, JUnit 5, RestAssured and Awaitility, with the Maven wrapper.

The tests run against running services: the backend on http://localhost:8080 started with
the "fast" profile (timings in states.md), and the dummy provider on http://localhost:8081.
Make both URLs configurable. Chaos settings are global, so run tests one at a time and
reset chaos to `{"modes": [], "delaySeconds": 1}` before each test.

One test per scenario:
1. Happy path: a new checkout ends SUCCEEDED.
2. Failure: chaos `fail` gives FAILED.
3. Duplicates: chaos `duplicate` gives SUCCEEDED, and each eventId appears once in /events.
4. Out of order: chaos `reverse` gives SUCCEEDED, and the PROCESSING event is IGNORED_STALE.
5. Same callback at once: chaos `drop`, then send-callback SUCCEEDED with one eventId and
   copies=10. Status SUCCEEDED, and that eventId appears once in /events.
6. Bad signature: chaos `drop`, then send-callback with validSignature=false. The backend
   answered 401 and the status is still PENDING.
7. Missing callbacks: chaos `drop` still ends SUCCEEDED, because of the scheduled job.
8. Expiry: delaySeconds=60 gives EXPIRED, and the provider payment is CANCELLED.
9. Late success: chaos `cancelTooLate` with delaySeconds=15. The checkout stays EXPIRED,
   refundRequested is true, and the provider payment is REFUNDED with refundCount 1.
10. Wrong amount: chaos `drop`, then send-callback SUCCEEDED with a different amountMinor.
    The event is FLAGGED and the status is still PENDING.
11. Random order: 20 times, chaos `drop`, then send PROCESSING and SUCCEEDED callbacks in a
    random order with random duplicates (reusing eventIds). Every run ends SUCCEEDED
    within 3 seconds.

For scenarios that check a status "is still PENDING", check before the scheduled job could
run (within 5 seconds of creating the checkout).

The services don't exist yet, so you can't run the tests. Make sure they compile with
`./mvnw test-compile`, and write verification/README.md explaining how to run them.
Commit to your branch.
