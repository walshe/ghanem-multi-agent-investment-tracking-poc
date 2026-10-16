# Checkout mobile app (Flutter)

Two screens, same as the React app:

1. **Invest**: enter an amount in euros. It is converted to integer cents by string parsing
   (no floating point: `"100.50"` → `10050`), then `POST /checkouts` is called.
2. **Status**: calls `GET /checkouts/{id}` every 2 seconds and stops once the status is final
   (SUCCEEDED, FAILED, EXPIRED). It shows only what the backend returns and never guesses a
   payment result. If a request fails, the last status stays on screen and polling continues.

## Run (Chrome)

```bash
flutter run -d chrome --web-port 5174                                  # backend at http://localhost:8080
flutter run -d chrome --web-port 5174 --dart-define=API_BASE_URL=http://127.0.0.1:4010   # Prism mock
```

Mock server built from the contract (port 4010):

```bash
npm install
npm run mock         # prism mock ../contract/checkout-api.yaml
# or: npx prism mock -d ../contract/checkout-api.yaml   (random dynamic responses)
```

## API client

`lib/api/` is generated from `../contract/checkout-api.yaml` with openapi-generator
(`dart` generator, version pinned in `openapitools.json`). **Never hand-edit it.**
Regenerate after a contract change:

```bash
npm install
npm run generate-api   # runs tool/generate_api.sh
```

## Tests

```bash
flutter test                    # VM
flutter test --platform chrome  # compiled to JS in Chrome
```

| File | Covers |
|---|---|
| `test/money_test.dart` | euros → cents conversion, including floating-point traps (0.29, 1.15) and invalid input |
| `test/checkout_poller_test.dart` | polls every 2 s, stops on each final status, survives errors, `stop()` |
| `test/status_screen_test.dart` | the message for every status, PENDING → PROCESSING → SUCCEEDED flow |
| `test/invest_flow_test.dart` | 100.50 is posted as `{amountMinor: 10050, currency: EUR}`, validation, backend errors |

## Layout

```
lib/
  api/                     generated client (do not edit)
  config.dart              API_BASE_URL (--dart-define), poll interval, currency, min amount
  money.dart               eurosToCents / formatMoney (integer only)
  checkout_status_text.dart  status → message, isFinalStatus
  checkout_poller.dart     sequential 2 s polling, stops on final status
  screens/invest_screen.dart
  screens/status_screen.dart
tool/generate_api.sh       regenerates lib/api/
```
