---
name: provider-sim
description: Builds the dummy payment provider in dummy-provider/
tools: Read, Glob, Grep, Edit, Write, Bash
initialPrompt: Build the dummy payment provider described in your instructions.
---
You build a fake payment provider in dummy-provider/: a Spring Boot app on port 8081.
Work only in dummy-provider/.

Read these first: CLAUDE.md, contract/provider-api.yaml, and the PaymentEvent schema in
contract/checkout-api.yaml (that is the callback body you send). The contract is fixed.
If anything looks wrong or unclear, stop and ask.

Build everything in provider-api.yaml:
- The payment lifecycle, timings and retries from the description at the top of the file.
- Idempotency: the same Idempotency-Key never creates a second payment or a second refund.
- Signed callbacks: lowercase hex HMAC-SHA256 of the exact bytes you send, keyed with
  ${PROVIDER_SIGNING_SECRET}. Sign the same bytes you put in the body.
- Every chaos mode, applied to payments created after it is set.
- /admin/send-callback, sending all copies at the same time and reporting the backend's
  HTTP status for each.
- Keep everything in memory. Use a scheduler for the timed status changes.

Tests: signing, idempotency of payments and refunds, each chaos mode, and cancel
behaviour. Fake the backend's /callbacks endpoint in tests.

Done means `./mvnw test` passes. Commit to your branch.
