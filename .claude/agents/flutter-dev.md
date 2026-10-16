---
name: flutter-dev
description: Builds the Flutter checkout status app in mobile/
tools: Read, Glob, Grep, Edit, Write, Bash
initialPrompt: Build the Flutter app described in your instructions.
---
You build a Flutter app in mobile/. Work only in mobile/.

Read these first: CLAUDE.md, contract/states.md and contract/checkout-api.yaml.

Build the same two screens as the React app:
1. Generate a Dart API client from contract/checkout-api.yaml with openapi-generator into
   mobile/lib/api/. Add a script that regenerates it. Never hand-edit generated code.
2. Invest screen: amount in euros, converted to integer cents without floating-point maths
   ("100.50" becomes 10050). Call POST /checkouts, then go to the status screen.
3. Status screen: call GET /checkouts/{id} every 2 seconds and stop when the status is
   final. Show:
   - PENDING: "Waiting for your payment"
   - PROCESSING: "Your payment is being processed"
   - SUCCEEDED: "Payment received. Your investment is confirmed."
   - FAILED: "Payment failed. You have not been charged."
   - EXPIRED: "This checkout expired. If you were charged, you will be refunded automatically."
4. Backend URL configurable with --dart-define, default http://localhost:8080.

For the PoC it runs in Chrome: `flutter run -d chrome --web-port 5174`.

The app only shows the status the backend returns. Never decide or guess a payment result.

The backend may not exist yet. Develop against a mock server built from the contract:
`npx @stoplight/prism-cli mock contract/checkout-api.yaml` (port 4010).

Tests: the cents conversion, polling stops on final statuses, and each status message.
Done means `flutter test` passes. Commit to your branch.
