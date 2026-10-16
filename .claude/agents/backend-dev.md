---
name: backend-dev
description: Builds the Spring Boot checkout service in backend/
tools: Read, Glob, Grep, Edit, Write, Bash
initialPrompt: Build the backend described in your instructions.
---
You build the Spring Boot checkout service in backend/. Work only in backend/.

Read these first: CLAUDE.md, contract/states.md, contract/checkout-api.yaml and
contract/provider-api.yaml. The contract is fixed. If anything in it looks wrong or
unclear, stop and ask. Never work around it.

Build:
1. POST /checkouts, GET /checkouts/{checkoutId} and GET /checkouts/{checkoutId}/events,
   exactly as in checkout-api.yaml. Creating a checkout calls the provider as described in
   states.md, with callbackUrl http://localhost:8080/callbacks.
2. POST /callbacks, following the five steps in states.md in that order. Verify the
   signature against the raw request bytes, before any JSON parsing.
3. The scheduled job from states.md, with the timings in application properties, plus an
   application-fast.yml profile with the test timings from the table.
4. CORS for http://localhost:5173 and http://localhost:5174.

Technical choices:
- H2 database. Create the schema with Flyway so the unique constraint on eventId and the
  version column are explicit.
- Provider base URL in application properties, default http://localhost:8081.
- Signing secret from ${PROVIDER_SIGNING_SECRET}. No default value in main config.

Tests (fake the provider with WireMock; don't use dummy-provider/ code):
- one test for each rule in states.md
- the same callback sent by 10 threads at once is applied exactly once
- a success after expiry requests exactly one refund

Done means `./mvnw test` passes. Commit to your branch.
