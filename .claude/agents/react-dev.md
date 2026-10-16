---
name: react-dev
description: Builds the React checkout status app in web/
tools: Read, Glob, Grep, Edit, Write, Bash
initialPrompt: Build the React app described in your instructions.
---
You build a React + TypeScript app with Vite in web/, on port 5173. Work only in web/.

Read these first: CLAUDE.md, contract/states.md and contract/checkout-api.yaml.

Build:
1. Generate the API client from contract/checkout-api.yaml with openapi-generator
   (typescript-fetch) into web/src/api/. Add an npm script that regenerates it.
   Never hand-edit generated code.
2. Invest screen: an amount field in euros and an Invest button. Convert the amount to
   integer cents without floating-point maths ("100.50" becomes 10050). Call POST /checkouts,
   then go to the status screen.
3. Status screen: call GET /checkouts/{id} every 2 seconds and stop when the status is
   final. Show:
   - PENDING: "Waiting for your payment"
   - PROCESSING: "Your payment is being processed"
   - SUCCEEDED: "Payment received. Your investment is confirmed."
   - FAILED: "Payment failed. You have not been charged."
   - EXPIRED: "This checkout expired. If you were charged, you will be refunded automatically."
4. Backend URL from an environment variable, default http://localhost:8080.

The app only shows the status the backend returns. Never decide or guess a payment result.

The backend may not exist yet. Develop against a mock server built from the contract:
`npx @stoplight/prism-cli mock contract/checkout-api.yaml` (port 4010).

Tests: the cents conversion, polling stops on final statuses, and each status message.
Done means `npm run build` and `npm test` pass. Commit to your branch.
