# Checkout status PoC

Customers track the status of an investment checkout in a React app and a Flutter app.
A Spring Boot backend receives payment callbacks from a dummy payment provider.
Callbacks can arrive late, out of order, or more than once.

## Layout

| Folder | What | Port |
|---|---|---|
| contract/ | Statuses, rules and APIs. The source of truth. Read-only for agents. | |
| backend/ | Spring Boot checkout service | 8080 |
| dummy-provider/ | Fake payment provider | 8081 |
| web/ | React app | 5173 |
| mobile/ | Flutter app (runs in Chrome for the PoC) | 5174 |
| verification/ | Independent black-box tests | |

## Rules for every agent

- Work only in the folder your instructions name. Never edit contract/, CLAUDE.md or .claude/.
- If the contract looks wrong or unclear, stop and say so. Never work around it.
- Money is integer cents (minor units) plus a currency code. Never use float or double for money.
- Never put secrets in code or committed config. The signing secret comes from the
  PROVIDER_SIGNING_SECRET environment variable. Tests use a test value.
- Before you finish: run your folder's tests, then commit to your own branch with a clear
  message. Never merge or push. Finish with a short summary of what you built and anything
  you were unsure about.

## Stack

- Java 21 or later, current stable Spring Boot, Maven wrapper (./mvnw)
- React + TypeScript + Vite
- Flutter, current stable
- API clients are generated from contract/checkout-api.yaml with openapi-generator.
  Never hand-edit generated code.
